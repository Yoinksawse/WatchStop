package com.example.watchstop.view.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext


val NeonLime = Color(0xFFC6FF00)
val ElectricYellow = Color(0xFFFFF176)
val SpaceGrey = Color(0xFF121212)
val CarbonGrey = Color(0xFF1E1E1E)
val SlateGrey = Color(0xFF2C2C2C)
val AndroidPurple = Purple40
private val DarkColorScheme = darkColorScheme(
    primary = NeonLime,
    onPrimary = Color.Black,

    secondary = CarbonGrey,
    onSecondary = Color.White,

    tertiary = ElectricYellow,
    onTertiary = Color.Black,

    background = SpaceGrey,
    onBackground = Color.White,

    surface = CarbonGrey,
    onSurface = Color.White,

    outline = Color.Gray,
    surfaceVariant = SlateGrey,
    onSurfaceVariant = Color.LightGray
)
private val LightColorScheme = lightColorScheme(
    primary = AndroidPurple,
    secondary = PurpleGrey40,
    tertiary = Pink40,

    surface = Color.White,
    onPrimary = Color.White,
    onSurface = Color.Black
)


    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */

@Composable
fun WatchStopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+ (nig i dont want it)
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content,
    )
}
