package fr.migtv.app

import android.net.Uri
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

    /** Extrait (serveur, utilisateur, mot de passe) d'une URL du type http://hote:port/get.php?username=..&password=.. */
    fun parseProviderUrl(text: String): Triple<String, String, String>? {
        val t = text.trim()
        if (!t.startsWith("http") || "username=" !in t || "password=" !in t) return null
        val u = Uri.parse(t)
        val user = u.getQueryParameter("username")
        val pass = u.getQueryParameter("password")
        val host = u.host
        if (user == null || pass == null || host == null) return null
        val port = if (u.port != -1) ":${u.port}" else ""
        return Triple("${u.scheme}://$host$port", user, pass)
    }

    private fun q(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun open(url: String): HttpURLConnection =
        (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "MigTV/0.3")
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

    /** Valeur de `cle="..."` dans une ligne #EXTINF, sans regex (le M3U compte des dizaines de milliers de lignes). */
    private fun attrValue(line: String, key: String): String? {
        val i = line.indexOf(key)
        if (i < 0) return null
        val start = i + key.length
        val end = line.indexOf('"', start)
        return if (end < 0) null else line.substring(start, end)
    }

    fun parseM3u(file: File): List<Entry> {
        val out = ArrayList<Entry>(32_768)
        // Les mêmes catégories reviennent des milliers de fois : une seule String par catégorie en mémoire.
        val groups = HashMap<String, String>()
        var name = ""
        var logo: String? = null
        var group = ""
        file.bufferedReader(bufferSize = 64 * 1024).useLines { lines ->
            for (raw in lines) {
                val line = raw.trim()
                when {
                    line.startsWith("#EXTINF") -> {
                        name = line.substringAfterLast(',').trim()
                            .ifEmpty { attrValue(line, "tvg-name=\"").orEmpty() }
                        logo = attrValue(line, "tvg-logo=\"")?.takeIf { it.isNotBlank() }
                        val g = attrValue(line, "group-title=\"").orEmpty().ifBlank { "Sans catégorie" }
                        group = groups.getOrPut(g) { g }
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
        out.trimToSize()
        return out
    }
}

/** Playlist déjà triée par type, avec les catégories de chaque type : calculé une fois, pas à chaque écran. */
class Catalog(val entries: List<Entry>) {
    val byKind: Map<Kind, List<Entry>> = entries.groupBy { it.kind }
    val groups: Map<Kind, List<String>> = Kind.entries.associateWith { k ->
        byKind[k].orEmpty().mapTo(java.util.TreeSet<String>()) { it.group }.toList()
    }
    val size: Int get() = entries.size

    companion object {
        val EMPTY = Catalog(emptyList())
    }
}
