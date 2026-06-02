package com.duval.sesamelite.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColors = lightColorScheme(
    primary   = ListPrimaryLight,
    secondary = ListSecondaryLight,
    tertiary  = ListTertiaryLight,
)

private val DarkColors = darkColorScheme(
    primary   = ListPrimaryDark,
    secondary = ListSecondaryDark,
    tertiary  = ListTertiaryDark,
)

@Composable
fun SesameLiteTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography,
        content = content
    )
}
