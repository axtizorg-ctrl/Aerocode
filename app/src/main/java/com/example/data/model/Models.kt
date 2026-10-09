package com.example.data.model

import androidx.compose.ui.graphics.Color

/**
 * File tree item for Project Explorer.
 */
data class ProjectItem(
    val name: String,
    val path: String,
    val isDirectory: Boolean,
    val children: List<ProjectItem> = emptyList(),
    val isExpanded: Boolean = false,
    val sizeBytes: Long = 0,
    val extension: String = ""
)

/**
 * Active tab in the Code Editor.
 */
data class EditorTab(
    val id: String,
    val title: String,
    val filePath: String,
    val content: String,
    val isDirty: Boolean = false,
    val language: Language = Language.detect(filePath),
    val cursorLine: Int = 1,
    val cursorColumn: Int = 1,
    val isPinned: Boolean = false
)

/**
 * Supported programming languages with auto-detection and execution templates.
 */
enum class Language(
    val displayName: String,
    val extensions: List<String>,
    val defaultRunCommand: String,
    val defaultBuildCommand: String = "",
    val sampleBoilerplate: String = ""
) {
    CPP(
        displayName = "C++",
        extensions = listOf("cpp", "cc", "cxx", "hpp", "h"),
        defaultRunCommand = "clang++ -O2 {file} -o {basename} && ./{basename}",
        defaultBuildCommand = "clang++ -O2 {file} -o {basename}",
        sampleBoilerplate = """#include <iostream>

int main() {
    std::cout << "Hello from AeroCode IDE!" << std::endl;
    return 0;
}
"""
    ),
    C(
        displayName = "C",
        extensions = listOf("c"),
        defaultRunCommand = "clang -O2 {file} -o {basename} && ./{basename}",
        defaultBuildCommand = "clang -O2 {file} -o {basename}",
        sampleBoilerplate = """#include <stdio.h>

int main() {
    printf("Hello from AeroCode C!\n");
    return 0;
}
"""
    ),
    PYTHON(
        displayName = "Python",
        extensions = listOf("py"),
        defaultRunCommand = "python3 {file}",
        sampleBoilerplate = """def main():
    print("Hello from AeroCode Python!")
    numbers = [x * 2 for x in range(1, 6)]
    print(f"Computed squares: {numbers}")

if __name__ == "__main__":
    main()
"""
    ),
    JAVASCRIPT(
        displayName = "JavaScript",
        extensions = listOf("js", "mjs", "jsx"),
        defaultRunCommand = "PREVIEW_WEB",
        sampleBoilerplate = """// Modern Frontend JavaScript
document.addEventListener("DOMContentLoaded", () => {
    console.log("🚀 AeroCode Frontend initialized!");

    const actionBtn = document.getElementById("action-btn");
    const outputEl = document.getElementById("output");
    let count = 0;

    if (actionBtn && outputEl) {
        actionBtn.addEventListener("click", () => {
            count++;
            outputEl.textContent = "Button clicked " + count + " times!";
            console.log("Action dispatched: count=" + count);
        });
    }
});
"""
    ),
    TYPESCRIPT(
        displayName = "TypeScript",
        extensions = listOf("ts"),
        defaultRunCommand = "ts-node {file} || npx ts-node {file}",
        sampleBoilerplate = """interface User {
    id: number;
    name: string;
}

const user: User = { id: 1, name: "AeroCode" };
console.log("User: " + user.name);
"""
    ),
    JAVA(
        displayName = "Java",
        extensions = listOf("java"),
        defaultRunCommand = "javac {file} && java {basename}",
        defaultBuildCommand = "javac {file}",
        sampleBoilerplate = """public class Main {
    public static void main(String[] args) {
        System.out.println("Hello from AeroCode Java!");
    }
}
"""
    ),
    KOTLIN(
        displayName = "Kotlin",
        extensions = listOf("kt", "kts"),
        defaultRunCommand = "kotlinc {file} -include-runtime -d {basename}.jar && java -jar {basename}.jar",
        sampleBoilerplate = """fun main() {
    println("Hello from AeroCode Kotlin!")
}
"""
    ),
    RUST(
        displayName = "Rust",
        extensions = listOf("rs"),
        defaultRunCommand = "rustc {file} -o {basename} && ./{basename}",
        defaultBuildCommand = "rustc {file} -o {basename}",
        sampleBoilerplate = """fn main() {
    println!("Hello from AeroCode Rust!");
}
"""
    ),
    GO(
        displayName = "Go",
        extensions = listOf("go"),
        defaultRunCommand = "go run {file}",
        defaultBuildCommand = "go build {file}",
        sampleBoilerplate = """package main

import "fmt"

func main() {
    fmt.Println("Hello from AeroCode Go!")
}
"""
    ),
    HTML(
        displayName = "HTML",
        extensions = listOf("html", "htm"),
        defaultRunCommand = "PREVIEW_WEB",
        sampleBoilerplate = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <title>AeroCode Web App</title>
    <link rel="stylesheet" href="style.css">
</head>
<body>
    <div class="container">
        <header>
            <h1>⚡ Modern Frontend App</h1>
            <p>Built with AeroCode live browser preview.</p>
        </header>
        <main>
            <div id="output" class="status-card">Ready to interact.</div>
            <button id="action-btn">Click Me</button>
        </main>
    </div>
    <script src="app.js"></script>
</body>
</html>
"""
    ),
    CSS(
        displayName = "CSS",
        extensions = listOf("css"),
        defaultRunCommand = "PREVIEW_WEB",
        sampleBoilerplate = """:root {
    --bg-primary: #0a0a0c;
    --bg-card: #13141a;
    --accent: #00e5ff;
    --text-primary: #f0f2f5;
    --text-secondary: #8a90a2;
}

* {
    box-sizing: border-box;
    margin: 0;
    padding: 0;
}

body {
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    background-color: var(--bg-primary);
    color: var(--text-primary);
    padding: 24px;
    display: flex;
    justify-content: center;
}

.container {
    max-width: 500px;
    width: 100%;
    margin-top: 40px;
    text-align: center;
}

h1 {
    font-size: 24px;
    margin-bottom: 8px;
    color: var(--accent);
}

p {
    color: var(--text-secondary);
    font-size: 14px;
    margin-bottom: 24px;
}

.status-card {
    background: var(--bg-card);
    border: 1px solid #22242e;
    border-radius: 10px;
    padding: 16px;
    margin-bottom: 16px;
    font-family: monospace;
    color: #50fa7b;
}

button {
    background: var(--accent);
    color: #000;
    border: none;
    padding: 12px 24px;
    font-size: 15px;
    font-weight: 700;
    border-radius: 8px;
    cursor: pointer;
    transition: opacity 0.2s;
}

button:active {
    opacity: 0.8;
}
"""
    ),
    SHELL(
        displayName = "Shell",
        extensions = listOf("sh", "bash"),
        defaultRunCommand = "bash {file}",
        sampleBoilerplate = """#!/usr/bin/env bash
echo "Executing AeroCode Shell Script..."
uname -a
date
"""
    ),
    PHP(
        displayName = "PHP",
        extensions = listOf("php"),
        defaultRunCommand = "php {file}",
        sampleBoilerplate = """<?php
echo "Hello from AeroCode PHP!\n";
?>
"""
    ),
    SQL(
        displayName = "SQL",
        extensions = listOf("sql"),
        defaultRunCommand = "sqlite3 :memory: < {file}",
        sampleBoilerplate = """CREATE TABLE users (id INTEGER PRIMARY KEY, name TEXT);
INSERT INTO users (name) VALUES ('Ada Lovelace'), ('Alan Turing');
SELECT * FROM users;
"""
    ),
    MARKDOWN(
        displayName = "Markdown",
        extensions = listOf("md", "markdown"),
        defaultRunCommand = "PREVIEW_WEB",
        sampleBoilerplate = """# AeroCode Project
A mobile-first IDE with:
- **Terminal** with Termux bridge
- **Local AI** coding assistant (Qwen 0.5B)
- **Git** graphical and command-line support
"""
    ),
    JSON(
        displayName = "JSON",
        extensions = listOf("json"),
        defaultRunCommand = "cat {file}",
        sampleBoilerplate = """{
  "name": "aerocode-project",
  "version": "1.0.0",
  "scripts": {
    "start": "node index.js"
  }
}
"""
    ),
    UNKNOWN(
        displayName = "Plain Text",
        extensions = emptyList(),
        defaultRunCommand = "cat {file}",
        sampleBoilerplate = ""
    );

    companion object {
        fun detect(filePath: String): Language {
            val ext = filePath.substringAfterLast('.', "").lowercase()
            return entries.firstOrNull { it.extensions.contains(ext) } ?: UNKNOWN
        }
    }
}

/**
 * Terminal session line types.
 */
enum class TerminalLineType {
    COMMAND, STDOUT, STDERR, SYSTEM, SUCCESS
}

data class TerminalLine(
    val text: String,
    val type: TerminalLineType = TerminalLineType.STDOUT
)

data class TerminalSession(
    val id: String,
    val title: String,
    val lines: List<TerminalLine> = emptyList(),
    val isRunning: Boolean = false,
    val workingDirectory: String = "/",
    val exitCode: Int? = null
)

/**
 * Compiler error or warning.
 */
enum class ProblemSeverity {
    ERROR, WARNING, INFO
}

data class Problem(
    val id: String,
    val filePath: String,
    val line: Int,
    val column: Int = 1,
    val message: String,
    val severity: ProblemSeverity = ProblemSeverity.ERROR,
    val source: String = "compiler"
)

/**
 * AI Message and proposed code patch.
 */
enum class AiSender {
    USER, QWEN, SYSTEM
}

data class CodeDiff(
    val filePath: String,
    val originalSnippet: String,
    val replacementSnippet: String,
    val startLine: Int = 1,
    val endLine: Int = 1
)

data class AiToolCall(
    val toolName: String,
    val inputArgs: String,
    val outputResult: String
)

data class AiMessage(
    val id: String,
    val sender: AiSender,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    val proposedDiff: CodeDiff? = null,
    val toolCalls: List<AiToolCall> = emptyList()
)

/**
 * Git State.
 */
data class GitCommit(
    val hash: String,
    val author: String,
    val date: String,
    val message: String
)

data class GitRemote(
    val name: String,
    val url: String
)

data class GitStashItem(
    val index: Int,
    val message: String
)

data class GitConflictFile(
    val filePath: String,
    val currentSnippet: String = "",
    val incomingSnippet: String = "",
    val fullConflictText: String = ""
)

data class GitState(
    val isGitRepo: Boolean = true,
    val branch: String = "main",
    val remotes: List<GitRemote> = emptyList(),
    val branches: List<String> = listOf("main"),
    val modifiedFiles: List<String> = emptyList(),
    val stagedFiles: List<String> = emptyList(),
    val untrackedFiles: List<String> = emptyList(),
    val deletedFiles: List<String> = emptyList(),
    val conflictFiles: List<GitConflictFile> = emptyList(),
    val stashes: List<GitStashItem> = emptyList(),
    val aheadCount: Int = 0,
    val behindCount: Int = 0,
    val commits: List<GitCommit> = emptyList(),
    val lastError: String? = null
)

/**
 * Termux Tool status.
 */
data class TermuxTool(
    val name: String,
    val binaryName: String,
    val isInstalled: Boolean,
    val version: String? = null,
    val installCommand: String,
    val category: String
)

/**
 * Command palette item.
 */
data class CommandItem(
    val id: String,
    val title: String,
    val shortcut: String? = null,
    val category: String = "General",
    val action: () -> Unit
)

/**
 * Plugin model.
 */
data class PluginItem(
    val id: String,
    val name: String,
    val version: String,
    val description: String,
    val author: String,
    val isEnabled: Boolean,
    val permissions: List<String> = emptyList()
)

/**
 * IDE Themes.
 */
enum class IdeTheme(
    val displayName: String,
    val isDark: Boolean,
    val bg: Color,
    val surface: Color,
    val surfaceHighlight: Color,
    val editorBg: Color,
    val gutterBg: Color,
    val lineNumColor: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val accent: Color,
    val keywordColor: Color,
    val stringColor: Color,
    val commentColor: Color,
    val numberColor: Color,
    val functionColor: Color,
    val typeColor: Color,
    val terminalBg: Color,
    val terminalFg: Color,
    val errorColor: Color,
    val warningColor: Color
) {
    CURSOR_DARK(
        displayName = "Cursor Black (Default)",
        isDark = true,
        bg = Color(0xFF000000),
        surface = Color(0xFF0D0E12),
        surfaceHighlight = Color(0xFF161820),
        editorBg = Color(0xFF000000),
        gutterBg = Color(0xFF000000),
        lineNumColor = Color(0xFF4A4E5E),
        textPrimary = Color(0xFFECEFF4),
        textSecondary = Color(0xFF8A90A2),
        accent = Color(0xFF00E5FF),
        keywordColor = Color(0xFF569CD6),
        stringColor = Color(0xFF98C379),
        commentColor = Color(0xFF676F82),
        numberColor = Color(0xFFD19A66),
        functionColor = Color(0xFF61AFEF),
        typeColor = Color(0xFF4EC9B0),
        terminalBg = Color(0xFF000000),
        terminalFg = Color(0xFFECEFF4),
        errorColor = Color(0xFFE06C75),
        warningColor = Color(0xFFE5C07B)
    ),
    VS_CODE_DARK(
        displayName = "VS Code Dark",
        isDark = true,
        bg = Color(0xFF1E1E1E),
        surface = Color(0xFF252526),
        surfaceHighlight = Color(0xFF2D2D30),
        editorBg = Color(0xFF1E1E1E),
        gutterBg = Color(0xFF1E1E1E),
        lineNumColor = Color(0xFF858585),
        textPrimary = Color(0xFFD4D4D4),
        textSecondary = Color(0xFF969696),
        accent = Color(0xFF007ACC),
        keywordColor = Color(0xFF569CD6),
        stringColor = Color(0xFFCE9178),
        commentColor = Color(0xFF6A9955),
        numberColor = Color(0xFFB5CEA8),
        functionColor = Color(0xFFDCDCAA),
        typeColor = Color(0xFF4EC9B0),
        terminalBg = Color(0xFF181818),
        terminalFg = Color(0xFFCCCCCC),
        errorColor = Color(0xFFF14C4C),
        warningColor = Color(0xFFCCA700)
    ),
    DRACULA(
        displayName = "Dracula",
        isDark = true,
        bg = Color(0xFF282A36),
        surface = Color(0xFF343746),
        surfaceHighlight = Color(0xFF44475A),
        editorBg = Color(0xFF282A36),
        gutterBg = Color(0xFF21222C),
        lineNumColor = Color(0xFF6272A4),
        textPrimary = Color(0xFFF8F8F2),
        textSecondary = Color(0xFFBD93F9),
        accent = Color(0xFFFF79C6),
        keywordColor = Color(0xFFFF79C6),
        stringColor = Color(0xFFF1FA8C),
        commentColor = Color(0xFF6272A4),
        numberColor = Color(0xFFBD93F9),
        functionColor = Color(0xFF50FA7B),
        typeColor = Color(0xFF8BE9FD),
        terminalBg = Color(0xFF21222C),
        terminalFg = Color(0xFFF8F8F2),
        errorColor = Color(0xFFFF5555),
        warningColor = Color(0xFFFFB86C)
    ),
    ONE_DARK(
        displayName = "One Dark",
        isDark = true,
        bg = Color(0xFF282C34),
        surface = Color(0xFF21252B),
        surfaceHighlight = Color(0xFF2C313A),
        editorBg = Color(0xFF282C34),
        gutterBg = Color(0xFF21252B),
        lineNumColor = Color(0xFF5C6370),
        textPrimary = Color(0xFFABB2BF),
        textSecondary = Color(0xFF828997),
        accent = Color(0xFF61AFEF),
        keywordColor = Color(0xFFC678DD),
        stringColor = Color(0xFF98C379),
        commentColor = Color(0xFF5C6370),
        numberColor = Color(0xFFD19A66),
        functionColor = Color(0xFF61AFEF),
        typeColor = Color(0xFFE5C07B),
        terminalBg = Color(0xFF1E2127),
        terminalFg = Color(0xFFABB2BF),
        errorColor = Color(0xFFE06C75),
        warningColor = Color(0xFFE5C07B)
    ),
    MONOKAI(
        displayName = "Monokai",
        isDark = true,
        bg = Color(0xFF272822),
        surface = Color(0xFF1E1F1C),
        surfaceHighlight = Color(0xFF3E3D32),
        editorBg = Color(0xFF272822),
        gutterBg = Color(0xFF1E1F1C),
        lineNumColor = Color(0xFF75715E),
        textPrimary = Color(0xFFF8F8F2),
        textSecondary = Color(0xFFA6E22E),
        accent = Color(0xFFF92672),
        keywordColor = Color(0xFFF92672),
        stringColor = Color(0xFFE6DB74),
        commentColor = Color(0xFF75715E),
        numberColor = Color(0xFFAE81FF),
        functionColor = Color(0xFFA6E22E),
        typeColor = Color(0xFF66D9EF),
        terminalBg = Color(0xFF191A16),
        terminalFg = Color(0xFFF8F8F2),
        errorColor = Color(0xFFF92672),
        warningColor = Color(0xFFFD971F)
    ),
    NORD(
        displayName = "Nord",
        isDark = true,
        bg = Color(0xFF2E3440),
        surface = Color(0xFF3B4252),
        surfaceHighlight = Color(0xFF434C5E),
        editorBg = Color(0xFF2E3440),
        gutterBg = Color(0xFF2E3440),
        lineNumColor = Color(0xFF4C566A),
        textPrimary = Color(0xFFD8DEE9),
        textSecondary = Color(0xFFE5E9F0),
        accent = Color(0xFF88C0D0),
        keywordColor = Color(0xFF81A1C1),
        stringColor = Color(0xFFA3BE8C),
        commentColor = Color(0xFF616E88),
        numberColor = Color(0xFFB48EAD),
        functionColor = Color(0xFF88C0D0),
        typeColor = Color(0xFF8FBCBB),
        terminalBg = Color(0xFF242933),
        terminalFg = Color(0xFFD8DEE9),
        errorColor = Color(0xFFBF616A),
        warningColor = Color(0xFFEBCB8B)
    ),
    CATPPUCCIN_MOCHA(
        displayName = "Catppuccin Mocha",
        isDark = true,
        bg = Color(0xFF1E1E2E),
        surface = Color(0xFF181825),
        surfaceHighlight = Color(0xFF313244),
        editorBg = Color(0xFF1E1E2E),
        gutterBg = Color(0xFF181825),
        lineNumColor = Color(0xFF6C7086),
        textPrimary = Color(0xFFCDD6F4),
        textSecondary = Color(0xFFA6ADC8),
        accent = Color(0xFFCBA6F7),
        keywordColor = Color(0xFFCBA6F7),
        stringColor = Color(0xFFA6E3A1),
        commentColor = Color(0xFF6C7086),
        numberColor = Color(0xFFFAB387),
        functionColor = Color(0xFF89B4FA),
        typeColor = Color(0xFFF9E2AF),
        terminalBg = Color(0xFF11111B),
        terminalFg = Color(0xFFCDD6F4),
        errorColor = Color(0xFFF38BA8),
        warningColor = Color(0xFFF9E2AF)
    ),
    ANDROMEDA(
        displayName = "Andromeda",
        isDark = true,
        bg = Color(0xFF23262E),
        surface = Color(0xFF1E2027),
        surfaceHighlight = Color(0xFF2B2E38),
        editorBg = Color(0xFF23262E),
        gutterBg = Color(0xFF1E2027),
        lineNumColor = Color(0xFF5A6070),
        textPrimary = Color(0xFFD5CED9),
        textSecondary = Color(0xFF7E8497),
        accent = Color(0xFF00E8C6),
        keywordColor = Color(0xFFC74DED),
        stringColor = Color(0xFF96E072),
        commentColor = Color(0xFF5A6070),
        numberColor = Color(0xFFF39C12),
        functionColor = Color(0xFFFFEE80),
        typeColor = Color(0xFF00E8C6),
        terminalBg = Color(0xFF1B1D23),
        terminalFg = Color(0xFFD5CED9),
        errorColor = Color(0xFFFF5252),
        warningColor = Color(0xFFFFD54F)
    ),
    VS_CODE_LIGHT(
        displayName = "VS Code Light",
        isDark = false,
        bg = Color(0xFFFFFFFF),
        surface = Color(0xFFF3F3F3),
        surfaceHighlight = Color(0xFFE5E5E5),
        editorBg = Color(0xFFFFFFFF),
        gutterBg = Color(0xFFF8F8F8),
        lineNumColor = Color(0xFF237893),
        textPrimary = Color(0xFF000000),
        textSecondary = Color(0xFF717171),
        accent = Color(0xFF005FB8),
        keywordColor = Color(0xFF0000FF),
        stringColor = Color(0xFFA31515),
        commentColor = Color(0xFF008000),
        numberColor = Color(0xFF098658),
        functionColor = Color(0xFF795E26),
        typeColor = Color(0xFF267F99),
        terminalBg = Color(0xFFF0F0F0),
        terminalFg = Color(0xFF111111),
        errorColor = Color(0xFFE51400),
        warningColor = Color(0xFFBF8803)
    ),
    SOLARIZED_LIGHT(
        displayName = "Solarized Light",
        isDark = false,
        bg = Color(0xFFFDF6E3),
        surface = Color(0xFFEEE8D5),
        surfaceHighlight = Color(0xFFE0D8C3),
        editorBg = Color(0xFFFDF6E3),
        gutterBg = Color(0xFFEEE8D5),
        lineNumColor = Color(0xFF93A1A1),
        textPrimary = Color(0xFF657B83),
        textSecondary = Color(0xFF586E75),
        accent = Color(0xFF268BD2),
        keywordColor = Color(0xFF859900),
        stringColor = Color(0xFF2AA198),
        commentColor = Color(0xFF93A1A1),
        numberColor = Color(0xFFD33682),
        functionColor = Color(0xFF268BD2),
        typeColor = Color(0xFFB58900),
        terminalBg = Color(0xFFEEE8D5),
        terminalFg = Color(0xFF586E75),
        errorColor = Color(0xFFDC322F),
        warningColor = Color(0xFFCB4B16)
    ),
    HIGH_CONTRAST(
        displayName = "High Contrast Dark",
        isDark = true,
        bg = Color(0xFF000000),
        surface = Color(0xFF111111),
        surfaceHighlight = Color(0xFF222222),
        editorBg = Color(0xFF000000),
        gutterBg = Color(0xFF050505),
        lineNumColor = Color(0xFFFFFFFF),
        textPrimary = Color(0xFFFFFFFF),
        textSecondary = Color(0xFFCCCCCC),
        accent = Color(0xFF00FFFF),
        keywordColor = Color(0xFF569CD6),
        stringColor = Color(0xFFFFFF00),
        commentColor = Color(0xFF7CA668),
        numberColor = Color(0xFF00FF00),
        functionColor = Color(0xFFFF8000),
        typeColor = Color(0xFF00FFFF),
        terminalBg = Color(0xFF000000),
        terminalFg = Color(0xFF00FF00),
        errorColor = Color(0xFFFF0000),
        warningColor = Color(0xFFFFFF00)
    )
}
