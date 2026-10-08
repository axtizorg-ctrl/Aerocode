package com.example

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.example.ui.MainIdeScreen
import com.example.ui.theme.EditorFontOption
import com.example.ui.theme.FiraCodeFontFamily
import com.example.ui.theme.JetBrainsMonoFontFamily
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.RobotoMonoFontFamily
import com.example.ui.theme.SourceCodeProFontFamily
import androidx.compose.ui.text.font.FontFamily
import com.example.viewmodel.ActiveModal
import com.example.viewmodel.IdeViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: IdeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val theme by viewModel.theme.collectAsState()
            val fontName by viewModel.fontFamilyName.collectAsState()

            val globalFontFamily = when (fontName) {
                EditorFontOption.JETBRAINS_MONO.displayName -> JetBrainsMonoFontFamily
                EditorFontOption.FIRA_CODE.displayName -> FiraCodeFontFamily
                EditorFontOption.ROBOTO_MONO.displayName -> RobotoMonoFontFamily
                EditorFontOption.SOURCE_CODE_PRO.displayName -> SourceCodeProFontFamily
                else -> FontFamily.Monospace
            }

            MyApplicationTheme(
                ideTheme = theme,
                globalFontFamily = globalFontFamily
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MainIdeScreen(viewModel = viewModel)
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (event != null && event.isCtrlPressed) {
            when (keyCode) {
                KeyEvent.KEYCODE_S -> {
                    viewModel.saveCurrentFile()
                    return true
                }
                KeyEvent.KEYCODE_R -> {
                    viewModel.runProjectOrCurrentFile()
                    return true
                }
                KeyEvent.KEYCODE_P -> {
                    if (event.isShiftPressed) {
                        viewModel.activeModal.value = ActiveModal.COMMAND_PALETTE
                        return true
                    }
                }
                KeyEvent.KEYCODE_F -> {
                    viewModel.activeModal.value = ActiveModal.FIND_REPLACE
                    return true
                }
                KeyEvent.KEYCODE_W -> {
                    val activeId = viewModel.activeTabId.value
                    if (activeId != null) {
                        viewModel.closeTab(activeId)
                        return true
                    }
                }
                KeyEvent.KEYCODE_Z -> {
                    viewModel.undo()
                    return true
                }
                KeyEvent.KEYCODE_Y -> {
                    viewModel.redo()
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }
}
