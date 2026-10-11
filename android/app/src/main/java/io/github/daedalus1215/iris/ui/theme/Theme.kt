package io.github.daedalus1215.iris.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** The web remote's palette. */
object IrisColors {
    val Background = Color(0xFF0A0A1A)
    val BackgroundEnd = Color(0xFF16213E)
    val Surface = Color(0xFF1A1A2E)
    val Navigation = Color(0xFF4A8FE0)
    val Media = Color(0xFFF0605D)
    val PowerOff = Color(0xFFE5484D)
    val PowerOn = Color(0xFF12A594)
    val Badge = Color(0xFFFFA94D)
}

private val colorScheme = darkColorScheme(
    primary = IrisColors.Navigation,
    secondary = IrisColors.Media,
    background = IrisColors.Background,
    surface = IrisColors.Surface,
    error = Color(0xFFFF8A80),
)

@Composable
fun IrisTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = colorScheme, content = content)
}
