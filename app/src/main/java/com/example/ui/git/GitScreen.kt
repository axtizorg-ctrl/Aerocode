package com.example.ui.git

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.model.GitCommit
import com.example.data.model.GitConflictFile
import com.example.data.model.GitRemote
import com.example.data.model.IdeTheme
import com.example.data.repository.GitRepository
import com.example.viewmodel.IdeViewModel

@Composable
fun GitScreen(
    viewModel: IdeViewModel,
    modifier: Modifier = Modifier
) {
    val theme by viewModel.theme.collectAsState()
    val gitState by viewModel.gitRepo.gitState.collectAsState()
    val currentProject by viewModel.currentProjectName.collectAsState()

    var commitMessage by remember { mutableStateOf("") }
    var selectedBranch by remember { mutableStateOf<String?>(null) }
    var isCreatingBranch by remember { mutableStateOf(false) }
    var newBranchName by remember { mutableStateOf("") }

    var isAddingRemote by remember { mutableStateOf(false) }
    var remoteNameInput by remember { mutableStateOf("") }
    var remoteUrlInput by remember { mutableStateOf("") }

    var isStashing by remember { mutableStateOf(false) }
    var stashMessageInput by remember { mutableStateOf("") }

    // Sub-dialogs and viewers
    var diffFilePath by remember { mutableStateOf<String?>(null) }
    var diffIsStaged by remember { mutableStateOf(false) }
    var diffContent by remember { mutableStateOf<String?>(null) }

    var activeConflictFile by remember { mutableStateOf<GitConflictFile?>(null) }
    var selectedCommitDetails by remember { mutableStateOf<Pair<String, String>?>(null) }

    // Warning confirmation for destructive ops
    var destructiveWarningPrompt by remember { mutableStateOf<Pair<String, () -> Unit>?>(null) }

    // Refresh state when entering Git tab
    LaunchedEffect(currentProject) {
        viewModel.refreshGit()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.bg)
            .padding(10.dp)
    ) {
        // Not a Git repo banner
        if (!gitState.isGitRepo) {
            Surface(
                color = theme.surface,
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().padding(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.Source, contentDescription = null, tint = theme.accent, modifier = Modifier.size(36.dp))
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Current Project is not a Git repository",
                        color = theme.textPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Initialize Git or clone a remote repository to start version control.",
                        color = theme.textSecondary,
                        fontSize = 11.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Row {
                        Button(
                            onClick = { viewModel.initGitRepository() },
                            colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                        ) {
                            Text("Initialize Git (main)", color = theme.bg, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
            return
        }

        // 1. Branch Header & Comprehensive Tool Actions (Commit, Push, Pull, Fetch, Branch, Merge, Stash, Refresh)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable { isCreatingBranch = true }
            ) {
                Icon(
                    imageVector = Icons.Default.AltRoute,
                    contentDescription = "Branch",
                    tint = theme.accent,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = gitState.branch,
                            color = theme.textPrimary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = theme.textSecondary, modifier = Modifier.size(16.dp))
                    }
                    if (gitState.aheadCount > 0 || gitState.behindCount > 0) {
                        Text(
                            text = "↑${gitState.aheadCount} ahead  ↓${gitState.behindCount} behind",
                            color = theme.accent,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Quick Status Actions Row
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = { viewModel.refreshGit() },
                    modifier = Modifier.size(32.dp).testTag("git_refresh")
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "REFRESH", tint = theme.textSecondary, modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = { viewModel.gitStageAll() },
                    modifier = Modifier.size(32.dp).testTag("git_stage_all")
                ) {
                    Icon(Icons.Default.SelectAll, contentDescription = "Stage All", tint = theme.functionColor, modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = { viewModel.gitPull() },
                    modifier = Modifier.size(32.dp).testTag("git_pull")
                ) {
                    Icon(Icons.Default.CloudDownload, contentDescription = "PULL", tint = theme.accent, modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = { viewModel.gitPush() },
                    modifier = Modifier.size(32.dp).testTag("git_push")
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = "PUSH", tint = theme.accent, modifier = Modifier.size(16.dp))
                }
                IconButton(
                    onClick = { viewModel.gitFetch() },
                    modifier = Modifier.size(32.dp).testTag("git_fetch")
                ) {
                    Icon(Icons.Default.Sync, contentDescription = "FETCH", tint = theme.textSecondary, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        // 2. Toolbar for BRANCH, MERGE, STASH, REMOTE, QWEN AI
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            GitActionChip("BRANCH", Icons.Default.CallSplit, theme) { isCreatingBranch = true }
            GitActionChip("MERGE", Icons.Default.MergeType, theme) {
                if (gitState.branches.size > 1) {
                    val other = gitState.branches.firstOrNull { it != gitState.branch } ?: "main"
                    viewModel.gitMerge(other)
                }
            }
            GitActionChip("STASH", Icons.Default.Archive, theme) { isStashing = true }
            GitActionChip("REMOTE", Icons.Default.Link, theme) { isAddingRemote = true }
            GitActionChip("AI Commit Msg", Icons.Default.AutoAwesome, theme, isAi = true) {
                viewModel.generateAiCommitMessage()
            }
            GitActionChip("AI Explain Git", Icons.Default.Psychology, theme, isAi = true) {
                viewModel.explainGitStatusWithQwen()
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 3. Commit Input Box with AI message auto-fill
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = commitMessage,
                onValueChange = { commitMessage = it },
                placeholder = { Text("Commit message (Ctrl+Enter)", color = theme.textSecondary, fontSize = 12.sp) },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .testTag("git_commit_input"),
                textStyle = LocalTextStyle.current.copy(color = theme.textPrimary, fontSize = 12.sp)
            )

            Spacer(modifier = Modifier.width(6.dp))

            Button(
                onClick = {
                    if (commitMessage.isNotBlank()) {
                        viewModel.gitCommit(commitMessage)
                        commitMessage = ""
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .height(48.dp)
                    .testTag("git_commit_button")
            ) {
                Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("COMMIT", color = theme.bg, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 4. File status lists & history in LazyColumn
        LazyColumn(modifier = Modifier.weight(1f)) {
            // MERGE CONFLICTS (CRITICAL)
            if (gitState.conflictFiles.isNotEmpty()) {
                item {
                    SectionHeader(
                        title = "⚠️ MERGE CONFLICTS (${gitState.conflictFiles.size})",
                        theme = theme,
                        color = theme.errorColor
                    )
                }
                items(gitState.conflictFiles) { conflict ->
                    Surface(
                        color = theme.errorColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = conflict.filePath,
                                    color = theme.errorColor,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "Conflict detected. Tap Resolve to choose changes.",
                                    color = theme.textSecondary,
                                    fontSize = 10.sp
                                )
                            }
                            Button(
                                onClick = { activeConflictFile = conflict },
                                colors = ButtonDefaults.buttonColors(containerColor = theme.errorColor),
                                modifier = Modifier.height(28.dp)
                            ) {
                                Text("Resolve", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // STAGED CHANGES
            if (gitState.stagedFiles.isNotEmpty()) {
                item {
                    SectionHeader(title = "STAGED CHANGES (${gitState.stagedFiles.size})", theme = theme)
                }
                items(gitState.stagedFiles) { file ->
                    GitFileRow(
                        fileName = file,
                        statusBadge = "S",
                        badgeColor = theme.functionColor,
                        theme = theme,
                        actionIcon = Icons.Default.Remove,
                        onAction = { viewModel.gitUnstageFile(file) },
                        onDiff = {
                            viewModel.viewGitDiff(file, staged = true) { diff ->
                                diffFilePath = file
                                diffIsStaged = true
                                diffContent = diff
                            }
                        },
                        onOpenFile = { viewModel.openProjectFile(file) }
                    )
                }
            }

            // MODIFIED CHANGES
            if (gitState.modifiedFiles.isNotEmpty()) {
                item {
                    SectionHeader(title = "MODIFIED (${gitState.modifiedFiles.size})", theme = theme)
                }
                items(gitState.modifiedFiles) { file ->
                    GitFileRow(
                        fileName = file,
                        statusBadge = "M",
                        badgeColor = theme.warningColor,
                        theme = theme,
                        actionIcon = Icons.Default.Add,
                        onAction = { viewModel.gitStageFile(file) },
                        onDiff = {
                            viewModel.viewGitDiff(file, staged = false) { diff ->
                                diffFilePath = file
                                diffIsStaged = false
                                diffContent = diff
                            }
                        },
                        onOpenFile = { viewModel.openProjectFile(file) }
                    )
                }
            }

            // UNTRACKED FILES
            if (gitState.untrackedFiles.isNotEmpty()) {
                item {
                    SectionHeader(title = "UNTRACKED (${gitState.untrackedFiles.size})", theme = theme)
                }
                items(gitState.untrackedFiles) { file ->
                    GitFileRow(
                        fileName = file,
                        statusBadge = "U",
                        badgeColor = theme.textSecondary,
                        theme = theme,
                        actionIcon = Icons.Default.Add,
                        onAction = { viewModel.gitStageFile(file) },
                        onDiff = {
                            viewModel.viewGitDiff(file, staged = false) { diff ->
                                diffFilePath = file
                                diffIsStaged = false
                                diffContent = diff
                            }
                        },
                        onOpenFile = { viewModel.openProjectFile(file) }
                    )
                }
            }

            // DELETED FILES
            if (gitState.deletedFiles.isNotEmpty()) {
                item {
                    SectionHeader(title = "DELETED (${gitState.deletedFiles.size})", theme = theme)
                }
                items(gitState.deletedFiles) { file ->
                    GitFileRow(
                        fileName = file,
                        statusBadge = "D",
                        badgeColor = theme.errorColor,
                        theme = theme,
                        actionIcon = Icons.Default.Add,
                        onAction = { viewModel.gitStageFile(file) },
                        onDiff = {
                            viewModel.viewGitDiff(file, staged = false) { diff ->
                                diffFilePath = file
                                diffIsStaged = false
                                diffContent = diff
                            }
                        },
                        onOpenFile = {}
                    )
                }
            }

            // STASHES LIST
            if (gitState.stashes.isNotEmpty()) {
                item {
                    SectionHeader(title = "STASHES (${gitState.stashes.size})", theme = theme)
                }
                items(gitState.stashes) { stashItem ->
                    Surface(
                        color = theme.surface,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "stash@{${stashItem.index}}: ${stashItem.message}",
                                color = theme.textPrimary,
                                fontSize = 11.sp,
                                modifier = Modifier.weight(1f)
                            )
                            Row {
                                TextButton(onClick = { viewModel.gitApplyStash(stashItem.index, pop = false) }) {
                                    Text("Apply", color = theme.accent, fontSize = 10.sp)
                                }
                                TextButton(onClick = { viewModel.gitApplyStash(stashItem.index, pop = true) }) {
                                    Text("Pop", color = theme.functionColor, fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }

            // REMOTES LIST
            if (gitState.remotes.isNotEmpty()) {
                item {
                    SectionHeader(title = "REMOTES (${gitState.remotes.size})", theme = theme)
                }
                items(gitState.remotes) { remote ->
                    Surface(
                        color = theme.surface,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = remote.name, color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(text = remote.url, color = theme.textSecondary, fontSize = 10.sp)
                            }
                            IconButton(
                                onClick = { viewModel.gitRemoveRemote(remote.name) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove Remote", tint = theme.errorColor, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }
            }

            // RECENT COMMITS
            item {
                Spacer(modifier = Modifier.height(10.dp))
                SectionHeader(title = "RECENT COMMITS (${gitState.commits.size})", theme = theme)
            }
            items(gitState.commits) { commit ->
                GitCommitItem(
                    commit = commit,
                    theme = theme,
                    onClick = {
                        viewModel.viewCommitDetails(commit.hash) { details ->
                            selectedCommitDetails = Pair(commit.hash, details)
                        }
                    },
                    onRevert = {
                        destructiveWarningPrompt = Pair("Revert commit ${commit.hash}?") {
                            viewModel.gitRevert(commit.hash)
                        }
                    }
                )
            }
        }
    }

    // --- MODALS & DIALOGS ---

    // 1. Mobile-Friendly Diff Viewer (Old vs New & Open Affected File)
    if (diffFilePath != null && diffContent != null) {
        val diffFontSize by viewModel.fontSize.collectAsState()
        AlertDialog(
            onDismissRequest = { diffFilePath = null; diffContent = null },
            containerColor = theme.surface,
            title = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Diff: ${diffFilePath?.substringAfterLast('/')}",
                        color = theme.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(onClick = {
                        val file = diffFilePath
                        diffFilePath = null
                        diffContent = null
                        if (file != null) viewModel.openProjectFile(file)
                    }) {
                        Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(14.dp), tint = theme.accent)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Open File", color = theme.accent, fontSize = 11.sp)
                    }
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .pointerInput(Unit) {
                            detectTransformGestures { _, _, zoom, _ ->
                                if (kotlin.math.abs(zoom - 1f) > 0.02f) {
                                    viewModel.adjustFontSizeByZoom(zoom)
                                }
                            }
                        }
                        .verticalScroll(rememberScrollState())
                ) {
                    diffContent!!.lines().forEach { line ->
                        val (lineBg, lineFg) = when {
                            line.startsWith("+") && !line.startsWith("+++") -> Pair(theme.functionColor.copy(alpha = 0.2f), theme.functionColor)
                            line.startsWith("-") && !line.startsWith("---") -> Pair(theme.errorColor.copy(alpha = 0.2f), theme.errorColor)
                            line.startsWith("@@") -> Pair(theme.accent.copy(alpha = 0.15f), theme.accent)
                            else -> Pair(Color.Transparent, theme.textPrimary)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(lineBg)
                                .padding(horizontal = 4.dp, vertical = 1.dp)
                        ) {
                            Text(
                                text = line,
                                color = lineFg,
                                fontSize = diffFontSize.sp
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { diffFilePath = null; diffContent = null },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                ) {
                    Text("Close", color = theme.bg)
                }
            }
        )
    }

    // 2. Merge Conflict Resolver Modal (Keep Current, Keep Incoming, Keep Both, Manual Edit)
    if (activeConflictFile != null) {
        val conflict = activeConflictFile!!
        AlertDialog(
            onDismissRequest = { activeConflictFile = null },
            containerColor = theme.surface,
            title = {
                Text(
                    text = "Merge Conflict in ${conflict.filePath.substringAfterLast('/')}",
                    color = theme.errorColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text("Select how to resolve this conflict:", color = theme.textSecondary, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("CURRENT CHANGE (HEAD):", color = theme.accent, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Surface(
                        color = theme.surfaceHighlight,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(conflict.currentSnippet, color = theme.textPrimary, fontSize = 11.sp, modifier = Modifier.padding(6.dp))
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("INCOMING CHANGE:", color = theme.functionColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Surface(
                        color = theme.surfaceHighlight,
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    ) {
                        Text(conflict.incomingSnippet, color = theme.textPrimary, fontSize = 11.sp, modifier = Modifier.padding(6.dp))
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = {
                            viewModel.gitResolveConflict(conflict.filePath, GitRepository.ConflictResolution.KEEP_CURRENT)
                            activeConflictFile = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.accent),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Keep Current (HEAD)", color = theme.bg, fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            viewModel.gitResolveConflict(conflict.filePath, GitRepository.ConflictResolution.KEEP_INCOMING)
                            activeConflictFile = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.functionColor),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Keep Incoming", color = theme.bg, fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Button(
                        onClick = {
                            viewModel.gitResolveConflict(conflict.filePath, GitRepository.ConflictResolution.KEEP_BOTH)
                            activeConflictFile = null
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = theme.surfaceHighlight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Keep Both", color = theme.textPrimary, fontSize = 11.sp)
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedButton(
                        onClick = {
                            val f = conflict.filePath
                            activeConflictFile = null
                            viewModel.openProjectFile(f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Manual Edit in Code Editor", color = theme.accent, fontSize = 11.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { activeConflictFile = null }) {
                    Text("Cancel", color = theme.textSecondary)
                }
            }
        )
    }

    // 3. Branch Management Dialog (Switch, Create, Rename, Delete)
    if (isCreatingBranch) {
        AlertDialog(
            onDismissRequest = { isCreatingBranch = false },
            containerColor = theme.surface,
            title = { Text("Branches & Checkout", color = theme.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("EXISTING BRANCHES:", color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    gitState.branches.forEach { br ->
                        val isCurr = br == gitState.branch
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (!isCurr) {
                                        viewModel.gitSwitchBranch(br)
                                        isCreatingBranch = false
                                    }
                                }
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = br + if (isCurr) " (current)" else "",
                                color = if (isCurr) theme.accent else theme.textPrimary,
                                fontSize = 12.sp,
                                fontWeight = if (isCurr) FontWeight.Bold else FontWeight.Normal
                            )
                            if (!isCurr) {
                                IconButton(
                                    onClick = {
                                        destructiveWarningPrompt = Pair("Delete branch '$br'?") {
                                            viewModel.gitDeleteBranch(br, force = false)
                                        }
                                        isCreatingBranch = false
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = theme.errorColor, modifier = Modifier.size(14.dp))
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Divider(color = theme.surfaceHighlight)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("CREATE NEW BRANCH:", color = theme.accent, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = newBranchName,
                        onValueChange = { newBranchName = it },
                        placeholder = { Text("feature/my-feature", color = theme.textSecondary, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(color = theme.textPrimary, fontSize = 12.sp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newBranchName.isNotBlank()) {
                            viewModel.gitCreateBranch(newBranchName, checkout = true)
                            newBranchName = ""
                            isCreatingBranch = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                ) {
                    Text("Create & Checkout", color = theme.bg, fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { isCreatingBranch = false }) {
                    Text("Close", color = theme.textSecondary)
                }
            }
        )
    }

    // 4. Stash Dialog
    if (isStashing) {
        AlertDialog(
            onDismissRequest = { isStashing = false },
            containerColor = theme.surface,
            title = { Text("Stash Working Changes", color = theme.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = stashMessageInput,
                    onValueChange = { stashMessageInput = it },
                    placeholder = { Text("Optional stash message...", color = theme.textSecondary, fontSize = 12.sp) },
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = LocalTextStyle.current.copy(color = theme.textPrimary, fontSize = 12.sp)
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.gitStash(stashMessageInput)
                        stashMessageInput = ""
                        isStashing = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                ) {
                    Text("Stash", color = theme.bg)
                }
            },
            dismissButton = {
                TextButton(onClick = { isStashing = false }) {
                    Text("Cancel", color = theme.textSecondary)
                }
            }
        )
    }

    // 5. Add Remote Dialog
    if (isAddingRemote) {
        AlertDialog(
            onDismissRequest = { isAddingRemote = false },
            containerColor = theme.surface,
            title = { Text("Add Git Remote", color = theme.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = remoteNameInput,
                        onValueChange = { remoteNameInput = it },
                        placeholder = { Text("Remote name (e.g. origin)", color = theme.textSecondary, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(color = theme.textPrimary, fontSize = 12.sp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = remoteUrlInput,
                        onValueChange = { remoteUrlInput = it },
                        placeholder = { Text("URL (https://github.com/...)", color = theme.textSecondary, fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        textStyle = LocalTextStyle.current.copy(color = theme.textPrimary, fontSize = 12.sp)
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (remoteNameInput.isNotBlank() && remoteUrlInput.isNotBlank()) {
                            viewModel.gitAddRemote(remoteNameInput, remoteUrlInput)
                            remoteNameInput = ""
                            remoteUrlInput = ""
                            isAddingRemote = false
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                ) {
                    Text("Add Remote", color = theme.bg)
                }
            },
            dismissButton = {
                TextButton(onClick = { isAddingRemote = false }) {
                    Text("Cancel", color = theme.textSecondary)
                }
            }
        )
    }

    // 6. Commit Details Modal
    if (selectedCommitDetails != null) {
        AlertDialog(
            onDismissRequest = { selectedCommitDetails = null },
            containerColor = theme.surface,
            title = { Text("Commit ${selectedCommitDetails!!.first}", color = theme.accent, fontSize = 14.sp, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 350.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = selectedCommitDetails!!.second,
                        color = theme.textPrimary,
                        fontSize = 11.sp
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { selectedCommitDetails = null },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.accent)
                ) {
                    Text("Close", color = theme.bg)
                }
            }
        )
    }

    // 7. Destructive Warning Modal
    if (destructiveWarningPrompt != null) {
        val (prompt, action) = destructiveWarningPrompt!!
        AlertDialog(
            onDismissRequest = { destructiveWarningPrompt = null },
            containerColor = theme.surface,
            icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = theme.errorColor) },
            title = { Text("Confirm Git Action", color = theme.errorColor, fontSize = 15.sp, fontWeight = FontWeight.Bold) },
            text = { Text(prompt, color = theme.textPrimary, fontSize = 12.sp) },
            confirmButton = {
                Button(
                    onClick = {
                        action()
                        destructiveWarningPrompt = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = theme.errorColor)
                ) {
                    Text("Proceed", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { destructiveWarningPrompt = null }) {
                    Text("Cancel", color = theme.textSecondary)
                }
            }
        )
    }
}

@Composable
fun GitActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    theme: IdeTheme,
    isAi: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (isAi) theme.accent.copy(alpha = 0.2f) else theme.surfaceHighlight,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.padding(end = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isAi) theme.accent else theme.textSecondary,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                color = if (isAi) theme.accent else theme.textPrimary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun SectionHeader(title: String, theme: IdeTheme, color: Color = theme.textSecondary) {
    Text(
        text = title,
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
fun GitFileRow(
    fileName: String,
    statusBadge: String,
    badgeColor: Color,
    theme: IdeTheme,
    actionIcon: androidx.compose.ui.graphics.vector.ImageVector,
    onAction: () -> Unit,
    onDiff: () -> Unit,
    onOpenFile: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            color = badgeColor.copy(alpha = 0.2f),
            shape = RoundedCornerShape(3.dp)
        ) {
            Text(
                text = statusBadge,
                color = badgeColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = fileName,
            color = theme.textPrimary,
            fontSize = 12.sp,
            modifier = Modifier
                .weight(1f)
                .clickable { onOpenFile() }
        )
        // Diff button
        IconButton(onClick = onDiff, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Difference, contentDescription = "View Diff", tint = theme.accent, modifier = Modifier.size(14.dp))
        }
        // Stage / Unstage action button
        IconButton(onClick = onAction, modifier = Modifier.size(24.dp)) {
            Icon(actionIcon, contentDescription = null, tint = theme.textSecondary, modifier = Modifier.size(14.dp))
        }
    }
}

@Composable
fun GitCommitItem(
    commit: GitCommit,
    theme: IdeTheme,
    onClick: () -> Unit,
    onRevert: () -> Unit
) {
    Surface(
        color = theme.surface,
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = commit.hash,
                    color = theme.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = commit.date,
                        color = theme.textSecondary,
                        fontSize = 10.sp
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = onRevert,
                        modifier = Modifier.size(20.dp)
                    ) {
                        Icon(Icons.Default.Undo, contentDescription = "Revert Commit", tint = theme.textSecondary, modifier = Modifier.size(12.dp))
                    }
                }
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = commit.message,
                color = theme.textPrimary,
                fontSize = 12.sp
            )
            Text(
                text = commit.author,
                color = theme.textSecondary,
                fontSize = 10.sp
            )
        }
    }
}
