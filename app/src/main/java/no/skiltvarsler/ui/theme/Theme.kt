package no.skiltvarsler.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Ink = Color(0xFF1A1518)
private val Card = Color(0xFF20242A)
private val CardHigh = Color(0xFF2A3038)
private val Coral = Color(0xFFF85B60)
private val Cream = Color(0xFFF5F5F5)
private val Mist = Color(0xFFA8B0B8)
private val Stop = Color(0xFFE04850)

private val Colors = darkColorScheme(
    primary = Coral,
    onPrimary = Color.White,
    background = Ink,
    onBackground = Cream,
    surface = Card,
    onSurface = Cream,
    surfaceVariant = CardHigh,
    onSurfaceVariant = Mist,
    secondary = Mist,
    onSecondary = Ink,
    secondaryContainer = Color(0xFF3A2428),
    onSecondaryContainer = Coral,
    error = Stop,
    onError = Color.White,
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(28.dp),
)

@Composable
fun SkiltTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Colors, shapes = AppShapes, content = content)
}
