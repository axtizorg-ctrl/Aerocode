package com.example.ui.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.IdeTheme
import com.example.data.model.TerminalLine
import com.example.data.model.TerminalLineType
import com.example.ui.theme.EditorFontOption
import com.example.ui.theme.FiraCodeFontFamily
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.RobotoMonoFontFamily
import com.example.ui.theme.SourceCodeProFontFamily
import com.example.viewmodel.IdeViewModel

@Composable
fun TerminalScreen(
    viewModel: IdeViewModel,
    modifier: Modifier = Modifier
) {
    val theme by viewModel.theme.collectAsState()
    val sessions by viewModel.terminalSessions.collectAsState()
    val activeSessionId by viewModel.activeTerminalSessionId.collectAsState()
    val commandInput by viewModel.terminalCommandInput.collectAsState()
    val isTermuxInstalled by viewModel.isTermuxInstalled.collectAsState()
    val fontName by viewModel.fontFamilyName.collectAsState()
    val terminalFontSize by viewModel.fontSize.collectAsState()

    val terminalFont = when (fontName) {
        EditorFontOption.JETBRAINS_MONO.displayName -> JetBrainsMonoFontFamily
        EditorFontOption.FIRA_CODE.displayName -> FiraCodeFontFamily
        EditorFontOption.ROBOTO_MONO.displayName -> RobotoMonoFontFamily
        EditorFontOption.SOURCE_CODE_PRO.displayName -> SourceCodeProFontFamily
        else -> FontFamily.Monospace
    }

    val currentSession = sessions.firstOrNull { it.id == activeSessionId } ?: sessions.firstOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.terminalBg)
            .pointerInput(Unit) {
                detectTransformGestures { _, _, zoom, _ ->
                    if (kotlin.math.abs(zoom - 1f) > 0.02f) {
                        viewModel.adjustFontSizeByZoom(zoom)
                    }
                }
            }
    ) {
        // 1. Session Tabs Bar & Controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .background(theme.surface)
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Sessions Scroll
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                sessions.forEach { sess ->
                    val isActive = sess.id == activeSessionId
                    Surface(
                        onClick = { viewModel.activeTerminalSessionId.value = sess.id },
                        color = if (isActive) theme.terminalBg else theme.surface,
                        shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp),
                        modifier = Modifier
                            .padding(end = 4.dp)
                            .height(32.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (sess.isRunning) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(10.dp),
                                    strokeWidth = 2.dp,
                                    color = theme.accent
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text(
                                text = sess.title,
                                color = if (isActive) theme.textPrimary else theme.textSecondary,
                                fontSize = 12.sp,
                                fontFamily = terminalFont
                            )
                        }
                    }
                }

                // Add Session
                IconButton(
                    onClick = { viewModel.addNewTerminalSession() },
                    modifier = Modifier.size(28.dp).testTag("terminal_add_session")
                ) {
                    Icon(Icons.Default.Add, contentDescription = "New Session", tint = theme.textSecondary, modifier = Modifier.size(16.dp))
                }
            }

            // Termux status chip
            Surface(
                onClick = { viewModel.activeModal.value = com.example.viewmodel.ActiveModal.TERMUX_ENVIRONMENT },
                color = if (isTermuxInstalled) theme.accent.copy(alpha = 0.2f) else theme.surfaceHighlight,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(
                                if (isTermuxInstalled) theme.functionColor else theme.warningColor,
                                RoundedCornerShape(3.dp)
                            )
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isTermuxInstalled) "Termux Ready" else "Detect Termux",
                        color = theme.textPrimary,
                        fontSize = 10.sp,
                        fontFamily = terminalFont
                    )
                }
            }

            // Stop Process Button
            if (currentSession?.isRunning == true) {
                IconButton(
                    onClick = { viewModel.stopTerminalProcess() },
                    modifier = Modifier.size(28.dp).testTag("terminal_stop_process")
                ) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop", tint = theme.errorColor, modifier = Modifier.size(16.dp))
                }
            }

            // Clear Button
            IconButton(
                onClick = { viewModel.clearActiveTerminal() },
                modifier = Modifier.size(28.dp).testTag("terminal_clear")
            ) {
                Icon(Icons.Default.DeleteSweep, contentDescription = "Clear", tint = theme.textSecondary, modifier = Modifier.size(16.dp))
            }
        }

        // 2. Terminal Output Stream
        val listState = rememberLazyListState()
        LaunchedEffect(currentSession?.lines?.size) {
            if ((currentSession?.lines?.size ?: 0) > 0) {
                listState.scrollToItem(currentSession!!.lines.size - 1)
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp)
        ) {
            items(currentSession?.lines ?: emptyList()) { line ->
                TerminalLineItem(
                    line = line,
                    theme = theme,
                    fontFamily = terminalFont,
                    fontSize = terminalFontSize
                )
            }
        }

        // 3. Quick Terminal Keys Row
        TerminalQuickKeysRow(
            theme = theme,
            fontFamily = terminalFont,
            onKeyClick = { key ->
                when (key) {
                    "↑" -> {
                        if (viewModel.terminalHistory.isNotEmpty()) {
                            viewModel.terminalHistoryIndex = (viewModel.terminalHistoryIndex - 1).coerceAtLeast(0)
                            viewModel.terminalCommandInput.value = viewModel.terminalHistory[viewModel.terminalHistoryIndex]
                        }
                    }
                    "↓" -> {
                        if (viewModel.terminalHistory.isNotEmpty()) {
                            viewModel.terminalHistoryIndex = (viewModel.terminalHistoryIndex + 1).coerceAtMost(viewModel.terminalHistory.size - 1)
                            viewModel.terminalCommandInput.value = viewModel.terminalHistory[viewModel.terminalHistoryIndex]
                        }
                    }
                    "TAB" -> viewModel.terminalCommandInput.value += "\t"
                    else -> viewModel.terminalCommandInput.value += key
                }
            }
        )

        // 4. Interactive Input Line
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.surface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$",
                color = theme.accent,
                fontSize = 14.sp,
                fontFamily = terminalFont,
                modifier = Modifier.padding(end = 6.dp)
            )

            OutlinedTextField(
                value = commandInput,
                onValueChange = { viewModel.terminalCommandInput.value = it },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .testTag("terminal_input_field"),
                placeholder = {
                    Text(
                        "Enter bash command...",
                        color = theme.textSecondary,
                        fontSize = (terminalFontSize - 2f).coerceAtLeast(10f).sp,
                        fontFamily = terminalFont
                    )
                },
                textStyle = LocalTextStyle.current.copy(
                    color = theme.terminalFg,
                    fontSize = terminalFontSize.sp,
                    fontFamily = terminalFont
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    val toExec = viewModel.terminalCommandInput.value
                    viewModel.terminalCommandInput.value = ""
                    viewModel.executeTerminalCommand(toExec)
                })
            )

            Spacer(modifier = Modifier.width(6.dp))

            IconButton(
                onClick = {
                    val toExec = viewModel.terminalCommandInput.value
                    viewModel.terminalCommandInput.value = ""
                    viewModel.executeTerminalCommand(toExec)
                },
                modifier = Modifier
                    .size(36.dp)
                    .testTag("terminal_send_button")
            ) {
                Icon(Icons.Default.Send, contentDescription = "Run", tint = theme.accent, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun TerminalLineItem(
    line: TerminalLine,
    theme: IdeTheme,
    fontFamily: FontFamily,
    fontSize: Float
) {
    val textColor = when (line.type) {
        TerminalLineType.COMMAND -> theme.accent
        TerminalLineType.STDOUT -> theme.terminalFg
        TerminalLineType.STDERR -> theme.errorColor
        TerminalLineType.SYSTEM -> theme.textSecondary
        TerminalLineType.SUCCESS -> theme.functionColor
    }

    Text(
        text = line.text,
        color = textColor,
        fontSize = fontSize.sp,
        fontFamily = fontFamily,
        lineHeight = (fontSize * 1.35f).sp,
        modifier = Modifier.padding(vertical = 1.dp)
    )
}

@Composable
fun TerminalQuickKeysRow(
    theme: IdeTheme,
    fontFamily: FontFamily,
    onKeyClick: (String) -> Unit
) {
    val keys = listOf("ESC", "TAB", "CTRL", "ALT", "↑", "↓", "←", "→", "|", "-", "~", "/")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(theme.surfaceHighlight)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        keys.forEach { key ->
            Surface(
                onClick = { onKeyClick(key) },
                color = theme.surface,
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .padding(horizontal = 2.dp)
                    .height(24.dp)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 7.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = key,
                        color = theme.textPrimary,
                        fontSize = 10.sp,
                        fontFamily = fontFamily
                    )
                }
            }
        }
    }
}

