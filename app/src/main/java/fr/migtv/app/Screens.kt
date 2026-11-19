package fr.migtv.app

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/** Zone en creux à fond blanc (liste), façon boîte de liste Windows 95. */
private fun Modifier.sunken(): Modifier = bevel(raised = false, fill = Hilite).padding(2.dp)

@Composable
fun StartingScreen() {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.Center) {
        Text("Chargement…")
        Spacer(Modifier.height(8.dp))
        WinProgress()
    }
}

@Composable
fun ProfilesScreen(vm: CatalogViewModel) {
    var toDelete by remember { mutableStateOf<Profile?>(null) }
    val noRipple = remember { MutableInteractionSource() }

    Column(Modifier.fillMaxSize()) {
        if (vm.loading) {
            WinProgress()
            Spacer(Modifier.height(8.dp))
        }
        vm.error?.let {
            Text(it, color = ErrorRed)
            Spacer(Modifier.height(8.dp))
        }
        LazyColumn(Modifier.weight(1f).fillMaxWidth().sunken()) {
            items(vm.profiles) { p ->
                Column(
                    Modifier.fillMaxWidth()
                        .clickable(interactionSource = noRipple, indication = null, enabled = !vm.loading) { vm.open(p) }
                        .padding(8.dp)
                ) {
                    Text(p.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(p.host, color = InkSoft, fontSize = 13.sp)
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        WinButton("Modifier", onClick = { vm.startEdit(p) }, compact = true)
                        Spacer(Modifier.width(8.dp))
                        WinButton("Supprimer", onClick = { toDelete = p }, compact = true)
                    }
                }
                WinSeparator()
            }
        }
        Spacer(Modifier.height(10.dp))
        WinButton("Ajouter un profil", onClick = { vm.startEdit(null) }, modifier = Modifier.fillMaxWidth())
    }

    toDelete?.let { p ->
        WinDialog("Supprimer", onDismiss = { toDelete = null }) {
            Text("Supprimer « ${p.name} » ?", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text("Le profil et sa playlist enregistrée seront effacés de ce téléphone.")
            Spacer(Modifier.height(14.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                WinButton("Supprimer", onClick = { vm.delete(p); toDelete = null })
                Spacer(Modifier.width(8.dp))
                WinButton("Annuler", onClick = { toDelete = null })
            }
        }
    }
}

@Composable
fun ProfileForm(vm: CatalogViewModel, profile: Profile) {
    var name by rememberSaveable(profile.id) { mutableStateOf(profile.name) }
    var server by rememberSaveable(profile.id) { mutableStateOf(profile.server) }
    var user by rememberSaveable(profile.id) { mutableStateOf(profile.user) }
    var pass by rememberSaveable(profile.id) { mutableStateOf(profile.pass) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text("La playlist est téléchargée avec ce compte Xtream.", color = InkSoft)
        Spacer(Modifier.height(16.dp))
        WinField(
            value = name, onValueChange = { name = it },
            label = "Nom du profil", placeholder = "Salon, Abonnement 2…",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        WinField(
            value = server,
            onValueChange = { text ->
                // Une URL complète collée (get.php?username=..&password=..) remplit tous les champs.
                val parsed = Xtream.parseProviderUrl(text)
                if (parsed != null) {
                    server = parsed.first; user = parsed.second; pass = parsed.third
                } else {
                    server = text
                }
            },
            label = "URL du serveur", placeholder = "http://exemple.fr:8080",
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        WinField(
            value = user, onValueChange = { user = it },
            label = "Utilisateur", modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        WinField(
            value = pass, onValueChange = { pass = it },
            label = "Mot de passe",
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        if (vm.loading) {
            Text("Téléchargement de la playlist…")
            Spacer(Modifier.height(6.dp))
            WinProgress()
            Spacer(Modifier.height(12.dp))
        }
        vm.error?.let {
            Text(it, color = ErrorRed)
            Spacer(Modifier.height(12.dp))
        }
        WinButton(
            "Enregistrer et se connecter",
            onClick = { vm.connect(Profile(profile.id, name, server, user, pass)) },
            enabled = !vm.loading && server.isNotBlank() && user.isNotBlank() && pass.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        )
        if (vm.profiles.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            WinButton("Annuler", onClick = vm::cancelForm, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
fun CatalogScreen(vm: CatalogViewModel, onPlay: (Entry) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable(tab) { mutableStateOf<String?>(null) }
    val kind = Kind.entries[tab]
    val noRipple = remember { MutableInteractionSource() }

    // Types et catégories sont pré-calculés au chargement de la playlist.
    val all = vm.catalog.byKind[kind].orEmpty()
    val groups = vm.catalog.groups[kind].orEmpty()

    // Filtrage hors du thread principal, avec une courte attente pendant la frappe :
    // un changement de clé annule le calcul précédent.
    val shown by produceState(all, all, group, query) {
        val q = query.trim()
        if (q.isNotEmpty()) delay(180)
        value = withContext(Dispatchers.Default) {
            if (group == null && q.isEmpty()) {
                all
            } else {
                all.filter { (group == null || it.group == group) && (q.isEmpty() || it.name.contains(q, ignoreCase = true)) }
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${vm.catalog.size} entrées",
                color = InkSoft, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            WinButton("Actualiser", onClick = vm::refresh, enabled = !vm.loading, compact = true)
            Spacer(Modifier.width(6.dp))
            WinButton("Profils", onClick = vm::switchProfile, compact = true)
        }
        Spacer(Modifier.height(8.dp))
        if (vm.loading) {
            WinProgress()
            Spacer(Modifier.height(8.dp))
        }
        vm.error?.let {
            Text(it, color = ErrorRed)
            Spacer(Modifier.height(8.dp))
        }
        WinTabs(Kind.entries.map { it.label }, tab) { tab = it }
        Spacer(Modifier.height(8.dp))
        WinField(
            value = query, onValueChange = { query = it },
            label = "Rechercher", modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            item { WinButton("Tout", onClick = { group = null }, compact = true, latched = group == null) }
            items(groups) { g -> WinButton(g, onClick = { group = g }, compact = true, latched = group == g) }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.weight(1f).fillMaxWidth().sunken()) {
            items(shown) { e ->
                Column(
                    Modifier.fillMaxWidth()
                        .clickable(interactionSource = noRipple, indication = null) { onPlay(e) }
                        .padding(horizontal = 8.dp, vertical = 7.dp)
                ) {
                    Text(e.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(e.group, color = InkSoft, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                WinSeparator()
            }
        }
    }
}

@Composable
fun CastPicker(cast: CastViewModel) {
    if (!cast.picking) return
    WinDialog("Caster sur…", onDismiss = cast::closePicker) {
        if (cast.routes.isEmpty()) {
            Text("Recherche d'appareils sur le Wi-Fi…")
            Spacer(Modifier.height(6.dp))
            WinProgress()
        } else {
            cast.routes.forEach { route ->
                WinButton(route.name, onClick = { cast.pick(route) }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(6.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        WinButton("Fermer", onClick = cast::closePicker, modifier = Modifier.fillMaxWidth())
    }
}

@Composable
fun CastPanel(cast: CastViewModel) {
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Text("Diffusion sur ${cast.deviceName}", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        Spacer(Modifier.height(14.dp))
        WinCheck(cast.converting, "Conversion audio (AAC)", cast::setConvert, Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Text(
            if (cast.converting) {
                "Le téléphone convertit le son et sert le flux au Chromecast. Plus gourmand en batterie ; gardez l'application ouverte."
            } else {
                "Flux envoyé tel quel. Pas de son sur le Chromecast ? Activez la conversion."
            },
            color = InkSoft, fontSize = 13.sp
        )
        cast.status?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = TitleBlue, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        WinButton("Arrêter la diffusion", onClick = cast::stopCasting, modifier = Modifier.fillMaxWidth())
    }
}
