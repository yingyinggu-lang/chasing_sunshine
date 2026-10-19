package com.sunshine.exchange.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 用户档案：昵称、每日真实阳光目标、提醒开关等。
 * 对应需求文档 7. UserProfile
 */
@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,
    val nickname: String = "追光者",
    /** 每日真实阳光目标（分钟） */
    val dailySunTargetMin: Int = 30,
    val reminderEnabled: Boolean = true,
    /** 手动确认降级开关（答辩演示 / 无光感设备时使用） */
    val manualVerifyFallback: Boolean = false,
    /** 光照阈值（lux）。室内灯光≈100lx，户外阴天≈1000+lx */
    val lightThresholdLux: Int = 1000,
    /**
     * 【v5.15】新用户启动礼包阳光币数（可在设置中调整）。
     *
     * 原来这个值硬编码在 `SunshineRepository.STARTUP_COINS` 常量里，
     * 用户无法调整。答辩演示时若想给评委一个不同的起始额度，
     * 原本只能改代码重新打包。
     */
    val startupCoins: Int = 25,
    val onboardingDone: Boolean = false,
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * 每日屏幕时长汇总。
 * 对应需求文档 7. ScreenRecord
 */
@Entity(tableName = "screen_record")
data class ScreenRecord(
    @PrimaryKey val date: String,          // yyyy-MM-dd
    /** 当日真实阳光时长（分钟）。屏幕时长统计已移除，只保留阳光维度。 */
    val sunMinutes: Int = 0
)

/**
 * 阳光币账本流水。
 * type: EARN 阳光收益 / REPAY 户外还债 / SETTLE 放弃返还 / SPEND 兑换任务卡
 * 对应需求文档 7. SunCoinLedger
 */
@Entity(tableName = "sun_coin_ledger")
data class SunCoinLedger(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: String,
    val type: String,
    val amount: Int,                // 正数=收入，负数=支出/负债
    val balance: Int,               // 记账后余额
    val desc: String = ""
)


/**
 * 任务卡定义。
 * 对应需求文档 7. TaskCard
 * category: MICRO 微运动 / OUTDOOR 户外探索 / RELAX 放松 / SEASON 节气限定
 */
@Entity(tableName = "task_card")
data class TaskCard(
    @PrimaryKey val id: Int,
    val name: String,
    val desc: String,
    val category: String,
    /** 难度 1~3 */
    val difficulty: Int,
    val costCoins: Int,
    val estMinutes: Int,
    /** 验收是否必须检测到步数增量 */
    val needSteps: Boolean,
    val iconKey: String,
    /** WEATHER / SEASON / null —— 决定是否受天气或节气加成 */
    val special: String? = null,
    /** 关联节气（仅 SEASON 卡） */
    val solarTerm: String? = null,
    val sunRewardMinutes: Int = 15,
    /** true = 用户自己创建的卡片（可删除，内置卡不可删） */
    val isCustom: Boolean = false
)

/**
 * 挑战记录：兑换 → 进行中 → 完成 的全流程状态与验收数据。
 * 对应需求文档 7. ChallengeLog
 * status: EXCHANGED / RUNNING / DONE / FAILED
 */
@Entity(tableName = "challenge_log")
data class ChallengeLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val taskId: Int,
    val cardName: String,
    val status: String,
    val startedAt: Long = System.currentTimeMillis(),
    val finishedAt: Long? = null,
    /** 验收记录的峰值照度 */
    val verifyLightLux: Int = 0,
    /** 是否判定为户外（定位/活动识别） */
    val verifyOutdoor: Boolean = false,
    /** 任务期间步数增量 */
    val verifySteps: Int = 0,
    val sunMinutesEarned: Int = 0
)

/**
 * 天空之城建筑点位：每次真户外点亮一座。
 * 对应需求文档 7. BuildingSpot
 */
@Entity(tableName = "building_spot")
data class BuildingSpot(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** house 小屋 / tower 高楼 / yards 场馆 / cloud 云朵 */
    val kind: String,
    val litAt: Long = System.currentTimeMillis(),
    val challengeId: Long? = null
)

/**
 * 徽章定义与解锁。
 * 对应需求文档 7. Badge / BadgeUnlocked
 */
@Entity(tableName = "badge")
data class Badge(
    @PrimaryKey val id: String,
    val name: String,
    val desc: String,
    val iconKey: String,
    val unlockedAt: Long? = null
)

/**
 * 二十四节气章。
 * 对应需求文档 7. SeasonBadge
 */
@Entity(tableName = "season_badge")
data class SeasonBadge(
    @PrimaryKey val solarTerm: String,     // 24 节气名
    val unlockedAt: Long? = null,
    val taskId: Int? = null
)

/**
 * 天气缓存（含日出日落）。
 * 对应需求文档 7. WeatherCache
 */
@Entity(tableName = "weather_cache")
data class WeatherCache(
    @PrimaryKey val date: String,
    /** 晴 / 多云 / 雨 / 雪 / 阴 */
    val weatherCode: String = "SUNNY",
    val sunrise: String = "06:00",
    val sunset: String = "18:00",
    /** 是否为雨天（用于「听雨漫步」等天气限定卡的标记） */
    val isRainy: Boolean = false
)

/**
 * 行为改变报告：使用前（首次 7 天）vs 使用中。
 * 对应需求文档 7. BehaviorReport
 */
@Entity(tableName = "behavior_report")
data class BehaviorReport(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val periodStart: String,
    val periodEnd: String,
    val avgScreenBefore: Double = 0.0,
    val avgScreenAfter: Double = 0.0,
    val avgSunBefore: Double = 0.0,
    val avgSunAfter: Double = 0.0,
    val debtClearRate: Double = 0.0,
    val generatedAt: Long = System.currentTimeMillis()
)