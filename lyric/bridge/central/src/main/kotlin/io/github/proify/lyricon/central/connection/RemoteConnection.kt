
package io.github.proify.lyricon.central.connection

internal interface RemoteConnection<K> {
    val key: K

    fun setDeathRecipient(onDeath: (() -> Unit)?)

    fun close()
}
