package com.sunshine.exchange.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.sunshine.exchange.core.DateUtils
import com.sunshine.exchange.data.BehaviorReport
import com.sunshine.exchange.data.ScreenRecord
import com.sunshine.exchange.data.WeeklySummary
import com.sunshine.exchange.ui.theme.SkyColors
import kotlin.math.abs

/**
 * ⑤ 报告（数据）。
 * 阳光周报（本周阳光积累 + 进度条 + 小结）
 * 行为改变报告（使用前 vs 使用中 折线对比，可直接用于比赛数据答辩）。
 *
 * 【v5.0】屏幕时长统计与债务系统已移除，本页只保留「真实阳光」单一维度：
 * 没有屏幕时长可对比，也就不存在「转化率」和「清还率」这类需要分母的指标。
 */
@Composable
fun ReportScreen(
    weekly: WeeklySummary?,
    screenRecords: List<ScreenRecord>,
    behaviorReport: BehaviorReport?,
    onGenerateReport: () -> Unit,
    /** 生成 14 天演示数据，用于在空状态时给用户一个可立即看到效果的出口 */
    onGenerateDemo: () -> Unit = {}
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SkyColors.SkyLight),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "报告",
                style = MaterialTheme.typography.headlineMedium,
                color = SkyColors.TextPrimary
            )
        }

// ---- 本周阳光积累 ----
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
      colors = CardDefaults.cardColors(containerColor = SkyColors.CardWhite),
        modifier = Modifier.fillMaxWidth()
         ) {
                Column(modifier = Modifier.padding(18.dp)) {
     Text(
             "阳光周报",
    style = MaterialTheme.typography.titleLarge,
         color = SkyColors.TextPrimary
    )
             Text(
              weekly?.let { "${DateUtils.shortLabel(it.weekStart)} 起 · ${it.activeDays} 天有阳光" } ?: "本周暂无数据",
     style = MaterialTheme.typography.labelSmall,
       color = SkyColors.TextSecondary
       )
    Spacer(Modifier.height(16.dp))

       if (weekly != null && weekly.activeDays > 0) {
    // 【v5.0】只剩阳光一条进度条：
              // 原先这里是「屏幕 vs 阳光」双条对比，屏幕时长统计移除后失去了对照对象，
       // 单条进度反而更直观——用户只需回答一个问题：这周晒够了没有。
     SunProgressBar(
      sunMinutes = weekly.sunMinutes,
  activeDays = weekly.activeDays
   )
    Spacer(Modifier.height(14.dp))

   // 本周小结
      Box(
          modifier = Modifier
           .fillMaxWidth()
      .background(SkyColors.SunGlow.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
          .padding(14.dp)
          ) {
         Column {
           Text(
    "本周小结",
         style = MaterialTheme.typography.titleMedium,
     color = SkyColors.TextPrimary
    )
        Spacer(Modifier.height(4.dp))
         Text(
  weekly.conversionCopy,
      style = MaterialTheme.typography.bodyMedium,
   color = SkyColors.TextSecondary
            )
        }
        }
   } else {
            // 空状态必须给出下一步出口，否则用户会以为报告坏了
     Column(
     modifier = Modifier.fillMaxWidth(),
     horizontalAlignment = Alignment.CenterHorizontally
       ) {
  Text(
   "本周还没有记录",
             style = MaterialTheme.typography.bodyMedium,
      color = SkyColors.TextSecondary
           )
        Spacer(Modifier.height(4.dp))
        Text(
 "屏幕时长统计已移除，现在只记录真实阳光的积累\n完成一张真户外任务卡即可点亮今天",
style = MaterialTheme.typography.labelSmall,
         color = SkyColors.TextSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
       )
    Spacer(Modifier.height(14.dp))
      Button(
      onClick = onGenerateDemo,
      shape = RoundedCornerShape(14.dp),
   colors = ButtonDefaults.buttonColors(containerColor = SkyColors.SkyTop)
    ) {
        Text("一键生成 14 天演示数据")
       }
      }
           }
      }
         }
        }

        // ---- 14 天趋势折线 ----
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = SkyColors.CardWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        "近 14 天走势",
                        style = MaterialTheme.typography.titleLarge,
                        color = SkyColors.TextPrimary
                    )
                    Spacer(Modifier.height(4.dp))
Text(
    "每日真实阳光分钟数的变化趋势",
style = MaterialTheme.typography.labelSmall,
      color = SkyColors.TextSecondary
      )
                    Spacer(Modifier.height(14.dp))
                    // screenRecords 是按日期倒序（最新在前），需要取最近 14 天并反转为时间正序，
                    // 否则折线图的 X 轴会从新到旧倒着画
                    TrendChart(
                        records = screenRecords.take(14).reversed(),
                        onGenerateDemo = onGenerateReport
                    )
                }
            }
        }

        // ---- 行为改变报告 ----
        item {
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = SkyColors.CardWhite),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        "行为改变报告",
                        style = MaterialTheme.typography.titleLarge,
                        color = SkyColors.TextPrimary
                    )
                    Text(
                        "使用前（最早7天）vs 使用中（最近7天）",
                        style = MaterialTheme.typography.labelSmall,
                        color = SkyColors.TextSecondary
                    )
                    Spacer(Modifier.height(14.dp))

                    if (behaviorReport == null) {
                        Text(
                            "还没有生成报告",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SkyColors.TextSecondary
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = onGenerateReport,
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SkyColors.SkyTop)
                        ) {
                            Text("生成行为改变报告")
                        }
} else {
                        CompareGrid(
                          sunBefore = behaviorReport.avgSunBefore,
        sunAfter = behaviorReport.avgSunAfter
   )
    Spacer(Modifier.height(14.dp))
    Text(
             "结论：真实阳光日均${if (behaviorReport.avgSunAfter >= behaviorReport.avgSunBefore) "增加" else "减少"} " +
       "${abs(behaviorReport.avgSunAfter - behaviorReport.avgSunBefore).toInt()} 分钟，" +
        "屏幕时长统计与结算机制已移除，本报告只衡量阳光积累。",
       style = MaterialTheme.typography.bodyMedium,
               color = SkyColors.SuccessGreen
      )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "统计区间：${behaviorReport.periodStart} ~ ${behaviorReport.periodEnd}",
                            style = MaterialTheme.typography.labelSmall,
                            color = SkyColors.TextSecondary
                        )
                    }
                }
            }
        }
    }
}

/**
 * 本周阳光进度条（单一维度）。
 *
 * 【为什么保留进度条】屏幕时长统计移除后，这里不再是「两条对比」，
 * 而是一条「这周晒够了没有」的达成度条。进度上限取 300 分钟（5 小时），
 * 与 [EconomyRules.levelFor] 的最高档「阳光城主」（600 分钟/7 天 ≈ 86 分钟/天）相比留足弹性，
 * 绝大多数认真使用的用户都能看到进度条走满，避免进度条长期贴底失去激励感。
 */
@Composable
private fun SunProgressBar(sunMinutes: Int, activeDays: Int) {
    // 一周 7 天；进度基准 300 分钟。两者都做下限保护，避免除零。
    val maxMinutes = 300
    val progress = (sunMinutes.toFloat() / maxMinutes).coerceIn(0f, 1f)

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
      "本周累计真实阳光",
    style = MaterialTheme.typography.bodyMedium,
          color = SkyColors.TextSecondary,
       modifier = Modifier.weight(1f)
       )
       Text(
                "$sunMinutes 分钟",
    style = MaterialTheme.typography.titleLarge,
       color = SkyColors.SuccessGreen,
     fontWeight = FontWeight.Bold
      )
        }
        Spacer(Modifier.height(4.dp))
        Text(
    "7 天中有阳光的 $activeDays 天",
          style = MaterialTheme.typography.labelSmall,
     color = SkyColors.TextSecondary
        )
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
    .fillMaxWidth()
         .height(12.dp)
            .background(SkyColors.DividerLight, RoundedCornerShape(6.dp))
     ) {
            Box(
                modifier = Modifier
   .fillMaxWidth(progress.coerceAtLeast(0.02f))
      .height(12.dp)
             .background(SkyColors.SuccessGreen, RoundedCornerShape(6.dp))
    )
        }
        Spacer(Modifier.height(6.dp))
    Text(
   "基准 ${maxMinutes} 分钟／周（平均每天 ${maxMinutes / 7} 分钟）",
   style = MaterialTheme.typography.labelSmall,
            color = SkyColors.TextSecondary
        )
    }
}

/** 真实阳光折线图（【v5.0】屏幕时长折线已移除，只保留单一维度） */
@Composable
private fun TrendChart(records: List<ScreenRecord>, onGenerateDemo: () -> Unit) {
    if (records.size < 2) {
        Text(
            "至少需要 2 天数据才能画出走势",
            style = MaterialTheme.typography.bodyMedium,
            color = SkyColors.TextSecondary
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onGenerateDemo,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SkyColors.SkyTop)
        ) {
            Text("生成演示数据看效果")
        }
        return
    }

    val maxVal = maxOf(
  records.maxOfOrNull { it.sunMinutes } ?: 1,
        1
    ).toFloat()

    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
    ) {
        val w = size.width
        val h = size.height
        val stepX = w / (records.size - 1)

        fun yFor(v: Int): Float = h - (v / maxVal) * (h - 12f) - 6f

        // 网格
        for (i in 0..3) {
            val gy = h * i / 3
            drawLine(
                color = SkyColors.DividerLight,
                start = Offset(0f, gy),
                end = Offset(w, gy),
                strokeWidth = 1f
            )
        }

// 真实阳光（绿）
        val sunPath = Path()
        records.forEachIndexed { i, r ->
            val x = i * stepX
        val y = yFor(r.sunMinutes)
            if (i == 0) sunPath.moveTo(x, y) else sunPath.lineTo(x, y)
        }
        drawPath(sunPath, color = SkyColors.SuccessGreen, style = Stroke(width = 3.5f))

        // 数据点
        records.forEachIndexed { i, r ->
      drawCircle(SkyColors.SuccessGreen, radius = 3.5f, center = Offset(i * stepX, yFor(r.sunMinutes)))
    }
    }

    Spacer(Modifier.height(8.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            DateUtils.shortLabel(records.first().date),
            style = MaterialTheme.typography.labelSmall,
            color = SkyColors.TextSecondary
        )
Text(
     "🟢 真实阳光",
            style = MaterialTheme.typography.labelSmall,
            color = SkyColors.TextSecondary
  )
        Text(
            DateUtils.shortLabel(records.last().date),
            style = MaterialTheme.typography.labelSmall,
            color = SkyColors.TextSecondary
        )
    }
}

/**
 * 行为改变对比网格。
 *
 * 【v5.0】原先展示「日均屏幕时长」与「日均真实阳光」两行对比，
 * 屏幕时长统计移除后只剩阳光一行。保留 goodWhenDown 参数是为了让
 * CompareRow 在「阳光变大 = 改善」这一唯一语义下仍可复用同一套渲染逻辑。
 */
@Composable
private fun CompareGrid(
    sunBefore: Double,
    sunAfter: Double
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        CompareRow(
   title = "日均真实阳光",
 before = sunBefore,
        after = sunAfter,
      goodWhenDown = false,
            unit = "分钟"
     )
    }
}

@Composable
private fun CompareRow(
    title: String,
    before: Double,
    after: Double,
    goodWhenDown: Boolean,
    unit: String
) {
    val delta = after - before
    val improved = if (goodWhenDown) delta < 0 else delta > 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SkyColors.SkyLight, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyMedium,
            color = SkyColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        Column(horizontalAlignment = Alignment.End) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "${before.toInt()}",
                    style = MaterialTheme.typography.titleMedium,
                    color = SkyColors.TextSecondary
                )
                Text(" → ", color = SkyColors.TextSecondary)
                Text(
                    "${after.toInt()}",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (improved) SkyColors.SuccessGreen else SkyColors.DebtOrange,
                    fontWeight = FontWeight.Bold
                )
                Text(" $unit", style = MaterialTheme.typography.labelSmall, color = SkyColors.TextSecondary)
            }
            Text(
                if (improved) "▼ 改善 ${abs(delta).toInt()} $unit" else "▲ 变化 ${abs(delta).toInt()} $unit",
                style = MaterialTheme.typography.labelSmall,
                color = if (improved) SkyColors.SuccessGreen else SkyColors.DebtOrange
            )
        }
    }
}