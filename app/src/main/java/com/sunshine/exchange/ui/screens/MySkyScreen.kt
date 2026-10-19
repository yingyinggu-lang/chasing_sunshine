package com.sunshine.exchange.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sunshine.exchange.core.DateUtils
import com.sunshine.exchange.data.Badge
import com.sunshine.exchange.data.SunCoinLedger
import com.sunshine.exchange.ui.components.SkylineView
import com.sunshine.exchange.ui.theme.SkyColors

/**
 * ④ 我的天空（成就）。
 * 天空之城全景 + 阳光账本流水 + 徽章墙。
 * 【v5.1】二十四节气章区块已下线，seasons 参数随之移除。
 */
@Composable
fun MySkyScreen(
    buildings: List<String>,
    weatherCode: String?,
    ledger: List<SunCoinLedger>,
    badges: List<Badge>,
    levelTitle: String
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SkyColors.SkyLight),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            // ---- 天空之城全景 ----
            Card(
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Column {
                    SkylineView(
                        buildings = buildings,
                        weather = weatherKindOf(weatherCode),
                        height = 200.dp
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(SkyColors.CardWhite)
                            .padding(16.dp)
                    ) {
                        Text(
                            "我的天空之城",
                            style = MaterialTheme.typography.titleLarge,
                            color = SkyColors.TextPrimary
                        )
                        Spacer(Modifier.height(6.dp))
                        Row {
                            MiniStat("建筑", "${buildings.count { it != "cloud" }} 座")
                            Spacer(Modifier.width(20.dp))
                            MiniStat("云朵", "${buildings.count { it == "cloud" }} 朵")
                            Spacer(Modifier.width(20.dp))
                            MiniStat("称号", levelTitle)
                        }
                    }
                }
            }
        }

// ---- 阳光账本 ----
        item {
            SectionHeader(
                title = "阳光账本",
    subtitle = "记录每一笔阳光的来源与去向"
            )
        }

        if (ledger.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = SkyColors.CardWhite),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "账本还是空的",
                            style = MaterialTheme.typography.titleMedium,
                            color = SkyColors.TextPrimary
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "阳光币只能靠真户外赚：\n每晒 5 分钟 +2 币，完成任务卡还净赚\n也可到「设置 → 演示模式」生成演示数据",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SkyColors.TextSecondary,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } else {
            items(ledger.take(15), key = { it.id }) { entry ->
                LedgerRow(entry)
            }
        }

        // ---- 徽章墙 ----
        item {
            SectionHeader(
                title = "徽章墙",
                subtitle = "已解锁 ${badges.count { it.unlockedAt != null }} / ${badges.size}"
            )
        }
        item {
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SkyColors.CardWhite),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    // 徽章表为空时会渲染出一张纯白空卡，用户会以为界面坏了。
                    // 必须显式处理空集合，给出说明与下一步出口。
                    if (badges.isEmpty()) {
                        Text(
                            "徽章数据尚未初始化\n请到「设置」中点击「生成演示数据」",
                            style = MaterialTheme.typography.bodyMedium,
                            color = SkyColors.TextSecondary,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 20.dp)
                        )
                    } else {
                        badges.chunked(3).forEach { row ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                row.forEach { badge -> BadgeCell(badge) }
                            }
                            Spacer(Modifier.height(10.dp))
                        }
                    }
                }
            }
        }

    }
}

@Composable
private fun MiniStat(label: String, value: String) {
    Column {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = SkyColors.TextSecondary
        )
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = SkyColors.SkyTop
        )
    }
}

@Composable
private fun LedgerRow(entry: SunCoinLedger) {
    val isIncome = entry.amount >= 0
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 3.dp)
            .background(SkyColors.CardWhite, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(
                    ledgerColor(entry.type).copy(alpha = 0.15f),
                    RoundedCornerShape(10.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(ledgerEmoji(entry.type))
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.desc,
                style = MaterialTheme.typography.bodyMedium,
                color = SkyColors.TextPrimary
            )
            Text(
                "${DateUtils.shortLabel(entry.date)} · 余额 ${entry.balance}",
                style = MaterialTheme.typography.labelSmall,
                color = SkyColors.TextSecondary
            )
        }
        Text(
            if (isIncome) "+${entry.amount}" else "${entry.amount}",
            style = MaterialTheme.typography.titleMedium,
            color = if (isIncome) SkyColors.SuccessGreen else SkyColors.DebtOrange
        )
    }
}

@Composable
private fun BadgeCell(badge: Badge) {
    val unlocked = badge.unlockedAt != null
    Column(
        modifier = Modifier.width(96.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(
                    if (unlocked) SkyColors.SunGlow.copy(alpha = 0.22f) else SkyColors.DividerLight,
                    RoundedCornerShape(26.dp)
                ),
            contentAlignment = Alignment.Center
        ) {
            Text(
                if (unlocked) badgeEmoji(badge.iconKey) else "🔒",
                style = MaterialTheme.typography.titleMedium
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            badge.name,
            style = MaterialTheme.typography.labelSmall,
            color = if (unlocked) SkyColors.TextPrimary else SkyColors.TextSecondary,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

private fun ledgerColor(type: String): Color = when (type) {
    "EARN" -> SkyColors.SunGold
    "LEND" -> SkyColors.DebtOrange
    "REPAY" -> SkyColors.SuccessGreen
    "SPEND" -> SkyColors.SkyTop
    else -> SkyColors.TextSecondary
}

private fun ledgerEmoji(type: String): String = when (type) {
    "EARN" -> "☀️"
    "LEND" -> "⏳"
    "REPAY" -> "🌿"
    "SPEND" -> "🎫"
    else -> "📒"
}

private fun badgeEmoji(key: String): String = when (key) {
    "sunrise" -> "🌅"
    "tower" -> "🏙"
  "check" -> "✅"
    "sun" -> "☀️"
    "sun_star" -> "⭐"
    "dawn" -> "🌄"
    "flame" -> "🔥"
    "leaf" -> "🍃"
    "book" -> "📖"
    "castle" -> "🏰"
    else -> "🏅"
}
