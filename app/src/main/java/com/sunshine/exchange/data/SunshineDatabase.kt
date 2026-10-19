package com.sunshine.exchange.data

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun observe(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun get(): UserProfile?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfile)
}

@Dao
interface ScreenRecordDao {
    @Query("SELECT * FROM screen_record ORDER BY date DESC")
    fun observeAll(): Flow<List<ScreenRecord>>

    @Query("SELECT * FROM screen_record WHERE date = :date")
    suspend fun getByDate(date: String): ScreenRecord?

    @Query("SELECT * FROM screen_record WHERE date BETWEEN :from AND :to ORDER BY date ASC")
    suspend fun getRange(from: String, to: String): List<ScreenRecord>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: ScreenRecord)

    /** 仅在当天记录不存在时插入（避免覆盖已有阳光时长） */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(record: ScreenRecord)

    /** 清空所有屏幕时长记录（清除演示数据时用） */
    @Query("DELETE FROM screen_record")
    suspend fun clearAll()

    @Query("SELECT * FROM screen_record ORDER BY date ASC")
    suspend fun getAll(): List<ScreenRecord>
}

@Dao
interface SunCoinLedgerDao {
    @Query("SELECT * FROM sun_coin_ledger ORDER BY id DESC")
    fun observeAll(): Flow<List<SunCoinLedger>>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM sun_coin_ledger")
    fun observeBalance(): Flow<Int>

    @Query("SELECT COALESCE(SUM(amount), 0) FROM sun_coin_ledger")
    suspend fun balance(): Int

    @Insert
    suspend fun insert(entry: SunCoinLedger)

    /** 清空账本（供演示数据重新生成时使用，避免反复叠加） */
    @Query("DELETE FROM sun_coin_ledger")
    suspend fun clearAll()
}


@Dao
interface TaskCardDao {
    @Query("SELECT * FROM task_card ORDER BY id ASC")
    fun observeAll(): Flow<List<TaskCard>>

    @Query("SELECT * FROM task_card ORDER BY id ASC")
    suspend fun getAll(): List<TaskCard>

    @Query("SELECT * FROM task_card WHERE id = :id")
    suspend fun getById(id: Int): TaskCard?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(cards: List<TaskCard>)

    /**
     * 插入或更新单张卡（用于用户创建自定义卡）。
     * 自定义卡需要能覆盖同 id 的旧内容，因此单独用 REPLACE。
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(card: TaskCard)

    /** 只删除用户自定义卡，内置卡不可删除 */
    @Query("DELETE FROM task_card WHERE id = :id AND isCustom = 1")
    suspend fun deleteCustom(id: Int)
}

@Dao
interface ChallengeLogDao {
    @Query("SELECT * FROM challenge_log ORDER BY startedAt DESC")
    fun observeAll(): Flow<List<ChallengeLog>>

    @Query("SELECT * FROM challenge_log WHERE status IN ('EXCHANGED','RUNNING') ORDER BY startedAt DESC")
    fun observeActive(): Flow<List<ChallengeLog>>

    @Query("SELECT * FROM challenge_log WHERE status = 'DONE' ORDER BY finishedAt DESC")
    fun observeDone(): Flow<List<ChallengeLog>>

    @Query("SELECT * FROM challenge_log ORDER BY startedAt DESC")
    suspend fun getAll(): List<ChallengeLog>

    @Query("SELECT COUNT(*) FROM challenge_log WHERE status = 'DONE'")
    suspend fun doneCount(): Int

    /**
     * 查询某张任务卡是否已有「进行中」的记录（已兑换待开始 或 验收中）。
     *
     * 【v5.5】用于拦截重复兑换：同一张卡在未完成前不允许再兑换一次。
     * 否则用户可以对同一张卡连点多次，每次都扣币并在验证台生成一条同名记录，
     * 导致验证台出现重复任务名、且阳光币被无意义地重复消耗。
     */
    @Query("SELECT COUNT(*) FROM challenge_log WHERE taskId = :taskId AND status IN ('EXCHANGED','RUNNING')")
    suspend fun activeCountOf(taskId: Int): Int

    @Query("SELECT * FROM challenge_log WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ChallengeLog?

    @Insert
    suspend fun insert(log: ChallengeLog): Long

    @Query("UPDATE challenge_log SET status = :status, finishedAt = :finishedAt, verifyLightLux = :lux, verifyOutdoor = :outdoor, verifySteps = :steps, sunMinutesEarned = :sunMinutes WHERE id = :id")
    suspend fun update(
        id: Long,
        status: String,
        finishedAt: Long,
        lux: Int,
        outdoor: Boolean,
        steps: Int,
        sunMinutes: Int
    )

    @Query("UPDATE challenge_log SET status = 'RUNNING', startedAt = :startedAt, verifyLightLux = 0, verifyOutdoor = 0, verifySteps = 0 WHERE id = :id")
    suspend fun markRunning(id: Long, startedAt: Long)

    /**
     * 把指定任务之外的所有「验收中」记录退回「已兑换待开始」。
     *
     * 【v5.5】防多任务同时 RUNNING 导致卡死。
     *
     * 【为什么需要】验证台取running 时用的是 `firstOrNull { status == "RUNNING" }`，
     * 只认第一条。若库里存在两条 RUNNING，第二条既不会被渲染成面板、
     * 也拿不到「放弃」按钮，等于永久卡死、无法完成也无法退款。
     *
     * 每次开始新验收前先调用一次，保证全局至多一条 RUNNING。
     */
    @Query("UPDATE challenge_log SET status = 'EXCHANGED', startedAt = :startedAt WHERE status = 'RUNNING' AND id != :keepId")
    suspend fun demoteOtherRunning(keepId: Long, startedAt: Long)
}

@Dao
interface BuildingSpotDao {
    @Query("SELECT * FROM building_spot ORDER BY litAt ASC")
    fun observeAll(): Flow<List<BuildingSpot>>

    @Query("SELECT COUNT(*) FROM building_spot WHERE kind != 'cloud'")
    suspend fun buildingCount(): Int

    @Insert
    suspend fun insert(spot: BuildingSpot)

    /** 清空天际线（演示数据重生成时用） */
    @Query("DELETE FROM building_spot")
    suspend fun clearAll()
}

@Dao
interface BadgeDao {
    @Query("SELECT * FROM badge ORDER BY id ASC")
    fun observeAll(): Flow<List<Badge>>

    @Query("SELECT COUNT(*) FROM badge")
    suspend fun count(): Int

    /** 清除全部徽章解锁状态（清除演示数据时用） */
    @Query("UPDATE badge SET unlockedAt = NULL")
    suspend fun clearUnlocked()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIfAbsent(badge: Badge)

    /**
     * 删除已下线的徽章记录。
     *
     * 【为什么需要】徽章墙读的是整张 badge 表，旧版装过的用户库里
     * 还留着 debt_clear / season_first / season_all 三行。只改定义文件不够——
     * 老行不会自动消失，会在界面上继续显示为「还债达人」等已下线内容。
     * 播种时主动删一次即可，无需改表结构（不涉及 schema 变更，不用升版本）。
     */
    @Query("DELETE FROM badge WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<String>)

    @Query("SELECT unlockedAt IS NOT NULL FROM badge WHERE id = :id LIMIT 1")
    suspend fun isUnlocked(id: String): Boolean

    @Query("SELECT name FROM badge WHERE id = :id LIMIT 1")
    suspend fun nameOf(id: String): String?

    @Query("UPDATE badge SET unlockedAt = :time WHERE id = :id")
    suspend fun unlock(id: String, time: Long)
}

@Dao
interface SeasonBadgeDao {
    @Query("SELECT * FROM season_badge")
    fun observeAll(): Flow<List<SeasonBadge>>

    @Query("SELECT COUNT(*) FROM season_badge")
    suspend fun count(): Int

    /** 清除全部节气章解锁状态 */
    @Query("UPDATE season_badge SET unlockedAt = NULL, taskId = NULL")
    suspend fun clearUnlocked()

    @Query("SELECT * FROM season_badge WHERE solarTerm = :term LIMIT 1")
    suspend fun term(term: String): SeasonBadge?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTerm(item: SeasonBadge)

    @Query("UPDATE season_badge SET unlockedAt = :time, taskId = :taskId WHERE solarTerm = :term")
    suspend fun unlock(term: String, time: Long, taskId: Int?)

    @Query("SELECT * FROM season_badge WHERE unlockedAt IS NOT NULL")
    suspend fun unlocked(): List<SeasonBadge>
}

@Dao
interface WeatherCacheDao {
    @Query("SELECT * FROM weather_cache WHERE date = :date")
    suspend fun getByDate(date: String): WeatherCache?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(cache: WeatherCache)
}

@Dao
interface BehaviorReportDao {
    @Query("SELECT * FROM behavior_report ORDER BY generatedAt DESC")
    fun observeAll(): Flow<List<BehaviorReport>>

    @Insert
    suspend fun insert(report: BehaviorReport)

    /** 清空已生成的行为报告（清除演示数据时用） */
    @Query("DELETE FROM behavior_report")
    suspend fun clearAll()
}

/** Room 数据库：本地优先，无服务器 */
@Database(
    entities = [
        UserProfile::class,
        ScreenRecord::class,
        SunCoinLedger::class,
        TaskCard::class,
        ChallengeLog::class,
        BuildingSpot::class,
        Badge::class,
        SeasonBadge::class,
        WeatherCache::class,
        BehaviorReport::class
    ],
// 【重要】改动任何 @Entity 字段后必须 +1，否则 Room 认为 schema 未变、
// 校验失败直接抛 IllegalStateException 导致 App 启动即崩溃。
    version = 4,
    exportSchema = false
)
abstract class SunshineDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun screenRecordDao(): ScreenRecordDao
    abstract fun sunCoinLedgerDao(): SunCoinLedgerDao
    abstract fun taskCardDao(): TaskCardDao
    abstract fun challengeLogDao(): ChallengeLogDao
    abstract fun buildingSpotDao(): BuildingSpotDao
    abstract fun badgeDao(): BadgeDao
    abstract fun seasonBadgeDao(): SeasonBadgeDao
    abstract fun weatherCacheDao(): WeatherCacheDao
    abstract fun behaviorReportDao(): BehaviorReportDao

    companion object {
        @Volatile
        private var instance: SunshineDatabase? = null

        /**
         * v2 → v3：删除债务系统与屏幕时长统计。
         *
         * 1. debt_record 表整体删除（阳光债功能已下线）
         * 2. screen_record 删除 totalMinutes / coinsEarned / isWeekend 三列，
         *只保留 date 与 sunMinutes
         *
         * 【为什么必须写 Migration】旧版用户的数据库里还有这些列与表。
         * Room 启动时会校验 schema，不一致会直接抛异常导致闪退。
         * SQLite 的 DROP COLUMN 需要 3.35+，为兼容旧版本改用「重建表」方式。
         */
        private val MIGRATION_2_3 = object : androidx.room.migration.Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // ① 删掉债务表
                db.execSQL("DROP TABLE IF EXISTS `debt_record`")

                // ② 重建 screen_record，只留 date + sunMinutes
                // （SQLite 早期版本不支持 ALTER TABLE DROP COLUMN）
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `screen_record_new` (
                        `date` TEXT NOT NULL,
                        `sunMinutes` INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(`date`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "INSERT INTO `screen_record_new` (`date`, `sunMinutes`) " +
                        "SELECT `date`, `sunMinutes` FROM `screen_record`"
                )
                db.execSQL("DROP TABLE `screen_record`")
                db.execSQL("ALTER TABLE `screen_record_new` RENAME TO `screen_record`")
            }
        }

private const val DB_NAME = "sunshine.db"

        /**
         * v3 → v4：user_profile 新增 startupCoins 列。
         *
         * 【背景】把「启动礼包阳光币数」从代码常量改为用户可配置项，
         * 默认值 25 —— 与原先 `SunshineRepository.STARTUP_COINS` 完全一致，
         * 因此老用户升级后行为不变，不会凭空多拿或少拿币。
         *
         * 【为什么用 ADD COLUMN】ALTER TABLE ADD COLUMN 会给已有行自动填默认值，
         * 不丢任何历史数据。
         */
        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL(
"ALTER TABLE user_profile ADD COLUMN startupCoins INTEGER NOT NULL DEFAULT 25"
     )
            }
        }

        /**
         * v1 → v2：task_card 新增 isCustom 列（用户自定义任务卡标记）。
         *
         * 【为什么必须写 Migration 而不是只靠 fallbackToDestructiveMigration】
         * `fallbackToDestructiveMigration()` **只在 version 号变化时**才清库重建。
         * 如果改了实体字段却忘记递增 version，Room 会认为 schema 未变，
         * 在校验时抛 IllegalStateException，导致 App 一启动就崩溃、
         * 数据库打不开、bootstrap() 跑不到（新手礼包也就发不出来）。
         */
        private val MIGRATION_1_2 = object : androidx.room.migration.Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                // ALTER TABLE ADD COLUMN 对已有行自动填默认值，不会丢数据
                db.execSQL(
                    "ALTER TABLE task_card ADD COLUMN isCustom INTEGER NOT NULL DEFAULT 0"
                )
            }
        }


        fun get(context: android.content.Context): SunshineDatabase =
            instance ?: synchronized(this) {
                instance ?: build(context.applicationContext)
                    .also { instance = it }
            }

        /**
         * 构建数据库，并带**自愈能力**。
         *
         * 【为什么要自愈】
         * Room 的 schema 校验失败（IllegalStateException）发生在**第一次查询**时，
         * 而不是 build() 时。也就是说 `fallbackToDestructiveMigration()` 救不了
         * 「改了实体但忘记递增 version」这类错误——异常在业务代码里抛出，
         * 会导致 App 启动即崩溃、数据库不可用、连新手礼包都发不出来。
         *
         * 这里主动做一次探测性查询：失败就删库重建。
         * 代价是丢数据，但「能启动、能继续用」比「保住坏数据」更重要。
         */
        private fun build(appContext: android.content.Context): SunshineDatabase {
            val open: () -> SunshineDatabase = {
                Room.databaseBuilder(appContext, SunshineDatabase::class.java, DB_NAME)
// 注册迁移：已有数据用户平滑升级，不丢账本
   .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                    // 兜底：未预期的 schema 变化时重建
                    .fallbackToDestructiveMigration()
                    .build()
            }

            return runCatching {
                val db = open()
                // 关键探测：真正打开文件并校验 schema
                db.openHelper.readableDatabase
                db
            }.getOrElse { e ->
                android.util.Log.w(
                    "SunshineDB",
                    "数据库打开失败，删除后重建（将清空本地数据）",
                    e
                )
                appContext.deleteDatabase(DB_NAME)
                instance = null
                val db = open()
                db.openHelper.readableDatabase
                db
            }
        }
    }
}