package com.liuxiansheng.lyricon.app

import android.app.Application
import android.util.Log
import com.liuxiansheng.lyricon.app.util.MasterPrefs
import com.liuxiansheng.lyricon.app.util.StatusBarPrefs
import io.github.libxposed.service.XposedService
import io.github.libxposed.service.XposedServiceHelper
import java.util.concurrent.CopyOnWriteArrayList

class LyriconApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Lyricon v3 Application created")

        // 绑定 XposedService：设置页写入的开关通过 remote prefs 同步给 SystemUI 进程
        XposedServiceHelper.registerListener(object : XposedServiceHelper.OnServiceListener {
            override fun onServiceBind(service: XposedService) {
                xposedService = service
                // 服务可用后：补推离线写入 + 用 remote 回填本地设置文件，
                // 保证"文件里存的就是 SystemUI 实际生效值"。
                runCatching { MasterPrefs.syncFromRemote(this@LyriconApp) }
                runCatching { StatusBarPrefs.syncFromRemote(this@LyriconApp) }
                notifyServiceChanged(true)
                Log.i(TAG, "XposedService bound")
            }

            override fun onServiceDied(service: XposedService) {
                xposedService = null
                notifyServiceChanged(false)
            }
        })
    }

    private fun notifyServiceChanged(active: Boolean) {
        serviceListeners.forEach { runCatching { it(active) } }
    }

    companion object {
        private const val TAG = "LyriconApp"

        @JvmStatic
        var xposedService: XposedService? = null
            private set

        /** 模块激活状态实时监听（绑定完成/断开时立即通知 UI，不依赖 ON_RESUME） */
        private val serviceListeners = CopyOnWriteArrayList<(Boolean) -> Unit>()

        @JvmStatic
        fun addServiceListener(listener: (Boolean) -> Unit) {
            serviceListeners.add(listener)
            // 立即回放当前状态，避免注册前后竞态漏更新
            runCatching { listener(xposedService != null) }
        }

        @JvmStatic
        fun removeServiceListener(listener: (Boolean) -> Unit) {
            serviceListeners.remove(listener)
        }
    }
}
