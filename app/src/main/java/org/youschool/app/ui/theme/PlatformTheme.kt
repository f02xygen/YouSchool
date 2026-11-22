package org.youschool.app.ui.theme
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.material3.*
@Composable
fun platformColorScheme(settings: ThemeSettings, dark: Boolean): ColorScheme? =
    if (settings.isDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (dark) dynamicDarkColorScheme(LocalContext.current) else dynamicLightColorScheme(LocalContext.current)
    } else null
