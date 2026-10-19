package com.sunshine.exchange.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * 系统权限检测（v5.2 提取为共享工具）。
 *
 * 【为什么从 MainActivity 抽出来】
 * 原先这三个函数是 MainActivity 的 private 扩展，MainViewModel 用不了。
 * 而 v5.2 要求 ViewModel 持有权限状态并在 refreshPermissions() 里读取，
 * 于是两边都需要同一套判定逻辑。抽到这里，两边共用一份，不会出现
 * 「ViewModel 判 true、界面判 false」这种自相矛盾的情况。
 *
 * 【重要】这些函数是**纯函数**：返回当前授权结果，但结果不是 Compose 的 State。
 * 想让界面跟着变，必须把返回值包进 StateFlow 并由 UI 侧 collect，
 * 这正是 MainViewModel.permissionState 的作用。
 */

/** 定位权限是否已授予（fine / coarse 任一即可） */
fun Context.hasLocationPermissionCompat(): Boolean {
    val fine = ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
    if (fine) return true
    return ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACCESS_COARSE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED
}

/** 通知权限（Android 13 以下系统会临时授权，视为已授予） */
fun Context.hasNotificationPermissionCompat(): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        this, Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

/**
 * 身体活动识别权限（Android 10+）。
 *
 * 【为什么必须单独判】没有它，`Sensor.TYPE_STEP_COUNTER` 不返回数据，
 * 步数会一直是 0，「需步数」类任务卡永远验收不通过。
 * 它与定位是两套独立权限，勾选定位时系统不会一并询问。
 */
fun Context.hasActivityRecognitionPermissionCompat(): Boolean {
    // Android 10 以下该权限随定位一起授予，无需单独申请
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return true
    return ContextCompat.checkSelfPermission(
        this, Manifest.permission.ACTIVITY_RECOGNITION
    ) == PackageManager.PERMISSION_GRANTED
}