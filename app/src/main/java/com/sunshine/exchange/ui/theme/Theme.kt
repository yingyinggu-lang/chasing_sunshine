package com.sunshine.exchange.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 天空之城配色：以晴空蓝为主调，阳光金为强调色。
 * 界面保持明亮（贴合"轻盈"命题），不使用深色模式以保证天际线观感一致。
 */
object SkyColors {
    val SkyTop = Color(0xFF1B6FD1)
    val SkyMid = Color(0xFF4A9DE8)
    val SkyBottom = Color(0xFFBFE3FF)
    val SkyLight = Color(0xFFEAF6FF)

    val SunGold = Color(0xFFFFB020)
    val SunGlow = Color(0xFFFFD54F)
    val CoinGold = Color(0xFFFFC94D)

    val CloudWhite = Color(0xFFFFFFFF)
    val CloudShadow = Color(0xFFD6E9F7)

    val DebtOrange = Color(0xFFFF8A4C)
    val SuccessGreen = Color(0xFF34C77B)
    val RainBlue = Color(0xFF6BA8E5)
    val SnowWhite = Color(0xFFF2F8FF)

    val TextPrimary = Color(0xFF16324F)
    val TextSecondary = Color(0xFF5B7691)
    val CardWhite = Color(0xFFFFFFFF)
    val DividerLight = Color(0xFFE3EEF7)

    val BuildingHouse = Color(0xFFFFB784)
    val BuildingTower = Color(0xFF8FA8C8)
    val BuildingYards = Color(0xFF7ED4A8)
    val BuildingDark = Color(0xFF4E6B8A)
}

private val SkyColorScheme = lightColorScheme(
    primary = SkyColors.SkyTop,
    onPrimary = Color.White,
    primaryContainer = SkyColors.SkyLight,
    onPrimaryContainer = SkyColors.TextPrimary,
    secondary = SkyColors.SunGold,
    onSecondary = Color.White,
    tertiary = SkyColors.SuccessGreen,
    background = SkyColors.SkyLight,
    onBackground = SkyColors.TextPrimary,
    surface = SkyColors.CardWhite,
    onSurface = SkyColors.TextPrimary,
    surfaceVariant = SkyColors.DividerLight,
    onSurfaceVariant = SkyColors.TextSecondary,
    error = SkyColors.DebtOrange
)

@Composable
fun SunshineTheme(content: @Composable () -> Unit) {
    // 有意固定使用亮色：天空之城的天气与天际线动画依赖明亮背景才能正确呈现
    @Suppress("UNUSED_EXPRESSION")
    isSystemInDarkTheme()
    MaterialTheme(
        colorScheme = SkyColorScheme,
        typography = androidx.compose.material3.Typography(
            headlineLarge = TextStyle(fontSize = 30.sp, fontWeight = FontWeight.Bold),
            headlineMedium = TextStyle(fontSize = 24.sp, fontWeight = FontWeight.Bold),
            titleLarge = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.SemiBold),
            titleMedium = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
            bodyLarge = TextStyle(fontSize = 15.sp),
            bodyMedium = TextStyle(fontSize = 13.sp),
            labelSmall = TextStyle(fontSize = 11.sp)
        ),
        content = content
    )
}