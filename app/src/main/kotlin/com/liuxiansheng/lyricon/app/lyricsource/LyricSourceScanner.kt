/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 *
 * LyricSourceScanner：扫描系统内歌词 provider 插件。
 * - 识别：非系统应用且 metaData 声明 lyricon_module=true（兼容 ColorOS 实时歌词桥接前缀包）
 * - 外部 provider 白名单：词岛生态已知的外部歌词服务（如 proify 项目 lyricon central / 老词梦 provider /
 *   刘先生聚合 provider），未声明 lyricon_module 标记也直接识别展示
 * - 认证：签名 SHA-256 与受信任来源比对，标记是否官方认证
 * - 分组：按 category 分类，未声明的归"其他"（始终排最后）
 * 全部逻辑在调用线程（IO）完成，一次性产出 UI 可直接渲染的分类列表。
 */

package com.liuxiansheng.lyricon.app.lyricsource

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.os.Process
import java.io.File
import java.security.MessageDigest
import java.text.Collator
import java.util.Locale

object LyricSourceScanner {

    /** 受信任签名来源（与上游 provider 一致），集中管理便于安全审计。 */
    object TrustedSignatures {
        const val DEFAULT = "d75a43f76dbe80d816046f952b8d0f5f7abd71c9bd7b57786d5367c488bd5816"
        const val TRANTOR = "ba86f0c1f52d0f6a24e1b9a63eade0e8b80e7b9e20b8ef068da2e39c7b6e7b49"
        const val SALT_PLAYER = "8488d67a39978f84fd876510e0acb85e3a0504b90fbd56f11beb2123e285fa78"
        const val FLAMINGO = "4ca5ff4e5bf8418b45a8ecb46ddb91b6403ab7cc4b1a4de7e58fba12f54278e6"
        // 刘先生聚合版签名
        const val LIUXIANSHENG = "875AFCEC6B9826FBF5382AB110D3FDCE03984A4B938338E3417C251D5DADB1FC"

        val ALL_CERTIFIED = arrayOf(DEFAULT, TRANTOR, SALT_PLAYER, FLAMINGO, LIUXIANSHENG)
    }

    private const val COLOROS_PREFIX = "io.github.andrealtb.coloroslyrics.provider."

    /**
     * 词岛生态已知的外部歌词服务（无 lyricon_module 标记的兜底白名单）：
     * 已安装即直接识别展示；描述优先取应用声明的 xposeddescription meta-data，
     * 其次取 manifest android:description，均无则留空。展示信息一律取应用自身真实元数据，不额外编造文字。
     */
    private val KNOWN_PROVIDERS = setOf(
        "io.github.proify.lyricon.core",
        "io.github.proify.lyricon.qishuiprovider",
        "liuxiansheng.lyricon.provider",
    )

    /**
     * 扫描 + 认证 + 分组排序，一次性产出 UI 可直接渲染的分类列表。
     * @param otherLabel 未声明分类的兜底分类名（默认"其他"）
     */
    fun scan(context: Context, otherLabel: String = "其他"): List<LyricSourceCategory> {
        return try {
            val pm = context.packageManager
            @Suppress("DEPRECATION")
            val packages = pm.getInstalledPackages(
                PackageManager.GET_META_DATA or PackageManager.GET_SIGNING_CERTIFICATES
            )

            // LSPosed 激活状态（null = 无法检测，不展示激活提示）
            val lspdState = loadLspdState(context)

            val valid = packages
                .filter { isValidModule(it) }
                .mapNotNull { processPackage(pm, it, lspdState) }

            if (valid.isEmpty()) return emptyList()

            val collator = Collator.getInstance(Locale.getDefault())
            // 认证优先，其次按标签首字母
            val sorted = valid.sortedWith { m1, m2 ->
                if (m1.isCertified != m2.isCertified) {
                    m2.isCertified.compareTo(m1.isCertified)
                } else {
                    collator.compare(m1.label, m2.label)
                }
            }
            // 分组：未声明分类归"其他"，"其他"始终排最后，各组内部按标签排序
            sorted.groupBy { it.category ?: otherLabel }
                .map { (name, items) ->
                    LyricSourceCategory(
                        name = name,
                        items = items.sortedWith { a, b -> collator.compare(a.label, b.label) },
                    )
                }
                .sortedWith { c1, c2 ->
                    when {
                        c1.name == otherLabel -> 1
                        c2.name == otherLabel -> -1
                        else -> collator.compare(c1.name, c2.name)
                    }
                }
        } catch (_: Exception) {
            emptyList()
        }
    }

    /** 是否为合规的歌词 provider 插件（非系统应用且含 lyricon_module 标记 / 已知外部 provider / ColorOS 桥接前缀）。 */
    private fun isValidModule(packageInfo: PackageInfo): Boolean {
        val appInfo = packageInfo.applicationInfo ?: return false
        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
        val isUpdatedSystem = (appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
        if (isSystem || isUpdatedSystem) return false
        // 词岛生态已知外部 provider（无需 lyricon_module 标记）
        if (packageInfo.packageName in KNOWN_PROVIDERS) return true
        // 内置支持：ColorOS 实时歌词桥接系列（包名前缀匹配）
        if (packageInfo.packageName.startsWith(COLOROS_PREFIX)) return true
        return appInfo.metaData?.getBoolean("lyricon_module") == true
    }

    /** LSPosed 激活状态快照：哪些包已注册为 Xposed 模块、哪些已启用。 */
    private data class LspdState(
        val xposedModules: Set<String>,
        val enabledModules: Set<String>,
    )

    /**
     * 读取 LSPosed 激活状态。
     * 新版 LSPosed 把激活状态存于 SQLite（modules_config.db 的 modules_state.enabled 字段）。
     * 设备通常无 sqlite3 可执行文件，因此通过 su 把数据库（含 WAL/SHM）复制到 app 私有目录，
     * 再用 SQLiteDatabase 查询。返回 null 表示检测失败（不展示激活提示，避免误标）。
     */
    private fun loadLspdState(context: Context): LspdState? {
        return try {
            val src = "/data/adb/lspd/config/modules_config.db"
            val dir = File(context.filesDir, "lspd")
            if (!dir.exists()) dir.mkdirs()
            val dst = File(dir, "modules_config.db")
            // 主库必须复制成功；WAL/SHM 单独容错（未开启 WAL 时不存在）；
            // 注意 -wal/-shm 也需 chmod/chown，否则 app 进程打不开 WAL（SQLite 报 Permission denied）
            val uid = Process.myUid()
            val cmd = "cp '$src' '${dst.path}' && chmod 644 '${dst.path}' && chown $uid:$uid '${dst.path}' && " +
                "{ cp '$src-wal' '${dst.path}-wal' 2>/dev/null || true; } && " +
                "{ cp '$src-shm' '${dst.path}-shm' 2>/dev/null || true; } && " +
                "{ chmod 644 '${dst.path}-wal' '${dst.path}-shm' 2>/dev/null || true; } && " +
                "{ chown $uid:$uid '${dst.path}-wal' '${dst.path}-shm' 2>/dev/null || true; }"
            val proc = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
            proc.waitFor()
            if (proc.exitValue() != 0 || !dst.exists()) return null

            val db = SQLiteDatabase.openDatabase(dst.path, null, SQLiteDatabase.OPEN_READONLY)
            try {
                val xposed = LinkedHashSet<String>()
                db.rawQuery("SELECT module_pkg_name FROM modules", null).use { c ->
                    while (c.moveToNext()) xposed.add(c.getString(0))
                }
                val enabled = LinkedHashSet<String>()
                db.rawQuery(
                    "SELECT module_pkg_name FROM modules_state WHERE enabled=1 AND user_id=0",
                    null
                ).use { c ->
                    while (c.moveToNext()) enabled.add(c.getString(0))
                }
                LspdState(xposed, enabled)
            } finally {
                db.close()
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * 计算单个模块的激活展示状态：
     * - 检测失败或模块未注册为 Xposed 模块（无激活概念）→ null（不展示提示）
     * - 已注册的 Xposed 模块 → enabled 库中命中即已激活，否则未激活
     */
    private fun activationOf(packageName: String, state: LspdState?): Boolean? {
        if (state == null) return null
        if (packageName !in state.xposedModules) return null
        return packageName in state.enabledModules
    }

    /** 处理单个安装包，提取模块元数据并进行签名认证。 */
    private fun processPackage(pm: PackageManager, packageInfo: PackageInfo, state: LspdState?): LyricSourceModule? {
        return try {
            val appInfo = packageInfo.applicationInfo ?: return null
            val label = appInfo.loadLabel(pm).toString()
            val metaData = appInfo.metaData
            val activation = activationOf(packageInfo.packageName, state)

            // 1) 通用分支：声明 lyricon_module 标记的模块（外部 provider 自带真实作者/描述/标签/分类）
            if (metaData?.getBoolean("lyricon_module") == true) {
                return LyricSourceModule(
                    packageName = packageInfo.packageName,
                    label = label,
                    versionName = packageInfo.versionName,
                    author = readMetaString(pm, appInfo, metaData, "lyricon_module_author"),
                    description = readMetaString(pm, appInfo, metaData, "lyricon_module_description"),
                    category = readMetaString(pm, appInfo, metaData, "lyricon_module_category"),
                    isCertified = isCertified(packageInfo),
                    tags = extractTags(pm, appInfo, metaData),
                    homeUrl = readMetaString(pm, appInfo, metaData, "lyricon_module_home"),
                    activation = activation,
                )
            }

            // 2) ColorOS 实时歌词桥接系列（前缀匹配）：无 lyricon_module metaData，描述取应用自身声明的 description
            if (packageInfo.packageName.startsWith(COLOROS_PREFIX)) {
                return LyricSourceModule(
                    packageName = packageInfo.packageName,
                    label = label,
                    versionName = packageInfo.versionName,
                    author = null,
                    description = loadDescription(pm, appInfo),
                    category = null,
                    isCertified = isCertified(packageInfo),
                    tags = emptyList(),
                    homeUrl = "https://modules.lsposed.org/module/io.github.andrealtb.lockscreenlyrics/",
                    activation = activation,
                )
            }

            // 3) 已知外部 provider 兜底：无 lyricon_module 标记才走这里（描述取 xposeddescription 或 manifest description）
            if (packageInfo.packageName in KNOWN_PROVIDERS) {
                return LyricSourceModule(
                    packageName = packageInfo.packageName,
                    label = label,
                    versionName = packageInfo.versionName,
                    author = null,
                    description = readXposedDescription(pm, appInfo, metaData) ?: loadDescription(pm, appInfo),
                    category = null,
                    isCertified = isCertified(packageInfo),
                    tags = emptyList(),
                    homeUrl = null,
                    activation = activation,
                )
            }

            null
        } catch (_: Exception) {
            null
        }
    }

    /** 读取 meta-data 字符串值：直接字符串原样返回，资源引用（Int）解析为文本。 */
    private fun readMetaString(
        pm: PackageManager,
        appInfo: ApplicationInfo,
        metaData: android.os.Bundle,
        key: String,
    ): String? {
        val value = metaData.get(key) ?: return null
        return when (value) {
            is Int -> if (value != 0) {
                runCatching { pm.getText(appInfo.packageName, value, appInfo)?.toString() }.getOrNull()
            } else {
                null
            }
            is String -> value.takeIf { it.isNotBlank() }
            else -> null
        }
    }

    /** 读取 Xposed 模块风格的 xposeddescription meta-data（支持资源引用），未声明返回 null。 */
    private fun readXposedDescription(
        pm: PackageManager,
        appInfo: ApplicationInfo,
        metaData: android.os.Bundle?,
    ): String? {
        if (metaData == null) return null
        return readMetaString(pm, appInfo, metaData, "xposeddescription")
    }

    /** 读取应用在 manifest 中自己声明的 description（android:description 字符串资源），未声明返回 null。 */
    private fun loadDescription(pm: PackageManager, appInfo: ApplicationInfo): String? {
        val resId = appInfo.descriptionRes
        if (resId == 0) return null
        return runCatching { pm.getText(appInfo.packageName, resId, appInfo)?.toString() }.getOrNull()
    }

    /** 解析插件的标签元数据（支持 string 数组资源或逗号分隔字符串）。 */
    private fun extractTags(
        pm: PackageManager,
        appInfo: ApplicationInfo,
        metaData: android.os.Bundle,
    ): List<String> {
        val tagsResId = metaData.getInt("lyricon_module_tags")
        val rawTags = if (tagsResId != 0) {
            runCatching { pm.getResourcesForApplication(appInfo).getStringArray(tagsResId).toList() }
                .getOrDefault(emptyList())
        } else {
            metaData.getString("lyricon_module_tags")?.let { listOf(it) } ?: emptyList()
        }
        return rawTags.filter { it.isNotBlank() }
    }

    /** 签名 SHA-256 是否命中任一受信任来源。 */
    private fun isCertified(packageInfo: PackageInfo): Boolean {
        val signatures = getSignatures(packageInfo) ?: return false
        return signatures.any { sig ->
            val sha256 = sha256(sig)
            TrustedSignatures.ALL_CERTIFIED.any { it.equals(sha256, ignoreCase = true) }
        }
    }

    private fun getSignatures(packageInfo: PackageInfo): List<Signature>? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.signingInfo?.let { si ->
                    if (si.hasMultipleSigners()) si.apkContentsSigners.toList()
                    else si.signingCertificateHistory.toList()
                }
            } else {
                @Suppress("DEPRECATION")
                packageInfo.signatures?.toList()
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun sha256(signature: Signature): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(signature.toByteArray())
        return digest.joinToString("") { "%02X".format(it) }
    }
}
