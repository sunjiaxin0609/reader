package com.example.reader.ui.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.example.reader.data.ReaderSettings
import com.example.reader.data.ReaderTheme

object ReaderColors {
    val Brand = Color(0xFF534AB7)
    val Paper = Color(0xFFF8F6F1)
    val Sepia = Color(0xFFF2E6CD)
    val Night = Color(0xFF1B1B1B)
    val InkLight = Color(0xFF22201D)
    val InkSepia = Color(0xFF4B3B24)
    val InkNight = Color(0xFFD4D4D4)
}

@Composable
fun readerBackgroundColor(theme: ReaderTheme): Color = when (theme) {
    ReaderTheme.LIGHT -> ReaderColors.Paper
    ReaderTheme.SEPIA -> ReaderColors.Sepia
    ReaderTheme.DARK -> ReaderColors.Night
}

@Composable
fun readerTextColor(theme: ReaderTheme): Color = when (theme) {
    ReaderTheme.LIGHT -> ReaderColors.InkLight
    ReaderTheme.SEPIA -> ReaderColors.InkSepia
    ReaderTheme.DARK -> ReaderColors.InkNight
}

@Composable
fun ReaderAppTheme(settings: ReaderSettings, content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val controller = WindowCompat.getInsetsController((view.context as Activity).window, view)
            val lightIcons = settings.theme != ReaderTheme.DARK
            controller.isAppearanceLightStatusBars = lightIcons
            controller.isAppearanceLightNavigationBars = lightIcons
        }
    }

    val colorScheme = when (settings.theme) {
        ReaderTheme.LIGHT -> lightColorScheme(
            primary = ReaderColors.Brand,
            background = ReaderColors.Paper,
            surface = Color(0xFFFFFFFF),
            onBackground = ReaderColors.InkLight
        )
        ReaderTheme.SEPIA -> lightColorScheme(
            primary = Color(0xFF8A5A2B),
            background = ReaderColors.Sepia,
            surface = Color(0xFFF7EDD8),
            onBackground = ReaderColors.InkSepia
        )
        ReaderTheme.DARK -> darkColorScheme(
            primary = Color(0xFFAFA9EC),
            background = ReaderColors.Night,
            surface = Color(0xFF2A2A2A),
            onBackground = ReaderColors.InkNight
        )
    }
    MaterialTheme(colorScheme = colorScheme, content = content)
}
