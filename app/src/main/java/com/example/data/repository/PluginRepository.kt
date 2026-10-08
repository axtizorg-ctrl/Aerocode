package com.example.data.repository

import com.example.data.model.PluginItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PluginRepository {

    private val _plugins = MutableStateFlow(
        listOf(
            PluginItem(
                id = "plugin.prettier",
                name = "Prettier & Clang-Format",
                version = "1.4.2",
                description = "Automatic code formatter supporting C++, Python, JavaScript, and HTML.",
                author = "AeroCode Core",
                isEnabled = true,
                permissions = listOf("Editor", "Filesystem")
            ),
            PluginItem(
                id = "plugin.colorpicker",
                name = "Color Picker & Hex Tool",
                version = "2.0.1",
                description = "Inline color swatch previews and quick hex/rgb palette picker in code.",
                author = "Community",
                isEnabled = true,
                permissions = listOf("Editor", "UI")
            ),
            PluginItem(
                id = "plugin.todohighlight",
                name = "TODO / FIXME Highlighter",
                version = "1.1.0",
                description = "Accentuates TODO, FIXME, BUG, and NOTE comments with bright badges.",
                author = "Community",
                isEnabled = true,
                permissions = listOf("Editor")
            ),
            PluginItem(
                id = "plugin.termuxbridge",
                name = "Termux Extra Keys & PTY",
                version = "1.0.5",
                description = "Expands the terminal accessory row with nano/vim control keys.",
                author = "Termux Community",
                isEnabled = true,
                permissions = listOf("Terminal", "Commands")
            ),
            PluginItem(
                id = "plugin.gitblame",
                name = "Git Blame Inline Annotator",
                version = "0.9.4",
                description = "Displays the latest git commit author and time alongside the active line.",
                author = "DevTools",
                isEnabled = false,
                permissions = listOf("Git", "Editor")
            )
        )
    )
    val plugins: StateFlow<List<PluginItem>> = _plugins.asStateFlow()

    fun togglePlugin(id: String) {
        _plugins.value = _plugins.value.map {
            if (it.id == id) it.copy(isEnabled = !it.isEnabled) else it
        }
    }

    fun addPlugin(plugin: PluginItem) {
        _plugins.value = (_plugins.value.filter { it.id != plugin.id } + plugin)
    }

    val apiDocumentation = """
### AeroCode Plugin API Reference (v1.0)

AeroCode provides a secure, sandboxed JavaScript/Kotlin plugin API inspired by Acode and VS Code.

#### 1. Command Registration
```js
aerocode.registerCommand({
  id: "myplugin.runCustomTask",
  title: "Run Custom Build Script",
  shortcut: "Ctrl+Shift+B",
  execute: () => {
    aerocode.terminal.run("make clean && make -j4");
  }
});
```

#### 2. Editor Actions & Toolbars
```js
aerocode.registerEditorAction({
  id: "myplugin.format",
  icon: "ic_format_align_left",
  tooltip: "Format Document",
  action: (editor) => {
    const text = editor.getText();
    const formatted = formatCode(text);
    editor.setText(formatted);
  }
});
```

#### 3. AI Bridge & Prompts
```js
aerocode.ai.sendToQwen({
  prompt: "Refactor this function for low memory usage",
  includeCurrentSelection: true,
  onPatchReady: (diff) => {
    aerocode.ui.showDiff(diff);
  }
});
```

#### 4. Controlled Capabilities
- `Editor`: Read, write, manipulate cursor, selection, and tokens.
- `Filesystem`: Sandboxed reads/writes within the active project directory.
- `Terminal`: Spawn sessions, send input, listen to stdout/stderr.
- `Git`: Query repository status, staging, and branch switching.
""".trimIndent()
}
