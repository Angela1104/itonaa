package com.bakhawone.thesis_bakhawone.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF7D8A6F),
    secondary = Color(0xFF607048),
    background = Color(0xFFF9F9F9),
    outline = Color(0xFF000000),
)

@Composable
fun ThesisbakhawoneTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LightColors,
        typography = androidx.compose.material3.Typography(),
        content = content
    )
}