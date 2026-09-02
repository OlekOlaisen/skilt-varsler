package no.skiltvarsler.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Navy = Color(0xFF0B1220)
private val Card = Color(0xFF152033)
private val Accent = Color(0xFFE11D48)
private val Text = Color(0xFFF8FAFC)
private val Muted = Color(0xFF94A3B8)

private val Colors = darkColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    background = Navy,
    onBackground = Text,
    surface = Card,
    onSurface = Text,
    secondary = Muted,
    onSecondary = Navy,
)

@Composable
fun SkiltTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, content = content)
}
