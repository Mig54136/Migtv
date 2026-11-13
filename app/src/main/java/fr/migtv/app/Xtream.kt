package fr.migtv.app

import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

enum class Kind(val label: String) { LIVE("Chaînes"), MOVIE("Films"), SERIES("Séries") }

data class Entry(
    val name: String,
    val logo: String?,
    val group: String,
    val url: String,
    val kind: Kind
)

class XtreamException(message: String) : Exception(message)

object Xtream {
    fun normalizeServer(raw: String): String {
        val s = raw.trim().trimEnd('/')
        return if (s.startsWith("http://") || s.startsWith("https://")) s else "http://$s"
    }

    private fun q(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "MigTV/0.2")
        }

    /** Vérifie les identifiants via player_api.php. Lève XtreamException si refusés. */
    fun login(server: String, user: String, pass: String) {
        val conn = open("$server/player_api.php?username=${q(user)}&password=${q(pass)}")
        try {
            if (conn.responseCode != 200) {
                throw XtreamException("Le serveur a répondu HTTP ${conn.responseCode}")
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val info = try {
                JSONObject(body).optJSONObject("user_info")
            } catch (e: JSONException) {
                null
            } ?: throw XtreamException("Réponse inattendue du serveur")
            if (info.optString("auth") != "1") throw XtreamException("Identifiants refusés")
            val status = info.optString("status")
            if (status.isNotEmpty() && !status.equals("Active", ignoreCase = true)) {
                throw XtreamException("Compte non actif ($status)")
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Télécharge la playlist M3U complète dans [dest] (écriture atomique). */
    fun downloadM3u(server: String, user: String, pass: String, dest: File) {
        val conn = open("$server/get.php?username=${q(user)}&password=${q(pass)}&type=m3u_plus&output=ts")
        val tmp = File(dest.parentFile, dest.name + ".tmp")
        try {
            if (conn.responseCode != 200) {
                throw XtreamException("Téléchargement M3U refusé (HTTP ${conn.responseCode})")
            }
            conn.inputStream.use { input -> tmp.outputStream().use { input.copyTo(it) } }
            val head = tmp.bufferedReader().use { it.readLine() }.orEmpty()
            if (!head.startsWith("#EXTM3U")) throw XtreamException("Le fichier reçu n'est pas une playlist M3U")
            if (!tmp.renameTo(dest)) {
                tmp.copyTo(dest, overwrite = true)
                tmp.delete()
            }
        } catch (e: IOException) {
            tmp.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    private val attr = Regex("""([\w-]+)="([^"]*)"""")

    fun parseM3u(file: File): List<Entry> {
        val out = ArrayList<Entry>()
        var name = ""
        var logo: String? = null
        var group = ""
        file.bufferedReader().useLines { lines ->
            for (raw in lines) {
                val line = raw.trim()
                when {
                    line.startsWith("#EXTINF") -> {
                        val attrs = attr.findAll(line).associate { it.groupValues[1] to it.groupValues[2] }
                        name = line.substringAfterLast(',').trim().ifEmpty { attrs["tvg-name"].orEmpty() }
                        logo = attrs["tvg-logo"]?.takeIf { it.isNotBlank() }
                        group = attrs["group-title"].orEmpty().ifBlank { "Sans catégorie" }
                    }
                    line.isNotEmpty() && !line.startsWith("#") -> {
                        val kind = when {
                            "/movie/" in line -> Kind.MOVIE
                            "/series/" in line -> Kind.SERIES
                            else -> Kind.LIVE
                        }
                        out.add(Entry(name.ifEmpty { line }, logo, group, line, kind))
                        name = ""; logo = null; group = ""
                    }
                }
            }
        }
        return out
    }
}
