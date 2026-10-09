package com.example.ui.dialogs

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.data.model.CommandItem
import com.example.data.model.IdeTheme
import com.example.data.model.Language
import com.example.viewmodel.ActiveModal
import com.example.viewmodel.IdeViewModel

@Composable
fun IdeModals(
    viewModel: IdeViewModel
) {
    val activeModal by viewModel.activeModal.collectAsState()
    val theme by viewModel.theme.collectAsState()

    when (activeModal) {
        ActiveModal.COMMAND_PALETTE -> CommandPaletteModal(viewModel = viewModel, theme = theme)
        ActiveModal.FIND_REPLACE -> FindReplaceModal(viewModel = viewModel, theme = theme)
        ActiveModal.GO_TO_LINE -> GoToLineModal(viewModel = viewModel, theme = theme)
        ActiveModal.TERMUX_ENVIRONMENT -> TermuxEnvironmentModal(viewModel = viewModel, theme = theme)
        ActiveModal.SETTINGS -> SettingsModal(viewModel = viewModel, theme = theme)
        ActiveModal.PLUGINS -> PluginsModal(viewModel = viewModel, theme = theme)
        ActiveModal.WEB_DOCS -> WebDocsModal(viewModel = viewModel, theme = theme)
        ActiveModal.DANGEROUS_CMD_CONFIRM -> DangerousCommandModal(viewModel = viewModel, theme = theme)
        ActiveModal.NEW_PROJECT -> NewProjectModal(viewModel = viewModel, theme = theme)
        ActiveModal.NEW_FILE -> NewFileModal(viewModel = viewModel, theme = theme)
        ActiveModal.NONE -> {}
    }
}

@Composable
fun CommandPaletteModal(viewModel: IdeViewModel, theme: IdeTheme) {
    var query by remember { mutableStateOf("") }
    val commands = remember { viewModel.getCommandPaletteItems() }

    val filtered = remember(query) {
        if (query.isBlank()) commands
        else commands.filter {
            it.title.contains(query, ignoreCase = true) || it.category.contains(query, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = { viewModel.activeModal.value = ActiveModal.NONE },
        containerColor = theme.surface,
        title = {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("> Type a command...", color = theme.textSecondary, fontSize = 13.sp) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("command_palette_search"),
                textStyle = LocalTextStyle.current.copy(color = theme.textPrimary, fontSize = 13.sp)
            )
        },
        text = {
            LazyColumn(modifier = Modifier.height(280.dp)) {
                items(filtered) { cmd ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.activeModal.value = ActiveModal.NONE
                                cmd.action()
                            }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(cmd.title, color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                            Text(cmd.category, color = theme.textSecondary, fontSize = 11.sp)
                        }
                        if (cmd.shortcut != null) {
                            Surface(color = theme.surfaceHighlight, shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    text = cmd.shortcut,
                                    color = theme.accent,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Divider(color = theme.surfaceHighlight, thickness = 0.5.dp)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.activeModal.value = ActiveModal.NONE }) {
                Text("Close", color = theme.textSecondary)
            }
        }
    )
}

@Composable
fun FindReplaceModal(viewModel: IdeViewModel, theme: IdeTheme) {
    var findText by remember { mutableStateOf(viewModel.searchQuery.value) }
    var replaceText by remember { mutableStateOf(viewModel.replaceQuery.value) }

    AlertDialog(
        onDismissRequest = { viewModel.activeModal.value = ActiveModal.NONE },
        containerColor = theme.surface,
        title = { Text("Find & Replace", color = theme.textPrimary, fontSize = 16.sp) },
        text = {
            Column {
                OutlinedTextField(
                    value = findText,
                    onValueChange = { findText = it },
                    label = { Text("Find", color = theme.textSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("find_input"),
                    textStyle = LocalTextStyle.current.copy(color = theme.textPrimary)
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = replaceText,
                    onValueChange = { replaceText = it },
                    label = { Text("Replace with", color = theme.textSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("replace_input"),
                    textStyle = LocalTextStyle.current.copy(color = theme.textPrimary)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val tab = viewModel.activeTab
                    if (tab != null && findText.isNotEmpty()) {
                        val newContent = tab.content.replace(findText, replaceText)
                        viewModel.onEditorContentChange(newContent)
                    }
                    viewModel.activeModal.value = ActiveModal.NONE
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) {
                Text("Replace All", color = theme.bg)
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.activeModal.value = ActiveModal.NONE }) {
                Text("Cancel", color = theme.textSecondary)
            }
        }
    )
}

@Composable
fun GoToLineModal(viewModel: IdeViewModel, theme: IdeTheme) {
    var lineInput by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { viewModel.activeModal.value = ActiveModal.NONE },
        containerColor = theme.surface,
        title = { Text("Go to Line", color = theme.textPrimary, fontSize = 16.sp) },
        text = {
            OutlinedTextField(
                value = lineInput,
                onValueChange = { lineInput = it },
                label = { Text("Line Number", color = theme.textSecondary) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().testTag("goto_line_input"),
                textStyle = LocalTextStyle.current.copy(color = theme.textPrimary)
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    // Jumps to requested line
                    viewModel.activeModal.value = ActiveModal.NONE
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) {
                Text("Go", color = theme.bg)
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.activeModal.value = ActiveModal.NONE }) {
                Text("Cancel", color = theme.textSecondary)
            }
        }
    )
}

@Composable
fun TermuxEnvironmentModal(viewModel: IdeViewModel, theme: IdeTheme) {
    val tools by viewModel.termuxTools.collectAsState()
    val isInstalled by viewModel.isTermuxInstalled.collectAsState()
    val isCloning by viewModel.isCloningTermux.collectAsState()
    val cloneResult by viewModel.termuxCloneResult.collectAsState()
    val context = LocalContext.current

    AlertDialog(
        onDismissRequest = { viewModel.activeModal.value = ActiveModal.NONE },
        containerColor = theme.surface,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Termux Environment", color = theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Surface(
                    color = if (isInstalled) theme.functionColor.copy(alpha = 0.2f) else theme.errorColor.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text(
                        text = if (isInstalled) "Installed" else "Not Detected",
                        color = if (isInstalled) theme.functionColor else theme.errorColor,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.height(340.dp)) {
                Text(
                    text = "AeroCode reuses existing Termux packages and storage natively without re-downloading duplicate copies.",
                    color = theme.textSecondary,
                    fontSize = 11.sp
                )

                Spacer(modifier = Modifier.height(8.dp))

                // One-Tap Clone & Link Termux Storage & Packages Button (Requested by user)
                Button(
                    onClick = { viewModel.cloneTermuxEnvironment() },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().testTag("clone_termux_button")
                ) {
                    if (isCloning) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), color = theme.bg, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    } else {
                        Icon(Icons.Default.CloudSync, contentDescription = null, tint = theme.bg, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Clone & Link Termux Storage & Packages", color = theme.bg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                if (cloneResult != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = theme.functionColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = cloneResult!!,
                            color = theme.functionColor,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    items(tools) { tool ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (tool.isInstalled) "✓" else "○",
                                    color = if (tool.isInstalled) theme.functionColor else theme.textSecondary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(tool.name, color = theme.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    Text(tool.category, color = theme.textSecondary, fontSize = 10.sp)
                                }
                            }

                            if (!tool.isInstalled) {
                                Surface(
                                    onClick = {
                                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                        clipboard.setPrimaryClip(ClipData.newPlainText("install_cmd", tool.installCommand))
                                        Toast.makeText(context, "Copied: ${tool.installCommand}", Toast.LENGTH_SHORT).show()
                                    },
                                    color = theme.surfaceHighlight,
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "Install in Termux",
                                        color = theme.accent,
                                        fontSize = 10.sp,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                    )
                                }
                            } else {
                                Text("Ready", color = theme.functionColor, fontSize = 11.sp)
                            }
                        }
                        Divider(color = theme.surfaceHighlight, thickness = 0.5.dp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val intent = viewModel.termuxRepo.openTermuxApp()
                    if (intent != null) {
                        context.startActivity(intent)
                    } else {
                        Toast.makeText(context, "Termux app not found. Please install Termux from F-Droid or GitHub.", Toast.LENGTH_LONG).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.surfaceHighlight)
            ) {
                Text("Open Termux App", color = theme.textPrimary, fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.refreshTermuxState() }) {
                Text("Re-scan", color = theme.accent)
            }
        }
    )
}

@Composable
fun SettingsModal(viewModel: IdeViewModel, theme: IdeTheme) {
    var selectedTheme by remember { mutableStateOf(viewModel.theme.value) }
    var fontSz by remember { mutableStateOf(viewModel.fontSize.value) }
    var autoSave by remember { mutableStateOf(viewModel.isAutoSaveEnabled.value) }
    var wordWrap by remember { mutableStateOf(viewModel.isWordWrapEnabled.value) }

    // Ollama and Qwen settings (Requested by user)
    var isOllamaOn by remember { mutableStateOf(viewModel.aiRepo.isOllamaEnabled) }
    var aiEndpoint by remember { mutableStateOf(viewModel.aiRepo.endpoint) }
    var selectedQwenModel by remember { mutableStateOf(viewModel.aiRepo.modelName) }
    val ollamaStatus by viewModel.ollamaStatusMessage.collectAsState()

    AlertDialog(
        onDismissRequest = { viewModel.activeModal.value = ActiveModal.NONE },
        containerColor = theme.surface,
        title = { Text("IDE & AI Settings", color = theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier
                    .height(360.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                // QWEN & OLLAMA SETTINGS SECTION
                Text("LOCAL QWEN / OLLAMA CONFIGURATION", color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                // Toggle Ollama Switch
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Enable Ollama Backend", color = theme.textPrimary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text("Connect to local Ollama on device/LAN", color = theme.textSecondary, fontSize = 10.sp)
                    }
                    Switch(
                        checked = isOllamaOn,
                        onCheckedChange = {
                            isOllamaOn = it
                            viewModel.aiRepo.isOllamaEnabled = it
                            viewModel.aiRepo.backend = if (it) "Ollama" else "Local Engine"
                        }
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Qwen Model Choice
                Text("QWEN CODER MODEL", color = theme.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                listOf("qwen2.5-coder:0.5b", "qwen2.5-coder:1.5b", "qwen2.5-coder:7b", "qwen2.5:0.5b").forEach { qModel ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedQwenModel = qModel
                                viewModel.aiRepo.modelName = qModel
                            }
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedQwenModel == qModel),
                            onClick = {
                                selectedQwenModel = qModel
                                viewModel.aiRepo.modelName = qModel
                            }
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(qModel, color = theme.textPrimary, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                OutlinedTextField(
                    value = aiEndpoint,
                    onValueChange = {
                        aiEndpoint = it
                        viewModel.aiRepo.endpoint = it
                    },
                    label = { Text("Ollama Endpoint URL", color = theme.textSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(color = theme.textPrimary, fontSize = 12.sp)
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Test Connection Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    OutlinedButton(
                        onClick = { viewModel.testOllamaConnection() },
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Test Connection", fontSize = 11.sp, color = theme.accent)
                    }
                    Text(
                        text = ollamaStatus.take(28),
                        color = if (ollamaStatus.contains("connected", ignoreCase = true)) theme.functionColor else theme.textSecondary,
                        fontSize = 10.sp
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                Divider(color = theme.surfaceHighlight)
                Spacer(modifier = Modifier.height(10.dp))

                // TERMUX PACKAGES & STORAGE ZERO-DOWNLOAD CLONING (Requested by user)
                val isCloningTermux by viewModel.isCloningTermux.collectAsState()
                val termuxCloneResult by viewModel.termuxCloneResult.collectAsState()

                Text("TERMUX PACKAGES & STORAGE (ZERO DOWNLOAD)", color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Clone & link existing Termux packages and storage directly on-device without downloading duplicate copies again.",
                    color = theme.textSecondary,
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = { viewModel.cloneTermuxEnvironment() },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth().testTag("settings_clone_termux_btn")
                ) {
                    if (isCloningTermux) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), color = theme.bg, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    } else {
                        Icon(Icons.Default.CloudSync, contentDescription = null, tint = theme.bg, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                    }
                    Text("Clone & Link Termux Packages & Storage", color = theme.bg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                if (termuxCloneResult != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Surface(
                        color = theme.functionColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = termuxCloneResult!!,
                            color = theme.functionColor,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Divider(color = theme.surfaceHighlight)
                Spacer(modifier = Modifier.height(10.dp))

                // Theme Picker
                Text("THEME", color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(4.dp))
                IdeTheme.entries.forEach { th ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selectedTheme = th
                                viewModel.updateTheme(th)
                            }
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedTheme == th),
                            onClick = {
                                selectedTheme = th
                                viewModel.updateTheme(th)
                            }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(th.displayName, color = theme.textPrimary, fontSize = 12.sp)
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = theme.surfaceHighlight)
                Spacer(modifier = Modifier.height(10.dp))

                // Font Size Slider
                Text("FONT SIZE (${fontSz.toInt()}sp)", color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Slider(
                    value = fontSz,
                    onValueChange = {
                        fontSz = it
                        viewModel.updateFontSize(it)
                    },
                    valueRange = 10f..32f,
                    steps = 10
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Unified Global Font Family Selector (All choices preserved)
                Text("GLOBAL APPLICATION FONT", color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Sets font for Editor, Menus, Terminal, Git, AI & all UI", color = theme.textSecondary, fontSize = 10.sp)
                Spacer(modifier = Modifier.height(4.dp))
                val currentFontFamily by viewModel.fontFamilyName.collectAsState()
                com.example.ui.theme.EditorFontOption.entries.forEach { fontOption ->
                    val isSelected = (currentFontFamily == fontOption.displayName)
                    Surface(
                        color = if (isSelected) theme.accent.copy(alpha = 0.15f) else Color.Transparent,
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.updateFontFamily(fontOption.displayName)
                            }
                            .padding(vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 6.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    viewModel.updateFontFamily(fontOption.displayName)
                                }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = fontOption.displayName,
                                    color = if (isSelected) theme.accent else theme.textPrimary,
                                    fontSize = 13.sp,
                                    fontFamily = fontOption.fontFamily,
                                    fontWeight = FontWeight.Bold
                                )
                                // Live Font Preview Sample
                                Text(
                                    text = "Aa Bb Cc 123  function hello() { return 42; }",
                                    color = if (isSelected) theme.textPrimary else theme.textSecondary,
                                    fontSize = 11.sp,
                                    fontFamily = fontOption.fontFamily
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Divider(color = theme.surfaceHighlight)
                Spacer(modifier = Modifier.height(10.dp))

                // Switches
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Auto-Save Changes", color = theme.textPrimary, fontSize = 12.sp)
                    Switch(
                        checked = autoSave,
                        onCheckedChange = {
                            autoSave = it
                            viewModel.isAutoSaveEnabled.value = it
                        }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Word Wrap", color = theme.textPrimary, fontSize = 12.sp)
                    Switch(
                        checked = wordWrap,
                        onCheckedChange = {
                            wordWrap = it
                            viewModel.isWordWrapEnabled.value = it
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.activeModal.value = ActiveModal.NONE },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) {
                Text("Done", color = theme.bg)
            }
        }
    )
}

@Composable
fun PluginsModal(viewModel: IdeViewModel, theme: IdeTheme) {
    val plugins by viewModel.pluginRepo.plugins.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = { viewModel.activeModal.value = ActiveModal.NONE },
        containerColor = theme.surface,
        title = {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = theme.surfaceHighlight,
                contentColor = theme.accent
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Installed Plugins", fontSize = 12.sp) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Plugin API Docs", fontSize = 12.sp) }
                )
            }
        },
        text = {
            if (selectedTab == 0) {
                LazyColumn(modifier = Modifier.height(300.dp)) {
                    items(plugins) { plugin ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(plugin.name, color = theme.textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                Text(plugin.description, color = theme.textSecondary, fontSize = 11.sp)
                                Spacer(modifier = Modifier.height(2.dp))
                                Text("v${plugin.version} by ${plugin.author}", color = theme.accent, fontSize = 10.sp)
                            }
                            Switch(
                                checked = plugin.isEnabled,
                                onCheckedChange = { viewModel.pluginRepo.togglePlugin(plugin.id) }
                            )
                        }
                        Divider(color = theme.surfaceHighlight, thickness = 0.5.dp)
                    }
                }
            } else {
                Column(
                    modifier = Modifier
                        .height(300.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = viewModel.pluginRepo.apiDocumentation,
                        color = theme.textPrimary,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.activeModal.value = ActiveModal.NONE }) {
                Text("Close", color = theme.accent)
            }
        }
    )
}

@Composable
fun WebDocsModal(viewModel: IdeViewModel, theme: IdeTheme) {
    var selectedDocTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("Live Web App", "C++ Reference", "Python Reference", "Bash Reference")

    AlertDialog(
        onDismissRequest = { viewModel.activeModal.value = ActiveModal.NONE },
        containerColor = theme.surface,
        title = {
            ScrollableTabRow(
                selectedTabIndex = selectedDocTab,
                containerColor = theme.surfaceHighlight,
                contentColor = theme.accent,
                edgePadding = 8.dp
            ) {
                tabTitles.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedDocTab == index,
                        onClick = { selectedDocTab = index },
                        text = { Text(title, fontSize = 11.sp) }
                    )
                }
            }
        },
        text = {
            Box(modifier = Modifier.height(320.dp).fillMaxWidth()) {
                when (selectedDocTab) {
                    0 -> {
                        // Live Full-Screen Web Window Launcher (Replaces popup box per request)
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Language,
                                contentDescription = null,
                                tint = theme.accent,
                                modifier = Modifier.size(48.dp)
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Full-Screen Web Window",
                                color = theme.textPrimary,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "HTML, CSS, and JS run in a full-screen browser window with console logs and live reload, instead of a popup box.",
                                color = theme.textSecondary,
                                fontSize = 11.sp,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(
                                onClick = {
                                    viewModel.activeModal.value = ActiveModal.NONE
                                    viewModel.isFullScreenWebPreview.value = true
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth().testTag("launch_fullscreen_web_btn")
                            ) {
                                Icon(Icons.Default.OpenInNew, contentDescription = null, tint = theme.bg, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open Full-Screen Web Window", color = theme.bg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    1 -> {
                        Text(
                            """### C++ 17/20 Quick Reference (Offline)
- Vectors: `std::vector<int> v; v.push_back(10);`
- Lambdas: `[](auto x) { return x * 2; }`
- Pointers: `std::unique_ptr<T> p = std::make_unique<T>();`
- Clang compiler flags: `-O2 -std=c++17 -Wall -Wextra`""",
                            color = theme.textPrimary,
                            fontSize = 12.sp
                        )
                    }
                    2 -> {
                        Text(
                            """### Python 3 Quick Reference (Offline)
- List comp: `[x**2 for x in nums if x > 0]`
- Dict comprehension: `{k: v for k, v in items}`
- F-strings: `f"Result: {val:.2f}"`
- File I/O: `with open('file.txt', 'r') as f:`""",
                            color = theme.textPrimary,
                            fontSize = 12.sp
                        )
                    }
                    3 -> {
                        Text(
                            """### Bash & Termux Quick Reference (Offline)
- Package install: `pkg install <package>`
- Search package: `pkg search <query>`
- Piping: `cat file.txt | grep 'pattern' | wc -l`
- Background process: `./server &`""",
                            color = theme.textPrimary,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { viewModel.activeModal.value = ActiveModal.NONE }) {
                Text("Close", color = theme.accent)
            }
        }
    )
}

@Composable
fun DangerousCommandModal(viewModel: IdeViewModel, theme: IdeTheme) {
    val cmd = viewModel.pendingDangerousCommand.value ?: ""

    AlertDialog(
        onDismissRequest = { viewModel.activeModal.value = ActiveModal.NONE },
        containerColor = theme.surface,
        icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = theme.errorColor) },
        title = { Text("Potentially Destructive Command", color = theme.errorColor, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "This command may delete files or modify core storage partitions without confirmation:",
                    color = theme.textPrimary,
                    fontSize = 12.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = theme.errorColor.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = cmd,
                        color = theme.errorColor,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    viewModel.activeModal.value = ActiveModal.NONE
                    viewModel.executeTerminalCommand(cmd)
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.errorColor)
            ) {
                Text("Execute Anyway", color = theme.textPrimary)
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.activeModal.value = ActiveModal.NONE }) {
                Text("Cancel", color = theme.textSecondary)
            }
        }
    )
}

@Composable
fun NewProjectModal(viewModel: IdeViewModel, theme: IdeTheme) {
    var projectName by remember { mutableStateOf("") }
    var selectedLang by remember { mutableStateOf(Language.CPP) }

    AlertDialog(
        onDismissRequest = { viewModel.activeModal.value = ActiveModal.NONE },
        containerColor = theme.surface,
        title = { Text("New Project", color = theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                OutlinedTextField(
                    value = projectName,
                    onValueChange = { projectName = it },
                    label = { Text("Project Name", color = theme.textSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_project_name_input"),
                    textStyle = LocalTextStyle.current.copy(color = theme.textPrimary)
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text("TEMPLATE / LANGUAGE", color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                listOf(Language.CPP, Language.PYTHON, Language.HTML, Language.JAVA, Language.RUST).forEach { lang ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedLang = lang }
                            .padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = (selectedLang == lang), onClick = { selectedLang = lang })
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(lang.displayName, color = theme.textPrimary, fontSize = 12.sp)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (projectName.isNotBlank()) {
                        viewModel.createProject(projectName, selectedLang)
                    }
                    viewModel.activeModal.value = ActiveModal.NONE
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) {
                Text("Create", color = theme.bg)
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.activeModal.value = ActiveModal.NONE }) {
                Text("Cancel", color = theme.textSecondary)
            }
        }
    )
}

data class FrontendFileTemplate(
    val name: String,
    val defaultFileName: String,
    val badge: String,
    val description: String,
    val content: String
)

@Composable
fun NewFileModal(viewModel: IdeViewModel, theme: IdeTheme) {
    val templates = remember {
        listOf(
            FrontendFileTemplate(
                name = "HTML5 Page",
                defaultFileName = "index.html",
                badge = "HTML",
                description = "Semantic HTML5 layout with stylesheet, viewport, and script tags",
                content = Language.HTML.sampleBoilerplate
            ),
            FrontendFileTemplate(
                name = "CSS Stylesheet",
                defaultFileName = "style.css",
                badge = "CSS",
                description = "Modern CSS variables, flexbox/grid layout, and responsive reset",
                content = Language.CSS.sampleBoilerplate
            ),
            FrontendFileTemplate(
                name = "JavaScript Module",
                defaultFileName = "app.js",
                badge = "JS",
                description = "Modern DOM event listeners, interactive state, and console logging",
                content = Language.JAVASCRIPT.sampleBoilerplate
            ),
            FrontendFileTemplate(
                name = "React JSX",
                defaultFileName = "Component.jsx",
                badge = "JSX",
                description = "Functional component with state, effects, and modern JSX markup",
                content = """// React Functional Component
export default function Component() {
    const [active, setActive] = React.useState(false);

    return (
        <div className="component-card">
            <h3>⚡ React Component</h3>
            <p>Status: {active ? "Active" : "Idle"}</p>
            <button onClick={() => setActive(!active)}>
                Toggle State
            </button>
        </div>
    );
}
"""
            ),
            FrontendFileTemplate(
                name = "TypeScript",
                defaultFileName = "main.ts",
                badge = "TS",
                description = "Strict typing interfaces, async utilities, and exported functions",
                content = Language.TYPESCRIPT.sampleBoilerplate
            ),
            FrontendFileTemplate(
                name = "JSON Config",
                defaultFileName = "data.json",
                badge = "JSON",
                description = "Structured JSON data with mock API payload",
                content = """{
  "name": "aero-frontend",
  "version": "1.0.0",
  "author": "Developer",
  "config": {
    "theme": "dark",
    "features": ["preview", "console", "cursor-ai"]
  }
}
"""
            ),
            FrontendFileTemplate(
                name = "Markdown Docs",
                defaultFileName = "README.md",
                badge = "MD",
                description = "Project documentation with quick-start and preview guides",
                content = """# 🚀 Frontend Project

This web app runs in the AeroCode Mobile IDE with live browser execution.

## Files
- `index.html`: Web structure & DOM elements
- `style.css`: Visual presentation & theme
- `app.js`: Logic, event handlers & console output
"""
            ),
            FrontendFileTemplate(
                name = "Empty File",
                defaultFileName = "script.js",
                badge = "EMPTY",
                description = "Blank text buffer with no boilerplate",
                content = ""
            )
        )
    }

    var selectedTemplateIndex by remember { mutableStateOf(0) }
    var fileName by remember { mutableStateOf("index.html") }

    AlertDialog(
        onDismissRequest = { viewModel.activeModal.value = ActiveModal.NONE },
        containerColor = theme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.NoteAdd, contentDescription = null, tint = theme.accent, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add File to Project", color = theme.textPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = "Adds a single template file to '${viewModel.currentProjectName.collectAsState().value}'. Does not create another project.",
                    color = theme.textSecondary,
                    fontSize = 12.sp,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(10.dp))

                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("File Name", color = theme.textSecondary) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().testTag("new_file_name_input"),
                    textStyle = LocalTextStyle.current.copy(color = theme.textPrimary)
                )

                Spacer(modifier = Modifier.height(12.dp))
                Text("CHOOSE FRONTEND TEMPLATE", color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))

                templates.forEachIndexed { index, tmpl ->
                    val isSelected = selectedTemplateIndex == index
                    Surface(
                        onClick = {
                            selectedTemplateIndex = index
                            fileName = tmpl.defaultFileName
                        },
                        color = if (isSelected) theme.surfaceHighlight else theme.surface,
                        shape = RoundedCornerShape(8.dp),
                        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, theme.accent) else null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                color = if (isSelected) theme.accent else theme.surfaceHighlight,
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.height(20.dp)
                            ) {
                                Box(modifier = Modifier.padding(horizontal = 6.dp), contentAlignment = Alignment.Center) {
                                    Text(
                                        text = tmpl.badge,
                                        color = if (isSelected) theme.bg else theme.textSecondary,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tmpl.name,
                                    color = if (isSelected) theme.accent else theme.textPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = tmpl.description,
                                    color = theme.textSecondary,
                                    fontSize = 10.sp,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (fileName.isNotBlank()) {
                        val chosenContent = templates.getOrNull(selectedTemplateIndex)?.content ?: ""
                        viewModel.createNewFileInProject(fileName.trim(), chosenContent)
                    }
                    viewModel.activeModal.value = ActiveModal.NONE
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
            ) {
                Text("Create File", color = theme.bg, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = { viewModel.activeModal.value = ActiveModal.NONE }) {
                Text("Cancel", color = theme.textSecondary)
            }
        }
    )
}

