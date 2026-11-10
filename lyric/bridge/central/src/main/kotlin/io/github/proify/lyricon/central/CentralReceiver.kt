
package io.github.proify.lyricon.central

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

internal object CentralReceiver : BroadcastReceiver() {

    private const val TAG = "CentralReceiver"

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action ?: return
        Log.d(TAG, "Received intent: $action")

        CentralRuntime.registration.handle(intent)
    }
}
