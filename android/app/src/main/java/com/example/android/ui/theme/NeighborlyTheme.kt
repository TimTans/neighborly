package com.example.android.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

object NeighborlyColors {
    val Background = Color(0xFFF7F3EC)
    val Surface = Color.White
    val TextPrimary = Color(0xFF1A1A1A)
    val TextStrong = Color(0xFF333333)
    val TextBody = Color(0xFF424242)
    val TextSecondary = Color(0xFF777777)
    val TextTertiary = Color(0xFF9E9E9E)
    val IconMuted = Color(0xFF757575)
    val NavigationUnselected = Color(0xFF8E8E8E)
    val Border = Color(0xFFD7D7D7)
    val MutedControl = Color(0xFFD8D8D8)
    val FieldBackground = Color(0xFFF8F8F8)

    val Green = Color(0xFF0C6A4A)
    val GreenSoft = Color(0xFFE0F1E8)
    val Blue = Color(0xFF1E63C6)
    val BlueSoft = Color(0xFFE7F0FF)
    val Orange = Color(0xFFE67E22)
    val OrangeSoft = Color(0xFFFFF3E0)
    val MapGradientStart = Color(0xFFCCE7D9)
    val MapText = Color(0xFF4F7E6B)
}

object NeighborlySpacing {
    val ScreenHorizontal = 16.dp
    val ScreenVertical = 12.dp
    val CardPadding = 16.dp
    val CardGap = 12.dp
}

object NeighborlyShapes {
    val Small = RoundedCornerShape(6.dp)
    val Medium = RoundedCornerShape(14.dp)
    val Card = RoundedCornerShape(20.dp)
    val LargeCard = RoundedCornerShape(24.dp)
    val Pill = RoundedCornerShape(999.dp)
}

private val NeighborlyColorScheme: ColorScheme = lightColorScheme(
    primary = NeighborlyColors.Green,
    secondary = NeighborlyColors.Orange,
    tertiary = NeighborlyColors.Blue,
    background = NeighborlyColors.Background,
    surface = NeighborlyColors.Surface,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = NeighborlyColors.TextPrimary,
    onSurface = NeighborlyColors.TextPrimary
)

private val NeighborlyMaterialShapes = Shapes(
    small = NeighborlyShapes.Small,
    medium = NeighborlyShapes.Medium,
    large = NeighborlyShapes.Card
)

@Composable
fun NeighborlyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = NeighborlyColorScheme,
        typography = Typography(),
        shapes = NeighborlyMaterialShapes,
        content = content
    )
}
