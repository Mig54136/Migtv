package fr.migtv.app

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.mediarouter.media.MediaRouteSelector
import androidx.mediarouter.media.MediaRouter
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.IOException
import java.util.concurrent.Executors

class CastViewModel(private val app: Application) : AndroidViewModel(app) {
    private val prefs = app.getSharedPreferences("cast", Context.MODE_PRIVATE)
    private val router = MediaRouter.getInstance(app)
    private val selector = MediaRouteSelector.Builder()
        .addControlCategory(
            CastMediaControlIntent.categoryForCast(CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID)
        ).build()
    private var castContext: CastContext? = null

    var available by mutableStateOf(false)
        private set
    var routes by mutableStateOf<List<MediaRouter.RouteInfo>>(emptyList())
        private set
    var picking by mutableStateOf(false)
        private set
    var casting by mutableStateOf(false)
        private set
    var deviceName by mutableStateOf("")
        private set

    /** Conversion audio AAC demandée (mémorisée par chaîne). */
    var converting by mutableStateOf(false)
        private set
    var status by mutableStateOf<String?>(null)
        private set

    private var target: Entry? = null
    private var loadJob: Job? = null

    private val routeCallback = object : MediaRouter.Callback() {
        override fun onRouteAdded(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshRoutes()
        override fun onRouteRemoved(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshRoutes()
        override fun onRouteChanged(router: MediaRouter, route: MediaRouter.RouteInfo) = refreshRoutes()
    }

    private val sessionListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(s: CastSession) {}
        override fun onSessionStarted(s: CastSession, id: String) = onConnected(s)
        override fun onSessionStartFailed(s: CastSession, error: Int) {
            status = "Connexion au Chromecast impossible"
        }
        override fun onSessionEnding(s: CastSession) {}
        override fun onSessionEnded(s: CastSession, error: Int) = onDisconnected()
        override fun onSessionResuming(s: CastSession, id: String) {}
        override fun onSessionResumed(s: CastSession, wasSuspended: Boolean) = onConnected(s)
        override fun onSessionResumeFailed(s: CastSession, error: Int) {}
        override fun onSessionSuspended(s: CastSession, reason: Int) {}
    }

    init {
        try {
            CastContext.getSharedInstance(app, Executors.newSingleThreadExecutor())
                .addOnSuccessListener { ctx ->
                    castContext = ctx
                    ctx.sessionManager.addSessionManagerListener(sessionListener, CastSession::class.java)
                    available = true
                }
        } catch (e: Exception) {
            available = false // Services Google Play absents
        }
    }

    private fun key(e: Entry) = "conv:${e.url.hashCode()}"

    private fun refreshRoutes() {
        routes = router.routes.filter { it.matchesSelector(selector) && !it.isDefaultOrBluetooth }
    }

    fun openPicker(entry: Entry) {
        target = entry
        converting = prefs.getBoolean(key(entry), false)
        picking = true
        router.addCallback(selector, routeCallback, MediaRouter.CALLBACK_FLAG_REQUEST_DISCOVERY)
        refreshRoutes()
    }

    fun closePicker() {
        picking = false
        router.removeCallback(routeCallback)
    }

    fun pick(route: MediaRouter.RouteInfo) {
        route.select()
        closePicker()
        status = "Connexion à ${route.name}…"
    }

    private fun onConnected(session: CastSession) {
        if (target == null) { // session reprise sans lecture en cours : on la ferme
            castContext?.sessionManager?.endCurrentSession(true)
            return
        }
        casting = true
        deviceName = session.castDevice?.friendlyName ?: "Chromecast"
        loadTarget()
    }

    private fun onDisconnected() {
        loadJob?.cancel()
        casting = false
        status = null
        AudioRelay.stop()
    }

    fun setConvert(on: Boolean) {
        converting = on
        target?.let { prefs.edit().putBoolean(key(it), on).apply() }
        if (casting) loadTarget()
    }

    fun stopCasting() {
        loadJob?.cancel()
        castContext?.sessionManager?.endCurrentSession(true)
        onDisconnected()
        target = null
    }

    private fun loadTarget() {
        val entry = target ?: return
        val client = castContext?.sessionManager?.currentCastSession?.remoteMediaClient ?: return
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            status = if (converting) "Conversion audio en préparation…" else "Envoi au Chromecast…"
            try {
                val url = if (converting) {
                    AudioRelay.start(app, entry.url)
                } else {
                    AudioRelay.stop()
                    entry.url
                }
                send(client, entry, url, live = converting || entry.kind == Kind.LIVE)
                status = null
            } catch (e: IOException) {
                status = (e.message ?: "Erreur").replace(entry.url, "<flux>")
            }
        }
    }

    private fun send(client: RemoteMediaClient, entry: Entry, url: String, live: Boolean) {
        val contentType = when {
            url.endsWith(".m3u8") -> "application/x-mpegURL"
            url.endsWith(".mkv") -> "video/x-matroska"
            url.endsWith(".mp4") -> "video/mp4"
            else -> "video/mp2t"
        }
        val meta = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE).apply {
            putString(MediaMetadata.KEY_TITLE, entry.name)
        }
        val info = MediaInfo.Builder(url)
            .setStreamType(if (live) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED)
            .setContentType(contentType)
            .setMetadata(meta)
            .build()
        client.registerCallback(object : RemoteMediaClient.Callback() {
            override fun onStatusUpdated() {
                val st = client.mediaStatus ?: return
                if (st.playerState == MediaStatus.PLAYER_STATE_IDLE &&
                    st.idleReason == MediaStatus.IDLE_REASON_ERROR
                ) {
                    status = "Le Chromecast n'arrive pas à lire ce flux"
                }
            }
        })
        client.load(MediaLoadRequestData.Builder().setMediaInfo(info).setAutoplay(true).build())
            .setResultCallback { r ->
                if (!r.status.isSuccess) status = "Le Chromecast a refusé le flux (code ${r.status.statusCode})"
            }
    }

    override fun onCleared() {
        castContext?.sessionManager?.removeSessionManagerListener(sessionListener, CastSession::class.java)
        router.removeCallback(routeCallback)
        AudioRelay.stop()
    }
}
