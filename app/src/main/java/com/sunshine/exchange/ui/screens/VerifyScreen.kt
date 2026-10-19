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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.sunshine.exchange.core.SunlightVerifier
import com.sunshine.exchange.data.ChallengeLog
import com.sunshine.exchange.data.TaskCard
import com.sunshine.exchange.ui.VerifyState
import com.sunshine.exchange.ui.theme.SkyColors

/**
 * ③ 进行中（验证台）。
 * 「感光中…」实时显示照度 / 定位 / 步数三路联动——本作品的技术护城河可视化。
 */
@Composable
fun VerifyScreen(
    state: VerifyState,
    activeLogs: List<ChallengeLog>,
    /** 已完成/ 已放弃的历史记录（用于展示闭环） */
    historyLogs: List<ChallengeLog> = emptyList(),
    taskLookup: (Int) -> TaskCard?,
    /** 统一判定：某张卡当前是否已可结算（与 SunlightVerifier.evaluate 共用逻辑） */
    canSettleNow: (TaskCard) -> Boolean,
    /** 传感器诊断信息：真机排查"传感器是否在回调" */
    diagnostics: SunlightVerifier.Diagnostics? = null,
    /** 一键申请身体活动识别权限（步数统计必需） */
    onRequestActivityPermission: () -> Unit = {},
    onGoToCards: () -> Unit,
    onStart: (ChallengeLog, TaskCard) -> Unit,
    onFinish: () -> Unit,
    onAbandon: (ChallengeLog, TaskCard?) -> Unit
) {
    // 正在进行的（含已兑换待开始）记录，按开始时间倒序
    //
    // 【v5.5】历史重复去重。
    //
    // 【为什么还需要这层】数据层已拦截重复兑换，但那只能保证「以后」不再产生。
    // 升级上来的老用户库里可能已有多条同名记录，直接渲染会出现重复任务名。
    // 这里按 taskId 分组，每组只保留 startedAt 最新的一条，
    // activeLogs 本身已按 startedAt DESC 排序，所以 distinctBy 保留的是最新的。
    //
    // 【为什么按 taskId 而不是 cardName】自定义卡允许重名（按名字去重会误杀合法卡）；
    // taskId 才是唯一身份。
    val pending = activeLogs.filter { it.status == "EXCHANGED" }.distinctBy { it.taskId }
    val running = state.activeLog
    val doneLogs = activeLogs.filter { it.status == "RUNNING" }.distinctBy { it.taskId }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(SkyColors.SkyLight),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                "验证台",
                style = MaterialTheme.typography.headlineMedium,
                color = SkyColors.TextPrimary
            )
        }
        item {
            Text(
                "别人验证「时间」，我们验证「光」",
                style = MaterialTheme.typography.bodyMedium,
                color = SkyColors.TextSecondary
            )
        }

// 有正在运行的任务 → 显示感光面板
        if (running != null && state.activeCard != null) {
      item {
      val card = state.activeCard!!
    SensingPanel(
       state = state,
  card = card,
     // 由 ViewModel 统一判定，UI 不再自行推断
          canSettle = canSettleNow(card),
      onFinish = onFinish,
        // 离开验证台会停止采样，必须提供「重新开始」与「放弃」两个出口，
      // 否则用户看到"请重新点开始验收"的提示却找不到那个按钮，任务永久卡死
          onRestart = { onStart(running, card) },
         onAbandon = { onAbandon(running, card) }
          )
        }
        }

        // 已兑换待开始 → 这里是「开始验收」按钮所在处
        if (pending.isNotEmpty()) {
            item {
                Text(
                    if (running == null) "选一个任务开始验收" else "待开始的任务",
                    style = MaterialTheme.typography.titleMedium,
                    color = SkyColors.TextPrimary
                )
            }
            items(pending, key = { it.id }) { log ->
                val card = taskLookup(log.taskId)
                if (card == null) {
                    // 任务卡元数据缺失时明确告知并给出出口，
                    // 而不是渲染一个点了没反应的「开始验收」按钮
                    RecoveryCard(
                        message = "「${log.cardName}」的任务卡数据加载失败，请重新进入任务卡页兑换一次",
                        actionText = "去任务卡",
                        onAction = onGoToCards
                    )
                } else {
                    PendingTaskCard(
                        log = log,
                        card = card,
                        onStart = { onStart(log, card) },
                        onAbandon = { onAbandon(log, card) }
                    )
                }
            }
        }

// 运行中但拿不到任务卡（数据异常）—— 给出修复入口而不是空白
  if (running != null && state.activeCard == null) {
       item {
        RecoveryCard(
      message = "任务数据加载异常，请重新进入任务卡页兑换一次",
    actionText = "去任务卡",
        onAction = onGoToCards
       )
   }
        }

   // 传感器诊断：直接显示"有没有在回调"，用户无需连 adb 就能判断问题所在
        if (running != null && diagnostics != null) {
    item {
            DiagnosticsCard(diagnostics, onRequestActivityPermission)
        }
        }

        // 全空态：明确告诉用户下一步该做什么，而不是一句"选择一个已兑换的任务"
        if (running == null && pending.isEmpty()) {
            item {
                EmptyStateCard(onGoToCards = onGoToCards)
            }
        }

        // 无光感设备提前告知
        if (doneLogs.isNotEmpty() && !state.verifierState.hasLightSensor) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = SkyColors.SunGlow.copy(alpha = 0.14f)
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        "提示：本机没有光线传感器，将用「户外定位 + 步数」验收。" +
                            "如需按计时结算，可在「设置 → 手动确认降级模式」开启。",
                        style = MaterialTheme.typography.labelSmall,
                        color = SkyColors.TextSecondary,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            }
        }

        // 已完成记录，让用户看到闭环
        // 【修复 P0-3】原先从 activeLogs 里筛DONE/FAILED，而 activeLogs 的
        // SQL 是 WHERE status IN ('EXCHANGED','RUNNING')，该条件恒为 false，
        // 导致「历史记录」永远不显示。现改用独立的 historyLogs（observeDone）。
        val completed = (historyLogs + activeLogs.filter { it.status == "FAILED" })
            .distinctBy { it.id }
            .sortedByDescending { it.finishedAt ?: it.startedAt }
        if (completed.isNotEmpty()) {
            item {
                Text(
                    "历史记录",
                    style = MaterialTheme.typography.titleMedium,
                    color = SkyColors.TextPrimary
                )
            }
            items(completed.take(5), key = { it.id }) { log ->
                CompletedRow(log)
            }
        }
    }
}

/**
 * 传感器诊断卡片。
 *
 * 目的：让用户无需连接 adb 就能判断"传感器到底有没有在工作"。
 * 国产 ROM 可能静默停止传感器回调，界面数值恒为 0 但不报错——
 * 只有展示"收到几次回调"才能区分是硬件问题、权限问题还是系统限制。
 */
@Composable
private fun DiagnosticsCard(
    diag: SunlightVerifier.Diagnostics,
    onRequestActivityPermission: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SkyColors.CardWhite),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "传感器诊断",
      style = MaterialTheme.typography.titleMedium,
                color = SkyColors.TextPrimary
)
            Spacer(Modifier.height(8.dp))

            DiagRow(
   "光线传感器",
       ok = diag.lightEventCount > 0,
       detail = buildString {
         append(if (diag.lightSensorPresent) "硬件存在" else "硬件缺失")
        append(" · ")
     append(if (diag.lightRegistered) "已注册" else "注册失败")
append(" · 已回调 ${diag.lightEventCount} 次")
      }
      )
    Spacer(Modifier.height(6.dp))
            DiagRow(
                "计步传感器",
                ok = diag.stepEventCount > 0,
                detail = buildString {
                    append(if (diag.stepSensorPresent) "硬件存在" else "硬件缺失")
                    append(" · ")
                    // 【实测修正】国产 ROM 上 registerListener 返回值不可靠，
                    // 即使返回 false 也可能正常送数据，所以以「硬件存在」判定即可，
                    // 真正有没有数据看「已回调 N 次」。
                    append(if (diag.stepSensorPresent) "已注册" else "注册失败")
                    append(" · 已回调 ${diag.stepEventCount} 次")
                    // 步数为 0 时最常见的成因就是缺少身体活动识别权限
                    if (diag.stepEventCount == 0 && diag.stepSensorPresent) {
                        append(if (diag.activityPermissionGranted)
                            " · 权限已开，系统限流所致"
                        else
                            " · 缺少身体活动识别权限")
                    }
                }
            )

            // 【一键修复】缺权限时直接给按钮，不必绕到设置页
            if (!diag.activityPermissionGranted) {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = onRequestActivityPermission,
                    shape = RoundedCornerShape(12.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = SkyColors.SkyTop
                    )
                ) {
                    Text("开启身体活动识别权限（步数统计必需）")
                }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                when {
                    diag.lightEventCount == 0 && !diag.lightSensorPresent ->
                        "本机没有光线传感器，已降级为「定位 + 步数」验收。"
                    diag.lightEventCount == 0 ->
                        "光线传感器未在送数据：请保持 App 在前台，" +
                            "并在系统设置中为本应用开启「自启动」与省电豁免。"
                    diag.stepEventCount == 0 && diag.stepSensorPresent ->
                        "光线正常。步数为 0 通常是缺少「身体活动识别」权限，" +
                            "请到 设置 → 权限管理 中开启；不开启时需步数的任务无法完成。"
                    else ->
                        "传感器工作正常，数值会随环境实时变化。"
                },
                style = MaterialTheme.typography.labelSmall,
                color = SkyColors.TextSecondary
            )
        }
    }
}

@Composable
private fun DiagRow(name: String, ok: Boolean, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
       modifier = Modifier
     .size(8.dp)
      .background(
    if (ok) SkyColors.SuccessGreen else SkyColors.DebtOrange,
      CircleShape
    )
        )
        Spacer(Modifier.width(10.dp))
        Text(
       name,
    style = MaterialTheme.typography.bodyMedium,
            color = SkyColors.TextPrimary,
            modifier = Modifier.width(88.dp)
        )
        Text(
    detail,
            style = MaterialTheme.typography.labelSmall,
            color = SkyColors.TextSecondary
     )
    }
}

/** 全空态：给出明确的下一步动作 */
@Composable
private fun EmptyStateCard(onGoToCards: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = SkyColors.CardWhite),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("🎫", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(10.dp))
            Text(
                "还没有正在进行的任务",
                style = MaterialTheme.typography.titleMedium,
                color = SkyColors.TextPrimary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "先去「任务卡」兑换一张卡，\n兑换后回到这里就能开始验收",
                style = MaterialTheme.typography.bodyMedium,
                color = SkyColors.TextSecondary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onGoToCards,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SkyColors.SkyTop),
                modifier = Modifier.height(46.dp)
            ) {
                Text("去兑换任务卡", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

/** 数据异常时的恢复入口 */
@Composable
private fun RecoveryCard(message: String, actionText: String, onAction: () -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = SkyColors.DebtOrange.copy(alpha = 0.12f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = SkyColors.TextSecondary
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onAction, shape = RoundedCornerShape(12.dp)) {
                Text(actionText)
            }
        }
    }
}

/** 已完成/已放弃的记录行 */
@Composable
private fun CompletedRow(log: ChallengeLog) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(SkyColors.CardWhite, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (log.status == "DONE") "✅" else "⏭",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                log.cardName,
                style = MaterialTheme.typography.bodyMedium,
                color = SkyColors.TextPrimary
            )
            val detail = if (log.status == "DONE") {
                "真实阳光 ${log.sunMinutesEarned} 分钟 · 峰值 ${log.verifyLightLux} lx" +
                    if (log.verifySteps > 0) " · ${log.verifySteps} 步" else ""
            } else {
                "已放弃"
            }
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = SkyColors.TextSecondary
            )
        }
    }
}

/** 感光中面板：照度 / 户外 / 步数 三路实时状态 */
@Composable
private fun SensingPanel(
    state: VerifyState,
    card: TaskCard,
    canSettle: Boolean,
    onFinish: () -> Unit,
    onRestart: () -> Unit,
    onAbandon: () -> Unit
) {
    // 放弃会返还一半币，属不可逆操作，加二次确认避免误点
    var confirmAbandon by remember { mutableStateOf(false) }

    if (confirmAbandon) {
        AlertDialog(
            onDismissRequest = { confirmAbandon = false },
    title = { Text("放弃这个任务？") },
   text = {
        Text(
   "将返还一半阳光币（约 ${card.costCoins / 2} 枚），" +
  "本次已累积的真实阳光时长不会计入天空之城。"
      )
          },
         confirmButton = {
        TextButton(onClick = {
      confirmAbandon = false
   onAbandon()
   }) { Text("确认放弃") }
        },
            dismissButton = {
      TextButton(onClick = { confirmAbandon = false }) { Text("继续任务") }
       }
  )
    }
    val s = state.verifierState

    /**
     * 【性能】太阳缩放改为**静态**，不再用呼吸动画。
     *
     * 【为什么】验证台本来就有每秒刷新的采样数据（照度/步数/时长），
     * 再叠一个 900ms 循环的无限动画，等于每帧都触发重组——
     * 而验证台正是用户停留时间最长、最容易感知卡顿的页面。
     * 缩放按光照状态直接取值，视觉区分度完全保留。
     */
    val pulse = if (s.lightHit) 1.12f else 0.92f

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = SkyColors.CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        if (s.lightHit) listOf(SkyColors.SunGlow.copy(alpha = 0.35f), Color.Transparent)
                        else listOf(SkyColors.SkyLight, Color.Transparent)
                    )
                )
                .padding(20.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(taskEmoji(card.iconKey), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        card.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = SkyColors.TextPrimary
                    )
                    Text(
                        "目标真实阳光 ${card.sunRewardMinutes} 分钟",
                        style = MaterialTheme.typography.labelSmall,
                        color = SkyColors.TextSecondary
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // 感光中的太阳
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(120.dp)
                        .scale(pulse)
                        .background(
                            Brush.radialGradient(
                                listOf(
                                    SkyColors.SunGlow.copy(alpha = if (s.lightHit) 0.95f else 0.35f),
                                    SkyColors.SunGlow.copy(alpha = 0f)
                                )
                            ),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text("☀️", style = MaterialTheme.typography.headlineLarge)
                }
            }

            Spacer(Modifier.height(8.dp))

            Text(
                if (s.lightHit) "已晒到太阳！" else "感光中…",
                style = MaterialTheme.typography.titleLarge,
                color = if (s.lightHit) SkyColors.SuccessGreen else SkyColors.SkyTop,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            // 三路传感器状态
SensorRow(
     label = "光照传感器",
          value = "${s.lightLux} lx",
       hint = if (s.hasLightSensor) "阈值 ${state.lightThreshold} lx" else "本机确实无光线传感器",
          hit = s.lightHit
    )
            SensorRow(
                label = "定位 / 活动识别",
                value = if (s.outdoorHit) "户外" else "室内或未知",
                hint = buildString {
                    // 展示 GPS 实测数值，让「定位真的在工作」可被验证
if (s.lat != null && s.lng != null) {
   // 【修复】必须指定 Locale.US：否则德语/法语环境下小数点会变成逗号，
       // 输出形如 "22,2855, 114,1621"，用户无法分辨经纬度。
     append(String.format(java.util.Locale.US, "%.4f, %.4f", s.lat, s.lng))
                        s.accuracy?.let { append(" · 精度 ${it.toInt()}m") }
                        append(if (s.outdoorHit) " · 已确认室外" else " · 走到室外才会命中")
} else {
          // 【修复】原先一律显示「未取到定位，请开启定位权限」，
    // 但"没授权"和"刚进室内还没定位到"是两回事，
    // 会让用户误以为手机定位坏了。改为给出两条排查方向。
    append("未取到定位：请确认已授予定位权限且手机定位开关已打开")
          append("；首次取定位需 3~5 秒，可走到窗边/室外稍候重试")
                }
                },
                hit = s.outdoorHit
            )
            SensorRow(
                label = "步数增量",
value = "${s.stepsDelta} 步",
       hint = when {
      // 有步数传感器却一直为 0，几乎都是「身体活动识别」权限没授
  !s.hasStepSensor -> "本机无步数传感器，本任务将跳过步数验收"
            s.stepsDelta == 0 -> "未检测到步数：请到「设置 → 权限管理」开启「身体活动识别」"
     card.needSteps -> "本任务需 ≥${SunlightVerifier.MIN_STEPS} 步"
     else -> "步数已记录，本任务不强制要求"
        },
  hit = if (card.needSteps) s.stepsDelta >= SunlightVerifier.MIN_STEPS else true
    )

            Spacer(Modifier.height(12.dp))

// 合格阳光累计进度。
            // 【修复 P0-1】原先写死「倒计时」但界面并不存在倒计时控件，
       // 而无传感器时该数值恒为 0。现在同时展示「已采样时长」，
        // 让手动降级模式有明确的进度反馈。
            Text(
       "有效阳光累计：${s.verifiedSeconds / 60} 分钟",
   style = MaterialTheme.typography.bodyMedium,
      color = SkyColors.TextSecondary
        )
            Spacer(Modifier.height(4.dp))
     Text(
    "已采样时长：${s.samplingSeconds / 60} / ${card.sunRewardMinutes} 分钟",
          style = MaterialTheme.typography.labelSmall,
     color = SkyColors.TextSecondary
      )
            Spacer(Modifier.height(6.dp))
            val progress = (s.verifiedSeconds / (card.sunRewardMinutes * 60f)).coerceIn(0f, 1f)
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .background(SkyColors.DividerLight, RoundedCornerShape(4.dp))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(8.dp)
                        .background(SkyColors.SunGlow, RoundedCornerShape(4.dp))
                )
            }

            Spacer(Modifier.height(16.dp))

// 实时验收状态：把「现在能不能结算」直接告诉用户，
            // 避免用户一直等却不知道差在哪一路。
    // 判定由 ViewModel.canSettleNow() 提供（内部与 SunlightVerifier.evaluate 共用同一套逻辑），
      // 不要在 UI 里另写一份，否则会出现「提示可以结算但点了失败」的错位。
      val ready = canSettle
        val stepsOk = !card.needSteps || s.stepsDelta >= SunlightVerifier.MIN_STEPS
        val outdoorOk = s.outdoorHit
        val durationOk = s.verifiedSeconds >= card.sunRewardMinutes * 60
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        if (ready) SkyColors.SuccessGreen.copy(alpha = 0.14f)
                        else SkyColors.DebtOrange.copy(alpha = 0.12f),
                        RoundedCornerShape(14.dp)
                    )
                    .padding(14.dp)
            ) {
                Text(
                    when {
                        !s.verifying ->
                            "采样已暂停（离开验证台时会自动停止），请重新点「开始验收」"
!s.hasLightSensor && !s.hasStepSensor ->
       "本机缺少光线与步数传感器，已进入手动计时模式；" +
        "保持页面开启，下方「已采样时长」达标后即可结算"
                        !s.hasLightSensor ->
                            "本机无光线传感器，将用「户外定位 + 步数」验收"
                        ready ->
                            "光照、位置、步数均已达标，可以点「完成结算」了"
                        !s.lightHit ->
                            "还差光照：走到窗边或户外，让照度超过阈值"
                        !outdoorOk ->
                            "还差户外判定：走到室外，等定位灯变绿"
                        !stepsOk ->
                            "还差步数：动起来，让天空之城看到你"
                        else ->
                            "正在采样…"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (ready) SkyColors.SuccessGreen else SkyColors.TextSecondary
                )
            }

Button(
onClick = onFinish,
       // 【修复】未达标时禁用按钮，避免用户反复点击却只看到失败提示
                enabled = ready,
   modifier = Modifier
        .fillMaxWidth()
 .height(50.dp),
      shape = RoundedCornerShape(25.dp),
      colors = ButtonDefaults.buttonColors(
         containerColor = SkyColors.SunGold,
      contentColor = Color.White
      )
     ) {
        Text(
   if (ready) "完成结算" else "尚未达到验收条件",
            style = MaterialTheme.typography.titleMedium
        )
    }

            Spacer(Modifier.height(8.dp))

// 兜底出口：采样被中断（离开页面）时可重新开始，或放弃任务返还一半币。
            // 没有这两个按钮，用户会被卡在无法结算也无法退出的状态。
     Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
          onClick = {
     // 立即给出视觉反馈：状态改变后上方面板会立即刷新，
     // 否则用户会以为按钮没生效
                        onRestart()
             },
     shape = RoundedCornerShape(14.dp),
 modifier = Modifier
    .weight(1f)
            .height(54.dp)
     ) {
      Text(if (s.verifying) "重新开始采样" else "恢复采样")
         }
OutlinedButton(
                    onClick = { confirmAbandon = true },
        shape = RoundedCornerShape(14.dp),
           colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
          contentColor = SkyColors.DebtOrange
    ),
          modifier = Modifier
          .weight(1f)
      // 【修复】原本 44dp 只够放一行，且 9 个字在半宽按钮里会被截断。
   // 现改为两行：「放弃」+「返还 N 枚」，并把具体金额动态显示出来。
                .height(54.dp)
  ) {
         Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
        "放弃",
        style = MaterialTheme.typography.labelLarge
    )
         Text(
            "返还 ${card.costCoins / 2} 枚",
  style = MaterialTheme.typography.labelSmall
        )
       }
        }
    }

            Spacer(Modifier.height(6.dp))
            Text(
                "未命中也没关系：不没收、不惩罚，只提示你再走到户外试试",
                style = MaterialTheme.typography.labelSmall,
                color = SkyColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun SensorRow(
    label: String,
    value: String,
    hint: String,
    hit: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(
                    if (hit) SkyColors.SuccessGreen else SkyColors.DividerLight,
                    CircleShape
                )
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.bodyMedium,
                color = SkyColors.TextPrimary
            )
            Text(
                hint,
                style = MaterialTheme.typography.labelSmall,
                color = SkyColors.TextSecondary
            )
        }
        Text(
            value,
            style = MaterialTheme.typography.titleMedium,
            color = if (hit) SkyColors.SuccessGreen else SkyColors.TextSecondary
        )
    }
}

@Composable
private fun PendingTaskCard(
    log: ChallengeLog,
    card: TaskCard?,
    onStart: () -> Unit,
    onAbandon: () -> Unit
) {
  // 放弃是不可逆操作，需二次确认防止误触
    var confirmAbandon by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SkyColors.CardWhite),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(taskEmoji(card?.iconKey ?: "✨"), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        card?.name ?: log.cardName,
                        style = MaterialTheme.typography.titleMedium,
                        color = SkyColors.TextPrimary
                    )
Text(
     buildString {
          // 【v5.4】两个数字语义完全不同，必须显式区分，否则用户会误解：
        //   estMinutes         = 这项运动**大概要做多久**（运动量参考）
        //   sunRewardMinutes   = **验收通过所需的户外光照时长**（门槛，不是承诺）
       // 原文案「预计 10 分钟 · 阳光 +15 分钟」并列显示，
    //   用户会以为跳 10 分钟就完事，实际被要求晒满 15 分钟才能通过。
        append("运动约 ${card?.estMinutes ?: 15} 分钟")
  val reward = card?.sunRewardMinutes ?: 15
if (reward > 0) append(" · 需户外光照满 $reward 分钟")
         },
          style = MaterialTheme.typography.labelSmall,
     color = SkyColors.TextSecondary
          )
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onStart,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SkyColors.SkyTop)
                ) {
                    Text("开始验收")
                }
OutlinedButton(
                    onClick = { confirmAbandon = true },
        shape = RoundedCornerShape(14.dp),
           colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
          contentColor = SkyColors.DebtOrange
    ),
          modifier = Modifier
          .weight(1f)
      // 【修复】原本 44dp 只够放一行，且 9 个字在半宽按钮里会被截断。
   // 现改为两行：「放弃」+「返还 N 枚」，并把具体金额动态显示出来。
                .height(54.dp)
  ) {
         Column(horizontalAlignment = Alignment.CenterHorizontally) {
          Text(
        "放弃",
        style = MaterialTheme.typography.labelLarge
    )
         Text(
            "返还 ${(card?.costCoins ?: 0) / 2} 枚",
  style = MaterialTheme.typography.labelSmall
        )
       }
        }
            }
        }

   if (confirmAbandon) {
   AlertDialog(
   onDismissRequest = { confirmAbandon = false },
       title = { Text("放弃这个任务？") },
            text = {
     Text(
       "将返还一半阳光币（约 ${card?.costCoins?.div(2) ?: 0} 枚）。" +
            "建议先去户外试一次，多数任务只是差一点光照。"
         )
        },
     confirmButton = {
        TextButton(onClick = {
        confirmAbandon = false
   onAbandon()
        }) { Text("确认放弃") }
      },
    dismissButton = {
      TextButton(onClick = { confirmAbandon = false }) { Text("再试一次") }
            }
    )
      }
    }
}