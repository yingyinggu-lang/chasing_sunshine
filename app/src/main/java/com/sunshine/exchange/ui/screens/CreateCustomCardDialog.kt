package com.sunshine.exchange.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sunshine.exchange.ui.theme.SkyColors

/**
 * 创建自定义任务卡弹窗。
 *
 * 对应需求：「用户做了其他运动」——内置42 张卡不可能覆盖所有人的运动方式，
 * 因此允许用户自己创建，但验收规则与内置卡完全一致（仍需三重传感器通过）。
 */
@Composable
fun CreateCustomCardDialog(
    balance: Int,
    onDismiss: () -> Unit,
onConfirm: (
    name: String,
        desc: String,
        category: String,
      difficulty: Int,
        costCoins: Int,
    estMinutes: Int,
        sunRewardMinutes: Int,
  needSteps: Boolean
    ) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var desc by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("OUTDOOR") }
    var difficulty by remember { mutableIntStateOf(2) }
    var costCoins by remember { mutableFloatStateOf(5f) }
    var estMinutes by remember { mutableIntStateOf(20) }
    // 【v5.7】光照门槛独立可调，默认 20 分钟。
    // 原先它被写死为 sunRewardMinutes = estMinutes（见 SunshineRepository.createCustomCard），
    // 用户无法单独调整验收强度——现在拆成两个独立设置项。
    var sunRewardMinutes by remember { mutableIntStateOf(20) }
    var needSteps by remember { mutableStateOf(true) }

    val categories = listOf(
        "OUTDOOR" to "户外探索",
        "MICRO" to "微运动",
        "RELAX" to "放松"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("创建自定义任务卡") },
text = {
    // 【v5.8】必须加 heightIn 上限，否则 verticalScroll 不会生效。
            //
        // 【原因】AlertDialog 的 text 槽位不给高度约束，Column 会被内容撑到
       // 远超弹窗容量，超出部分直接被裁剪（不是溢出显示），
        // 导致底部「需要步数验证」开关等控件完全看不见。
    // 先限高再滚动，溢出内容才可被手势触达。
      Column(
 modifier = Modifier
 .heightIn(max = 420.dp)
    .verticalScroll(rememberScrollState())
        ) {
Text(
      // 【v5.8】数字修正：原写「42 种」，但那包含 24 张节气卡，
     // 而节气卡已在 v5.1 下线，用户实际能看到只有 18 张，说 42 会让人困惑。
          "内置卡覆盖 18 种运动，但你的运动方式可能不在其中。" +
    "创建一个专属卡片——验收规则与内置卡完全一致，" +
      "仍需真实户外与传感器验证通过。",
      style = MaterialTheme.typography.labelSmall,
        color = SkyColors.TextSecondary,
        modifier = Modifier.fillMaxWidth()
          )

                Spacer(Modifier.height(14.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= 12) name = it },
                    label = { Text("运动名称") },
                    placeholder = { Text("如：打羽毛球") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))

                OutlinedTextField(
                    value = desc,
                    onValueChange = { if (it.length <= 30) desc = it },
                    label = { Text("简单描述（可选）") },
                    placeholder = { Text("和谁、做什么、大概多久") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(14.dp))

                // 分类
                Text("分类", style = MaterialTheme.typography.bodyMedium, color = SkyColors.TextPrimary)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    categories.forEach { (key, label) ->
                        FilterChip(
                            selected = category == key,
                            onClick = { category = key },
                            label = { Text(label, style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SkyColors.SkyBottom,
                                selectedLabelColor = SkyColors.TextPrimary
                            )
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                // 难度
                Text(
                    "难度：$difficulty 级",
                    style = MaterialTheme.typography.bodyMedium,
                    color = SkyColors.TextPrimary
                )
                Slider(
                    value = difficulty.toFloat(),
                    onValueChange = { difficulty = it.toInt() },
                    valueRange = 1f..3f,
                    steps = 1
                )

                // 所需阳光币
                Text(
                    "所需阳光币：${costCoins.toInt()} 枚（当前可用 $balance）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (costCoins > balance) SkyColors.DebtOrange else SkyColors.TextPrimary
                )
                Slider(
                    value = costCoins,
                    onValueChange = { costCoins = it },
                    valueRange = 0f..12f,
                    steps = 11
                )

// 运动时长
   Text(
 "运动时长：$estMinutes 分钟",
    style = MaterialTheme.typography.bodyMedium,
      color = SkyColors.TextPrimary
      )
   Slider(
      value = estMinutes.toFloat(),
           onValueChange = { estMinutes = (it / 5).toInt() * 5 },
       valueRange = 10f..120f,
  steps = 21
       )

  Spacer(Modifier.height(6.dp))

            // 【v5.7】光照门槛独立设置（原先固定等于运动时长）
    Text(
   "验收光照门槛：$sunRewardMinutes 分钟",
     style = MaterialTheme.typography.bodyMedium,
  color = SkyColors.TextPrimary
            )
            Slider(
   value = sunRewardMinutes.toFloat(),
       onValueChange = { sunRewardMinutes = (it / 5).toInt() * 5 },
     valueRange = 5f..90f,
    steps = 16
            )
// 【v5.8】原来这里是一段 if/else 长句 + ⚠️ emoji 混排，
  // 在 AlertDialog 有限宽度下长句被压成极窄一列逐字换行（真机反馈「无法正常显示」）。
            // 现在拆成 Row：emoji 独立不参与换行，正文占满剩余宽度。
            val overLimit = sunRewardMinutes > estMinutes
Row(
        verticalAlignment = Alignment.Top,
     modifier = Modifier.fillMaxWidth()
         ) {
     Text(
      if (overLimit) "⚠️" else "☀️",
      style = MaterialTheme.typography.labelSmall
         )
        Spacer(Modifier.width(4.dp))
            Text(
          text = if (overLimit)
    "光照门槛高于运动时长：需要在户外照满 $sunRewardMinutes 分钟才能通过验收"
           else
   "需在户外被阳光照满 $sunRewardMinutes 分钟，才能通过验收并结算阳光",
            style = MaterialTheme.typography.labelSmall,
          color = if (overLimit) SkyColors.DebtOrange else SkyColors.TextSecondary,
            modifier = Modifier.weight(1f)
                )
        }

                Spacer(Modifier.height(6.dp))

                // 步数验收
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("需要步数验证", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "开启后需走动累计 ≥60 步才算通过",
                            style = MaterialTheme.typography.labelSmall,
                            color = SkyColors.TextSecondary
                        )
                    }
                    Switch(checked = needSteps, onCheckedChange = { needSteps = it })
                }

                Spacer(Modifier.height(8.dp))

Text(
      "💡 建议：运动类开启步数验证，伸展/拉伸类可关闭",
           style = MaterialTheme.typography.labelSmall,
   color = SkyColors.TextSecondary,
      modifier = Modifier.fillMaxWidth()
         )
            }
        },
        confirmButton = {
            Button(
                onClick = {
onConfirm(
   name.trim(),
   desc.trim(),
      category,
      difficulty,
   costCoins.toInt(),
       estMinutes,
               sunRewardMinutes,
           needSteps
      )
                },
                enabled = name.isNotBlank() && costCoins <= balance,
                colors = ButtonDefaults.buttonColors(containerColor = SkyColors.SkyTop)
            ) {
                Text("创建并兑换")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 我的自定义卡片列表：展示与删除。
 * 内置卡片不可删除，这是用户「自己创建的运动」的管理入口。
 */
@Composable
fun MyCustomCardsSection(
    customCards: List<com.sunshine.exchange.data.TaskCard>,
    onDelete: (Int) -> Unit
) {
    if (customCards.isEmpty()) return

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SkyColors.CardWhite),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "我的运动（${customCards.size}）",
                style = MaterialTheme.typography.titleMedium,
                color = SkyColors.TextPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "这些是你自己创建的卡片，可删除；内置卡片不可删除",
                style = MaterialTheme.typography.labelSmall,
                color = SkyColors.TextSecondary
            )

            Spacer(Modifier.height(12.dp))

            customCards.forEach { card ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box2()
                    Spacer(Modifier.width(10.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            card.name,
                            style = MaterialTheme.typography.bodyMedium,
                            color = SkyColors.TextPrimary
                        )
Text(
   // 【v5.4】与内置卡保持同一套措辞：明确区分「运动时长」与「光照门槛」
     "${categoryLabel(card.category)} · 运动 ${card.estMinutes} 分钟 · " +
       "光照 ${card.sunRewardMinutes} 分钟 · ${card.costCoins} 币 · 难度 ${card.difficulty}",
 style = MaterialTheme.typography.labelSmall,
     color = SkyColors.TextSecondary
      )
                    }
                    TextButton(onClick = { onDelete(card.id) }) {
                        Text("删除", color = SkyColors.DebtOrange)
                    }
                }
            }
        }
    }
}

@Composable
private fun Box2() {
    Spacer(
        modifier = Modifier
            .size(32.dp)
            .background(categoryColor("OUTDOOR"), RoundedCornerShape(10.dp))
    )
}