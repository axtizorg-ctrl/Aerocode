package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.data.model.Language
import com.example.data.model.Problem
import com.example.data.repository.AiRepository
import com.example.data.repository.ProjectRepository
import com.example.data.repository.TermuxRepository
import com.example.ui.editor.detectCodeBlocks
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("AeroCode", appName)
    }

    @Test
    fun `test language detection`() {
        assertEquals(Language.CPP, Language.detect("main.cpp"))
        assertEquals(Language.PYTHON, Language.detect("script.py"))
        assertEquals(Language.JAVASCRIPT, Language.detect("Input.js"))
        assertEquals(Language.HTML, Language.detect("Index.html"))
        assertEquals(Language.CSS, Language.detect("Style.css"))
        assertEquals(Language.RUST, Language.detect("main.rs"))
    }

    @Test
    fun `test dangerous command detection`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val termuxRepo = TermuxRepository(context)
        assertTrue(termuxRepo.isDangerousCommand("rm -rf /"))
        assertTrue(termuxRepo.isDangerousCommand("mkfs.ext4 /dev/sda1"))
        assertFalse(termuxRepo.isDangerousCommand("clang++ main.cpp -o main"))
        assertFalse(termuxRepo.isDangerousCommand("python3 test.py"))
    }

    @Test
    fun `test project creation and seeding`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val projectRepo = ProjectRepository(context)
        val projects = projectRepo.getProjects()
        assertTrue(projects.isNotEmpty())
        assertTrue(projects.contains("WebPreview"))
    }

    @Test
    fun `test termux clone and link without download`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val termuxRepo = TermuxRepository(context)
        val result = termuxRepo.cloneAndLinkTermuxStorageAndPackages()
        assertTrue(result.isSuccess)
        assertNotNull(result.storagePath)
    }

    @Test
    fun `test codex auto fix flow`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val projectRepo = ProjectRepository(context)
        val aiRepo = AiRepository(projectRepo)

        val dummyFile = File(context.filesDir, "Input.js")
        val dummyCode = "const x = pritnf('test');"
        val prob = Problem("p1", dummyFile.absolutePath, 1, 1, "pritnf was not declared")

        val resultMsg = aiRepo.runCodexAutoFix(dummyFile, dummyCode, listOf(prob), "error: pritnf undeclared").first()
        assertNotNull(resultMsg)
        assertNotNull(resultMsg.text)
    }

    @Test
    fun `test function and class code folding detection`() {
        val sampleJsCode = listOf(
            "// setup",
            "function calculateSum(a, b) {",
            "    const total = a + b;",
            "    return total;",
            "}",
            "class MathHelper {",
            "    multiply(x, y) { return x * y; }",
            "}"
        )

        val blocks = detectCodeBlocks(sampleJsCode, Language.JAVASCRIPT)
        assertTrue("calculateSum should be detected as foldable", blocks.containsKey(1))
        assertEquals(4, blocks[1]?.endLine)
        assertEquals("function", blocks[1]?.label)

        assertTrue("MathHelper class should be detected as foldable", blocks.containsKey(5))
        assertEquals(7, blocks[5]?.endLine)
        assertEquals("class", blocks[5]?.label)
    }
}
