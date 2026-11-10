/*
 * Lyricon V3 · 状态栏通知图标"播放时右移"（对齐 HyperLyric StatusBarNotificationIconPosition）
 *
 * 移植思路（不照抄小米专有反射）：
 *  - HyperLyric 用系统 hook + Bridge 反射读小米私有类（MiuiPhoneStatusBarView 等）定位；
 *  - 本类只用 findViewById 找通知图标容器/系统图标/电池/隐私/挖孔，用每帧 onLayoutUpdated()
 *    替代 HyperLyric 的 hook onLayout/calculate 刷新，岛避让复用 StatusBarLyricIslandRegionDispatcher。
 *  - 核心保留：把通知图标容器从左侧布局流摘除，插入 status_bar_icons 下的 overlay viewport，
 *    按右边界（系统图标/电池/隐私）定位到系统图标旁，宽度受限避免与歌词重叠。
 */

package com.liuxiansheng.lyricon.hyper.root.statusbar

import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.liuxiansheng.lyricon.hyper.root.utils.HookLogger
import java.lang.ref.WeakReference

/** 通知图标"播放时右移"：把通知图标容器移到右侧系统图标旁（脱离原布局流，宽度受限）。 */
internal class StatusBarNotificationIconPosition(
    root: ViewGroup,
) {
    private val rootReference = WeakReference(root)
    private var iconsReference: WeakReference<ViewGroup>? = null
    private var originalParent: ViewGroup? = null
    private var originalIndex = -1
    private var originalParams: ViewGroup.LayoutParams? = null
    private var overlay: FrameLayout? = null
    private var viewport: FrameLayout? = null
    private var requested = false
    private var failed = false
    private var restoring = false
    var isMoved = false
        private set
    private val location = IntArray(2)
    private val origin = IntArray(2)
    private val cutoutReference = WeakReference(find(root, "cutout_space_view"))
    private val systemIconsReference = WeakReference(find(root, "statusIcons"))
    private val batteryReference = WeakReference(find(root, "battery"))
    private val privacyReference = WeakReference(find(root, "home_privacy_container"))
    private val overlayParentReference = WeakReference(find(root, "status_bar_icons") as? FrameLayout)
    private var spacingPx = 0

    private val rootAttachListener = object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) = reconcile()
        override fun onViewDetachedFromWindow(v: View) = restore()
    }

    init {
        root.addOnAttachStateChangeListener(rootAttachListener)
    }

    fun setEnabled(enabled: Boolean) {
        if (requested == enabled) return
        requested = enabled
        reconcile()
    }

    /** 每帧/状态刷新时调用（替代 HyperLyric 的 hook onLayout 驱动）。 */
    fun onLayoutUpdated() {
        if (!restoring) reconcile()
        if (isMoved) layoutRightArea()
    }

    private fun reconcile() {
        if (!requested || failed) {
            restore()
            return
        }
        val root = rootReference.get() ?: return
        if (!root.isAttachedToWindow) return
        runCatching {
            val icons = findNotificationIcons(root) ?: return
            val parent = overlayParentReference.get() ?: return
            if (isMoved && iconsReference?.get() !== icons) restore()
            if (!isMoved) {
                iconsReference = WeakReference(icons)
                originalParent = icons.parent as? ViewGroup
                originalIndex = originalParent?.indexOfChild(icons) ?: -1
                originalParams = icons.layoutParams
                val frame = FrameLayout(root.context).apply {
                    clipChildren = true
                    clipToPadding = true
                }
                val layer = object : FrameLayout(root.context) {
                    override fun onLayout(
                        changed: Boolean, left: Int, top: Int, right: Int, bottom: Int
                    ) {
                        layoutRightArea()
                    }
                }.apply {
                    clipChildren = true
                    clipToPadding = true
                    addView(frame, FrameLayout.LayoutParams(0, 0))
                }
                viewport = frame
                overlay = layer
                isMoved = true
                parent.addView(
                    layer,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
            val frame = viewport ?: return
            if (icons.parent !== frame) {
                (icons.parent as? ViewGroup)?.removeView(icons)
                frame.addView(
                    icons,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                )
            }
        }.onFailure {
            failed = true
            HookLogger.w(TAG, "通知图标右移已降级: reason=${it.message}")
            restore()
        }
    }

    /** 右边界跟随系统图标/电池/隐私边界，宽度受限，岛避让。 */
    private fun layoutRightArea() {
        if (!isMoved) return
        val layer = overlay ?: return
        val frame = viewport ?: return
        val icons = iconsReference?.get() ?: return
        val systemIcons = systemIconsReference.get() ?: return
        val cutout = cutoutReference.get() ?: return
        locationWithoutTransform(layer, origin)
        locationWithoutTransform(cutout, location)
        var leftBound = location[0] + cutout.width - origin[0]
        locationWithoutTransform(systemIcons, location)
        val top = location[1] - origin[1]
        val height = systemIcons.height
        var rightBound = if (systemIcons.visibility == View.VISIBLE) {
            // 系统图标容器右边界（放系统图标右侧，避免盖住系统图标本身）
            location[0] + systemIcons.width - origin[0]
        } else layer.width
        rightBound = boundaryBefore(batteryReference.get(), rightBound)
        rightBound = boundaryBefore(privacyReference.get(), rightBound)
        rightBound = rightBound.coerceIn(0, layer.width)
        val bounds = StatusBarLyricIslandRegionDispatcher.visibleIslandBounds()
        if (bounds != null && bounds.top < origin[1] + top + height && bounds.bottom > origin[1] + top) {
            val spacing = islandSpacingPx(layer)
            leftBound = maxOf(leftBound, bounds.right + spacing - origin[0])
        }
        val available = (rightBound - leftBound).coerceAtLeast(0)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        icons.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            heightSpec
        )
        var width = icons.measuredWidth
        if (width > available) width = available.coerceAtLeast(0)
        frame.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            heightSpec
        )
        frame.layout(rightBound - width, top, rightBound, top + height)
    }

    private fun restore() {
        if (!isMoved) return
        isMoved = false
        val icons = iconsReference?.get()
        val parent = originalParent
        val index = originalIndex
        val params = originalParams
        val root = rootReference.get()
        restoring = true
        try {
            runCatching {
                if (root != null && icons != null) {
                    (icons.parent as? ViewGroup)?.removeView(icons)
                    if (parent != null) {
                        if (params != null) icons.layoutParams = params
                        parent.addView(icons, index.coerceAtLeast(0))
                    }
                }
            }.onFailure { HookLogger.w(TAG, "通知图标原位置恢复失败: reason=${it.message}") }
        } finally {
            restoring = false
        }
        overlay?.let { (it.parent as? ViewGroup)?.removeView(it) }
        viewport = null
        overlay = null
        iconsReference = null
        originalParent = null
        originalParams = null
        originalIndex = -1
    }

    fun release() {
        requested = false
        restore()
        rootReference.get()?.removeOnAttachStateChangeListener(rootAttachListener)
    }

    private fun boundaryBefore(view: View?, currentBoundary: Int): Int {
        if (view == null || view.visibility != View.VISIBLE || view.width == 0) return currentBoundary
        locationWithoutTransform(view, location)
        return minOf(currentBoundary, location[0] - origin[0])
    }

    private fun islandSpacingPx(view: View): Int {
        val resources = view.resources
        val resourceId = resources.getIdentifier(
            "status_bar_island_padding",
            "dimen",
            "com.android.systemui"
        )
        return if (resourceId != 0) resources.getDimensionPixelSize(resourceId)
        else (4f * resources.displayMetrics.density).toInt()
    }

    private fun locationWithoutTransform(view: View, target: IntArray) {
        val windowRoot = view.rootView
        windowRoot.getLocationInWindow(target)
        var current = view
        while (current !== windowRoot) {
            target[0] += current.left
            target[1] += current.top
            val parent = current.parent as? View ?: break
            target[0] -= parent.scrollX
            target[1] -= parent.scrollY
            current = parent
        }
    }

    private fun findNotificationIcons(root: ViewGroup): ViewGroup? {
        val candidates = listOf(
            "notification_icon_area",
            "status_bar_notification_area",
            "system_icon_area"
        )
        for (name in candidates) {
            val id = root.resources.getIdentifier(name, "id", "com.android.systemui")
            if (id != 0) {
                val v = root.findViewById<View>(id)
                if (v is ViewGroup) return v
            }
        }
        return null
    }

    private fun find(root: View, name: String): View? = root.findViewById(
        root.resources.getIdentifier(name, "id", "com.android.systemui")
    )

    private companion object { const val TAG = "StatusBarNotificationIcons" }
}
