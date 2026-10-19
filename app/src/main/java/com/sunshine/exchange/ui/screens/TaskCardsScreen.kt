package com.sunshine.exchange.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sunshine.exchange.data.TaskCard
import com.sunshine.exchange.ui.theme.SkyColors

/**
 * ② 任务卡（兑换中心）。
 * 三类：微运动 / 户外探索 / 放松。
 * 【v5.1】原第四类「节气限定」已下线。
 */
@Composable
fun TaskCardsScreen(
    allCards: List<TaskCard>,
    balance: Int,
    isRainy: Boolean,
    onExchange: (TaskCard) -> Unit,
    /** 打开「创建自定义任务卡」弹窗 */
    onCreateCustom: () -> Unit
) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("微运动", "户外探索", "放松")
    val categories = listOf("MICRO", "OUTDOOR", "RELAX")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SkyColors.SkyLight)
    ) {
        // 顶部余额条
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.horizontalGradient(listOf(SkyColors.SkyTop, SkyColors.SkyMid)))
                .padding(20.dp)
        ) {
            Column {
                Text(
                    "任务卡兑换中心",
                    style = MaterialTheme.typography.headlineMedium,
                    color = Color.White
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("☀️ ", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "$balance",
                        style = MaterialTheme.typography.headlineMedium,
                        color = SkyColors.SunGlow
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "  枚阳光币可用",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "兑换后到「进行中」开始验收，完成真户外才算数",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.75f)
                )
            }
        }

  TabRow(
            selectedTabIndex = tab,
            containerColor = SkyColors.CardWhite,
      contentColor = SkyColors.SkyTop
        ) {
       tabs.forEachIndexed { index, title ->
    Tab(
       selected = tab == index,
            onClick = { tab = index },
           text = { Text(title, style = MaterialTheme.typography.bodyMedium) }
       )
   }
        }

        // =====【v4.0】阳光币从哪来：把经济系统讲清楚 =====
  // 【为什么必须显式说明】旧版把「每 30 分钟屏幕 = +1 币」藏在代码里，
        // 界面上完全看不出币的来源，用户只能靠猜，
   // 结果「做运动要先花币、做完还亏币」显得毫无道理。
        // 现在真户外是唯一印钞机，必须让用户一眼看懂闭环。
        // 【v5.0】原「周末玩手机预支欠债」一行已删除：债务系统整体下线，
  // 留下这一行会让用户以为仍有负债机制存在。
        Card(
      shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = SkyColors.SunGlow.copy(alpha = 0.16f)),
            modifier = Modifier
             .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
            "阳光币怎么来？",
   style = MaterialTheme.typography.titleSmall,
         color = SkyColors.TextPrimary
     )
        Spacer(Modifier.height(6.dp))
Text(
      "☀️ 晒到真实阳光：每 5 分钟 +2 币（唯一来源）\n" +
                "✅ 每完成一张卡都净赚，晒得越久赚得越多",
     style = MaterialTheme.typography.labelSmall,
     color = SkyColors.TextSecondary
     )
       }
        }

        // 【v5.1】不再需要节气回退分支：SEASON 类卡已无对应 Tab，
        // 统一排除，避免任何位置漏出节气卡。
        val filtered = allCards.filter { it.category == categories[tab] }

        // 任务卡来自数据库异步流，首帧为空属「加载中」，需与「确实没有卡」区分开，
        // 否则用户会以为这一类任务是空的
        if (allCards.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    androidx.compose.material3.CircularProgressIndicator(
                        color = SkyColors.SkyTop
                    )
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "正在加载任务卡…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SkyColors.TextSecondary
                    )
                }
            }
        } else if (filtered.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("🌙", style = MaterialTheme.typography.headlineLarge)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "这一类暂无任务卡\n试试切到其他分类看看",
                        style = MaterialTheme.typography.bodyMedium,
                        color = SkyColors.TextSecondary,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
LazyColumn(
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
          top = 10.dp, bottom = 16.dp
        ),
    verticalArrangement = Arrangement.spacedBy(2.dp)
      ) {
                items(filtered, key = { it.id }) { card ->
TaskCardRow(
        card = card,
              balance = balance,
            weatherBonus = card.special == "WEATHER" && isRainy,
   onClick = { onExchange(card) }
     )
         }

     // ---- 创建自定义任务卡入口 ----
                item {
       Spacer(Modifier.height(14.dp))
            Card(
    shape = RoundedCornerShape(18.dp),
            colors = CardDefaults.cardColors(containerColor = SkyColors.SkyLight),
       modifier = Modifier
  .fillMaxWidth()
      .padding(horizontal = 16.dp)
        .clickable { onCreateCustom() }
    ) {
   Row(
    modifier = Modifier.padding(16.dp),
   verticalAlignment = Alignment.CenterVertically
      ) {
        Text("➕", style = MaterialTheme.typography.headlineMedium)
     Spacer(Modifier.width(12.dp))
       Column(modifier = Modifier.weight(1f)) {
          Text(
        "我的运动不在列表里？",
            style = MaterialTheme.typography.titleMedium,
            color = SkyColors.TextPrimary
     )
   Spacer(Modifier.height(2.dp))
   Text(
     "创建一张专属任务卡，如「打羽毛球」「遛狗」",
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
}
