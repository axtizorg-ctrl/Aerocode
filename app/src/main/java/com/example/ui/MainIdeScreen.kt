package com.example.ui

import androidx.activity.compose.BackHandler
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.IdeTheme
import com.example.ui.ai.AiAssistantScreen
import com.example.ui.dialogs.IdeModals
import com.example.ui.editor.CodeEditorScreen
import com.example.ui.explorer.FileExplorerDrawer
import com.example.ui.git.GitScreen
import com.example.ui.problems.ProblemsScreen
import com.example.ui.terminal.TerminalScreen
import com.example.ui.web.FullScreenWebScreen
import com.example.viewmodel.ActiveModal
import com.example.viewmodel.BottomDockTab
import com.example.viewmodel.IdeViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainIdeScreen(
    viewModel: IdeViewModel,
    modifier: Modifier = Modifier
) {
    val theme by viewModel.theme.collectAsState()
    val currentProject by viewModel.currentProjectName.collectAsState()
    val isBottomDockExpanded by viewModel.isBottomDockExpanded.collectAsState()
    val activeDockTab by viewModel.activeDockTab.collectAsState()
    val problems by viewModel.problems.collectAsState()
    val isFullScreenWeb by viewModel.isFullScreenWebPreview.collectAsState()
    val terminalHeightFraction by viewModel.terminalHeightFraction.collectAsState()

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()

    // 1. FULL SCREEN WEB WINDOW (Replaces popup box for HTML/CSS/JS)
    if (isFullScreenWeb) {
        FullScreenWebScreen(
            viewModel = viewModel,
            onBack = { viewModel.isFullScreenWebPreview.value = false }
        )
        return
    }

    // Handle back button
    BackHandler(enabled = drawerState.isOpen) {
        coroutineScope.launch {
            drawerState.close()
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(theme.bg)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        val isWideScreen = maxWidth >= 768.dp

        if (isWideScreen) {
            // Tablet / Landscape Desktop-like Multi-Pane Layout
            TabletLandscapeLayout(viewModel = viewModel, theme = theme)
        } else {
            // Mobile-First Layout with Navigation Drawer & Bottom Dock
            ModalNavigationDrawer(
                drawerState = drawerState,
                drawerContent = {
                    ModalDrawerSheet(
                        drawerContainerColor = theme.surface,
                        modifier = Modifier.width(280.dp)
                    ) {
                        FileExplorerDrawer(
                            viewModel = viewModel,
                            onCloseDrawer = {
                                coroutineScope.launch {
                                    drawerState.close()
                                }
                            }
                        )
                    }
                }
            ) {
                Scaffold(
                    topBar = {
                        val activeTab = viewModel.activeTab
                        val displayTitle = activeTab?.title ?: currentProject
                        val displaySubtitle = if (activeTab != null) "projects/$currentProject" else "projects/"

                        MobileIdeTopBar(
                            title = displayTitle,
                            subtitle = displaySubtitle,
                            theme = theme,
                            onMenuClick = {
                                coroutineScope.launch {
                                    drawerState.open()
                                }
                            },
                            onCodexAiClick = { viewModel.triggerCodexAiAutoFix() },
                            onRunClick = { viewModel.runProjectOrCurrentFile() },
                            onEditClick = { viewModel.activeModal.value = ActiveModal.NEW_FILE },
                            onSettingsClick = { viewModel.activeModal.value = ActiveModal.SETTINGS }
                        )
                    },
                    containerColor = theme.bg
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        // Main Editor View (Dynamic Resizable Height)
                        val editorWeight = if (isBottomDockExpanded) {
                            (1f - terminalHeightFraction).coerceIn(0.15f, 0.85f)
                        } else 1f

                        CodeEditorScreen(
                            viewModel = viewModel,
                            modifier = Modifier.weight(editorWeight)
                        )

                        // Draggable / Adjustable Terminal Handle Bar (When dock is open)
                        if (isBottomDockExpanded) {
                            TerminalResizeHandle(
                                heightFraction = terminalHeightFraction,
                                onHeightFractionChange = { viewModel.terminalHeightFraction.value = it },
                                theme = theme
                            )
                        }

                        // Bottom Sliding Dock Bar (Tabs)
                        BottomDockBar(
                            theme = theme,
                            isExpanded = isBottomDockExpanded,
                            activeTab = activeDockTab,
                            problemCount = problems.size,
                            onToggleExpand = {
                                viewModel.isBottomDockExpanded.value = !viewModel.isBottomDockExpanded.value
                            },
                            onSelectTab = { tab ->
                                viewModel.activeDockTab.value = tab
                                viewModel.isBottomDockExpanded.value = true
                            }
                        )

                        // Bottom Sliding Dock Body (Adjustable Fraction)
                        AnimatedVisibility(
                            visible = isBottomDockExpanded,
                            enter = fadeIn(),
                            exit = fadeOut(),
                            modifier = Modifier.weight(terminalHeightFraction.coerceIn(0.15f, 0.85f))
                        ) {
                            when (activeDockTab) {
                                BottomDockTab.TERMINAL, BottomDockTab.OUTPUT -> TerminalScreen(viewModel = viewModel)
                                BottomDockTab.PROBLEMS -> ProblemsScreen(viewModel = viewModel)
                                BottomDockTab.AI -> AiAssistantScreen(viewModel = viewModel)
                                BottomDockTab.GIT -> GitScreen(viewModel = viewModel)
                            }
                        }
                    }
                }
            }
        }

        // Global Modals
        IdeModals(viewModel = viewModel)
    }
}

@Composable
fun MobileIdeTopBar(
    title: String,
    subtitle: String,
    theme: IdeTheme,
    onMenuClick: () -> Unit,
    onCodexAiClick: () -> Unit,
    onRunClick: () -> Unit,
    onEditClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    Surface(
        color = theme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Left: Hamburger + Title / Subtitle (Matches Screenshot)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.size(40.dp).testTag("top_menu_drawer_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Explorer Drawer",
                        tint = theme.textPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                Column {
                    Text(
                        text = title,
                        color = theme.textPrimary,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = subtitle,
                        color = theme.textSecondary,
                        fontSize = 11.sp,
                        maxLines = 1
                    )
                }
            }

            // Right: Pencil Edit, AI Codex Button, Play Button, More Menu (Matches Screenshot)
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Pencil Edit
                IconButton(
                    onClick = onEditClick,
                    modifier = Modifier.size(38.dp).testTag("top_edit_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Edit / New File",
                        tint = theme.textSecondary,
                        modifier = Modifier.size(19.dp)
                    )
                }

                // AI Codex Fix Button (Requested by user)
                Surface(
                    onClick = onCodexAiClick,
                    color = theme.accent,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .height(30.dp)
                        .padding(horizontal = 2.dp)
                        .testTag("top_ai_codex_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = "AI Fix",
                            tint = theme.bg,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "AI Fix",
                            color = theme.bg,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Spacer(modifier = Modifier.width(2.dp))

                // Play / Run Button (Triangle icon as in screenshot)
                IconButton(
                    onClick = onRunClick,
                    modifier = Modifier.size(40.dp).testTag("top_run_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Run",
                        tint = theme.functionColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Three Dots Menu
                IconButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.size(38.dp).testTag("top_settings_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More",
                        tint = theme.textSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun BottomDockBar(
    theme: IdeTheme,
    isExpanded: Boolean,
    activeTab: BottomDockTab,
    problemCount: Int,
    onToggleExpand: () -> Unit,
    onSelectTab: (BottomDockTab) -> Unit
) {
    Surface(
        color = theme.surface,
        modifier = Modifier.fillMaxWidth().height(36.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DockTabItem(
                    title = "Terminal",
                    isActive = isExpanded && activeTab == BottomDockTab.TERMINAL,
                    theme = theme,
                    onClick = { onSelectTab(BottomDockTab.TERMINAL) }
                )
                DockTabItem(
                    title = "Problems" + if (problemCount > 0) " ($problemCount)" else "",
                    isActive = isExpanded && activeTab == BottomDockTab.PROBLEMS,
                    theme = theme,
                    highlightBadge = problemCount > 0,
                    onClick = { onSelectTab(BottomDockTab.PROBLEMS) }
                )
                DockTabItem(
                    title = "Qwen AI",
                    isActive = isExpanded && activeTab == BottomDockTab.AI,
                    theme = theme,
                    onClick = { onSelectTab(BottomDockTab.AI) }
                )
                DockTabItem(
                    title = "Git",
                    isActive = isExpanded && activeTab == BottomDockTab.GIT,
                    theme = theme,
                    onClick = { onSelectTab(BottomDockTab.GIT) }
                )
            }

            IconButton(onClick = onToggleExpand, modifier = Modifier.size(28.dp).testTag("dock_toggle_expand")) {
                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = "Toggle Dock",
                    tint = theme.textSecondary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun DockTabItem(
    title: String,
    isActive: Boolean,
    theme: IdeTheme,
    highlightBadge: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (isActive) theme.surfaceHighlight else theme.surface,
        shape = RoundedCornerShape(4.dp),
        modifier = Modifier.padding(end = 4.dp).height(28.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title,
                color = if (highlightBadge) theme.errorColor else if (isActive) theme.accent else theme.textSecondary,
                fontSize = 11.sp,
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun TabletLandscapeLayout(
    viewModel: IdeViewModel,
    theme: IdeTheme
) {
    val currentProject by viewModel.currentProjectName.collectAsState()

    Column(modifier = Modifier.fillMaxSize()) {
        // Desktop Top Bar
        Surface(color = theme.surface, modifier = Modifier.fillMaxWidth().height(44.dp)) {
            Row(
                modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "AeroCode IDE",
                        color = theme.accent,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "Project: $currentProject",
                        color = theme.textPrimary,
                        fontSize = 13.sp
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Codex AI Fix Button
                    Button(
                        onClick = { viewModel.triggerCodexAiAutoFix() },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = theme.bg, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("AI Fix (Qwen)", color = theme.bg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(6.dp))

                    Button(
                        onClick = { viewModel.runProjectOrCurrentFile() },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.functionColor),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 2.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, tint = theme.bg, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("RUN", color = theme.bg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(onClick = { viewModel.activeModal.value = ActiveModal.COMMAND_PALETTE }) {
                        Icon(Icons.Default.Terminal, contentDescription = "Palette", tint = theme.textSecondary, modifier = Modifier.size(18.dp))
                    }
                    IconButton(onClick = { viewModel.activeModal.value = ActiveModal.SETTINGS }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings", tint = theme.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }
        }

        // 3-Column Split View: [ Explorer (240dp) | Editor + Bottom Terminal | AI Assistant (320dp) ]
        Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Left: Project Explorer
            FileExplorerDrawer(
                viewModel = viewModel,
                onCloseDrawer = {},
                modifier = Modifier.width(240.dp)
            )

            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(theme.surfaceHighlight))

            // Center: Editor + Bottom Dock
            val tabletTerminalFraction by viewModel.terminalHeightFraction.collectAsState()
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                CodeEditorScreen(
                    viewModel = viewModel,
                    modifier = Modifier.weight((1f - tabletTerminalFraction).coerceIn(0.2f, 0.8f))
                )

                TerminalResizeHandle(
                    heightFraction = tabletTerminalFraction,
                    onHeightFractionChange = { viewModel.terminalHeightFraction.value = it },
                    theme = theme
                )

                TerminalScreen(
                    viewModel = viewModel,
                    modifier = Modifier.weight(tabletTerminalFraction.coerceIn(0.2f, 0.8f))
                )
            }

            Box(modifier = Modifier.width(1.dp).fillMaxHeight().background(theme.surfaceHighlight))

            // Right: AI Assistant Panel
            AiAssistantScreen(
                viewModel = viewModel,
                modifier = Modifier.width(320.dp)
            )
        }
    }
}

@Composable
fun TerminalResizeHandle(
    heightFraction: Float,
    onHeightFractionChange: (Float) -> Unit,
    theme: IdeTheme,
    modifier: Modifier = Modifier
) {
    Surface(
        color = theme.surface,
        modifier = modifier
            .fillMaxWidth()
            .height(26.dp)
            .pointerInput(heightFraction) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    // Dragging upwards (negative drag) increases terminal fraction
                    val delta = -dragAmount / 800f
                    val newFraction = (heightFraction + delta).coerceIn(0.18f, 0.85f)
                    onHeightFractionChange(newFraction)
                }
            }
            .testTag("terminal_resize_handle")
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Height: ${(heightFraction * 100).toInt()}%",
                color = theme.textSecondary,
                fontSize = 10.sp
            )

            // Centered Drag Handle Pill
            Box(
                modifier = Modifier
                    .width(42.dp)
                    .height(4.dp)
                    .background(theme.surfaceHighlight, RoundedCornerShape(2.dp))
            )

            // Quick Preset Buttons
            Row(verticalAlignment = Alignment.CenterVertically) {
                ResizePresetChip(label = "30%", isSelected = heightFraction in 0.25f..0.35f, theme = theme) {
                    onHeightFractionChange(0.30f)
                }
                Spacer(modifier = Modifier.width(3.dp))
                ResizePresetChip(label = "50%", isSelected = heightFraction in 0.45f..0.55f, theme = theme) {
                    onHeightFractionChange(0.50f)
                }
                Spacer(modifier = Modifier.width(3.dp))
                ResizePresetChip(label = "75%", isSelected = heightFraction in 0.70f..0.80f, theme = theme) {
                    onHeightFractionChange(0.75f)
                }
                Spacer(modifier = Modifier.width(3.dp))
                ResizePresetChip(label = "Max", isSelected = heightFraction > 0.80f, theme = theme) {
                    onHeightFractionChange(0.85f)
                }
            }
        }
    }
}

@Composable
fun ResizePresetChip(
    label: String,
    isSelected: Boolean,
    theme: IdeTheme,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (isSelected) theme.accent else theme.surfaceHighlight,
        shape = RoundedCornerShape(3.dp),
        modifier = Modifier.height(18.dp)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (isSelected) theme.bg else theme.textPrimary,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
