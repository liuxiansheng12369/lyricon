package io.github.proify.lyricon.provider;

import android.os.SharedMemory;
import io.github.proify.lyricon.lyric.model.Song;

// Add new methods at the end only, to keep aidl signature order and version compatibility
interface IRemotePlayer {
    void setSong(in byte[] song);
    void setPlaybackState(boolean isPlaying);
    void seekTo(long position);
    void sendText(String text);
    void setPositionUpdateInterval(int interval);
    void setDisplayTranslation(boolean isDisplayTranslation);
    SharedMemory getPositionMemory();
    void setDisplayRoma(boolean isDisplayRoma);

    // Judge playback state based on android.media.session.PlaybackState and compute playback position
    void setPlaybackState2(in PlaybackState state);
}