/*
 * IslandContentBridge — 超级岛内容桥接框架
 * Copyright (c) 2026 刘先生（酷安 coolmarket://u/336057）
 * Licensed under the Apache License, Version 2.0
 */
package com.liuxiansheng.lyricon.hyper.root.island.content.provider

/**
 * 超级岛内容模式枚举（55 种）。
 */
enum class IslandContentMode(val value: String, val displayName: String) {
    // === 已有（5） ===
    LYRIC("lyric", "歌词"),
    SONG_INFO("song_info", "歌名+艺术家"),
    TIME("time", "当前时间"),
    CUSTOM_TEXT("custom_text", "自定义文字"),
    NETWORK_SPEED("network_speed", "实时网速"),
    STATUS_BAR_ICONS("status_bar_icons", "状态栏图标(蓝牙+电池+网速)"),

    // === ⭐ 低难度（23） ===
    BATTERY_LEVEL("battery_level", "电池电量"),
    BATTERY_TEMP("battery_temp", "电池温度"),
    CHARGING_STATUS("charging_status", "充电状态"),
    BLUETOOTH_BATTERY("bluetooth_battery", "蓝牙电量"),
    DATE_WEEKDAY("date_weekday", "日期+星期"),
    SECONDS_TIME("seconds_time", "秒级时间"),
    SYSTEM_UPTIME("system_uptime", "系统运行时长"),
    WIFI_SSID("wifi_ssid", "WiFi名称"),
    MOBILE_NETWORK_TYPE("mobile_network_type", "移动网络类型"),
    CARRIER_NAME("carrier_name", "运营商"),
    VOLUME_LEVEL("volume_level", "音量等级"),
    SCREEN_BRIGHTNESS("screen_brightness", "屏幕亮度"),
    SCREEN_REFRESH_RATE("screen_refresh_rate", "屏幕刷新率"),
    DND_STATUS("dnd_status", "勿扰模式"),
    AIRPLANE_MODE("airplane_mode", "飞行模式"),
    VPN_STATUS("vpn_status", "VPN状态"),
    ALARM_TIME("alarm_time", "闹钟时间"),
    HOTSPOT_STATUS("hotspot_status", "热点状态"),
    CURRENT_APP("current_app", "当前应用"),
    LOCAL_IP("local_ip", "IP地址"),
    SCREEN_USAGE("screen_usage", "屏幕使用时长"),
    DAILY_DATA_USAGE("daily_data_usage", "今日流量"),
    SIGNAL_STRENGTH("signal_strength", "信号强度"),

    // === ⭐⭐ 中难度（17） ===
    CPU_USAGE("cpu_usage", "CPU使用率"),
    CPU_TEMP("cpu_temp", "CPU温度"),
    MEMORY_USAGE("memory_usage", "内存使用率"),
    STORAGE_SPACE("storage_space", "存储空间"),
    NETWORK_SPEED_SPLIT("network_speed_split", "网速分上下行"),
    LUNAR_DATE("lunar_date", "农历日期"),
    UNREAD_NOTIFICATIONS("unread_notifications", "未读通知数"),
    CHARGING_WATTAGE("charging_wattage", "充电功率"),
    BATTERY_CYCLES("battery_cycles", "电池循环"),
    CPU_GOVERNOR("cpu_governor", "CPU调度策略"),
    GPU_FREQ("gpu_freq", "GPU频率"),
    FPS_COUNTER("fps_counter", "实时帧率"),
    BATTERY_HEALTH("battery_health", "电池健康度"),
    WALLPAPER_NAME("wallpaper_name", "当前壁纸"),
    MOON_PHASE("moon_phase", "月相"),
    WIFI_FREQ_BAND("wifi_freq_band", "WiFi频段"),
    THERMAL_SUMMARY("thermal_summary", "设备温度"),

    // === ⭐⭐⭐ 高难度（10） ===
    TODAY_STEPS("today_steps", "今日步数"),
    WEATHER_TEMP("weather_temp", "天气温度"),
    STOCK_PRICE("stock_price", "股票价格"),
    NOW_PLAYING("now_playing", "Now Playing"),
    COUNTDOWN_DAYS("countdown_days", "倒数日"),
    POMODORO_TIMER("pomodoro_timer", "番茄钟"),
    CUSTOM_API("custom_api", "自定义API"),
    AIR_QUALITY("air_quality", "空气质量"),
    UV_INDEX("uv_index", "紫外线指数"),
    EARTHQUAKE_ALERT("earthquake_alert", "地震预警");

    companion object {
        fun fromValue(value: String?): IslandContentMode {
            return values().find { it.value == value } ?: LYRIC
        }

        /** 与 V3 现有 int 模式键兼容的映射：7=歌词 8=音乐信息 100+=扩展模式。 */
        fun fromInt(value: Int): IslandContentMode {
            return when (value) {
                7 -> LYRIC
                8 -> SONG_INFO
                else -> values().getOrNull(value - 100) ?: LYRIC
            }
        }
    }

    /** 与 V3 现有 int 模式键兼容：歌词=7 音乐信息=8 扩展模式=100+ordinal。 */
    fun toInt(): Int {
        return when (this) {
            LYRIC -> 7
            SONG_INFO -> 8
            else -> 100 + ordinal
        }
    }
}
