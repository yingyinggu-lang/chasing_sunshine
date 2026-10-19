package com.sunshine.exchange.core

import com.sunshine.exchange.data.Badge
import com.sunshine.exchange.data.TaskCard

/**
 * 内置任务卡库：18 张常规卡 + 24节气限定卡池。
 * 对应需求文档 5. P0-5「任务卡系统（内置 18 张 + 3 张节气限定）」。
 * 四类：微运动 MICRO / 户外探索 OUTDOOR / 放松 RELAX / 节气限定 SEASON
 */
object TaskCardLibrary {

    fun baseCards(): List<TaskCard> = listOf(
        // ---------- 微运动（室内也能做，但完成需真户外验收结算）----------
        TaskCard(1, "课间伸展", "站起来伸展 3 分钟，让眼睛看看远处", "MICRO", 1, 2, 5, false, "stretch", sunRewardMinutes = 10),
        TaskCard(2, "跳绳挑战", "连续跳绳，累计步数达标即可结算", "MICRO", 2, 3, 10, true, "rope", sunRewardMinutes = 15),
        TaskCard(3, "眼保健操", "做完一整套眼保健操，看看窗外的绿", "MICRO", 1, 2, 8, false, "eye", sunRewardMinutes = 10),
        TaskCard(4, "深蹲 20 个", "分组完成 20 个深蹲，唤醒身体", "MICRO", 2, 2, 8, true, "squat", sunRewardMinutes = 12),
        TaskCard(5, "原地开合跳", "开合跳 100 个，出门前把身体叫醒", "MICRO", 2, 3, 10, true, "jump", sunRewardMinutes = 15),
        TaskCard(6, "靠墙站立", "靠墙静站，改善含胸驼背", "MICRO", 1, 2, 6, false, "stand", sunRewardMinutes = 10),

        // ---------- 户外探索（必须走到室外接受阳光验收）----------
        TaskCard(7, "楼下散步", "到楼下走一圈，看看今天的天空", "OUTDOOR", 1, 7, 20, true, "walk", sunRewardMinutes = 20),
        TaskCard(8, "追风跑步", "慢跑一段，让心跳和阳光一起升起来", "OUTDOOR", 3, 11, 30, true, "run", sunRewardMinutes = 30),
        TaskCard(9, "球类运动", "打球或打球类运动，出汗才算数", "OUTDOOR", 3, 15, 40, true, "ball", sunRewardMinutes = 40),
        TaskCard(10, "晒 15 分钟太阳", "找个 sunny 的地方，静静晒一会儿", "OUTDOOR", 1, 5, 15, false, "sunbath", sunRewardMinutes = 25),
        TaskCard(11, "阳台远眺", "到阳台看看远处的楼和树，放松眼睛", "OUTDOOR", 1, 3, 12, false, "balcony", sunRewardMinutes = 15),
        TaskCard(12, "骑行上学", "骑车或步行上学，路上留意路边的光", "OUTDOOR", 2, 9, 25, true, "bike", sunRewardMinutes = 25),
        TaskCard(13, "公园探索", "去最近的公园，找三种不同的植物", "OUTDOOR", 2, 13, 35, true, "park", sunRewardMinutes = 35),
        TaskCard(14, "天台看云", "找一处开阔地，观察云的样子并记录", "OUTDOOR", 2, 7, 20, false, "cloud_watch", sunRewardMinutes = 20),

        // ---------- 放松（户外优先，照顾情绪）----------
        TaskCard(15, "听雨漫步", "雨天散步，听雨打伞的声音", "RELAX", 1, 7, 20, true, "rain", special = "WEATHER", sunRewardMinutes = 30),
        TaskCard(16, "深呼吸练习", "在户外做 5 组深呼吸，安静一分钟", "RELAX", 1, 3, 10, false, "breathe", sunRewardMinutes = 15),
        TaskCard(17, "黄昏散步", "日落时分走一走，给今天一个温柔结尾", "RELAX", 1, 9, 25, true, "dusk", sunRewardMinutes = 25),
        TaskCard(18, "抬头看太阳", "闭眼朝太阳方向感受一会儿光（勿直视）", "RELAX", 1, 3, 12, false, "sun_gaze", sunRewardMinutes = 18)
    )

    /** 节气限定卡池：每个节气一张，当天才出现 */
    fun seasonCards(): List<TaskCard> = listOf(
        TaskCard(101, "惊蛰·出门听春雷", "惊蛰时节，走到户外听听春雷与虫鸣", "SEASON", 2, 7, 20, true, "thunder", special = "SEASON", solarTerm = "惊蛰", sunRewardMinutes = 25),
        TaskCard(102, "春分·竖蛋试验", "春分日太阳直射赤道，试试立蛋成不成立", "SEASON", 2, 9, 25, false, "egg", special = "SEASON", solarTerm = "春分", sunRewardMinutes = 25),
        TaskCard(103, "清明·踏青", "清明时节去户外踏青，看新生的草木", "SEASON", 2, 13, 35, true, "qingtian", special = "SEASON", solarTerm = "清明", sunRewardMinutes = 35),
        TaskCard(104, "谷雨·赏雨", "谷雨时节雨生百谷，站在屋檐下看雨", "SEASON", 1, 7, 20, false, "guyu", special = "SEASON", solarTerm = "谷雨", sunRewardMinutes = 20),
        TaskCard(105, "夏至·正午晒太阳", "夏至日太阳能跑全天，正午晒一晒", "SEASON", 2, 11, 30, false, "solstice", special = "SEASON", solarTerm = "夏至", sunRewardMinutes = 35),
        TaskCard(106, "小暑·消暑纳凉", "小暑去有树荫的地方走一走", "SEASON", 1, 9, 25, true, "xiaoshu", special = "SEASON", solarTerm = "小暑", sunRewardMinutes = 25),
        TaskCard(107, "大暑·夜风纳凉", "傍晚到户外散步，感受夏夜的风", "SEASON", 1, 9, 25, true, "dashu", special = "SEASON", solarTerm = "大暑", sunRewardMinutes = 25),
        TaskCard(108, "立秋·贴秋膘", "立秋后走一走，给身体换个节奏", "SEASON", 1, 9, 25, true, "liqiu", special = "SEASON", solarTerm = "立秋", sunRewardMinutes = 25),
        TaskCard(109, "白露·赏月", "白露时节，夜晚到户外看月亮与露水", "SEASON", 1, 9, 25, false, "moon", special = "SEASON", solarTerm = "白露", sunRewardMinutes = 25),
        TaskCard(110, "秋分·丰收望月", "秋分平分昼夜，户外赏一轮明月", "SEASON", 1, 9, 25, false, "autumn", special = "SEASON", solarTerm = "秋分", sunRewardMinutes = 25),
        TaskCard(111, "寒露·登高", "寒露登高望远，爬一段小坡", "SEASON", 3, 15, 40, true, "climb", special = "SEASON", solarTerm = "寒露", sunRewardMinutes = 40),
        TaskCard(112, "霜降·赏霜", "霜降时节看草叶上的白霜", "SEASON", 1, 9, 25, true, "frost", special = "SEASON", solarTerm = "霜降", sunRewardMinutes = 25),
        TaskCard(113, "立冬·晒被子", "立冬的阳光很珍贵，户外晒一晒", "SEASON", 1, 9, 25, false, "lidong", special = "SEASON", solarTerm = "立冬", sunRewardMinutes = 28),
        TaskCard(114, "冬至·正午晒太阳", "冬至日阳光最短，珍惜正午那一刻", "SEASON", 2, 11, 30, false, "dongzhi", special = "SEASON", solarTerm = "冬至", sunRewardMinutes = 35),
        TaskCard(115, "大雪·雪中漫步", "下雪天出去走一走，看雪落在手上", "SEASON", 2, 11, 30, true, "snow", special = "SEASON", solarTerm = "大雪", sunRewardMinutes = 30),
        TaskCard(116, "小寒·热身跑", "小寒跑一段，身体热起来", "SEASON", 3, 13, 35, true, "xiaohan", special = "SEASON", solarTerm = "小寒", sunRewardMinutes = 35),
        TaskCard(117, "大寒·迎新光", "大寒岁末，走到户外迎接新一年", "SEASON", 2, 11, 30, true, "dahan", special = "SEASON", solarTerm = "大寒", sunRewardMinutes = 32),
        TaskCard(118, "立春·放风筝", "立春风起，去户外放一次风筝", "SEASON", 2, 13, 35, true, "kite", special = "SEASON", solarTerm = "立春", sunRewardMinutes = 35),
        TaskCard(119, "雨水·听雨", "雨水时节，听一场春雨", "SEASON", 1, 7, 20, false, "yushui", special = "SEASON", solarTerm = "雨水", sunRewardMinutes = 22),
        TaskCard(120, "立夏·迎夏", "立夏第一天，去户外感受夏天的开始", "SEASON", 1, 9, 25, true, "lixia", special = "SEASON", solarTerm = "立夏", sunRewardMinutes = 25),
        TaskCard(121, "小满·晒谷", "小满时节户外走走，晒一晒阳光", "SEASON", 1, 7, 20, false, "xiaoman", special = "SEASON", solarTerm = "小满", sunRewardMinutes = 20),
        TaskCard(122, "芒种·抢收", "芒种抓紧时间，户外快走一段", "SEASON", 2, 11, 30, true, "mangzhong", special = "SEASON", solarTerm = "芒种", sunRewardMinutes = 30),
        TaskCard(123, "处暑·收心", "处暑暑气渐消，傍晚散步纳凉", "SEASON", 1, 9, 25, true, "chushu", special = "SEASON", solarTerm = "处暑", sunRewardMinutes = 25),
        TaskCard(124, "小雪·围炉", "小雪天户外走走，看雪花与呵气", "SEASON", 1, 9, 25, false, "xiaoxue", special = "SEASON", solarTerm = "小雪", sunRewardMinutes = 25)
    )

    fun all(): List<TaskCard> = baseCards() + seasonCards()

    /** 徽章定义 */
    fun badges(): List<Badge> = listOf(
        Badge("first_light", "第一缕光", "完成第一次真户外验收", "sunrise"),
        Badge("tower_10", "天际线十连", "点亮 10 座建筑", "tower"),
        Badge("sun_60", "正午追光者", "累计 60 分钟真实阳光", "sun"),
        Badge("sun_300", "半日光行者", "累计 300 分钟真实阳光", "sun_star"),
        Badge("early_bird", "晨光收集者", "早上 8 点前完成户外", "dawn"),
        Badge("seven_days", "一周不熄灭", "连续 7 天有真实阳光", "flame"),
        Badge("city_builder", "天空城主", "点亮 30 座建筑", "castle")
    )
}