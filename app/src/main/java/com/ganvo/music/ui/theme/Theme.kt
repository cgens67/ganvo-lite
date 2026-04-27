package com.ganvo.music.ui.theme

import android.graphics.Bitmap
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.SaverScope
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.palette.graphics.Palette
import com.ganvo.music.R

// Modern Blue Accent
val DefaultThemeColor = Color(0xFF007AFF)

private val DarkColorScheme = darkColorScheme(
    primary = DefaultThemeColor,
    background = Color(0xFF000000), 
    surface = Color(0xFF000000),
    surfaceVariant = Color(0xFF1A1A1A),
    secondaryContainer = Color(0xFF242426),
    onPrimary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White, // Title Color (High Contrast)
    onSurfaceVariant = Color(0xFFE0E0E0) // Subtitle Color (High Contrast)
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

val SfProDisplayFontFamily = FontFamily(
    Font(R.font.sf_pro_display_bold, FontWeight.Normal),
    Font(R.font.sf_pro_display_bold, FontWeight.Bold),
    Font(R.font.sf_pro_display_bold, FontWeight.Medium),
    Font(R.font.sf_pro_display_bold, FontWeight.SemiBold)
)

private val AppTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = SfProDisplayFontFamily),
        displayMedium = displayMedium.copy(fontFamily = SfProDisplayFontFamily),
        displaySmall = displaySmall.copy(fontFamily = SfProDisplayFontFamily),
        headlineLarge = headlineLarge.copy(fontFamily = SfProDisplayFontFamily),
        headlineMedium = headlineMedium.copy(fontFamily = SfProDisplayFontFamily),
        headlineSmall = headlineSmall.copy(fontFamily = SfProDisplayFontFamily),
        titleLarge = titleLarge.copy(fontFamily = SfProDisplayFontFamily),
        titleMedium = titleMedium.copy(fontFamily = SfProDisplayFontFamily),
        titleSmall = titleSmall.copy(fontFamily = SfProDisplayFontFamily),
        bodyLarge = bodyLarge.copy(fontFamily = SfProDisplayFontFamily),
        bodyMedium = bodyMedium.copy(fontFamily = SfProDisplayFontFamily),
        bodySmall = bodySmall.copy(fontFamily = SfProDisplayFontFamily),
        labelLarge = labelLarge.copy(fontFamily = SfProDisplayFontFamily),
        labelMedium = labelMedium.copy(fontFamily = SfProDisplayFontFamily),
        labelSmall = labelSmall.copy(fontFamily = SfProDisplayFontFamily)
    )
}

@Composable
fun GanvoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    pureBlack: Boolean = false,
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
        typography = AppTypography,
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