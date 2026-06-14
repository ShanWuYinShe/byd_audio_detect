package com.byd.audiochanneldetector.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = BYDBlueLight,
    onPrimary = BackgroundDark,
    primaryContainer = BYDBlueDark,
    onPrimaryContainer = BYDBlueLight,
    secondary = MediaColor,
    onSecondary = BackgroundDark,
    background = BackgroundDark,
    onBackground = androidx.compose.ui.graphics.Color.White,
    surface = SurfaceDark,
    onSurface = androidx.compose.ui.graphics.Color.White
)

private val LightColorScheme = lightColorScheme(
    primary = BYDBlue,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = BYDBlueLight,
    onPrimaryContainer = BYDBlueDark,
    secondary = MediaColor,
    onSecondary = BackgroundDark,
    background = BackgroundLight,
    onBackground = androidx.compose.ui.graphics.Color.Black,
    surface = SurfaceLight,
    onSurface = androidx.compose.ui.graphics.Color.Black
)

@Composable
fun BYDAudioChannelDetectorTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    // Edge-to-Edge 模式：状态栏背景与字体深浅由 Activity.enableEdgeToEdge() 统一管理，
    // 此处不再手工设置 statusBarColor / isAppearanceLightStatusBars（两者均已 deprecated）。
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}