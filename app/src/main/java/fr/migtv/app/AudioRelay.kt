package fr.migtv.app

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import androidx.core.content.ContextCompat
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.Session
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.net.Inet4Address

/**
 * Relais local : FFmpeg lit le flux IPTV, copie la vidéo telle quelle et convertit l'audio en AAC,
 * puis écrit du HLS dans le cache ; un petit serveur HTTP le sert au Chromecast sur le Wi-Fi.
 */
object AudioRelay {
    private var appContext: Context? = null
    private var server: RelayServer? = null
    private var session: Session? = null

    @Volatile private var ended = false
    @Volatile private var lastLog = ""

    /** Démarre le relais et renvoie l'URL HLS locale, une fois les premiers segments prêts. */
    suspend fun start(context: Context, url: String): String = withContext(Dispatchers.IO) {
        val app = context.applicationContext
        stop()
        appContext = app
        try {
            val ip = localIp(app) ?: throw IOException("Pas de réseau Wi-Fi local")
            val dir = File(app.cacheDir, "relay").apply { deleteRecursively(); mkdirs() }
            ContextCompat.startForegroundService(app, Intent(app, RelayService::class.java))

            val srv = RelayServer(dir)
            srv.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
            server = srv

            ended = false
            lastLog = ""
            val args = arrayOf(
                "-hide_banner", "-loglevel", "warning",
                "-user_agent", "MigTV/0.3",
                "-reconnect", "1", "-reconnect_streamed", "1", "-reconnect_delay_max", "5",
                "-re", "-i", url,
                "-map", "0:v:0", "-map", "0:a:0?", "-sn", "-dn",
                "-c:v", "copy",
                "-c:a", "aac", "-b:a", "160k", "-ac", "2", "-ar", "48000",
                "-f", "hls", "-hls_time", "4", "-hls_list_size", "8",
                "-hls_flags", "delete_segments+independent_segments",
                "-hls_segment_filename", File(dir, "seg%d.ts").absolutePath,
                File(dir, "live.m3u8").absolutePath
            )
            session = FFmpegKit.executeWithArgumentsAsync(
                args,
                { ended = true },
                { log -> lastLog = log.message.orEmpty().trim() },
                null
            )

            val playlist = File(dir, "live.m3u8")
            var waited = 0
            while (waited < 30_000) {
                if (playlist.exists() && playlist.readText().split("#EXTINF").size - 1 >= 2) {
                    return@withContext "http://$ip:${srv.listeningPort}/live.m3u8"
                }
                if (ended) throw IOException("FFmpeg s'est arrêté : ${lastLog.ifEmpty { "flux illisible" }}")
                delay(500)
                waited += 500
            }
            throw IOException("Délai dépassé : le flux ne démarre pas")
        } catch (t: Throwable) {
            stop()
            throw t
        }
    }

    fun stop() {
        session?.let { FFmpegKit.cancel(it.sessionId) }
        session = null
        server?.stop()
        server = null
        appContext?.let { it.stopService(Intent(it, RelayService::class.java)) }
    }

    private fun localIp(context: Context): String? {
        val cm = context.getSystemService(ConnectivityManager::class.java)
        val props = cm.getLinkProperties(cm.activeNetwork) ?: return null
        return props.linkAddresses.map { it.address }
            .firstOrNull { it is Inet4Address && !it.isLoopbackAddress }
            ?.hostAddress
    }
}

private class RelayServer(private val dir: File) : NanoHTTPD(0) {
    override fun serve(session: IHTTPSession): Response {
        val name = session.uri.trimStart('/')
        val file = File(dir, name)
        val inside = runCatching { file.canonicalPath.startsWith(dir.canonicalPath + File.separator) }
            .getOrDefault(false)
        val response = if (!inside || !file.isFile) {
            newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        } else {
            val mime = if (name.endsWith(".m3u8")) "application/x-mpegURL" else "video/mp2t"
            newFixedLengthResponse(Response.Status.OK, mime, FileInputStream(file), file.length())
        }
        // Le récepteur Chromecast exige les en-têtes CORS.
        response.addHeader("Access-Control-Allow-Origin", "*")
        response.addHeader("Cache-Control", "no-cache")
        return response
    }
}
