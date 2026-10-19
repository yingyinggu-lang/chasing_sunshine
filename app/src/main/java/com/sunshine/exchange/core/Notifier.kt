package com.sunshine.exchange.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.sunshine.exchange.MainActivity
import com.sunshine.exchange.SunshineApp

/**
 * 通知工具：全程零批评、只做交易与成长提示。
 * 对应需求 5. P1-1「屏幕连续使用温柔提醒」、P1-2「当日户外目标达成」。
 */
object Notifier {

    private const val ID_NUDGE = 1001
    private const val ID_SETTLE = 1002
    private const val ID_GOAL = 1003

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(SunshineApp.CHANNEL_SUNSHINE) != null) return
        val channel = NotificationChannel(
            SunshineApp.CHANNEL_SUNSHINE,
            "阳光助手",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply { description = "连续使用提醒与每日目标达成通知" }
        nm.createNotificationChannel(channel)
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

    private fun buildPendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    /** 连续使用温柔提醒：一键跳转开始一项室外任务 */
    fun showNudge(context: Context, screenMinutes: Int) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, SunshineApp.CHANNEL_SUNSHINE)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("已连续用屏幕 $screenMinutes 分钟")
            .setContentText("要不要去晒 10 分钟太阳？天空之城在等你点亮一座楼")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "已连续用屏幕 $screenMinutes 分钟。\n去完成一项户外任务吧——晒 5 分钟真实阳光就是 2 枚币。"
                )
            )
            .setContentIntent(buildPendingIntent(context))
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(ID_NUDGE, notification)
        }
    }

    /** 周日结算：温和提醒，不惩罚 */
    fun showSettlement(context: Context, title: String, body: String) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, SunshineApp.CHANNEL_SUNSHINE)
            .setSmallIcon(android.R.drawable.ic_menu_recent_history)
            .setContentTitle(title)
            .setContentText(body)
            .setContentIntent(buildPendingIntent(context))
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(ID_SETTLE, notification)
        }
    }

    /** 每日户外目标达成庆祝 */
    fun showGoalReached(context: Context, minutes: Int) {
        if (!canNotify(context)) return
        ensureChannel(context)
        val notification = NotificationCompat.Builder(context, SunshineApp.CHANNEL_SUNSHINE)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentTitle("今日目标达成：真实阳光 $minutes 分钟")
            .setContentText("天空之城又长高了一点，去看看今天点亮了什么")
            .setContentIntent(buildPendingIntent(context))
            .setAutoCancel(true)
            .build()
        runCatching {
            NotificationManagerCompat.from(context).notify(ID_GOAL, notification)
        }
    }
}