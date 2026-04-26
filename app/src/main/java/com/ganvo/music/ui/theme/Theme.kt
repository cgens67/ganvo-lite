package com.ganvo.music.ui.theme

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette

val DefaultThemeColor = Color(0xFF4285F4)

private val DarkColorScheme = darkColorScheme(
    primary = DefaultThemeColor,
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E)
)

private val LightColorScheme = lightColorScheme(
    primary = DefaultThemeColor,
    background = Color(0xFFFDFDFD),
    surface = Color(0xFFFFFFFF)
)

@Composable
fun GanvoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
    themeColor: Color = DefaultThemeColor,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colors = remember(darkTheme, pureBlack, themeColor) {
        if (themeColor == DefaultThemeColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (darkTheme) {
                dynamicDarkColorScheme(context).pureBlack(pureBlack)
            } else {
                dynamicLightColorScheme(context)
            }
        } else {
            if (darkTheme) {
                if (pureBlack) DarkColorScheme.copy(background = Color.Black, surface = Color.Black, primary = themeColor)
                else DarkColorScheme.copy(primary = themeColor)
            } else {
                LightColorScheme.copy(primary = themeColor)
            }
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

fun Bitmap.extractGradientColors(darkTheme: Boolean = false): List<Color> {
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
        listOf(Color(0xFF595959), Color(0xFF0D0D0D))
}

fun ColorScheme.pureBlack(apply: Boolean) =
    if (apply) {
        copy(
            surface = Color.Black,
            background = Color.Black,
        )
    } else {
        this
    }

val ColorSaver = object : Saver<Color, Int> {
    override fun restore(value: Int): Color = Color(value)
    override fun SaverScope.save(value: Color): Int = value.toArgb()
}