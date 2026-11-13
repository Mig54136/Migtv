package fr.migtv.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

/**
 * Détient le lecteur : il survit aux changements de configuration (rotation, plein écran)
 * et n'est libéré que lorsque l'activité est définitivement détruite.
 */
class PlayerViewModel(app: Application) : AndroidViewModel(app) {
    val player: ExoPlayer = ExoPlayer.Builder(app).build()

    var playing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private var currentUrl: String? = null
    private var resumeOnStart = false

    init {
        player.addListener(object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) {
                error = "Lecture impossible (${e.errorCodeName})"
            }
        })
    }

    fun play(url: String) {
        currentUrl = url
        error = null
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.playWhenReady = true
        playing = true
    }

    fun retry() {
        currentUrl?.let(::play)
    }

    fun stop() {
        player.stop()
        player.clearMediaItems()
        playing = false
        error = null
    }

    /** Appelé quand l'app passe en arrière-plan : met la lecture en pause. */
    fun onAppStopped() {
        resumeOnStart = player.playWhenReady
        player.pause()
    }

    /** Appelé au retour au premier plan : reprend si la lecture était active. */
    fun onAppStarted() {
        if (playing && resumeOnStart && error == null) player.play()
        resumeOnStart = false
    }

    override fun onCleared() {
        player.release()
    }
}
