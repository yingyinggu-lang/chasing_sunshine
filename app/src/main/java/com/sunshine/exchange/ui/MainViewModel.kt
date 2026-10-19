package com.sunshine.exchange.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.sunshine.exchange.SunshineApp
import com.sunshine.exchange.core.DateUtils
import com.sunshine.exchange.core.EconomyRules
import com.sunshine.exchange.core.Level
import com.sunshine.exchange.core.SensorForegroundService
import com.sunshine.exchange.core.hasActivityRecognitionPermissionCompat
import com.sunshine.exchange.core.hasLocationPermissionCompat
import com.sunshine.exchange.core.hasNotificationPermissionCompat
import com.sunshine.exchange.core.SunlightVerifier
import com.sunshine.exchange.core.Verdict
import com.sunshine.exchange.data.ChallengeLog
import com.sunshine.exchange.data.SunCoinLedger
import com.sunshine.exchange.data.SunshineRepository
import com.sunshine.exchange.data.TaskCard
import com.sunshine.exchange.data.UserProfile
import com.sunshine.exchange.data.WeatherCache
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** 首页 / 全局聚合状态 */
data class HomeState(
    val profile: UserProfile = UserProfile(),
    val coinBalance: Int = 0,
    val todaySunMinutes: Int = 0,
    val todayScreenMinutes: Int = 0,
    val buildings: List<String> = emptyList(),
    val weather: WeatherCache? = null,
    val recommend: List<TaskCard> = emptyList(),
    val level: Level = EconomyRules.levelFor(0),
    val sunMinutes7d: Int = 0,
)

/**
 * 系统权限授权状态（v5.2）。
 *
 * 【为什么必须是 State 而不是现算的Boolean】
 * ContextCompat.checkSelfPermission() 是纯函数：它返回当前授权结果，
 * 但这个结果**不是 Compose 的 State**，Compose 无从感知它变了。
 * 之前设置页直接用 `context.hasLocationPermission()` 当参数传入，
 * 权限弹窗点完「允许」后系统状态确实变了，界面却收不到任何信号，
 * 按钮一直显示「申请」——这是典型的「一次性快照当响应式状态用」的坑。
 */
data class PermissionState(
    val location: Boolean = false,
    val notification: Boolean = false,
    val activityRecognition: Boolean = false
)

/** 进行中页面状态 */
data class VerifyState(
    val activeLog: ChallengeLog? = null,
    val activeCard: TaskCard? = null,
    val verifierState: SunlightVerifier.State = SunlightVerifier.State(),
    val elapsedMinutes: Int = 0,
    /** 当前生效的光照阈值（可在设置中调整 200~3000），须与实际判定一致 */
    val lightThreshold: Int = 1000
)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo: SunshineRepository
    val verifier: SunlightVerifier

    init {
        val application = app as SunshineApp
        repo = SunshineRepository(application, application.database)
        verifier = SunlightVerifier(application)
    }

    // ---------------- 数据流 ----------------

    val profileFlow = repo.db.userProfileDao().observe()
    val ledgerFlow = repo.db.sunCoinLedgerDao().observeAll()
    val balanceFlow = repo.db.sunCoinLedgerDao().observeBalance()
    val buildingsFlow = repo.db.buildingSpotDao().observeAll()
    val activeFlow = repo.db.challengeLogDao().observeActive()
    val doneFlow = repo.db.challengeLogDao().observeDone()
    val badgesFlow = repo.db.badgeDao().observeAll()
    val seasonsFlow = repo.db.seasonBadgeDao().observeAll()
    val screenFlow = repo.db.screenRecordDao().observeAll()
    val cardsFlow = repo.db.taskCardDao().observeAll()

    private val todaySun = MutableStateFlow(0)
    private val weatherState = MutableStateFlow<WeatherCache?>(null)
    private val recommendState = MutableStateFlow<List<TaskCard>>(emptyList())
    private val levelState = MutableStateFlow(EconomyRules.levelFor(0))

    /**
     * 系统权限状态（v5.2）。
     *
     * 【初值为什么在这里现算而不是留空】
     * 若初值全false，App 刚启动、还没调过 refreshPermissions 时，
     * 设置页会短暂显示三项「未授权」，即使系统其实早就授权过了。
     * 用Application context 现算一次作为初值，首帧就是对的。
     */
    val permissionState = MutableStateFlow(
        run {
            val c = app
            PermissionState(
                location = c.hasLocationPermissionCompat(),
                notification = c.hasNotificationPermissionCompat(),
                activityRecognition = c.hasActivityRecognitionPermissionCompat()
            )
        }
    )

    val verifyState: StateFlow<VerifyState> = verifier.state.let { flow ->
        combine(flow, activeFlow, profileFlow) { s, logs, profile ->
            val running = logs.firstOrNull { it.status == "RUNNING" }
            VerifyState(
                activeLog = running,
                activeCard = running?.let { findCard(it.taskId) },
                verifierState = s,
                elapsedMinutes = s.elapsedSeconds / 60,
                // 阈值取自用户档案，保证面板显示与实际判定完全一致
      lightThreshold = profile?.lightThresholdLux ?: 1000
)
        }
   // 【性能】传感器每 15Hz 推送，若组装结果与上次完全相同则不重组界面。
            // 采集中有大量数值不变的时刻，这一步能显著减少无效重组。
  .distinctUntilChanged()
         .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), VerifyState())
    }

/** 传感器采样诊断信息（供UI 排查"传感器有没有在回调"） */
    val diagnostics = MutableStateFlow(SunlightVerifier.Diagnostics(
        lightSensorPresent = false,
        lightRegistered = false,
        lightEventCount = 0,
        stepSensorPresent = false,
        stepRegistered = false,
        stepEventCount = 0,
        permissionGranted = false,
        activityPermissionGranted = false
    ))

    fun refreshDiagnostics() {
        diagnostics.value = verifier.diagnostics()
    }

    /** 近 7 日累计阳光分钟数，用于计算等级称号 */
    private var levelSunMinutes = 0

    /** 当前进行中的挑战 ID，用于精确结算（避免多任务时结算错对象） */
    private var activeLogId: Long = 0L

    /**
     * 首页聚合状态。
     * 说明：Flow.combine 最多支持 5 个参数，超出部分用嵌套两两合并，
     * 因此这里先把 daily / extra 两组各自合并，再与基础流合并。
     */
    val homeState: StateFlow<HomeState> = run {
        val daily = combine(todaySun, weatherState) { s, w -> Pair(s, w) }
        val extra = combine(recommendState, levelState) { r, l ->
      Pair(r, l)
        }
        val base = combine(profileFlow, balanceFlow, buildingsFlow) { profile, balance, spots ->
            Triple(profile ?: UserProfile(), balance, spots.map { it.kind })
        }
        combine(base, daily, extra) { b, d, x ->
            HomeState(
                profile = b.first,
                coinBalance = b.second,
                buildings = b.third,
                todaySunMinutes = d.first,
                weather = d.second,
                recommend = x.first,
                level = x.second,
                sunMinutes7d = levelSunMinutes
            )
        }
    }
    // 【性能】拦截「值完全相同」的重复 emit。
    // homeState 依赖 9 条数据流，任一变化都会重新组装 HomeState，
    // 若组装结果与上一次相同（例如同一个 List 实例），就没必要触发 UI 重组。
            .distinctUntilChanged()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HomeState())

    /** 内部用的四元组 */
    private data class Quad<A, B, C, D>(
        val first: A, val second: B, val third: C, val fourth: D
    )

    // ---------------- 动作 ----------------

    fun bootstrap() = viewModelScope.launch {
        // 数据库初始化失败不应让App 崩溃，也不应阻断后续状态刷新
        runCatching { repo.bootstrap() }
        runCatching { weatherState.value = repo.getWeatherToday() }
        loadRecommend()
        computeLevel()
    }

    /**
     * 刷新全部权限状态。
     *
     * 【v5.2 修复】原来这里是**空函数**，导致整条刷新链路从源头断开：
     * 权限弹窗回调后什么都没做，界面自然停在旧值上。
     * 现在真正读取系统授权结果写入 StateFlow，触发设置页重组。
     *
     * 【为什么要用 distinctUntilChanged 之类的判断】
     * onResume 每次回到前台都会调，若无条件赋值会产生一次多余重组。
     * 这里只在内容真的变化时才 emit。
     */
    fun refreshPermissions() {
        val context = getApplication<Application>()
        val next = PermissionState(
            location = context.hasLocationPermissionCompat(),
            notification = context.hasNotificationPermissionCompat(),
            activityRecognition = context.hasActivityRecognitionPermissionCompat()
        )
        if (permissionState.value != next) {
            permissionState.value = next
        }
    }

    private fun loadRecommend() = viewModelScope.launch {
        recommendState.value = repo.recommendCards()
    }

    private fun computeLevel() = viewModelScope.launch {
        val records = repo.db.screenRecordDao().getAll()
        val week = records.filter { it.date >= DateUtils.daysAgo(6) }
        levelSunMinutes = week.sumOf { it.sunMinutes }
        todaySun.value = repo.db.screenRecordDao().getByDate(DateUtils.today())?.sunMinutes ?: 0
        levelState.value = EconomyRules.levelFor(levelSunMinutes)
    }

    private suspend fun findCard(id: Int): TaskCard? =
        repo.db.taskCardDao().getById(id)

    // ---- 任务卡兑换 ----

    /**
     * 兑换任务卡。
     * 回调签名带上成功标志，UI 层据此决定是否跳转，不再靠解析提示文案来判断，
     * 避免文案一改跳转就失效。
     */
    fun exchangeCard(taskId: Int, onResult: (success: Boolean, message: String) -> Unit) =
        viewModelScope.launch {
            val result = repo.exchangeCard(taskId)
            onResult(result.success, result.message)
            loadRecommend()
        }

    // ---- 开始验证 ----

    fun startVerify(logId: Long, card: TaskCard) = viewModelScope.launch {
        repo.startChallenge(logId)
activeLogId = logId
     val profile = repo.db.userProfileDao().get() ?: UserProfile()
        // 【关键】必须先启动前台服务再注册传感器。
        // 国产 ROM 在App 切后台后会停止传感器回调，导致照度/步数恒为 0。
        SensorForegroundService.start(getApplication())
        // 【关键】注入每秒 tick：推进采样计时 + 刷新 UI，
        // 否则无传感器设备上计时永远为 0，手动降级模式将无法结算
        SensorForegroundService.onSensorTick = {
            verifier.tick()
        }
        verifier.start(
          requireSteps = card.needSteps,
            threshold = profile.lightThresholdLux,
  manualFallback = profile.manualVerifyFallback
        )
        // 定位刷新（户外判定的兜底）
        verifier.refreshLocation()
    // 采样一段时间后刷新诊断信息（确认传感器是否真的在回调）
        viewModelScope.launch {
            kotlinx.coroutines.delay(1500)
            refreshDiagnostics()
        }
    }

    fun stopVerify() {
        verifier.stop()
        SensorForegroundService.stop(getApplication())
    }

    /**
     * 某张卡当前是否已可结算。
     * 复用 Verifier 的统一判定，避免 UI 与 evaluate() 两处实现漂移。
     */
    fun canSettleNow(targetMinutes: Int, needSteps: Boolean): Boolean =
        verifier.canSettleNow(targetMinutes, needSteps)

    /**
 * 【防重复提交】结算 / 放弃按钮可能在 100ms 内被连击。
 * Room 的 getById 是挂起调用，两个协程可能都在任何一次 update 之前
     * 读到同一条 RUNNING 记录，导致双倍结算 + 双座建筑。
     * 这里用原子开关做前置拦截。
     */
    private val settling = java.util.concurrent.atomic.AtomicBoolean(false)

    /**
     * 完成结算：三路验收通过。
     * 回调带回结构化结果，供 UI 精确展示（避免靠解析文案判断）。
     */
    fun completeVerify(onDone: (String, Boolean, Int, Boolean) -> Unit) = viewModelScope.launch {
        if (!settling.compareAndSet(false, true)) {
      onDone("正在结算中，请稍候", false, 0, false)
   return@launch
        }
     try {
        // 用 UI 传来的 logId 精确定位，避免多个 RUNNING 时结算错对象
        val log = repo.db.challengeLogDao().getById(activeLogId)
            ?: repo.db.challengeLogDao().getAll().firstOrNull { it.status == "RUNNING" }
        val card = log?.let { findCard(it.taskId) }
        if (log == null || card == null) {
            onDone("没有正在进行的任务", false, 0, false)
            return@launch
        }
        val state = verifier.state.value
        val verdict: Verdict = verifier.evaluate(card.sunRewardMinutes)
        // 结算时保留读数：未通过时用户还要看到差哪一路，不能立刻抹掉
        verifier.stop(clearData = false)
      // 采样结束，停掉前台服务通知
 SensorForegroundService.stop(getApplication())

        if (!verdict.passed) {
            // 未命中 → 温和提示，不没收不惩罚
            onDone(verdict.reason, false, 0, false)
            return@launch
        }

        // 【修复展示与到账不一致】原代码用 maxOf(sunMinutes, card.sunRewardMinutes / 2)，
        // 导致面板显示"累计 0 分钟"却到账 12 分钟。现在按实测时长结算，
        // 并保证至少给 1 分钟，避免用户白跑一趟。
        val measuredMinutes = state.verifiedSeconds / 60
        val sunMinutes = maxOf(1, measuredMinutes)
val result = repo.completeChallenge(
      logId = log.id,
            lightLux = verdict.lightLux,
    outdoor = verdict.outdoor,
            steps = verdict.steps,
      sunMinutes = sunMinutes,
          card = card
        )
        activeLogId = 0L
        computeLevel()
    loadRecommend()
   onDone(
            result.message,
    true,
     result.settlement.sunTotalMinutes,
 result.settlement.targetReached
        )
    } finally {
     // 【关键】无论成功失败都要释放标志位，否则后续无法再结算
    settling.set(false)
        }
    }

fun abandonChallenge(logId: Long, card: TaskCard?, onDone: (Boolean) -> Unit = {}) =
        viewModelScope.launch {
        // 【P1-1】同样需要防连击：两个协程可能都在 update 之前通过状态校验
        if (!settling.compareAndSet(false, true)) {
       onDone(false)
     return@launch
            }
 try {
  val ok = repo.abandonChallenge(logId, card)
       if (ok) {
   activeLogId = 0L
          // 【修复 P1-10】放弃后必须同时停传感器，
      // 否则传感器继续注册、verifiedSeconds 继续累加却没有前台服务兜底
          verifier.stop()
      SensorForegroundService.stop(getApplication())
       loadRecommend()
    }
       onDone(ok)
            } finally {
         settling.set(false)
            }
        }

    /**
     * 创建自定义任务卡。
     * 成功后自动跳到「进行中」，用户可以立即开始验收。
     */
fun createCustomCard(
   name: String,
     desc: String,
        category: String,
        difficulty: Int,
        costCoins: Int,
        estMinutes: Int,
        /** 【v5.7】验收光照门槛（独立于运动时长） */
        sunRewardMinutes: Int,
        needSteps: Boolean,
        onResult: (success: Boolean, message: String) -> Unit
) = viewModelScope.launch {
   val ok = repo.createCustomCard(
   name, desc, category, difficulty, costCoins, estMinutes,
            sunRewardMinutes, needSteps
        )
        val msg = when {
            ok -> "已创建并兑换「$name」，去「进行中」开始验收吧"
            name.isBlank() -> "请先给这个运动起个名字"
            name.trim().length > 12 -> "名字太长了，请控制在 12 个字以内"
            else -> "创建失败：可能与已有卡片重名，或阳光币不足"
        }
        if (ok) loadRecommend()
        onResult(ok, msg)
    }

    /** 删除自定义任务卡 */
    fun deleteCustomCard(id: Int, onResult: (Boolean) -> Unit = {}) = viewModelScope.launch {
        val ok = repo.deleteCustomCard(id)
        if (ok) loadRecommend()
        onResult(ok)
    }

    // ---- 设置 ----

    fun updateProfile(profile: UserProfile) = viewModelScope.launch {
        repo.updateProfile(profile)
    }

    // ---- 演示模式 ----

    fun generateDemoData(onDone: (String) -> Unit) = viewModelScope.launch {
        repo.generateDemoData(14)
        computeLevel()
        loadRecommend()
        onDone("已生成 14 天演示数据：天空之城、账本、徽章与行为改变报告均可直接展示")
    }

    /** 清除演示数据，恢复干净账本与天际线 */
    fun clearDemoData(onDone: (String) -> Unit) = viewModelScope.launch {
        runCatching { repo.clearDemoData() }
            .onSuccess {
                        computeLevel()
                loadRecommend()
                onDone("演示数据已清除，账本与天空之城已重置")
            }
            .onFailure { onDone("清除失败，请重试") }
    }

    /** 行为改变报告 */
    fun generateReport(onDone: (com.sunshine.exchange.data.BehaviorReport) -> Unit) =
        viewModelScope.launch {
            val report = repo.generateBehaviorReport()
            onDone(report)
        }

    /** 拉取指定周汇总（对外暴露为非挂起形式） */
    fun weeklySummary(weekStart: String, onResult: (com.sunshine.exchange.data.WeeklySummary) -> Unit) =
        viewModelScope.launch {
            onResult(repo.weeklySummary(weekStart))
        }
}