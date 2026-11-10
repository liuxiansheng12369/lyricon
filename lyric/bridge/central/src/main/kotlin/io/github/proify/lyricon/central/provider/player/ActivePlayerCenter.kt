
package io.github.proify.lyricon.central.provider.player

import io.github.proify.lyricon.central.CentralRuntime

object ActivePlayerCenter {

    fun addListener(listener: ActivePlayerListener) {
        CentralRuntime.activePlayers.addListener(listener)
    }

    fun removeListener(listener: ActivePlayerListener) {
        CentralRuntime.activePlayers.removeListener(listener)
    }
}
