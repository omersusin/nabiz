package com.omersusin.nabiz.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val Ink = Color(0xFF100E0D)
val Ember = Color(0xFFE8A13A)
val Rose = Color(0xFFE06A5B)
val Teal = Color(0xFF5FB3A3)
val Violet = Color(0xFF9B87D4)
val Sand = Color(0xFFEDE4D8)

/** Lane colours, warm through cool — never all the same hue. */
val LaneColors = listOf(Ember, Rose, Teal, Violet)

private val NabizColors = darkColorScheme(
    primary = Ember,
    onPrimary = Color(0xFF241503),
    secondary = Rose,
    onSecondary = Color(0xFF2B0F0B),
    tertiary = Teal,
    onTertiary = Color(0xFF06231E),
    background = Ink,
    onBackground = Sand,
    surface = Color(0xFF171413),
    onSurface = Sand,
    surfaceVariant = Color(0xFF221E1C),
    onSurfaceVariant = Color(0xFFC9BFB4),
    outline = Color(0xFF4A423D),
)

@Composable
fun NabizTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = NabizColors, content = content)
}