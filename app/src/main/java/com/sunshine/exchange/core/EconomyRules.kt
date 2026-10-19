package com.sunshine.exchange.core

import com.sunshine.exchange.data.SunCoinLedger

/**
 * 阳光币经济系统（v5.0 简化版）。
 *
 * 【设计原则：真户外是唯一印钞机】
 *
 * 用户想变富的唯一方式，是**真的走到户外晒太阳**：
 *   每 5 分钟有效阳光 = +2 币（0.4 币/分钟，向上取整）
 *
 * 屏幕时长不再产生任何收益，也不再形成预支负债——
 * 屏幕时间既不是奖励，也不是惩罚。激励方向与健康目标完全一致：
 * 想让账户变好，就必须出门。
 *
 * 【已移除的机制】原 v1~v4 版本的「周末预支 / 阳光债 / 还债」整套负债系统已下线。
 * 原因有两点：
 *   1. 既然户外是唯一收入来源，欠债总能很快还清甚至超额，机制形同虚设；
 *   2. 预支规则（每 30 分钟欠 0.5 币）对新用户是纯负担，缺乏正反馈。
 * 移除后叙事更干净：**只记录成长，不制造负债**。
 */
object EconomyRules {

    /**
     * 阳光币的唯一产出：每 5 分钟有效阳光 = 2 枚。
     *
     * 这是整个作品经济系统的核心常量——用户所有财富都来自真实户外。
     */
    const val COINS_PER_SUN_5MIN = 2

    /** 任务卡最低定价（新手门槛） */
    const val MIN_CARD_COST = 2

    /**
     * 任务卡定价基准：0.4 币/阳光分钟 × 80%。
     *
     * 定价约为奖励的 80% ⇒ 每完成一张卡净赚约 20%，
     * 保证「做运动能让账户变富」，又不会让余额无限膨胀。
     */
    const val PRICING_RATE = 0.32

    /**
     * 真实阳光分钟数 → 结算币数（唯一的印钞机）。
     *
     * 0.4 币/分钟，且**向上取整**——若用整除（sunMinutes / 5 * 2），
     * 5~9 分钟只能拿到 2 币，而短卡定价也是 2 币，完成后净赚 0，缺乏正反馈。
     */
    fun repayCoins(sunMinutes: Int): Int =
        if (sunMinutes <= 0) 0
        else (sunMinutes * COINS_PER_SUN_5MIN + 4) / 5

    /**
     * 按目标阳光时长推导合理定价。
     * 用于自定义任务卡与内置卡保持同一套经济逻辑。
     */
    fun suggestedCost(sunMinutes: Int): Int =
        maxOf(MIN_CARD_COST, (sunMinutes * PRICING_RATE).toInt())

    /**
     * 生成一次阳光收益的账本流水。
     *
     * 【简化说明】原实现有三段逻辑（工作日结晶 / 周末预支 / 户外还债），
     * 现在只剩「户外产生阳光收益」一段。
     *
     * @param runningBalance 当前余额
     */
    fun buildSunEntry(
        date: String,
        sunMinutes: Int,
        runningBalance: Int
    ): SunCoinLedger? {
        val coins = repayCoins(sunMinutes)
        if (coins <= 0) return null

        return SunCoinLedger(
            date = date,
            type = TYPE_EARN,
            amount = coins,
            balance = runningBalance + coins,
            desc = "真实阳光 $sunMinutes 分钟，获得 $coins 枚阳光币"
        )
    }

    /** 账本类型常量 */
    const val TYPE_EARN = "EARN"
    const val TYPE_REPAY = "REPAY"
    const val TYPE_SETTLE = "SETTLE"
    const val TYPE_SPEND = "SPEND"

    /**
     * 等级称号（近 7 日阳光量）。
     * 见需求 5. P1-3「本地排行榜 / 称号等级」
     */
    fun levelFor(sunMinutes7d: Int): Level = when {
        sunMinutes7d >= 600 -> Level("阳光城主", "sky_tower", 5)
        sunMinutes7d >= 420 -> Level("天空领航员", "sky_house", 4)
        sunMinutes7d >= 240 -> Level("云端旅人", "cloud", 3)
        sunMinutes7d >= 120 -> Level("追光学徒", "seedling", 2)
        else -> Level("见习追光者", "sunrise", 1)
    }
}