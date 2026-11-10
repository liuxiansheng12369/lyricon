/*
 * IslandContentBridge — 超级岛内容桥接框架
 * Copyright (c) 2026 刘先生（酷安 coolmarket://u/336057）
 * Licensed under the Apache License, Version 2.0
 */
package com.liuxiansheng.lyricon.hyper.root.island.content.provider

import android.content.Context
import android.view.View
import com.liuxiansheng.lyricon.hyper.root.island.content.provider.providers.*

/**
 * 超级岛内容提供者工厂（55 种模式）。
 *
 * LYRIC / SONG_INFO 两种模式由 V3 现有渲染链路负责
 * （IslandSlotContentFacade 歌词组装器 / 自定义音乐信息组装器），
 * 工厂内仅返回占位实现，不会实际被调用。
 */
object IslandContentProviderFactory {
    fun getProvider(mode: IslandContentMode): IslandContentProvider {
        return when (mode) {
            // === 由 V3 现有渲染链路处理（占位，不实际使用） ===
            IslandContentMode.LYRIC -> PlaceholderProvider()
            IslandContentMode.SONG_INFO -> PlaceholderProvider()
            IslandContentMode.TIME -> TimeContentProvider()
            IslandContentMode.CUSTOM_TEXT -> CustomTextContentProvider()
            IslandContentMode.NETWORK_SPEED -> NetworkSpeedContentProvider()
            IslandContentMode.STATUS_BAR_ICONS -> StatusBarIconsProvider()

            // === ⭐ 低难度（22） ===
            IslandContentMode.BATTERY_LEVEL -> BatteryLevelProvider()
            IslandContentMode.BATTERY_TEMP -> BatteryTempProvider()
            IslandContentMode.CHARGING_STATUS -> ChargingStatusProvider()
            IslandContentMode.DATE_WEEKDAY -> DateWeekdayProvider()
            IslandContentMode.SECONDS_TIME -> SecondsTimeProvider()
            IslandContentMode.SYSTEM_UPTIME -> SystemUptimeProvider()
            IslandContentMode.WIFI_SSID -> WifiSsidProvider()
            IslandContentMode.MOBILE_NETWORK_TYPE -> MobileNetworkTypeProvider()
            IslandContentMode.CARRIER_NAME -> CarrierNameProvider()
            IslandContentMode.VOLUME_LEVEL -> VolumeLevelProvider()
            IslandContentMode.SCREEN_BRIGHTNESS -> ScreenBrightnessProvider()
            IslandContentMode.SCREEN_REFRESH_RATE -> ScreenRefreshRateProvider()
            IslandContentMode.DND_STATUS -> DndStatusProvider()
            IslandContentMode.AIRPLANE_MODE -> AirplaneModeProvider()
            IslandContentMode.VPN_STATUS -> VpnStatusProvider()
            IslandContentMode.ALARM_TIME -> AlarmTimeProvider()
            IslandContentMode.HOTSPOT_STATUS -> HotspotStatusProvider()
            IslandContentMode.CURRENT_APP -> CurrentAppProvider()
            IslandContentMode.LOCAL_IP -> LocalIpProvider()
            IslandContentMode.SCREEN_USAGE -> ScreenUsageProvider()
            IslandContentMode.DAILY_DATA_USAGE -> DailyDataUsageProvider()
            IslandContentMode.SIGNAL_STRENGTH -> SignalStrengthProvider()

            // === ⭐⭐ 中难度（18） ===
            IslandContentMode.CPU_USAGE -> CpuUsageProvider()
            IslandContentMode.CPU_TEMP -> CpuTempProvider()
            IslandContentMode.MEMORY_USAGE -> MemoryUsageProvider()
            IslandContentMode.STORAGE_SPACE -> StorageSpaceProvider()
            IslandContentMode.NETWORK_SPEED_SPLIT -> NetworkSpeedSplitProvider()
            IslandContentMode.BLUETOOTH_BATTERY -> BluetoothBatteryProvider()
            IslandContentMode.LUNAR_DATE -> LunarDateProvider()
            IslandContentMode.UNREAD_NOTIFICATIONS -> UnreadNotificationsProvider()
            IslandContentMode.CHARGING_WATTAGE -> ChargingWattageProvider()
            IslandContentMode.BATTERY_CYCLES -> BatteryCyclesProvider()
            IslandContentMode.CPU_GOVERNOR -> CpuGovernorProvider()
            IslandContentMode.GPU_FREQ -> GpuFreqProvider()
            IslandContentMode.FPS_COUNTER -> FpsCounterProvider()
            IslandContentMode.BATTERY_HEALTH -> BatteryHealthProvider()
            IslandContentMode.WALLPAPER_NAME -> WallpaperNameProvider()
            IslandContentMode.MOON_PHASE -> MoonPhaseProvider()
            IslandContentMode.WIFI_FREQ_BAND -> WifiFreqBandProvider()
            IslandContentMode.THERMAL_SUMMARY -> ThermalSummaryProvider()

            // === ⭐⭐⭐ 高难度（10） ===
            IslandContentMode.TODAY_STEPS -> TodayStepsProvider()
            IslandContentMode.WEATHER_TEMP -> WeatherTempProvider()
            IslandContentMode.STOCK_PRICE -> StockPriceProvider()
            IslandContentMode.NOW_PLAYING -> NowPlayingProvider()
            IslandContentMode.COUNTDOWN_DAYS -> CountdownDaysProvider()
            IslandContentMode.POMODORO_TIMER -> PomodoroTimerProvider()
            IslandContentMode.CUSTOM_API -> CustomApiProvider()
            IslandContentMode.AIR_QUALITY -> AirQualityProvider()
            IslandContentMode.UV_INDEX -> UvIndexProvider()
            IslandContentMode.EARTHQUAKE_ALERT -> EarthquakeAlertProvider()
        }
    }

    /** LYRIC/SONG_INFO 占位（实际由 V3 渲染链路处理） */
    private class PlaceholderProvider : IslandContentProvider {
        override fun createView(context: Context, config: ContentProviderConfig, side: String): View {
            return View(context)
        }
    }
}
