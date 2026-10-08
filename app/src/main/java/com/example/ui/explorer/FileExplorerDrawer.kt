package com.example.ui.explorer

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.IdeTheme
import com.example.data.model.ProjectItem
import com.example.viewmodel.ActiveModal
import com.example.viewmodel.IdeViewModel

@Composable
fun FileExplorerDrawer(
    viewModel: IdeViewModel,
    onCloseDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme by viewModel.theme.collectAsState()
    val currentProject by viewModel.currentProjectName.collectAsState()
    val projects by viewModel.projects.collectAsState()
    val files by viewModel.projectFiles.collectAsState()
    val context = LocalContext.current

    var selectedItemForMenu by remember { mutableStateOf<ProjectItem?>(null) }
    var showProjectDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(theme.surface)
            .padding(vertical = 12.dp)
    ) {
        // 1. Project Title & Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Box {
                Row(
                    modifier = Modifier
                        .clickable { showProjectDropdown = true }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderSpecial,
                        contentDescription = null,
                        tint = theme.accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = currentProject,
                        color = theme.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Switch project",
                        tint = theme.textSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = showProjectDropdown,
                    onDismissRequest = { showProjectDropdown = false },
                    modifier = Modifier.background(theme.surfaceHighlight)
                ) {
                    Text(
                        text = "RECENT PROJECTS",
                        color = theme.textSecondary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                    projects.forEach { proj ->
                        DropdownMenuItem(
                            text = { Text(proj, color = theme.textPrimary, fontSize = 13.sp) },
                            onClick = {
                                viewModel.switchProject(proj)
                                showProjectDropdown = false
                            }
                        )
                    }
                    Divider(color = theme.surface)
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Add, contentDescription = null, tint = theme.accent, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("New Project...", color = theme.accent, fontSize = 13.sp)
                            }
                        },
                        onClick = {
                            showProjectDropdown = false
                            viewModel.activeModal.value = ActiveModal.NEW_PROJECT
                        }
                    )
                }
            }

            // Quick Actions: New File / Refresh
            Row {
                IconButton(
                    onClick = { viewModel.activeModal.value = ActiveModal.NEW_FILE },
                    modifier = Modifier.size(28.dp).testTag("explorer_new_file_icon")
                ) {
                    Icon(Icons.Default.NoteAdd, contentDescription = "New File", tint = theme.textSecondary, modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = { viewModel.refreshProjectTree() },
                    modifier = Modifier.size(28.dp).testTag("explorer_refresh_icon")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = theme.textSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }

        Divider(
            color = theme.surfaceHighlight,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        // 2. Tree list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        ) {
            items(files) { item ->
                ProjectTreeItem(
                    item = item,
                    theme = theme,
                    level = 0,
                    onFileClick = { fileItem ->
                        viewModel.openFile(fileItem.path)
                        onCloseDrawer()
                    },
                    onItemLongClick = { fileItem ->
                        selectedItemForMenu = fileItem
                    }
                )
            }
        }

        // Context Menu Dialog
        if (selectedItemForMenu != null) {
            val item = selectedItemForMenu!!
            AlertDialog(
                onDismissRequest = { selectedItemForMenu = null },
                containerColor = theme.surfaceHighlight,
                title = {
                    Text(
                        text = item.name,
                        color = theme.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column {
                        ListItem(
                            headlineContent = { Text("Duplicate", color = theme.textPrimary, fontSize = 13.sp) },
                            leadingContent = { Icon(Icons.Default.ContentCopy, contentDescription = null, tint = theme.accent) },
                            modifier = Modifier.clickable {
                                viewModel.projectRepo.duplicateFile(item.path)
                                viewModel.refreshProjectTree()
                                selectedItemForMenu = null
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Share File", color = theme.textPrimary, fontSize = 13.sp) },
                            leadingContent = { Icon(Icons.Default.Share, contentDescription = null, tint = theme.accent) },
                            modifier = Modifier.clickable {
                                val content = viewModel.projectRepo.readFile(item.path)
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, item.name)
                                    putExtra(Intent.EXTRA_TEXT, content)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Share ${item.name}"))
                                selectedItemForMenu = null
                            }
                        )
                        ListItem(
                            headlineContent = { Text("Delete", color = theme.errorColor, fontSize = 13.sp) },
                            leadingContent = { Icon(Icons.Default.Delete, contentDescription = null, tint = theme.errorColor) },
                            modifier = Modifier.clickable {
                                viewModel.deleteFileFromProject(item.path)
                                selectedItemForMenu = null
                            }
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = { selectedItemForMenu = null }) {
                        Text("Cancel", color = theme.textSecondary)
                    }
                }
            )
        }
    }
}

@Composable
fun ProjectTreeItem(
    item: ProjectItem,
    theme: IdeTheme,
    level: Int,
    onFileClick: (ProjectItem) -> Unit,
    onItemLongClick: (ProjectItem) -> Unit
) {
    var isExpanded by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = (level * 14).dp)
                .clickable {
                    if (item.isDirectory) {
                        isExpanded = !isExpanded
                    } else {
                        onFileClick(item)
                    }
                }
                .padding(vertical = 4.dp, horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            val icon = when {
                item.isDirectory && isExpanded -> Icons.Default.FolderOpen
                item.isDirectory -> Icons.Default.Folder
                item.extension in listOf("cpp", "c", "h", "hpp") -> Icons.Default.Code
                item.extension == "py" -> Icons.Default.Terminal
                item.extension in listOf("js", "ts", "html", "css") -> Icons.Default.Language
                item.extension == "java" -> Icons.Default.Memory
                else -> Icons.Default.InsertDriveFile
            }

            val iconTint = when {
                item.isDirectory -> theme.accent
                item.extension in listOf("cpp", "c") -> theme.keywordColor
                item.extension == "py" -> theme.functionColor
                item.extension in listOf("js", "html") -> theme.warningColor
                else -> theme.textSecondary
            }

            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(16.dp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Text(
                text = item.name,
                color = theme.textPrimary,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = { onItemLongClick(item) },
                modifier = Modifier.size(20.dp)
            ) {
                Icon(
                    Icons.Default.MoreVert,
                    contentDescription = "Options",
                    tint = theme.textSecondary,
                    modifier = Modifier.size(12.dp)
                )
            }
        }

        if (item.isDirectory && isExpanded) {
            item.children.forEach { child ->
                ProjectTreeItem(
                    item = child,
                    theme = theme,
                    level = level + 1,
                    onFileClick = onFileClick,
                    onItemLongClick = onItemLongClick
                )
            }
        }
    }
}
