
package io.github.proify.lyricon.central.provider

import io.github.proify.lyricon.central.CentralRuntime
import io.github.proify.lyricon.central.provider.player.PlayerBinder
import io.github.proify.lyricon.provider.IRemoteService

internal class ProviderServiceBinder(
    private var connection: ProviderConnection?
) : IRemoteService.Stub() {

    private var player: PlayerBinder? = connection?.let {
        PlayerBinder(it.providerInfo, CentralRuntime.activePlayers)
    }

    override fun getPlayer(): PlayerBinder? = player

    fun close() {
        player?.close()
        player = null
        connection = null
    }

    override fun disconnect() {
        connection?.let { CentralRuntime.providers.unregister(it) }
    }
}
