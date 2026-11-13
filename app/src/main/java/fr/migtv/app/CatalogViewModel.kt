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
import java.util.UUID

class CatalogViewModel(app: Application) : AndroidViewModel(app) {
    private val store = SessionStore(app)
    private val dir = app.filesDir

    /** Chaque profil a sa propre playlist en cache : changer de profil est instantané. */
    private fun cacheFile(p: Profile) = File(dir, "playlist_${p.id}.m3u")

    var profiles by mutableStateOf(store.profiles())
        private set
    /** Profil ouvert ; null = écran de choix des profils. */
    var active by mutableStateOf<Profile?>(null)
        private set
    /** Profil en cours d'édition (id vide = nouveau) ; null = formulaire fermé. */
    var formProfile by mutableStateOf<Profile?>(null)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var entries by mutableStateOf<List<Entry>>(emptyList())
        private set

    init {
        val last = profiles.firstOrNull { it.id == store.lastActive }
        when {
            profiles.isEmpty() -> startEdit(null)
            last != null && cacheFile(last).exists() -> open(last)
        }
    }

    fun startEdit(p: Profile?) {
        error = null
        formProfile = p ?: Profile("", "", "", "", "")
    }

    fun cancelForm() {
        formProfile = null
        error = null
    }

    /** Ouvre un profil : playlist en cache si elle existe, sinon téléchargement. */
    fun open(p: Profile) {
        val f = cacheFile(p)
        if (!f.exists()) {
            connect(p)
            return
        }
        viewModelScope.launch {
            loading = true
            error = null
            try {
                entries = withContext(Dispatchers.IO) { Xtream.parseM3u(f) }
                active = p
                store.lastActive = p.id
            } catch (e: IOException) {
                connect(p)
            } finally {
                loading = false
            }
        }
    }

    /** Vérifie les identifiants, télécharge la playlist, puis enregistre et ouvre le profil. */
    fun connect(input: Profile) {
        if (loading) return
        val p = input.copy(
            id = input.id.ifEmpty { UUID.randomUUID().toString() },
            server = Xtream.normalizeServer(input.server),
            user = input.user.trim()
        ).let { it.copy(name = it.name.trim().ifEmpty { it.host }) }
        viewModelScope.launch {
            loading = true
            error = null
            try {
                entries = withContext(Dispatchers.IO) {
                    Xtream.login(p.server, p.user, p.pass)
                    Xtream.downloadM3u(p.server, p.user, p.pass, cacheFile(p))
                    Xtream.parseM3u(cacheFile(p))
                }
                profiles = profiles.filter { it.id != p.id } + p
                store.saveProfiles(profiles)
                store.lastActive = p.id
                active = p
                formProfile = null
            } catch (e: XtreamException) {
                error = e.message
            } catch (e: IOException) {
                error = "Connexion impossible : ${e.message ?: "erreur réseau"}"
            } finally {
                loading = false
            }
        }
    }

    /** Retélécharge la playlist du profil ouvert. */
    fun refresh() {
        active?.let(::connect)
    }

    /** Retour au choix des profils (sans rien supprimer). */
    fun switchProfile() {
        active = null
        entries = emptyList()
        error = null
        store.lastActive = null
    }

    fun delete(p: Profile) {
        cacheFile(p).delete()
        profiles = profiles.filter { it.id != p.id }
        store.saveProfiles(profiles)
        if (profiles.isEmpty()) startEdit(null)
    }
}
