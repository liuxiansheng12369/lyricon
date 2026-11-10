
package io.github.proify.lyricon.provider

import android.content.Intent
import android.os.Bundle

/** 将 [ProviderService] 适配为提供给中心服务调用的 AIDL Binder。 */
internal class LocalProviderService(var callback: ProviderService? = null) :
    IProviderService.Stub() {

    override fun onRunCommand(intent: Intent?): Bundle? = callback?.onRunCommand(intent)
}
