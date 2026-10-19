package com.sunshine.exchange.core

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.GregorianCalendar
import java.util.Locale
import kotlin.math.abs

/** 日期/周次工具：全部基于本地时区，避免时区偏移导致跨天结算出错 */
object DateUtils {

    private val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    private val shortFormat = SimpleDateFormat("M/d", Locale.CHINA)

    /** 供外部复用的日期解析（避免重复创建 SimpleDateFormat） */
    fun parse(dateStr: String): Date? = runCatching { dayFormat.parse(dateStr) }.getOrNull()

    fun today(): String = dayFormat.format(Date())

    fun format(date: Date): String = dayFormat.format(date)

    fun shortLabel(dateStr: String): String = runCatching {
        shortFormat.format(dayFormat.parse(dateStr)!!)
    }.getOrDefault(dateStr)

    fun isWeekend(dateStr: String): Boolean {
        val cal = Calendar.getInstance()
        cal.time = dayFormat.parse(dateStr) ?: return false
        val dow = cal.get(Calendar.DAY_OF_WEEK)
        return dow == Calendar.SATURDAY || dow == Calendar.SUNDAY
    }

    /** 该日期所在周的周一（周起始日） */
    fun weekStartOf(dateStr: String): String {
        val cal = Calendar.getInstance()
        cal.time = dayFormat.parse(dateStr) ?: return dateStr
        cal.firstDayOfWeek = Calendar.MONDAY
        // 定位到本周周一
        while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
            cal.add(Calendar.DAY_OF_YEAR, -1)
        }
        return dayFormat.format(cal.time)
    }

    /** 从 from 到 to 的日期序列（含首尾） */
    fun dateRange(from: String, to: String): List<String> {
        val start = dayFormat.parse(from) ?: return emptyList()
        val end = dayFormat.parse(to) ?: return emptyList()
        val out = mutableListOf<String>()
        var cur = start
        while (!cur.after(end)) {
            out.add(dayFormat.format(cur))
            val c = Calendar.getInstance().apply { time = cur }
            c.add(Calendar.DAY_OF_YEAR, 1)
            cur = c.time
        }
        return out
    }

    fun daysAgo(n: Int): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, -n)
        return dayFormat.format(cal.time)
    }

    fun plusDays(dateStr: String, n: Int): String {
        val cal = Calendar.getInstance()
        cal.time = dayFormat.parse(dateStr) ?: return dateStr
        cal.add(Calendar.DAY_OF_YEAR, n)
        return dayFormat.format(cal.time)
    }

    /** 当前小时，用于日照窗口判断 */
    fun currentHour(): Int = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
}

/**
 * 二十四节气近似计算（1900-2100 通用近似公式）。
 * 用于节气限定任务卡与节气章的解锁判定，纯本地计算，无需联网。
 */
object SolarTerms {

    private val TERMS = listOf(
        "小寒", "大寒", "立春", "雨水", "惊蛰", "春分",
        "清明", "谷雨", "立夏", "小满", "芒种", "夏至",
        "小暑", "大暑", "立秋", "处暑", "白露", "秋分",
        "寒露", "霜降", "立冬", "小雪", "大雪", "冬至"
    )

    /** 基准：1900-01-01 起的节气分钟偏移表（节气长度约 365.2422/24 天） */
    private val MINUTES = intArrayOf(
        0, 21208, 42467, 63836, 85337, 107014,
        128867, 150921, 173149, 195551, 218072, 240693,
        263343, 285989, 308563, 331033, 353350, 375494,
        397447, 419210, 440795, 462224, 483532, 504758
    )

    fun all(): List<String> = TERMS

    /**
     * 返回指定日期对应的节气（近似算法，纯本地计算，无需联网）。
     *
     * 【算法说明】MINUTES 表是「每个节气距当年元旦的偏移分钟数」（0 ~ 525600 的年内循环值）。
     * 因此计算时必须用同一口径：先算出目标日期距当年元旦的分钟数，再取最接近的节气偏移。
     * 农历节气实际间隔约 15.22 天，逐年累积会有数天误差，故窗口取 ±7 天——
     * 这样任何一天都能匹配到一个节气（用于决定当天展示哪张节气卡），
     * 同时「24 张节气章」的收集仍由用户逐日推进，不会一次性全亮。
     */
    fun currentTerm(date: Date = Date()): String? {
        val cal = GregorianCalendar().apply { time = date }
        val year = cal.get(Calendar.YEAR)

        val startOfYear = GregorianCalendar(year, Calendar.JANUARY, 1, 0, 0, 0)
        val minutesNow = ((date.time - startOfYear.timeInMillis) / 60_000L).toInt()

        var bestIndex = -1
        var bestDiff = Int.MAX_VALUE
        for (i in MINUTES.indices) {
            // 同时考虑「落在下一年偏移」的情况，处理年末跨年
            val diff = minOf(
                abs(MINUTES[i] - minutesNow),
                abs(MINUTES[i] - minutesNow + DAYS_PER_YEAR_MIN)
            )
            if (diff < bestDiff) {
                bestDiff = diff
                bestIndex = i
            }
        }
        // ±7 天内视为该节气窗口；超出则视为「无节气」，
        // 由调用方回退到最近的节气卡，保证任何一天都有限定卡可展示
        return if (bestDiff <= TERM_WINDOW_MIN && bestIndex >= 0) TERMS[bestIndex] else null
    }

    /**
     * 取距离指定日期最近的节气（不设窗口）。
     * 用于「节气限定」列表在无当日节气时仍展示一张卡，避免出现空白页。
     */
    fun nearestTerm(date: Date = Date()): String {
        val cal = GregorianCalendar().apply { time = date }
        val year = cal.get(Calendar.YEAR)
        val startOfYear = GregorianCalendar(year, Calendar.JANUARY, 1, 0, 0, 0)
        val minutesNow = ((date.time - startOfYear.timeInMillis) / 60_000L).toInt()

        var bestIndex = 0
        var bestDiff = Int.MAX_VALUE
        for (i in MINUTES.indices) {
            val diff = minOf(
                abs(MINUTES[i] - minutesNow),
                abs(MINUTES[i] - minutesNow + DAYS_PER_YEAR_MIN)
            )
            if (diff < bestDiff) {
                bestDiff = diff
                bestIndex = i
            }
        }
        return TERMS[bestIndex]
    }

    /** 平年一天的分钟数，用于跨年归一化 */
    private const val DAYS_PER_YEAR_MIN = 365 * 24 * 60

    /** 节气匹配窗口：±7 天（覆盖农历节气 15.22 天间隔带来的累积误差） */
    private const val TERM_WINDOW_MIN = 7 * 24 * 60

    /** 判断给定节气名是否为"当前节气窗口"，用于限定卡当日出现 */
    fun isTermToday(term: String, date: Date = Date()): Boolean = currentTerm(date) == term
}