
@file:Suppress("ReplacePrintlnWithLogging")

package io.github.proify.lyricon.bridge

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.proify.lyricon.provider.LyriconProvider
import io.github.proify.lyricon.provider.ProviderLogo
import io.github.proify.lyricon.provider.service.addConnectionListener
import org.junit.runner.RunWith

/**
 * Instrumented test, which will execute on an Android device.
 *
 * See [testing documentation](http://d.android.com/tools/testing).
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {

    @Suppress("TestMethodWithoutAssertion")
    fun useLyriconProvider() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // 创建提供者实例
        val provider = LyriconProvider(
            context = context,
            providerPackageName = "io.github.proify.lyricon.amprovider",
            playerPackageName = "com.apple.android.music",
            logo = ProviderLogo.fromDrawable(context, R.drawable.ic_launcher),
        )

        val service = provider.service
        service.addConnectionListener {
            onConnected {
                println("已连接")
            }
            onReconnected {
                println("已重新连接")
            }
            onDisconnected {
                println("已断开")
            }
            onConnectTimeout {
                println("连接超时")
            }
        }

        // 开始注册
        provider.register()
    }

    @Suppress("ConstPropertyName")
    object R {
        val drawable = Drawable

        object Drawable {
            const val ic_launcher = 0
        }
    }
}