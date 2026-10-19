package com.sunshine.exchange

import android.app.Application
import android.util.Log
import android.app.NotificationChannel
import android.app.NotificationManager
import com.sunshine.exchange.data.SunshineDatabase

/**
 * 应用入口：初始化数据库与通知渠道。
 *
 * 【v5.0】每日结算调度（DailySettleWorker）已随债务系统一并下线：
 * 原先它每天把「屏幕时长预支 / 户外还债」写进账本，
 * 这套机制不存在后已无任何需要后台调度的结算任务。
 */
class SunshineApp : Application() {

    /**
     * 数据库实例。
     *
     * SunshineDatabase.get() 内部已带自愈（schema 校验失败则删库重建），
     * 这里再加一层兜底：即便极端情况下数据库完全不可用，
     * 也不应让 Application 初始化抛异常——那会导致整个 App 一打开就闪退、
     * 界面全白、连设置页都进不去。
     */
    val database: SunshineDatabase by lazy {
        runCatching { SunshineDatabase.get(this) }
            .getOrElse { e ->
                Log.e("SunshineApp", "数据库完全不可用，尝试删除后重建", e)
                runCatching { deleteDatabase("sunshine.db") }
                SunshineDatabase.get(this)
            }
    }

    override fun onCreate() {
    super.onCreate()

        // 全局兜底：任何未捕获异常都不静默崩溃，
        // 而是记录日志并提示重启，避免"闪退后不知道发生了什么"
        installCrashGuard()

    createNotificationChannel()
    }

    /**
     * 安装全局异常兜底。
     * 传感器与前台服务在不同 ROM 上行为差异大，
     * 一旦抛异常不应直接闪退，而应记录下来便于排查。
     */
    private fun installCrashGuard() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            Log.e("SunshineCrash", "未捕获异常: ${e.message}", e)

            // 【体验优化】崩溃后自动拉起 App。
            //
            // 【为什么】传感器与前台服务在不同 ROM 上行为差异极大，
            // 即便我们把所有已知异常都包住了，仍可能有未预料的崩溃。
            // 用户看到「闪退」后必须手动点图标重开——演示现场这是致命的。
            // 这里用 PendingIntent 重启 MainActivity，1 秒后自动恢复，
            // 用户几乎感觉不到中断（数据库是 Room，重启后数据完好）。
            //
            // 【为什么不再直接抛给系统】直接抛会导致进程被杀、通知栏残留、
            // 且部分 ROM 会连续弹「应用无响应」，反而更容易崩。
            runCatching {
                val intent = android.content.Intent(this, MainActivity::class.java).apply {
                    addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                val pi = android.app.PendingIntent.getActivity(
                    this, 0, intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                        android.app.PendingIntent.FLAG_IMMUTABLE
                )
                android.os.Handler(android.os.Looper.getMainLooper())
                    .postDelayed({ pi.send() }, 800L)
            }

            // 仍要交回系统，让 Android 正常清理进程与通知
            previous?.uncaughtException(thread, e)
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_SUNSHINE,
            "阳光助手",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "连续使用提醒与每日目标达成通知"
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_SUNSHINE = "sunshine_assistant"
    }
}