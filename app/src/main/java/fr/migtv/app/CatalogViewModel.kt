package fr.migtv.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class CatalogViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SessionStore(app)
    private val file = File(app.filesDir, "playlist.m3u")

    val saved: Credentials? = store.load()

    var loggedIn by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var entries by mutableStateOf<List<Entry>>(emptyList())
        private set

    init {
        // Reprise de session : on relit la playlist en cache sans retélécharger.
        if (saved != null && file.exists()) {
            viewModelScope.launch {
                loading = true
                try {
                    entries = withContext(Dispatchers.IO) { Xtream.parseM3u(file) }
                    loggedIn = entries.isNotEmpty()
                } catch (e: IOException) {
                    loggedIn = false
                } finally {
                    loading = false
                }
            }
        }
    }

    fun connect(server: String, user: String, pass: String) {
        if (loading) return
        val creds = Credentials(Xtream.normalizeServer(server), user.trim(), pass)
        viewModelScope.launch {
            loading = true
            error = null
            try {
                entries = withContext(Dispatchers.IO) {
                    Xtream.login(creds.server, creds.user, creds.pass)
                    Xtream.downloadM3u(creds.server, creds.user, creds.pass, file)
                    Xtream.parseM3u(file)
                }
                store.save(creds)
                loggedIn = true
            } catch (e: XtreamException) {
                error = e.message
            } catch (e: IOException) {
                error = "Connexion impossible : ${e.message ?: "erreur réseau"}"
            } finally {
                loading = false
            }
        }
    }

    /** Retélécharge la playlist avec les identifiants enregistrés. */
    fun refresh() {
        saved?.let { connect(it.server, it.user, it.pass) }
            ?: store.load()?.let { connect(it.server, it.user, it.pass) }
    }

    fun logout() {
        store.clear()
        file.delete()
        entries = emptyList()
        loggedIn = false
    }
}
