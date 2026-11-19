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
    // Créé hors du thread principal : l'initialisation du chiffrement (Keystore) est lente.
    private lateinit var store: SessionStore
    private val dir = app.filesDir

    /** Chaque profil a sa propre playlist en cache : changer de profil est instantané. */
    private fun cacheFile(p: Profile) = File(dir, "playlist_${p.id}.m3u")

    /** Vrai tant que les profils et la dernière playlist ne sont pas chargés (démarrage). */
    var starting by mutableStateOf(true)
        private set
    var profiles by mutableStateOf<List<Profile>>(emptyList())
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
    var catalog by mutableStateOf(Catalog.EMPTY)
        private set

    init {
        viewModelScope.launch {
            val (list, lastId) = withContext(Dispatchers.IO) {
                store = SessionStore(app)
                store.profiles() to store.lastActive
            }
            profiles = list
            val last = list.firstOrNull { it.id == lastId }
            if (list.isEmpty()) {
                startEdit(null)
            } else if (last != null && cacheFile(last).exists()) {
                try {
                    catalog = withContext(Dispatchers.IO) { Catalog(Xtream.parseM3u(cacheFile(last))) }
                    active = last
                } catch (e: IOException) {
                    // Cache illisible : l'utilisateur choisira son profil et la playlist sera retéléchargée.
                }
            }
            starting = false
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
                catalog = withContext(Dispatchers.IO) { Catalog(Xtream.parseM3u(f)) }
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
                catalog = withContext(Dispatchers.IO) {
                    Xtream.login(p.server, p.user, p.pass)
                    Xtream.downloadM3u(p.server, p.user, p.pass, cacheFile(p))
                    Catalog(Xtream.parseM3u(cacheFile(p)))
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
        catalog = Catalog.EMPTY
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
