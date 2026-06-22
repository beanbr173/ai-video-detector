package com.kreativesolutions.aivideodetector.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val BrandBlue = Color(0xFF1565C0)
private val BrandDark = Color(0xFF0D47A1)
private val SurfaceTint = Color(0xFFF5F8FC)

private val LightColors = lightColorScheme(
    primary = BrandBlue,
    onPrimary = Color.White,
    secondary = BrandDark,
    background = SurfaceTint,
    surface = Color.White
)

@Composable
fun AiVideoDetectorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        content = content
    )
}
