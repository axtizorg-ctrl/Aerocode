package com.example.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.EditorTab
import com.example.data.model.IdeTheme
import com.example.data.model.Language
import com.example.viewmodel.BottomDockTab
import com.example.viewmodel.IdeViewModel

data class CodeFoldBlock(
    val startLine: Int,
    val endLine: Int,
    val label: String
)

@Composable
fun CodeEditorScreen(
    viewModel: IdeViewModel,
    modifier: Modifier = Modifier
) {
    val tabs by viewModel.tabs.collectAsState()
    val activeTabId by viewModel.activeTabId.collectAsState()
    val theme by viewModel.theme.collectAsState()
    val fontSizeVal by viewModel.fontSize.collectAsState()
    val fontFamilyName by viewModel.fontFamilyName.collectAsState()
    val isWordWrap by viewModel.isWordWrapEnabled.collectAsState()
    val showLineNumbers by viewModel.showLineNumbers.collectAsState()
    val problems by viewModel.problems.collectAsState()

    // Cursor AI Editor State
    val isCursorPromptOpen by viewModel.isCursorInlinePromptOpen.collectAsState()
    val cursorPromptText by viewModel.cursorInlinePromptText.collectAsState()
    val isCursorGenerating by viewModel.isCursorGenerating.collectAsState()
    val cursorInlineDiff by viewModel.cursorInlineDiff.collectAsState()
    val cursorGhostText by viewModel.cursorGhostText.collectAsState()

    val cursorLine by viewModel.activeCursorLine.collectAsState()
    val cursorCol by viewModel.activeCursorCol.collectAsState()
    val cursorOffset by viewModel.activeCursorOffset.collectAsState()

    val editorFontFamily = when (fontFamilyName) {
        com.example.ui.theme.EditorFontOption.JETBRAINS_MONO.displayName -> com.example.ui.theme.JetBrainsMonoFontFamily
        com.example.ui.theme.EditorFontOption.FIRA_CODE.displayName -> com.example.ui.theme.FiraCodeFontFamily
        com.example.ui.theme.EditorFontOption.ROBOTO_MONO.displayName -> com.example.ui.theme.RobotoMonoFontFamily
        else -> FontFamily.Monospace
    }

    val activeTab = tabs.firstOrNull { it.id == activeTabId }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.bg)
    ) {
        // 1. Editor Tabs Bar with Colored Language Badges
        EditorTabsRow(
            tabs = tabs,
            activeTabId = activeTabId,
            theme = theme,
            onSelectTab = { id -> viewModel.openFile(tabs.first { it.id == id }.filePath) },
            onCloseTab = { id -> viewModel.closeTab(id) },
            onNewFileClick = { viewModel.activeModal.value = com.example.viewmodel.ActiveModal.NEW_FILE }
        )

        // 2. Main Editor Content Area + Right Edge "Problems" Vertical Banner
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(theme.editorBg)
        ) {
            if (activeTab != null) {
                EditorCanvas(
                    tab = activeTab,
                    theme = theme,
                    fontSize = fontSizeVal,
                    fontFamily = editorFontFamily,
                    showLineNumbers = showLineNumbers,
                    isWordWrap = isWordWrap,
                    activeCursorOffset = cursorOffset,
                    onContentChange = { newContent -> viewModel.onEditorContentChange(newContent) },
                    onCursorChanged = { text, offset -> viewModel.onEditorTextCursorChanged(text, offset) },
                    onZoom = { zoomFactor -> viewModel.adjustFontSizeByZoom(zoomFactor) }
                )

                // Live Cursor Inline Diff Overlay (Diff preview floating card right inside editor)
                if (cursorInlineDiff != null) {
                    CursorInlineDiffOverlay(
                        diff = cursorInlineDiff!!,
                        theme = theme,
                        onAccept = { viewModel.acceptCursorInlineDiff() },
                        onReject = { viewModel.rejectCursorInlineDiff() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp)
                            .align(Alignment.TopCenter)
                            .padding(8.dp)
                    )
                }

                // Cursor Inline Cmd+K Prompt Bar
                if (isCursorPromptOpen) {
                    CursorInlinePromptBar(
                        prompt = cursorPromptText,
                        onPromptChange = { viewModel.cursorInlinePromptText.value = it },
                        isGenerating = isCursorGenerating,
                        theme = theme,
                        onSubmit = { viewModel.submitCursorInlineEdit(it) },
                        onClose = { viewModel.closeCursorInlinePrompt() },
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            } else {
                EmptyEditorPlaceholder(theme = theme, onNewFile = {
                    viewModel.activeModal.value = com.example.viewmodel.ActiveModal.NEW_FILE
                })
            }

            // Right-edge Vertical "⚠️ Problems" Banner (Matches Screenshot)
            VerticalProblemsEdgeBadge(
                problemCount = problems.size,
                theme = theme,
                onClick = {
                    viewModel.activeDockTab.value = BottomDockTab.PROBLEMS
                    viewModel.isBottomDockExpanded.value = true
                },
                modifier = Modifier.align(Alignment.TopEnd)
            )
        }

        // 3. Cursor Editor Status Bar (Ln, Col, Language, Spaces, UTF-8)
        EditorStatusBar(
            tab = activeTab,
            cursorLine = cursorLine,
            cursorCol = cursorCol,
            fontSize = fontSizeVal,
            isCursorGenerating = isCursorGenerating,
            theme = theme
        )

        // 4. Mobile Coding Dual-Row Accessory Bar (with Cursor Copilot Ghost Text & ⌘K)
        if (activeTab != null) {
            val isWebLanguage = activeTab.language in listOf(Language.HTML, Language.CSS, Language.JAVASCRIPT, Language.TYPESCRIPT)
            DualRowAccessoryBar(
                theme = theme,
                isWebLanguage = isWebLanguage,
                cursorGhostText = cursorGhostText,
                onAcceptGhost = { viewModel.acceptCursorGhostText() },
                onDismissGhost = { viewModel.dismissCursorGhostText() },
                onToggleCursorCmdK = { viewModel.toggleCursorInlinePrompt() },
                onRunFullScreenWeb = { viewModel.isFullScreenWebPreview.value = true },
                onInsertText = { token ->
                    viewModel.insertTextAtCursor(token)
                },
                onUndo = { viewModel.undo() },
                onRedo = { viewModel.redo() },
                onFind = { viewModel.activeModal.value = com.example.viewmodel.ActiveModal.FIND_REPLACE },
                onSave = { viewModel.saveCurrentFile() },
                onCodexAiFix = { viewModel.triggerCodexAiAutoFix() }
            )
        }
    }
}

@Composable
fun EditorTabsRow(
    tabs: List<EditorTab>,
    activeTabId: String?,
    theme: IdeTheme,
    onSelectTab: (String) -> Unit,
    onCloseTab: (String) -> Unit,
    onNewFileClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(44.dp)
            .background(theme.surface)
            .horizontalScroll(rememberScrollState()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tabs.forEach { tab ->
            val isActive = tab.id == activeTabId
            val tabBg = if (isActive) theme.editorBg else theme.surface

            val (badgeLabel, badgeBg, badgeFg) = when (tab.language) {
                Language.JAVASCRIPT -> Triple("JS", Color(0xFFF7DF1E), Color(0xFF111111))
                Language.TYPESCRIPT -> Triple("TS", Color(0xFF3178C6), Color.White)
                Language.CSS -> Triple("CSS", Color(0xFF264DE4), Color.White)
                Language.HTML -> Triple("HTML", Color(0xFFE34F26), Color.White)
                Language.CPP, Language.C -> Triple("C++", Color(0xFF00599C), Color.White)
                Language.PYTHON -> Triple("PY", Color(0xFF3572A5), Color.White)
                Language.JAVA -> Triple("JAVA", Color(0xFFB07219), Color.White)
                Language.RUST -> Triple("RS", Color(0xFFDEA584), Color.Black)
                else -> Triple(tab.title.substringAfterLast('.', "").uppercase().take(3), Color(0xFF6C7086), Color.White)
            }

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .background(tabBg)
                    .clickable { onSelectTab(tab.id) }
                    .testTag("tab_${tab.title}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxHeight()
                        .padding(horizontal = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Language Badge (colored square box like in screenshot)
                    Surface(
                        color = badgeBg,
                        shape = RoundedCornerShape(3.dp),
                        modifier = Modifier.size(width = 22.dp, height = 18.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = badgeLabel,
                                color = badgeFg,
                                fontSize = 8.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Text(
                        text = tab.title,
                        color = if (isActive) theme.textPrimary else theme.textSecondary,
                        fontSize = 13.sp,
                        fontWeight = if (isActive) FontWeight.Medium else FontWeight.Normal
                    )

                    // Dirty indicator
                    if (tab.isDirty) {
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(theme.accent, RoundedCornerShape(3.dp))
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = { onCloseTab(tab.id) },
                        modifier = Modifier
                            .size(18.dp)
                            .testTag("close_tab_${tab.title}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close tab",
                            tint = theme.textSecondary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                // Active bottom highlight bar
                if (isActive) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .background(theme.accent)
                            .align(Alignment.BottomCenter)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(theme.surfaceHighlight)
            )
        }

        IconButton(
            onClick = onNewFileClick,
            modifier = Modifier
                .padding(horizontal = 4.dp)
                .size(32.dp)
                .testTag("add_tab_button")
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "New Tab",
                tint = theme.textSecondary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun VerticalProblemsEdgeBadge(
    problemCount: Int,
    theme: IdeTheme,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        color = theme.errorColor,
        shape = RoundedCornerShape(topStart = 4.dp, bottomStart = 4.dp),
        modifier = modifier
            .padding(top = 16.dp)
            .width(26.dp)
            .height(100.dp)
            .testTag("vertical_problems_badge")
    ) {
        Box(contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier.rotate(90f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = theme.bg,
                    modifier = Modifier.size(13.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = if (problemCount > 0) "Problems ($problemCount)" else "Problems",
                    color = theme.bg,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun EditorStatusBar(
    tab: EditorTab?,
    cursorLine: Int,
    cursorCol: Int,
    fontSize: Float,
    isCursorGenerating: Boolean,
    theme: IdeTheme
) {
    Surface(
        color = theme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .height(24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Ln $cursorLine, Col $cursorCol",
                    color = theme.accent,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(10.dp))
                if (tab != null) {
                    Text(
                        text = "${tab.content.lines().size} lines",
                        color = theme.textSecondary,
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Spaces: 2",
                        color = theme.textSecondary,
                        fontSize = 10.sp
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isCursorGenerating) {
                    Text(
                        text = "⚡ Cursor Generating...",
                        color = theme.accent,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = tab?.language?.displayName ?: "Plain Text",
                    color = theme.textPrimary,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "UTF-8",
                    color = theme.textSecondary,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
fun EditorCanvas(
    tab: EditorTab,
    theme: IdeTheme,
    fontSize: Float,
    fontFamily: FontFamily = FontFamily.Monospace,
    showLineNumbers: Boolean,
    isWordWrap: Boolean,
    activeCursorOffset: Int = 0,
    onContentChange: (String) -> Unit,
    onCursorChanged: (String, Int) -> Unit = { _, _ -> },
    onZoom: (Float) -> Unit = {}
) {
    var textFieldValue by remember(tab.id) {
        mutableStateOf(TextFieldValue(tab.content, TextRange(tab.content.length)))
    }

    LaunchedEffect(tab.content) {
        if (textFieldValue.text != tab.content) {
            val safePos = activeCursorOffset.coerceIn(0, tab.content.length)
            textFieldValue = textFieldValue.copy(
                text = tab.content,
                selection = TextRange(safePos)
            )
        }
    }

    val lines = remember(textFieldValue.text) {
        textFieldValue.text.lines()
    }

    val activeLineIdx = remember(textFieldValue.selection, textFieldValue.text) {
        val cursor = textFieldValue.selection.start.coerceIn(0, textFieldValue.text.length)
        val textBefore = textFieldValue.text.take(cursor)
        textBefore.count { it == '\n' }
    }

    // Dynamic Code Block Detection for Functions, Classes, and Blocks
    val codeBlocks = remember(textFieldValue.text, tab.language) {
        detectCodeBlocks(lines, tab.language)
    }

    // Interactive Code Folding State (Set of folded start line indices)
    var foldedStarts by remember(tab.id) { mutableStateOf(setOf<Int>()) }

    // Clean up folded starts if lines change
    LaunchedEffect(lines.size) {
        foldedStarts = foldedStarts.filter { it in lines.indices && codeBlocks.containsKey(it) }.toSet()
    }

    val hiddenLineIndices = remember(foldedStarts, codeBlocks) {
        val hidden = mutableSetOf<Int>()
        for (start in foldedStarts) {
            val block = codeBlocks[start]
            if (block != null && block.endLine > block.startLine) {
                for (hiddenIdx in (block.startLine + 1)..block.endLine) {
                    hidden.add(hiddenIdx)
                }
            }
        }
        hidden
    }

    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()
    val density = androidx.compose.ui.platform.LocalDensity.current
    val rowLineHeight = with(density) { (fontSize * 1.5f).sp.toDp() }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (kotlin.math.abs(zoom - 1f) > 0.02f) {
                        onZoom(zoom)
                    }
                }
            }
            .verticalScroll(verticalScroll)
    ) {
        // Line Numbers Gutter with Interactive Code Folding Arrows (▼ / ▶)
        if (showLineNumbers) {
            Column(
                modifier = Modifier
                    .background(theme.gutterBg)
                    .padding(vertical = 12.dp, horizontal = 4.dp)
                    .widthIn(min = 48.dp),
                horizontalAlignment = Alignment.End
            ) {
                lines.indices.forEach { lineIdx ->
                    if (!hiddenLineIndices.contains(lineIdx)) {
                        val block = codeBlocks[lineIdx]
                        val isFolded = foldedStarts.contains(lineIdx)

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End,
                            modifier = Modifier.height(rowLineHeight)
                        ) {
                            Text(
                                text = "${lineIdx + 1}",
                                color = if (lineIdx == activeLineIdx) theme.accent else theme.lineNumColor,
                                fontSize = fontSize.sp,
                                fontFamily = fontFamily,
                                fontWeight = if (lineIdx == activeLineIdx) FontWeight.Bold else FontWeight.Normal
                            )

                            Spacer(modifier = Modifier.width(3.dp))

                            // Little Arrow Sign for minimizing / maximizing function or class code
                            if (block != null) {
                                Box(
                                    modifier = Modifier
                                        .size(16.dp)
                                        .clickable {
                                            foldedStarts = if (isFolded) {
                                                foldedStarts - lineIdx // Maximize
                                            } else {
                                                foldedStarts + lineIdx // Minimize
                                            }
                                        }
                                        .testTag("fold_toggle_$lineIdx"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isFolded) "▶" else "▼",
                                        color = if (isFolded) theme.accent else theme.functionColor,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.width(16.dp))
                            }
                        }
                    }
                }
            }
            Box(
                modifier = Modifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(theme.surfaceHighlight)
            )
        }

        // Code Editor Text Area with Dynamic Folding Transformation
        val editorModifier = if (isWordWrap) {
            Modifier
                .weight(1f)
                .padding(vertical = 12.dp, horizontal = 12.dp)
        } else {
            Modifier
                .weight(1f)
                .horizontalScroll(horizontalScroll)
                .padding(vertical = 12.dp, horizontal = 12.dp)
        }

        Box(modifier = editorModifier) {
            BasicTextField(
                value = textFieldValue,
                onValueChange = { newVal ->
                    val isTextChanged = textFieldValue.text != newVal.text
                    textFieldValue = newVal
                    if (isTextChanged) {
                        onContentChange(newVal.text)
                    }
                    onCursorChanged(newVal.text, newVal.selection.start)
                },
                modifier = Modifier.fillMaxWidth().testTag("code_editor_input"),
                textStyle = TextStyle(
                    color = theme.textPrimary,
                    fontSize = fontSize.sp,
                    fontFamily = fontFamily,
                    lineHeight = (fontSize * 1.5f).sp
                ),
                cursorBrush = SolidColor(theme.accent),
                visualTransformation = remember(textFieldValue.text, foldedStarts, codeBlocks, tab.language, theme) {
                    createFoldVisualTransformation(
                        originalText = textFieldValue.text,
                        codeBlocks = codeBlocks,
                        foldedStarts = foldedStarts,
                        language = tab.language,
                        theme = theme
                    )
                }
            )
        }
    }
}

/**
 * Creates a VisualTransformation that collapses folded blocks into styled markers
 * and provides safe non-decreasing offset mapping.
 */
fun createFoldVisualTransformation(
    originalText: String,
    codeBlocks: Map<Int, CodeFoldBlock>,
    foldedStarts: Set<Int>,
    language: Language,
    theme: IdeTheme
): androidx.compose.ui.text.input.VisualTransformation {
    if (foldedStarts.isEmpty()) {
        return androidx.compose.ui.text.input.VisualTransformation { annotated ->
            val highlighted = SyntaxHighlighter.highlight(annotated.text, language, theme)
            androidx.compose.ui.text.input.TransformedText(
                highlighted,
                androidx.compose.ui.text.input.OffsetMapping.Identity
            )
        }
    }

    val lines = originalText.lines()
    val lineStartOffsets = IntArray(lines.size + 1)
    var curOffset = 0
    for (i in lines.indices) {
        lineStartOffsets[i] = curOffset
        curOffset += lines[i].length + 1
    }
    lineStartOffsets[lines.size] = originalText.length

    val sortedStarts = foldedStarts.filter { codeBlocks.containsKey(it) }.sorted()
    val activeBlocks = mutableListOf<CodeFoldBlock>()
    var maxEnd = -1
    for (start in sortedStarts) {
        val block = codeBlocks[start] ?: continue
        if (block.startLine > maxEnd && block.endLine > block.startLine) {
            activeBlocks.add(block)
            maxEnd = block.endLine
        }
    }

    if (activeBlocks.isEmpty()) {
        return androidx.compose.ui.text.input.VisualTransformation { annotated ->
            val highlighted = SyntaxHighlighter.highlight(annotated.text, language, theme)
            androidx.compose.ui.text.input.TransformedText(
                highlighted,
                androidx.compose.ui.text.input.OffsetMapping.Identity
            )
        }
    }

    data class FoldSeg(
        val origStart: Int,
        val origEnd: Int,
        val replacement: String,
        val transStart: Int
    )

    val segments = mutableListOf<FoldSeg>()
    val sb = StringBuilder()
    var lastOrigIndex = 0

    for (block in activeBlocks) {
        val origStart = lineStartOffsets.getOrElse(block.startLine + 1) { originalText.length }.coerceAtMost(originalText.length)
        val origEnd = lineStartOffsets.getOrElse(block.endLine + 1) { originalText.length }.coerceAtMost(originalText.length)

        if (origStart > lastOrigIndex) {
            sb.append(originalText.substring(lastOrigIndex, origStart))
        }

        val transStart = sb.length
        val rep = "  /* ... ${block.label} folded (${block.endLine - block.startLine} lines) ... */\n"
        sb.append(rep)
        segments.add(FoldSeg(origStart, origEnd, rep, transStart))
        lastOrigIndex = origEnd
    }

    if (lastOrigIndex < originalText.length) {
        sb.append(originalText.substring(lastOrigIndex))
    }

    val transformedString = sb.toString()
    val transformedAnnotated = SyntaxHighlighter.highlight(transformedString, language, theme)

    val offsetMapping = object : androidx.compose.ui.text.input.OffsetMapping {
        override fun originalToTransformed(offset: Int): Int {
            val clamped = offset.coerceIn(0, originalText.length)
            var shift = 0
            for (seg in segments) {
                if (clamped > seg.origEnd) {
                    shift += (seg.origEnd - seg.origStart) - seg.replacement.length
                } else if (clamped >= seg.origStart) {
                    return seg.transStart.coerceIn(0, transformedString.length)
                }
            }
            return (clamped - shift).coerceIn(0, transformedString.length)
        }

        override fun transformedToOriginal(offset: Int): Int {
            val clamped = offset.coerceIn(0, transformedString.length)
            var shift = 0
            for (seg in segments) {
                val segTransEnd = seg.transStart + seg.replacement.length
                if (clamped > segTransEnd) {
                    shift += (seg.origEnd - seg.origStart) - seg.replacement.length
                } else if (clamped >= seg.transStart) {
                    return seg.origStart.coerceIn(0, originalText.length)
                }
            }
            return (clamped + shift).coerceIn(0, originalText.length)
        }
    }

    return androidx.compose.ui.text.input.VisualTransformation { _ ->
        androidx.compose.ui.text.input.TransformedText(transformedAnnotated, offsetMapping)
    }
}

/**
 * Detects code fold blocks for functions, classes, and braced groups.
 */
fun detectCodeBlocks(lines: List<String>, language: Language): Map<Int, CodeFoldBlock> {
    val blocks = mutableMapOf<Int, CodeFoldBlock>()
    val stack = ArrayDeque<Pair<Int, String>>()

    lines.forEachIndexed { idx, line ->
        val trimmed = line.trim()

        val isClass = trimmed.startsWith("class ") || trimmed.startsWith("struct ") || trimmed.startsWith("interface ")
        val isFunction = trimmed.startsWith("function ") || trimmed.startsWith("def ") || trimmed.startsWith("fun ") ||
                trimmed.startsWith("fn ") || trimmed.startsWith("func ") || trimmed.startsWith("async def ") ||
                trimmed.startsWith("async function ") || trimmed.startsWith("public ") || trimmed.startsWith("private ") ||
                trimmed.startsWith("protected ") || (trimmed.contains("(") && trimmed.contains(")") && (trimmed.endsWith("{") || trimmed.contains("=>")))

        val label = when {
            isClass -> "class"
            isFunction -> "function"
            else -> "block"
        }

        val openBraces = trimmed.count { it == '{' }
        val closeBraces = trimmed.count { it == '}' }

        if (openBraces > closeBraces) {
            val effectiveStart = if (idx > 0 && (lines[idx - 1].trim().contains("(") || lines[idx - 1].trim().startsWith("class ")) && openBraces == 1 && trimmed.startsWith("{")) {
                idx - 1
            } else {
                idx
            }
            stack.addLast(Pair(effectiveStart, label))
        } else if (closeBraces > openBraces) {
            if (stack.isNotEmpty()) {
                val (start, blockLabel) = stack.removeLast()
                if (idx > start) {
                    blocks[start] = CodeFoldBlock(start, idx, blockLabel)
                }
            }
        }
    }

    // Python indentation-based folding
    if (language == Language.PYTHON) {
        lines.forEachIndexed { idx, line ->
            val trimmed = line.trimStart()
            if (trimmed.startsWith("def ") || trimmed.startsWith("async def ") || trimmed.startsWith("class ")) {
                val indent = line.length - trimmed.length
                var end = idx
                for (j in (idx + 1) until lines.size) {
                    val next = lines[j]
                    if (next.isBlank()) continue
                    val nextIndent = next.length - next.trimStart().length
                    if (nextIndent > indent) {
                        end = j
                    } else {
                        break
                    }
                }
                if (end > idx) {
                    blocks[idx] = CodeFoldBlock(idx, end, if (trimmed.startsWith("class ")) "class" else "function")
                }
            }
        }
    }

    // HTML tag blocks
    if (language == Language.HTML) {
        val tagStack = ArrayDeque<Pair<Int, String>>()
        val openTagRegex = Regex("""<([a-zA-Z0-9_-]+)[^>/]*>""")
        val closeTagRegex = Regex("""</([a-zA-Z0-9_-]+)>""")
        val selfClosingTags = setOf("img", "br", "hr", "input", "meta", "link", "area", "base", "col")

        lines.forEachIndexed { idx, line ->
            val openMatches = openTagRegex.findAll(line).toList()
            val closeMatches = closeTagRegex.findAll(line).toList()

            for (m in openMatches) {
                val tagName = m.groupValues[1].lowercase()
                if (!selfClosingTags.contains(tagName) && !line.contains("</$tagName>")) {
                    tagStack.addLast(Pair(idx, tagName))
                }
            }

            for (m in closeMatches) {
                val tagName = m.groupValues[1].lowercase()
                if (tagStack.isNotEmpty() && tagStack.last().second == tagName) {
                    val (start, t) = tagStack.removeLast()
                    if (idx > start) {
                        blocks[start] = CodeFoldBlock(start, idx, "<$t>")
                    }
                }
            }
        }
    }

    return blocks
}

@Composable
fun DualRowAccessoryBar(
    theme: IdeTheme,
    isWebLanguage: Boolean = false,
    cursorGhostText: String? = null,
    onAcceptGhost: () -> Unit = {},
    onDismissGhost: () -> Unit = {},
    onToggleCursorCmdK: () -> Unit = {},
    onRunFullScreenWeb: () -> Unit = {},
    onInsertText: (String) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onFind: () -> Unit,
    onSave: () -> Unit,
    onCodexAiFix: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(theme.surface)
            .padding(vertical = 2.dp)
    ) {
        // Cursor Copilot Inline Ghost-Text Pill (Accept with Tab)
        if (cursorGhostText != null) {
            Surface(
                color = theme.bg,
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.accent),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 6.dp, vertical = 2.dp)
                    .testTag("cursor_copilot_ghost_pill")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "Copilot",
                            tint = theme.accent,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Cursor: " + cursorGhostText.take(40),
                            color = theme.textPrimary,
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            onClick = onAcceptGhost,
                            color = theme.accent,
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.height(22.dp)
                        ) {
                            Box(modifier = Modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                                Text("Tab ⇥ Accept", color = theme.bg, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(onClick = onDismissGhost, modifier = Modifier.size(20.dp)) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = theme.textSecondary, modifier = Modifier.size(12.dp))
                        }
                    }
                }
            }
        }

        // Quick Symbol Strip (Scrollable) with Prominent "✦ ⌘K Edit", "✨ AI Codex Fix" and "🌐 Full Screen Web" buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(34.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Cursor ⌘K Edit Button
            Surface(
                onClick = onToggleCursorCmdK,
                color = theme.accent,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .height(28.dp)
                    .testTag("accessory_cursor_cmdk_btn")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Cursor Cmd+K",
                        tint = theme.bg,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "⌘K Edit",
                        color = theme.bg,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Codex AI Fix Button
            Surface(
                onClick = onCodexAiFix,
                color = theme.surfaceHighlight,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .height(28.dp)
                    .testTag("accessory_codex_ai_fix")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoFixHigh,
                        contentDescription = null,
                        tint = theme.accent,
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "AI Fix",
                        color = theme.textPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Quick Full-Screen Web Window Launcher for HTML, CSS, JS
            if (isWebLanguage) {
                Spacer(modifier = Modifier.width(4.dp))
                Surface(
                    onClick = onRunFullScreenWeb,
                    color = theme.functionColor,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .height(28.dp)
                        .testTag("accessory_fullscreen_web_btn")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Language,
                            contentDescription = null,
                            tint = theme.bg,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Web Preview",
                            color = theme.bg,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(6.dp))

            listOf(
                "    ", "{", "}", "(", ")", "[", "]", ";", "\"", "'",
                "=", ":", "/", "<", ">", "_", "$", "&", "|", "->", "const ", "let ", "return "
            ).forEach { token ->
                val label = if (token == "    ") "TAB" else token
                Surface(
                    onClick = { onInsertText(token) },
                    color = theme.surfaceHighlight,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .padding(horizontal = 2.dp)
                        .height(26.dp)
                ) {
                    Box(modifier = Modifier.padding(horizontal = 8.dp), contentAlignment = Alignment.Center) {
                        Text(label, color = theme.textPrimary, fontSize = 11.sp)
                    }
                }
            }
        }

        // Row 1 (Matches Screenshot): ⌘K, TAB, Search, Undo, Redo, Up, Save, ESC
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AccessoryTextButton(text = "⌘K", color = theme.accent, onClick = onToggleCursorCmdK)
            AccessoryIconButton(
                icon = Icons.Default.KeyboardTab,
                desc = "Tab",
                tint = if (cursorGhostText != null) theme.accent else theme.textPrimary,
                onClick = {
                    if (cursorGhostText != null) onAcceptGhost() else onInsertText("    ")
                }
            )
            AccessoryIconButton(icon = Icons.Default.Search, desc = "Search", tint = theme.textPrimary, onClick = onFind)
            AccessoryIconButton(icon = Icons.Default.Undo, desc = "Undo", tint = theme.textPrimary, onClick = onUndo)
            AccessoryIconButton(icon = Icons.Default.Redo, desc = "Redo", tint = theme.textPrimary, onClick = onRedo)
            AccessoryIconButton(icon = Icons.Default.KeyboardArrowUp, desc = "Up", tint = theme.textPrimary, onClick = {})
            AccessoryIconButton(icon = Icons.Default.Save, desc = "Save", tint = theme.textPrimary, onClick = onSave)
            AccessoryTextButton(text = "ESC", color = theme.textPrimary, onClick = {})
        }

        // Row 2 (Matches Screenshot): CTRL, ALT, |< (Home), >| (End), Left, Down, Right, |c
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            AccessoryTextButton(text = "CTRL", color = theme.textPrimary, onClick = {})
            AccessoryTextButton(text = "ALT", color = theme.textPrimary, onClick = {})
            AccessoryTextButton(text = "⇃M", color = theme.textPrimary, onClick = {})
            AccessoryTextButton(text = "⇂M", color = theme.textPrimary, onClick = {})
            AccessoryIconButton(icon = Icons.Default.KeyboardArrowLeft, desc = "Left", tint = theme.textPrimary, onClick = {})
            AccessoryIconButton(icon = Icons.Default.KeyboardArrowDown, desc = "Down", tint = theme.textPrimary, onClick = {})
            AccessoryIconButton(icon = Icons.Default.KeyboardArrowRight, desc = "Right", tint = theme.textPrimary, onClick = {})
            AccessoryTextButton(text = "⇂C", color = theme.textPrimary, onClick = { onInsertText("{};") })
        }
    }
}

@Composable
fun AccessoryTextButton(
    text: String,
    color: Color = Color.White,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = Color.Transparent,
        modifier = Modifier.size(width = 44.dp, height = 36.dp)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                color = color,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun AccessoryIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    desc: String,
    tint: Color = Color.White,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.size(36.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = desc,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun EmptyEditorPlaceholder(
    theme: IdeTheme,
    onNewFile: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Default.Code,
            contentDescription = null,
            tint = theme.accent.copy(alpha = 0.5f),
            modifier = Modifier.size(64.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "No File Open",
            color = theme.textPrimary,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Select a file from the explorer or create a new one.",
            color = theme.textSecondary,
            fontSize = 13.sp
        )
        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = onNewFile,
            colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
            modifier = Modifier.testTag("empty_state_new_file_button")
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Create File", color = theme.bg)
        }
    }
}

@Composable
fun CursorInlinePromptBar(
    prompt: String,
    onPromptChange: (String) -> Unit,
    isGenerating: Boolean,
    theme: IdeTheme,
    onSubmit: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = theme.surface,
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, theme.accent),
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("cursor_inline_prompt_bar")
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = "Cursor AI",
                        tint = theme.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Cursor AI  •  Cmd+K",
                        color = theme.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                IconButton(onClick = onClose, modifier = Modifier.size(24.dp)) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = theme.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Input Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(theme.bg, RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BasicTextField(
                    value = prompt,
                    onValueChange = onPromptChange,
                    modifier = Modifier.weight(1f).testTag("cursor_inline_prompt_input"),
                    textStyle = TextStyle(color = theme.textPrimary, fontSize = 13.sp),
                    cursorBrush = SolidColor(theme.accent),
                    decorationBox = { innerTextField ->
                        if (prompt.isEmpty()) {
                            Text(
                                "Describe edit... (e.g. 'Add try-catch', 'Refactor')",
                                color = theme.textSecondary.copy(alpha = 0.7f),
                                fontSize = 12.sp
                            )
                        }
                        innerTextField()
                    }
                )

                Spacer(modifier = Modifier.width(6.dp))

                Button(
                    onClick = { onSubmit(prompt) },
                    enabled = prompt.isNotBlank() && !isGenerating,
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                    shape = RoundedCornerShape(6.dp),
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Text(
                        if (isGenerating) "..." else "Edit",
                        color = theme.bg,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            if (isGenerating) {
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = theme.accent,
                    trackColor = theme.surfaceHighlight
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Quick Prompt Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(
                    "✦ Add Error Handling" to "Add comprehensive try-catch and error logging",
                    "✦ Optimize" to "Optimize code performance and memory allocation",
                    "✦ Add Comments" to "Add clear documentation comments and docstrings",
                    "✦ Fix Syntax" to "Fix any syntax errors and formatting",
                    "✦ Add Tests" to "Generate unit test cases for this code"
                ).forEach { (chipLabel, fullInstruction) ->
                    Surface(
                        onClick = {
                            onPromptChange(fullInstruction)
                            onSubmit(fullInstruction)
                        },
                        color = theme.surfaceHighlight,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.padding(end = 6.dp).height(22.dp)
                    ) {
                        Box(modifier = Modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                            Text(chipLabel, color = theme.textPrimary, fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CursorInlineDiffOverlay(
    diff: com.example.data.model.CodeDiff,
    theme: IdeTheme,
    onAccept: () -> Unit,
    onReject: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = theme.bg.copy(alpha = 0.95f),
        modifier = modifier
            .testTag("cursor_inline_diff_overlay")
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            // Header Bar
            Surface(
                color = theme.surface,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, theme.accent),
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = theme.accent,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Cursor Diff (Lines ${diff.startLine}..${diff.endLine})",
                            color = theme.textPrimary,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = onAccept,
                            colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp).testTag("cursor_accept_diff_btn")
                        ) {
                            Text("✓ Accept (Tab)", color = theme.bg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        OutlinedButton(
                            onClick = onReject,
                            shape = RoundedCornerShape(6.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp).testTag("cursor_reject_diff_btn")
                        ) {
                            Text("✕ Reject (Esc)", color = theme.textSecondary, fontSize = 11.sp)
                        }
                    }
                }
            }

            // Diff Scroll Area
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Red Original Snippet (Removed)
                if (diff.originalSnippet.isNotBlank()) {
                    Text(
                        text = "Original Code",
                        color = theme.errorColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                    Surface(
                        color = Color(0x33FF5555),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            diff.originalSnippet.lines().forEach { line ->
                                Text(
                                    text = "- $line",
                                    color = Color(0xFFFF8B8B),
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                }

                // Green Replacement Snippet (Added)
                Text(
                    text = "Cursor Proposed Code",
                    color = Color(0xFF50FA7B),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 2.dp)
                )
                Surface(
                    color = Color(0x3350FA7B),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        diff.replacementSnippet.lines().forEach { line ->
                            Text(
                                text = "+ $line",
                                color = Color(0xFF8EFFB0),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
