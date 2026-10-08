package no.skiltvarsler.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Ink = Color(0xFF101418)
private val Card = Color(0xFF1B222B)
private val CardHigh = Color(0xFF262F3A)
private val Amber = Color(0xFFF0B429)
private val AmberInk = Color(0xFF1C1404)
private val Cream = Color(0xFFF4F1EA)
private val Mist = Color(0xFF9AA3AD)
private val Stop = Color(0xFFE15A4A)

private val Colors = darkColorScheme(
    primary = Amber,
    onPrimary = AmberInk,
    background = Ink,
    onBackground = Cream,
    surface = Card,
    onSurface = Cream,
    surfaceVariant = CardHigh,
    onSurfaceVariant = Mist,
    secondary = Mist,
    onSecondary = Ink,
    secondaryContainer = Color(0xFF3A3118),
    onSecondaryContainer = Amber,
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
