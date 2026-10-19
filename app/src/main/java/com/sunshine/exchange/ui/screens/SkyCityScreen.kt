package com.sunshine.exchange.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sunshine.exchange.core.DateUtils
import com.sunshine.exchange.data.TaskCard
import com.sunshine.exchange.ui.HomeState
import com.sunshine.exchange.ui.components.SkylineView
import com.sunshine.exchange.ui.components.WeatherKind
import com.sunshine.exchange.ui.theme.SkyColors

/**
 * ① 天空之城（首页）。
 * 天际线成长可视化 + 真实天气动画 + 今日阳光/余额 + 推荐任务卡。
 */
@Composable
fun SkyCityScreen(
    state: HomeState,
    onTaskClick: (TaskCard) -> Unit,
    onSeeAllTasks: () -> Unit,
    onOpenSettings: () -> Unit,
    /** 【性能】是否开启动画；false = 静态绘制，零帧率占用 */
    animated: Boolean = false
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SkyColors.SkyLight),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp)
    ) {
        item {
            // ---- 顶部：今日概览 ----
            TopSummaryCard(state = state, onOpenSettings = onOpenSettings, animated = animated)
        }

        item {
            // ---- 中部：天空之城天际线 + 天气 ----
            SkylineSection(state = state, animated = animated)
        }

        item {
            // ---- 今日推荐任务卡 ----
            SectionHeader(
                title = "今日推荐",
                subtitle = "完成任务卡，点亮一座建筑",
                actionText = "全部任务",
                onAction = onSeeAllTasks
            )
        }

        items(state.recommend, key = { it.id }) { card ->
            TaskCardRow(
                card = card,
                balance = state.coinBalance,
                onClick = { onTaskClick(card) }
            )
        }

        if (state.recommend.isEmpty()) {
            item {
                // 新用户余额为 0 时，推荐列表会被过滤为空。
                // 这里必须给出「下一步做什么」的可操作引导，否则用户会以为 App 没反应。
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("☀️", style = MaterialTheme.typography.headlineLarge)
                        Spacer(Modifier.height(10.dp))
                        Text(
if (state.coinBalance <= 0)
       "还没有阳光币\n完成一张真户外任务卡就能赚到；\n也可以到设置里「生成演示数据」立即体验"
       else "还没有可兑换的任务卡\n先去户外晒太阳，攒够阳光币就能兑换",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SkyColors.TextSecondary,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(14.dp))
                        Text(
                            "去设置 → 演示模式",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SkyColors.SkyTop,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .clickable { onOpenSettings() }
                                .padding(8.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopSummaryCard(
    state: HomeState,
    onOpenSettings: () -> Unit,
    /** 【性能】是否开启动画 */
    animated: Boolean = false
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(listOf(SkyColors.SkyTop, SkyColors.SkyMid))
            )
            .padding(20.dp)
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "你好，${state.profile.nickname}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.92f)
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "${DateUtils.shortLabel(DateUtils.today())} · ${state.level.title}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.75f)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                        .clickable { onOpenSettings() },
                    contentAlignment = Alignment.Center
                ) {
                    Text("⚙", color = Color.White)
                }
            }

            Spacer(Modifier.height(18.dp))

// 【v5.0】核心指标由 3 格改为 2 格。
          // 原第三格是「待还债」，债务系统下线后已无对应数据。
 // 两格均分（各 weight(1f)）而不是保留空位或压缩宽度：
      // 屏幕宽度被 2 格均分后每格更宽，「今日真实阳光」这类 6 字标签不会换行截断，
      // 顶部也不会因为留下半格空白而显得没对齐。
            Row(
        modifier = Modifier.fillMaxWidth(),
         horizontalArrangement = Arrangement.spacedBy(10.dp)
      ) {
     MetricBox(
      modifier = Modifier.weight(1f),
         label = "今日真实阳光",
          value = "${state.todaySunMinutes}",
     unit = "分钟",
      accent = SkyColors.SunGlow
          )
           MetricBox(
          modifier = Modifier.weight(1f),
    label = "阳光币",
          value = "${state.coinBalance}",
  unit = "枚",
         accent = SkyColors.CoinGold
           )
      }

            // 目标进度条
            Spacer(Modifier.height(14.dp))
            val target = state.profile.dailySunTargetMin.coerceAtLeast(1)
            val progress = (state.todaySunMinutes.toFloat() / target).coerceIn(0f, 1f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "今日目标 $target 分钟",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
                Spacer(Modifier.width(8.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(6.dp)
                        .background(Color.White.copy(alpha = 0.25f), RoundedCornerShape(3.dp))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .height(6.dp)
                            .background(SkyColors.SunGlow, RoundedCornerShape(3.dp))
                    )
                }
Spacer(Modifier.width(8.dp))
            Text(
         "${(progress * 100).toInt()}%",
         style = MaterialTheme.typography.labelSmall,
                color = Color.White
         )
            }
   }
    }
}

@Composable
private fun MetricBox(
    modifier: Modifier,
    label: String,
    value: String,
    unit: String,
    accent: Color
) {
    Column(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp, horizontal = 10.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.8f),
            maxLines = 1
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                value,
                style = MaterialTheme.typography.headlineMedium,
                color = accent,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.width(2.dp))
            Text(
                unit,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White.copy(alpha = 0.75f),
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun SkylineSection(
    state: HomeState,
    /** 【性能】是否开启动画 */
    animated: Boolean = false
) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Card(
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
   SkylineView(
              buildings = state.buildings,
            weather = weatherKind(state.weather?.weatherCode),
        height = 210.dp,
        showSun = state.weather?.weatherCode != "SNOWY",
                        animated = animated
          )
                    // 天气条
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SkyColors.CardWhite)
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
Text(weatherEmoji(state.weather?.weatherCode), style = MaterialTheme.typography.titleLarge)
       Spacer(Modifier.width(10.dp))
          Column(modifier = Modifier.weight(1f)) {
                  Text(
       weatherText(state.weather?.weatherCode),
     style = MaterialTheme.typography.titleMedium,
   color = SkyColors.TextPrimary
       )
Text(
        "日出 ${state.weather?.sunrise ?: "--:--"} · 日落 ${state.weather?.sunset ?: "--:--"}",
    style = MaterialTheme.typography.labelSmall,
       color = SkyColors.TextSecondary
      )
          }
Column(horizontalAlignment = Alignment.End) {
Text(
      "${state.buildings.count { it != "cloud" }} 座建筑",
          style = MaterialTheme.typography.titleMedium,
    color = SkyColors.SkyTop
    )
  Text(
 "每完成一次真户外 +1",
        style = MaterialTheme.typography.labelSmall,
     color = SkyColors.TextSecondary
            )
        }
   }
        }
  }
}
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String = "",
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleLarge,
                color = SkyColors.TextPrimary
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = SkyColors.TextSecondary
                )
            }
        }
        if (actionText != null && onAction != null) {
            Text(
                actionText,
                style = MaterialTheme.typography.bodyMedium,
                color = SkyColors.SkyTop,
                modifier = Modifier
                    .clickable { onAction() }
                    .padding(6.dp)
            )
        }
    }
}

/** 任务卡行（首页与兑换中心共用） */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskCardRow(
    card: TaskCard,
    balance: Int,
    onClick: () -> Unit,
    weatherBonus: Boolean = false
) {
    val affordable = balance >= card.costCoins
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 5.dp)
            // 卡片永远可点：币不够时也给出「还差 N 币」的明确反馈，
            // 而不是禁用点击让用户以为 App 卡死
            .clickable { onClick() },
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SkyColors.CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(
                        categoryColor(card.category),
                        RoundedCornerShape(14.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(taskEmoji(card.iconKey), style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        card.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = SkyColors.TextPrimary
                    )
                    if (weatherBonus) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "双倍",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier
                                .background(SkyColors.RainBlue, RoundedCornerShape(6.dp))
                                .padding(horizontal = 5.dp, vertical = 1.dp)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    card.desc,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SkyColors.TextSecondary,
                    maxLines = 2
                )
Spacer(Modifier.height(6.dp))
           // 【v5.6】改用 FlowRow：标签放不下时整体换到下一行。
   //
       // 【为什么原来是竖排的】原用固定单行 Row，5 个标签横向硬挤，
      // 空间不足时 Compose 会压缩**最后一个**标签的可用宽度，
     //「户外探索」四个汉字被逐字换行，变成一条竖着的文字（见真机截图）。
        // FlowRow 不会压缩单个标签，只会让它完整地移到下一行。
    FlowRow(
    horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
  TagChip("消耗 ${card.costCoins} 阳光币", SkyColors.CoinGold)
   // 【v5.4】同时标出运动时长与光照门槛。原只显示 estMinutes，
            // 用户会以为「10 分钟」就是全部要求，实际还要晒满 sunRewardMinutes。
     TagChip("运动 ${card.estMinutes} 分钟", SkyColors.SkyMid)
      if (card.sunRewardMinutes > 0) {
        TagChip("光照 ${card.sunRewardMinutes} 分钟", SkyColors.SunGold)
      }
   TagChip(categoryLabel(card.category), categoryColor(card.category))
   if (card.needSteps) TagChip("需步数", SkyColors.SuccessGreen)
        }
            }
Spacer(Modifier.width(8.dp))
        // 【v5.6】右侧操作区同样会被横向挤压导致「兑换/币」竖排，
   // 这里给它固定最小宽度，保证「兑换」始终横排显示。
     Column(
           horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.widthIn(min = 40.dp)
          ) {
                Text(
        if (affordable) "兑换" else "差${card.costCoins - balance}",
     style = MaterialTheme.typography.labelSmall,
     color = if (affordable) SkyColors.SkyTop else SkyColors.DebtOrange,
        maxLines = 1
     )
           Text(
     "币",
          style = MaterialTheme.typography.labelSmall,
     color = SkyColors.TextSecondary,
             maxLines = 1
        )
      }
        }
    }
}

@Composable
private fun TagChip(text: String, color: Color) {
    Text(
        text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = Modifier
            .background(color.copy(alpha = 0.12f), RoundedCornerShape(6.dp))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

// ---------------- 辅助映射 ----------------

fun weatherKindOf(code: String?): WeatherKind = when (code) {
    "RAINY" -> WeatherKind.RAINY
    "SNOWY" -> WeatherKind.SNOWY
    "CLOUDY" -> WeatherKind.CLOUDY
    else -> WeatherKind.SUNNY
}

private fun weatherKind(code: String?) = weatherKindOf(code)

fun weatherEmoji(code: String?): String = when (code) {
    "RAINY" -> "🌧"
    "SNOWY" -> "❄️"
    "CLOUDY" -> "☁️"
    else -> "☀️"
}

/**
 * 天气提示文案。
 *
 * 【v5.14】原文案是「今天有雨，适合「雨中漫步」双倍卡」，有两个问题：
 * 1. **卡名错位**：文案写「雨中漫步」，实际卡名叫「听雨漫步」，对不上；
 * 2. **双倍承诺无法兑现**：双倍判定依赖真实天气，而天气已回退为内置示例数据，
 *    运行时恒为「晴」，这条分支实际上永远不会走到。
 * 答辩时被问「下雨双倍怎么结算」会很难回答——机制事实上已不存在。
 *
 * 现在改为纯天气提示，不推荐具体卡名、不承诺倍率。
 * 双倍徽章仍保留在天气限定卡上（那是 `special = "WEATHER"` 的标记，与本文案无关）。
 */
fun weatherText(code: String?): String = when (code) {
    "RAINY" -> "今天有雨，出门记得带伞"
    "SNOWY" -> "今天有雪，去户外走一走看雪"
    "CLOUDY" -> "多云转晴，云层会散开的"
    else -> "晴，适合出门晒太阳攒阳光"
}

fun categoryLabel(category: String): String = when (category) {
    "MICRO" -> "微运动"
    "OUTDOOR" -> "户外探索"
    "RELAX" -> "放松"
    // 【v5.1】「SEASON -> 节气限定」分支已删除：
// 节气卡不再出现在任何界面，保留它只是无人会走到的死分支。
    else -> category
}

fun categoryColor(category: String): Color = when (category) {
    "MICRO" -> SkyColors.SunGlow
    "OUTDOOR" -> SkyColors.SuccessGreen
    "RELAX" -> Color(0xFFB79CED)
    "SEASON" -> SkyColors.DebtOrange
    else -> SkyColors.SkyMid
}

fun taskEmoji(key: String): String = when (key) {
// 用户自己创建的卡片
    "custom" -> "⭐"
    "stretch" -> "🙆"
    "rope" -> "🪢"
    "eye" -> "👀"
    "squat" -> "🏋️"
    "jump" -> "🤸"
    "stand" -> "🧍"
    "walk" -> "🚶"
    "run" -> "🏃"
    "ball" -> "🏀"
    "sunbath" -> "🧴"
    "balcony" -> "🌇"
    "bike" -> "🚲"
    "park" -> "🌳"
    "cloud_watch" -> "☁️"
    "rain" -> "🌧"
    "breathe" -> "🫧"
    "dusk" -> "🌆"
    "sun_gaze" -> "🌞"
    else -> "✨"
}