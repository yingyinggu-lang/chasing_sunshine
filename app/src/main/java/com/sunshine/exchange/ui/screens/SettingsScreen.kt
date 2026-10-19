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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sunshine.exchange.data.UserProfile
import com.sunshine.exchange.ui.theme.SkyColors

/**
 * 设置页：目标、提醒开关、权限管理、演示模式、关于。
 */
@Composable
fun SettingsScreen(
    profile: UserProfile,
    locationGranted: Boolean,
    contentPadding: androidx.compose.foundation.layout.PaddingValues = androidx.compose.foundation.layout.PaddingValues(0.dp),
    onSave: (UserProfile) -> Unit,
    onRequestLocation: () -> Unit,
    onGenerateDemo: () -> Unit,
    onClearDemo: () -> Unit = {},
    /**
     * 【v5.11】notificationGranted 参数已移除。
     * 它原本只用于「连续使用温柔提醒」卡片的提示文案，该卡片已删除；
     * 通知权限的申请入口在启动引导页，设置页不再展示也不再申请。
     */
    /** 身体活动识别权限：缺失时步数传感器不返回数据 */
    activityGranted: Boolean = false,
    /**
     * 【v5.15】当前阳光币余额，用于在「初始化阳光币」设置项里提示
     *「已有余额时修改不会补币」，避免用户以为改完立刻到账。
     */
    currentBalance: Int = 0,
    /**
     * 【v5.2】单独申请身体活动识别权限。
     *
     * 【为什么必须独立】原来第三行的 onClick 接的是 onRequestLocation，
     * 点它会弹「定位 + 身体活动」的合并弹窗，用户无法分辨究竟哪一项生效。
     * 而 ACTIVITY_RECOGNITION 与定位本就是两套独立权限，
     * 接回requestActivityPermission() 才能做到点哪申请哪。
     */
    onRequestActivity: () -> Unit = onRequestLocation,
    onBack: () -> Unit
) {
    // 必须用 profile.xxx 作为 remember 的 key：
// profile 来自数据库异步流，首帧还是默认值。若不带 key，
// 真实档案到达时状态不会重新初始化，用户点「保存」就会把真实设置覆盖成默认值。
    var target by remember(profile.dailySunTargetMin) {
        mutableIntStateOf(profile.dailySunTargetMin.coerceIn(10, 120))
    }
    var manual by remember(profile.manualVerifyFallback) { mutableStateOf(profile.manualVerifyFallback) }
    var threshold by remember(profile.lightThresholdLux) {
        mutableFloatStateOf(profile.lightThresholdLux.coerceIn(200, 3000).toFloat())
    }
    var nickname by remember(profile.nickname) { mutableStateOf(profile.nickname) }
    // 【v5.15】初始化阳光币（启动礼包数额），默认 25
    var startupCoins by remember(profile.startupCoins) {
        mutableIntStateOf(profile.startupCoins.coerceIn(0, 100))
    }

    // 演示数据：二次确认 + 生成中防抖，避免连点导致数据叠加
    var confirmingDemo by remember { mutableStateOf(false) }
    var confirmingClear by remember { mutableStateOf(false) }
    var demoBusy by remember { mutableStateOf(false) }

    // 档案是否已从数据库加载完成（updatedAt > 0 表示已落库）。
    // 未加载完成时禁用保存按钮，防止用默认值覆盖真实数据。
    val profileLoaded = profile.updatedAt > 0L

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(SkyColors.SkyLight)
            .padding(contentPadding)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBack, shape = RoundedCornerShape(14.dp)) {
                Text("返回")
            }
            Spacer(Modifier.width(12.dp))
            Text(
                "设置",
                style = MaterialTheme.typography.headlineMedium,
                color = SkyColors.TextPrimary
            )
        }

        Spacer(Modifier.height(16.dp))

        // ---- 个人 ----
        SettingCard(title = "我的昵称") {
            androidx.compose.material3.OutlinedTextField(
                value = nickname,
                onValueChange = { nickname = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // ---- 每日目标 ----
        SettingCard(title = "每日真实阳光目标", subtitle = "当前 $target 分钟") {
            Slider(
                value = target.toFloat(),
                onValueChange = { target = it.toInt() },
                valueRange = 10f..120f,
                steps = 10
            )
        }

        // ---- 启动礼包币数（v5.15 新增）----
        SettingCard(
            title = "初始化阳光币",
    subtitle = "当前 $startupCoins 枚（新用户首次启动时发放）"
        ) {
    Slider(
    value = startupCoins.toFloat(),
   onValueChange = { startupCoins = (it / 5).toInt() * 5 },
       valueRange = 0f..100f,
          steps = 19
            )
          Text(
          "仅在余额为 0 时发放（首次启动、清空数据后）。已有余额时修改不会立即补币，" +
       "避免反复改设置刷币。当前余额 $currentBalance 枚。",
            style = MaterialTheme.typography.labelSmall,
          color = SkyColors.TextSecondary,
          modifier = Modifier.fillMaxWidth()
      )
        }

        // ---- 光照阈值 ----
        SettingCard(
            title = "光照验收阈值",
            subtitle = "当前 ${threshold.toInt()} lx（室内灯光≈100，户外阴天≈1000+）"
        ) {
            Slider(
                value = threshold,
                onValueChange = { threshold = it },
                valueRange = 200f..3000f,
                steps = 27
            )
            Text(
                "若在室内误判为户外，调高阈值；若在阴天无法达标，调低阈值",
                style = MaterialTheme.typography.labelSmall,
                color = SkyColors.TextSecondary
            )
        }

// ---- 开关 ----
// 【v5.11】「连续使用温柔提醒」开关已删除。
        // 【原因】屏幕时长统计早在 v4.0 就整体下线了，
     // 而 Notifier.showScreenReminder 全项目从未被调用（屏幕时长已无从采集），
        // 留着这个开关等于让用户以为有功能在跑。
        // 【遗留】UserProfile.reminderEnabled 字段与数据库列保留不动
        //（删字段要升 Room version +写 Migration，收益不抵风险），
        // 保存时不再回写它，保持用户原有取值不被默认值覆盖。

        SettingCard(
            title = "手动确认降级模式",
            subtitle = "仅用于答辩演示或本机无光线传感器时，按计时直接结算"
        ) {
            Switch(checked = manual, onCheckedChange = { manual = it })
        }

// ---- 权限 ----
        SettingCard(
            title = "权限管理",
      subtitle = "定位与身体活动识别需分别授权；拒绝也不影响使用，App 会自动降级"
        ) {
            Column {
          // 定位为可选权限，未授权时 App 降级为「光照 + 步数」验收
    PermissionRow(
        name = "定位（判断是否在户外，可选）",
   granted = locationGranted,
   action = "申请",
    onClick = onRequestLocation
          )
   Spacer(Modifier.height(8.dp))
   // 身体活动识别：缺失时 TYPE_STEP_COUNTER 不返回数据，步数会一直是 0
                // 【v5.3】与定位彻底解耦，点这一行只申请身体活动识别本身
         PermissionRow(
         name = "身体活动识别（步数统计，可选）",
       granted = activityGranted,
       action = "申请",
         onClick = onRequestActivity
         )
 }
        }

        // ---- 演示模式 ----
        SettingCard(
            title = "演示模式",
            subtitle = "一键生成 14 天模拟数据，天空之城 / 账本 / 徽章 / 报告立刻可展示"
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { confirmingDemo = true },
                    enabled = !demoBusy,
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SkyColors.SunGold),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (demoBusy) "生成中…" else "生成演示数据", color = Color.White)
                }
                OutlinedButton(
                    onClick = { confirmingClear = true },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("清除演示数据")
                }
            }
        }

        if (confirmingDemo) {
            AlertDialog(
                onDismissRequest = { confirmingDemo = false },
                title = { Text("生成演示数据？") },
                text = {
                    Text(
                        "将写入 14 天模拟流水（含 20枚启动币）与 21 个建筑点位。\n\n" +
                            "重复生成会自动覆盖上一轮，不会叠加。"
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        confirmingDemo = false
                        demoBusy = true
                        onGenerateDemo()
                        demoBusy = false
                    }) { Text("确认生成") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmingDemo = false }) { Text("取消") }
                }
            )
        }

        if (confirmingClear) {
            AlertDialog(
                onDismissRequest = { confirmingClear = false },
                title = { Text("清除演示数据？") },
                text = { Text("将清空阳光账本流水与天空之城建筑，恢复到空白状态。此操作不可撤销。") },
                confirmButton = {
                    TextButton(onClick = {
                        confirmingClear = false
                        onClearDemo()
                    }) { Text("确认清除") }
                },
                dismissButton = {
                    TextButton(onClick = { confirmingClear = false }) { Text("取消") }
                }
            )
        }

        // ---- 保存 ----
        if (!profileLoaded) {
            Text(
                "正在加载你的设置…稍等片刻即可保存",
                style = MaterialTheme.typography.labelSmall,
                color = SkyColors.TextSecondary
            )
            Spacer(Modifier.height(8.dp))
        }
        Button(
            onClick = {
                onSave(
                    profile.copy(
nickname = nickname.ifBlank { "追光者" },
 dailySunTargetMin = target,
           // 【v5.11】不再回写 reminderEnabled：对应开关已删除，
   // 回写会用默认值覆盖用户历史设置。字段保留但不再由 UI 改动。
manualVerifyFallback = manual,
    lightThresholdLux = threshold.toInt(),
          // 【v5.15】保存启动礼包数额
    startupCoins = startupCoins
                    )
                )
                onBack()
            },
            // 档案未加载完成时禁用保存，防止默认值覆盖真实设置
            enabled = profileLoaded,
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp),
            shape = RoundedCornerShape(25.dp),
            colors = ButtonDefaults.buttonColors(containerColor = SkyColors.SkyTop)
        ) {
            Text("保存设置")
        }

        // ---- 关于 ----
        Spacer(Modifier.height(16.dp))
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = SkyColors.SkyTop),
            modifier = Modifier.fillMaxWidth()
        ) {
Column(modifier = Modifier.padding(18.dp)) {
    Text(
  "追光 1.0",
 style = MaterialTheme.typography.titleLarge,
     color = Color.White
       )
    Spacer(Modifier.height(8.dp))
Text(
          // 【v5.12】关于文案重写为三段结构：对比 → 机制 → 产出。
  // 原句偏技术抽象，评委读完不知道这产品到底解决什么问题、与同类有何区别。
    "别的 App 催你少看屏幕，我们陪你出门。\n\n" +
     "每次走到户外，光线、定位、步数三路传感器一起作证，把晒到的阳光存成阳光币。\n\n" +
        "阳光币能兑换任务卡，完成真户外验收就点亮一座建筑 —— " +
        "从一间小屋到整片天空之城，成长看得见。",
style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.92f),
      // 【v5.12】文案变长，显式占满宽度，避免被压缩成窄列逐字换行
    modifier = Modifier.fillMaxWidth()
          )
        }
    }
}
}

@Composable
private fun SettingCard(
    title: String,
    subtitle: String = "",
    content: @Composable () -> Unit
) {
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SkyColors.CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = SkyColors.TextPrimary
            )
            if (subtitle.isNotEmpty()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = SkyColors.TextSecondary
                )
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun PermissionRow(
    name: String,
    granted: Boolean,
    action: String,
    onClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(
                    if (granted) SkyColors.SuccessGreen else SkyColors.DebtOrange,
                    androidx.compose.foundation.shape.CircleShape
                )
        )
        Spacer(Modifier.width(10.dp))
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            color = SkyColors.TextPrimary,
            modifier = Modifier.weight(1f)
        )
        if (!granted) {
            OutlinedButton(onClick = onClick, shape = RoundedCornerShape(12.dp)) {
                Text(action)
            }
        } else {
            Text(
                "已开启",
                style = MaterialTheme.typography.labelSmall,
                color = SkyColors.SuccessGreen
            )
        }
    }
}