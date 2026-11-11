package com.v2ray.ang.ui.compose

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.v2ray.ang.AppConfig
import com.v2ray.ang.handler.MmkvManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private val LightColor = lightColorScheme(
    primary = Color(0xFF000000), // Black
    onPrimary = Color(0xFFFFFFFF), // White
    primaryContainer = Color(0xFFE0E0E0), // Light Gray
    onPrimaryContainer = Color(0xFF000000), // Black
    secondary = Color(0xFFf97910), // Orange
    onSecondary = Color(0xFFFFFFFF), // White
    secondaryContainer = Color(0xFFFFE8D6), // Pale Orange
    onSecondaryContainer = Color(0xFF2B1700), // Dark Brown
    tertiary = Color(0xFF009966), // Green
    onTertiary = Color(0xFFFFFFFF), // White
    tertiaryContainer = Color(0xFFA0F2D0), // Light Green
    onTertiaryContainer = Color(0xFF00201A), // Dark Teal
    error = Color(0xFFBA1A1A), // Red
    errorContainer = Color(0xFFFFDAD6), // Light Red
    onError = Color(0xFFFFFFFF), // White
    onErrorContainer = Color(0xFF410002), // Dark Red
    background = Color(0xFFFFFFFF), // White
    onBackground = Color(0xFF1C1B1F), // Near Black
    surface = Color(0xFFFFFFFF), // White
    onSurface = Color(0xFF1C1B1F), // Near Black
    surfaceVariant = Color(0xFFE7E0EC), // Light Purple Gray
    onSurfaceVariant = Color(0xFF49454F), // Dark Gray
    outline = Color(0xFF79747E), // Medium Gray
    outlineVariant = Color(0xFFCAC4D0), // Light Gray
    inverseSurface = Color(0xFF313033), // Dark Gray
    inverseOnSurface = Color(0xFFF4EFF4), // Very Light Gray
    inversePrimary = Color(0xFFC0C0C0), // Silver Gray
    scrim = Color(0xFF000000), // Black
    surfaceTint = Color(0xFF000000), // Black
    surfaceContainerLowest = Color(0xFFFFFFFF), // White
    surfaceContainerLow = Color(0xFFF7F7F7), // Very Light Gray
    surfaceContainer = Color(0xFFF1F1F1), // Light Gray
    surfaceContainerHigh = Color(0xFFEBEBEB), // Light Gray
    surfaceContainerHighest = Color(0xFFE5E5E5), // Light Gray
)

private val DarkColor = darkColorScheme(
    primary = Color(0xFFC0C0C0), // Silver Gray
    onPrimary = Color(0xFF303030), // Dark Gray
    primaryContainer = Color(0xFF474747), // Gray
    onPrimaryContainer = Color(0xFFE0E0E0), // Light Gray
    secondary = Color(0xFFf97910), // Orange
    onSecondary = Color(0xFF4E2600), // Dark Brown
    secondaryContainer = Color(0xFF6F3800), // Brown
    onSecondaryContainer = Color(0xFFFFE8D6), // Pale Orange
    tertiary = Color(0xFF83D6B5), // Mint Green
    onTertiary = Color(0xFF00382E), // Dark Teal
    tertiaryContainer = Color(0xFF005143), // Teal
    onTertiaryContainer = Color(0xFFA0F2D0), // Light Green
    error = Color(0xFFFFB4AB), // Light Red
    errorContainer = Color(0xFF93000A), // Dark Red
    onError = Color(0xFF690005), // Deep Red
    onErrorContainer = Color(0xFFFFDAD6), // Light Red
    background = Color(0xFF1C1B1F), // Near Black
    onBackground = Color(0xFFE6E1E5), // Light Gray
    surface = Color(0xFF1C1B1F), // Near Black
    onSurface = Color(0xFFE6E1E5), // Light Gray
    surfaceVariant = Color(0xFF49454F), // Dark Gray
    onSurfaceVariant = Color(0xFFCAC4D0), // Light Gray
    outline = Color(0xFF938F99), // Grayish Purple
    outlineVariant = Color(0xFF49454F), // Dark Gray
    inverseSurface = Color(0xFFE6E1E5), // Light Gray
    inverseOnSurface = Color(0xFF1C1B1F), // Near Black
    inversePrimary = Color(0xFF000000), // Black
    scrim = Color(0xFF000000), // Black
    surfaceTint = Color(0xFFC0C0C0), // Silver Gray
    surfaceContainerLowest = Color(0xFF0F0F12), // Near Black
    surfaceContainerLow = Color(0xFF1A191D), // Dark Gray
    surfaceContainer = Color(0xFF1E1D21), // Dark Gray
    surfaceContainerHigh = Color(0xFF282729), // Dark Gray
    surfaceContainerHighest = Color(0xFF333234), // Dark Gray
)

/**
 * VPS 007 brand palette (same tokens as the web cabinet fi.club-007.com): dark navy only.
 */
object Vps007Colors {
    val Bg = Color(0xFF0A0F1A)
    val Surface = Color(0xFF0F172A)
    val Surface2 = Color(0xFF172033)
    val Line = Color(0xFF1E293B)
    val Line2 = Color(0xFF334155)
    val Text = Color(0xFFF1F5F9)
    val Text2 = Color(0xFFCBD5E1)
    val Muted = Color(0xFF94A3B8)
    val Accent = Color(0xFF3B82F6)
    val Accent2 = Color(0xFF60A5FA)
    val AccentDeep = Color(0xFF1D4ED8)
    val Ok = Color(0xFF22C55E)
    val Gold = Color(0xFFDDBD70)
    val Danger = Color(0xFFF87171)
}

private val Vps007Color = darkColorScheme(
    primary = Vps007Colors.Accent,
    onPrimary = Color.White,
    primaryContainer = Color(0xFF1E3A8A),
    onPrimaryContainer = Color(0xFFDBEAFE),
    secondary = Vps007Colors.Gold,
    onSecondary = Color(0xFF2B2105),
    secondaryContainer = Color(0xFF3A3018),
    onSecondaryContainer = Color(0xFFF3E4BC),
    tertiary = Vps007Colors.Ok,
    onTertiary = Color(0xFF052E16),
    tertiaryContainer = Color(0xFF14532D),
    onTertiaryContainer = Color(0xFFBBF7D0),
    error = Vps007Colors.Danger,
    errorContainer = Color(0xFF7F1D1D),
    onError = Color(0xFF450A0A),
    onErrorContainer = Color(0xFFFEE2E2),
    background = Vps007Colors.Bg,
    onBackground = Vps007Colors.Text,
    surface = Vps007Colors.Bg,
    onSurface = Vps007Colors.Text,
    surfaceVariant = Vps007Colors.Surface2,
    onSurfaceVariant = Vps007Colors.Muted,
    outline = Vps007Colors.Line2,
    outlineVariant = Vps007Colors.Line,
    inverseSurface = Vps007Colors.Text,
    inverseOnSurface = Vps007Colors.Bg,
    inversePrimary = Vps007Colors.AccentDeep,
    scrim = Color.Black,
    surfaceTint = Vps007Colors.Accent,
    surfaceContainerLowest = Color(0xFF070B14),
    surfaceContainerLow = Vps007Colors.Surface,
    surfaceContainer = Vps007Colors.Surface,
    surfaceContainerHigh = Vps007Colors.Surface2,
    surfaceContainerHighest = Color(0xFF1C2740),
)

// Semantic Colors
val colorPing = Color(0xFF22C55E) // Green
val colorPingRed = Color(0xFFFF0099) // Pink Red
val colorConfigType = Color(0xFF60A5FA) // Blue
val colorFabActive = Color(0xFF3B82F6) // Blue
val colorFabInactiveLight = Color(0xFF9C9C9C) // Gray
val colorFabInactiveDark = Color(0xFF646464) // Dark Gray
val dividerColorLight = Color(0xFFE0E0E0) // Light Gray
val dividerColorDark = Color(0xFF424242) // Dark Gray

// Toast Colors 85%
val toastNormalBgLight = Color(0xD9353A3E) // Dark Gray
val toastNormalBgDark = Color(0xD94A4F54) // Darker Gray
val toastSuccessBg = Color(0xD9388E3C) // Green
val toastErrorBg = Color(0xD9D50000) // Red
val toastInfoBg = Color(0xD93F51B5) // Indigo Blue
val toastIconCircleBg = Color(0x33FFFFFF) // Semi-transparent White
val toastTextColor = Color.White // White

object ThemeManager {
    private val _themeMode = MutableStateFlow(
        MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    private val _dynamicColorEnabled = MutableStateFlow(
        MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, true)
    )
    val dynamicColorEnabled: StateFlow<Boolean> = _dynamicColorEnabled.asStateFlow()

    fun setThemeMode(mode: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, mode)
        _themeMode.value = mode
    }

    fun setDynamicColorEnabled(enabled: Boolean) {
        MmkvManager.encodeSettings(AppConfig.PREF_DYNAMIC_COLOR, enabled)
        _dynamicColorEnabled.value = enabled
    }

    fun refresh() {
        _themeMode.value =
            MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
        _dynamicColorEnabled.value =
            MmkvManager.decodeSettingsBool(AppConfig.PREF_DYNAMIC_COLOR, true)
    }
}

@Composable
fun resolveDarkTheme(): Boolean {
    val mode by ThemeManager.themeMode.collectAsState()
    return when (mode) {
        "1" -> false
        "2" -> true
        else -> isSystemInDarkTheme()
    }
}

val LocalDarkTheme = compositionLocalOf { false }

@Composable
fun AppTheme(
    // VPS 007: one brand look (dark navy, like the web cabinet); light/dynamic themes are not used.
    @Suppress("UNUSED_PARAMETER") darkTheme: Boolean = true,
    content: @Composable () -> Unit
) {
    val darkTheme = true
    val colorScheme = Vps007Color
    val snackbarController = rememberAppSnackbarController()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalAppSnackbar provides snackbarController
    ) {
        MaterialTheme(
            colorScheme = colorScheme
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppSnackbarBridge(controller = snackbarController)
                content()
                AppSnackbarHost(hostState = snackbarController.hostState)
            }
        }
    }
}
