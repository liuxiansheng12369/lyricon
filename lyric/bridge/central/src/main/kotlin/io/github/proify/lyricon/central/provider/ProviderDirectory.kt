
package io.github.proify.lyricon.central.provider

import io.github.proify.lyricon.central.connection.ConnectionRegistry
import io.github.proify.lyricon.central.provider.player.ActivePlayerCoordinator
import io.github.proify.lyricon.provider.IProviderBinder
import io.github.proify.lyricon.provider.ProviderInfo

internal class ProviderDirectory(
    private val activePlayers: ActivePlayerCoordinator
) {
    private val registry = ConnectionRegistry<ProviderInfo, ProviderConnection>()

    fun getOrCreate(binder: IProviderBinder, info: ProviderInfo): ProviderConnection {
        val existing = registry.get(info)
        if (existing != null) return existing
        return registry.register(ProviderConnection(binder, info, activePlayers))
    }

    fun unregister(connection: ProviderConnection) {
        registry.unregister(connection)
    }
}
