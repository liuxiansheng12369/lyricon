/**
 *
 *
 */
package io.github.proify.lyricon.subscriber;

import io.github.proify.lyricon.subscriber.ProviderInfo;
import io.github.proify.lyricon.lyric.model.Song;

interface IActivePlayerListener {
    // Called when the active player provider changes
    void onActiveProviderChanged(in byte[] providerInfo);

    // Called when song metadata changes
    void onSongChanged(in byte[] song);

    // Called when playback state (playing/paused) changes
    void onPlaybackStateChanged(boolean isPlaying);

    // Respond to seek requests
    void onSeekTo(long position);

    // Receive text message notifications
    void onReceiveText(String text);

    // Translation display toggle changed
    void onDisplayTranslationChanged(boolean isDisplayTranslation);

    // Roma display toggle changed
    void onDisplayRomaChanged(boolean isDisplayRoma);
}
