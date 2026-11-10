/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 */

package com.liuxiansheng.lyricon.app.util

import android.content.Context
import android.content.SharedPreferences
import com.liuxiansheng.lyricon.app.LyriconApp

/**
 * B1 暂停防冻结：本地 prefs 桥。
 * B1 配置强制写本地 SharedPreferences 文件
 * `com.liuxiansheng.lyricon_preferences.xml`（/data/user/0/com.liuxiansheng.lyricon.v3/shared_prefs/），
 * Pro 端（跑在音乐 App 进程）用 su 读同一文件生效。
 * 不走 Xposed 远程 prefs —— remote 数据落不到本地文件，provider 用 su 读不到。
 */
object LocalPrefsBridge : PrefsBridge {
    private fun p(context: Context): SharedPreferences =
        context.getSharedPreferences(MasterPrefs.PREF_NAME, Context.MODE_PRIVATE)
    override fun contains(context: Context, key: String): Boolean = p(context).contains(key)
    override fun getBoolean(context: Context, key: String, default: Boolean): Boolean =
        p(context).getBoolean(key, default)
    override fun setBoolean(context: Context, key: String, value: Boolean) {
        p(context).edit().putBoolean(key, value).apply()
    }
    override fun getInt(context: Context, key: String, default: Int): Int = p(context).getInt(key, default)
    override fun setInt(context: Context, key: String, value: Int) {
        p(context).edit().putInt(key, value).apply()
    }
    override fun getFloat(context: Context, key: String, default: Float): Float = p(context).getFloat(key, default)
    override fun setFloat(context: Context, key: String, value: Float) {
        p(context).edit().putFloat(key, value).apply()
    }
    override fun getString(context: Context, key: String, default: String?): String? =
        p(context).getString(key, default)
    override fun setString(context: Context, key: String, value: String?) {
        p(context).edit().putString(key, value).apply()
    }
}

/**
 * 设置页 prefs 桥。
 *
 * 与 xposed 模块 `UIConstants.PREF_NAME` / `RootConstants` 完全对齐：
 * - 读取：优先读 Xposed 远程 prefs（SystemUI 实际生效值），不可用回退本地
 * - 写入：本地 SharedPreferences + Xposed 远程 prefs 双写
 *
 * 键清单覆盖两类：
 * 1. HyperLyric 移植键（key_hook_*，SystemUI 侧 HookEntry 直接读取生效）
 * 2. 老词幕超级岛键（island_*，对应功能在 HyperLyric 管线中逐步对接）
 */
object MasterPrefs : PrefsBridge {
    const val PREF_NAME = "com.liuxiansheng.lyricon_preferences"

    // ================= HyperLyric 键（RootConstants KEY_HOOK_*） =================
    const val KEY_ENABLE_SUPER_ISLAND = "key_hook_enable_super_island"
    const val KEY_HOOK_ENABLE_STATUS_BAR_LYRIC = "key_hook_enable_status_bar_lyric"
    const val KEY_LYRIC_MODE = "key_hook_lyric_mode"
    const val KEY_LYRIC_SOURCE = "key_hook_lyric_source"
    const val KEY_ISLAND_ALBUM_COVER_STYLE = "key_hook_island_album_cover_style"

    // ================= 歌词源（V3 歌词源管理页） =================
    const val KEY_LYRIC_SOURCE_LIST_STYLE = "lyric_source_list_style"
    const val KEY_LYRIC_SOURCE_KEEP_SESSION_ON_PAUSE = "lyric_source_keep_session_on_pause"
    const val KEY_LYRIC_SOURCE_KEEP_SESSION_MINUTES = "lyric_source_keep_session_minutes"
    const val KEY_ISLAND_CONTENT_LEFT = "key_hook_island_content_left"
    const val KEY_ISLAND_CONTENT_RIGHT = "key_hook_island_content_right"
    const val KEY_ISLAND_MUSIC_INFO_FIRST_LINE = "key_hook_island_music_info_first_line"
    const val KEY_ISLAND_MUSIC_INFO_SECOND_LINE = "key_hook_island_music_info_second_line"
    const val KEY_ISLAND_LEFT_PADDING_LEFT = "key_hook_island_left_padding_left"
    const val KEY_ISLAND_LEFT_PADDING_RIGHT = "key_hook_island_left_padding_right"
    const val KEY_ISLAND_RIGHT_PADDING_LEFT = "key_hook_island_right_padding_left"
    const val KEY_ISLAND_RIGHT_PADDING_RIGHT = "key_hook_island_right_padding_right"
    const val KEY_ISLAND_WIDTH_MODE = "key_hook_island_width_mode"
    const val KEY_ISLAND_DYNAMIC_MIN_WIDTH = "key_hook_island_dynamic_min_width"
    const val KEY_ISLAND_DYNAMIC_MAX_WIDTH = "key_hook_island_dynamic_max_width"
    const val KEY_ISLAND_DYNAMIC_WIDTH_BASIS = "key_hook_island_dynamic_width_basis"
    const val KEY_ISLAND_DISABLE_WIDTH_LIMIT = "key_hook_island_disable_width_limit"
    const val KEY_ISLAND_BEHAVIOR_AFTER_PAUSE = "key_hook_island_behavior_after_pause"
    const val KEY_ISLAND_LONG_PRESS_BEHAVIOR = "key_hook_island_long_press_behavior"
    const val KEY_REMOVE_FOCUS_WHITELIST = "key_hook_remove_focus_whitelist"
    const val KEY_REMOVE_ISLAND_WHITELIST = "key_hook_remove_island_whitelist"

    // 样式 & 字体
    const val KEY_TEXT_SIZE = "key_hook_text_size"
    const val KEY_TEXT_SIZE_RATIO = "key_hook_text_size_ratio"
    /** 文字大小比例 UI 状态键（Int 百分比回显，渲染键是 Float） */
    const val KEY_ISLAND_TEXT_SIZE_RATIO_PCT = "island_text_size_ratio_pct"
    /** 下一句动画效果（Int 索引，与渲染端 PrefSpinner 对应）：0=无动画（原始切换）/ 1=副行放大 */
    const val KEY_LINE_SWITCH_ANIM = "key_hook_line_switch_anim"
    const val KEY_FONT_WEIGHT = "key_hook_font_weight"
    const val KEY_FONT_ITALIC = "key_hook_font_italic"
    const val KEY_GRADIENT_PROGRESS = "key_hook_gradient_progress"
    const val KEY_CENTER_LYRIC = "key_hook_center_lyric"
    const val KEY_CENTER_MUSIC_INFO = "key_hook_center_music_info"
    const val KEY_RIGHT_LYRIC = "key_hook_right_lyric"
    const val KEY_PLACEHOLDER_FORMAT = "key_hook_placeholder_format"
    const val KEY_ISLAND_MUSIC_WAVE_STYLE = "key_hook_island_music_wave_style"

    // 动画
    const val KEY_ANIM_ENABLE = "key_hook_anim_enable"
    const val KEY_ANIM_ID = "key_hook_anim_id"

    // 逐字
    const val KEY_SYLLABLE_RELATIVE = "key_hook_syllable_relative"
    const val KEY_SYLLABLE_HIGHLIGHT = "key_hook_syllable_highlight"
    const val KEY_SYLLABLE_LINE_DISPLAY = "key_hook_syllable_line_display"

    // 翻译
    /** 显示翻译（V3 直写键：true=显示翻译，开=开；不再写 disable 取反） */
    const val KEY_SHOW_TRANSLATION = "v3_show_translation"
    const val KEY_DISABLE_TRANSLATION = "key_hook_disable_translation"
    const val KEY_TRANSLATION_ONLY = "key_hook_translation_only"
    const val KEY_SWAP_TRANSLATION = "key_hook_swap_translation"
    const val KEY_NEXT_LYRIC_LINE = "key_hook_next_lyric_line"
    const val KEY_AUTO_SWITCH_TRANSLATION = "key_hook_auto_switch_translation"

    // 颜色
    const val KEY_TEXT_COLOR_STYLE = "key_hook_text_color_style"
    const val KEY_ISLAND_PROGRESS_GLOW = "key_hook_island_progress_glow"
    const val KEY_ISLAND_PROGRESS_STYLE = "key_hook_island_progress_style"
    const val KEY_ISLAND_PROGRESS_GRADIENT = "key_hook_island_progress_gradient"
    const val KEY_HOOK_ISLAND_PROGRESS_TRACK = "key_hook_island_progress_track"

    // 字体
    const val KEY_CUSTOM_FONT_PATH = "key_hook_custom_font_path"
    const val KEY_NARROW_LATIN_FONT = "key_hook_narrow_latin_font"

    // 逐字动效
    const val KEY_WORD_MOTION_ENABLED = "key_hook_word_motion_enabled"

    // ================= 老词幕超级岛键（island_*，逐步对接） =================
    const val KEY_ISLAND_LYRIC_ENABLED = "island_lyric_enabled"
    const val KEY_ISLAND_SPLIT_LYRIC_MODE = "island_split_lyric_mode"
    const val KEY_ISLAND_ENABLE_DYNAMIC_SHRINK = "island_enable_dynamic_shrink"
    const val KEY_ISLAND_SPECTRUM_LINK_WIDTH = "island_spectrum_link_width_enabled"
    const val KEY_ISLAND_DYNAMIC_SPLIT_MAX_WIDTH = "island_dynamic_split_max_width_dp"
    const val KEY_ISLAND_TEXT_SIZE = "island_text_size"
    const val KEY_ISLAND_COUNTDOWN_ENABLED = "island_countdown_enabled"
    const val KEY_ISLAND_COUNTDOWN_STYLE = "island_countdown_style"
    const val KEY_ISLAND_SPLIT_LEFT_OFFSET = "island_split_left_offset_dp"
    const val KEY_ISLAND_SPLIT_RIGHT_OFFSET = "island_split_right_offset_dp"
    const val KEY_ISLAND_LEFT_ENABLED = "island_left_enabled"
    const val KEY_ISLAND_RIGHT_ENABLED = "island_right_enabled"
    const val KEY_ISLAND_LEFT_CONTENT_MODE = "island_left_content_mode"
    const val KEY_ISLAND_RIGHT_CONTENT_MODE = "island_right_content_mode"
    const val KEY_ISLAND_LEFT_CUSTOM_TEXT = "island_left_custom_text"
    const val KEY_ISLAND_RIGHT_CUSTOM_TEXT = "island_right_custom_text"
    const val KEY_ISLAND_TITLE_EFFECT = "island_title_effect"
    const val KEY_ISLAND_LYRIC_ANIMATION = "island_lyric_animation"
    const val KEY_ISLAND_LYRIC_EFFECT = "island_lyric_effect"
    const val KEY_ISLAND_LYRIC_MIRROR = "island_lyric_mirror"
    const val KEY_ISLAND_RAINBOW_ENABLED = "island_rainbow_enabled"
    const val KEY_ISLAND_ENABLE_COVER_COLOR = "island_enable_cover_color"
    const val KEY_ISLAND_TEXT_COLOR = "island_text_color"
    const val KEY_HOOK_TEXT_COLOR = "key_hook_text_color"
    const val KEY_HOOK_RAINBOW_ENABLED = "key_hook_rainbow_enabled"
    const val KEY_ISLAND_ALBUM_COVER_ROTATION = "island_album_cover_rotation"
    const val KEY_ISLAND_AUTO_HIDE_COVER_SPECTRUM = "island_auto_hide_cover_spectrum"
    const val KEY_ISLAND_AUTO_HIDE_THRESHOLD = "island_auto_hide_threshold"
    const val KEY_ISLAND_HIDE_ON_PAUSE = "island_hide_on_pause"
    const val KEY_ISLAND_LYRIC_FILTER_MODE = "island_lyric_filter_mode"
    const val KEY_ISLAND_LYRIC_FILTER_PACKAGES_WHITE = "island_lyric_filter_packages_white"
    const val KEY_ISLAND_LYRIC_FILTER_PACKAGES_BLACK = "island_lyric_filter_packages_black"
    const val KEY_ISLAND_PROGRESS_BAR_ENABLED = "island_progress_bar_enabled"
    const val KEY_ISLAND_PROGRESS_BAR_WIDTH = "island_progress_bar_width"
    const val KEY_ISLAND_PROGRESS_BAR_COLOR_MODE = "island_progress_bar_color_mode"
    const val KEY_ISLAND_PROGRESS_BAR_PULSE = "island_progress_bar_pulse"
    const val KEY_ISLAND_PROGRESS_BAR_STYLE = "island_progress_bar_style"
    const val KEY_ISLAND_PROGRESS_BAR_TRACK_ENABLED = "island_progress_bar_track_enabled"
    // ===== 超级岛外发光/超级光效（老词幕 IslandOuterGlowHooker 移植，内外分开） =====
    const val KEY_ISLAND_OUTER_GLOW_ENABLED = "island_outer_glow_enabled"
    const val KEY_ISLAND_OUTER_GLOW_OUTER_ENABLED = "island_outer_glow_outer_enabled"
    const val KEY_ISLAND_OUTER_GLOW_INNER_ENABLED = "island_outer_glow_inner_enabled"
    const val KEY_ISLAND_OUTER_GLOW_OUTER_MODE = "island_outer_glow_outer_mode"
    const val KEY_ISLAND_OUTER_GLOW_OUTER_COLOR = "island_outer_glow_outer_color"
    const val KEY_ISLAND_OUTER_GLOW_INNER_MODE = "island_outer_glow_inner_mode"
    const val KEY_ISLAND_OUTER_GLOW_INNER_COLOR = "island_outer_glow_inner_color"
    // 渲染端同步键（extraWrite 写入，xposed 端读取）
    const val KEY_HOOK_ISLAND_OUTER_GLOW_ENABLED = "key_hook_island_outer_glow_enabled"
    const val KEY_HOOK_ISLAND_OUTER_GLOW_OUTER_ENABLED = "key_hook_island_outer_glow_outer_enabled"
    const val KEY_HOOK_ISLAND_OUTER_GLOW_INNER_ENABLED = "key_hook_island_outer_glow_inner_enabled"
    const val KEY_HOOK_ISLAND_OUTER_GLOW_OUTER_MODE = "key_hook_island_outer_glow_outer_mode"
    const val KEY_HOOK_ISLAND_OUTER_GLOW_OUTER_COLOR = "key_hook_island_outer_glow_outer_color"
    const val KEY_HOOK_ISLAND_OUTER_GLOW_INNER_MODE = "key_hook_island_outer_glow_inner_mode"
    const val KEY_HOOK_ISLAND_OUTER_GLOW_INNER_COLOR = "key_hook_island_outer_glow_inner_color"
    const val KEY_BIG_ISLAND_MEDIA_ENHANCE = "big_island_media_enhance"
    const val KEY_ISLAND_TRANSPARENT_ENABLED = "island_transparent_enabled"
    const val KEY_ISLAND_TRANSPARENT_BIG_ENABLED = "island_transparent_big_enabled"
    const val KEY_ISLAND_BLUR_BIG_ENABLED = "pref_island_blur_big_enabled"
    const val KEY_ISLAND_BLUR_SMALL_ENABLED = "pref_island_blur_small_enabled"
    const val KEY_ISLAND_BLUR_EXPAND_ENABLED = "pref_island_blur_expand_enabled"
    const val KEY_ISLAND_BG_BIG_PATH = "pref_island_bg_big_path"
    const val KEY_ISLAND_COVER_BG_ENABLED = "island_cover_bg_enabled"
    const val KEY_ISLAND_GLASS_ONLY_MEDIA = "island_glass_only_media"
    // ===== 毛玻璃（HyperIsland 移植：系统实时模糊挂岛背景，键名对齐 RootConstants） =====
    const val KEY_ISLAND_MATERIAL_ENABLED = "pref_island_material_enabled"
    /** 试验性功能免责声明已同意：只在第一次开启毛玻璃时弹框，同意一次后不再弹 */
    const val KEY_ISLAND_EXPERIMENT_CONSENT = "pref_island_experiment_consent"
    const val KEY_ISLAND_MATERIAL_TYPE = "pref_island_material_type"
    const val KEY_ISLAND_BLUR_BIG_RADIUS = "pref_island_blur_big_radius"
    const val KEY_ISLAND_BLUR_BIG_COLOR = "pref_island_blur_big_color"
    const val DEFAULT_ISLAND_MATERIAL_TYPE = 0
    // ===== 上游 HyperIsland 材质 JSON（设置页选材质类型时同步写入，上游只认此结构） =====
    const val KEY_ISLAND_MATERIAL_BIG = "pref_island_material_big_config"
    const val KEY_ISLAND_MATERIAL_SMALL = "pref_island_material_small_config"
    const val KEY_ISLAND_MATERIAL_EXPAND = "pref_island_material_expand_config"
    const val KEY_ISLAND_MATERIAL_SMALL_FOLLOW_BIG = "pref_island_material_small_follow_big"
    const val KEY_ISLAND_MATERIAL_EXPAND_FOLLOW_BIG = "pref_island_material_expand_follow_big"
    const val KEY_ISLAND_DISPLAY_TRANSLATION = "island_display_translation"
    const val KEY_ISLAND_DISPLAY_ROMA = "island_display_roma"
    const val KEY_ISLAND_RELATIVE_PROGRESS = "island_relative_progress"
    const val KEY_ISLAND_SUSTAIN_GLOW = "island_sustain_glow"
    const val KEY_ISLAND_TYPEFACE_PATH = "island_typeface_path"
    const val KEY_ISLAND_FONT_WEIGHT = "island_font_weight"
    const val KEY_ISLAND_TYPEFACE_BOLD = "island_typeface_bold"
    const val KEY_ISLAND_TYPEFACE_ITALIC = "island_typeface_italic"

    // ================= 读写 =================

    /**
     * 持久化修复（2026-10-29）：remote 与本地文件是**两份不同的存储**，必须同时维护。
     *
     * 实测（3.0.945 + LSPosed 102）：
     * - SystemUI 进程读的 remote prefs 落在框架库
     *   `/data/adb/lspd/config/modules_config.db` 的 module_configs 表，
     *   **不是** App 的 `shared_prefs/com.liuxiansheng.lyricon_preferences.xml`；
     * - 3.0.934 起 setXxx 在服务已绑定时只写 remote，本地文件不再更新 →
     *   设置页回显（读本地）与 SystemUI 生效值（读 remote）分叉；
     *   重进设置页 / 重启 App 后看起来"设置没记住"，文件里也缺键。
     *
     * 现在的策略：
     * - 写：remote（实时生效）+ 本地文件（持久镜像，可备份/导出/排障）；
     * - 读：本进程刚写的值 → remote → 本地文件 → 默认值；
     * - 服务未绑定期间只写进本地文件的键，绑定后自动补推 remote。
     */
    private fun localPrefs(context: Context): SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    /** remote 引用缓存（3.0.933）：服务绑定后只取一次，避免每次 set 都重建 Binder 引用（IPC） */
    @Volatile private var cachedRemoteService: Any? = null
    @Volatile private var cachedRemotePrefs: SharedPreferences? = null

    /** 本进程刚写入的值：remote 的 apply 是异步提交，保证"写后立即读"拿到新值。 */
    private val recentWrites = java.util.concurrent.ConcurrentHashMap<String, Any>()

    /** 服务未绑定期间只写进本地文件的键，绑定后补推 remote，避免 SystemUI 永远读不到。 */
    private val pendingRemote = java.util.concurrent.ConcurrentHashMap<String, Any>()

    /** remote：libxposed 跨进程 prefs（SystemUI 实际读取的那一份，存放在框架库里） */
    private fun remotePrefs(context: Context): SharedPreferences? {
        val svc = LyriconApp.xposedService
        if (svc === cachedRemoteService) return cachedRemotePrefs
        cachedRemoteService = svc
        val remote = runCatching { svc?.getRemotePreferences(PREF_NAME) }.getOrNull()
        cachedRemotePrefs = remote
        if (remote != null) flushPending(remote)
        return remote
    }

    /** 把"服务未绑定期间"只写进本地文件的值补推给 remote（此后 SystemUI 才能读到）。 */
    private fun flushPending(remote: SharedPreferences) {
        if (pendingRemote.isEmpty()) return
        val editor = remote.edit()
        pendingRemote.forEach { (key, value) -> editor.putValue(key, value) }
        runCatching { editor.apply() }
        pendingRemote.clear()
        android.util.Log.i("LyriconPrefsWrite", "pending values flushed to remote")
    }

    /** 按实际类型写入，避免 Boolean/Int/Float/String 互相踩类型。 */
    private fun SharedPreferences.Editor.putValue(key: String, value: Any): SharedPreferences.Editor =
        when (value) {
            is Boolean -> putBoolean(key, value)
            is Int -> putInt(key, value)
            is Float -> putFloat(key, value)
            is Long -> putLong(key, value)
            is String -> putString(key, value)
            else -> this
        }

    /** 岛背景管线的键（受「毛玻璃」总开关管控）。 */
    fun isBackgroundPipelineKey(key: String): Boolean {
        if (key == KEY_ISLAND_MATERIAL_ENABLED) return false
        return key.startsWith("pref_island_material_") ||
            key.startsWith("pref_island_blur_") ||
            key.startsWith("pref_island_glass_") ||
            key.startsWith("pref_island_refraction_") ||
            key.startsWith("pref_island_bg_")
    }

    /** 毛玻璃总开关是否开启（岛背景管线是否允许写入/生效）。 */
    fun isMaterialMasterOn(context: Context): Boolean =
        getBoolean(context, KEY_ISLAND_MATERIAL_ENABLED, false)

    /**
     * 单键写入：remote 实时生效 + 本地文件持久镜像 + 进程内回读缓存。
     * value == null 等价于 remove（SharedPreferences.putString(key, null) 语义）。
     */
    private fun write(context: Context, key: String, value: Any?) {
        // ★ 总开关门禁（2026-11-08）：「毛玻璃」总开关没开时，岛背景那一整块一律**拒绝写入**。
        //   纳入管控：材质 pref_island_material_*、模糊 pref_island_blur_*、玻璃 pref_island_glass_*、
        //   折射 pref_island_refraction_*、背景预设 pref_island_bg_*。
        //   明确不纳入：总开关自身（否则永远打不开）、透明/封面（用户口径：透明是独立功能）。
        if (isBackgroundPipelineKey(key) && !getBoolean(context, KEY_ISLAND_MATERIAL_ENABLED, false)) {
            android.util.Log.i("LyriconPrefsWrite", "ignored key=$key value=$value (毛玻璃总开关未开)")
            return
        }
        val remote = remotePrefs(context)
        if (value == null) {
            recentWrites.remove(key)
            pendingRemote.remove(key)
            if (remote != null) runCatching { remote.edit().remove(key).apply() }
        } else {
            recentWrites[key] = value
            if (remote != null) {
                runCatching { remote.edit().putValue(key, value).apply() }
            } else {
                pendingRemote[key] = value
            }
        }
        // 本地文件是 App 侧唯一的持久副本：无论 remote 是否可用都要同步，
        // 否则换机 / 清框架数据 / 导出设置时全部丢失。
        runCatching {
            val editor = localPrefs(context).edit()
            if (value == null) editor.remove(key) else editor.putValue(key, value)
            editor.apply()
        }
        android.util.Log.i(
            "LyriconPrefsWrite",
            "set key=$key value=$value remote=${remote != null}"
        )
    }

    // 3.0.934：notifyConfigChanged（广播桥）已移除——老慈木式读写下，
    // SystemUI 通过 remote prefs 的 SharedPreferences 监听实时生效，
    // 无需广播兜底（App 改设置时进程必然存活，监听有效）。

    override fun contains(context: Context, key: String): Boolean =
        recentWrites.containsKey(key) ||
            remotePrefs(context)?.contains(key) == true ||
            localPrefs(context).contains(key)

    override fun getBoolean(context: Context, key: String, default: Boolean): Boolean {
        (recentWrites[key] as? Boolean)?.let { return it }
        val remote = remotePrefs(context)
        if (remote != null && remote.contains(key)) return compatBoolean(remote, key, default)
        return compatBoolean(localPrefs(context), key, default)
    }

    private fun compatBoolean(p: SharedPreferences, key: String, default: Boolean): Boolean {
        runCatching { return p.getBoolean(key, default) }
        return when (val v = p.all[key]) {
            is Boolean -> v
            is String -> v.toBoolean()
            is Int -> v != 0
            else -> default
        }
    }

    override fun getInt(context: Context, key: String, default: Int): Int {
        when (val cached = recentWrites[key]) {
            is Int -> return cached
            is Boolean -> return if (cached) 1 else 0
            is String -> return if (cached == "none") 0 else 1
        }
        val remote = remotePrefs(context)
        if (remote != null && remote.contains(key)) return compatGetInt(remote, key, default)
        return compatGetInt(localPrefs(context), key, default)
    }

    /** 类型兼容读取 Int：旧版曾把某键写成 String（如动画值 "scale_up"/"none"），
     *  直接 getInt 会 ClassCastException 闪退——按实际类型分发，平滑迁移。 */
    private fun compatGetInt(p: SharedPreferences, key: String, default: Int): Int {
        runCatching { return p.getInt(key, default) }
        return when (val v = p.all[key]) {
            is Int -> v
            is String -> if (v == "none") 0 else 1
            is Boolean -> if (v) 1 else 0
            else -> default
        }
    }

    override fun getFloat(context: Context, key: String, default: Float): Float {
        (recentWrites[key] as? Number)?.let { return it.toFloat() }
        val remote = remotePrefs(context)
        if (remote != null && remote.contains(key)) return compatFloat(remote, key, default)
        return compatFloat(localPrefs(context), key, default)
    }

    private fun compatFloat(p: SharedPreferences, key: String, default: Float): Float {
        runCatching { return p.getFloat(key, default) }
        return when (val v = p.all[key]) {
            is Number -> v.toFloat()
            is String -> v.toFloatOrNull() ?: default
            else -> default
        }
    }

    override fun getString(context: Context, key: String, default: String?): String? {
        val cached = recentWrites[key]
        if (cached != null) return cached as? String ?: default
        val remote = remotePrefs(context)
        if (remote != null && remote.contains(key)) return compatString(remote, key, default)
        return compatString(localPrefs(context), key, default)
    }

    private fun compatString(p: SharedPreferences, key: String, default: String?): String? {
        runCatching { return p.getString(key, default) }
        return p.all[key]?.toString() ?: default
    }

    override fun setBoolean(context: Context, key: String, value: Boolean) = write(context, key, value)
    override fun setInt(context: Context, key: String, value: Int) = write(context, key, value)
    override fun setFloat(context: Context, key: String, value: Float) = write(context, key, value)
    override fun setString(context: Context, key: String, value: String?) = write(context, key, value)

    /**
     * 服务绑定后调用一次：先补推离线期间只写进本地文件的值，
     * 再用 remote 全量回填本地文件（文件才是可备份/可导出的持久副本）。
     */
    fun syncFromRemote(context: Context) {
        val remote = remotePrefs(context) ?: return
        val local = localPrefs(context)
        val existing = local.all
        val editor = local.edit()
        var changed = false
        remote.all.forEach { (key, value) ->
            if (value != null && existing[key] != value) {
                editor.putValue(key, value)
                changed = true
            }
        }
        if (changed) runCatching { editor.apply() }
        android.util.Log.i(
            "LyriconPrefsWrite",
            "sync remote->local: remote=${remote.all.size} local=${existing.size} changed=$changed"
        )
    }
}
