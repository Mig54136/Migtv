package fr.migtv.app

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class Profile(
    val id: String,
    val name: String,
    val server: String,
    val user: String,
    val pass: String
) {
    /** Hôte affiché sous le nom du profil (sans identifiants). */
    val host: String
        get() = runCatching { java.net.URI(server).host }.getOrNull() ?: server
}

/** Stocke les profils Xtream chiffrés (clé dans l'Android Keystore), jamais dans le dépôt. */
class SessionStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    var lastActive: String?
        get() = prefs.getString("last_active", null)
        set(value) = prefs.edit().putString("last_active", value).apply()

    fun profiles(): List<Profile> {
        val raw = prefs.getString("profiles", null) ?: return migrateLegacy()
        return runCatching {
            val arr = JSONArray(raw)
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                Profile(o.getString("id"), o.getString("name"), o.getString("server"), o.getString("user"), o.getString("pass"))
            }
        }.getOrDefault(emptyList())
    }

    fun saveProfiles(list: List<Profile>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("name", it.name)
                    .put("server", it.server).put("user", it.user).put("pass", it.pass)
            )
        }
        prefs.edit().putString("profiles", arr.toString()).apply()
    }

    /** Reprend le compte unique de la version 0.2, s'il existe. */
    private fun migrateLegacy(): List<Profile> {
        val server = prefs.getString("server", null) ?: return emptyList()
        val user = prefs.getString("user", null) ?: return emptyList()
        val pass = prefs.getString("pass", null) ?: return emptyList()
        val p = Profile(UUID.randomUUID().toString(), "Mon profil", server, user, pass)
        saveProfiles(listOf(p))
        prefs.edit().remove("server").remove("user").remove("pass").apply()
        return listOf(p)
    }
}
