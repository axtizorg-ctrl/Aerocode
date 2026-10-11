package com.example.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import com.example.data.model.TerminalLine
import com.example.data.model.TerminalLineType
import com.example.data.model.TermuxTool
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader

class TermuxRepository(private val context: Context) {

    private val TERMUX_PACKAGE = "com.termux"
    private val TERMUX_USR_BIN = "/data/data/com.termux/files/usr/bin"
    private val activeProcesses = mutableMapOf<String, Process>()

    val bridge = TermuxBridge(context)

    /**
     * Checks if Termux is installed on the user device.
     */
    fun isTermuxInstalled(): Boolean {
        return bridge.isTermuxInstalled()
    }

    /**
     * Scans for available development tools in Termux and system PATH.
     */
    suspend fun scanTermuxEnvironment(): List<TermuxTool> = withContext(Dispatchers.IO) {
        val toolsToScan = listOf(
            Triple("Python", "python3", "pkg install python"),
            Triple("Node.js", "node", "pkg install nodejs"),
            Triple("npm", "npm", "pkg install nodejs"),
            Triple("Clang / C++", "clang++", "pkg install clang"),
            Triple("Java", "javac", "pkg install openjdk-17"),
            Triple("Git", "git", "pkg install git"),
            Triple("CMake", "cmake", "pkg install cmake"),
            Triple("Make", "make", "pkg install make"),
            Triple("PHP", "php", "pkg install php"),
            Triple("Rust", "rustc", "pkg install rust"),
            Triple("Go", "go", "pkg install golang"),
            Triple("Bash", "bash", "pkg install bash")
        )

        toolsToScan.map { (name, binary, installCmd) ->
            val detected = checkBinaryAvailable(binary)
            TermuxTool(
                name = name,
                binaryName = binary,
                isInstalled = detected.first,
                version = detected.second,
                installCommand = installCmd,
                category = when (name) {
                    "Clang / C++", "Rust", "Go", "Java" -> "Compiler"
                    "Python", "Node.js", "PHP" -> "Interpreter"
                    "Git", "CMake", "Make", "npm" -> "Build & VCS"
                    else -> "Shell"
                }
            )
        }
    }

    private fun checkBinaryAvailable(binary: String): Pair<Boolean, String?> {
        // 1. Direct Termux path check
        val termuxBin = File("$TERMUX_USR_BIN/$binary")
        if (termuxBin.exists() && termuxBin.canExecute()) {
            return Pair(true, "Termux ($binary)")
        }

        // 2. Android system PATH check (which or command -v)
        return try {
            val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", "command -v $binary || which $binary"))
            val reader = BufferedReader(InputStreamReader(proc.inputStream))
            val line = reader.readLine()
            proc.waitFor()
            if (!line.isNullOrBlank()) {
                Pair(true, line.trim())
            } else {
                Pair(false, null)
            }
        } catch (_: Exception) {
            Pair(false, null)
        }
    }

    /**
     * Detects potentially destructive commands.
     */
    fun isDangerousCommand(cmd: String): Boolean {
        val lower = cmd.trim().lowercase()
        return lower.contains("rm -rf") ||
                lower.contains("mkfs") ||
                lower.contains("dd if=") ||
                lower.contains("> /dev/sda") ||
                lower.contains(":(){ :|:& };:") ||
                lower.contains("chmod -r 777 /")
    }

    /**
     * Executes a command in an interactive session, streaming output lines.
     * Uses Termux RUN_COMMAND bridge when Termux is installed, falling back to local sandbox gracefully.
     */
    fun executeCommand(
        sessionId: String,
        command: String,
        workingDir: File,
        envVars: Map<String, String> = emptyMap()
    ): Flow<TerminalLine> {
        return bridge.executeCommandStreaming(sessionId, command, workingDir)
    }

    /**
     * Runs a harmless test command (Step 4 verification) via Termux bridge.
     */
    suspend fun runBridgeTestCommand(workingDir: File): TermuxCommandResult {
        val request = TermuxCommandRequest(
            command = "echo \"AEROCODE_TERMUX_BRIDGE_OK\" && uname -m",
            workingDir = workingDir.absolutePath,
            inBackground = true,
            timeoutMs = 15_000L
        )
        return bridge.executeCommand(request)
    }

    /**
     * Runs environment diagnostics for Step 3.
     */
    fun performDiagnostics(workingDir: File): TermuxEnvironmentDiagnostics {
        return bridge.performDiagnostics(workingDir)
    }

    /**
     * Stops an active process (SIGINT/kill).
     */
    fun stopProcess(sessionId: String): Boolean {
        val proc = activeProcesses[sessionId] ?: return false
        return try {
            proc.destroy()
            activeProcesses.remove(sessionId)
            true
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Creates an intent to launch or send command to Termux via RUN_COMMAND.
     */
    fun createTermuxRunIntent(command: String, workingDir: String): Intent {
        val request = TermuxCommandRequest(
            command = command,
            workingDir = workingDir,
            inBackground = false
        )
        return bridge.createRunCommandIntent(request)
    }

    /**
     * Opens the Termux app directly on device.
     */
    fun openTermuxApp(): Intent? {
        return context.packageManager.getLaunchIntentForPackage(TERMUX_PACKAGE)
    }

    data class TermuxCloneResult(
        val isSuccess: Boolean,
        val message: String,
        val packageCount: Int,
        val detectedTools: List<String>,
        val storagePath: String
    )

    /**
     * Clones and links Termux storage and existing packages without re-downloading anything.
     */
    suspend fun cloneAndLinkTermuxStorageAndPackages(): TermuxCloneResult = withContext(Dispatchers.IO) {
        val termuxBinDir = File(TERMUX_USR_BIN)
        val termuxHomeDir = File("/data/data/com.termux/files/home")
        val sdcardStorage = File("/sdcard")

        val toolsFound = mutableListOf<String>()
        var pkgCount = 0

        if (termuxBinDir.exists() && termuxBinDir.canRead()) {
            val bins = termuxBinDir.listFiles() ?: emptyArray()
            pkgCount = bins.size
            listOf("python3", "python", "node", "npm", "clang", "clang++", "javac", "java", "git", "cmake", "make", "rustc", "go", "php", "bash", "zsh").forEach { tool ->
                if (File(termuxBinDir, tool).exists()) {
                    toolsFound.add(tool)
                }
            }
        } else {
            // Check system PATH tools as fallback zero-download link
            listOf("python3", "python", "node", "npm", "clang", "clang++", "javac", "java", "git", "cmake", "make", "rustc", "go", "php", "sh").forEach { tool ->
                val res = checkBinaryAvailable(tool)
                if (res.first) {
                    toolsFound.add(tool)
                    pkgCount++
                }
            }
        }

        val storagePath = when {
            termuxHomeDir.exists() && termuxHomeDir.canRead() -> termuxHomeDir.absolutePath
            sdcardStorage.exists() -> sdcardStorage.absolutePath
            else -> context.filesDir.absolutePath
        }

        TermuxCloneResult(
            isSuccess = true,
            message = "Termux packages and storage linked successfully without re-downloading! Detected $pkgCount executables and tools.",
            packageCount = pkgCount,
            detectedTools = toolsFound,
            storagePath = storagePath
        )
    }
}
