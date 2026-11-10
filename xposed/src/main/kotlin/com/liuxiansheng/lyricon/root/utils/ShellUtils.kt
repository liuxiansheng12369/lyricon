/*
 * Lyricon V3 · 超级岛歌词 Xposed 模块（小米澎湃 OS / HyperOS）
 * 包名：com.liuxiansheng.lyricon.v3
 * Copyright 2026 liuxiansheng（刘先生）
 * 本文件为 Lyricon V3 自研实现。
 *
 * Shell 工具：经 su/nsenter 执行 root 命令，
 * 提供进程终止与 SystemUI 插件缓存只读查询。
 */

package com.liuxiansheng.lyricon.hyper.root.utils

import com.liuxiansheng.lyricon.hyper.plugin.core.PluginCacheFileLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataOutputStream

/**
 * Shell 工具（V3 自研）。
 */
object ShellUtils {
    data class RootPluginCacheFile(
        val fileName: String,
        val sizeBytes: Long,
        val legacyPreferences: Boolean,
        val absolutePath: String,
    )

    sealed interface RootPluginCacheQuery {
        data class Available(val files: List<RootPluginCacheFile>) : RootPluginCacheQuery
        data object RootUnavailable : RootPluginCacheQuery
        data object InvalidPluginId : RootPluginCacheQuery
    }

    private data class RootCommandResult(
        val exitCode: Int,
        val standardOutput: String,
    ) {
        val isSuccess: Boolean get() = exitCode == 0
    }

    /**
     * 底层配置双副本（3.0.0-R8）：su 读取 App 主副本配置文件，
     * 供 SystemUI 启动对账——remote 内存态重启即丢，SystemUI 本地文件
     * 又可能残留旧值；App 本地文件是唯一权威持久源，这里经 su 拉取全量。
     * 返回 null 表示 su 不可用/文件不存在/解析失败（调用方静默回退）。
     */
    suspend fun readAppConfigPrefsFile(): Map<String, Any?>? {
        val script = "cat /data/user/0/com.liuxiansheng.lyricon.v3/shared_prefs/com.liuxiansheng.lyricon_preferences.xml"
        val result = execRootScript("nsenter --mount=/proc/1/ns/mnt -- sh", script)
            ?: return null
        if (!result.isSuccess) return null
        return parseSharedPrefsXml(result.standardOutput)
    }

    private fun parseSharedPrefsXml(xml: String): Map<String, Any?> {
        val map = HashMap<String, Any?>()
        // 属性形式：<boolean name="k" value="v" /> / <int ...> / <float> / <long> / <string name="k" value="v"/>
        val attrRegex = Regex("""<(\w+)\s+name="([^"]+)"\s+value="([^"]*)"\s*/>""")
        attrRegex.findAll(xml).forEach { m ->
            val type = m.groupValues[1]
            val key = m.groupValues[2]
            val value = m.groupValues[3]
            when (type) {
                "boolean" -> map[key] = value.equals("true", ignoreCase = true)
                "int" -> map[key] = value.toIntOrNull()
                "long" -> map[key] = value.toLongOrNull()
                "float" -> map[key] = value.toFloatOrNull()
                "string" -> if (value.isNotEmpty()) map[key] = value
            }
        }
        // 文本形式：<string name="k">value</string>
        val textRegex = Regex("""<string\s+name="([^"]+)">([^<]*)</string>""")
        textRegex.findAll(xml).forEach { m ->
            map[m.groupValues[1]] = m.groupValues[2]
        }
        return map.filterValues { it != null }
    }

    suspend fun killAppProcess(packageName: String, signal: Int = 15): Boolean {        val script = $$"""
            pid=$(pgrep -f "$$packageName" | grep -v $$)
            if [ -z "$pid" ]; then
                pids=""
                pid=$(ps -A -o PID,ARGS=CMD | grep "$$packageName" | grep -v "grep")
                for i in $pid; do
                    case "$i" in
                        ''|*[!0-9]*) ;;
                        *) pids="$pids $i" ;;
                    esac
                done
                pid=$pids
            fi
            
            killed=0
            if [ -n "$pid" ]; then
                for i in $pid; do
                    kill -s $$signal "$i" >/dev/null 2>&1
                    kill -s 9 "$i" >/dev/null 2>&1
                    if [ $? -eq 0 ]; then
                        killed=1
                    fi
                done
            fi
            
            if [ $killed -eq 1 ]; then
                exit 0
            else
                exit 1
            fi
        """.trimIndent()

        return execRootScriptSilent("nsenter --mount=/proc/1/ns/mnt -- sh", script)
    }

    suspend fun execRootScriptSilent(cmd: String, inputScript: String? = null): Boolean {
        return execRootScript(cmd, inputScript)?.isSuccess == true
    }

    /**
     * 注入的 SystemUI 运行时尚未重载时的缓存只读兜底查询。
     * 只读取、不解析、不修改插件的缓存内容。
     */
    suspend fun querySystemUiPluginCacheFiles(pluginId: String): RootPluginCacheQuery {
        if (!PluginCacheFileLayout.isValidPluginId(pluginId)) {
            return RootPluginCacheQuery.InvalidPluginId
        }
        val script = $$"""
            for data_root in /data/user/0 /data/user_de/0; do
                cache_dir="$data_root/com.android.systemui/$${PluginCacheFileLayout.rootRelativeDirectory(pluginId)}"
                legacy_prefs="$data_root/com.android.systemui/shared_prefs/hyperlyric_plugin_cache_$${pluginId}.xml"
                if [ -d "$cache_dir" ]; then
                    for file in "$cache_dir"/*.cache; do
                        [ -f "$file" ] || continue
                        size=$(wc -c < "$file" 2>/dev/null | tr -d ' ')
                        printf 'file\t%s\t%s\t%s\n' "$file" "${file##*/}" "$size"
                    done
                fi
                if [ -f "$legacy_prefs" ]; then
                    size=$(wc -c < "$legacy_prefs" 2>/dev/null | tr -d ' ')
                    printf 'legacy\t%s\t%s\t%s\n' "$legacy_prefs" "${legacy_prefs##*/}" "$size"
                fi
            done
        """.trimIndent()
        val result = execRootScript("nsenter --mount=/proc/1/ns/mnt -- sh", script)
            ?: return RootPluginCacheQuery.RootUnavailable
        if (!result.isSuccess) return RootPluginCacheQuery.RootUnavailable
        return RootPluginCacheQuery.Available(parsePluginCacheFiles(result.standardOutput))
    }

    internal fun parsePluginCacheFiles(output: String): List<RootPluginCacheFile> = output
        .lineSequence()
        .mapNotNull { line ->
            val parts = line.split('\t')
            val legacy = when (parts.getOrNull(0)) {
                "file" -> false
                "legacy" -> true
                else -> return@mapNotNull null
            }
            val absolutePath = parts.getOrNull(1)?.takeIf { it.startsWith("/") }
                ?: return@mapNotNull null
            val fileName = parts.getOrNull(2)?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val sizeBytes = parts.getOrNull(3)?.toLongOrNull()?.takeIf { it >= 0L }
                ?: return@mapNotNull null
            RootPluginCacheFile(fileName, sizeBytes, legacy, absolutePath)
        }
        .take(128)
        .toList()

    private suspend fun execRootScript(
        cmd: String,
        inputScript: String? = null
    ): RootCommandResult? {
        return withContext(Dispatchers.IO) {
            var process: Process? = null
            var os: DataOutputStream? = null
            try {
                process = Runtime.getRuntime().exec(arrayOf("su", "-c", cmd))
                if (inputScript != null) {
                    os = DataOutputStream(process.outputStream)
                    os.write(inputScript.toByteArray(Charsets.UTF_8))
                    os.writeBytes("\nexit\n")
                    os.flush()
                }
                val standardOutput = process.inputStream.bufferedReader().use { it.readText() }
                val exitCode = process.waitFor()
                return@withContext RootCommandResult(exitCode, standardOutput)
            } catch (_: Exception) {
                return@withContext null
            } finally {
                try {
                    os?.close()
                } catch (_: Exception) {
                }
                process?.destroy()
            }
        }
    }

}
