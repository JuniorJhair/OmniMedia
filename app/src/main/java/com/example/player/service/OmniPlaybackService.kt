package com.example.player.service

import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.player.controller.OmniPlayerController

@OptIn(UnstableApi::class)
class OmniPlaybackService : MediaSessionService() {

    override fun onCreate() {
        super.onCreate()
        try {
            val session = OmniPlayerController.getInstance(applicationContext).mediaSession
            addSession(session)
        } catch (_: Exception) {}
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return OmniPlayerController.getInstance(applicationContext).mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val controller = OmniPlayerController.getInstance(applicationContext)
        val player = controller.exoPlayer
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
    }
}
