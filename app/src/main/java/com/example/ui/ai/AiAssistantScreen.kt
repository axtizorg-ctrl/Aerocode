package com.example.ui.ai

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.AiMessage
import com.example.data.model.AiSender
import com.example.data.model.CodeDiff
import com.example.data.model.IdeTheme
import com.example.viewmodel.IdeViewModel

@Composable
fun AiAssistantScreen(
    viewModel: IdeViewModel,
    modifier: Modifier = Modifier
) {
    val theme by viewModel.theme.collectAsState()
    val messages by viewModel.aiMessages.collectAsState()
    val isGenerating by viewModel.isAiGenerating.collectAsState()
    val pendingDiff by viewModel.activePendingDiff.collectAsState()

    var inputPrompt by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.size - 1)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.bg)
    ) {
        // 1. Header with Model Status
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.surface)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(if (viewModel.aiRepo.isOllamaEnabled) Color(0xFFA6E3A1) else theme.functionColor, RoundedCornerShape(4.dp))
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = viewModel.aiRepo.modelName,
                    color = theme.textPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Surface(
                    color = theme.surfaceHighlight,
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (viewModel.aiRepo.isOllamaEnabled) "Ollama" else "Local Engine",
                        color = theme.accent,
                        fontSize = 10.sp,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Codex Auto Fix shortcut
                Surface(
                    onClick = { viewModel.triggerCodexAiAutoFix() },
                    color = Color(0xFFCBA6F7),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.height(26.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = Color(0xFF11111B),
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            "Codex Fix",
                            color = Color(0xFF11111B),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                IconButton(
                    onClick = { viewModel.activeModal.value = com.example.viewmodel.ActiveModal.SETTINGS },
                    modifier = Modifier.size(28.dp).testTag("ai_settings_button")
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "AI Settings",
                        tint = theme.textSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        // 2. Pending Diff View (if AI generated code patch)
        if (pendingDiff != null) {
            AiDiffViewer(
                diff = pendingDiff!!,
                theme = theme,
                onApply = { viewModel.applyPendingDiff(pendingDiff!!) },
                onReject = { viewModel.rejectPendingDiff() }
            )
        }

        // 3. Conversation Message List
        LazyColumn(
            state = listState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            items(messages) { msg ->
                AiMessageBubble(message = msg, theme = theme)
                Spacer(modifier = Modifier.height(10.dp))
            }

            if (isGenerating) {
                item {
                    Row(
                        modifier = Modifier.padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                            color = theme.accent
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Qwen 0.5B is thinking & using tools...",
                            color = theme.textSecondary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        // 4. Quick Action Prompt Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.surface)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            listOf(
                "Explain Code",
                "Fix Build Errors",
                "Explain Git Diff",
                "Explain Merge Conflict",
                "Generate Commit Message",
                "Add Comments",
                "Optimize Speed"
            ).forEach { chip ->
                Surface(
                    onClick = {
                        if (chip == "Generate Commit Message") {
                            viewModel.generateAiCommitMessage()
                        } else if (chip == "Explain Git Diff" || chip == "Explain Merge Conflict") {
                            viewModel.explainGitStatusWithQwen()
                        } else {
                            viewModel.askQwen(chip)
                        }
                    },
                    color = theme.surfaceHighlight,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .testTag("prompt_chip_$chip")
                ) {
                    Text(
                        text = chip,
                        color = theme.textPrimary,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                    )
                }
            }
        }

        // 5. Ask Qwen Prompt Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(theme.surface)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputPrompt,
                onValueChange = { inputPrompt = it },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp)
                    .testTag("ai_prompt_input"),
                placeholder = {
                    Text("Ask Qwen...", color = theme.textSecondary, fontSize = 13.sp)
                },
                textStyle = LocalTextStyle.current.copy(
                    color = theme.textPrimary,
                    fontSize = 13.sp
                ),
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = {
                    val promptToSend = inputPrompt
                    inputPrompt = ""
                    viewModel.askQwen(promptToSend)
                })
            )

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = {
                    val promptToSend = inputPrompt
                    inputPrompt = ""
                    viewModel.askQwen(promptToSend)
                },
                modifier = Modifier
                    .size(42.dp)
                    .background(theme.accent, RoundedCornerShape(8.dp))
                    .testTag("ai_send_button")
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "Send",
                    tint = theme.bg,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun AiMessageBubble(message: AiMessage, theme: IdeTheme) {
    val isUser = message.sender == AiSender.USER

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        // Tool invocations pills
        if (message.toolCalls.isNotEmpty()) {
            message.toolCalls.forEach { tool ->
                Surface(
                    color = theme.surfaceHighlight,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .padding(bottom = 4.dp)
                        .clip(RoundedCornerShape(6.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Build,
                            contentDescription = null,
                            tint = theme.accent,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "${tool.toolName} -> ${tool.outputResult.take(36)}",
                            color = theme.textSecondary,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        }

        // Message Body
        Surface(
            color = if (isUser) theme.accent.copy(alpha = 0.25f) else theme.surface,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.widthIn(max = 320.dp)
        ) {
            Text(
                text = message.text,
                color = theme.textPrimary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                modifier = Modifier.padding(10.dp)
            )
        }
    }
}

@Composable
fun AiDiffViewer(
    diff: CodeDiff,
    theme: IdeTheme,
    onApply: () -> Unit,
    onReject: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = theme.surfaceHighlight),
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "AI Proposed Patch (${diff.filePath.substringAfterLast('/')})",
                    color = theme.accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Lines ${diff.startLine}-${diff.endLine}",
                    color = theme.textSecondary,
                    fontSize = 11.sp
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Removed lines (-)
            Surface(
                color = theme.errorColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "- " + diff.originalSnippet,
                    color = theme.errorColor,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(6.dp)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Added lines (+)
            Surface(
                color = theme.functionColor.copy(alpha = 0.15f),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "+ " + diff.replacementSnippet,
                    color = theme.functionColor,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(6.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(
                    onClick = onReject,
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("diff_reject_button")
                ) {
                    Text("Reject", color = theme.textSecondary, fontSize = 12.sp)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = onApply,
                    colors = ButtonDefaults.buttonColors(containerColor = theme.functionColor),
                    modifier = Modifier
                        .height(34.dp)
                        .testTag("diff_apply_button")
                ) {
                    Text("Apply Patch", color = theme.bg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
