package com.ganvo.music.ui.theme

import android.graphics.Bitmap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.palette.graphics.Palette

// Vibrant Modern Accent
val DefaultThemeColor = Color(0xFF8C52FF) 

private val DarkColorScheme = darkColorScheme(
    primary = DefaultThemeColor,
    background = Color(0xFF000000), // Pure OLED Black
    surface = Color(0xFF121212),
    surfaceVariant = Color(0xFF1E1E1E),
    secondaryContainer = Color(0xFF1A1A1A),
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onSurfaceVariant = Color(0xFFAAAAAA)
)

private val LightColorScheme = lightColorScheme(
    primary = DefaultThemeColor,
    background = Color(0xFFF7F7F9),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFF0F0F0),
    secondaryContainer = Color(0xFFE0E0E0),
    onPrimary = Color.White,
    onBackground = Color(0xFF121212),
    onSurface = Color(0xFF121212),
    onSurfaceVariant = Color(0xFF666666)
)

@Composable
fun GanvoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = true, // Force pure black for modern look
    themeColor: Color = DefaultThemeColor,
    content: @Composable () -> Unit,
) {
    val colors = remember(darkTheme, pureBlack, themeColor) {
        if (darkTheme) {
            DarkColorScheme.copy(primary = themeColor)
        } else {
            LightColorScheme.copy(primary = themeColor)
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography,
        content = content,
    )
}

fun Bitmap.extractThemeColor(): Color {
    val palette = Palette.from(this).maximumColorCount(8).generate()
    val swatch = palette.dominantSwatch ?: palette.vibrantSwatch ?: palette.mutedSwatch
    return swatch?.let { Color(it.rgb) } ?: DefaultThemeColor
}

fun Bitmap.extractGradientColors(darkTheme: Boolean = true): List<Color> {
    val palette = Palette.from(this).maximumColorCount(16).generate()
    val swatches = palette.swatches.sortedByDescending { it.population }.take(2)
    
    val colors = swatches.map { Color(it.rgb) }
    val orderedColors = if (darkTheme) {
        colors.sortedBy { it.luminance() }.reversed()
    } else {
        colors.sortedByDescending { it.luminance() }
    }

    return if (orderedColors.size >= 2)
        listOf(orderedColors[0], orderedColors[1])
    else
        listOf(Color(0xFF2A2A2A), Color(0xFF000000))
}

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}