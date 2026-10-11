package com.example

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.example.data.repository.*
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TermuxBridgeTest {

    private val context: Context get() = ApplicationProvider.getApplicationContext()

    @Test
    fun `test Termux bridge constants match Termux specification`() {
        assertEquals("com.termux", TermuxBridgeConstants.TERMUX_PACKAGE)
        assertEquals("com.termux.app.RunCommandService", TermuxBridgeConstants.TERMUX_SERVICE_NAME)
        assertEquals("com.termux.RUN_COMMAND", TermuxBridgeConstants.ACTION_RUN_COMMAND)
        assertEquals("com.termux.RUN_COMMAND_PATH", TermuxBridgeConstants.EXTRA_RUN_COMMAND_PATH)
        assertEquals("com.termux.RUN_COMMAND_ARGUMENTS", TermuxBridgeConstants.EXTRA_RUN_COMMAND_ARGUMENTS)
        assertEquals("com.termux.RUN_COMMAND_WORKDIR", TermuxBridgeConstants.EXTRA_RUN_COMMAND_WORKDIR)
        assertEquals("com.termux.RUN_COMMAND_BACKGROUND", TermuxBridgeConstants.EXTRA_RUN_COMMAND_BACKGROUND)
        assertEquals("com.termux.RUN_COMMAND_SESSION_ACTION", TermuxBridgeConstants.EXTRA_RUN_COMMAND_SESSION_ACTION)
        assertEquals("com.termux.RUN_COMMAND_PENDING_INTENT", TermuxBridgeConstants.EXTRA_RUN_COMMAND_PENDING_INTENT)
        assertEquals("com.termux.permission.RUN_COMMAND", TermuxBridgeConstants.PERMISSION_RUN_COMMAND)
    }

    @Test
    fun `test argument-safe command tokenization and quoting`() {
        val bridge = TermuxBridge(context)

        // Simple command
        val tokens1 = bridge.tokenizeCommand("ls -la /sdcard")
        assertEquals(listOf("ls", "-la", "/sdcard"), tokens1)

        // Quoted spaces
        val tokens2 = bridge.tokenizeCommand("python3 \"my script.py\" --arg 'value with spaces'")
        assertEquals(listOf("python3", "my script.py", "--arg", "value with spaces"), tokens2)

        // Escaped characters
        val tokens3 = bridge.tokenizeCommand("echo hello\\ world")
        assertEquals(listOf("echo", "hello world"), tokens3)

        // Safe bash arguments array
        val bashArgs = bridge.createSafeBashArguments("echo 'safe command'")
        assertEquals(2, bashArgs.size)
        assertEquals("-c", bashArgs[0])
        assertEquals("echo 'safe command'", bashArgs[1])
    }

    @Test
    fun `test RUN_COMMAND Intent generation with PendingIntent callback`() {
        val bridge = TermuxBridge(context)
        val request = TermuxCommandRequest(
            command = "echo \"AEROCODE_TERMUX_BRIDGE_OK\" && uname -m",
            workingDir = "/storage/emulated/0/AeroCode/projects/demo",
            inBackground = true,
            executionId = "test-exec-12345"
        )

        val intent = bridge.createRunCommandIntent(request)

        assertNotNull(intent)
        assertEquals(TermuxBridgeConstants.ACTION_RUN_COMMAND, intent.action)
        assertEquals("com.termux", intent.component?.packageName)
        assertEquals("com.termux.app.RunCommandService", intent.component?.className)

        // Check path
        assertEquals(
            TermuxBridgeConstants.TERMUX_BASH_PATH,
            intent.getStringExtra(TermuxBridgeConstants.EXTRA_RUN_COMMAND_PATH)
        )

        // Check arguments array
        val args = intent.getStringArrayExtra(TermuxBridgeConstants.EXTRA_RUN_COMMAND_ARGUMENTS)
        assertNotNull(args)
        assertEquals(2, args!!.size)
        assertEquals("-c", args[0])
        assertEquals("echo \"AEROCODE_TERMUX_BRIDGE_OK\" && uname -m", args[1])

        // Check workdir
        assertEquals("/storage/emulated/0/AeroCode/projects/demo", intent.getStringExtra(TermuxBridgeConstants.EXTRA_RUN_COMMAND_WORKDIR))

        // Check background flag
        assertTrue(intent.getBooleanExtra(TermuxBridgeConstants.EXTRA_RUN_COMMAND_BACKGROUND, false))

        // Check PendingIntent extra
        val pendingIntent = intent.getParcelableExtra<PendingIntent>(TermuxBridgeConstants.EXTRA_RUN_COMMAND_PENDING_INTENT)
        assertNotNull("PendingIntent callback must be attached to RUN_COMMAND intent", pendingIntent)
    }

    @Test
    fun `test project path accessibility check differentiates private sandbox from shared storage`() {
        val bridge = TermuxBridge(context)

        // Private app files dir (Termux cannot access without root)
        val privateDir = File(context.filesDir, "projects/CppProject")
        val privateCheck = bridge.checkDirectoryAccessibility(privateDir)
        assertTrue(
            "Private internal sandbox should be detected as Inaccessible to Termux",
            privateCheck is PathAccessibilityResult.Inaccessible
        )
        if (privateCheck is PathAccessibilityResult.Inaccessible) {
            assertTrue(privateCheck.reason.contains("private app sandbox"))
            assertNotNull(privateCheck.recommendedAlternative)
        }

        // Shared external storage (Accessible to both)
        val sharedDir = File("/storage/emulated/0/AeroCode/projects")
        val sharedCheck = bridge.checkDirectoryAccessibility(sharedDir)
        assertTrue(
            "Shared storage should be detected as Accessible",
            sharedCheck is PathAccessibilityResult.Accessible
        )

        // Termux home directory (Accessible to Termux)
        val termuxHome = File(TermuxBridgeConstants.TERMUX_HOME_DIR)
        val homeCheck = bridge.checkDirectoryAccessibility(termuxHome)
        assertTrue(
            "Termux home should be detected as Accessible",
            homeCheck is PathAccessibilityResult.Accessible
        )
    }

    @Test
    fun `test TermuxResultReceiver broadcasts and dispatcher correlation`() {
        val receiver = TermuxResultReceiver()
        val execId = "exec-test-999"

        val deferred = CompletableDeferred<TermuxCommandResult>()
        TermuxResultDispatcher.register(execId, deferred)

        // Simulate incoming broadcast from Termux
        val incomingIntent = Intent(TermuxBridgeConstants.ACTION_TERMUX_RESULT).apply {
            putExtra(TermuxBridgeConstants.EXTRA_EXECUTION_ID, execId)
            putExtra(TermuxBridgeConstants.EXTRA_RESULT_STDOUT, "AEROCODE_TERMUX_BRIDGE_OK\naarch64\n")
            putExtra(TermuxBridgeConstants.EXTRA_RESULT_STDERR, "")
            putExtra(TermuxBridgeConstants.EXTRA_RESULT_EXIT_CODE, 0)
            putExtra(TermuxBridgeConstants.EXTRA_RESULT_ERR_CODE, 0)
            putExtra(TermuxBridgeConstants.EXTRA_RESULT_ERRMSG, "")
        }

        receiver.onReceive(context, incomingIntent)

        assertTrue("Deferred must be completed by receiver", deferred.isCompleted)
        val result = runBlocking { deferred.await() }

        assertEquals(execId, result.executionId)
        assertTrue(result.isSuccess)
        assertEquals(0, result.exitCode)
        assertTrue(result.stdout.contains("AEROCODE_TERMUX_BRIDGE_OK"))
        assertEquals("", result.stderr)
        assertEquals(0, result.errCode)
    }

    @Test
    fun `test TermuxResultReceiver handles failure and error code from Termux`() {
        val receiver = TermuxResultReceiver()
        val execId = "exec-fail-888"

        val deferred = CompletableDeferred<TermuxCommandResult>()
        TermuxResultDispatcher.register(execId, deferred)

        // Simulate error broadcast from Termux (e.g. command not found or exit code 127)
        val incomingIntent = Intent(TermuxBridgeConstants.ACTION_TERMUX_RESULT).apply {
            putExtra(TermuxBridgeConstants.EXTRA_EXECUTION_ID, execId)
            putExtra(TermuxBridgeConstants.EXTRA_RESULT_STDOUT, "")
            putExtra(TermuxBridgeConstants.EXTRA_RESULT_STDERR, "bash: nonexistent: command not found\n")
            putExtra(TermuxBridgeConstants.EXTRA_RESULT_EXIT_CODE, 127)
            putExtra(TermuxBridgeConstants.EXTRA_RESULT_ERR_CODE, 0)
        }

        receiver.onReceive(context, incomingIntent)

        assertTrue(deferred.isCompleted)
        val result = runBlocking { deferred.await() }

        assertEquals(execId, result.executionId)
        assertFalse(result.isSuccess)
        assertEquals(127, result.exitCode)
        assertTrue(result.stderr.contains("command not found"))
    }

    @Test
    fun `test Termux wrapper script template generation`() {
        val bridge = TermuxBridge(context)
        val script = bridge.getTermuxWrapperScript()

        assertNotNull(script)
        assertTrue(script.contains("#!/data/data/com.termux/files/usr/bin/bash"))
        assertTrue(script.contains("AeroCode Termux Bridge Wrapper"))
        assertTrue(script.contains("WORKDIR="))
        assertTrue(script.contains("exec \"\$@\""))
    }

    @Test
    fun `test environment diagnostics generation`() {
        val bridge = TermuxBridge(context)
        val testDir = File(context.filesDir, "test")
        val diag = bridge.performDiagnostics(testDir)

        assertNotNull(diag)
        assertNotNull(diag.setupInstructions)
        assertTrue(diag.setupInstructions.isNotEmpty())
        assertTrue(diag.setupInstructions.any { it.contains("allow-external-apps = true") })
        assertTrue(diag.setupInstructions.any { it.contains("termux-setup-storage") })
        assertNotNull(diag.commonTools)
        assertTrue(diag.commonTools.any { it.name == "Bash" })
    }

    @Test
    fun `test harmless bridge test command request creation`() = runBlocking {
        val repo = TermuxRepository(context)
        val testDir = File(context.filesDir, "test")

        // When Termux is not installed in the JVM test runner, runBridgeTestCommand
        // must return a clean, non-crashing result explaining Termux is not installed.
        val result = repo.runBridgeTestCommand(testDir)
        assertNotNull(result)
        assertNotNull(result.executionId)
        // Since Termux is not installed in standard Robolectric JVM environment:
        assertFalse(result.isSuccess)
        assertTrue(result.stderr.contains("Termux") || result.errmsg != null)
    }
}
