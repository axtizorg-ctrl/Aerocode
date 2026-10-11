package com.example.data.repository

import android.content.Context
import com.example.data.model.Language
import com.example.data.model.ProjectItem
import java.io.File

class ProjectRepository(private val context: Context) {

    private val projectsRoot: File = File(context.filesDir, "projects").apply {
        if (!exists()) mkdirs()
    }

    fun getProjectsRoot(): File = projectsRoot

    /**
     * Shared storage directory accessible to both AeroCode and Termux without root.
     */
    fun getSharedProjectsRoot(): File {
        val ext = context.getExternalFilesDir(null)
        val dir = if (ext != null) {
            File(ext, "projects")
        } else {
            File(android.os.Environment.getExternalStorageDirectory(), "AeroCode/projects")
        }
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Copies a project into shared storage so Termux can execute tools in it without root.
     */
    fun copyProjectToSharedStorage(projectName: String): File? {
        val src = getProjectDirectory(projectName)
        val dest = File(getSharedProjectsRoot(), projectName)
        return try {
            if (!dest.exists()) dest.mkdirs()
            src.copyRecursively(dest, overwrite = true)
            dest
        } catch (_: Exception) {
            null
        }
    }

    init {
        seedSampleProjectsIfNeeded()
    }

    fun getProjects(): List<String> {
        return projectsRoot.listFiles { f -> f.isDirectory }
            ?.map { it.name }
            ?.sorted()
            ?: listOf("CppProject")
    }

    fun getProjectDirectory(projectName: String): File {
        val dir = File(projectsRoot, projectName)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun listProjectFiles(projectDir: File, showHidden: Boolean = false): List<ProjectItem> {
        return buildFileTree(projectDir, showHidden)
    }

    private fun buildFileTree(dir: File, showHidden: Boolean): List<ProjectItem> {
        val files = dir.listFiles() ?: return emptyList()
        return files
            .filter { showHidden || !it.name.startsWith(".") }
            .sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            .map { file ->
                if (file.isDirectory) {
                    ProjectItem(
                        name = file.name,
                        path = file.absolutePath,
                        isDirectory = true,
                        children = buildFileTree(file, showHidden),
                        isExpanded = false
                    )
                } else {
                    ProjectItem(
                        name = file.name,
                        path = file.absolutePath,
                        isDirectory = false,
                        sizeBytes = file.length(),
                        extension = file.extension.lowercase()
                    )
                }
            }
    }

    fun readFile(path: String): String {
        val file = File(path)
        return if (file.exists()) file.readText() else ""
    }

    fun writeFile(path: String, content: String): Boolean {
        return try {
            val file = File(path)
            file.parentFile?.mkdirs()
            file.writeText(content)
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun createFile(parentPath: String, name: String, initialContent: String = ""): File? {
        val parent = File(parentPath)
        parent.mkdirs()
        val target = File(parent, name)
        return try {
            target.parentFile?.mkdirs()
            if (!target.exists()) {
                target.createNewFile()
            }
            if (initialContent.isNotEmpty()) {
                target.writeText(initialContent)
            }
            target
        } catch (e: Exception) {
            null
        }
    }

    fun createFolder(parentPath: String, name: String): File? {
        val target = File(parentPath, name)
        return if (target.mkdirs() || target.exists()) target else null
    }

    fun deleteItem(path: String): Boolean {
        val file = File(path)
        return if (file.isDirectory) file.deleteRecursively() else file.delete()
    }

    fun renameItem(path: String, newName: String): File? {
        val source = File(path)
        val target = File(source.parentFile, newName)
        return if (source.renameTo(target)) target else null
    }

    fun duplicateFile(path: String): File? {
        val source = File(path)
        if (!source.exists() || source.isDirectory) return null
        val base = source.nameWithoutExtension
        val ext = if (source.extension.isNotEmpty()) ".${source.extension}" else ""
        var counter = 1
        var target: File
        do {
            target = File(source.parentFile, "${base}_copy$counter$ext")
            counter++
        } while (target.exists())

        return try {
            source.copyTo(target)
            target
        } catch (e: Exception) {
            null
        }
    }

    fun searchProject(projectDir: File, query: String): List<Pair<File, Int>> {
        val results = mutableListOf<Pair<File, Int>>()
        if (query.isBlank()) return results

        projectDir.walkTopDown()
            .filter { it.isFile && !it.name.startsWith(".") && it.length() < 1_000_000 }
            .forEach { file ->
                try {
                    file.useLines { lines ->
                        lines.forEachIndexed { index, line ->
                            if (line.contains(query, ignoreCase = true)) {
                                results.add(Pair(file, index + 1))
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        return results
    }

    fun createNewProject(name: String, template: Language): File {
        val sanitized = name.replace("[^a-zA-Z0-9_-]".toRegex(), "")
        val projDir = File(projectsRoot, sanitized).apply { mkdirs() }

        when (template) {
            Language.CPP -> {
                File(projDir, "main.cpp").writeText(Language.CPP.sampleBoilerplate)
                File(projDir, "CMakeLists.txt").writeText("""cmake_minimum_required(VERSION 3.10)
project($sanitized CXX)
set(CMAKE_CXX_STANDARD 17)
add_executable($sanitized main.cpp)
""")
            }
            Language.PYTHON -> {
                File(projDir, "main.py").writeText(Language.PYTHON.sampleBoilerplate)
                File(projDir, "requirements.txt").writeText("# AeroCode Python dependencies\n")
            }
            Language.HTML -> {
                File(projDir, "index.html").writeText(Language.HTML.sampleBoilerplate)
                File(projDir, "style.css").writeText(Language.CSS.sampleBoilerplate)
                File(projDir, "app.js").writeText(Language.JAVASCRIPT.sampleBoilerplate)
            }
            Language.JAVA -> {
                File(projDir, "Main.java").writeText(Language.JAVA.sampleBoilerplate)
            }
            Language.RUST -> {
                File(projDir, "main.rs").writeText(Language.RUST.sampleBoilerplate)
                File(projDir, "Cargo.toml").writeText("""[package]
name = "$sanitized"
version = "0.1.0"
edition = "2021"

[dependencies]
""")
            }
            else -> {
                File(projDir, "main.txt").writeText("Hello from $name")
            }
        }
        return projDir
    }

    private fun seedSampleProjectsIfNeeded() {
        val cppDir = File(projectsRoot, "CppDemo")
        if (!cppDir.exists()) {
            cppDir.mkdirs()
            File(cppDir, "main.cpp").writeText("""#include <iostream>
#include <vector>
#include <numeric>

int calculateSum(const std::vector<int>& numbers) {
    return std::accumulate(numbers.begin(), numbers.end(), 0);
}

int main() {
    std::cout << "=== AeroCode C++ Mobile Runtime ===" << std::endl;
    std::vector<int> data = {10, 20, 30, 40, 50};
    int total = calculateSum(data);
    std::cout << "Sum of elements: " << total << std::endl;
    return 0;
}
""")
            File(cppDir, "CMakeLists.txt").writeText("""cmake_minimum_required(VERSION 3.10)
project(CppDemo CXX)
set(CMAKE_CXX_STANDARD 17)
add_executable(CppDemo main.cpp)
""")
        }

        val pyDir = File(projectsRoot, "PythonTools")
        if (!pyDir.exists()) {
            pyDir.mkdirs()
            File(pyDir, "main.py").writeText("""import sys
import math

def fibonacci(n):
    a, b = 0, 1
    result = []
    for _ in range(n):
        result.append(a)
        a, b = b, a + b
    return result

if __name__ == "__main__":
    print("=== AeroCode Python Environment ===")
    print(f"Python interpreter: {sys.version.split()[0]}")
    seq = fibonacci(8)
    print(f"Fibonacci series: {seq}")
    print(f"Square root of 144: {math.isqrt(144)}")
""")
            File(pyDir, "requirements.txt").writeText("# Python dependencies\n")
        }

        val webDir = File(projectsRoot, "WebPreview")
        if (!webDir.exists()) {
            webDir.mkdirs()
            File(webDir, "index.html").writeText(Language.HTML.sampleBoilerplate)
            File(webDir, "style.css").writeText(Language.CSS.sampleBoilerplate)
            File(webDir, "app.js").writeText(Language.JAVASCRIPT.sampleBoilerplate)
        }
    }
}
