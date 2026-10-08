package com.example.data.repository

import com.example.data.model.GitCommit
import com.example.data.model.GitConflictFile
import com.example.data.model.GitRemote
import com.example.data.model.GitStashItem
import com.example.data.model.GitState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

class GitRepository(
    private val projectRepo: ProjectRepository,
    private val termuxRepo: TermuxRepository
) {

    private val _gitState = MutableStateFlow(GitState())
    val gitState: StateFlow<GitState> = _gitState.asStateFlow()

    private val TERMUX_USR_BIN = "/data/data/com.termux/files/usr/bin"

    data class GitCommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    ) {
        val isSuccess: Boolean get() = exitCode == 0
    }

    /**
     * Executes git commands in the specified directory using Termux/system git.
     */
    suspend fun runGit(args: String, workingDir: File): GitCommandResult = withContext(Dispatchers.IO) {
        try {
            val builder = ProcessBuilder("sh", "-c", "git $args")
            builder.directory(workingDir)

            val env = builder.environment()
            env["TERM"] = "xterm-256color"
            env["HOME"] = workingDir.absolutePath
            val currentPath = env["PATH"] ?: "/system/bin:/system/xbin"
            env["PATH"] = "$TERMUX_USR_BIN:$currentPath"

            val proc = builder.start()
            val stdout = proc.inputStream.bufferedReader().readText()
            val stderr = proc.errorStream.bufferedReader().readText()
            val code = proc.waitFor()

            GitCommandResult(code, stdout.trim(), stderr.trim())
        } catch (e: Exception) {
            GitCommandResult(-1, "", e.message ?: "Unknown process error")
        }
    }

    /**
     * Refreshes the full Git state for the specified project directory.
     */
    suspend fun refreshState(projectName: String): GitState = withContext(Dispatchers.IO) {
        val dir = projectRepo.getProjectDirectory(projectName)
        val gitDir = File(dir, ".git")

        if (!gitDir.exists() || !gitDir.isDirectory) {
            val emptyState = GitState(
                isGitRepo = false,
                branch = "none",
                remotes = emptyList(),
                branches = emptyList(),
                modifiedFiles = emptyList(),
                stagedFiles = emptyList(),
                untrackedFiles = emptyList(),
                deletedFiles = emptyList(),
                conflictFiles = emptyList(),
                stashes = emptyList(),
                aheadCount = 0,
                behindCount = 0,
                commits = emptyList()
            )
            _gitState.value = emptyState
            return@withContext emptyState
        }

        // 1. Current branch
        val branchRes = runGit("rev-parse --abbrev-ref HEAD", dir)
        val currentBranch = if (branchRes.isSuccess && branchRes.stdout.isNotBlank()) branchRes.stdout else "main"

        // 2. All branches
        val branchListRes = runGit("branch --format='%(refname:short)'", dir)
        val branches = if (branchListRes.isSuccess) {
            branchListRes.stdout.lines().map { it.trim().trim('\'') }.filter { it.isNotBlank() }
        } else listOf(currentBranch)

        // 3. Status (porcelain)
        val statusRes = runGit("status --porcelain=v1", dir)
        val staged = mutableListOf<String>()
        val modified = mutableListOf<String>()
        val untracked = mutableListOf<String>()
        val deleted = mutableListOf<String>()
        val conflictFiles = mutableListOf<GitConflictFile>()

        if (statusRes.isSuccess && statusRes.stdout.isNotBlank()) {
            statusRes.stdout.lines().forEach { rawLine ->
                if (rawLine.length >= 3) {
                    val code = rawLine.substring(0, 2)
                    val file = rawLine.substring(3).trim()

                    when {
                        // Conflicts: UU, AA, UD, DU, DD, AU, UA
                        code in listOf("UU", "AA", "UD", "DU", "DD", "AU", "UA") -> {
                            val targetFile = File(dir, file)
                            val content = if (targetFile.exists()) targetFile.readText() else ""
                            conflictFiles.add(parseConflictFile(file, content))
                        }
                        // Staged in index (X != ' ' and X != '?')
                        code[0] in listOf('M', 'A', 'R', 'C') -> staged.add(file)
                        code[0] == 'D' -> {
                            staged.add(file)
                            deleted.add(file)
                        }
                    }

                    // Worktree status (Y)
                    when (code[1]) {
                        'M' -> modified.add(file)
                        'D' -> {
                            modified.add(file)
                            deleted.add(file)
                        }
                        '?' -> untracked.add(file)
                    }
                }
            }
        }

        // 4. Remotes
        val remotesRes = runGit("remote -v", dir)
        val remotes = mutableListOf<GitRemote>()
        if (remotesRes.isSuccess && remotesRes.stdout.isNotBlank()) {
            val seen = mutableSetOf<String>()
            remotesRes.stdout.lines().forEach { line ->
                val parts = line.split("\\s+".toRegex())
                if (parts.size >= 2 && !seen.contains(parts[0])) {
                    seen.add(parts[0])
                    remotes.add(GitRemote(parts[0], parts[1]))
                }
            }
        }

        // 5. Ahead / Behind
        var ahead = 0
        var behind = 0
        val revListRes = runGit("rev-list --left-right --count HEAD...@{upstream}", dir)
        if (revListRes.isSuccess && revListRes.stdout.isNotBlank()) {
            val counts = revListRes.stdout.split("\\s+".toRegex())
            if (counts.size >= 2) {
                ahead = counts[0].toIntOrNull() ?: 0
                behind = counts[1].toIntOrNull() ?: 0
            }
        }

        // 6. Recent Commits
        val logRes = runGit("log -n 25 --pretty=format:'%h|%an <%ae>|%ar|%s'", dir)
        val commits = mutableListOf<GitCommit>()
        if (logRes.isSuccess && logRes.stdout.isNotBlank()) {
            logRes.stdout.lines().forEach { line ->
                val parts = line.split('|')
                if (parts.size >= 4) {
                    commits.add(
                        GitCommit(
                            hash = parts[0].trim('\''),
                            author = parts[1],
                            date = parts[2],
                            message = parts[3].trim('\'')
                        )
                    )
                }
            }
        }

        // 7. Stashes
        val stashRes = runGit("stash list", dir)
        val stashes = mutableListOf<GitStashItem>()
        if (stashRes.isSuccess && stashRes.stdout.isNotBlank()) {
            stashRes.stdout.lines().forEachIndexed { index, line ->
                if (line.isNotBlank()) {
                    stashes.add(GitStashItem(index, line))
                }
            }
        }

        val updatedState = GitState(
            isGitRepo = true,
            branch = currentBranch,
            remotes = remotes,
            branches = if (branches.isEmpty()) listOf(currentBranch) else branches,
            modifiedFiles = modified.distinct(),
            stagedFiles = staged.distinct(),
            untrackedFiles = untracked.distinct(),
            deletedFiles = deleted.distinct(),
            conflictFiles = conflictFiles,
            stashes = stashes,
            aheadCount = ahead,
            behindCount = behind,
            commits = commits,
            lastError = null
        )

        _gitState.value = updatedState
        return@withContext updatedState
    }

    private fun parseConflictFile(filePath: String, content: String): GitConflictFile {
        var currentSnippet = ""
        var incomingSnippet = ""

        val currentRegex = Regex("""<<<<<<< HEAD\r?\n([\s\S]*?)=======""", RegexOption.MULTILINE)
        val incomingRegex = Regex("""=======\r?\n([\s\S]*?)>>>>>>> [^\r\n]+""", RegexOption.MULTILINE)

        currentRegex.find(content)?.let { match ->
            currentSnippet = match.groupValues[1]
        }
        incomingRegex.find(content)?.let { match ->
            incomingSnippet = match.groupValues[1]
        }

        return GitConflictFile(
            filePath = filePath,
            currentSnippet = currentSnippet,
            incomingSnippet = incomingSnippet,
            fullConflictText = content
        )
    }

    // Git Operations

    suspend fun initRepo(projectName: String): GitCommandResult {
        val dir = projectRepo.getProjectDirectory(projectName)
        val res = runGit("init -b main", dir)
        refreshState(projectName)
        return res
    }

    suspend fun cloneRepo(url: String, targetProjectName: String): GitCommandResult {
        val baseDir = projectRepo.getProjectsRoot()
        val res = runGit("clone \"$url\" \"$targetProjectName\"", baseDir)
        if (res.isSuccess) {
            projectRepo.getProjects()
        }
        refreshState(targetProjectName)
        return res
    }

    suspend fun stageFile(file: String, projectName: String): GitCommandResult {
        val dir = projectRepo.getProjectDirectory(projectName)
        val res = runGit("add \"$file\"", dir)
        refreshState(projectName)
        return res
    }

    suspend fun unstageFile(file: String, projectName: String): GitCommandResult {
        val dir = projectRepo.getProjectDirectory(projectName)
        val res = runGit("restore --staged \"$file\"", dir)
        refreshState(projectName)
        return res
    }

    suspend fun stageAll(projectName: String): GitCommandResult {
        val dir = projectRepo.getProjectDirectory(projectName)
        val res = runGit("add -A", dir)
        refreshState(projectName)
        return res
    }

    suspend fun unstageAll(projectName: String): GitCommandResult {
        val dir = projectRepo.getProjectDirectory(projectName)
        val res = runGit("restore --staged .", dir)
        refreshState(projectName)
        return res
    }

    suspend fun commit(message: String, projectName: String): GitCommandResult {
        val dir = projectRepo.getProjectDirectory(projectName)
        // Ensure user config exists if not set
        runGit("config user.name \"AeroCode Developer\"", dir)
        runGit("config user.email \"dev@aerocode.local\"", dir)

        val escapedMsg = message.replace("\"", "\\\"")
        val res = runGit("commit -m \"$escapedMsg\"", dir)
        refreshState(projectName)
        return res
    }

    suspend fun push(remote: String = "origin", branch: String? = null, force: Boolean = false, projectName: String): GitCommandResult {
        val dir = projectRepo.getProjectDirectory(projectName)
        val targetBranch = branch ?: _gitState.value.branch
        val forceFlag = if (force) " --force" else ""
        val res = runGit("push$forceFlag $remote $targetBranch", dir)
        refreshState(projectName)
        return res
    }

    suspend fun pull(remote: String = "origin", branch: String? = null, projectName: String): GitCommandResult {
        val dir = projectRepo.getProjectDirectory(projectName)
        val targetBranch = branch ?: _gitState.value.branch
        val res = runGit("pull $remote $targetBranch", dir)
        refreshState(projectName)
        return res
    }

    suspend fun fetch(remote: String = "origin", projectName: String): GitCommandResult {
        val dir = projectRepo.getProjectDirectory(projectName)
        val res = runGit("fetch $remote", dir)
        refreshState(projectName)
        return res
    }

    suspend fun createBranch(branchName: String, checkout: Boolean = true, projectName: String): GitCommandResult {
        val dir = projectRepo.getProjectDirectory(projectName)
        val res = if (checkout) {
            runGit("checkout -b \"$branchName\"", dir)
        } else {
            runGit("branch \"$branchName\"", dir)
        }
        refreshState(projectName)
        return res
    }

    suspend fun switchBranch(branchName: String, projectName: String): GitCommandResult {
        val dir = projectRepo.getProjectDirectory(projectName)
        val res = runGit("checkout \"$branchName\"", dir)
        refreshState(projectName)
        return res
    }

    suspend fun renameBranch(oldName: String, newName: String, projectName: String): GitCommandResult {
        val dir = projectRepo.getProjectDirectory(projectName)
        val res = runGit("branch -m \"$oldName\" \"$newName\"", dir)
        refreshState(projectName)
        return res
    }

    suspend fun deleteBranch(branchName: String, force: Boolean = false, projectName: String): GitCommandResult {
        val dir = projectRepo.getProjectDirectory(projectName)
        val flag = if (force) "-D" else "-d"
        val res = runGit("branch $flag \"$branchName\"", dir)
        refreshState(projectName)
        return res
    }

    suspend fun merge(branchToMerge: String, projectName: String): GitCommandResult {
        val dir = projectRepo.getProjectDirectory(projectName)
        val res = runGit("merge \"$branchToMerge\"", dir)
        refreshState(projectName)
        return res
    }

    suspend fun stash(message: String = "", projectName: String): GitCommandResult {
        val dir = projectRepo.getProjectDirectory(projectName)
        val msgArg = if (message.isNotBlank()) " push -m \"$message\"" else " push"
        val res = runGit("stash$msgArg", dir)
        refreshState(projectName)
        return res
    }

    suspend fun applyStash(index: Int = 0, pop: Boolean = false, projectName: String): GitCommandResult {
        val dir = projectRepo.getProjectDirectory(projectName)
        val cmd = if (pop) "stash pop stash@{$index}" else "stash apply stash@{$index}"
        val res = runGit(cmd, dir)
        refreshState(projectName)
        return res
    }

    suspend fun addRemote(name: String, url: String, projectName: String): GitCommandResult {
        val dir = projectRepo.getProjectDirectory(projectName)
        val res = runGit("remote add \"$name\" \"$url\"", dir)
        refreshState(projectName)
        return res
    }

    suspend fun removeRemote(name: String, projectName: String): GitCommandResult {
        val dir = projectRepo.getProjectDirectory(projectName)
        val res = runGit("remote remove \"$name\"", dir)
        refreshState(projectName)
        return res
    }

    suspend fun revertCommit(commitHash: String, projectName: String): GitCommandResult {
        val dir = projectRepo.getProjectDirectory(projectName)
        val res = runGit("revert --no-edit \"$commitHash\"", dir)
        refreshState(projectName)
        return res
    }

    suspend fun resetHard(target: String = "HEAD", projectName: String): GitCommandResult {
        val dir = projectRepo.getProjectDirectory(projectName)
        val res = runGit("reset --hard $target", dir)
        refreshState(projectName)
        return res
    }

    suspend fun gitClean(force: Boolean = true, projectName: String): GitCommandResult {
        val dir = projectRepo.getProjectDirectory(projectName)
        val flag = if (force) "-fd" else "-nd"
        val res = runGit("clean $flag", dir)
        refreshState(projectName)
        return res
    }

    suspend fun getFileDiff(file: String, staged: Boolean = false, projectName: String): String = withContext(Dispatchers.IO) {
        val dir = projectRepo.getProjectDirectory(projectName)
        val stagedFlag = if (staged) "--staged" else ""
        val res = runGit("diff $stagedFlag -- \"$file\"", dir)
        if (res.isSuccess && res.stdout.isNotBlank()) res.stdout else "No diff found for $file"
    }

    suspend fun getCommitDetails(commitHash: String, projectName: String): String = withContext(Dispatchers.IO) {
        val dir = projectRepo.getProjectDirectory(projectName)
        val res = runGit("show --stat -p $commitHash", dir)
        if (res.isSuccess) res.stdout else res.stderr
    }

    // Merge Conflict Resolvers
    suspend fun resolveConflict(
        filePath: String,
        resolution: ConflictResolution,
        projectName: String
    ): Boolean = withContext(Dispatchers.IO) {
        val dir = projectRepo.getProjectDirectory(projectName)
        val targetFile = File(dir, filePath)
        if (!targetFile.exists()) return@withContext false

        val content = targetFile.readText()
        val conflictRegex = Regex("""<<<<<<< HEAD\r?\n([\s\S]*?)=======\r?\n([\s\S]*?)>>>>>>> [^\r\n]+""")

        val resolved = when (resolution) {
            ConflictResolution.KEEP_CURRENT -> {
                conflictRegex.replace(content) { match -> match.groupValues[1] }
            }
            ConflictResolution.KEEP_INCOMING -> {
                conflictRegex.replace(content) { match -> match.groupValues[2] }
            }
            ConflictResolution.KEEP_BOTH -> {
                conflictRegex.replace(content) { match ->
                    match.groupValues[1] + "\n" + match.groupValues[2]
                }
            }
        }

        targetFile.writeText(resolved)
        stageFile(filePath, projectName)
        refreshState(projectName)
        true
    }

    enum class ConflictResolution {
        KEEP_CURRENT, KEEP_INCOMING, KEEP_BOTH
    }
}
