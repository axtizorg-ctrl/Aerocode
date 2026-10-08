package com.example.ui.web

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import com.example.viewmodel.IdeViewModel
import java.io.File

data class WebConsoleLog(
    val message: String,
    val level: ConsoleMessage.MessageLevel,
    val lineNumber: Int
)

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun FullScreenWebScreen(
    viewModel: IdeViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val currentProject by viewModel.currentProjectName.collectAsState()
    val activeTab = viewModel.activeTab
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var isConsoleOpen by remember { mutableStateOf(false) }
    val consoleLogs = remember { mutableStateListOf<WebConsoleLog>() }

    // Intercept back button to return to editor
    BackHandler {
        onBack()
    }

    // Prepare complete HTML content (merging HTML, CSS, and JS in the active project)
    val projectDir = viewModel.projectRepo.getProjectDirectory(currentProject)
    val htmlContent = remember(currentProject, activeTab?.content) {
        buildCombinedHtml(projectDir, activeTab?.content, activeTab?.filePath)
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF141720))
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        // 1. Full Screen Browser Navigation Bar
        Surface(
            color = Color(0xFF1C202C),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(36.dp).testTag("web_preview_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Editor",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Address / Title Bar Pill
                    Surface(
                        color = Color(0xFF12141C),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(30.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFFA6E3A1),
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "localhost:8080/${activeTab?.title ?: "Index.html"}",
                                color = Color(0xFFCDD6F4),
                                fontSize = 11.sp,
                                maxLines = 1
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(6.dp))

                // Action Buttons: Console, Reload
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Console Toggle Button
                    IconButton(
                        onClick = { isConsoleOpen = !isConsoleOpen },
                        modifier = Modifier.size(36.dp).testTag("web_preview_console_button")
                    ) {
                        BadgeBox(badgeCount = consoleLogs.size) {
                            Icon(
                                imageVector = Icons.Default.Terminal,
                                contentDescription = "Console Logs",
                                tint = if (isConsoleOpen) Color(0xFFCBA6F7) else Color(0xFFCDD6F4),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    // Reload Button
                    IconButton(
                        onClick = {
                            webViewInstance?.loadDataWithBaseURL(
                                "file://${projectDir.absolutePath}/",
                                htmlContent,
                                "text/html",
                                "UTF-8",
                                null
                            )
                        },
                        modifier = Modifier.size(36.dp).testTag("web_preview_reload_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Reload",
                            tint = Color(0xFFCDD6F4),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        // 2. Full-Screen Viewport WebView
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color.White)
        ) {
            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            useWideViewPort = true
                            loadWithOverviewMode = true
                            setSupportZoom(true)
                            builtInZoomControls = true
                            displayZoomControls = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onConsoleMessage(consoleMessage: ConsoleMessage?): Boolean {
                                if (consoleMessage != null) {
                                    consoleLogs.add(
                                        WebConsoleLog(
                                            message = consoleMessage.message(),
                                            level = consoleMessage.messageLevel(),
                                            lineNumber = consoleMessage.lineNumber()
                                        )
                                    )
                                }
                                return super.onConsoleMessage(consoleMessage)
                            }
                        }

                        webViewClient = WebViewClient()

                        loadDataWithBaseURL(
                            "file://${projectDir.absolutePath}/",
                            htmlContent,
                            "text/html",
                            "UTF-8",
                            null
                        )

                        webViewInstance = this
                    }
                },
                update = { webView ->
                    webView.loadDataWithBaseURL(
                        "file://${projectDir.absolutePath}/",
                        htmlContent,
                        "text/html",
                        "UTF-8",
                        null
                    )
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // 3. Collapsible DevTools Console Drawer
        AnimatedVisibility(visible = isConsoleOpen) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(Color(0xFF141720))
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "CONSOLE OUTPUT (${consoleLogs.size})",
                        color = Color(0xFF89B4FA),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    TextButton(
                        onClick = { consoleLogs.clear() },
                        modifier = Modifier.height(26.dp)
                    ) {
                        Text("Clear", color = Color(0xFF7A8092), fontSize = 10.sp)
                    }
                }

                if (consoleLogs.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No console logs emitted yet.",
                            color = Color(0xFF5D6477),
                            fontSize = 11.sp
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(consoleLogs) { log ->
                            val textColor = when (log.level) {
                                ConsoleMessage.MessageLevel.ERROR -> Color(0xFFF38BA8)
                                ConsoleMessage.MessageLevel.WARNING -> Color(0xFFFAB387)
                                else -> Color(0xFFA6E3A1)
                            }
                            Text(
                                text = "> ${log.message} (line ${log.lineNumber})",
                                color = textColor,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(vertical = 1.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BadgeBox(badgeCount: Int, content: @Composable () -> Unit) {
    Box {
        content()
        if (badgeCount > 0) {
            Surface(
                color = Color(0xFFCBA6F7),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier
                    .size(14.dp)
                    .align(Alignment.TopEnd)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (badgeCount > 9) "9+" else "$badgeCount",
                        color = Color(0xFF11111B),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Combines Index.html, Style.css, and Input.js into a unified executing HTML document.
 */
private fun buildCombinedHtml(projectDir: File, activeContent: String?, activePath: String?): String {
    val indexFile = File(projectDir, "Index.html").takeIf { it.exists() }
        ?: File(projectDir, "index.html").takeIf { it.exists() }
    val styleFile = File(projectDir, "Style.css").takeIf { it.exists() }
        ?: File(projectDir, "style.css").takeIf { it.exists() }
    val jsFile = File(projectDir, "Input.js").takeIf { it.exists() }
        ?: File(projectDir, "app.js").takeIf { it.exists() }

    // 1. Determine base HTML
    var htmlText = when {
        activePath?.endsWith(".html", ignoreCase = true) == true && !activeContent.isNullOrBlank() -> activeContent
        activePath?.endsWith(".htm", ignoreCase = true) == true && !activeContent.isNullOrBlank() -> activeContent
        indexFile != null -> indexFile.readText()
        else -> projectDir.walkTopDown().firstOrNull { it.isFile && it.extension.lowercase() in listOf("html", "htm") }?.readText() ?: """
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <meta name="viewport" content="width=device-width, initial-scale=1.0">
  <title>AeroCode Web Preview</title>
  <style>
    body { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif; background: #12121e; color: #fff; padding: 24px; text-align: center; }
    .card { background: #1e1e2e; border: 1px solid #313244; padding: 24px; border-radius: 12px; max-width: 480px; margin: 30px auto; box-shadow: 0 8px 30px rgba(0,0,0,0.5); }
    h1 { color: #89b4fa; margin-bottom: 8px; font-size: 22px; }
    p { color: #a6adc8; font-size: 14px; line-height: 1.5; }
    #app-output { margin-top: 16px; padding: 12px; background: #11111b; border-radius: 8px; font-family: monospace; color: #a6e3a1; text-align: left; }
    button { background: #cba6f7; color: #11111b; border: none; padding: 10px 18px; font-weight: bold; border-radius: 6px; cursor: pointer; margin-top: 12px; }
  </style>
</head>
<body>
  <div class="card">
    <h1>🚀 AeroCode Full-Screen Web Window</h1>
    <p>Running your HTML, CSS, and JavaScript with live browser execution & console logging.</p>
    <div id="app-output">Status: Ready</div>
    <button onclick="document.getElementById('app-output').innerText = 'Executed successfully at: ' + new Date().toLocaleTimeString()">Test Action</button>
  </div>
</body>
</html>
""".trimIndent()
    }

    val cssText = when {
        activePath?.endsWith(".css", ignoreCase = true) == true && activeContent != null -> activeContent
        styleFile != null -> styleFile.readText()
        else -> ""
    }

    val jsText = when {
        activePath?.endsWith(".js", ignoreCase = true) == true && activeContent != null -> activeContent
        jsFile != null -> jsFile.readText()
        else -> ""
    }

    // Inject CSS
    if (cssText.isNotBlank() && !htmlText.contains("<style>")) {
        htmlText = if (htmlText.contains("</head>")) {
            htmlText.replace("</head>", "<style>\n$cssText\n</style>\n</head>")
        } else {
            "<style>\n$cssText\n</style>\n$htmlText"
        }
    }

    // Inject JS
    if (jsText.isNotBlank()) {
        val scriptTag = "<script>\n$jsText\n</script>"
        htmlText = if (htmlText.contains("</body>")) {
            htmlText.replace("</body>", "$scriptTag\n</body>")
        } else {
            "$htmlText\n$scriptTag"
        }
    }

    return htmlText
}
