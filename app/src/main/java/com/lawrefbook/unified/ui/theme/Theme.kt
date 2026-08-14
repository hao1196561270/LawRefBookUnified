package com.lawrefbook.unified.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * 法条通 — Material 3 (Material You) 主题。
 *
 * 设计依据：Android Developers《Compose 中的 Material Design 3》官方规范。
 * - 动态配色（Material You）为默认路径：Android 12+ 从壁纸派生整套 tonal palette；
 * - 关闭动态或低版本时，回退到以「主题色 seed」为主色的完整 M3 配色方案；
 * - 显式 Typography（沿用 M3 默认字号/行高，标题与标签层级设 Medium 字重）；
 * - 统一 MD3 形状令牌（4/8/12/16/28dp）；
 * - 系统状态栏/导航栏与界面基底色（surface）对齐，消除深色模式下的色差。
 */

/** M3 浅色基准配色（完整角色，含中性容器与 inverse/surfaceTint）。 */
private val mdLightBase = lightColorScheme(
    primary = Color(0xFF6750A4),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFEADDFF),
    onPrimaryContainer = Color(0xFF21005D),
    secondary = Color(0xFF625B71),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFE8DEF8),
    onSecondaryContainer = Color(0xFF1D192B),
    tertiary = Color(0xFF7D5260),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFFFD8E4),
    onTertiaryContainer = Color(0xFF31111D),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFFEF7FF),
    onBackground = Color(0xFF1C1B1F),
    surface = Color(0xFFFEF7FF),
    onSurface = Color(0xFF1C1B1F),
    surfaceVariant = Color(0xFFE7E0EC),
    onSurfaceVariant = Color(0xFF49454F),
    outline = Color(0xFF79747E),
    outlineVariant = Color(0xFFCAC4D0),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFF313033),
    inverseOnSurface = Color(0xFFF4EFF4),
    inversePrimary = Color(0xFFD0BCFF),
    surfaceTint = Color(0xFF6750A4)
)

/** M3 深色基准配色（完整角色）。 */
private val mdDarkBase = darkColorScheme(
    primary = Color(0xFFD0BCFF),
    onPrimary = Color(0xFF381E72),
    primaryContainer = Color(0xFF4F378B),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    onSecondary = Color(0xFF332D41),
    secondaryContainer = Color(0xFF4A4458),
    onSecondaryContainer = Color(0xFFE8DEF8),
    tertiary = Color(0xFFEFB8C8),
    onTertiary = Color(0xFF492532),
    tertiaryContainer = Color(0xFF633B48),
    onTertiaryContainer = Color(0xFFFFD8E4),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
    background = Color(0xFF1C1B1F),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1C1B1F),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF49454F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    outline = Color(0xFF938F99),
    outlineVariant = Color(0xFF49454F),
    scrim = Color(0xFF000000),
    inverseSurface = Color(0xFFE6E1E5),
    inverseOnSurface = Color(0xFF313033),
    inversePrimary = Color(0xFF6750A4),
    surfaceTint = Color(0xFFD0BCFF)
)

private val White = Color(0xFFFFFFFF)
private val Black = Color(0xFF000000)

/** 将颜色按 ratio 向 target 混合（ratio=0 原色，ratio=1 target 色）。用于由 seed 派生连贯的 tonal 容器色。 */
private fun Color.blend(target: Color, ratio: Float): Color = Color(
    red = red * (1 - ratio) + target.red * ratio,
    green = green * (1 - ratio) + target.green * ratio,
    blue = blue * (1 - ratio) + target.blue * ratio,
    alpha = 1f
)

/**
 * 以 seed 为主色生成连贯的 M3 浅色配色：primary 取 seed，
 * 容器色按 Material Theme Builder 思路用 seed 的浅/深色调派生，保证整套配色同色相。
 */
private fun lightScheme(seed: Long) = mdLightBase.copy(
    primary = Color(seed),
    onPrimary = White,
    primaryContainer = Color(seed).blend(White, 0.84f),
    onPrimaryContainer = Color(seed).blend(Black, 0.62f),
    inversePrimary = Color(seed).blend(White, 0.45f),
    surfaceTint = Color(seed)
)

/**
 * 以 seed 为主色生成连贯的 M3 深色配色：深色下主色为 seed 的提亮调，
 * 容器/反色容器用 seed 的中调与浅调派生。
 */
private fun darkScheme(seed: Long) = mdDarkBase.copy(
    primary = Color(seed).blend(White, 0.55f),
    onPrimary = Color(seed).blend(Black, 0.62f),
    primaryContainer = Color(seed).blend(White, 0.30f),
    onPrimaryContainer = Color(seed).blend(White, 0.80f),
    inversePrimary = Color(seed),
    surfaceTint = Color(seed).blend(White, 0.55f)
)

/**
 * 显式 M3 排版：沿用默认 M3 字号/行高，按官方比例将标题与标签层级设为 Medium 字重，
 * 不指定 fontFamily 以保证中文渲染回退正确。
 */
private val AppTypography = Typography().copy(
    titleLarge = Typography().titleLarge.copy(fontWeight = FontWeight.Medium),
    titleMedium = Typography().titleMedium.copy(fontWeight = FontWeight.Medium),
    titleSmall = Typography().titleSmall.copy(fontWeight = FontWeight.Medium),
    labelLarge = Typography().labelLarge.copy(fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal)
)

/** MD3 形状令牌：extraSmall/small/medium/large/extraLarge。 */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun LawRefBookUnifiedTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    seed: Long = 0xFF6750A4,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        // Material You：Android 12+ 从壁纸派生整套 tonal palette
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        darkTheme -> darkScheme(seed)
        else -> lightScheme(seed)
    }

    // 系统状态栏/导航栏与界面基底色（surface）一致，消除深色模式下的「时间栏」色差
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.surface.toArgb()
            window.navigationBarColor = colorScheme.surface.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
