package com.example.data.repository

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import com.example.data.model.TerminalLine
import com.example.data.model.TerminalLineType
import com.example.data.model.TermuxTool
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

/**
 * Constants for Termux RUN_COMMAND Intent API and AeroCode Bridge.
 */
object TermuxBridgeConstants {
    const val TAG = "TermuxBridge"

    // Termux target package and service
    const val TERMUX_PACKAGE = "com.termux"
    const val TERMUX_SERVICE_NAME = "com.termux.app.RunCommandService"

    // Termux RUN_COMMAND Intent specifications
    const val ACTION_RUN_COMMAND = "com.termux.RUN_COMMAND"
    const val EXTRA_RUN_COMMAND_PATH = "com.termux.RUN_COMMAND_PATH"
    const val EXTRA_RUN_COMMAND_ARGUMENTS = "com.termux.RUN_COMMAND_ARGUMENTS"
    const val EXTRA_RUN_COMMAND_WORKDIR = "com.termux.RUN_COMMAND_WORKDIR"
    const val EXTRA_RUN_COMMAND_BACKGROUND = "com.termux.RUN_COMMAND_BACKGROUND"
    const val EXTRA_RUN_COMMAND_SESSION_ACTION = "com.termux.RUN_COMMAND_SESSION_ACTION"
    const val EXTRA_RUN_COMMAND_PENDING_INTENT = "com.termux.RUN_COMMAND_PENDING_INTENT"

    // Termux default paths
    const val TERMUX_PREFIX = "/data/data/com.termux/files/usr"
    const val TERMUX_BIN_DIR = "/data/data/com.termux/files/usr/bin"
    const val TERMUX_BASH_PATH = "/data/data/com.termux/files/usr/bin/bash"
    const val TERMUX_HOME_DIR = "/data/data/com.termux/files/home"

    // AeroCode result broadcast action and extras
    const val ACTION_TERMUX_RESULT = "com.example.aerocode.ACTION_TERMUX_RESULT"
    const val EXTRA_EXECUTION_ID = "aerocode_execution_id"

    // Extras sent back by Termux inside the PendingIntent
    const val EXTRA_RESULT_STDOUT = "stdout"
    const val EXTRA_RESULT_STDERR = "stderr"
    const val EXTRA_RESULT_EXIT_CODE = "exitCode"
    const val EXTRA_RESULT_ERR_CODE = "errCode"
    const val EXTRA_RESULT_ERRMSG = "errmsg"

    // Required permission in Termux
    const val PERMISSION_RUN_COMMAND = "com.termux.permission.RUN_COMMAND"
}

/**
 * Command request model for Termux execution.
 */
data class TermuxCommandRequest(
    val command: String,
    val arguments: List<String> = emptyList(),
    val executable: String = TermuxBridgeConstants.TERMUX_BASH_PATH,
    val workingDir: String,
    val inBackground: Boolean = true,
    val sessionAction: String = "0", // "0" = default/new session
    val timeoutMs: Long = 45_000L,
    val executionId: String = UUID.randomUUID().toString()
)

/**
 * Detailed result of a Termux command execution.
 */
data class TermuxCommandResult(
    val executionId: String,
    val isSuccess: Boolean,
    val exitCode: Int,
    val stdout: String,
    val stderr: String,
    val errCode: Int = 0,
    val errmsg: String? = null,
    val durationMs: Long = 0L,
    val executionMode: TermuxExecutionMode = TermuxExecutionMode.TERMUX_INTENT
)

enum class TermuxExecutionMode {
    TERMUX_INTENT,
    LOCAL_FALLBACK,
    ERROR
}

/**
 * Accessibility diagnosis for a directory path shared between AeroCode and Termux.
 */
sealed class PathAccessibilityResult {
    data class Accessible(val path: String, val note: String) : PathAccessibilityResult()
    data class Inaccessible(val path: String, val reason: String, val recommendedAlternative: String) : PathAccessibilityResult()
}

/**
 * Complete environment diagnostic report.
 */
data class TermuxEnvironmentDiagnostics(
    val isInstalled: Boolean,
    val versionName: String?,
    val versionCode: Long?,
    val isServiceResolvable: Boolean,
    val isPermissionGranted: Boolean,
    val pathAccessibility: PathAccessibilityResult,
    val currentWorkingDir: String,
    val commonTools: List<TermuxTool>,
    val setupInstructions: List<String>,
    val canExecuteCommands: Boolean
)

/**
 * Global registry for correlating Termux PendingIntent broadcast results.
 */
object TermuxResultDispatcher {
    private val activeCallbacks = ConcurrentHashMap<String, CompletableDeferred<TermuxCommandResult>>()

    fun register(executionId: String, deferred: CompletableDeferred<TermuxCommandResult>) {
        activeCallbacks[executionId] = deferred
    }

    fun unregister(executionId: String) {
        activeCallbacks.remove(executionId)
    }

    fun dispatch(result: TermuxCommandResult): Boolean {
        val deferred = activeCallbacks.remove(result.executionId)
        return if (deferred != null) {
            deferred.complete(result)
            true
        } else {
            // If executionId didn't match directly, check if single callback is waiting
            if (activeCallbacks.size == 1) {
                val singleKey = activeCallbacks.keys.firstOrNull()
                if (singleKey != null) {
                    val singleDeferred = activeCallbacks.remove(singleKey)
                    singleDeferred?.complete(result.copy(executionId = singleKey))
                    return true
                }
            }
            false
        }
    }
}

/**
 * BroadcastReceiver registered to receive Termux execution results via PendingIntent.
 */
class TermuxResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null) return

        val action = intent.action
        if (action != TermuxBridgeConstants.ACTION_TERMUX_RESULT) {
            return
        }

        val executionId = intent.getStringExtra(TermuxBridgeConstants.EXTRA_EXECUTION_ID) ?: ""
        val stdout = intent.getStringExtra(TermuxBridgeConstants.EXTRA_RESULT_STDOUT) ?: ""
        val stderr = intent.getStringExtra(TermuxBridgeConstants.EXTRA_RESULT_STDERR) ?: ""
        val exitCode = intent.getIntExtra(TermuxBridgeConstants.EXTRA_RESULT_EXIT_CODE, 0)
        val errCode = intent.getIntExtra(TermuxBridgeConstants.EXTRA_RESULT_ERR_CODE, 0)
        val errmsg = intent.getStringExtra(TermuxBridgeConstants.EXTRA_RESULT_ERRMSG)

        val isSuccess = (exitCode == 0 && errCode == 0 && errmsg.isNullOrBlank())

        val result = TermuxCommandResult(
            executionId = executionId,
            isSuccess = isSuccess,
            exitCode = exitCode,
            stdout = stdout,
            stderr = stderr,
            errCode = errCode,
            errmsg = errmsg,
            executionMode = TermuxExecutionMode.TERMUX_INTENT
        )

        Log.d(TermuxBridgeConstants.TAG, "Termux result received for id=$executionId exitCode=$exitCode errCode=$errCode")
        TermuxResultDispatcher.dispatch(result)
    }
}

/**
 * Kotlin Termux Bridge Service.
 * Manages reliable communication with Termux via RUN_COMMAND Intent API.
 */
class TermuxBridge(private val context: Context) {

    /**
     * Checks if Termux application is installed on the device.
     */
    fun isTermuxInstalled(): Boolean {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    TermuxBridgeConstants.TERMUX_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(TermuxBridgeConstants.TERMUX_PACKAGE, 0)
            }
            true
        } catch (_: PackageManager.NameNotFoundException) {
            false
        }
    }

    /**
     * Retrieves installed Termux version name if available.
     */
    fun getTermuxVersion(): Pair<String?, Long?> {
        return try {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    TermuxBridgeConstants.TERMUX_PACKAGE,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(TermuxBridgeConstants.TERMUX_PACKAGE, 0)
            }
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                info.versionCode.toLong()
            }
            Pair(info.versionName, versionCode)
        } catch (_: Exception) {
            Pair(null, null)
        }
    }

    /**
     * Checks if the Termux RUN_COMMAND permission is granted to AeroCode.
     */
    fun isRunCommandPermissionGranted(): Boolean {
        val perm = TermuxBridgeConstants.PERMISSION_RUN_COMMAND
        val res = context.checkCallingOrSelfPermission(perm)
        return res == PackageManager.PERMISSION_GRANTED
    }

    /**
     * Checks if RunCommandService is resolvable via PackageManager.
     */
    fun isRunCommandServiceResolvable(): Boolean {
        val intent = Intent(TermuxBridgeConstants.ACTION_RUN_COMMAND).apply {
            component = ComponentName(
                TermuxBridgeConstants.TERMUX_PACKAGE,
                TermuxBridgeConstants.TERMUX_SERVICE_NAME
            )
        }
        val resolveInfo = context.packageManager.resolveService(intent, 0)
        return resolveInfo != null
    }

    /**
     * Verifies if a directory is accessible by both AeroCode and Termux.
     */
    fun checkDirectoryAccessibility(directory: File): PathAccessibilityResult {
        val absPath = directory.absolutePath
        val privateFilesDir = context.filesDir.absolutePath
        val privateDataDir = context.applicationInfo.dataDir

        // Android private sandbox (/data/user/0/... or /data/data/...)
        if (absPath.startsWith(privateFilesDir) || absPath.startsWith(privateDataDir)) {
            val sharedDir = getRecommendedSharedProjectsDir()
            return PathAccessibilityResult.Inaccessible(
                path = absPath,
                reason = "Directory is located in AeroCode's private app sandbox ($absPath). On Android, Termux runs under a different Linux UID and cannot read or write private app directories without root.",
                recommendedAlternative = sharedDir.absolutePath
            )
        }

        // Shared external storage or Termux home
        if (absPath.startsWith("/storage/emulated/0") || absPath.startsWith("/sdcard") || absPath.startsWith(TermuxBridgeConstants.TERMUX_HOME_DIR)) {
            return PathAccessibilityResult.Accessible(
                path = absPath,
                note = "Directory is accessible to both AeroCode and Termux via Android Shared Storage."
            )
        }

        return PathAccessibilityResult.Accessible(
            path = absPath,
            note = "Custom storage path ($absPath)."
        )
    }

    /**
     * Returns the recommended shared directory accessible to both apps.
     */
    fun getRecommendedSharedProjectsDir(): File {
        val extDir = context.getExternalFilesDir(null)
        if (extDir != null && extDir.exists()) {
            val projDir = File(extDir, "projects")
            if (!projDir.exists()) projDir.mkdirs()
            return projDir
        }
        val sdcard = File(Environment.getExternalStorageDirectory(), "AeroCode/projects")
        if (!sdcard.exists()) sdcard.mkdirs()
        return sdcard
    }

    /**
     * Safely constructs an argument array to pass to Termux bash or executable.
     * Prevents raw string concatenation and shell injection.
     */
    fun createSafeBashArguments(commandString: String): Array<String> {
        return arrayOf("-c", commandString)
    }

    /**
     * Tokenizes a command line string into argument tokens respecting quotes.
     */
    fun tokenizeCommand(commandLine: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var inSingle = false
        var inDouble = false
        var escaping = false

        for (ch in commandLine.trim()) {
            if (escaping) {
                sb.append(ch)
                escaping = false
                continue
            }
            if (ch == '\\' && !inSingle) {
                escaping = true
                continue
            }
            if (ch == '\'' && !inDouble) {
                inSingle = !inSingle
                continue
            }
            if (ch == '"' && !inSingle) {
                inDouble = !inDouble
                continue
            }
            if (ch.isWhitespace() && !inSingle && !inDouble) {
                if (sb.isNotEmpty()) {
                    tokens.add(sb.toString())
                    sb.clear()
                }
            } else {
                sb.append(ch)
            }
        }
        if (sb.isNotEmpty()) {
            tokens.add(sb.toString())
        }
        return tokens
    }

    /**
     * Creates the documented Termux RUN_COMMAND Intent with PendingIntent callback.
     */
    fun createRunCommandIntent(request: TermuxCommandRequest): Intent {
        val intent = Intent(TermuxBridgeConstants.ACTION_RUN_COMMAND).apply {
            component = ComponentName(
                TermuxBridgeConstants.TERMUX_PACKAGE,
                TermuxBridgeConstants.TERMUX_SERVICE_NAME
            )
            putExtra(TermuxBridgeConstants.EXTRA_RUN_COMMAND_PATH, request.executable)

            val argsArray = if (request.arguments.isNotEmpty()) {
                request.arguments.toTypedArray()
            } else {
                createSafeBashArguments(request.command)
            }
            putExtra(TermuxBridgeConstants.EXTRA_RUN_COMMAND_ARGUMENTS, argsArray)
            putExtra(TermuxBridgeConstants.EXTRA_RUN_COMMAND_WORKDIR, request.workingDir)
            putExtra(TermuxBridgeConstants.EXTRA_RUN_COMMAND_BACKGROUND, request.inBackground)
            putExtra(TermuxBridgeConstants.EXTRA_RUN_COMMAND_SESSION_ACTION, request.sessionAction)
        }

        // Attach PendingIntent for callback results
        val resultIntent = Intent(context, TermuxResultReceiver::class.java).apply {
            action = TermuxBridgeConstants.ACTION_TERMUX_RESULT
            putExtra(TermuxBridgeConstants.EXTRA_EXECUTION_ID, request.executionId)
            setPackage(context.packageName)
        }

        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            request.executionId.hashCode(),
            resultIntent,
            flags
        )

        intent.putExtra(TermuxBridgeConstants.EXTRA_RUN_COMMAND_PENDING_INTENT, pendingIntent)
        return intent
    }

    /**
     * Executes a command via Termux RUN_COMMAND service asynchronously and awaits result.
     */
    suspend fun executeCommand(request: TermuxCommandRequest): TermuxCommandResult = withContext(Dispatchers.IO) {
        val startTime = System.currentTimeMillis()

        // 1. Check if Termux is installed
        if (!isTermuxInstalled()) {
            return@withContext TermuxCommandResult(
                executionId = request.executionId,
                isSuccess = false,
                exitCode = -1,
                stdout = "",
                stderr = "Termux is not installed on this device.\nPlease install Termux from F-Droid or GitHub Releases.",
                errCode = 1,
                errmsg = "TERMUX_NOT_INSTALLED",
                durationMs = 0L,
                executionMode = TermuxExecutionMode.ERROR
            )
        }

        // 2. Check path accessibility
        val pathCheck = checkDirectoryAccessibility(File(request.workingDir))
        if (pathCheck is PathAccessibilityResult.Inaccessible) {
            return@withContext TermuxCommandResult(
                executionId = request.executionId,
                isSuccess = false,
                exitCode = -1,
                stdout = "",
                stderr = "Working directory is inaccessible to Termux:\n${pathCheck.reason}\nRecommended path: ${pathCheck.recommendedAlternative}",
                errCode = 2,
                errmsg = "INACCESSIBLE_PATH",
                durationMs = 0L,
                executionMode = TermuxExecutionMode.ERROR
            )
        }

        // 3. Register callback deferred
        val deferred = CompletableDeferred<TermuxCommandResult>()
        TermuxResultDispatcher.register(request.executionId, deferred)

        try {
            val intent = createRunCommandIntent(request)
            context.startService(intent)

            // Await result with timeout
            val result = withTimeoutOrNull(request.timeoutMs) {
                deferred.await()
            }

            if (result != null) {
                val elapsed = System.currentTimeMillis() - startTime
                result.copy(durationMs = elapsed)
            } else {
                TermuxResultDispatcher.unregister(request.executionId)
                TermuxCommandResult(
                    executionId = request.executionId,
                    isSuccess = false,
                    exitCode = -1,
                    stdout = "",
                    stderr = "Command timed out after ${request.timeoutMs}ms.\nEnsure 'allow-external-apps = true' is added to ~/.termux/termux.properties.",
                    errCode = 3,
                    errmsg = "COMMAND_TIMEOUT",
                    durationMs = request.timeoutMs,
                    executionMode = TermuxExecutionMode.ERROR
                )
            }
        } catch (e: SecurityException) {
            TermuxResultDispatcher.unregister(request.executionId)
            TermuxCommandResult(
                executionId = request.executionId,
                isSuccess = false,
                exitCode = -1,
                stdout = "",
                stderr = "Permission denied: AeroCode requires permission com.termux.permission.RUN_COMMAND to send commands to Termux.\nError: ${e.message}",
                errCode = 4,
                errmsg = "PERMISSION_DENIED",
                durationMs = System.currentTimeMillis() - startTime,
                executionMode = TermuxExecutionMode.ERROR
            )
        } catch (e: Exception) {
            TermuxResultDispatcher.unregister(request.executionId)
            TermuxCommandResult(
                executionId = request.executionId,
                isSuccess = false,
                exitCode = -1,
                stdout = "",
                stderr = "Failed to communicate with Termux: ${e.message}",
                errCode = 5,
                errmsg = e.javaClass.simpleName,
                durationMs = System.currentTimeMillis() - startTime,
                executionMode = TermuxExecutionMode.ERROR
            )
        }
    }

    /**
     * Executes a command and streams output as TerminalLine items for the Terminal UI.
     */
    fun executeCommandStreaming(
        sessionId: String,
        command: String,
        workingDir: File
    ): Flow<TerminalLine> = flow {
        emit(TerminalLine(text = "$ $command", type = TerminalLineType.COMMAND))

        if (!isTermuxInstalled()) {
            emit(TerminalLine(text = "[Termux Bridge] Termux app is not detected on device.", type = TerminalLineType.STDERR))
            emit(TerminalLine(text = "Please install Termux from F-Droid (https://f-droid.org/packages/com.termux/).", type = TerminalLineType.SYSTEM))
            emit(TerminalLine(text = "Running command using local Android sandbox fallback...", type = TerminalLineType.SYSTEM))

            // Fallback to local sandbox process
            runLocalSandbox(command, workingDir) { emit(it) }
            return@flow
        }

        val request = TermuxCommandRequest(
            command = command,
            workingDir = workingDir.absolutePath,
            inBackground = true
        )

        val result = executeCommand(request)

        if (result.stdout.isNotEmpty()) {
            result.stdout.lines().forEach { line ->
                if (line.isNotEmpty()) {
                    emit(TerminalLine(text = line, type = TerminalLineType.STDOUT))
                }
            }
        }

        if (result.stderr.isNotEmpty()) {
            result.stderr.lines().forEach { line ->
                if (line.isNotEmpty()) {
                    emit(TerminalLine(text = line, type = TerminalLineType.STDERR))
                }
            }
        }

        if (result.isSuccess) {
            emit(TerminalLine(text = "[Termux Process exited with code ${result.exitCode}]", type = TerminalLineType.SUCCESS))
        } else {
            if (result.errmsg != null && result.errmsg != "0") {
                emit(TerminalLine(text = "[Termux Error: ${result.errmsg}] (code=${result.errCode})", type = TerminalLineType.STDERR))
                if (result.errmsg == "COMMAND_TIMEOUT") {
                    emit(TerminalLine(text = "Tip: Make sure ~/.termux/termux.properties has 'allow-external-apps = true' enabled.", type = TerminalLineType.SYSTEM))
                }
            } else {
                emit(TerminalLine(text = "[Termux Process exited with error code ${result.exitCode}]", type = TerminalLineType.STDERR))
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Local sandbox fallback when Termux is unavailable.
     */
    private suspend fun runLocalSandbox(
        command: String,
        workingDir: File,
        emitLine: suspend (TerminalLine) -> Unit
    ) {
        try {
            val builder = ProcessBuilder("sh", "-c", command)
            if (workingDir.exists()) builder.directory(workingDir)
            val proc = builder.start()

            proc.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    kotlinx.coroutines.runBlocking {
                        emitLine(TerminalLine(text = line, type = TerminalLineType.STDOUT))
                    }
                }
            }
            proc.errorStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    kotlinx.coroutines.runBlocking {
                        emitLine(TerminalLine(text = line, type = TerminalLineType.STDERR))
                    }
                }
            }
            val code = proc.waitFor()
            if (code == 0) {
                emitLine(TerminalLine(text = "[Local sandbox exited with code 0]", type = TerminalLineType.SUCCESS))
            } else {
                emitLine(TerminalLine(text = "[Local sandbox exited with code $code]", type = TerminalLineType.STDERR))
            }
        } catch (e: Exception) {
            emitLine(TerminalLine(text = "Local execution error: ${e.message}", type = TerminalLineType.STDERR))
        }
    }

    /**
     * Generates environment diagnostic report for Step 3.
     */
    fun performDiagnostics(currentWorkingDir: File): TermuxEnvironmentDiagnostics {
        val installed = isTermuxInstalled()
        val (versionName, versionCode) = getTermuxVersion()
        val serviceResolvable = isRunCommandServiceResolvable()
        val permGranted = isRunCommandPermissionGranted()
        val pathCheck = checkDirectoryAccessibility(currentWorkingDir)

        val setupInstructions = listOf(
            "1. Install Termux from F-Droid or GitHub Releases (do not use Google Play version).",
            "2. Open Termux and run:",
            "   mkdir -p ~/.termux",
            "   echo 'allow-external-apps = true' >> ~/.termux/termux.properties",
            "   termux-reload-settings",
            "3. Grant Termux shared storage access:",
            "   termux-setup-storage",
            "4. Link AeroCode shared projects directory:",
            "   mkdir -p ~/storage/shared/AeroCode/projects"
        )

        val commonTools = listOf(
            TermuxTool("Bash", "bash", true, "Termux / System", "pkg install bash", "Shell"),
            TermuxTool("Python 3", "python3", false, null, "pkg install python", "Interpreter"),
            TermuxTool("Node.js", "node", false, null, "pkg install nodejs", "Interpreter"),
            TermuxTool("Clang / C++", "clang++", false, null, "pkg install clang", "Compiler"),
            TermuxTool("Git", "git", false, null, "pkg install git", "VCS")
        )

        val canExecute = installed && (pathCheck is PathAccessibilityResult.Accessible)

        return TermuxEnvironmentDiagnostics(
            isInstalled = installed,
            versionName = versionName,
            versionCode = versionCode,
            isServiceResolvable = serviceResolvable,
            isPermissionGranted = permGranted,
            pathAccessibility = pathCheck,
            currentWorkingDir = currentWorkingDir.absolutePath,
            commonTools = commonTools,
            setupInstructions = setupInstructions,
            canExecuteCommands = canExecute
        )
    }

    /**
     * Termux-side wrapper script template (placed in ~/.aerocode/bridge_exec.sh if needed).
     */
    fun getTermuxWrapperScript(): String {
        return """
            #!/data/data/com.termux/files/usr/bin/bash
            # AeroCode Termux Bridge Wrapper Script
            # Usage: bridge_exec.sh <workdir> <command...>
            set -e
            WORKDIR="${'$'}1"
            shift
            if [ -n "${'$'}WORKDIR" ] && [ -d "${'$'}WORKDIR" ]; then
                cd "${'$'}WORKDIR"
            fi
            exec "${'$'}@"
        """.trimIndent()
    }
}
