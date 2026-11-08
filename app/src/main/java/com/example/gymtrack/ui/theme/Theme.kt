package com.example.gymtrack.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

enum class AppBrand { Gym, Nutrition }

private val AppRed = Color(0xFFD32F2F)
private val NutritionGreenDark = Color(0xFF1B5E20)
private val NutritionGreenLight = Color(0xFF2E7D32)

private val GymDarkScheme = darkColorScheme(
    primary = AppRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB71C1C),
    onPrimaryContainer = Color.White,
    error = AppRed,
    onError = Color.White,
    secondary = Color(0xFFEF9A9A),
    onSecondary = Color(0xFF1F1A1A),
    background = Color(0xFF121212),
    onBackground = Color.White,
    surface = Color(0xFF1E1E1E),
    onSurface = Color.White,
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFEAEAEA),
    outline = Color(0xFF6E6E6E)
)

private val GymLightScheme = lightColorScheme(
    primary = AppRed,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD6),
    onPrimaryContainer = Color(0xFF410001),
    error = AppRed,
    onError = Color.White,
    secondary = Color(0xFFFFCDD2),
    onSecondary = Color(0xFF1F1A1A),
    background = Color(0xFFFFFBFB),
    onBackground = Color(0xFF1F1A1A),
    surface = Color.White,
    onSurface = Color(0xFF1F1A1A),
    surfaceVariant = Color(0xFFFFEDEA),
    onSurfaceVariant = Color(0xFF3A2A2A),
    outline = Color(0xFF7E6666)
)

private val NutritionDarkScheme = darkColorScheme(
    primary = Color(0xFF81C784),
    onPrimary = Color(0xFF003910),
    primaryContainer = NutritionGreenDark,
    onPrimaryContainer = Color(0xFFC8E6C9),
    error = Color(0xFFEF9A9A),
    onError = Color(0xFF1F1A1A),
    secondary = Color(0xFFA5D6A7),
    onSecondary = Color(0xFF0D1F12),
    background = Color(0xFF0D1A10),
    onBackground = Color(0xFFE8F5E9),
    surface = Color(0xFF142418),
    onSurface = Color(0xFFE8F5E9),
    surfaceVariant = Color(0xFF1E3324),
    onSurfaceVariant = Color(0xFFC8E6C9),
    outline = Color(0xFF5D7A62)
)

private val NutritionLightScheme = lightColorScheme(
    primary = NutritionGreenLight,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFC8E6C9),
    onPrimaryContainer = Color(0xFF1B5E20),
    error = Color(0xFFC62828),
    onError = Color.White,
    secondary = Color(0xFFA5D6A7),
    onSecondary = Color(0xFF1B3A20),
    background = Color(0xFFF1F8E9),
    onBackground = Color(0xFF1B3A20),
    surface = Color(0xFFF7FBF2),
    onSurface = Color(0xFF1B3A20),
    surfaceVariant = Color(0xFFE8F5E9),
    onSurfaceVariant = Color(0xFF2E4A34),
    outline = Color(0xFF6B8A70)
)

@Composable
fun GymTrackTheme(
    forceDarkTheme: Boolean? = null,
    brand: AppBrand = AppBrand.Gym,
    content: @Composable () -> Unit
) {
    val darkTheme = forceDarkTheme ?: isSystemInDarkTheme()
    val scheme = when (brand) {
        AppBrand.Gym -> if (darkTheme) GymDarkScheme else GymLightScheme
        AppBrand.Nutrition -> if (darkTheme) NutritionDarkScheme else NutritionLightScheme
    }
    MaterialTheme(
        colorScheme = scheme,
        content = content
    )
}
