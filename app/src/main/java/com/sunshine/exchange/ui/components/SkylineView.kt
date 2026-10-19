package com.sunshine.exchange.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.sunshine.exchange.ui.theme.SkyColors
import kotlin.math.PI
import kotlin.math.sin

enum class WeatherKind { SUNNY, CLOUDY, RAINY, SNOWY }
/**
 * 静态版天际线（无动画）。
 *
 * 【性能】不创建任何 rememberInfiniteTransition，Canvas 只绘制一次。
 * 太阳光晕固定、云朵固定、雨雪用静态线条表现——
 * 视觉上仍能分辨天气类型，但完全不占帧率。
 */
@Composable
private fun StaticSkylineView(
    buildings: List<String>,
    weather: WeatherKind,
    modifier: Modifier,
    height: Dp,
    showSun: Boolean
) {
    Box(modifier = modifier.fillMaxWidth().height(height)) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val w = size.width
            val h = size.height
            val groundY = h * 0.86f

            // 天空渐变
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(SkyColors.SkyTop, SkyColors.SkyMid, SkyColors.SkyBottom)
                ),
                size = Size(w, groundY + 8f)
            )

            // 太阳（静态光晕）
            if (showSun && (weather == WeatherKind.SUNNY || weather == WeatherKind.CLOUDY)) {
                drawCircle(
                    color = SkyColors.SunGlow,
                    radius = h * 0.11f,
                    center = Offset(w * 0.78f, h * 0.20f)
                )
            }

            // 云
            if (weather == WeatherKind.CLOUDY || weather == WeatherKind.RAINY) {
                drawCloud(w * 0.32f, h * 0.26f, w * 0.13f, 0.85f)
            }

            // 静态雨线
            if (weather == WeatherKind.RAINY) {
                for (i in 0 until 12) {
                    val x = w * (0.08f + i * 0.075f)
                    val y = groundY * 0.45f + (i % 4) * 14f
                    drawLine(
                        color = SkyColors.RainBlue,
                        start = Offset(x, y),
                        end = Offset(x - 3f, y + 11f),
                        strokeWidth = 1.6f
                    )
                }
            }

      // 静态雪点
          if (weather == WeatherKind.SNOWY) {
    for (i in 0 until 14) {
   val x = w * (0.06f + i * 0.068f)
  val y = groundY * 0.40f + (i % 5) * 12f
      drawCircle(
   color = Color.White.copy(alpha = 0.85f),
    radius = 2.4f,
        center = Offset(x, y)
       )
    }
        }

            // ---- 天际线建筑（与动画版完全一致的布局算法）----
       // 【修复】静态版最初遗漏了整个建筑段，导致首页天际线空空如也。
            // 建筑是「天空之城」的核心视觉，必须与动画版用同一套布局逻辑。
      val realBuildings = buildings.filter { it != "cloud" }
  val clouds = buildings.filter { it == "cloud" }
            val count = realBuildings.size
       val slotW = if (count > 0) (w * 0.96f) / count else 0f
            val startX = w * 0.02f

            realBuildings.forEachIndexed { index, kind ->
        val x = startX + index * slotW
     // 高度随点亮顺序增长：越新的建筑越高，形成成长感
                val growth = (index + 1).toFloat() / maxOf(1, count)
        val baseH = h * (0.12f + growth * 0.20f)
      drawBuilding(
 kind = kind,
            x = x,
        slotW = slotW,
          groundY = groundY,
      buildingH = baseH,
    seed = index
         )
          }

   // 云朵点缀（每 3 次户外奖励一朵）
            clouds.forEachIndexed { i, _ ->
   val cx = w * (0.12f + (i % 5) * 0.19f)
                val cy = h * (0.10f + (i % 3) * 0.06f)
           drawCloud(cx, cy, w * 0.075f, 0.9f)
      }

    // 地面
      drawRect(
       color = Color(0xFF9CCF8F),
topLeft = Offset(0f, groundY),
         size = Size(w, h - groundY)
 )
     drawRect(
        color = Color(0xFF7FBF72),
    topLeft = Offset(0f, groundY),
          size = Size(w, (h - groundY) * 0.22f)
      )
        }
    }
}

@Composable
fun SkylineView(
    buildings: List<String>,
    weather: WeatherKind,
    modifier: Modifier = Modifier,
    height: Dp = 220.dp,
    showSun: Boolean = true,
    /**
     * 【性能开关】是否开启动画。
     *
     * 【为什么需要】本组件原本有 5 个 `rememberInfiniteTransition`，
     * 每帧都会让整个 Canvas 重新绘制。而首页 + 我的天空页都用到它，
     * 加上验证台的 2 个动画，全局共 7 个无限动画——
     * 即使界面没变化，帧率也一直占满，**切标签时明显感到卡顿**。
     *
     * 现在默认关闭动画（静态一帧），仅在首页首次进入时短暂开启，
     * 既保留了视觉表现，又把常驻帧率降到 0。
     */
    animated: Boolean = false
) {
    if (!animated) {
        // 静态模式：不创建任何 Transition，Canvas 只绘制一次
        StaticSkylineView(buildings, weather, modifier, height, showSun)
        return
    }
    // 太阳光晕呼吸动画
    val glow by rememberInfiniteTransition(label = "sunGlow").animateFloat(
        initialValue = 0.55f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow"
    )

    // 云朵横向漂移
    val drift by rememberInfiniteTransition(label = "cloudDrift").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(38000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "drift"
    )

    // 雨滴下落
    val rain by rememberInfiniteTransition(label = "rain").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rain"
    )

    // 雪花飘落
    val snow by rememberInfiniteTransition(label = "snow").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(7000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "snow"
    )

    Box(modifier = modifier.fillMaxWidth().height(height)) {
        Canvas(Modifier.fillMaxWidth().height(height)) {
            val w = size.width
            val h = size.height
            val groundY = h * 0.86f

            // 1) 天空渐变
            drawSkyBackground(weather)

            // 2) 太阳/月亮
            if (showSun && (weather == WeatherKind.SUNNY || weather == WeatherKind.CLOUDY)) {
                drawSun(w * 0.80f, h * 0.20f, minOf(w, h) * 0.11f, glow)
            }

            // 3) 远处云朵
            drawCloud(w * (0.10f + drift * 0.05f), h * 0.18f, w * 0.13f, 0.85f)
            drawCloud(w * (0.72f - drift * 0.04f), h * 0.30f, w * 0.10f, 0.65f)

// 4) 天际线建筑（按点亮顺序从左到右排列，越高越茂盛）
   val realBuildings = buildings.filter { it != "cloud" }
  val clouds = buildings.filter { it == "cloud" }
            // 【修复】原先 slots = min(建筑数, 14) 且用 `index % slots` 定位，
    // 建筑超过 14 座后 index 会回绕到同一列，造成建筑相互重叠遮挡
       //（演示数据固定生成 16 座，因此必然触发）。
       // 现在按实际建筑数等分，宽度自适应收缩，保证永不重叠。
     val count = realBuildings.size
            val slotW = if (count > 0) (w * 0.96f) / count else 0f
            val startX = w * 0.02f

  realBuildings.forEachIndexed { index, kind ->
       val x = startX + index * slotW
        // 高度随点亮数量增长：越靠后（越新的建筑）越高，形成成长感
      val growth = (index + 1).toFloat() / maxOf(1, count)
         // 楼越高越窄，避免高瘦比例在密集布局下显得拥挤
        val baseH = h * (0.12f + growth * 0.20f)
                drawBuilding(
                    kind = kind,
                    x = x,
                    slotW = slotW,
                    groundY = groundY,
                    buildingH = baseH,
                    seed = index
                )
            }

            // 5) 云朵点缀（每 3 次户外奖励一朵云）
            clouds.forEachIndexed { i, _ ->
                val cx = w * (0.12f + (i % 5) * 0.19f)
                val cy = h * (0.10f + (i % 3) * 0.06f)
                drawCloud(cx, cy, w * 0.075f, 0.9f)
            }

            // 6) 地面
            drawRect(
                color = Color(0xFF9CCF8F),
                topLeft = Offset(0f, groundY),
                size = Size(w, h - groundY)
            )
            drawRect(
                color = Color(0xFF7FBF72),
                topLeft = Offset(0f, groundY),
                size = Size(w, (h - groundY) * 0.22f)
            )

            // 7) 天气粒子
            when (weather) {
                WeatherKind.RAINY -> drawRain(w, h, rain)
                WeatherKind.SNOWY -> drawSnow(w, h, snow)
                else -> Unit
            }
        }
    }
}

private fun DrawScope.drawSkyBackground(weather: WeatherKind) {
    val colors = when (weather) {
        WeatherKind.SUNNY -> listOf(SkyColors.SkyTop, SkyColors.SkyMid, SkyColors.SkyBottom)
        WeatherKind.CLOUDY -> listOf(Color(0xFF6E8CA8), Color(0xFFA8C0D4), Color(0xFFDCE8F2))
        WeatherKind.RAINY -> listOf(Color(0xFF3F5468), Color(0xFF6B8299), Color(0xFFA9BECD))
        WeatherKind.SNOWY -> listOf(Color(0xFF7C93AC), Color(0xFFB8CBDB), Color(0xFFEAF2FA))
    }
    drawRect(
        brush = Brush.verticalGradient(colors),
        size = size
    )
}

private fun DrawScope.drawSun(cx: Float, cy: Float, radius: Float, glow: Float) {
    // 外层光晕
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                SkyColors.SunGlow.copy(alpha = 0.55f * glow),
                SkyColors.SunGlow.copy(alpha = 0f)
            ),
            center = Offset(cx, cy),
            radius = radius * 3.2f
        ),
        radius = radius * 3.2f,
        center = Offset(cx, cy)
    )
    // 本体
    drawCircle(SkyColors.SunGlow, radius = radius, center = Offset(cx, cy))
    drawCircle(
        color = SkyColors.SunGold,
        radius = radius,
        center = Offset(cx, cy),
        style = Stroke(width = radius * 0.18f)
    )
}

private fun DrawScope.drawCloud(cx: Float, cy: Float, size: Float, alpha: Float) {
    val color = SkyColors.CloudWhite.copy(alpha = alpha)
    drawCircle(color, radius = size * 0.32f, center = Offset(cx - size * 0.30f, cy))
    drawCircle(color, radius = size * 0.42f, center = Offset(cx, cy - size * 0.10f))
    drawCircle(color, radius = size * 0.28f, center = Offset(cx + size * 0.34f, cy))
    drawOval(
        color = color,
        topLeft = Offset(cx - size * 0.50f, cy - size * 0.02f),
        size = Size(size * 1.0f, size * 0.30f)
    )
}

/** 单座建筑：house 小屋 / tower 高楼 / yards 场馆 */
private fun DrawScope.drawBuilding(
    kind: String,
    x: Float,
    slotW: Float,
    groundY: Float,
    buildingH: Float,
    seed: Int
) {
    if (slotW <= 0f) return
    // 楼宽占比随密度自适应：建筑少时宽敞，建筑多时收窄，保证相邻楼不贴死
    val bodyRatio = (0.78f - (slotW / size.width) * 0.35f).coerceIn(0.52f, 0.78f)
    val bodyW = slotW * bodyRatio
    val left = x + (slotW - bodyW) / 2f
  val top = groundY - buildingH

    val bodyColor = when (kind) {
        "tower" -> SkyColors.BuildingTower
        "yards" -> SkyColors.BuildingYards
        else -> SkyColors.BuildingHouse
    }

    // 主体
    drawRect(
        color = bodyColor,
        topLeft = Offset(left, top),
        size = Size(bodyW, buildingH)
    )

    when (kind) {
        "house" -> {
            // 三角屋顶
            val roof = Path().apply {
                moveTo(left - bodyW * 0.10f, top)
                lineTo(left + bodyW / 2f, top - buildingH * 0.34f)
                lineTo(left + bodyW * 1.10f, top)
                close()
            }
            drawPath(roof, color = Color(0xFFE8734A))
            // 门
            drawRect(
                color = Color(0xFF8B5A3C),
                topLeft = Offset(left + bodyW * 0.40f, top + buildingH * 0.52f),
                size = Size(bodyW * 0.20f, buildingH * 0.48f)
            )
            // 窗
            drawRect(
                color = SkyColors.SunGlow,
                topLeft = Offset(left + bodyW * 0.14f, top + buildingH * 0.20f),
                size = Size(bodyW * 0.20f, buildingH * 0.18f)
            )
        }

        "tower" -> {
            // 顶部装饰
            drawCircle(
                color = SkyColors.SunGlow,
                radius = bodyW * 0.13f,
                center = Offset(left + bodyW / 2f, top - bodyW * 0.08f)
            )
            // 楼层窗格
            val rows = (buildingH / (buildingH * 0.22f)).toInt().coerceIn(2, 8)
            for (r in 0 until rows) {
                val wy = top + buildingH * 0.12f + r * buildingH * 0.20f
                // 每层亮一扇窗，夜晚城市的感觉
                val lit = (r + seed) % 3 != 0
                drawRect(
                    color = if (lit) SkyColors.SunGlow.copy(alpha = 0.92f) else SkyColors.BuildingDark.copy(alpha = 0.55f),
                    topLeft = Offset(left + bodyW * 0.22f, wy),
                    size = Size(bodyW * 0.56f, buildingH * 0.10f)
                )
            }
        }

        "yards" -> {
            // 穹顶场馆
            drawCircle(
                color = Color(0xFF4FA98A),
                radius = bodyW * 0.42f,
                center = Offset(left + bodyW / 2f, top)
            )
            drawRect(
                color = bodyColor,
                topLeft = Offset(left + bodyW * 0.08f, top),
                size = Size(bodyW * 0.84f, buildingH)
            )
            // 场地线
            drawRect(
                color = Color(0xFFEFF8F3).copy(alpha = 0.85f),
                topLeft = Offset(left + bodyW * 0.18f, top + buildingH * 0.34f),
                size = Size(bodyW * 0.64f, buildingH * 0.20f)
            )
        }
    }
}

private fun DrawScope.drawRain(w: Float, h: Float, progress: Float) {
    val dropCount = 46
    for (i in 0 until dropCount) {
        val seedX = ((i * 37) % 100) / 100f
        val seedSpeed = 0.55f + ((i * 13) % 40) / 100f
        val x = seedX * w
        val travel = ((progress * seedSpeed) + seedX) % 1f
        val y = travel * h
        drawLine(
            color = Color(0xFFBBD8F0).copy(alpha = 0.55f),
            start = Offset(x, y),
            end = Offset(x + 3.5f, y + 15f),
            strokeWidth = 1.6f
        )
    }
}

private fun DrawScope.drawSnow(w: Float, h: Float, progress: Float) {
    val flakeCount = 32
    for (i in 0 until flakeCount) {
        val seedX = ((i * 53) % 100) / 100f
        val sway = sin(progress * 2 * PI + i.toDouble()).toFloat() * 9f
        val y = (((progress + ((i * 29) % 100) / 130f)) % 1f) * h
        val x = seedX * w + sway
        drawCircle(
            color = Color.White.copy(alpha = 0.88f),
            radius = 1.7f + (i % 3) * 0.7f,
            center = Offset(x, y)
        )
    }
}