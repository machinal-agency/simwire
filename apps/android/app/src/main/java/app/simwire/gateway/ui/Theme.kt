package app.simwire.gateway.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Strict monochrome: hierarchy comes from type and hairlines, not color.
val Black = Color(0xFF000000)
val White = Color(0xFFFFFFFF)
val Grey70 = Color(0xFFB3B3B3)
val Grey50 = Color(0xFF808080)
val Grey30 = Color(0xFF4D4D4D)
val Hairline = Color(0xFF222222)

private val MonoColors = darkColorScheme(
    primary = White,
    onPrimary = Black,
    secondary = Grey70,
    background = Black,
    onBackground = White,
    surface = Black,
    onSurface = White,
    surfaceVariant = Black,
    onSurfaceVariant = Grey70,
    outline = Hairline,
)

@Composable
fun SimwireTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MonoColors, content = content)
}
