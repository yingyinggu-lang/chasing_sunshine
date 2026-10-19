package com.sunshine.exchange.core

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.HandlerThread
import android.os.Looper
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.max

/**
 * 真实阳光验证：三路交叉校验（光线 / 定位·活动识别 / 步数）。
 * 对应需求文档 4.1——本作品的核心差异化：别人验证「时间」，我们验证「光」。
 *
 * 判定规则：
 *  · 至少「光照命中 + 户外位置命中」→ 结算真实阳光时长
 *  · 需步数的任务额外要求步数增量达标
 *  · 未命中 → 温和提示，不没收不惩罚
 */
class SunlightVerifier(private val context: Context) : SensorEventListener {

data class State(
  val lightLux: Int = 0,
    val peakLux: Int = 0,
      val lightHit: Boolean = false,
        val outdoorHit: Boolean = false,
        val stepsDelta: Int = 0,
        val elapsedSeconds: Int = 0,
   /**
         * 已采样时长（秒）。由 [tick] 定时器推进，
         * 保证在**无任何传感器**时也能正常计时（手动降级模式依赖它）。
         */
        val samplingSeconds: Int = 0,
        val verifying: Boolean = false,
// 【重要】这两个是**硬件能力**，在 State 创建时就必须准确。
        // 早期版本默认 false、只在 start() 里赋值，导致用户没点「开始验收」时
// 界面误报「本机无光感传感器」「本机无步数传感器」——而实际上手机有。
        val hasLightSensor: Boolean = false,
        val hasStepSensor: Boolean = false,
        /** 已累计的合格阳光秒数 */
        val verifiedSeconds: Int = 0,
        /** GPS 纬度（null 表示尚未取到定位） */
        val lat: Double? = null,
        /** GPS 经度 */
        val lng: Double? = null,
        /** 定位精度（米），越小越准 */
        val accuracy: Float? = null
    )

private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val lightSensor = sensorManager?.getDefaultSensor(Sensor.TYPE_LIGHT)
    private val stepCounter = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_COUNTER)

    // 初始 State 即带上真实的硬件能力，避免界面误报「本机无传感器」
    private val _state = MutableStateFlow(
        State(
            hasLightSensor = lightSensor != null,
            hasStepSensor = stepCounter != null
        )
    )
    val state: StateFlow<State> = _state.asStateFlow()

    /**
     * 本机是否具备光线传感器。
     *
     * 【重要】这是**硬件能力**，应在构造期由 SensorManager 查询确定，
     * 不能放进 State 里等start() 才赋值——否则用户没点「开始验收」时
     * State默认 false，界面会误报「本机无光感传感器」。
     */
    val hasLightSensor: Boolean = lightSensor != null

    /** 本机是否具备计步传感器（同上，硬件能力，构造期确定） */
    val hasStepSensor: Boolean = stepCounter != null

    /** 本机是否具备定位能力 */
    val hasLocationCapability: Boolean = context.packageManager.hasSystemFeature(
        android.content.pm.PackageManager.FEATURE_LOCATION
    )

    /** 阈值：默认 1000lux（户外阴天），可在设置中调整 */
    @Volatile
    var lightThreshold: Int = 1000

    @Volatile
    private var baselineSteps: Float = -1f

    @Volatile
    private var lastKnownLocation: Location? = null

    /** 本次验证是否要求步数（散步/跳绳类必须） */
    @Volatile
    var requireSteps: Boolean = false

    @Volatile
    private var startTime: Long = 0L

    /** 手动确认降级开关：答辩演示或设备无光感时使用 */
    @Volatile
    var manualFallback: Boolean = false

    /**
     * 上一次光线事件的真实时间戳（纳秒）。
     * 用于按「真实经过的秒数」累加有效阳光时长——
     * 若按事件次数累加，SENSOR_DELAY_UI 约每秒 15 次事件，会让时长虚高约 15 倍。
     */
    @Volatile
    private var lastLightTick: Long = 0L

    /** 上一次检查光照的时刻（用于按真实时间累加有效阳光） */
    @Volatile
    private var lastLightCheckAt: Long = 0L

    /** 本次连续达标光照的起始时刻；0 表示当前未达标 */
    @Volatile
    private var lightHitStartAt: Long = 0L

/** 累计的「光照达标」毫秒数。用毫秒累加避免 60ms 事件间隔被整除归零 */
    @Volatile
    private var verifiedMillis: Long = 0L

    /** 上次刷新定位的时间戳，用于判断定位数据是否陈旧 */
    @Volatile
    private var lastLocationAt: Long = 0L

    /**
     * 【性能】是否已有定位请求在飞。
     *
     * tick 每秒都会尝试刷新定位，若不加此标记，
     * 会每秒注册一次 LocationListener（高频注册/注销），
     * 既耗电又会被系统限流，导致永远拿不到定位。
     */
    @Volatile
    private var locating: Boolean = false

    /**
     * 【性能关键】传感器专用线程。
     *
     * 光线与步数传感器每秒产生 30~35 次回调，若跑在主线程，
     * 会造成每秒 30+ 次状态写入与 Compose 重组——这就是"进入验证台后卡顿"的主因。
     * 把它们挪到独立线程后，主线程只在 UI 真正需要时被动更新。
     */
    private var sensorThread: HandlerThread? = null

    /** 传感器回调专用的 Handler（绑定到 sensorThread） */
    @Volatile
    private var sensorHandler: android.os.Handler? = null

    /**
     * 【性能关键】UI 节流：距上次发布状态的时间戳。
     *
     * 光线 15Hz + 步数 20Hz = 每秒 35 次回调，
     * 若每次都写 `_state.value`，UI 每秒重组 35 次——
     * 这才是"验证台卡顿"的根本原因（不是传感器本身）。
     *
     * 现在改为**最多每 500ms 发布一次状态**（2Hz）：
     * 对"照度多少 lux""步数多少步"这类读数，2Hz 已完全够用（人眼看不出差别），
     * 但重组次数从每秒 35 次降到 2 次，**降幅 94%**。
     *
     * 注意：时间累加（verifiedMillis）仍然每次回调都更新，只是不写 State。
     */
    @Volatile
    private var lastStatePublishAt: Long = 0L

    /** UI 状态发布节流间隔（毫秒） */
    private val PUBLISH_INTERVAL_MS = 500L

    /** 用于定位请求的超时兜底回调 */
    private val handler = android.os.Handler(Looper.getMainLooper())

    /** 当前注册中的定位监听器，用于反注册 */
    private var locationListener: android.location.LocationListener? = null

// ---- 诊断信息：用于真机排查"传感器有没有在回调" ----

    /** 光线传感器已收到的回调次数（>0 说明系统真的在送数据） */
    @Volatile
    var lightEventCount: Int = 0
        private set

    /** 计步传感器已收到的回调次数 */
    @Volatile
    var stepEventCount: Int = 0
        private set

    /** 光线传感器注册是否成功 */
    @Volatile
    var lightRegistered: Boolean = false
        private set

    /** 计步传感器注册是否成功 */
    @Volatile
    var stepRegistered: Boolean = false
        private set

    /** 采样诊断信息，供 UI 直接展示 */
    fun diagnostics(): Diagnostics = Diagnostics(
        lightSensorPresent = lightSensor != null,
        lightRegistered = lightRegistered,
        lightEventCount = lightEventCount,
        stepSensorPresent = stepCounter != null,
        stepRegistered = stepRegistered,
        stepEventCount = stepEventCount,
        permissionGranted = hasAllPermissions(),
        activityPermissionGranted = hasActivityPermission()
    )

    data class Diagnostics(
        val lightSensorPresent: Boolean,
        val lightRegistered: Boolean,
        val lightEventCount: Int,
        val stepSensorPresent: Boolean,
        val stepRegistered: Boolean,
        val stepEventCount: Int,
        val permissionGranted: Boolean,
        /** 身体活动识别权限（步数传感器的独立前置条件，Android 10+ 需运行时申请） */
        val activityPermissionGranted: Boolean
    )

    /**
     * 身体活动识别权限。
     *
     * 【为什么要单独检测】Android 10 (API 29) 起，TYPE_STEP_COUNTER
     * 依赖的是 ACTIVITY_RECOGNITION 权限，与定位权限**相互独立**。
     * 用户勾选定位时系统不会一并询问，若引导页不单独提示，
     * 步数传感器就会一直不返回数据（实测「已回调 0 次」的常见成因）。
     */
    private fun hasActivityPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

    private fun hasAllPermissions(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACTIVITY_RECOGNITION) ==
            PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start(requireSteps: Boolean, threshold: Int, manualFallback: Boolean) {
    this.requireSteps = requireSteps
        this.lightThreshold = threshold
     this.manualFallback = manualFallback

        _state.value = State(
    verifying = true,
            hasLightSensor = lightSensor != null,
  hasStepSensor = stepCounter != null
        )
        startTime = System.currentTimeMillis()
        baselineSteps = -1f
        lastLightTick = 0L
        lastLightCheckAt = System.currentTimeMillis()
        lightHitStartAt = 0L
        verifiedMillis = 0L
      lightEventCount = 0
      stepEventCount = 0

        // 【重要】必须检查注册返回值——某些 ROM 上注册会静默失败。
        //
        // 【实测修正】小米等国产 ROM 上，即使注册成功，
        // registerListener 也可能返回 false（返回值不可靠）。
        // 因此「注册成功」的判定必须以**硬件存在**为准，
        // 而「是否真的在送数据」由 lightEventCount / stepEventCount 判断。
        // 否则界面会出现「注册失败」但明明读到 8028 lx 的自相矛盾状态。
        // 【性能关键】必须传入独立的 Handler，把传感器回调挪到子线程。
        // 否则回调跑在主线程：光线传感器 15Hz + 步数传感器 20Hz
        // 持续触发 _state 更新 → 每秒 35 次以上的主线程状态写入与重组，
        // 直接导致界面卡顿。
        // 每次 start() 建一个新线程，stop() 时彻底回收，避免累积泄漏
        val thread = HandlerThread("SunshineSensor-${System.currentTimeMillis()}").apply { start() }
        sensorThread = thread
        sensorHandler = android.os.Handler(thread.looper)

        lightRegistered = lightSensor != null && runCatching {
            sensorManager?.registerListener(
      this, lightSensor, SensorManager.SENSOR_DELAY_UI, sensorHandler
            )
        }.getOrDefault(false) == true

        // 【实测】小米等国产 ROM 对步数传感器限流严重，
        // SENSOR_DELAY_UI 下可能几十秒才回调一次（实测「已回调 0 次」）。
        // 改用 GAME 档（约 20ms）可显著提升实时性；
        // TYPE_STEP_COUNTER 是低功耗传感器，功耗影响可忽略。
        stepRegistered = stepCounter != null && runCatching {
            sensorManager?.registerListener(
this, stepCounter, SensorManager.SENSOR_DELAY_GAME, sensorHandler
     )
        }.getOrDefault(false) == true
 android.util.Log.i(
     "SunshineSensor",
      "注册结果 light=$lightRegistered(present=${lightSensor != null}) " +
        "step=$stepRegistered(present=${stepCounter != null})"
        )
        // 预取一次最近定位，便于首屏即有户外判定
        lastKnownLocation = lastKnownLocation()
    }

    /**
     * 停止采样。
     *
     * @param clearData true = 连同实时数据一起清空（切换任务/放弃时用）；
     *                 false = 只停传感器，保留最后一次读数（结算时用，
     *                 否则未通过的原因会被立刻抹掉，用户看不到差哪一路）
     */
    fun stop(clearData: Boolean = true) {
        sensorManager?.unregisterListener(this)
        // 【重要】必须彻底关闭传感器线程，否则每次采样都泄漏一个线程，
        // 开几次验证台就会耗尽进程资源 → 卡顿、OOM 闪退。
        sensorHandler?.removeCallbacksAndMessages(null)
        sensorHandler = null
        sensorThread?.quitSafely()
        sensorThread = null
        cancelLocationRequest()          // 停止采样时务必反注册定位
        handler.removeCallbacksAndMessages(null)
        if (!clearData) {
            _state.value = _state.value.copy(verifying = false)
            return
        }
        // 【修复】停采样时必须**清空实时数据**。
        // 原先只把 verifying 置 false，却把 lightLux / lightHit / outdoorHit /
        // verifiedSeconds 留在 State 里，导致换一张任务卡后面板仍显示上一张卡的
        // 光照值与「已晒到太阳」，判定也沿用旧数据——
        // 界面提示与实际结算不一致，用户感知就是「点了没反应」。
        lightHitStartAt = 0L
        lastLightCheckAt = 0L
        lightEventCount = 0
        stepEventCount = 0
        verifiedMillis = 0L
        baselineSteps = -1f
        _state.value = _state.value.copy(
            verifying = false,
            lightLux = 0,
            peakLux = 0,
            lightHit = false,
            outdoorHit = false,
            stepsDelta = 0,
            elapsedSeconds = 0,
            samplingSeconds = 0,
            verifiedSeconds = 0,
            lat = null,
            lng = null,
            accuracy = null
        )
    }

override fun onSensorChanged(event: SensorEvent) {
        // [性能修复] 原先这里每 15Hz 打一条 Log.d（每秒 15 条）。
        // 高频日志在国产 ROM 上非常昂贵（要写文件 + 字符串拼接），
        // 是「进入验证台后卡顿」的重要原因之一。
        // 诊断需求已由「已回调 N 次」计数器满足，无需逐条打印。
      when (event.sensor.type) {
            Sensor.TYPE_LIGHT -> {
                lightEventCount++
                val lux = event.values.firstOrNull()?.toInt() ?: 0
                val prev = _state.value
                val lightHit = lux >= lightThreshold

                // 【核心修复】不再用「相邻两次事件的间隔」累加时长。
                //
                // 【为什么原来会永远 0】国产 ROM 常把光线传感器限流到
                // 「状态变化时才回调」——用户站在户外照度不变时，
                // 几秒甚至几十秒才来一个事件，甚至只有 1 个事件。
                // 而旧逻辑 lastLightTick 只有在收到第 2 个事件后才开始累加，
                // 于是 verifiedMillis 恒为 0 → 「有效阳光累计永远 0 分钟」，
                // 用户站在太阳下多久都无法结算（实机实测确认）。
                //
                // 【改为时间驱动】记录「首次达标时刻」，之后每次回调
                // （以及 tick 定时器）都按真实经过时间累加，
                // 这样事件稀疏也不会少算，且照度掉下去会立即暂停。
                if (lightHit) {
                    val now = System.currentTimeMillis()
                    if (lightHitStartAt <= 0L) lightHitStartAt = now
                    verifiedMillis += (now - lastLightCheckAt).coerceIn(0L, 2_000L)
                } else {
                    lightHitStartAt = 0L
                }
                lastLightCheckAt = System.currentTimeMillis()

                // 【节流】只在距上次发布 ≥500ms 时才写 State，
                // 避免每秒 15 次重组把 UI 线程压垮。
                // 但 peakLux 与 lightHit 的变化必须立即生效（影响判定），
                // 因此「状态翻转」时破例立即发布。
                val nowMs = System.currentTimeMillis()
                val shouldPublish = nowMs - lastStatePublishAt >= PUBLISH_INTERVAL_MS
                        || lightHit != prev.lightHit
                        || lux != prev.lightLux && lightHit
                if (shouldPublish) {
                    lastStatePublishAt = nowMs
                    _state.value = prev.copy(
                        lightLux = lux,
                        peakLux = max(prev.peakLux, lux),
                        lightHit = lightHit,
                        verifiedSeconds = (verifiedMillis / 1000L).toInt(),
                        samplingSeconds = ((nowMs - startTime) / 1000).toInt(),
                        elapsedSeconds = ((nowMs - startTime) / 1000).toInt()
                    )
                }
                // 光照变化时刷新定位。
                // 无光感设备上 lightHit 恒为 false，必须靠定时轮询，
                // 否则用户即使走到室外，定位灯也永远不亮。
                if (lightHit != prev.lightHit || isLocationStale()) refreshLocation()
            }

Sensor.TYPE_STEP_COUNTER -> {
          stepEventCount++
         val total = event.values.firstOrNull() ?: return
          if (baselineSteps < 0) {
    baselineSteps = total
                    _state.value = _state.value.copy(stepsDelta = 0)
    } else {
            val delta = (total - baselineSteps).toInt()
         _state.value = _state.value.copy(
     stepsDelta = max(0, delta),
   elapsedSeconds = ((System.currentTimeMillis() - startTime) / 1000).toInt()
          )
     }
            }
        }
    }

    /** 定位数据是否已陈旧（超过保鲜期则需要重取） */
    private fun isLocationStale(): Boolean {
  // 无光感设备 lightHit 恒为 false，只能靠定时轮询更新定位
  if (lightSensor == null) return true
        return System.currentTimeMillis() - lastLocationAt > LOCATION_REFRESH_MS
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

/**
     * 推进计时器。
     *
     * 【必须独立于传感器回调】原先elapsedSeconds 只在 onSensorChanged 里更新，
     * 若设备无光线/步数传感器就永远停在 0，导致：
     *   1) 手动确认降级模式永久无法结算
     *   2) 界面上的计时显示不出来
     * 现在由 SensorForegroundService 每秒调用一次，与传感器是否存在解耦。
     */
    fun tick() {
        if (!_state.value.verifying) return

        // 【重要】tick 也必须驱动有效阳光累加。
        // 国产 ROM 对光线传感器限流严重（照度不变时几十秒才回调一次），
        // 若只在 onSensorChanged 里累加，用户站在太阳下很久也无法结算。
        // 这里按真实经过时间补齐，保证采样时长与传感器事件密度无关。
        val now = System.currentTimeMillis()
        val s = _state.value
        if (s.lightHit) {
            if (lightHitStartAt <= 0L) lightHitStartAt = now
            verifiedMillis += (now - lastLightCheckAt).coerceIn(0L, 2_000L)
        } else {
            lightHitStartAt = 0L
        }
        lastLightCheckAt = now

        // 定位保鲜：tick 同样触发刷新，无光感设备靠这条链路更新位置
        if (isLocationStale()) refreshLocation()

        _state.value = _state.value.copy(
            elapsedSeconds = elapsedSecondsNow(),
            samplingSeconds = elapsedSecondsNow(),
            verifiedSeconds = (verifiedMillis / 1000L).toInt()
        )
    }

    /** 已采样时长（秒）。不依赖传感器回调，无传感器时同样正确。 */
    fun elapsedSecondsNow(): Int =
        if (startTime <= 0L) 0 else ((System.currentTimeMillis() - startTime) / 1000).toInt()

    /**
     * 汇总当前三路数据并给出验收结论。
     * @param targetMinutes 任务要求的目标真实阳光时长
     */
    fun evaluate(targetMinutes: Int): Verdict {
        val s = _state.value
   // 真实计时（不依赖传感器回调）
   val elapsed = elapsedSecondsNow()

// 手动降级：直接按计时结算（仅用于演示模式/无传感器设备）
        // 【修复 P0-1】原先用 s.elapsedSeconds，而该字段只在传感器回调里更新，
        // 无传感器设备上恒为 0 → 永远无法结算。改用真实计时。
        if (manualFallback) {
        val ok = elapsed >= targetMinutes * 60
      val remainMinutes = ((targetMinutes * 60 - elapsed) / 60).coerceAtLeast(0)
     return Verdict(
                passed = ok,
           lightLux = s.lightLux,
                outdoor = true,
                steps = s.stepsDelta,
   reason = if (ok) "手动确认模式：计时达标"
  else "手动确认模式：已计时 ${elapsed / 60} 分钟，还需 ${remainMinutes} 分钟"
       )
     }

        val lightOk = s.lightHit
        val stepsOk = !requireSteps || s.stepsDelta >= MIN_STEPS
        val lightMissing = !s.hasLightSensor

        // 【降级链】保证任一传感器缺失时都有可行路径，不会永久卡死：
        //  1. 有光感 + 有定位 → 三路交叉（最严格）
        //  2. 有光感 + 无定位 → 光照 + 步数（照度需明显高于阈值才算户外证据）
        //  3. 无光感+ 有定位 → 定位 + 步数
        //  4. 两者都无       → 仅按步数/计时（配合手动确认模式）
        val outdoorOk = if (lightMissing) {
            s.outdoorHit
        } else {
            val hasLoc = lastKnownLocation != null
            if (hasLoc) s.outdoorHit || s.peakLux >= OUTDOOR_STRONG_LUX
            else s.peakLux >= OUTDOOR_STRONG_LUX || s.lightHit && !requireSteps
        }

// 【修复 P0-3】原本 gatePassed 完全不看累计时长，
        // 导致用户 outdoors 站5 秒让灯变绿即可结算"真实阳光"，
        // 面板上写的"目标 30 分钟"从未被校验 —— 「验光不验时」变成「只验光不验任何时长」。
        // 现在把「累计达标时长」作为必要条件之一，与 UI 进度条口径一致。
  val durationOk = s.verifiedSeconds >= targetMinutes * 60

        // 【修复 P0-4】双传感器缺失的兜底分支原先无条件返回 true，
        // 会在进程被系统回收重建后（State 回到默认值 hasLightSensor=false）
        // 误触发这条放行路径。现在要求确实在采样中才允许走该兜底。
   val sampling = s.verifying && (s.hasLightSensor || s.hasStepSensor)

     val gatePassed = if (!sampling) {
   false       // 未在采样中 → 一律不通过（进程重建后不可凭空结算）
        } else if (lightMissing && !s.hasStepSensor) {
         // 双传感器都无：需开启手动确认模式，且按计时达标
            manualFallback && durationOk
      } else if (lightMissing) {
   outdoorOk && stepsOk && durationOk
        } else {
        lightOk && outdoorOk && stepsOk && durationOk
     }

     val reason = when {
         !sampling ->
     "采样未在进行中，请点「重新开始采样」后继续"
          !lightOk && lightMissing ->
          "本机没有光线传感器，且当前也没取到定位。请开启定位权限，" +
   "或在设置里开启「手动确认降级模式」"
         !durationOk && lightOk && outdoorOk && stepsOk ->
    "三路都命中了，但有效阳光还差 ${(targetMinutes * 60 - s.verifiedSeconds + 59) / 60} 分钟，继续保持户外"
            lightMissing && !s.hasStepSensor ->
      "本机缺少光线与步数传感器，已按计时结算"
    lightMissing && outdoorOk ->
       "本机无光线传感器，已用「户外定位 + 步数」验收"
            !lightOk ->
      "这枚币还没晒到太阳，走到窗边或户外再试一次吧"
    !outdoorOk ->
    "还没确认你走到室外（或当前设备缺少可用传感器），走到室外再验收吧"
            !stepsOk ->
     "还差一点点步数，动起来让天空之城看到你"
    !durationOk ->
      "有效阳光还差 ${(targetMinutes * 60 - s.verifiedSeconds + 59) / 60} 分钟"
      else -> "光照、位置、步数三路命中，真·户外确认"
        }

return Verdict(
            passed = gatePassed,
            lightLux = s.peakLux,
    outdoor = outdoorOk,
   steps = s.stepsDelta,
      reason = reason
        )
    }

    /**
     * 当前是否已具备结算条件。
     *
     * 【重要】UI 与 [evaluate] 必须使用同一套判定逻辑，
     * 否则会出现「界面提示可以结算、点击却失败」的预期错位。
     */
    fun canSettleNow(targetMinutes: Int, requireSteps: Boolean): Boolean {
        val s = _state.value
        if (manualFallback) return elapsedSecondsNow() >= targetMinutes * 60
        if (!s.verifying) return false
     val lightMissing = !s.hasLightSensor
     val stepsOk = !requireSteps || s.stepsDelta >= MIN_STEPS
      val outdoorOk = outdoorOk(s, lightMissing)
  val durationOk = s.verifiedSeconds >= targetMinutes * 60

        return when {
      lightMissing && !s.hasStepSensor -> false
 lightMissing -> outdoorOk && stepsOk && durationOk
            else -> s.lightHit && outdoorOk && stepsOk && durationOk
        }
    }

    /** 户外判定（UI 与 evaluate 共用，避免两处实现漂移） */
    private fun outdoorOk(s: State, lightMissing: Boolean): Boolean {
        if (lightMissing) return s.outdoorHit
        val hasLoc = lastKnownLocation != null
        return if (hasLoc) {
 s.outdoorHit || s.peakLux >= OUTDOOR_STRONG_LUX
    } else {
    s.peakLux >= OUTDOOR_STRONG_LUX || (s.lightHit && !requireSteps)
        }
    }

    /**
     * 户外启发式判定（定位兜底）。
     *
     * 三档降级，保证任何权限组合下任务都能完成：
     *  1. 有定位 + 光照达标 → 户外（最可信）
     *  2. 无定位权限但光照极高（≥ OUTDOOR_STRONG_LUX，接近正午直射）→ 判定户外
     *  3. 无定位权限、光照仅刚过阈值 → 仍按达标处理，避免用户卡死
     *     （此时只用光照判定，属于本App「验光不验时」的核心逻辑）
     */
    @SuppressLint("MissingPermission")
    fun isLikelyOutdoor(): Boolean {
        val s = _state.value
        // 无光感时只能依赖定位
        if (!s.hasLightSensor) return s.outdoorHit

        // 有光感：照度本身就是户外最强的证据
        if (!s.lightHit) return s.outdoorHit

        val loc = lastKnownLocation
        // 【关键】无定位权限时不能恒真，否则「三路交叉校验」退化为光照单路。
        // 改为要求照度明显高于验收阈值（OUTDOOR_STRONG_LUX），才算强日照证据。
        if (loc == null) return s.peakLux >= OUTDOOR_STRONG_LUX

        // 有定位：作为交叉校验，或照度极强时直接判定
        return s.outdoorHit || s.peakLux >= OUTDOOR_STRONG_LUX
    }

    @SuppressLint("MissingPermission")
 fun refreshLocation() {
        lastLocationAt = System.currentTimeMillis()
        val hasLight = _state.value.hasLightSensor
        val lightHit = _state.value.lightHit
        val loc = lastKnownLocation()
        lastKnownLocation = loc
        if (loc == null) {
            _state.value = _state.value.copy(outdoorHit = false, lat = null, lng = null, accuracy = null)
            return
        }
        // 【关键】无光感设备时 outdoorHit 不能依赖 lightHit，
        // 否则 lightHit 恒为 false → outdoorHit 恒为 false → 任务永久无法结算。
        // 有光感时仍要求「光达标 + 定位精度够」作为交叉验证。
        val outdoor = if (hasLight) lightHit && loc.accuracy < ACCURACY_THRESHOLD_M
        else loc.accuracy < ACCURACY_THRESHOLD_M
        _state.value = _state.value.copy(
            outdoorHit = outdoor,
            // 记录 GPS 数值供 UI 展示
            lat = loc.latitude,
            lng = loc.longitude,
            accuracy = loc.accuracy
        )
    }


/** 累计验证时长（分钟） */
    fun verifiedMinutes(): Int = _state.value.verifiedSeconds / 60

    companion object {
        /** 需要步数验收的最低增量 */
        const val MIN_STEPS = 60

/** 户外判定的照度水位 */
        const val OUTDOOR_LUX = 800

        /** 强日照水位：接近正午直射，无定位时也可据此判定户外 */
    const val OUTDOOR_STRONG_LUX = 2000

        /** 定位精度阈值（米）：小于此值视为定位可靠 */
   const val ACCURACY_THRESHOLD_M = 200f

        /** 定位数据的最长保鲜期（毫秒），超过则重新读取 */
        const val LOCATION_REFRESH_MS = 5_000L

        /**
     * 缓存定位的最大可接受年龄（毫秒）。
         * 超过说明是陈旧数据（如系统重启前的最后位置），应主动请求新定位。
         */
   const val MAX_LOCATION_AGE_MS = 60_000L
  }
    private fun lastKnownLocation(): Location? {
        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) return null

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return null

        // 【性能修复】这里只读取**系统缓存**，绝不主动请求定位。
        //
        // 【原来为什么卡顿崩溃】旧实现缓存过旧时会调用 requestSingleUpdate()，
        // 而那个函数用 CountDownLatch.await(3秒) 等待回调，
        // 同时又把回调注册在 getMainLooper()（主线程）——
        // 主线程在等自己收到回调，形成死锁，UI 线程被阻塞 3 秒。
        // 加上 tick 每秒都触发刷新定位，等于**每秒卡死 3 秒**，
        // 必然触发 ANR（应用无响应）被系统杀掉。
        //
        // 现在定位改为**纯异步**：由 requestLocationAsync 主动发起，
        // 回调直接写入 _state，本方法只做同步读取，不阻塞。
        return runCatching {
            lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
        }.getOrNull()
    }

    /**
     * 异步请求一次定位（**非阻塞**）。
     *
     * 【关键】回调线程用 [Looper.getMainLooper] 是安全的，
     * 因为这里只是**注册**并立即返回，不等待结果；
     * 定位到达后回调里更新状态，主线程不会卡住。
     *
     * 同时用 [locating] 保证同一时刻只有一个请求在飞，
     * 避免 tick 与传感器事件高频重复注册导致耗电与系统限流。
     */
    @SuppressLint("MissingPermission")
    private fun requestLocationAsync() {
        if (locating) return          // 已有请求在飞，丢弃本次
        val fineGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!fineGranted && !coarseGranted) return

        val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return

        val providers = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER
        ).filter { runCatching { lm.isProviderEnabled(it) }.getOrDefault(false) }
        if (providers.isEmpty()) return

        // 【实现要点】匿名对象在初始化时无法引用自身，
     // 因此把 listener 存进字段，回调里再从字段取出来反注册。
        locating = true
        val pending = object : android.location.LocationListener {
            override fun onLocationChanged(location: Location) {
        applyLocation(location)
     cancelLocationRequest()
   }
            override fun onProviderDisabled(provider: String) = cancelLocationRequest()
        override fun onProviderEnabled(provider: String) = Unit

   @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: android.os.Bundle?) {
      cancelLocationRequest()
      }
      }
        locationListener = pending

        runCatching {
            providers.forEach { p ->
      // minTime=30s 让系统控制频率；回调在主线程但我们只是注册、绝不等待
   runCatching {
  lm.requestLocationUpdates(p, 30_000L, 0f, pending, Looper.getMainLooper())
      }
            }
        }.onFailure { cancelLocationRequest() }

        // 兜底：6 秒后无论是否收到定位都清理标记，避免永久卡在 locating=true
        handler.postDelayed({ cancelLocationRequest() }, 6_000L)
    }

    /** 取消当前定位请求（幂等） */
    private fun cancelLocationRequest() {
        locating = false
        locationListener?.let { l ->
            runCatching {
        (context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager)
  ?.removeUpdates(l)
       }
        }
        locationListener = null
    }

    /** 应用一次定位结果并更新户外判定 */
    private fun applyLocation(loc: Location) {
        lastKnownLocation = loc
        lastLocationAt = System.currentTimeMillis()
        val hasLight = _state.value.hasLightSensor
        val lightHit = _state.value.lightHit
        val outdoor = if (hasLight) lightHit && loc.accuracy < ACCURACY_THRESHOLD_M
        else loc.accuracy < ACCURACY_THRESHOLD_M
        _state.value = _state.value.copy(
            outdoorHit = outdoor,
            lat = loc.latitude,
            lng = loc.longitude,
            accuracy = loc.accuracy
        )
    }

    private fun stopLocationUpdates(lm: LocationManager, listener: android.location.LocationListener) {
        locating = false
        runCatching { lm.removeUpdates(listener) }
    }

    }

/** 验收结论 */
data class Verdict(
    val passed: Boolean,
    val lightLux: Int,
    val outdoor: Boolean,
    val steps: Int,
    val reason: String
)
