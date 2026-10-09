package com.example.ui.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import com.example.data.model.IdeTheme

@Composable
fun MyApplicationTheme(
    ideTheme: IdeTheme = IdeTheme.CURSOR_DARK,
    globalFontFamily: FontFamily = JetBrainsMonoFontFamily,
    content: @Composable () -> Unit
) {
    val colorScheme = if (ideTheme.isDark) {
        darkColorScheme(
            primary = ideTheme.accent,
            onPrimary = ideTheme.bg,
            secondary = ideTheme.functionColor,
            onSecondary = ideTheme.bg,
            tertiary = ideTheme.keywordColor,
            onTertiary = ideTheme.bg,
            background = ideTheme.bg,
            onBackground = ideTheme.textPrimary,
            surface = ideTheme.surface,
            onSurface = ideTheme.textPrimary,
            surfaceVariant = ideTheme.surfaceHighlight,
            onSurfaceVariant = ideTheme.textSecondary,
            error = ideTheme.errorColor,
            onError = ideTheme.bg
        )
    } else {
        lightColorScheme(
            primary = ideTheme.accent,
            onPrimary = Color.White,
            secondary = ideTheme.functionColor,
            onSecondary = Color.White,
            tertiary = ideTheme.keywordColor,
            onTertiary = Color.White,
            background = ideTheme.bg,
            onBackground = ideTheme.textPrimary,
            surface = ideTheme.surface,
            onSurface = ideTheme.textPrimary,
            surfaceVariant = ideTheme.surfaceHighlight,
            onSurfaceVariant = ideTheme.textSecondary,
            error = ideTheme.errorColor,
            onError = Color.White
        )
    }

    val dynamicTypography = createIdeTypography(globalFontFamily)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = dynamicTypography
    ) {
        CompositionLocalProvider(
            LocalTextStyle provides dynamicTypography.bodyMedium.copy(
                fontFamily = globalFontFamily,
                color = ideTheme.textPrimary
            ),
            LocalContentColor provides ideTheme.textPrimary,
            content = content
        )
    }
}
