package com.sunshine.exchange.core

/** 等级称号 */
data class Level(
    val title: String,
    val iconKey: String,
    val level: Int
)

/** 天气动画类型（供 UI 层使用，与 Canvas 组件解耦） */
enum class WeatherKindCompat { SUNNY, CLOUDY, RAINY, SNOWY }

/**
 * 天气服务。
 *
 * 说明：优先尝试和风天气公开接口；未配置 Key 或网络失败时，
 * 使用内置示例数据 fallback，保证界面永远可用、不死机（对应需求 10 风险对策）。
 *
 * 【v5.13 回退说明】此前尝试接入 Open-Meteo 真实天气数据（免注册免 Key），
 * 但答辩场景下需要完全离线可演示，故已整体撤回，恢复为内置示例数据。
 * 网络请求相关代码（定位查询、HTTP 请求、JSON 解析）已全部移除。
 */
class WeatherService {

    /**
     * 获取今日天气。
     * @param apiKey 和风天气 API Key（可为空，为空时直接用 fallback）
     */
    suspend fun fetchToday(apiKey: String? = null): Weather {
        if (apiKey.isNullOrBlank()) return Weather.fallback()
        return runCatching {
  // 真实接入时在此发起网络请求解析 JSON；
            // 未配置 Key 时直接返回示例数据，保证离线可用。
            Weather.fallback()
        }.getOrElse { Weather.fallback() }
    }

    data class Weather(
        val code: String,
        val sunrise: String,
        val sunset: String,
        val tempC: Int,
        val desc: String
    ) {
        val isRainy: Boolean get() = code == "RAINY"

        companion object {
    /** 内置示例数据 fallback：接口失败时使用 */
      fun fallback(): Weather = Weather(
      code = "SUNNY",
            sunrise = "06:12",
       sunset = "18:36",
    tempC = 24,
     desc = "晴"
  )
        }
    }
}