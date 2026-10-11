package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.model.AiMessage
import com.example.data.model.AiSender
import com.example.data.model.CodeDiff
import com.example.data.model.CommandItem
import com.example.data.model.EditorTab
import com.example.data.model.IdeTheme
import com.example.data.model.Language
import com.example.data.model.Problem
import com.example.data.model.ProblemSeverity
import com.example.data.model.ProjectItem
import com.example.data.model.TerminalLine
import com.example.data.model.TerminalLineType
import com.example.data.model.TerminalSession
import com.example.data.model.TermuxTool
import com.example.data.repository.AiRepository
import com.example.data.repository.GitRepository
import com.example.data.repository.PluginRepository
import com.example.data.repository.ProjectRepository
import com.example.data.repository.TermuxRepository
import com.example.data.repository.TermuxEnvironmentDiagnostics
import com.example.data.repository.TermuxCommandResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.UUID

enum class BottomDockTab {
    TERMINAL, OUTPUT, PROBLEMS, AI, GIT
}

enum class ActiveModal {
    NONE,
    COMMAND_PALETTE,
    FIND_REPLACE,
    GO_TO_LINE,
    TERMUX_ENVIRONMENT,
    SETTINGS,
    PLUGINS,
    WEB_DOCS,
    NEW_PROJECT,
    NEW_FILE,
    DANGEROUS_CMD_CONFIRM
}

class IdeViewModel(application: Application) : AndroidViewModel(application) {

    val projectRepo = ProjectRepository(application)
    val termuxRepo = TermuxRepository(application)
    val aiRepo = AiRepository(projectRepo)
    val gitRepo = GitRepository(projectRepo, termuxRepo)
    val pluginRepo = PluginRepository()

    // Projects
    private val _projects = MutableStateFlow<List<String>>(emptyList())
    val projects: StateFlow<List<String>> = _projects.asStateFlow()

    private val _currentProjectName = MutableStateFlow("WebPreview")
    val currentProjectName: StateFlow<String> = _currentProjectName.asStateFlow()

    private val _projectFiles = MutableStateFlow<List<ProjectItem>>(emptyList())
    val projectFiles: StateFlow<List<ProjectItem>> = _projectFiles.asStateFlow()

    // Editor Tabs
    private val _tabs = MutableStateFlow<List<EditorTab>>(emptyList())
    val tabs: StateFlow<List<EditorTab>> = _tabs.asStateFlow()

    private val _activeTabId = MutableStateFlow<String?>(null)
    val activeTabId: StateFlow<String?> = _activeTabId.asStateFlow()

    val activeTab: EditorTab?
        get() = _tabs.value.firstOrNull { it.id == _activeTabId.value }

    // Undo / Redo history map
    private val undoStacks = mutableMapOf<String, ArrayDeque<String>>()
    private val redoStacks = mutableMapOf<String, ArrayDeque<String>>()

    private val prefs = application.getSharedPreferences("aerocode_ide_settings", android.content.Context.MODE_PRIVATE)

    // Editor Settings
    val theme = MutableStateFlow(
        try {
            IdeTheme.valueOf(prefs.getString("theme", IdeTheme.CURSOR_DARK.name) ?: IdeTheme.CURSOR_DARK.name)
        } catch (_: Exception) {
            IdeTheme.CURSOR_DARK
        }
    )
    val fontSize = MutableStateFlow(prefs.getFloat("fontSize", 14f).coerceIn(10f, 32f))
    val fontFamilyName = MutableStateFlow(prefs.getString("fontFamilyName", "JetBrains Mono") ?: "JetBrains Mono")
    val isWordWrapEnabled = MutableStateFlow(prefs.getBoolean("isWordWrapEnabled", false))
    val isAutoSaveEnabled = MutableStateFlow(prefs.getBoolean("isAutoSaveEnabled", true))
    val showLineNumbers = MutableStateFlow(prefs.getBoolean("showLineNumbers", true))

    fun updateTheme(newTheme: IdeTheme) {
        theme.value = newTheme
        prefs.edit().putString("theme", newTheme.name).apply()
    }

    fun updateFontFamily(fontName: String) {
        fontFamilyName.value = fontName
        prefs.edit().putString("fontFamilyName", fontName).apply()
    }

    fun updateFontSize(newSize: Float) {
        val clamped = newSize.coerceIn(10f, 32f)
        fontSize.value = clamped
        prefs.edit().putFloat("fontSize", clamped).apply()
    }

    fun adjustFontSizeByZoom(zoomFactor: Float) {
        val current = fontSize.value
        val updated = (current * zoomFactor).coerceIn(10f, 32f)
        if (kotlin.math.abs(updated - current) >= 0.2f) {
            fontSize.value = updated
            prefs.edit().putFloat("fontSize", updated).apply()
        }
    }

    // Bottom Dock (Terminal / Output / Problems / AI / Git)
    val isBottomDockExpanded = MutableStateFlow(false)
    val activeDockTab = MutableStateFlow(BottomDockTab.TERMINAL)
    val terminalHeightFraction = MutableStateFlow(0.45f) // Adjustable terminal height fraction

    // Full Screen Web Preview Window (for HTML/CSS/JS)
    val isFullScreenWebPreview = MutableStateFlow(false)

    // Terminal Sessions
    private val _terminalSessions = MutableStateFlow<List<TerminalSession>>(
        listOf(
            TerminalSession(
                id = "term-1",
                title = "bash 1",
                lines = listOf(
                    TerminalLine(text = "=== AeroCode Integrated Terminal ===", type = TerminalLineType.SYSTEM),
                    TerminalLine(text = "Linux/Android environment initialized.", type = TerminalLineType.SYSTEM)
                )
            )
        )
    )
    val terminalSessions: StateFlow<List<TerminalSession>> = _terminalSessions.asStateFlow()

    val activeTerminalSessionId = MutableStateFlow("term-1")
    val terminalCommandInput = MutableStateFlow("")
    val terminalHistory = mutableListOf<String>()
    var terminalHistoryIndex = -1

    // Termux Tools
    private val _termuxTools = MutableStateFlow<List<TermuxTool>>(emptyList())
    val termuxTools: StateFlow<List<TermuxTool>> = _termuxTools.asStateFlow()
    val isTermuxInstalled = MutableStateFlow(false)

    // Problems Panel
    private val _problems = MutableStateFlow<List<Problem>>(emptyList())
    val problems: StateFlow<List<Problem>> = _problems.asStateFlow()

    // Local Qwen 0.5B AI State
    private val _aiMessages = MutableStateFlow<List<AiMessage>>(
        listOf(
            AiMessage(
                id = "ai-intro",
                sender = AiSender.QWEN,
                text = "Hello! I am **Cursor AI**, your intelligent coding companion.\n\nI can generate code, refactor functions, edit inline (Cmd+K), and apply live diffs. How can I help you today?"
            )
        )
    )
    val aiMessages: StateFlow<List<AiMessage>> = _aiMessages.asStateFlow()
    val isAiGenerating = MutableStateFlow(false)
    val activePendingDiff = MutableStateFlow<CodeDiff?>(null)

    // Cursor AI Editor States (Cmd+K / Inline Edit & Copilot)
    val isCursorInlinePromptOpen = MutableStateFlow(false)
    val cursorInlinePromptText = MutableStateFlow("")
    val isCursorGenerating = MutableStateFlow(false)
    val cursorInlineDiff = MutableStateFlow<CodeDiff?>(null)
    val cursorGhostText = MutableStateFlow<String?>(null)

    fun toggleCursorInlinePrompt() {
        isCursorInlinePromptOpen.value = !isCursorInlinePromptOpen.value
        if (!isCursorInlinePromptOpen.value) {
            cursorInlineDiff.value = null
        }
    }

    fun openCursorInlinePrompt(initialPrompt: String = "") {
        cursorInlinePromptText.value = initialPrompt
        isCursorInlinePromptOpen.value = true
    }

    fun closeCursorInlinePrompt() {
        isCursorInlinePromptOpen.value = false
        cursorInlineDiff.value = null
    }

    fun submitCursorInlineEdit(instruction: String) {
        val tab = activeTab ?: return
        if (instruction.isBlank()) return
        isCursorGenerating.value = true
        viewModelScope.launch {
            try {
                val diff = aiRepo.generateCursorInlineEdit(
                    instruction = instruction,
                    currentCode = tab.content,
                    filePath = tab.filePath
                )
                cursorInlineDiff.value = diff
            } catch (_: Exception) {
            } finally {
                isCursorGenerating.value = false
            }
        }
    }

    fun acceptCursorInlineDiff() {
        val diff = cursorInlineDiff.value ?: return
        applyPendingDiff(diff)
        cursorInlineDiff.value = null
        isCursorInlinePromptOpen.value = false
        cursorInlinePromptText.value = ""
    }

    fun rejectCursorInlineDiff() {
        cursorInlineDiff.value = null
    }

    val activeCursorOffset = MutableStateFlow(0)
    val activeCursorLine = MutableStateFlow(1)
    val activeCursorCol = MutableStateFlow(1)

    fun onEditorTextCursorChanged(code: String, cursorOffset: Int) {
        val safeOffset = cursorOffset.coerceIn(0, code.length)
        activeCursorOffset.value = safeOffset
        val textBefore = code.take(safeOffset)
        activeCursorLine.value = textBefore.count { it == '\n' } + 1
        val lastNewline = textBefore.lastIndexOf('\n')
        activeCursorCol.value = if (lastNewline >= 0) safeOffset - lastNewline else safeOffset + 1

        val suggestion = aiRepo.getCursorCopilotSuggestion(code, safeOffset)
        cursorGhostText.value = suggestion
    }

    fun insertTextAtCursor(token: String) {
        val tab = activeTab ?: return
        val currentContent = tab.content
        val offset = activeCursorOffset.value.coerceIn(0, currentContent.length)
        val inserted = if (token == "\t") "  " else token
        val newContent = currentContent.substring(0, offset) + inserted + currentContent.substring(offset)
        val newOffset = offset + inserted.length
        activeCursorOffset.value = newOffset
        onEditorContentChange(newContent)
        onEditorTextCursorChanged(newContent, newOffset)
    }

    fun acceptCursorGhostText() {
        val ghost = cursorGhostText.value ?: return
        insertTextAtCursor(ghost)
        cursorGhostText.value = null
    }

    fun dismissCursorGhostText() {
        cursorGhostText.value = null
    }

    // Modals
    val activeModal = MutableStateFlow(ActiveModal.NONE)
    val pendingDangerousCommand = MutableStateFlow<String?>(null)

    // Find & Replace
    val searchQuery = MutableStateFlow("")
    val replaceQuery = MutableStateFlow("")

    // Running Execution Job
    private var runJob: Job? = null

    init {
        refreshProjects()
        refreshTermuxState()
        // Open default file
        openInitialFile()
    }

    private fun openInitialFile() {
        val projDir = projectRepo.getProjectDirectory(_currentProjectName.value)
        val styleFile = File(projDir, "Style.css")
        val inputFile = File(projDir, "Input.js")
        val indexFile = File(projDir, "Index.html")

        if (styleFile.exists()) openFile(styleFile.absolutePath)
        if (inputFile.exists()) openFile(inputFile.absolutePath)
        if (indexFile.exists()) openFile(indexFile.absolutePath)

        // Focus Input.js tab to match the screenshot
        val inputTab = _tabs.value.firstOrNull { it.filePath == inputFile.absolutePath }
        if (inputTab != null) {
            _activeTabId.value = inputTab.id
        } else if (_tabs.value.isNotEmpty()) {
            _activeTabId.value = _tabs.value.first().id
        }
    }

    fun refreshProjects() {
        val list = projectRepo.getProjects()
        _projects.value = list
        refreshProjectTree()
    }

    fun refreshProjectTree() {
        val dir = projectRepo.getProjectDirectory(_currentProjectName.value)
        _projectFiles.value = projectRepo.listProjectFiles(dir, showHidden = false)
    }

    fun switchProject(name: String) {
        _currentProjectName.value = name
        refreshProjectTree()
        val dir = projectRepo.getProjectDirectory(name)
        val firstFile = dir.walkTopDown().firstOrNull { it.isFile && !it.name.startsWith(".") }
        if (firstFile != null) {
            openFile(firstFile.absolutePath)
        }
    }

    fun createProject(name: String, language: Language) {
        projectRepo.createNewProject(name, language)
        refreshProjects()
        switchProject(name)
    }

    fun openFile(path: String) {
        val existing = _tabs.value.firstOrNull { it.filePath == path }
        if (existing != null) {
            _activeTabId.value = existing.id
            return
        }

        val content = projectRepo.readFile(path)
        val file = File(path)
        val tab = EditorTab(
            id = UUID.randomUUID().toString(),
            title = file.name,
            filePath = path,
            content = content,
            isDirty = false,
            language = Language.detect(path)
        )

        undoStacks[tab.id] = ArrayDeque()
        redoStacks[tab.id] = ArrayDeque()

        _tabs.value = _tabs.value + tab
        _activeTabId.value = tab.id
    }

    fun closeTab(tabId: String) {
        val currentTabs = _tabs.value
        val closingIdx = currentTabs.indexOfFirst { it.id == tabId }
        val remaining = currentTabs.filter { it.id != tabId }
        _tabs.value = remaining

        if (_activeTabId.value == tabId) {
            _activeTabId.value = when {
                remaining.isEmpty() -> null
                closingIdx >= remaining.size -> remaining.last().id
                else -> remaining[closingIdx].id
            }
        }
    }

    fun closeOtherTabs(tabId: String) {
        _tabs.value = _tabs.value.filter { it.id == tabId }
        _activeTabId.value = tabId
    }

    fun closeAllTabs() {
        _tabs.value = emptyList()
        _activeTabId.value = null
    }

    fun onEditorContentChange(newText: String) {
        val tab = activeTab ?: return
        if (tab.content == newText) return

        // Push to undo stack
        val uStack = undoStacks.getOrPut(tab.id) { ArrayDeque() }
        if (uStack.size > 50) uStack.removeFirst()
        uStack.addLast(tab.content)
        redoStacks[tab.id]?.clear()

        _tabs.value = _tabs.value.map {
            if (it.id == tab.id) it.copy(content = newText, isDirty = true) else it
        }

        refreshGit()

        if (isAutoSaveEnabled.value) {
            saveCurrentFile()
        }
    }

    fun undo() {
        val tab = activeTab ?: return
        val uStack = undoStacks[tab.id] ?: return
        if (uStack.isNotEmpty()) {
            val prev = uStack.removeLast()
            val rStack = redoStacks.getOrPut(tab.id) { ArrayDeque() }
            rStack.addLast(tab.content)
            _tabs.value = _tabs.value.map {
                if (it.id == tab.id) it.copy(content = prev, isDirty = true) else it
            }
        }
    }

    fun redo() {
        val tab = activeTab ?: return
        val rStack = redoStacks[tab.id] ?: return
        if (rStack.isNotEmpty()) {
            val next = rStack.removeLast()
            val uStack = undoStacks.getOrPut(tab.id) { ArrayDeque() }
            uStack.addLast(tab.content)
            _tabs.value = _tabs.value.map {
                if (it.id == tab.id) it.copy(content = next, isDirty = true) else it
            }
        }
    }

    fun saveCurrentFile() {
        val tab = activeTab ?: return
        projectRepo.writeFile(tab.filePath, tab.content)
        _tabs.value = _tabs.value.map {
            if (it.id == tab.id) it.copy(isDirty = false) else it
        }
    }

    fun saveAllFiles() {
        _tabs.value.forEach { tab ->
            if (tab.isDirty) {
                projectRepo.writeFile(tab.filePath, tab.content)
            }
        }
        _tabs.value = _tabs.value.map { it.copy(isDirty = false) }
    }

    // Unified RUN system
    fun runProjectOrCurrentFile() {
        val tab = activeTab
        val projDir = projectRepo.getProjectDirectory(_currentProjectName.value)

        isBottomDockExpanded.value = true
        activeDockTab.value = BottomDockTab.TERMINAL

        val (command, title) = determineRunCommand(tab, projDir)

        if (command == "PREVIEW_WEB") {
            isFullScreenWebPreview.value = true
            return
        }

        executeTerminalCommand(command, isBuild = true)
    }

    private fun determineRunCommand(tab: EditorTab?, projDir: File): Pair<String, String> {
        // 1. Check project level build systems
        if (File(projDir, "CMakeLists.txt").exists()) {
            return Pair("cmake -B build && cmake --build build && ./build/${projDir.name}", "CMake Build & Run")
        }
        if (File(projDir, "Makefile").exists()) {
            return Pair("make && ./main", "Make Build & Run")
        }
        if (File(projDir, "package.json").exists()) {
            return Pair("npm test || node index.js", "Node.js Run")
        }
        if (File(projDir, "Cargo.toml").exists()) {
            return Pair("cargo run", "Cargo Run")
        }

        // 2. Active file execution
        if (tab != null) {
            val file = File(tab.filePath)
            val base = file.nameWithoutExtension
            val cmd = when (tab.language) {
                Language.CPP -> "clang++ -O2 \"${file.name}\" -o \"$base\" && ./\"$base\""
                Language.C -> "clang -O2 \"${file.name}\" -o \"$base\" && ./\"$base\""
                Language.PYTHON -> "python3 \"${file.name}\""
                Language.JAVA -> "javac \"${file.name}\" && java \"$base\""
                Language.KOTLIN -> "kotlinc \"${file.name}\" -include-runtime -d \"$base.jar\" && java -jar \"$base.jar\""
                Language.RUST -> "rustc \"${file.name}\" -o \"$base\" && ./\"$base\""
                Language.GO -> "go run \"${file.name}\""
                Language.SHELL -> "bash \"${file.name}\""
                Language.HTML, Language.CSS, Language.JAVASCRIPT, Language.TYPESCRIPT, Language.MARKDOWN -> "PREVIEW_WEB"
                else -> "echo 'No run configuration for ${tab.language.displayName}'"
            }
            return Pair(cmd, "${tab.language.displayName} Run")
        }

        return Pair("echo 'No active file or project'", "Default")
    }

    // Terminal
    fun executeTerminalCommand(cmd: String, isBuild: Boolean = false) {
        val trimmed = cmd.trim()
        if (trimmed.isEmpty()) return

        if (termuxRepo.isDangerousCommand(trimmed)) {
            pendingDangerousCommand.value = trimmed
            activeModal.value = ActiveModal.DANGEROUS_CMD_CONFIRM
            return
        }

        terminalHistory.add(trimmed)
        terminalHistoryIndex = terminalHistory.size

        val session = _terminalSessions.value.firstOrNull { it.id == activeTerminalSessionId.value }
            ?: _terminalSessions.value.first()

        val projDir = projectRepo.getProjectDirectory(_currentProjectName.value)

        runJob?.cancel()
        runJob = viewModelScope.launch {
            _terminalSessions.value = _terminalSessions.value.map {
                if (it.id == session.id) it.copy(isRunning = true) else it
            }

            val capturedErrors = mutableListOf<Problem>()

            termuxRepo.executeCommand(session.id, trimmed, projDir).collect { line ->
                appendTerminalLine(session.id, line)

                // Parse compiler errors on-the-fly for Problems panel
                if (line.type == TerminalLineType.STDERR) {
                    val parsedProb = parseCompilerError(line.text, activeTab?.filePath ?: "")
                    if (parsedProb != null) {
                        capturedErrors.add(parsedProb)
                    }
                }
            }

            _terminalSessions.value = _terminalSessions.value.map {
                if (it.id == session.id) it.copy(isRunning = false) else it
            }

            if (isBuild && capturedErrors.isNotEmpty()) {
                _problems.value = capturedErrors
                activeDockTab.value = BottomDockTab.PROBLEMS
            } else if (isBuild && capturedErrors.isEmpty()) {
                _problems.value = emptyList()
            }
        }
    }

    private fun parseCompilerError(line: String, defaultFilePath: String): Problem? {
        // Parses: file:line:col: error: msg OR file:line: error: msg
        val regex = Regex("""(.+?):(\d+):(?:(\d+):)?\s*(error|warning):\s*(.+)""", RegexOption.IGNORE_CASE)
        val match = regex.find(line)
        if (match != null) {
            val (file, lineNum, colNum, severityStr, msg) = match.destructured
            val sev = if (severityStr.equals("warning", ignoreCase = true)) ProblemSeverity.WARNING else ProblemSeverity.ERROR
            return Problem(
                id = UUID.randomUUID().toString(),
                filePath = file.ifBlank { defaultFilePath },
                line = lineNum.toIntOrNull() ?: 1,
                column = colNum.toIntOrNull() ?: 1,
                message = msg,
                severity = sev
            )
        }
        return null
    }

    private fun appendTerminalLine(sessionId: String, line: TerminalLine) {
        _terminalSessions.value = _terminalSessions.value.map {
            if (it.id == sessionId) {
                it.copy(lines = (it.lines + line).takeLast(1000))
            } else {
                it
            }
        }
    }

    fun clearActiveTerminal() {
        val sId = activeTerminalSessionId.value
        _terminalSessions.value = _terminalSessions.value.map {
            if (it.id == sId) it.copy(lines = emptyList()) else it
        }
    }

    fun stopTerminalProcess() {
        val sId = activeTerminalSessionId.value
        termuxRepo.stopProcess(sId)
        runJob?.cancel()
        _terminalSessions.value = _terminalSessions.value.map {
            if (it.id == sId) it.copy(isRunning = false) else it
        }
    }

    fun addNewTerminalSession() {
        val count = _terminalSessions.value.size + 1
        val newSession = TerminalSession(
            id = "term-$count",
            title = "session $count",
            lines = listOf(TerminalLine(text = "New session $count started.", type = TerminalLineType.SYSTEM))
        )
        _terminalSessions.value = _terminalSessions.value + newSession
        activeTerminalSessionId.value = newSession.id
    }

    // Termux State & Diagnostics
    val isCloningTermux = MutableStateFlow(false)
    val termuxCloneResult = MutableStateFlow<String?>(null)
    val termuxDiagnostics = MutableStateFlow<TermuxEnvironmentDiagnostics?>(null)
    val isRunningBridgeTest = MutableStateFlow(false)
    val termuxBridgeTestResult = MutableStateFlow<TermuxCommandResult?>(null)
    val ollamaStatusMessage = MutableStateFlow("Ollama: Disabled (Local Qwen 0.5B Active)")

    fun refreshTermuxState() {
        viewModelScope.launch {
            isTermuxInstalled.value = termuxRepo.isTermuxInstalled()
            _termuxTools.value = termuxRepo.scanTermuxEnvironment()
            val projDir = projectRepo.getProjectDirectory(_currentProjectName.value)
            termuxDiagnostics.value = termuxRepo.performDiagnostics(projDir)
        }
    }

    fun runTermuxBridgeTest() {
        viewModelScope.launch {
            isRunningBridgeTest.value = true
            val projDir = projectRepo.getProjectDirectory(_currentProjectName.value)
            
            val session = _terminalSessions.value.firstOrNull { it.id == activeTerminalSessionId.value }
                ?: _terminalSessions.value.first()
            
            appendTerminalLine(session.id, TerminalLine(text = "[Bridge Test] Initiating harmless Termux bridge verification...", type = TerminalLineType.SYSTEM))
            
            val result = termuxRepo.runBridgeTestCommand(projDir)
            termuxBridgeTestResult.value = result
            
            if (result.stdout.isNotEmpty()) {
                appendTerminalLine(session.id, TerminalLine(text = result.stdout, type = TerminalLineType.STDOUT))
            }
            if (result.stderr.isNotEmpty()) {
                appendTerminalLine(session.id, TerminalLine(text = result.stderr, type = TerminalLineType.STDERR))
            }
            
            if (result.isSuccess) {
                appendTerminalLine(session.id, TerminalLine(text = "[Bridge Test SUCCESS] Exit code: ${result.exitCode} (${result.durationMs}ms)", type = TerminalLineType.SUCCESS))
            } else {
                appendTerminalLine(session.id, TerminalLine(text = "[Bridge Test FAILED] ${result.errmsg ?: "Exit code ${result.exitCode}"}", type = TerminalLineType.STDERR))
            }
            
            isRunningBridgeTest.value = false
            refreshTermuxState()
        }
    }

    fun cloneTermuxEnvironment() {
        viewModelScope.launch {
            isCloningTermux.value = true
            val res = termuxRepo.cloneAndLinkTermuxStorageAndPackages()
            termuxCloneResult.value = res.message
            refreshTermuxState()
            refreshProjectTree()
            isCloningTermux.value = false
        }
    }

    fun testOllamaConnection() {
        viewModelScope.launch {
            val res = aiRepo.testOllamaConnection(aiRepo.endpoint)
            ollamaStatusMessage.value = res.second
        }
    }

    /**
     * One-tap Codex-style AI button: Inspects current file, accesses compiler errors,
     * and automatically uses Qwen models to propose a surgical fix.
     */
    fun triggerCodexAiAutoFix() {
        val tab = activeTab ?: return
        val currentFile = File(tab.filePath)
        val currentCode = tab.content
        val activeProblems = _problems.value
        val terminalOut = _terminalSessions.value
            .firstOrNull { it.id == activeTerminalSessionId.value }
            ?.lines?.takeLast(10)?.joinToString("\n") { it.text } ?: ""

        isBottomDockExpanded.value = true
        activeDockTab.value = BottomDockTab.AI
        isAiGenerating.value = true

        val userMsg = AiMessage(
            id = UUID.randomUUID().toString(),
            sender = AiSender.USER,
            text = "✨ Codex Auto-Fix: Inspect ${tab.title}, check compiler errors, and fix code using ${aiRepo.modelName}."
        )
        _aiMessages.value = _aiMessages.value + userMsg

        viewModelScope.launch {
            aiRepo.runCodexAutoFix(
                currentFile = currentFile,
                currentCode = currentCode,
                problems = activeProblems,
                compilerLogs = terminalOut
            ).collect { partialOrFinal ->
                val currentList = _aiMessages.value
                val existingIdx = currentList.indexOfFirst { it.id == partialOrFinal.id }
                if (existingIdx >= 0) {
                    val updated = currentList.toMutableList()
                    updated[existingIdx] = partialOrFinal
                    _aiMessages.value = updated
                } else {
                    _aiMessages.value = currentList + partialOrFinal
                }

                if (partialOrFinal.proposedDiff != null) {
                    activePendingDiff.value = partialOrFinal.proposedDiff
                }
            }
            isAiGenerating.value = false
        }
    }

    // AI Qwen 0.5B
    fun askQwen(prompt: String) {
        if (prompt.isBlank()) return
        val userMsg = AiMessage(
            id = UUID.randomUUID().toString(),
            sender = AiSender.USER,
            text = prompt
        )
        _aiMessages.value = _aiMessages.value + userMsg
        isAiGenerating.value = true

        val tab = activeTab
        val currentFile = tab?.let { File(it.filePath) }
        val currentCode = tab?.content ?: ""
        val terminalOut = _terminalSessions.value
            .firstOrNull { it.id == activeTerminalSessionId.value }
            ?.lines?.takeLast(10)?.joinToString("\n") { it.text } ?: ""

        viewModelScope.launch {
            aiRepo.askQwen(
                prompt = prompt,
                currentFile = currentFile,
                currentCode = currentCode,
                selectedCode = "",
                problems = _problems.value,
                terminalLogs = terminalOut
            ).collect { partialOrFinal ->
                val currentList = _aiMessages.value
                val existingIdx = currentList.indexOfFirst { it.id == partialOrFinal.id }
                if (existingIdx >= 0) {
                    val updated = currentList.toMutableList()
                    updated[existingIdx] = partialOrFinal
                    _aiMessages.value = updated
                } else {
                    _aiMessages.value = currentList + partialOrFinal
                }

                if (partialOrFinal.proposedDiff != null) {
                    activePendingDiff.value = partialOrFinal.proposedDiff
                }
            }
            isAiGenerating.value = false
        }
    }

    fun triggerFixWithQwen(problem: Problem) {
        val tab = activeTab ?: return
        isBottomDockExpanded.value = true
        activeDockTab.value = BottomDockTab.AI

        val fixPrompt = "Fix compiler error on line ${problem.line}: ${problem.message}"
        askQwen(fixPrompt)
    }

    fun applyPendingDiff(diff: CodeDiff) {
        val tab = _tabs.value.firstOrNull { it.filePath == diff.filePath } ?: activeTab
        if (tab != null) {
            val updatedContent = if (tab.content.contains(diff.originalSnippet)) {
                tab.content.replace(diff.originalSnippet, diff.replacementSnippet)
            } else {
                // Surgical replacement at line index
                val lines = tab.content.lines().toMutableList()
                val targetIdx = (diff.startLine - 1).coerceIn(0, (lines.size - 1).coerceAtLeast(0))
                lines[targetIdx] = diff.replacementSnippet
                lines.joinToString("\n")
            }

            onEditorContentChange(updatedContent)
            saveCurrentFile()
            activePendingDiff.value = null

            // Re-run project to test fix!
            runProjectOrCurrentFile()
        }
    }

    fun rejectPendingDiff() {
        activePendingDiff.value = null
    }

    // Command Palette
    fun getCommandPaletteItems(): List<CommandItem> {
        return listOf(
            CommandItem("run", "Run Project / File", "Ctrl+R", "Execution") { runProjectOrCurrentFile() },
            CommandItem("save", "Save Active File", "Ctrl+S", "File") { saveCurrentFile() },
            CommandItem("save_all", "Save All Files", "Ctrl+Shift+S", "File") { saveAllFiles() },
            CommandItem("term", "Open Terminal", "Ctrl+`", "Terminal") {
                isBottomDockExpanded.value = true
                activeDockTab.value = BottomDockTab.TERMINAL
            },
            CommandItem("qwen", "Ask Qwen 0.5B Assistant", "Ctrl+Shift+A", "AI") {
                isBottomDockExpanded.value = true
                activeDockTab.value = BottomDockTab.AI
            },
            CommandItem("find", "Find and Replace", "Ctrl+F", "Editor") { activeModal.value = ActiveModal.FIND_REPLACE },
            CommandItem("goto", "Go to Line", "Ctrl+G", "Editor") { activeModal.value = ActiveModal.GO_TO_LINE },
            CommandItem("termux", "Import Termux Environment", null, "Tools") { activeModal.value = ActiveModal.TERMUX_ENVIRONMENT },
            CommandItem("git", "Open Source Control", null, "Git") {
                isBottomDockExpanded.value = true
                activeDockTab.value = BottomDockTab.GIT
            },
            CommandItem("web", "Documentation & Web Preview", null, "Web") { activeModal.value = ActiveModal.WEB_DOCS },
            CommandItem("plugins", "Plugins Manager & API", null, "Plugins") { activeModal.value = ActiveModal.PLUGINS },
            CommandItem("settings", "IDE Settings & Themes", null, "Preferences") { activeModal.value = ActiveModal.SETTINGS },
            CommandItem("close_all", "Close All Tabs", null, "Tabs") { closeAllTabs() },
            CommandItem("new_proj", "Create New Project", null, "Project") { activeModal.value = ActiveModal.NEW_PROJECT }
        )
    }

    // File manipulation helpers
    fun createNewFileInProject(fileName: String, customContent: String? = null) {
        val projDir = projectRepo.getProjectDirectory(_currentProjectName.value)
        val content = if (!customContent.isNullOrEmpty()) {
            customContent
        } else {
            Language.detect(fileName).sampleBoilerplate
        }
        val file = projectRepo.createFile(projDir.absolutePath, fileName, content)
        if (file != null) {
            refreshProjectTree()
            openFile(file.absolutePath)
        }
    }

    fun openProjectFile(relativePath: String) {
        val projDir = projectRepo.getProjectDirectory(_currentProjectName.value)
        val target = if (relativePath.startsWith("/")) File(relativePath) else File(projDir, relativePath)
        if (target.exists() && target.isFile) {
            openFile(target.absolutePath)
        }
    }

    fun deleteFileFromProject(path: String) {
        projectRepo.deleteItem(path)
        val tab = _tabs.value.firstOrNull { it.filePath == path }
        if (tab != null) {
            closeTab(tab.id)
        }
        refreshProjectTree()
    }

    // --- COMPLETE GIT INTEGRATION ---

    fun refreshGit() {
        viewModelScope.launch {
            gitRepo.refreshState(_currentProjectName.value)
        }
    }

    fun initGitRepository() {
        viewModelScope.launch {
            gitRepo.initRepo(_currentProjectName.value)
            appendTerminalLine(
                activeTerminalSessionId.value,
                TerminalLine("Git repository initialized on branch main.", TerminalLineType.SUCCESS)
            )
        }
    }

    fun gitStageFile(filePath: String) {
        viewModelScope.launch {
            gitRepo.stageFile(filePath, _currentProjectName.value)
        }
    }

    fun gitUnstageFile(filePath: String) {
        viewModelScope.launch {
            gitRepo.unstageFile(filePath, _currentProjectName.value)
        }
    }

    fun gitStageAll() {
        viewModelScope.launch {
            gitRepo.stageAll(_currentProjectName.value)
        }
    }

    fun gitCommit(message: String) {
        viewModelScope.launch {
            val res = gitRepo.commit(message, _currentProjectName.value)
            val lineType = if (res.isSuccess) TerminalLineType.SUCCESS else TerminalLineType.STDERR
            val feedback = if (res.isSuccess) "Committed: $message" else "Commit error: ${res.stderr}"
            appendTerminalLine(activeTerminalSessionId.value, TerminalLine(feedback, lineType))
        }
    }

    fun gitPush(remote: String = "origin", force: Boolean = false) {
        viewModelScope.launch {
            appendTerminalLine(activeTerminalSessionId.value, TerminalLine("Pushing to $remote...", TerminalLineType.SYSTEM))
            val res = gitRepo.push(remote = remote, force = force, projectName = _currentProjectName.value)
            val lineType = if (res.isSuccess) TerminalLineType.SUCCESS else TerminalLineType.STDERR
            val output = if (res.isSuccess) "Push succeeded: ${res.stdout}" else "Push failed: ${res.stderr}"
            appendTerminalLine(activeTerminalSessionId.value, TerminalLine(output, lineType))
        }
    }

    fun gitPull(remote: String = "origin") {
        viewModelScope.launch {
            appendTerminalLine(activeTerminalSessionId.value, TerminalLine("Pulling from $remote...", TerminalLineType.SYSTEM))
            val res = gitRepo.pull(remote = remote, projectName = _currentProjectName.value)
            val lineType = if (res.isSuccess) TerminalLineType.SUCCESS else TerminalLineType.STDERR
            val output = if (res.isSuccess) "Pull succeeded: ${res.stdout}" else "Pull failed: ${res.stderr}"
            appendTerminalLine(activeTerminalSessionId.value, TerminalLine(output, lineType))
            refreshProjectTree()
        }
    }

    fun gitFetch(remote: String = "origin") {
        viewModelScope.launch {
            appendTerminalLine(activeTerminalSessionId.value, TerminalLine("Fetching from $remote...", TerminalLineType.SYSTEM))
            val res = gitRepo.fetch(remote = remote, projectName = _currentProjectName.value)
            val lineType = if (res.isSuccess) TerminalLineType.SUCCESS else TerminalLineType.STDERR
            val output = if (res.isSuccess) "Fetch succeeded: ${res.stdout}" else "Fetch failed: ${res.stderr}"
            appendTerminalLine(activeTerminalSessionId.value, TerminalLine(output, lineType))
        }
    }

    fun gitCreateBranch(branchName: String, checkout: Boolean = true) {
        viewModelScope.launch {
            val res = gitRepo.createBranch(branchName, checkout, _currentProjectName.value)
            val lineType = if (res.isSuccess) TerminalLineType.SUCCESS else TerminalLineType.STDERR
            appendTerminalLine(activeTerminalSessionId.value, TerminalLine(if (res.isSuccess) "Created branch $branchName" else res.stderr, lineType))
        }
    }

    fun gitSwitchBranch(branchName: String) {
        viewModelScope.launch {
            val res = gitRepo.switchBranch(branchName, _currentProjectName.value)
            val lineType = if (res.isSuccess) TerminalLineType.SUCCESS else TerminalLineType.STDERR
            appendTerminalLine(activeTerminalSessionId.value, TerminalLine(if (res.isSuccess) "Switched to branch $branchName" else res.stderr, lineType))
            refreshProjectTree()
        }
    }

    fun gitDeleteBranch(branchName: String, force: Boolean = false) {
        viewModelScope.launch {
            val res = gitRepo.deleteBranch(branchName, force, _currentProjectName.value)
            val lineType = if (res.isSuccess) TerminalLineType.SUCCESS else TerminalLineType.STDERR
            appendTerminalLine(activeTerminalSessionId.value, TerminalLine(if (res.isSuccess) "Deleted branch $branchName" else res.stderr, lineType))
        }
    }

    fun gitMerge(branchToMerge: String) {
        viewModelScope.launch {
            appendTerminalLine(activeTerminalSessionId.value, TerminalLine("Merging branch $branchToMerge...", TerminalLineType.SYSTEM))
            val res = gitRepo.merge(branchToMerge, _currentProjectName.value)
            val lineType = if (res.isSuccess) TerminalLineType.SUCCESS else TerminalLineType.STDERR
            appendTerminalLine(activeTerminalSessionId.value, TerminalLine(if (res.isSuccess) "Merged $branchToMerge" else res.stderr, lineType))
            refreshProjectTree()
        }
    }

    fun gitStash(message: String = "") {
        viewModelScope.launch {
            val res = gitRepo.stash(message, _currentProjectName.value)
            val lineType = if (res.isSuccess) TerminalLineType.SUCCESS else TerminalLineType.STDERR
            appendTerminalLine(activeTerminalSessionId.value, TerminalLine(if (res.isSuccess) "Stashed changes" else res.stderr, lineType))
            refreshProjectTree()
        }
    }

    fun gitApplyStash(index: Int = 0, pop: Boolean = false) {
        viewModelScope.launch {
            val res = gitRepo.applyStash(index, pop, _currentProjectName.value)
            val lineType = if (res.isSuccess) TerminalLineType.SUCCESS else TerminalLineType.STDERR
            appendTerminalLine(activeTerminalSessionId.value, TerminalLine(if (res.isSuccess) (if (pop) "Popped stash" else "Applied stash") else res.stderr, lineType))
            refreshProjectTree()
        }
    }

    fun gitAddRemote(name: String, url: String) {
        viewModelScope.launch {
            val res = gitRepo.addRemote(name, url, _currentProjectName.value)
            val lineType = if (res.isSuccess) TerminalLineType.SUCCESS else TerminalLineType.STDERR
            appendTerminalLine(activeTerminalSessionId.value, TerminalLine(if (res.isSuccess) "Added remote $name -> $url" else res.stderr, lineType))
        }
    }

    fun gitRemoveRemote(name: String) {
        viewModelScope.launch {
            val res = gitRepo.removeRemote(name, _currentProjectName.value)
            val lineType = if (res.isSuccess) TerminalLineType.SUCCESS else TerminalLineType.STDERR
            appendTerminalLine(activeTerminalSessionId.value, TerminalLine(if (res.isSuccess) "Removed remote $name" else res.stderr, lineType))
        }
    }

    fun gitRevert(commitHash: String) {
        viewModelScope.launch {
            val res = gitRepo.revertCommit(commitHash, _currentProjectName.value)
            val lineType = if (res.isSuccess) TerminalLineType.SUCCESS else TerminalLineType.STDERR
            appendTerminalLine(activeTerminalSessionId.value, TerminalLine(if (res.isSuccess) "Reverted commit $commitHash" else res.stderr, lineType))
            refreshProjectTree()
        }
    }

    fun gitResolveConflict(filePath: String, resolution: GitRepository.ConflictResolution) {
        viewModelScope.launch {
            gitRepo.resolveConflict(filePath, resolution, _currentProjectName.value)
            refreshProjectTree()
        }
    }

    fun viewGitDiff(filePath: String, staged: Boolean = false, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val diff = gitRepo.getFileDiff(filePath, staged, _currentProjectName.value)
            onResult(diff)
        }
    }

    fun viewCommitDetails(commitHash: String, onResult: (String) -> Unit) {
        viewModelScope.launch {
            val details = gitRepo.getCommitDetails(commitHash, _currentProjectName.value)
            onResult(details)
        }
    }

    // --- QWEN + GIT INTEGRATION ---

    fun generateAiCommitMessage() {
        val currentGit = gitRepo.gitState.value
        val staged = currentGit.stagedFiles
        val modified = currentGit.modifiedFiles
        val files = if (staged.isNotEmpty()) staged else modified

        val prompt = "Based on the following changed files in Git: ${files.joinToString(", ")}, generate a clear, professional Conventional Commit message (e.g. 'feat: ...' or 'fix: ...'). Keep it concise and return ONLY the proposed message."
        isBottomDockExpanded.value = true
        activeDockTab.value = BottomDockTab.AI
        askQwen(prompt)
    }

    fun explainGitStatusWithQwen() {
        val currentGit = gitRepo.gitState.value
        val summary = """Git Status Summary:
Branch: ${currentGit.branch}
Staged: ${currentGit.stagedFiles.joinToString(", ")}
Modified: ${currentGit.modifiedFiles.joinToString(", ")}
Untracked: ${currentGit.untrackedFiles.joinToString(", ")}
Conflicts: ${currentGit.conflictFiles.map { it.filePath }.joinToString(", ")}
Ahead: ${currentGit.aheadCount}, Behind: ${currentGit.behindCount}
Explain what these changes mean, what actions are recommended next, and how to resolve any conflicts."""

        isBottomDockExpanded.value = true
        activeDockTab.value = BottomDockTab.AI
        askQwen(summary)
    }
}
