package com.sunshine.exchange.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.IBinder
import com.sunshine.exchange.MainActivity

/**
 * 传感器采样前台服务。
 *
 * 【为什么必须有这个】
 * MIUI / HyperOS / ColorOS / OriginOS 等国产 ROM 对后台传感器管控极严：
 * App 一旦切到后台，`TYPE_LIGHT` 与 `TYPE_STEP_COUNTER` 会**直接停止回调**，
 * 表现为「感光中…」面板里光照恒为 0、步数恒为 0，且不给出任何报错。
 *
 * 系统提供的标准解法是前台服务（foreground service）：
 * 保持进程存活并让系统感知到"用户正在主动使用传感器"，传感器才会持续输出。
 * 这也是健康类 App（手环、运动记录）的通行做法。
 *
 * 通知栏会显示一条常驻的采样提示，用户可随时停止——不静默采集。
 */
class SensorForegroundService : Service() {

    companion object {
        private const val CHANNEL_ID = "sunshine_sensor_sampling"
        private const val NOTIFICATION_ID = 2001

        /** 采样计时线程（必须在 onDestroy 里 quitSafely，否则线程泄漏） */
        private var ticker: HandlerThread? = null

        /** tick 用的 Handler，绑定在 ticker 线程上 */
        private var tickHandler: Handler? = null

        /** 用户是否正在采样——决定服务是否该继续活着 */
        @Volatile
        private var samplingActive: Boolean = false

        /**
         * 每秒回调钩子。由 ViewModel 在启动采样时注入（推进计时与 UI 刷新），
         * 服务停止时置空，避免泄漏。
         */
        @Volatile
        var onSensorTick: (() -> Unit)? = null

        /** 开始采样（由「开始验收」触发） */
        fun start(context: Context) {
            samplingActive = true
            val intent = Intent(context, SensorForegroundService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure {
                // Android 12+ 从后台启动前台服务会抛异常；
                // 此处不崩溃，降级为"仅前台采样"（用户不切后台时仍可正常工作）
                samplingActive = false
                android.util.Log.w(
                    "SunshineSensor",
                    "启动前台服务失败（可能处于后台），降级为仅前台采样",
                    it
                )
            }
        }

        /** 停止采样（结算或放弃后调用） */
        fun stop(context: Context) {
            samplingActive = false
            onSensorTick = null
            stopTicker()
            runCatching {
                context.stopService(Intent(context, SensorForegroundService::class.java))
            }
        }

        /** 停止计时线程（幂等，可在多处调用） */
        private fun stopTicker() {
            tickHandler?.removeCallbacksAndMessages(null)
            tickHandler = null
            ticker?.quitSafely()
            ticker = null
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * 【关键顺序】必须**先** startForeground，**再**启动计时线程。
     *
     * 【为什么】Android 要求前台服务在启动后 5 秒内调用 startForeground，
     * 否则抛 ForegroundServiceDidNotStartInTimeException 直接杀进程（表现为闪退）。
     * 原实现把 startTicker() 放在 startForeground() 之前，
     * 一旦线程启动稍慢（ROM 繁忙时很常见）就会超时崩溃。
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createChannel()
        val notification = buildNotification()

        // ① 先宣告前台，占住系统的 5 秒窗口
        var started = false
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFICATION_ID,
                    notification,
                    // 【修复】原先用 FOREGROUND_SERVICE_TYPE_HEALTH，
                    // 但 Android 14 对 health 类型有严格资格校验（需 BODY_SENSORS 等权限），
                    // 未满足时直接抛 SecurityException 导致闪退。
                    // 改为 specialUse，与 Manifest 声明一致，各版本均可用。
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
            started = true
        }.onFailure {
            android.util.Log.w("SunshineSensor", "前台服务类型声明失败，退回兼容模式", it)
            runCatching { startForeground(NOTIFICATION_ID, notification) }
                .onSuccess { started = true }
        }

        if (!started) {
            // 兜底：即使前台服务完全不可用，也必须停掉自己，
            // 否则系统会在超时后抛 ForegroundServiceDidNotStartInTimeException 导致闪退
            android.util.Log.w("SunshineSensor", "前台服务无法启动，停止自身以避免闪退")
            stopSelf()
            return START_NOT_STICKY
        }

        // ② 前台身份已确立，现在才安全地启动计时线程
        startTicker()

        // START_STICKY：进程被系统回收后自动重建，保证采样不中断
        return START_STICKY
    }

    /**
     * 每秒推进一次采样计时。
     *
     * 【为什么必须有】采样时长原先只在传感器回调里更新，设备若无传感器就永远是 0，
     * 会导致「手动确认降级模式」永远无法结算、界面也显示不出计时进度。
     * 用独立定时器把计时与传感器解耦。
     *
     * 【修复线程泄漏】原先用 `while (!isInterrupted) { runCatching { sleep(1000) } }`，
     * 但 `runCatching` 会把 `sleep` 抛的 InterruptedException **吞掉**，
     * 导致循环条件永远为 true，`quitSafely()` 根本退不出来——
     * 每开一次验证台就泄漏一个线程，最终导致卡顿甚至 OOM 闪退。
     * 现改为 Handler.postDelayed，由系统管理消息队列，quitSafely 可正常生效。
     */
    private fun startTicker() {
        if (ticker != null) return
        val thread = HandlerThread("SunshineTicker").apply { start() }
        ticker = thread
        val handler = Handler(thread.looper)
        tickHandler = handler

        handler.postDelayed(object : Runnable {
            override fun run() {
                if (!samplingActive) return   // 用户已停止，不再续时
                runCatching { onSensorTick?.invoke() }
                handler.postDelayed(this, 1000L)
            }
        }, 1000L)
    }

    override fun onDestroy() {
        stopTicker()
        onSensorTick = null
        super.onDestroy()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "阳光采样",
            NotificationManager.IMPORTANCE_LOW     // 低优先级：不发出声音、不打扰
        ).apply {
            description = "进行户外任务时保持光线与步数传感器持续采样"
            setShowBadge(false)
        }
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // minSdk = 26，两参构造函数在此版本起可用，无需分支
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("正在验证真实阳光")
   .setContentText("保持此通知开启，光线与步数才会持续采样")
      .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(contentIntent)
    .setOngoing(true)
      // 静默通知：channel 已是 IMPORTANCE_LOW，此处再关一次声音/震动
 .setDefaults(0)
      .build()
    }
}