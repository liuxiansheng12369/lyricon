/*
 * HyperLyric - Copyright (C) 2026 limczhh
 * Licensed under GPL-3.0-or-later.
 *
 * Ported to Lyricon V3 by liuxiansheng.
 */
package com.liuxiansheng.lyricon.hyper.common

object RootConstants {
    // 状态栏歌词【独立】prefs 文件：与超级岛设置各是各的文本、各是各的控制
    const val PREF_NAME_STATUS_BAR_LYRIC = "com.liuxiansheng.lyricon_status_bar_preferences"
    // ================= HOOK & SUPER ISLAND KEYS =================
    const val KEY_HOOK_ENABLE_SUPER_ISLAND = "key_hook_enable_super_island"
    const val KEY_HOOK_ENABLE_STATUS_BAR_LYRIC = "key_hook_enable_status_bar_lyric"
    // ============ 状态栏歌词全套设置（对齐 HyperLyric StatusBarLyricSettingsPage） ============
    const val KEY_HOOK_STATUS_BAR_LYRIC_INSERTION_ORDER = "key_hook_status_bar_lyric_insertion_order"
    const val KEY_HOOK_STATUS_BAR_LYRIC_ICON_ENABLED = "key_hook_status_bar_lyric_icon_enabled"
    const val KEY_HOOK_STATUS_BAR_LYRIC_ICON_STYLE = "key_hook_status_bar_lyric_icon_style"
    const val KEY_HOOK_STATUS_BAR_LYRIC_ICON_ORDER = "key_hook_status_bar_lyric_icon_order"
    const val KEY_HOOK_STATUS_BAR_LYRIC_ICON_SIZE_DP = "key_hook_status_bar_lyric_icon_size_dp"
    const val KEY_HOOK_STATUS_BAR_LYRIC_ICON_SPACING_DP = "key_hook_status_bar_lyric_icon_spacing_dp"
    const val KEY_HOOK_STATUS_BAR_LYRIC_PORTRAIT_DYNAMIC_MAX_WIDTH = "key_hook_status_bar_lyric_portrait_dynamic_max_width"
    const val KEY_HOOK_STATUS_BAR_LYRIC_LANDSCAPE_DYNAMIC_MAX_WIDTH = "key_hook_status_bar_lyric_landscape_dynamic_max_width"
    const val KEY_HOOK_STATUS_BAR_LYRIC_PADDING_LEFT_DP = "key_hook_status_bar_lyric_padding_left_dp"
    const val KEY_HOOK_STATUS_BAR_LYRIC_PADDING_RIGHT_DP = "key_hook_status_bar_lyric_padding_right_dp"
    const val KEY_HOOK_STATUS_BAR_LYRIC_CLOCK_HIDE_BEHAVIOR = "key_hook_status_bar_lyric_clock_hide_behavior"
    const val KEY_HOOK_STATUS_BAR_LYRIC_NOTIFICATION_ICON_HIDE_BEHAVIOR = "key_hook_status_bar_lyric_notification_icon_hide_behavior"
    const val KEY_HOOK_STATUS_BAR_LYRIC_ISLAND_HIDE_BEHAVIOR = "key_hook_status_bar_lyric_island_hide_behavior"
    const val KEY_HOOK_STATUS_BAR_LYRIC_CONTENT_MODE = "key_hook_status_bar_lyric_content_mode"
    // 默认内容=歌词（= ISLAND_CONTENT_MODE_LYRIC=7，避免 const 前向引用）
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_CONTENT_MODE = 7
    const val KEY_HOOK_STATUS_BAR_LYRIC_ADJUST_WIDTH_FOR_SUPER_ISLAND = "key_hook_status_bar_lyric_adjust_width_for_super_island"
    const val KEY_HOOK_STATUS_BAR_LYRIC_HIDE_ON_LOCK_SCREEN = "key_hook_status_bar_lyric_hide_on_lock_screen"
    const val KEY_HOOK_STATUS_BAR_LYRIC_DOUBLE_TAP_ACTION = "key_hook_status_bar_lyric_double_tap_action"
    const val KEY_HOOK_STATUS_BAR_LYRIC_LONG_PRESS_ACTION = "key_hook_status_bar_lyric_long_press_action"
    const val KEY_HOOK_STATUS_BAR_LYRIC_SWIPE_LEFT_ACTION = "key_hook_status_bar_lyric_swipe_left_action"
    const val KEY_HOOK_STATUS_BAR_LYRIC_SWIPE_RIGHT_ACTION = "key_hook_status_bar_lyric_swipe_right_action"
    const val KEY_HOOK_STATUS_BAR_LYRIC_GESTURE_HAPTIC_FEEDBACK = "key_hook_status_bar_lyric_gesture_haptic_feedback"
    // 状态栏歌词样式（对齐超级岛 textSize/textColor 机制，独立 key 独立控制）
    const val KEY_HOOK_STATUS_BAR_LYRIC_TEXT_SIZE = "key_hook_status_bar_lyric_text_size"
    const val KEY_HOOK_STATUS_BAR_LYRIC_TEXT_COLOR = "key_hook_status_bar_lyric_text_color"
    // 状态栏歌词颜色模式（对齐超级岛彩虹/封面取色机制）
    const val KEY_HOOK_STATUS_BAR_LYRIC_RAINBOW_ENABLED = "key_hook_status_bar_lyric_rainbow_enabled"
    const val KEY_HOOK_STATUS_BAR_LYRIC_COVER_COLOR_ENABLED = "key_hook_status_bar_lyric_cover_color_enabled"
    // 状态栏歌词黑白背景反色（跟随状态栏背景亮度：亮背景→深色字，暗背景→浅色字）
    const val KEY_HOOK_STATUS_BAR_LYRIC_INVERT_ENABLED = "key_hook_status_bar_lyric_invert_enabled"
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_INVERT_ENABLED = false

    // 状态栏歌词字体样式（字体族 / 粗体 / 字重，直连 key_hook_* 独立控制）
    const val KEY_HOOK_STATUS_BAR_LYRIC_FONT_FAMILY = "key_hook_status_bar_lyric_font_family"
    const val KEY_HOOK_STATUS_BAR_LYRIC_BOLD = "key_hook_status_bar_lyric_bold"
    const val KEY_HOOK_STATUS_BAR_LYRIC_FONT_WEIGHT = "key_hook_status_bar_lyric_font_weight"
    // 字体族档位（映射 Typeface.create 的 family 名）
    const val STATUS_BAR_LYRIC_FONT_SANS_SERIF = 0
    const val STATUS_BAR_LYRIC_FONT_SANS_SERIF_MEDIUM = 1
    const val STATUS_BAR_LYRIC_FONT_SANS_SERIF_LIGHT = 2
    const val STATUS_BAR_LYRIC_FONT_SERIF = 3
    const val STATUS_BAR_LYRIC_FONT_MONOSPACE = 4
    const val STATUS_BAR_LYRIC_FONT_SANS_SERIF_CONDENSED = 5
    const val STATUS_BAR_LYRIC_FONT_SANS_SERIF_BLACK = 6
    const val STATUS_BAR_LYRIC_FONT_CURSIVE = 7
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_FONT_FAMILY = STATUS_BAR_LYRIC_FONT_SANS_SERIF
    // 粗体：保持现状默认加粗（原硬编码 BOLD）
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_BOLD = true
    // 字重档位（可变字体 wght 值，index 与设置页一致）
    const val STATUS_BAR_LYRIC_WEIGHT_THIN = 0       // 100
    const val STATUS_BAR_LYRIC_WEIGHT_LIGHT = 1      // 300
    const val STATUS_BAR_LYRIC_WEIGHT_NORMAL = 2     // 400
    const val STATUS_BAR_LYRIC_WEIGHT_MEDIUM = 3     // 500
    const val STATUS_BAR_LYRIC_WEIGHT_SEMI_BOLD = 4  // 600
    const val STATUS_BAR_LYRIC_WEIGHT_BOLD = 5       // 700
    const val STATUS_BAR_LYRIC_WEIGHT_BLACK = 6      // 900
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_FONT_WEIGHT = STATUS_BAR_LYRIC_WEIGHT_BOLD
    // 字重档位 → wght 数值映射（可变字体用）
    val STATUS_BAR_LYRIC_FONT_WEIGHT_VALUES = intArrayOf(100, 300, 400, 500, 600, 700, 900)

    const val STATUS_BAR_LYRIC_INSERTION_BEFORE_CLOCK = 0
    const val STATUS_BAR_LYRIC_INSERTION_AFTER_CLOCK = 1
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_INSERTION_ORDER = STATUS_BAR_LYRIC_INSERTION_AFTER_CLOCK

    // 图标样式枚举与设置页 PrefSpinner 的 index（0-based）对齐，避免错位一格
    const val STATUS_BAR_LYRIC_ICON_MUSIC_COVER = 0
    const val STATUS_BAR_LYRIC_ICON_CIRCLE_COVER = 1
    const val STATUS_BAR_LYRIC_ICON_ROTATING_COVER = 2
    const val STATUS_BAR_LYRIC_ICON_APP = 3
    const val STATUS_BAR_LYRIC_ICON_MONOCHROME = 4
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_ICON_ENABLED = false
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_ICON_STYLE = STATUS_BAR_LYRIC_ICON_MUSIC_COVER
    const val STATUS_BAR_LYRIC_ICON_BEFORE_LYRIC = 0
    const val STATUS_BAR_LYRIC_ICON_AFTER_LYRIC = 1
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_ICON_ORDER = STATUS_BAR_LYRIC_ICON_BEFORE_LYRIC
    const val STATUS_BAR_LYRIC_ICON_MIN_SIZE_DP = 10
    const val STATUS_BAR_LYRIC_ICON_MAX_SIZE_DP = 24
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_ICON_SIZE_DP = 16
    // 图标间距（对齐 HyperLyric：0~16dp，默认3dp；直连 key_hook_* 可调）
    const val STATUS_BAR_LYRIC_ICON_MIN_SPACING_DP = 0
    const val STATUS_BAR_LYRIC_ICON_MAX_SPACING_DP = 16
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_ICON_SPACING_DP = 3
    const val STATUS_BAR_LYRIC_ICON_SPACING_DP = DEFAULT_HOOK_STATUS_BAR_LYRIC_ICON_SPACING_DP

    const val STATUS_BAR_LYRIC_PORTRAIT_MAX_WIDTH_DP = 200
    const val STATUS_BAR_LYRIC_LANDSCAPE_MAX_WIDTH_DP = 400
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_PORTRAIT_DYNAMIC_MAX_WIDTH = 150
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_LANDSCAPE_DYNAMIC_MAX_WIDTH = 200
    const val DEFAULT_STATUS_BAR_LYRIC_TEXT_SIZE_SP = 13
    const val MIN_HOOK_STATUS_BAR_LYRIC_TEXT_SIZE_SP = 8
    const val MAX_HOOK_STATUS_BAR_LYRIC_TEXT_SIZE_SP = 20
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_TEXT_COLOR = 0xFFFFFFFF.toInt()
    // 状态栏默认彩虹（用户要求默认开启），封面取色默认关闭
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_RAINBOW_ENABLED = true
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_COVER_COLOR_ENABLED = false
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_PADDING_DP = 0f
    const val MIN_HOOK_STATUS_BAR_LYRIC_PADDING_DP = -32f
    const val MAX_HOOK_STATUS_BAR_LYRIC_PADDING_DP = 32f

    const val STATUS_BAR_LYRIC_CLOCK_HIDE_NONE = 0
    const val STATUS_BAR_LYRIC_CLOCK_HIDE_WHILE_PLAYING = 1
    const val STATUS_BAR_LYRIC_CLOCK_HIDE_WHEN_ISLAND_PRESENT = 2
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_CLOCK_HIDE_BEHAVIOR = STATUS_BAR_LYRIC_CLOCK_HIDE_WHILE_PLAYING

    const val STATUS_BAR_LYRIC_NOTIFICATION_ICON_HIDE_NONE = 0
    const val STATUS_BAR_LYRIC_NOTIFICATION_ICON_HIDE_WHILE_PLAYING = 1
    const val STATUS_BAR_LYRIC_NOTIFICATION_ICON_MOVE_RIGHT_WHILE_PLAYING = 2
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_NOTIFICATION_ICON_HIDE_BEHAVIOR = STATUS_BAR_LYRIC_NOTIFICATION_ICON_HIDE_WHILE_PLAYING

    const val STATUS_BAR_LYRIC_ISLAND_HIDE_NONE = 0
    const val STATUS_BAR_LYRIC_ISLAND_HIDE_WHILE_PLAYING = 1
    const val STATUS_BAR_LYRIC_ISLAND_HIDE_ALWAYS = 2
    // 3.0.935：状态栏歌词显示时超级岛默认隐藏（播放时，用户指定；原从不隐藏）
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_ISLAND_HIDE_BEHAVIOR = STATUS_BAR_LYRIC_ISLAND_HIDE_WHILE_PLAYING

    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_ADJUST_WIDTH_FOR_SUPER_ISLAND = true
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_HIDE_ON_LOCK_SCREEN = true

    const val STATUS_BAR_LYRIC_GESTURE_ACTION_NONE = 0
    const val STATUS_BAR_LYRIC_GESTURE_ACTION_TOGGLE_PLAYBACK = 1
    const val STATUS_BAR_LYRIC_GESTURE_ACTION_TEMPORARY_CLOCK = 2
    const val STATUS_BAR_LYRIC_GESTURE_ACTION_OPEN_MEDIA_APP = 3
    const val STATUS_BAR_LYRIC_GESTURE_ACTION_TEMPORARY_HIDE_LYRIC = 4
    const val STATUS_BAR_LYRIC_GESTURE_ACTION_OPEN_MEDIA_APP_FREEFORM = 5
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_DOUBLE_TAP_ACTION = STATUS_BAR_LYRIC_GESTURE_ACTION_NONE
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_LONG_PRESS_ACTION = STATUS_BAR_LYRIC_GESTURE_ACTION_TOGGLE_PLAYBACK

    const val STATUS_BAR_LYRIC_GESTURE_SWIPE_NONE = 0
    const val STATUS_BAR_LYRIC_GESTURE_SWIPE_PREVIOUS = 1
    const val STATUS_BAR_LYRIC_GESTURE_SWIPE_NEXT = 2
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_SWIPE_LEFT_ACTION = STATUS_BAR_LYRIC_GESTURE_SWIPE_NEXT
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_SWIPE_RIGHT_ACTION = STATUS_BAR_LYRIC_GESTURE_SWIPE_PREVIOUS
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_GESTURE_HAPTIC_FEEDBACK = true
    const val STATUS_BAR_LYRIC_GESTURE_SWIPE_THRESHOLD_DP = 50
    const val KEY_HOOK_LYRIC_MODE = "key_hook_lyric_mode"
    const val KEY_HOOK_LYRIC_SOURCE = "key_hook_lyric_source"
    const val DEFAULT_HOOK_LYRIC_SOURCE = "lyricon"
    const val KEY_HOOK_LYRICON_PROVIDER_DELAY_PREFIX = "key_hook_lyricon_provider_delay_"


    const val KEY_HOOK_ISLAND_ALBUM_COVER_STYLE = "key_hook_island_album_cover_style"
    const val KEY_HOOK_ISLAND_CONTENT_LEFT = "key_hook_island_content_left"
    const val KEY_HOOK_ISLAND_CONTENT_RIGHT = "key_hook_island_content_right"
    // 3.0.881：左右内容模式字符串键（与设置页 MasterPrefs 同名同值直连，55 种内容模式）
    const val KEY_ISLAND_LEFT_CONTENT_MODE = "island_left_content_mode"
    const val KEY_ISLAND_RIGHT_CONTENT_MODE = "island_right_content_mode"
    const val KEY_ISLAND_LEFT_ENABLED = "island_left_enabled"
    const val KEY_ISLAND_RIGHT_ENABLED = "island_right_enabled"
    const val KEY_HOOK_ISLAND_MUSIC_INFO_FIRST_LINE =
        "key_hook_island_music_info_first_line"
    const val KEY_HOOK_ISLAND_MUSIC_INFO_SECOND_LINE =
        "key_hook_island_music_info_second_line"
    const val KEY_HOOK_ISLAND_LEFT_PADDING_LEFT = "key_hook_island_left_padding_left"
    const val KEY_HOOK_ISLAND_LEFT_PADDING_RIGHT = "key_hook_island_left_padding_right"
    const val KEY_HOOK_ISLAND_RIGHT_PADDING_LEFT = "key_hook_island_right_padding_left"
    const val KEY_HOOK_ISLAND_RIGHT_PADDING_RIGHT = "key_hook_island_right_padding_right"
    const val KEY_HOOK_ISLAND_WIDTH_MODE = "key_hook_island_width_mode"
    const val KEY_HOOK_ISLAND_DYNAMIC_MIN_WIDTH = "key_hook_island_dynamic_min_width"
    const val KEY_HOOK_ISLAND_DYNAMIC_MAX_WIDTH = "key_hook_island_dynamic_max_width"
    const val KEY_HOOK_ISLAND_DYNAMIC_WIDTH_BASIS = "key_hook_island_dynamic_width_basis"
    const val KEY_HOOK_ISLAND_DISABLE_WIDTH_LIMIT = "key_hook_island_disable_width_limit"
    // 频谱封面隐藏（老词幕 spectrumLinkWidthEnabled / autoHideCoverSpectrum 移植）
    const val KEY_ISLAND_SPECTRUM_LINK_WIDTH = "island_spectrum_link_width_enabled"
    const val KEY_ISLAND_AUTO_HIDE_COVER_SPECTRUM = "island_auto_hide_cover_spectrum"
    const val KEY_ISLAND_AUTO_HIDE_THRESHOLD = "island_auto_hide_threshold"
    const val DEFAULT_ISLAND_SPECTRUM_LINK_WIDTH = true
    const val DEFAULT_ISLAND_AUTO_HIDE_COVER_SPECTRUM = true
    const val DEFAULT_ISLAND_AUTO_HIDE_THRESHOLD = 12
    const val KEY_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE = "key_hook_island_behavior_after_pause"
    const val KEY_HOOK_ISLAND_LONG_PRESS_BEHAVIOR = "key_hook_island_long_press_behavior"

    const val KEY_HOOK_REMOVE_FOCUS_WHITELIST = "key_hook_remove_focus_whitelist"
    const val KEY_HOOK_REMOVE_ISLAND_WHITELIST = "key_hook_remove_island_whitelist"

    // ================= STYLE & TYPOGRAPHY KEYS =================
    const val KEY_HOOK_TEXT_SIZE = "key_hook_text_size"
    const val KEY_HOOK_TEXT_SIZE_RATIO = "key_hook_text_size_ratio"
    const val KEY_HOOK_FONT_WEIGHT = "key_hook_font_weight"
    const val KEY_HOOK_FONT_ITALIC = "key_hook_font_italic"
    const val KEY_HOOK_GRADIENT_PROGRESS = "key_hook_gradient_progress"
    const val KEY_HOOK_CENTER_LYRIC = "key_hook_center_lyric"
    const val KEY_HOOK_CENTER_MUSIC_INFO = "key_hook_center_music_info"
    const val KEY_HOOK_RIGHT_LYRIC = "key_hook_right_lyric"
    const val KEY_HOOK_PLACEHOLDER_FORMAT = "key_hook_placeholder_format"
    const val KEY_HOOK_ISLAND_MUSIC_WAVE_STYLE = "key_hook_island_music_wave_style"

    // ================= ANIMATION KEYS =================
    const val KEY_HOOK_ANIM_ENABLE = "key_hook_anim_enable"
    const val KEY_HOOK_ANIM_ID = "key_hook_anim_id"

    // ================= SYLLABLE KEYS =================
    const val KEY_HOOK_SYLLABLE_RELATIVE = "key_hook_syllable_relative"
    const val KEY_HOOK_SYLLABLE_HIGHLIGHT = "key_hook_syllable_highlight"
    const val KEY_HOOK_SYLLABLE_LINE_DISPLAY = "key_hook_syllable_line_display"

    // ================= TRANSLATION KEYS =================
    /** 显示翻译（V3 直写键：true=显示翻译，开=开）。渲染端优先读本键，回退 disable 取反兼容旧配置 */
    const val KEY_SHOW_TRANSLATION = "v3_show_translation"
    const val KEY_HOOK_DISABLE_TRANSLATION = "key_hook_disable_translation"
    const val KEY_HOOK_TRANSLATION_ONLY = "key_hook_translation_only"
    const val KEY_HOOK_SWAP_TRANSLATION = "key_hook_swap_translation"
    const val KEY_HOOK_NEXT_LYRIC_LINE = "key_hook_next_lyric_line"
    const val KEY_HOOK_AUTO_SWITCH_TRANSLATION = "key_hook_auto_switch_translation"

    // 下一句动画效果（预览副行提升为主行时的过渡动画；Int 索引，与设置页 PrefSpinner 对应）
    const val KEY_HOOK_LINE_SWITCH_ANIM = "key_hook_line_switch_anim"
    const val LINE_SWITCH_ANIM_NONE = 0
    const val LINE_SWITCH_ANIM_SCALE_UP = 1
    // 状态栏歌词单独的换句动画（与超级岛分开配置：同一套动画实现，各自一个键）
    const val KEY_HOOK_STATUS_BAR_LYRIC_SWITCH_ANIM = "key_hook_status_bar_lyric_switch_anim"
    // 状态栏歌词单独的"持续动画效果"（0=静态，1~25 与超级岛同一套效果编号，但配置键独立）
    const val KEY_HOOK_STATUS_BAR_LYRIC_EFFECT = "key_hook_status_bar_lyric_effect"
    const val DEFAULT_HOOK_STATUS_BAR_LYRIC_EFFECT = 0

    // ================= COLOR KEYS =================
    const val KEY_HOOK_TEXT_COLOR_STYLE = "key_hook_text_color_style"
    /** 用户自定义固定歌词颜色（ARGB Int），V3 设置页"文字颜色"写入 */
    const val KEY_HOOK_TEXT_COLOR = "key_hook_text_color"
    /** 彩虹歌词开关（老词幕 island_rainbow_enabled 对应渲染键） */
    const val KEY_HOOK_RAINBOW_ENABLED = "key_hook_rainbow_enabled"
    const val KEY_HOOK_ISLAND_PROGRESS_GLOW = "key_hook_island_progress_glow"
    const val KEY_HOOK_ISLAND_PROGRESS_STYLE = "key_hook_island_progress_style"
    const val KEY_HOOK_ISLAND_PROGRESS_GRADIENT = "key_hook_island_progress_gradient"
    /** 进度条底圈（环绕岛的完整轨道）开关；关闭时只画进度弧不画底圈 */
    const val KEY_HOOK_ISLAND_PROGRESS_TRACK = "key_hook_island_progress_track"
    const val DEFAULT_HOOK_ISLAND_PROGRESS_TRACK = true

    // ================= SUPER ISLAND OUTER GLOW KEYS（超级岛外发光/超级光效，老词幕 IslandOuterGlowHooker 移植，内外分开） =================
    const val KEY_HOOK_ISLAND_OUTER_GLOW_ENABLED = "key_hook_island_outer_glow_enabled"
    /** 外圈独立开关（总开关之外，各自控制） */
    const val KEY_HOOK_ISLAND_OUTER_GLOW_OUTER_ENABLED = "key_hook_island_outer_glow_outer_enabled"
    /** 内圈独立开关（总开关之外，各自控制） */
    const val KEY_HOOK_ISLAND_OUTER_GLOW_INNER_ENABLED = "key_hook_island_outer_glow_inner_enabled"
    /** 外圈颜色模式：0=跟随封面 1=固定色 */
    const val KEY_HOOK_ISLAND_OUTER_GLOW_OUTER_MODE = "key_hook_island_outer_glow_outer_mode"
    const val KEY_HOOK_ISLAND_OUTER_GLOW_OUTER_COLOR = "key_hook_island_outer_glow_outer_color"
    /** 内圈颜色模式：0=跟随封面 1=固定色 */
    const val KEY_HOOK_ISLAND_OUTER_GLOW_INNER_MODE = "key_hook_island_outer_glow_inner_mode"
    const val KEY_HOOK_ISLAND_OUTER_GLOW_INNER_COLOR = "key_hook_island_outer_glow_inner_color"
    const val DEFAULT_HOOK_ISLAND_OUTER_GLOW_ENABLED = false
    /** 外圈/内圈默认开启（总开关开时内外圈各自生效，可分别关闭） */
    const val DEFAULT_HOOK_ISLAND_OUTER_GLOW_OUTER_ENABLED = true
    const val DEFAULT_HOOK_ISLAND_OUTER_GLOW_INNER_ENABLED = true
    const val ISLAND_OUTER_GLOW_MODE_FOLLOW_COVER = 0
    const val ISLAND_OUTER_GLOW_MODE_FIXED = 1
    const val DEFAULT_HOOK_ISLAND_OUTER_GLOW_OUTER_COLOR = 0xFF4285F4.toInt() // Google 蓝
    const val DEFAULT_HOOK_ISLAND_OUTER_GLOW_INNER_COLOR = 0xFF4285F4.toInt() // Google 蓝

    // ================= FONT KEYS =================
    const val KEY_HOOK_CUSTOM_FONT_PATH = "key_hook_custom_font_path"
    const val KEY_HOOK_NARROW_LATIN_FONT = "key_hook_narrow_latin_font"

    // ================= WORD MOTION KEYS =================
    const val KEY_HOOK_WORD_MOTION_ENABLED = "key_hook_word_motion_enabled"

    // ================= DEFAULTS =================
    const val DEFAULT_HOOK_LYRIC_MODE = 0
    const val DEFAULT_HOOK_LYRICON_PROVIDER_DELAY = 0
    const val MIN_HOOK_LYRICON_PROVIDER_DELAY = -5000
    const val MAX_HOOK_LYRICON_PROVIDER_DELAY = 5000

    const val DEFAULT_HOOK_ENABLE_SUPER_ISLAND = false
    const val DEFAULT_HOOK_ENABLE_DYNAMIC_ISLAND = false

    // 岛透明模式（预留）：用户主动开启"背景透明"时，防透明纠偏必须跳过——
    // 透明是用户要的效果，背景保持 GONE/透明，纠偏不得恢复背景。
    const val KEY_ISLAND_TRANSPARENT_ENABLED = "island_transparent_enabled"
    const val DEFAULT_ISLAND_TRANSPARENT_ENABLED = false
    const val ISLAND_ALBUM_COVER_STYLE_DEFAULT = 0
    const val ISLAND_ALBUM_COVER_STYLE_CIRCLE = 1
    const val ISLAND_ALBUM_COVER_STYLE_APP_ICON = 2
    const val ISLAND_ALBUM_COVER_STYLE_ROTATING_CIRCLE = 3
    const val ISLAND_ALBUM_COVER_STYLE_HIDDEN = 4
    const val DEFAULT_HOOK_ISLAND_ALBUM_COVER_STYLE = ISLAND_ALBUM_COVER_STYLE_DEFAULT
    const val DEFAULT_HOOK_REMOVE_FOCUS_WHITELIST = false
    const val DEFAULT_HOOK_REMOVE_ISLAND_WHITELIST = false
    const val ISLAND_CONTENT_MODE_NONE = 0
    const val ISLAND_CONTENT_MODE_LYRIC = 7
    const val ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO = 8
    const val DEFAULT_HOOK_ISLAND_CONTENT_LEFT = ISLAND_CONTENT_MODE_CUSTOM_MUSIC_INFO
    const val DEFAULT_HOOK_ISLAND_CONTENT_RIGHT = ISLAND_CONTENT_MODE_LYRIC
    const val DEFAULT_HOOK_ISLAND_MUSIC_INFO_FIRST_LINE = "title"
    const val DEFAULT_HOOK_ISLAND_MUSIC_INFO_SECOND_LINE = "artist"
    const val DEFAULT_HOOK_ISLAND_LEFT_PADDING_LEFT = 2
    // 贴摄像头侧避让：默认 0dp（用户指定收紧）
    const val DEFAULT_HOOK_ISLAND_LEFT_PADDING_RIGHT = 0
    const val DEFAULT_HOOK_ISLAND_RIGHT_PADDING_LEFT = 0
    const val DEFAULT_HOOK_ISLAND_RIGHT_PADDING_RIGHT = 0
    const val ISLAND_WIDTH_MODE_FIXED = 0
    const val ISLAND_WIDTH_MODE_DYNAMIC = 1
    // 3.0.935：动态宽度收缩默认开（用户指定，原固定）
    const val DEFAULT_HOOK_ISLAND_WIDTH_MODE = ISLAND_WIDTH_MODE_DYNAMIC
    const val DEFAULT_HOOK_ISLAND_DYNAMIC_MIN_WIDTH = 22
    // 动态单侧最大宽度：对齐原始词幕 island_dynamic_split_max_width_dp=130dp
    const val DEFAULT_HOOK_ISLAND_DYNAMIC_MAX_WIDTH = 130
    const val ISLAND_DYNAMIC_WIDTH_BASIS_ALL = 0
    const val ISLAND_DYNAMIC_WIDTH_BASIS_LYRIC_ONLY = 1
    const val DEFAULT_HOOK_ISLAND_DYNAMIC_WIDTH_BASIS = ISLAND_DYNAMIC_WIDTH_BASIS_ALL
    const val DEFAULT_HOOK_ISLAND_DISABLE_WIDTH_LIMIT = false
    const val DEFAULT_HOOK_ISLAND_BEHAVIOR_AFTER_PAUSE = 0
    const val ISLAND_LONG_PRESS_BEHAVIOR_LYRIC_SHARE = 0
    const val ISLAND_LONG_PRESS_BEHAVIOR_TOGGLE_PLAYBACK = 1
    const val DEFAULT_HOOK_ISLAND_LONG_PRESS_BEHAVIOR =
        ISLAND_LONG_PRESS_BEHAVIOR_LYRIC_SHARE
    const val NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_DISABLED = 0
    const val NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_DYNAMIC = 1
    const val NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_COVER_COLOR = 2
    const val NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_CUSTOM_FULL = 3
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE =
        NOTIFICATION_MEDIA_AMBIENT_FLOW_MODE_DISABLED
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_ENABLED = false
    const val NOTIFICATION_MEDIA_CARD_SWITCHER_MODE_SINGLE = 0
    const val NOTIFICATION_MEDIA_CARD_SWITCHER_MODE_MULTI = 1
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MODE =
        NOTIFICATION_MEDIA_CARD_SWITCHER_MODE_MULTI
    const val MIN_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MAX_COUNT = 2
    const val MAX_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MAX_COUNT = 6
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_CARD_SWITCHER_MAX_COUNT = 3
    const val ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE_DEFAULT = 0
    const val ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE_DISABLED = 1
    const val ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE_COVER_COLOR = 2
    const val ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE_CUSTOM_FULL = 3
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE =
        ISLAND_EXPANDED_MEDIA_AMBIENT_FLOW_MODE_DEFAULT
    const val MEDIA_CARD_THEME_FOLLOW_SYSTEM = 0
    const val MEDIA_CARD_THEME_ALWAYS_LIGHT = 1
    const val MEDIA_CARD_THEME_ALWAYS_DARK = 2
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_CARD_THEME = MEDIA_CARD_THEME_FOLLOW_SYSTEM
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_CARD_THEME = MEDIA_CARD_THEME_ALWAYS_DARK
    const val DEFAULT_HOOK_AOD_DISABLE_MEDIA_CARD_COLLAPSING = false
    const val NOTIFICATION_MEDIA_LAYOUT_STYLE_SYSTEM = 0
    const val NOTIFICATION_MEDIA_LAYOUT_STYLE_IOS = 1
    const val NOTIFICATION_MEDIA_LAYOUT_STYLE_COLOROS = 2
    const val NOTIFICATION_MEDIA_LAYOUT_STYLE_ONEUI = 3
    const val NOTIFICATION_MEDIA_LAYOUT_STYLE_MIUI = 4
    const val NOTIFICATION_MEDIA_LAYOUT_STYLE_PIXEL = 5
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_LAYOUT_STYLE =
        NOTIFICATION_MEDIA_LAYOUT_STYLE_SYSTEM
    const val ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_SYSTEM = 0
    const val ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_IOS = 1
    const val ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_COLOROS = 2
    const val ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_ONEUI = 3
    const val ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_MIUI = 4
    const val ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_PIXEL = 5
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE =
        ISLAND_EXPANDED_MEDIA_LAYOUT_STYLE_SYSTEM
    const val NOTIFICATION_MEDIA_COVER_STYLE_DEFAULT = 0
    const val NOTIFICATION_MEDIA_COVER_STYLE_CIRCLE = 1
    const val NOTIFICATION_MEDIA_COVER_STYLE_ROTATING_CIRCLE = 2
    const val NOTIFICATION_MEDIA_COVER_STYLE_HIDDEN = 3
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_COVER_STYLE =
        NOTIFICATION_MEDIA_COVER_STYLE_DEFAULT
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_COVER_SOURCE = false
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_COVER_SHADOW = false
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_DEVICE_SWITCH = false
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_TIME = false
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_HIDE_CUSTOM_ACTIONS = false
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_DISABLE_COVER_FLIP = false
    const val NOTIFICATION_MEDIA_PROGRESS_STYLE_DEFAULT = 0
    const val NOTIFICATION_MEDIA_PROGRESS_STYLE_WAVE = 1
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_PROGRESS_STYLE = NOTIFICATION_MEDIA_PROGRESS_STYLE_DEFAULT
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_PROGRESS_HEAD_GLOW = false
    const val NOTIFICATION_MEDIA_THUMB_STYLE_DEFAULT = 0
    const val NOTIFICATION_MEDIA_THUMB_STYLE_VERTICAL = 1
    const val NOTIFICATION_MEDIA_THUMB_STYLE_HIDDEN = 2
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_THUMB_STYLE = NOTIFICATION_MEDIA_THUMB_STYLE_DEFAULT
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_ACTION_ALIGN_LEFT = false
    const val NOTIFICATION_MEDIA_ACTION_ORDER_DEFAULT = 0
    const val NOTIFICATION_MEDIA_ACTION_ORDER_CUSTOM_RIGHT = 1
    const val NOTIFICATION_MEDIA_ACTION_ORDER_PLAY_LEFT = 2
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_ACTION_ORDER = NOTIFICATION_MEDIA_ACTION_ORDER_DEFAULT
    const val MEDIA_SOFT_COVER_TONE_LIGHT = 0
    const val MEDIA_SOFT_COVER_TONE_DARK = 1
    const val MEDIA_SOFT_COVER_TONE_FOLLOW_SYSTEM = 2

    const val NOTIFICATION_MEDIA_BACKGROUND_STYLE_DEFAULT = 0
    const val NOTIFICATION_MEDIA_BACKGROUND_STYLE_COVER_ART = 1
    const val NOTIFICATION_MEDIA_BACKGROUND_STYLE_BLURRED_COVER = 2
    const val NOTIFICATION_MEDIA_BACKGROUND_STYLE_RADIAL_GRADIENT = 3
    const val NOTIFICATION_MEDIA_BACKGROUND_STYLE_LINEAR_GRADIENT = 4
    const val NOTIFICATION_MEDIA_BACKGROUND_STYLE_SOFT_COVER = 5
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_BACKGROUND_STYLE =
        NOTIFICATION_MEDIA_BACKGROUND_STYLE_DEFAULT
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_BACKGROUND_BLUR = 10
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_BACKGROUND_COLOR_ANIMATION = false
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_BACKGROUND_AUTO_INVERT = false
    const val DEFAULT_HOOK_NOTIFICATION_MEDIA_SOFT_COVER_TONE = MEDIA_SOFT_COVER_TONE_DARK
    const val ISLAND_EXPANDED_MEDIA_COVER_STYLE_DEFAULT = 0
    const val ISLAND_EXPANDED_MEDIA_COVER_STYLE_CIRCLE = 1
    const val ISLAND_EXPANDED_MEDIA_COVER_STYLE_ROTATING_CIRCLE = 2
    const val ISLAND_EXPANDED_MEDIA_COVER_STYLE_HIDDEN = 3
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_COVER_STYLE =
        ISLAND_EXPANDED_MEDIA_COVER_STYLE_DEFAULT
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_COVER_SOURCE = false
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_DEVICE_SWITCH = false
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_TIME = false
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_HIDE_CUSTOM_ACTIONS = false
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_DISABLE_COVER_FLIP = false
    const val ISLAND_EXPANDED_MEDIA_PROGRESS_STYLE_DEFAULT = 0
    const val ISLAND_EXPANDED_MEDIA_PROGRESS_STYLE_WAVE = 1
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_PROGRESS_STYLE = ISLAND_EXPANDED_MEDIA_PROGRESS_STYLE_DEFAULT
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_PROGRESS_HEAD_GLOW = true
    const val ISLAND_EXPANDED_MEDIA_THUMB_STYLE_DEFAULT = 0
    const val ISLAND_EXPANDED_MEDIA_THUMB_STYLE_VERTICAL = 1
    const val ISLAND_EXPANDED_MEDIA_THUMB_STYLE_HIDDEN = 2
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_THUMB_STYLE = ISLAND_EXPANDED_MEDIA_THUMB_STYLE_DEFAULT
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_ACTION_ALIGN_LEFT = false
    const val ISLAND_EXPANDED_MEDIA_ACTION_ORDER_DEFAULT = 0
    const val ISLAND_EXPANDED_MEDIA_ACTION_ORDER_CUSTOM_RIGHT = 1
    const val ISLAND_EXPANDED_MEDIA_ACTION_ORDER_PLAY_LEFT = 2
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_ACTION_ORDER = ISLAND_EXPANDED_MEDIA_ACTION_ORDER_DEFAULT
    const val ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE_DEFAULT = 0
    const val ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE_COVER_ART = 1
    const val ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE_BLURRED_COVER = 2
    const val ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE_RADIAL_GRADIENT = 3
    const val ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE_LINEAR_GRADIENT = 4
    const val ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE_SOFT_COVER = 5
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE =
        ISLAND_EXPANDED_MEDIA_BACKGROUND_STYLE_DEFAULT
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_BLUR = 10
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_COLOR_ANIMATION = false
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_BACKGROUND_AUTO_INVERT = false
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_SOFT_COVER_TONE = MEDIA_SOFT_COVER_TONE_DARK
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_COVER_SIZE = 50f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_COVER_TOP = 15f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_COVER_START = 15f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_TITLE_TOP = 19f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_INFO_START_GAP = 12f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_ARTIST_GAP = 3f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_MUSIC_WAVE_SIZE = 24f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_MUSIC_WAVE_TOP = 24f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_MUSIC_WAVE_END = 24f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_PROGRESS_TOP_GAP = 5f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_PROGRESS_HEIGHT = 38f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_PROGRESS_TIME_GAP = 0f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_TIME_OUTER_MARGIN = 12f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_ACTION_WIDTH = 60f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_ACTION_HEIGHT = 50f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_ACTION_BOTTOM = 12f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_ACTION_OUTER_MARGIN = 6f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_ACTION_SCALE_PERCENT = 100f
    const val DEFAULT_HOOK_ISLAND_EXPANDED_MEDIA_IOS_DEVICE_SWITCH_SCALE_PERCENT = 90f


    const val DEFAULT_HOOK_TEXT_SIZE = 12
    const val DEFAULT_HOOK_TEXT_SIZE_RATIO = 0.7f
    const val DEFAULT_HOOK_FONT_WEIGHT = 600
    const val DEFAULT_HOOK_FONT_ITALIC = false
    const val DEFAULT_HOOK_NARROW_LATIN_FONT = false
    const val DEFAULT_HOOK_GRADIENT_PROGRESS = true
    const val DEFAULT_HOOK_CENTER_LYRIC = false
    const val DEFAULT_HOOK_CENTER_MUSIC_INFO = false
    const val DEFAULT_HOOK_RIGHT_LYRIC = false
    const val PLACEHOLDER_FORMAT_NONE = 0
    const val PLACEHOLDER_FORMAT_TITLE_ARTIST = 1
    const val PLACEHOLDER_FORMAT_TITLE = 2
    const val PLACEHOLDER_FORMAT_COUNTDOWN = 3
    // 默认占位：歌名（歌词出来之前显示歌名，让人知道功能已生效）
    const val DEFAULT_HOOK_PLACEHOLDER_FORMAT = PLACEHOLDER_FORMAT_TITLE
    const val ISLAND_MUSIC_WAVE_STYLE_DEFAULT = 0
    const val ISLAND_MUSIC_WAVE_STYLE_COVER_COLOR = 1
    const val ISLAND_MUSIC_WAVE_STYLE_COVER_GRADIENT = 2
    const val ISLAND_MUSIC_WAVE_STYLE_HIDDEN = 3
    const val DEFAULT_HOOK_ISLAND_MUSIC_WAVE_STYLE = ISLAND_MUSIC_WAVE_STYLE_DEFAULT
    const val DEFAULT_HOOK_ANIM_ENABLE = false
    const val DEFAULT_HOOK_ANIM_ID = "default"

    const val DEFAULT_HOOK_SYLLABLE_RELATIVE = true
    const val DEFAULT_HOOK_SYLLABLE_HIGHLIGHT = false
    const val DEFAULT_HOOK_SYLLABLE_LINE_DISPLAY = false

    const val DEFAULT_HOOK_DISABLE_TRANSLATION = false
    const val DEFAULT_HOOK_TRANSLATION_ONLY = false
    const val DEFAULT_HOOK_SWAP_TRANSLATION = false
    const val DEFAULT_HOOK_NEXT_LYRIC_LINE = false
    const val DEFAULT_HOOK_AUTO_SWITCH_TRANSLATION = false
    const val DEFAULT_HOOK_LINE_SWITCH_ANIM = LINE_SWITCH_ANIM_SCALE_UP
    const val TEXT_COLOR_STYLE_DEFAULT = 0
    const val TEXT_COLOR_STYLE_COVER_COLOR = 1
    const val TEXT_COLOR_STYLE_COVER_GRADIENT = 2
    const val TEXT_COLOR_STYLE_FOLLOW_STATUS_BAR = 3
    const val TEXT_COLOR_STYLE_RAINBOW = 4
    const val TEXT_COLOR_STYLE_CUSTOM = 5
    // 3.0.935：歌词颜色默认彩色（彩虹 7 色渐变，用户指定，原默认色）
    const val DEFAULT_HOOK_TEXT_COLOR_STYLE = TEXT_COLOR_STYLE_RAINBOW
    const val DEFAULT_HOOK_ISLAND_GLOW_EXTRACT_COLOR = false
    const val DEFAULT_HOOK_ISLAND_PROGRESS_GLOW = false
    const val DEFAULT_HOOK_ISLAND_PROGRESS_GRADIENT = false
    const val ISLAND_PROGRESS_STYLE_TOP_CLOCKWISE = 0
    const val ISLAND_PROGRESS_STYLE_RIGHT_CLOCKWISE = 1
    const val ISLAND_PROGRESS_STYLE_BOTTOM_CLOCKWISE = 2
    const val ISLAND_PROGRESS_STYLE_LEFT_CLOCKWISE = 3
    const val ISLAND_PROGRESS_STYLE_LEFT_BIDIRECTIONAL = 4
    const val ISLAND_PROGRESS_STYLE_TOP_BIDIRECTIONAL = 5
    const val ISLAND_PROGRESS_STYLE_BOTTOM_BIDIRECTIONAL = 6
    const val DEFAULT_HOOK_ISLAND_PROGRESS_STYLE = ISLAND_PROGRESS_STYLE_TOP_CLOCKWISE
    const val DEFAULT_HOOK_WORD_MOTION_ENABLED = false

    // 歌词镜像倒影（key 与设置页 MasterPrefs.KEY_ISLAND_LYRIC_MIRROR 一致）
    const val KEY_HOOK_ISLAND_LYRIC_MIRROR = "island_lyric_mirror"
    const val DEFAULT_HOOK_ISLAND_LYRIC_MIRROR = false

    // 动画总开关（key = ANIMATION KEYS 段的 key_hook_anim_enable，与设置页 MasterPrefs.KEY_ANIM_ENABLE 一致）
    // 关掉时：歌词动画效果=静态、镜像倒影/换句动画/逐字跳动一并停用（与设置页"子项收起"一致）
    // 注意：设置页该开关 default=true，渲染端读取也必须按 true 兜底，否则没设置过的用户会全被关掉

    // 歌词动画效果（0=静态，1~25=老词幕效果，与设置页 MasterPrefs.KEY_ISLAND_LYRIC_EFFECT 一致）
    const val KEY_HOOK_LYRIC_EFFECT = "island_lyric_effect"
    const val DEFAULT_HOOK_LYRIC_EFFECT = 0

    // ============ 岛毛玻璃/磨砂材质（对齐 HyperIsland 键名，Hub 下载预设可直接写入） ============
    // 总开关
    const val KEY_ISLAND_MATERIAL_ENABLED = "pref_island_material_enabled"
    const val DEFAULT_ISLAND_MATERIAL_ENABLED = false
    // 材质类型（gaussian / highlight_glass / liquid_glass / soft_glass / default）
    const val KEY_ISLAND_MATERIAL_TYPE = "pref_island_material_type"
    const val DEFAULT_ISLAND_MATERIAL_TYPE = "default"
    // 材质 JSON 配置（对齐 HyperIsland pref_island_material_big_config 结构）
    const val KEY_ISLAND_MATERIAL_BIG = "pref_island_material_big_config"
    const val KEY_ISLAND_MATERIAL_SMALL = "pref_island_material_small_config"
    const val KEY_ISLAND_MATERIAL_EXPAND = "pref_island_material_expand_config"
    const val KEY_ISLAND_MATERIAL_SMALL_FOLLOW_BIG = "pref_island_material_small_follow_big"
    const val KEY_ISLAND_MATERIAL_EXPAND_FOLLOW_BIG = "pref_island_material_expand_follow_big"
    // 模糊（对齐 HyperIsland pref_island_blur_*）
    const val KEY_ISLAND_BLUR_SMALL_ENABLED = "pref_island_blur_small_enabled"
    const val KEY_ISLAND_BLUR_SMALL_RADIUS = "pref_island_blur_small_radius"
    const val KEY_ISLAND_BLUR_SMALL_COLOR = "pref_island_blur_small_color"
    const val KEY_ISLAND_BLUR_BIG_ENABLED = "pref_island_blur_big_enabled"
    const val KEY_ISLAND_BLUR_BIG_RADIUS = "pref_island_blur_big_radius"
    const val KEY_ISLAND_BLUR_BIG_COLOR = "pref_island_blur_big_color"
    const val KEY_ISLAND_BLUR_EXPAND_ENABLED = "pref_island_blur_expand_enabled"
    const val KEY_ISLAND_BLUR_EXPAND_RADIUS = "pref_island_blur_expand_radius"
    const val KEY_ISLAND_BLUR_EXPAND_COLOR = "pref_island_blur_expand_color"
    const val DEFAULT_ISLAND_BLUR_RADIUS = 35
    const val DEFAULT_ISLAND_BLUR_COLOR = 0x14FFFFFF
    // 背景图路径（对齐 HyperIsland pref_island_bg_*，Hub 预设可直接写入）
    const val KEY_ISLAND_BG_BIG = "pref_island_bg_big_path"
    const val KEY_ISLAND_BG_SMALL = "pref_island_bg_small_path"
    const val KEY_ISLAND_BG_EXPAND = "pref_island_bg_expand_path"
    // 液态/柔光参数（对齐 HyperIsland pref_island_glass_*）
    const val KEY_ISLAND_GLASS_ENABLED = "pref_island_glass_enabled"
    const val DEFAULT_ISLAND_GLASS_ENABLED = true
    const val KEY_ISLAND_GLASS_BIG_ENABLED = "pref_island_glass_big_enabled"
    const val KEY_ISLAND_GLASS_SMALL_ENABLED = "pref_island_glass_small_enabled"
    const val KEY_ISLAND_GLASS_EXPAND_ENABLED = "pref_island_glass_expand_enabled"
    const val KEY_ISLAND_REFRACTION_BIG_ENABLED = "pref_island_refraction_big_enabled"
    const val KEY_ISLAND_REFRACTION_SMALL_ENABLED = "pref_island_refraction_small_enabled"
    const val KEY_ISLAND_REFRACTION_EXPAND_ENABLED = "pref_island_refraction_expand_enabled"
    const val KEY_ISLAND_GLASS_EDGE_WIDTH = "pref_island_glass_edge_width"
    const val KEY_ISLAND_GLASS_REFRACTION = "pref_island_glass_refraction"
    const val KEY_ISLAND_GLASS_HIGHLIGHT = "pref_island_glass_highlight"
    const val KEY_ISLAND_GLASS_SHADOW = "pref_island_glass_shadow"
    const val KEY_ISLAND_GLASS_LIGHT_DIRECTION = "pref_island_glass_light_direction"
    const val KEY_ISLAND_GLASS_DISPERSION = "pref_island_glass_dispersion"
    const val DEFAULT_ISLAND_GLASS_EDGE_WIDTH = 16
    const val DEFAULT_ISLAND_GLASS_REFRACTION = 16
    const val DEFAULT_ISLAND_GLASS_HIGHLIGHT = 42
    const val DEFAULT_ISLAND_GLASS_SHADOW = 14
    const val DEFAULT_ISLAND_GLASS_LIGHT_DIRECTION = 243
    const val DEFAULT_ISLAND_GLASS_DISPERSION = 18
    // 液态玻璃自绘引擎（ScreenCaptureInternal / ScreenCapture 屏幕采样）专用键
    const val KEY_ISLAND_GLASS_CAPTURE_FPS = "pref_island_glass_capture_fps"
    const val KEY_ISLAND_GLASS_CAPTURE_QUALITY = "pref_island_glass_capture_quality"
    const val KEY_ISLAND_GLASS_HDR_HIGHLIGHT = "pref_island_glass_hdr_highlight"
    const val KEY_ISLAND_GLASS_GYROSCOPE = "pref_island_glass_gyroscope"
    const val DEFAULT_ISLAND_GLASS_CAPTURE_FPS = 60
    const val DEFAULT_ISLAND_GLASS_CAPTURE_QUALITY = 50
    const val DEFAULT_ISLAND_GLASS_HDR_HIGHLIGHT = false
    const val DEFAULT_ISLAND_GLASS_GYROSCOPE = false
}
