package com.sunshine.exchange.data

import android.content.Context
import com.sunshine.exchange.core.DateUtils
import com.sunshine.exchange.core.EconomyRules
import com.sunshine.exchange.core.SolarTerms
import com.sunshine.exchange.core.TaskCardLibrary
import com.sunshine.exchange.core.WeatherService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Calendar
import kotlin.random.Random

/**
 * 业务编排层：把账本经济、阳光验证、天空之城成长串成闭环。
 * 对应需求文档「生态闭环：屏幕→币→真户外→天空之城成长→行为改变数据报告」。
 */
class SunshineRepository(
    private val context: Context,
    /** 暴露给 ViewModel 直接做只读查询，避免重复定义 DAO 访问层 */
    val db: SunshineDatabase
) {

    private val profileDao = db.userProfileDao()
    private val screenDao = db.screenRecordDao()
    private val ledgerDao = db.sunCoinLedgerDao()
    private val cardDao = db.taskCardDao()
    private val logDao = db.challengeLogDao()
    private val spotDao = db.buildingSpotDao()
    private val badgeDao = db.badgeDao()
    private val seasonDao = db.seasonBadgeDao()
    private val weatherDao = db.weatherCacheDao()
    private val reportDao = db.behaviorReportDao()

    // ---------------- 初始化 ----------------

    /**
     * 首次启动：写入默认档案、任务卡、徽章、节气表。
     *
     * 【幂等性】三类数据各自独立判空，而不是统一用「任务卡表是否为空」作为总开关——
     * 否则历史库升级或演示数据清库后，徽章/节气表会永久为空且无法自愈。
     */
    suspend fun bootstrap() = withContext(Dispatchers.IO) {
        val isNewInstall = profileDao.get() == null
        if (isNewInstall) {
            profileDao.upsert(UserProfile())
        }
        // 【无条件全量播种】card_card 主键固定、insertAll 用 REPLACE，
        // 重复插入是安全的。不要加 isEmpty() 判断——
        // 那会导致「旧版已装过的用户」永远拿不到新版新增的任务卡与徽章。
        cardDao.insertAll(TaskCardLibrary.all())
        // 【v5.1】先清理已下线功能的遗留数据，再播种当前定义。
        // 旧版用户库里还留着「还债达人」「节气初见」「二十四节气章」三行，
        // 不删的话它们会继续出现在徽章墙上。
        badgeDao.deleteByIds(listOf("debt_clear", "season_first", "season_all"))
        TaskCardLibrary.badges().forEach { badgeDao.insertIfAbsent(it) }
        SolarTerms.all().forEach { term ->
            if (seasonDao.term(term) == null) seasonDao.insertTerm(SeasonBadge(solarTerm = term))
        }
        // 新用户启动礼包：否则余额为 0，而币要靠屏幕时长慢慢结晶，
        // 新装用户第一天什么都做不了，等于「无法体验核心闭环」。
        //
        // 【幂等保护】只判断「账户余额为 0」，而不是「是否新装」——
        // 否则清除演示数据 / 数据库迁移重建后余额会变 0，
        // 用户将永久陷入「没有币 → 换不了卡 → 做不了任务 → 更拿不到币」的死循环。
        ensureStartupCoins()

        // 生成任何自定义卡都要走同一套播种流程，
        // 否则重启后新卡可能不显示（DB 被重建过的场景）
        ensureTodayRecord()
    }

/**
     * 确保账户至少有启动礼包币。
  *
     * 【v5.15】发放数额改为读取用户设置（`UserProfile.startupCoins`），
     * 而非原先写死的 `STARTUP_COINS` 常量。
     *
     * 幂等：仅当账本余额为 0 时才发放，且发放后余额必然 > 0，
     * 因此重复调用不会叠加。
     *
     * 【为什么用户改了设置不会立刻补币】
     * 本函数只在余额为 0 时触发。若用户已有余额，改设置不会凭空增加币——
     * 这是刻意的：否则「改一次设置刷一次币」，经济系统直接失控。
     * 设置项的实际作用场景是**新装/清库后的首次启动**。
     */
    private suspend fun ensureStartupCoins() {
        if (ledgerDao.balance() > 0) return
        val coins = (profileDao.get()?.startupCoins ?: STARTUP_COINS).coerceIn(0, 999)
        if (coins <= 0) return
   ledgerDao.insert(
    SunCoinLedger(
          date = DateUtils.today(),
                type = EconomyRules.TYPE_EARN,
            amount = coins,
      balance = coins,
     desc = "启动礼包：$coins 枚阳光币，先完成一张任务卡试试"
         )
      )
    }

    companion object {
        /**
         * 启动礼包币数的**兜底默认值**：用户档案缺失或字段异常时使用。
         * 【v5.15】原先这是唯一的发放来源，现在正常路径读 UserProfile.startupCoins。
         */
        const val STARTUP_COINS = 25

        /**
         * 自定义任务卡的 ID 起始段。
         * 内置卡使用 1~18（常规）与 101~124（节气），从 [CUSTOM_ID_BASE] 起
         * 单独分配一段，避免与内置卡主键冲突。
         */
        const val CUSTOM_ID_BASE = 1000
    }

    /**
     * 用户创建自定义任务卡。
     *
     * 【为什么开放这个功能】内置卡覆盖 42 种运动，但用户可能有
     * 「打羽毛球」「遛狗」「坐轮椅出行」等自己的运动方式，
     * 硬性限制会逼用户去凑合一张不合适的卡。
     *
     * 注意：自定义卡同样要走三重传感器验收，不能因为"自己创建"就跳过验证。
     */
suspend fun createCustomCard(
        name: String,
    desc: String,
        category: String,
        difficulty: Int,
        costCoins: Int,
        estMinutes: Int,
    /**
         * 【v5.7】验收光照门槛，由用户独立设置。
         * 原先固定写死为 `sunRewardMinutes = estMinutes`，导致两个概念被绑死——
         * 用户无法表达「运动 20 分钟，但要晒够 30 分钟才算数」这类需求。
         */
        sunRewardMinutes: Int,
    needSteps: Boolean
): Boolean = withContext(Dispatchers.IO) {
        val cleanName = name.trim()
        if (cleanName.isEmpty() || cleanName.length > 12) return@withContext false

// 同名卡片不允许重复创建
        val existingNames = cardDao.getAll().map { it.name }
        if (existingNames.contains(cleanName)) return@withContext false

        val id = cardDao.getAll()
         .filter { it.isCustom }
    .maxOfOrNull { it.id }
            ?.plus(1)
        ?: CUSTOM_ID_BASE
        val card = TaskCard(
            id = id,
            name = cleanName,
            desc = desc.trim().ifEmpty { "我自定义的户外运动" },
            category = category,
            difficulty = difficulty.coerceIn(1, 3),
            costCoins = costCoins.coerceIn(0, 30),
            estMinutes = estMinutes.coerceIn(5, 180),
            needSteps = needSteps,
            iconKey = "custom",
            // 【v5.7】改为使用用户设置的光照门槛，不再强制等于 estMinutes
      sunRewardMinutes = sunRewardMinutes.coerceIn(5, 90),
            isCustom = true
        )

        val balance = ledgerDao.balance()
        if (balance < card.costCoins) return@withContext false

        cardDao.upsert(card)
        ledgerDao.insert(
            SunCoinLedger(
                date = DateUtils.today(),
                type = EconomyRules.TYPE_SPEND,
                amount = -card.costCoins,
                balance = balance - card.costCoins,
                desc = "创建自定义任务卡「${card.name}」"
            )
        )
        val logId = logDao.insert(
            ChallengeLog(
                taskId = card.id,
                cardName = card.name,
                status = "EXCHANGED"
            )
        )
        logId > 0
    }

    /** 删除用户自定义任务卡（内置卡不可删） */
    suspend fun deleteCustomCard(id: Int): Boolean = withContext(Dispatchers.IO) {
        val card = cardDao.getById(id) ?: return@withContext false
        if (!card.isCustom) return@withContext false
        cardDao.deleteCustom(id)
        true
    }

    private suspend fun ensureTodayRecord() {
        val today = DateUtils.today()
        if (screenDao.getByDate(today) == null) {
            screenDao.insert(ScreenRecord(date = today))
        }
    }

    // ---------------- 屏幕时长与账本结算 ----------------

    /**
     * 兑换任务卡：扣除阳光币并生成「已兑换」状态的挑战记录。
     *
     * 【v5.0】已移除债务系统，这里只做纯扣币 + 建记录，不再有任何负债判定。
     */
    suspend fun exchangeCard(taskId: Int): ExchangeResult = withContext(Dispatchers.IO) {
        val card = cardDao.getById(taskId) ?: return@withContext ExchangeResult(false, "任务卡不存在")

        // 【v5.5】重复兑换拦截。
        //
        // 【为什么必须在这里挡】同一张卡在「已兑换待开始 / 验收中」状态下
        // 再兑换一次，会同时造成两个问题：
        //   1. 验证台出现两条同名任务，用户不知道该做哪个
        //   2. 阳光币被重复扣除，而第二条记录同样能通过验收重复结算
        // 所以这是数据一致性问题，不能只靠 UI 禁用按钮——
        // 快速连点、并发调用都能绕过 UI 层判断，必须在数据层兜底。
        val activeCount = logDao.activeCountOf(taskId)
        if (activeCount > 0) {
            return@withContext ExchangeResult(
                false,
                "「${card.name}」已在进行中，完成或放弃后再兑换"
            )
        }

        val balance = ledgerDao.balance()
        if (balance < card.costCoins) {
            return@withContext ExchangeResult(
                false,
                "还差 ${card.costCoins - balance} 币，晒 5 分钟真实阳光就 +2 币"
            )
        }

        val logId = logDao.insert(
            ChallengeLog(
                taskId = card.id,
                cardName = card.name,
                status = "EXCHANGED"
            )
        )
        ledgerDao.insert(
            SunCoinLedger(
                date = DateUtils.today(),
                type = EconomyRules.TYPE_SPEND,
                amount = -card.costCoins,
                balance = balance - card.costCoins,
                desc = "兑换「${card.name}」，消耗 ${card.costCoins} 枚阳光币"
            )
        )
        ExchangeResult(true, "已兑换「${card.name}」，到「进行中」开始验收", logId)
    }

    /**
     * 开始挑战：把已兑换的记录标记为「验收中」。
     *
     * 之后由 SunlightVerifier 驱动三重传感器验证，
     * 达标后由 completeChallenge 结算。
     */
    suspend fun startChallenge(logId: Long) = withContext(Dispatchers.IO) {
        // 【v5.5】先把其他「验收中」的记录退回待开始，保证全局至多一条 RUNNING。
        // 否则界面上running 只取第一条，多余的那条会既没有面板也没有放弃按钮，
        // 永久卡死且无法退款。
        logDao.demoteOtherRunning(logId, System.currentTimeMillis())
        logDao.markRunning(logId, System.currentTimeMillis())
    }

    /**
     * 记录当日真实阳光时长。
     *
     * 【v5.0】屏幕时长统计与债务系统已移除，这里只负责
     * 把当日累计阳光分钟数写入 screen_record 表，供报告与等级计算使用。
     * 阳光币的产出统一由 [addSunMinutes] 在验收通过时记账。
     */
    suspend fun syncSunRecord(): Int = withContext(Dispatchers.IO) {
        ensureTodayRecord()
        val today = DateUtils.today()
        val existing = screenDao.getByDate(today)
        val sunToday = existing?.sunMinutes ?: 0
        if (existing == null) {
            screenDao.insert(ScreenRecord(date = today))
        }
        sunToday
    }

    /**
     * 记录当日真实阳光时长（验收通过后调用），并按 0.4 币/分钟产出阳光币。
     *
     * 【v5.0 简化】原实现需先冲抵债务、剩余再存入；现在没有债务，
     * 全部收益直接进入余额。
     */
    suspend fun addSunMinutes(minutes: Int): SettlementResult = withContext(Dispatchers.IO) {
        val today = DateUtils.today()
        val existing = screenDao.getByDate(today)
        val sunTotal = (existing?.sunMinutes ?: 0) + minutes

        val coins = EconomyRules.repayCoins(minutes)
        var balance = ledgerDao.balance()
        balance += coins
        if (coins > 0) {
            ledgerDao.insert(
                SunCoinLedger(
                    date = today,
                    type = EconomyRules.TYPE_EARN,
                    amount = coins,
                    balance = balance,
                    desc = "真实阳光 $minutes 分钟，获得 $coins 枚阳光币"
                )
            )
        }

        screenDao.upsert(ScreenRecord(date = today, sunMinutes = sunTotal))

        SettlementResult(
            coinsGained = coins,
            sunTotalMinutes = sunTotal
        )
    }


    suspend fun completeChallenge(
        logId: Long,
        lightLux: Int,
        outdoor: Boolean,
        steps: Int,
        sunMinutes: Int,
        card: TaskCard
    ): CompletionResult = withContext(Dispatchers.IO) {
        logDao.update(
            id = logId,
            status = "DONE",
            finishedAt = System.currentTimeMillis(),
            lux = lightLux,
            outdoor = outdoor,
            steps = steps,
            sunMinutes = sunMinutes
        )

        val settlement = addSunMinutes(sunMinutes)

        // 点亮一座建筑（真户外才点亮）
        if (outdoor) {
            spotDao.insert(
                BuildingSpot(
                    kind = buildingKindFor(card),
                    litAt = System.currentTimeMillis(),
                    challengeId = logId
                )
            )
            // 每 3 次户外追加一朵云
            val buildings = spotDao.buildingCount()
            if (buildings % 3 == 0) {
                spotDao.insert(BuildingSpot(kind = "cloud", challengeId = logId))
            }
        }

        // 【v5.1】原「节气卡 → 解锁节气章」已删除：
        // 节气限定卡已从兑换中心下线，正常流程永远走不到这个分支，
        // 留着只会让读者误以为节气章体系仍在运行。
        val unlocked = evaluateBadges()

        CompletionResult(
            success = true,
            settlement = settlement,
            unlockedBadges = unlocked,
            litBuilding = outdoor,
            message = buildString {
                append("「${card.name}」验收通过，真实阳光 +$sunMinutes 分钟")
                if (settlement.coinsGained > 0) append("，还债/存入 ${settlement.coinsGained} 枚阳光币")
            }
        )
    }

    /** 放弃任务：返还一半币（不惩罚）。返回是否成功返还。 */
    suspend fun abandonChallenge(logId: Long, card: TaskCard?): Boolean = withContext(Dispatchers.IO) {
        // 【幂等校验】必须是 EXCHANGED（未开始）或 RUNNING（已中断）才允许放弃。
        // 少了这一判断，快速双击「放弃」会插入两条返还流水，凭空造币。
        val log = logDao.getById(logId) ?: return@withContext false
        if (log.status != "EXCHANGED" && log.status != "RUNNING") {
            return@withContext false
        }

        // 【v4.0】放弃只返还固定 1 枚，不再按定价比例返还。
        //
        // 【为什么改】新经济系统里「每张卡完成都能净赚」，
        // 若仍按一半返还，用户会故意「换高价卡 → 立刻放弃 → 退一半」，
        // 反复操作即可无成本刷币（换 15 币的卡退 7 枚，净赚 7 枚）。
        // 固定退 1 枚既保留了「不惩罚用户」的设计意图，
        // 又让放弃的真实成本 = 定价 - 1，套利不成立。
        val refund = EconomyRules.MIN_CARD_COST.coerceAtMost(card?.costCoins ?: 0)
        val balance = ledgerDao.balance()
        if (refund > 0) {
            ledgerDao.insert(
                SunCoinLedger(
                    date = DateUtils.today(),
                    type = EconomyRules.TYPE_SETTLE,
                    amount = refund,
                    balance = balance + refund,
                    desc = "放弃任务，返还 $refund 枚阳光币"
                )
            )
        }
        logDao.update(
            id = logId, status = "FAILED",
            finishedAt = System.currentTimeMillis(),
            lux = 0, outdoor = false, steps = 0, sunMinutes = 0
        )
        true
    }

    // ---------------- 徽章 ----------------

    private suspend fun evaluateBadges(): List<String> {
        val newly = mutableListOf<String>()
        val now = System.currentTimeMillis()
        val buildings = spotDao.buildingCount()
        val doneCount = logDao.doneCount()
        val allSuns = screenDao.getAll().sumOf { it.sunMinutes }

        suspend fun unlock(id: String, ok: Boolean) {
            if (!ok) return
            val already = badgeDao.isUnlocked(id)
            if (!already) {
                badgeDao.unlock(id, now)
                newly += badgeDao.nameOf(id) ?: id
            }
        }

        unlock("first_light", doneCount >= 1)
        unlock("tower_10", buildings >= 10)
        unlock("city_builder", buildings >= 30)
        unlock("sun_60", allSuns >= 60)
        unlock("sun_300", allSuns >= 300)
    unlock("seven_days", screenDao.getAll().count { it.sunMinutes > 0 } >= 7)

        // 【补上晨光收集者】早晨 8 点前完成过一次真户外验收即解锁。
        //
        // 【判定依据】用验收记录的 finishedAt（而非 startedAt）：
        // 徽章的语义是「你在这个时间点完成了真实户外」，看结束时间更贴切。
        //
        // 【为什么不能用光感阈值代替】很多人是「下班后到天黑前」出门，
        // 早晨这条路径本身稀缺，正是这个徽章想鼓励的行为——
        // 因此严格按「8 点前完成验收」判定，不做任何放宽。
        val morningDone = logDao.getAll().any { log ->
            val finished = log.finishedAt ?: return@any false
            // 只有真户外（户外判定通过）才算，室内拉伸不算
            if (log.status != "DONE" || !log.verifyOutdoor) return@any false
            val cal = java.util.Calendar.getInstance()
            cal.timeInMillis = finished
            cal.get(java.util.Calendar.HOUR_OF_DAY) < 8
        }
        unlock("early_bird", morningDone)
        return newly
    }

    // ---------------- 演示模式 ----------------

    /**
     * 生成演示数据（答辩必需）：模拟 14 天屏幕时长与真实阳光，
     * 并生成建筑、徽章、节气章，让天空之城立刻「有内容」。
     */
    suspend fun generateDemoData(days: Int = 14) = withContext(Dispatchers.IO) {
        val today = DateUtils.today()
        val random = Random(20260908)   // 固定种子，保证演示可复现

        // 【幂等】先清除上一轮演示数据再重建。
        // 否则用户连点几次会让余额与建筑数量成倍膨胀，污染真实账本且无法恢复。
        clearDemoRecords()

     // 1) 真实阳光时长（前半段少、后半段明显改善，体现成长曲线）
      for (i in (days - 1) downTo 0) {
   val date = DateUtils.daysAgo(i)
            val progress = 1f - i.toFloat() / days      // 0→1，越靠后越改善
       val sun = (6 + (44 * progress)).toInt() + random.nextInt(14) - 7
          screenDao.upsert(
    ScreenRecord(
    date = date,
          sunMinutes = sun.coerceAtLeast(0)
)
        )
        }

  // 2) 账本流水
        // 【v4.0】期初赠送启动礼包币，让演示一开始就能兑换任务卡
  // 【v5.15】改为读取用户设置（原先固定 25）
var balance = (profileDao.get()?.startupCoins ?: STARTUP_COINS).coerceIn(0, 999)
        ledgerDao.insert(
        SunCoinLedger(
 date = DateUtils.daysAgo(days),
            type = EconomyRules.TYPE_EARN,
     // 【v5.15】原先这里 amount 写死 20 而 balance 用 STARTUP_COINS(25)，
       // 两者不一致会让账本流水对不上（流水加 20，余额却是 25）。
    // 现统一使用用户设置的启动币数。
        amount = balance,
         balance = balance,
 desc = "新手礼包：$balance 枚阳光币"
          )
      )
        for (i in (days - 1) downTo 0) {
            val date = DateUtils.daysAgo(i)
            val rec = screenDao.getByDate(date) ?: continue
            val repay = EconomyRules.repayCoins(rec.sunMinutes)
            if (repay > 0) {
                balance += repay
                ledgerDao.insert(
                    SunCoinLedger(
                        date = date, type = EconomyRules.TYPE_REPAY, amount = repay,
                        balance = balance, desc = "真实阳光 ${rec.sunMinutes} 分钟，存入 $repay 枚"
                    )
                )
            }
        }


        // 4) 建筑天际线：16 座建筑 + 5 朵云
        val kinds = listOf("house", "tower", "yards", "house", "tower", "yards")
        repeat(16) { i ->
            spotDao.insert(
                BuildingSpot(
                    kind = kinds[i % kinds.size],
                    litAt = System.currentTimeMillis() - (16 - i) * 3600_000L
                )
            )
        }
        repeat(5) {
            spotDao.insert(BuildingSpot(kind = "cloud", litAt = System.currentTimeMillis()))
        }

        // 5) 徽章
        // 【v5.1】原「debt_clear 还债达人」已删除：债务系统整体下线，
        // 该徽章的达成条件（清零阳光债）已不存在，留着是永远打不开的死徽章。
        // 「season_first / season_all」同样删除：节气限定卡已从兑换中心下线，
        // 节气章无法再解锁，徽章会永久锁死。
        val allSuns = screenDao.getAll().sumOf { it.sunMinutes }
        val buildings = spotDao.buildingCount()
        listOf(
            "first_light" to (buildings >= 1),
            "tower_10" to (buildings >= 10),
            "sun_60" to (allSuns >= 60),
            "sun_300" to (allSuns >= 300),
            "early_bird" to true,
            "city_builder" to (buildings >= 30),
            "seven_days" to true
        ).forEach { (id, ok) ->
            if (ok && !badgeDao.isUnlocked(id)) badgeDao.unlock(id, System.currentTimeMillis())
        }

        // 6) 天气（模拟一周）
        val codes = listOf("SUNNY", "SUNNY", "CLOUDY", "RAINY", "SUNNY", "CLOUDY", "RAINY")
        for (i in 0 until 7) {
            val date = DateUtils.daysAgo(6 - i)
            val code = codes[i % codes.size]
            weatherDao.upsert(
                WeatherCache(
                    date = date,
                    weatherCode = code,
                    sunrise = "06:0${i % 3}",
                    sunset = "18:3${i % 6}",
                    isRainy = code == "RAINY"
                )
            )
        }

        // 7) 生成行为改变报告（比赛答辩用）
        generateBehaviorReport()
    }

    /** 清除演示数据（账本流水与天际线建筑），供用户一键恢复干净状态 */
    suspend fun clearDemoData() = withContext(Dispatchers.IO) {
        clearDemoRecords()
        // 【重要】清空账本后余额为 0，而启动礼包只在首次安装时发放过。
        // 若不补发，用户会陷入「余额 0 → 无法兑换 → 只能再生成演示 → 又被清空」的死循环。
if (ledgerDao.balance() <= 0) {
            // 【v5.15】读取用户设置的启动礼包数额
            val coins = (profileDao.get()?.startupCoins ?: STARTUP_COINS).coerceIn(0, 999)
            if (coins <= 0) return@withContext
     ledgerDao.insert(
         SunCoinLedger(
         date = DateUtils.today(),
            type = EconomyRules.TYPE_EARN,
      amount = coins,
        balance = coins,
          desc = "重新发放启动礼包：$coins 枚阳光币"
        )
          )
      }
    }

    /**
     * 清除演示写入的全部数据。
     *
     * 【修复 P0-4】原先只清账本与建筑，导致清除后出现
     *「天空之城空了一半、徽章全亮、报告曲线完好、等级仍是阳光城主」的矛盾画面。
     * 必须把徽章解锁、节气章、屏幕记录、行为报告一并清理。
     */
    private suspend fun clearDemoRecords() {
        ledgerDao.clearAll()
        spotDao.clearAll()
        badgeDao.clearUnlocked()
        seasonDao.clearUnlocked()
        screenDao.clearAll()
        reportDao.clearAll()
    }

    /** 重新计算等级等派生状态 */
    private suspend fun computeLevelSafely() {
        val records = screenDao.getAll()
        val week = records.filter { it.date >= DateUtils.daysAgo(6) }
        // 仅刷新缓存，避免在 Repository 层耦合 UI 状态
        week.sumOf { it.sunMinutes }
    }

    // ---------------- 报告 ----------------

    /**
     * 行为改变报告：使用前（最早 7 天）vs 使用中（最近 7 天）。
     */
    suspend fun generateBehaviorReport(): BehaviorReport = withContext(Dispatchers.IO) {
        val all = screenDao.getAll().sortedBy { it.date }
        val half = all.size / 2
        val before = all.take(if (half > 0) half else all.size)
        val after = all.drop(if (half > 0) half else 0).ifEmpty { all }

        val report = BehaviorReport(
            periodStart = all.firstOrNull()?.date ?: DateUtils.today(),
            periodEnd = all.lastOrNull()?.date ?: DateUtils.today(),
            // 【v5.0】屏幕时长统计已移除，行为报告只对比阳光维度
   avgSunBefore = before.map { it.sunMinutes }.average().takeIf { !it.isNaN() } ?: 0.0,
            avgSunAfter = after.map { it.sunMinutes }.average().takeIf { !it.isNaN() } ?: 0.0
        )
        reportDao.insert(report)
        report
    }

    /** 周报：本周阳光积累情况（屏幕时长统计已移除） */
    suspend fun weeklySummary(weekStart: String): WeeklySummary = withContext(Dispatchers.IO) {
   val weekEnd = DateUtils.plusDays(weekStart, 6)
      val records = screenDao.getRange(weekStart, weekEnd)
        val sun = records.sumOf { it.sunMinutes }
    val activeDays = records.count { it.sunMinutes > 0 }
        WeeklySummary(
            weekStart = weekStart,
            sunMinutes = sun,
   activeDays = activeDays,
            conversionCopy = buildCopy(sun)
        )
    }

    /** 本周阳光小结 */
    private fun buildCopy(sunMin: Int): String = when {
        sunMin == 0 -> "本周还没有真实阳光记录——从 10 分钟散步开始试试"
        sunMin < 60 -> "本周累计 $sunMin 分钟真实阳光，继续保持"
   sunMin < 180 -> "本周累计 $sunMin 分钟真实阳光，已经超过 3 小时了"
        else -> "本周累计 $sunMin 分钟真实阳光，天空之城又亮了不少"
    }

    /** 转化小结：屏幕时间被翻译成阳光的文案 */
    private fun buildCopy(screenMin: Int, sunMin: Int): String = when {
        screenMin == 0 && sunMin == 0 -> "还没有数据，去完成一次真户外吧"
        sunMin == 0 -> "本周屏幕 $screenMin 分钟，但天空之城还没有亮灯——先从 10 分钟散步开始"
        sunMin * 60 >= screenMin -> "本周 $screenMin 分钟屏幕时间，已被 $sunMin 分钟真实阳光覆盖，转化率超过 100%"
        else -> {
            val rate = (sunMin * 100 / screenMin.coerceAtLeast(1))
            "本周屏幕 $screenMin 分钟，已用 $sunMin 分钟真实阳光覆盖（$rate%），还差 ${screenMin - sunMin} 分钟"
        }
    }

    // ---------------- 辅助 ----------------

    private fun buildingKindFor(card: TaskCard): String = when (card.category) {
        "OUTDOOR" -> if (card.iconKey in listOf("run", "ball", "bike", "park")) "yards" else "tower"
        "MICRO" -> "house"
        "RELAX" -> "house"
        "SEASON" -> "tower"
        else -> "house"
    }

    /** 今日推荐任务卡：优先未兑换 + 难度适中 + 天气加成 */
    suspend fun recommendCards(limit: Int = 3): List<TaskCard> = withContext(Dispatchers.IO) {
        val balance = ledgerDao.balance()
        val all = cardDao.getAll()
        val weather = weatherDao.getByDate(DateUtils.today())

        all.asSequence()
            // 【v5.1】节气限定卡已从兑换中心下线，不再进入任何推荐位。
            // 卡数据仍保留在库中（便于日后恢复），但界面上完全不出现。
            .filter { it.category != "SEASON" }
            .sortedByDescending { card ->
                var score = 0
if (card.costCoins <= balance) score += 20   // 优先推现在就能兑换的
    // 【v5.14】雨天时优先推听雨漫步，但**不再承诺双倍收益**——
                // 双倍机制依赖真实天气，天气已回退为内置示例数据，该机制实际不成立。
      // 这里只做「场景相关性」排序，不涉及任何倍率。
    if (card.special == "WEATHER" && weather?.isRainy == true) score += 5
                if (card.sunRewardMinutes >= 20) score += 2
                if (card.difficulty == 2) score += 1
                // 同等条件下低价卡优先（新手先做便宜的更易成功）
                score += (12 - card.costCoins).coerceIn(0, 12)
                score
            }
            .take(limit)
            .toList()
    }

/**
     * 今日天气：优先读缓存，缺失时用内置示例数据 fallback（保证离线可用）。
  *
     * 【v5.13 回退】曾接入 Open-Meteo 真实接口，现已撤回，恢复纯离线可用。
     */
    suspend fun getWeatherToday(): WeatherCache = withContext(Dispatchers.IO) {
   val today = DateUtils.today()
     weatherDao.getByDate(today) ?: WeatherService.Weather.fallback().let {
            val cache = WeatherCache(
          date = today,
     weatherCode = it.code,
       sunrise = it.sunrise,
           sunset = it.sunset,
  isRainy = it.isRainy
            )
  weatherDao.upsert(cache)
     cache
        }
    }

    /** 更新用户设置 */
    suspend fun updateProfile(profile: UserProfile) = withContext(Dispatchers.IO) {
        profileDao.upsert(profile.copy(updatedAt = System.currentTimeMillis()))
    }
}

data class SettlementResult(
    val coinsGained: Int = 0,
    val sunTotalMinutes: Int = 0,
    val targetReached: Boolean = false,
    val targetMinutes: Int = 30
)

data class ExchangeResult(
    val success: Boolean,
    val message: String,
    val logId: Long = 0
)

data class CompletionResult(
    val success: Boolean,
    val settlement: SettlementResult = SettlementResult(),
    val unlockedBadges: List<String> = emptyList(),
    val litBuilding: Boolean = false,
    val message: String = ""
)

/** 周报数据（屏幕时长与债务已移除，只保留阳光维度） */
data class WeeklySummary(
    val weekStart: String,
    /** 本周累计真实阳光分钟数 */
    val sunMinutes: Int,
    /** 本周有阳光记录的天数 */
    val activeDays: Int,
    val conversionCopy: String
)