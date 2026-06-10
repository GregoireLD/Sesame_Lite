package com.paris.duval.sesamelite.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

private val LightColors = lightColorScheme(
    primary   = ListPrimaryLight,
    secondary = ListSecondaryLight,
    tertiary  = ListTertiaryLight,
    error     = SesameRed,
)

private val DarkColors = darkColorScheme(
    primary   = ListPrimaryDark,
    secondary = ListSecondaryDark,
    tertiary  = ListTertiaryDark,
    error     = SesameRed,
)

@Composable
fun SesameLiteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    CompositionLocalProvider(LocalSesameColors provides SesameColors(dangerous = SesameRed)) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography,
            content = content
        )
    }
}
