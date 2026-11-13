package fr.migtv.app

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

data class Credentials(val server: String, val user: String, val pass: String)

/** Stocke les identifiants Xtream chiffrés (clé dans l'Android Keystore), jamais dans le dépôt. */
class SessionStore(context: Context) {
    private val prefs = EncryptedSharedPreferences.create(
        context,
        "session",
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun load(): Credentials? {
        val server = prefs.getString("server", null) ?: return null
        val user = prefs.getString("user", null) ?: return null
        val pass = prefs.getString("pass", null) ?: return null
        return Credentials(server, user, pass)
    }

    fun save(c: Credentials) {
        prefs.edit().putString("server", c.server).putString("user", c.user).putString("pass", c.pass).apply()
    }

    fun clear() = prefs.edit().clear().apply()
}
