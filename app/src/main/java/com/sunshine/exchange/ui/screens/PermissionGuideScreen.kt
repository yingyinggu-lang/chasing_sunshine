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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sunshine.exchange.ui.theme.SkyColors

/**
 * 权限中心 / 首次引导（3 屏，可跳过）。
 * 对应需求文档 5. P0-1「首次引导 / 权限中心：敏感权限先解释后请求」。
 */
@Composable
fun PermissionGuideScreen(
    locationGranted: Boolean,
    notificationGranted: Boolean,
    onRequestLocation: () -> Unit,
    onRequestNotification: () -> Unit,
    onFinish: () -> Unit,
    onSkip: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(SkyColors.SkyTop, SkyColors.SkyMid, SkyColors.SkyBottom)
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // 顶部标题
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Spacer(Modifier.height(28.dp))
                Text("☀️", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(10.dp))
                Text(
                    "追光",
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "用真实阳光，换一张会长大的天空之城",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.9f),
                    textAlign = TextAlign.Center
                )
            }

      // 权限说明
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                PermissionCard(
                    emoji = "📍",
                    title = "定位权限",
                    why = "用来判断你是否真的走到了室外。拒绝也不影响使用，会降级为「光照 + 步数」双路验收",
                    granted = locationGranted,
                    actionLabel = "去开启",
                    onAction = onRequestLocation
                )
                PermissionCard(
                    emoji = "🔔",
                    title = "通知权限",
                    why = "连续用屏幕 60 分钟时，给你一条温柔提醒（可随时在设置里关闭）",
                    granted = notificationGranted,
                    actionLabel = "去开启",
                    onAction = onRequestNotification
                )
            }

            // 底部操作
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = onFinish,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(26.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SkyColors.SunGold,
                        contentColor = Color.White
                    )
                ) {
                    Text("进入天空之城", style = MaterialTheme.typography.titleMedium)
                }
                Spacer(Modifier.height(6.dp))
                TextButton(onClick = onSkip) {
                    Text(
                        "先跳过，之后在设置里开启",
                        color = Color.White.copy(alpha = 0.85f)
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionCard(
    emoji: String,
    title: String,
    why: String,
    granted: Boolean,
    actionLabel: String,
    onAction: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.96f), RoundedCornerShape(20.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Text(emoji, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = SkyColors.TextPrimary
                    )
                    if (granted) {
                        Spacer(Modifier.width(6.dp))
                        Text("已开启 ✓", style = MaterialTheme.typography.labelSmall, color = SkyColors.SuccessGreen)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    why,
                    style = MaterialTheme.typography.bodyMedium,
                    color = SkyColors.TextSecondary
                )
                if (!granted) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = onAction,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(actionLabel, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}