package com.sunshine.exchange

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apartment
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Style
import androidx.compose.material.icons.filled.TrackChanges
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.sunshine.exchange.core.DateUtils
import com.sunshine.exchange.core.Notifier
import com.sunshine.exchange.data.TaskCard
import com.sunshine.exchange.ui.MainViewModel
import com.sunshine.exchange.ui.screens.CreateCustomCardDialog
import com.sunshine.exchange.ui.screens.MySkyScreen
import com.sunshine.exchange.ui.screens.PermissionGuideScreen
import com.sunshine.exchange.ui.screens.ReportScreen
import com.sunshine.exchange.ui.screens.SettingsScreen
import com.sunshine.exchange.ui.screens.SkyCityScreen
import com.sunshine.exchange.ui.screens.TaskCardsScreen
import com.sunshine.exchange.ui.screens.VerifyScreen
import com.sunshine.exchange.ui.theme.SkyColors
import com.sunshine.exchange.ui.theme.SunshineTheme
import kotlinx.coroutines.launch

/**
 * 单 Activity 入口：底部 5 Tab 导航（天空之城 / 任务卡 / 进行中 / 我的天空 / 报告）。
 * 对应需求文档 6「页面结构与交互流程」。
 */
class MainActivity : ComponentActivity() {

    private val locationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { viewModel?.refreshPermissions() }

    private val activityPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel?.refreshPermissions()
        if (!granted) {
            android.util.Log.w("SunshineApp", "身体活动识别权限被拒绝，计步任务无法完成")
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { viewModel?.refreshPermissions() }

    private var viewModel: MainViewModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SunshineTheme {
                val vm: MainViewModel = viewModel()
                viewModel = vm
                SunshineApp(vm)
            }
        }
    }

    /**
     * 【v5.2】回到前台时刷新权限状态。
     *
     * 【为什么必须有】用户可能不点 App 内的「申请」按钮，而是自己去
     * 「手机设置 → 应用 → 追光 → 权限」里手动打开。这时没有任何权限回调，
     * 界面会一直停在旧状态。onResume 是唯一能兜住这个场景的时机。
     */
    override fun onResume() {
        super.onResume()
        viewModel?.refreshPermissions()
    }

    /**
     * 单独申请身体活动识别权限。
     *
     * 【为什么要单独一个方法】Android 10 起，计步传感器依赖的是
     * ACTIVITY_RECOGNITION，与定位权限相互独立。用户在定位弹窗里勾选时
     * 系统不会一并询问，导致步数一直为 0。必须单独发起申请。
     */
    fun requestActivityPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        activityPermissionLauncher.launch(Manifest.permission.ACTIVITY_RECOGNITION)
    }

    /**
     * 【v5.3】申请定位权限。
     *
     * 【重要改动】原先这里会把 ACTIVITY_RECOGNITION 一起打包进定位申请
     * （Android 10+ 时追加）。设置页「权限管理」改为两项独立申请后，
     * 打包会导致用户在点「定位」时被动申请身体活动识别，
     * 而点「身体活动识别」那行又单独申请一次——同一个权限弹两遍，
     * 且用户无法判断弹窗里到底在要什么。
     *
     * 现在定位只申请定位本身，两项权限彻底解耦：
     * 点哪行申请哪行，互不干扰。
     */
    fun requestLocationPermissions() {
        val perms = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        locationPermissionLauncher.launch(perms.toTypedArray())
    }

    fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/** 定位权限是否已授予（任一精度即可） */
private fun android.content.Context.hasLocationPermission(): Boolean {
    val fine = androidx.core.content.ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    val coarse = androidx.core.content.ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
    return fine || coarse
}

/** 通知权限（Android 13 以下视为已授予） */
private fun android.content.Context.hasNotificationPermission(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return androidx.core.content.ContextCompat.checkSelfPermission(
        this, Manifest.permission.POST_NOTIFICATIONS
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

/**
 * 身体活动识别权限（Android 10+）。
 *
 * 【重要】没有它，`Sensor.TYPE_STEP_COUNTER` 不返回数据，步数会一直是 0。
 * 它和定位是两套独立权限，必须单独申请。
 */
private fun android.content.Context.hasActivityRecognitionPermission(): Boolean {
    // Android 10 以下该权限随定位权限一起授予，无需单独申请
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
    return androidx.core.content.ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACTIVITY_RECOGNITION
    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
}

private enum class Tab(val label: String, val icon: ImageVector) {
    CITY("天空之城", Icons.Filled.Apartment),
    CARDS("任务卡", Icons.Filled.Style),
    VERIFY("进行中", Icons.Filled.TrackChanges),
    MINE("我的天空", Icons.Filled.Person),
    REPORT("报告", Icons.Filled.Insights)
}

@Composable
private fun SunshineApp(vm: MainViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHost = remember { SnackbarHostState() }

    val homeState by vm.homeState.collectAsStateWithLifecycle()
    val profile by vm.profileFlow.collectAsStateWithLifecycle(initialValue = null)
    val balance by vm.balanceFlow.collectAsStateWithLifecycle(initialValue = 0)
    val ledger by vm.ledgerFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val buildings by vm.buildingsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val cards by vm.cardsFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val activeLogs by vm.activeFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    // 【修复 P0-3】doneFlow 之前定义了却从未 collect，
    // 导致验证台「历史记录」永远为空（activeFlow 只含 EXCHANGED/RUNNING）
    val doneLogs by vm.doneFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val badges by vm.badgesFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    // 【v5.2】权限状态改为从 ViewModel 订阅。
    // 【关键】不能直接用 context.hasLocationPermission() 传参——那是纯函数返回值，
    // 权限变化时 Compose 收不到信号，界面永远停在旧值。必须走 State 才有响应式。
    val permissions by vm.permissionState.collectAsStateWithLifecycle()
    // 【v5.1】seasons 订阅已移除：二十四节气章区块下线。
    // 若继续订阅，节气的每次 Room 变更都会白白触发整页重组。
    val screenRecords by vm.screenFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    // 【性能】tab 必须声明在任何依赖它的订阅之前
    var tab by remember { mutableStateOf(Tab.CITY) }

    // 【性能】天际线动画开关：首次进入首页时开启，5 秒后自动关闭。
    // 这样动画只在本页短暂生效，不会在后台持续占用帧率。
    var skylineAnimated by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        skylineAnimated = true
        kotlinx.coroutines.delay(5000)
        skylineAnimated = false
    }

    /**
     * 【性能】验证台状态与诊断信息**按 Tab 订阅**，不放在顶层无条件 collect。
     *
     * 【为什么这样改】verifyState 由前台服务每秒 tick 推送（传感器采样期间），
     * diagnostics 也持续变化。若在顶层 collect，**每秒都会重组整个 SunshineApp**
     * ——包括底部导航栏和当前页面，这正是「切标签不灵活 / 像无响应」的直接原因。
     *
     * 现在只有停留在「进行中」页时才订阅这两条 Flow，离开即取消，
     * 其余时间它们完全不活跃。
     */
    val verifyState = if (tab == Tab.VERIFY) {
      vm.verifyState.collectAsStateWithLifecycle().value
    } else {
        com.sunshine.exchange.ui.VerifyState()
    }
    val diagnostics = if (tab == Tab.VERIFY) {
        vm.diagnostics.collectAsStateWithLifecycle().value
    } else {
        com.sunshine.exchange.core.SunlightVerifier.Diagnostics(
       lightSensorPresent = false,
      lightRegistered = false,
    lightEventCount = 0,
stepSensorPresent = false,
    stepRegistered = false,
         stepEventCount = 0,
            permissionGranted = false,
     activityPermissionGranted = false
        )
    }

    var showSettings by remember { mutableStateOf(false) }
    var onboarding by remember { mutableStateOf<Boolean?>(null) }
    var weekly by remember { mutableStateOf<com.sunshine.exchange.data.WeeklySummary?>(null) }
    var behaviorReport by remember { mutableStateOf<com.sunshine.exchange.data.BehaviorReport?>(null) }
    var showCreateCard by remember { mutableStateOf(false) }
    /**
     * 【v5.9】兑换二次确认：点击任务卡时先弹此框，用户确认后才真正扣币创建。
     * 存的是待兑换的卡，确认时用它拿到当时的定价——避免二次读取时价格已变。
     */
    var pendingExchange by remember { mutableStateOf<com.sunshine.exchange.data.TaskCard?>(null) }

    // 引导页判定标志：只有真正展示过引导页后才置位，避免把"数据还没到"误判为"已完成引导"
    var guideShown by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        vm.bootstrap()
    }

    /**
     * 等 profile 真正从数据库 emit 出来后再决定是否展示引导页。
     * 【为什么必须这样】vm.bootstrap() 只是启动协程、不阻塞，
     * 若在它之后立即读取 profile，读到的必然是 collectAsState 的初始值 null，
     * 会误判成「已完成引导」而永久跳过引导页。
     */
    LaunchedEffect(profile) {
        val p = profile ?: return@LaunchedEffect
        if (!p.onboardingDone && onboarding == null) onboarding = true
    }

    // 引导页展示过 / 用户跳过后，写入标志位，保证不再重复弹出
    LaunchedEffect(guideShown) {
        if (guideShown) {
            profile?.let { p ->
                if (!p.onboardingDone) vm.updateProfile(p.copy(onboardingDone = true))
            }
        }
    }

    // 每周汇总 & 报告加载
    LaunchedEffect(tab, screenRecords.size) {
        vm.weeklySummary(DateUtils.weekStartOf(DateUtils.today())) { summary ->
            weekly = summary
        }
    }

    // 首次启动的权限引导。
    // 首次启动的权限引导。
    // 兜底：若 3秒后数据库仍未返回 profile（异常情况），直接放行进入主界面，
    // 避免用户卡在全屏引导页却不知为何点不动。
    LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(3000)
        if (onboarding == null) {
            onboarding = false
            guideShown = true      // 兜底放行同样标记完成，避免下次又卡
        }
    }

    val onboardingState = onboarding
    if (onboardingState != null && !onboardingState) {
        PermissionGuideScreen(
// 【v5.2】统一走 permissions 状态流，与设置页保持同一数据源
            locationGranted = permissions.location,
            notificationGranted = permissions.notification,
            onRequestLocation = { (context as? MainActivity)?.requestLocationPermissions() },
            onRequestNotification = { (context as? MainActivity)?.requestNotificationPermission() },
            onFinish = {
                guideShown = true
                onboarding = false
            },
            onSkip = {
                guideShown = true
                onboarding = false
            }
        )
        return
    }

    // 设置页：包在 Scaffold 里，保证 Snackbar 有宿主（否则保存/演示数据的反馈无处显示）
    if (showSettings) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHost) }
        ) { padding ->
SettingsScreen(
profile = homeState.profile,
            locationGranted = permissions.location,
      onSave = {
                    vm.updateProfile(it)
                    showSettings = false
                    scope.launch { snackbarHost.showSnackbar("设置已保存", duration = SnackbarDuration.Short) }
                },
onRequestLocation = { (context as? MainActivity)?.requestLocationPermissions() },
          onGenerateDemo = {
                    vm.generateDemoData { msg ->
                        scope.launch {
                            snackbarHost.showSnackbar(msg, duration = SnackbarDuration.Long)
                        }
                    }
                },
                onClearDemo = {
                    vm.clearDemoData { msg ->
                        scope.launch {
                            snackbarHost.showSnackbar(msg, duration = SnackbarDuration.Long)
                        }
                    }
                },
// 【v5.11】notificationGranted 已从设置页移除：「连续使用温柔提醒」卡片已删除，
      // 设置页不再展示通知权限状态。通知权限仍保留在启动引导页中申请。
   activityGranted = permissions.activityRecognition,
   // 【v5.15】传当前余额，供「初始化阳光币」设置项显示提示
      currentBalance = balance,
         onRequestActivity = { (context as? MainActivity)?.requestActivityPermission() },
        onBack = { showSettings = false },
                contentPadding = padding
            )
        }
        return
    }

// 创建自定义任务卡弹窗
    if (showCreateCard) {
        CreateCustomCardDialog(
     balance = balance,
            onDismiss = { showCreateCard = false },
onConfirm = { name, desc, category, difficulty, costCoins, estMinutes, sunRewardMinutes, needSteps ->
           showCreateCard = false
            vm.createCustomCard(
        name, desc, category, difficulty, costCoins, estMinutes,
   sunRewardMinutes, needSteps
    ) { ok, msg ->
  scope.launch {
      // 【修复 P0-7】先跳验证台再弹提示，消除 4 秒无反馈的死寂
  if (ok) tab = Tab.VERIFY
 snackbarHost.showSnackbar(msg, duration = SnackbarDuration.Long)
      }
     }
       }
        )
        return
    }

    // ===== 【v5.9】兑换二次确认 =====
    pendingExchange?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingExchange = null },
  title = { Text("确认兑换「${target.name}」？") },
   text = {
            Column {
          // 价格要醒目：这是用户在确认前唯一会仔细看的数字
     Text(
    "将消耗 ${target.costCoins} 枚阳光币",
            style = MaterialTheme.typography.titleMedium,
       color = SkyColors.CoinGold
      )
          Spacer(Modifier.height(10.dp))
           Text(
        "当前余额 $balance 枚" +
   if (target.costCoins <= balance)
   "，兑换后剩余 ${balance - target.costCoins} 枚"
         else "，还差 ${target.costCoins - balance} 枚",
      style = MaterialTheme.typography.bodyMedium,
        color = if (target.costCoins <= balance) SkyColors.TextPrimary
      else SkyColors.DebtOrange
   )
       Spacer(Modifier.height(10.dp))
   Text(
      "运动 ${target.estMinutes} 分钟 · 需户外光照 ${target.sunRewardMinutes} 分钟验收\n" +
     "完成后按实测阳光时长结算，每晒 5 分钟 +2 币",
        style = MaterialTheme.typography.labelSmall,
                    color = SkyColors.TextSecondary
     )
  }
            },
  confirmButton = {
           Button(
  onClick = {
        pendingExchange = null
        vm.exchangeCard(target.id) { ok, msg ->
          scope.launch {
    if (ok) tab = Tab.VERIFY
      snackbarHost.showSnackbar(
      message = msg,
        actionLabel = if (ok) null else "知道了",
   duration = if (ok) SnackbarDuration.Short
      else SnackbarDuration.Long
          )
   }
   }
    },
              enabled = balance >= target.costCoins,
           colors = ButtonDefaults.buttonColors(containerColor = SkyColors.SkyTop)
   ) {
          Text("确认兑换")
      }
        },
  dismissButton = {
         TextButton(onClick = { pendingExchange = null }) { Text("取消") }
            }
        )
}

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            NavigationBar(containerColor = SkyColors.CardWhite) {
                Tab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = {
                            // 重复点击当前 Tab 不做任何事，避免误停传感器
                            if (tab == item) return@NavigationBarItem
                            // 离开「进行中」时才停止传感器采样。
                            // 注意方向：进入验证台绝不能停，否则验收中的任务会被永久冻结。
                            if (tab == Tab.VERIFY) vm.stopVerify()
                            tab = item
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label, style = MaterialTheme.typography.labelSmall) },
                        colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                            selectedIconColor = SkyColors.SkyTop,
                            selectedTextColor = SkyColors.SkyTop,
                            indicatorColor = SkyColors.SkyBottom
                        )
                    )
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(SkyColors.SkyLight)
        ) {
            when (tab) {
                Tab.CITY -> SkyCityScreen(
      state = homeState,
       // 【性能】动画只在首次进入首页时开启，5 秒后自动关闭。
  // 这样既保留了「天空之城」的动态表现（第一眼印象很重要），
    // 又不会让常驻帧率一直占用导致的卡顿。
        animated = skylineAnimated,
onTaskClick = { card ->
      // 【v5.10】与「任务卡」Tab 统一：点击不再直接扣币，
       // 改为弹出同一个二次确认框，让用户在扣币前看清「消耗多少、剩多少」。
      // 原先这里绕过了确认直接兑换，同一应用两处交互不一致。
pendingExchange = card
      },
   onSeeAllTasks = { tab = Tab.CARDS },
                    onOpenSettings = { showSettings = true }
                )

                Tab.CARDS -> TaskCardsScreen(
                    allCards = cards,
                    balance = balance,
                    isRainy = homeState.weather?.isRainy == true,
// 【v5.9】点击不再直接兑换，改为弹出二次确认框，
                // 让用户在扣币前看清「消耗多少、剩多少、要晒多久」。
                onExchange = { card -> pendingExchange = card },
                onCreateCustom = { showCreateCard = true }
      )

      Tab.VERIFY -> VerifyScreen(
         state = verifyState,
     activeLogs = activeLogs,
      // 【修复 P0-3】补传已完成/已放弃记录，让「历史记录」真正显示
          historyLogs = doneLogs,
   // 直接查已兑换时用的真实任务卡（不要伪造占位卡，会让步数校验失效）
     taskLookup = { id -> cards.firstOrNull { it.id == id } },
     canSettleNow = { card -> vm.canSettleNow(card.sunRewardMinutes, card.needSteps) },
      diagnostics = diagnostics,
   onRequestActivityPermission = { (context as? MainActivity)?.requestActivityPermission() },
            onGoToCards = { tab = Tab.CARDS },
onStart = { log, card -> vm.startVerify(log.id, card) },
           onFinish = {
       vm.completeVerify { msg, ok, sunMinutes, targetReached ->
   scope.launch {
      // 【修复 P0-6】先跳页再弹提示：showSnackbar 是挂起函数，
       // 先弹提示会在数秒内显示「还没有正在进行的任务」空态
       if (ok) {
      if (targetReached) Notifier.showGoalReached(context, sunMinutes)
       tab = Tab.CITY
      }
      snackbarHost.showSnackbar(msg, duration = SnackbarDuration.Long)
       }
             }
     },
        onAbandon = { log, card ->
          vm.abandonChallenge(log.id, card) { ok ->
                scope.launch {
   snackbarHost.showSnackbar(
    if (ok) "已放弃任务，返还一半阳光币" else "该任务已处理，未重复返还",
          duration = SnackbarDuration.Short
        )
          }
   }
            }
          )


Tab.MINE -> MySkyScreen(
      buildings = buildings.map { it.kind },
           weatherCode = homeState.weather?.weatherCode,
        ledger = ledger,
        badges = badges,
      levelTitle = homeState.level.title
     )

                Tab.REPORT -> ReportScreen(
                    weekly = weekly,
                    screenRecords = screenRecords,
                    behaviorReport = behaviorReport,
                    onGenerateReport = {
                        vm.generateReport { report ->
                            behaviorReport = report
                            scope.launch { snackbarHost.showSnackbar("行为改变报告已生成") }
                        }
                    },
onGenerateDemo = {
      vm.generateDemoData { msg ->
        scope.launch {
    snackbarHost.showSnackbar(msg, duration = SnackbarDuration.Short)
  // 生成后必须**主动重查**周报与行为报告。
    // 【修复 P0-5】原先写 `weekly = null` 想强制刷新，
       // 但 weekly 只在 LaunchedEffect(tab, screenRecords.size) 里被赋值，
           // 该协程早已跑完 → 置空后永不恢复，页面永远显示「本周还没有记录」。
      vm.weeklySummary(DateUtils.weekStartOf(DateUtils.today())) { summary ->
            weekly = summary
  }
        vm.generateReport { report -> behaviorReport = report }
        }
    }
        }
      )
        }
    }
  }
}
