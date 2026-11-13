package fr.migtv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProfilesScreen(vm: CatalogViewModel) {
    var toDelete by remember { mutableStateOf<Profile?>(null) }

    Column(Modifier.fillMaxSize()) {
        Text("Mes profils", color = Cream, fontSize = 23.sp)
        Spacer(Modifier.height(16.dp))
        if (vm.loading) {
            LinearProgressIndicator(Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
        }
        vm.error?.let {
            Text(it, color = Color(0xFFE57373))
            Spacer(Modifier.height(8.dp))
        }
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(vm.profiles) { p ->
                Column(
                    Modifier.fillMaxWidth()
                        .background(Panel, RoundedCornerShape(12.dp))
                        .clickable(enabled = !vm.loading) { vm.open(p) }
                        .padding(start = 16.dp, top = 14.dp, end = 8.dp, bottom = 4.dp)
                ) {
                    Text(p.name, color = Cream, fontSize = 18.sp)
                    Text(p.host, color = Color.Gray, fontSize = 13.sp)
                    Row {
                        Spacer(Modifier.weight(1f))
                        TextButton(onClick = { vm.startEdit(p) }) { Text("Modifier") }
                        TextButton(onClick = { toDelete = p }) { Text("Supprimer") }
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = { vm.startEdit(null) },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) { Text("Ajouter un profil") }
    }

    toDelete?.let { p ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("Supprimer « ${p.name} » ?") },
            text = { Text("Le profil et sa playlist enregistrée seront effacés de ce téléphone.") },
            confirmButton = { TextButton(onClick = { vm.delete(p); toDelete = null }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Annuler") } }
        )
    }
}

@Composable
fun ProfileForm(vm: CatalogViewModel, profile: Profile) {
    var name by rememberSaveable(profile.id) { mutableStateOf(profile.name) }
    var server by rememberSaveable(profile.id) { mutableStateOf(profile.server) }
    var user by rememberSaveable(profile.id) { mutableStateOf(profile.user) }
    var pass by rememberSaveable(profile.id) { mutableStateOf(profile.pass) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(if (profile.id.isEmpty()) "Nouveau profil" else "Modifier le profil", color = Cream, fontSize = 23.sp)
        Spacer(Modifier.height(6.dp))
        Text("La playlist est téléchargée avec ce compte Xtream.", color = Color.LightGray)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = name, onValueChange = { name = it },
            label = { Text("Nom du profil") },
            placeholder = { Text("Salon, Abonnement 2…") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
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
            label = { Text("URL du serveur") },
            placeholder = { Text("http://exemple.fr:8080") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = user, onValueChange = { user = it },
            label = { Text("Utilisateur") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = pass, onValueChange = { pass = it },
            label = { Text("Mot de passe") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        vm.error?.let {
            Text(it, color = Color(0xFFE57373))
            Spacer(Modifier.height(12.dp))
        }
        Button(
            onClick = { vm.connect(Profile(profile.id, name, server, user, pass)) },
            enabled = !vm.loading && server.isNotBlank() && user.isNotBlank() && pass.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            if (vm.loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Téléchargement de la playlist…")
            } else {
                Text("Enregistrer et se connecter")
            }
        }
        if (vm.profiles.isNotEmpty()) {
            TextButton(onClick = vm::cancelForm, modifier = Modifier.fillMaxWidth()) { Text("Annuler") }
        }
    }
}

@Composable
fun CatalogScreen(vm: CatalogViewModel, onPlay: (Entry) -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var query by rememberSaveable { mutableStateOf("") }
    var group by rememberSaveable(tab) { mutableStateOf<String?>(null) }
    val kind = Kind.entries[tab]

    val ofKind = remember(vm.entries, kind) { vm.entries.filter { it.kind == kind } }
    val groups = remember(ofKind) { ofKind.map { it.group }.distinct().sorted() }
    val shown = remember(ofKind, group, query) {
        val q = query.trim()
        ofKind.filter { (group == null || it.group == group) && (q.isEmpty() || it.name.contains(q, ignoreCase = true)) }
    }

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${vm.active?.name.orEmpty()} · ${vm.entries.size}",
                color = Color.LightGray, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = vm::refresh, enabled = !vm.loading) { Text("Actualiser") }
            TextButton(onClick = vm::switchProfile) { Text("Profils") }
        }
        if (vm.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
        vm.error?.let { Text(it, color = Color(0xFFE57373)) }
        TabRow(selectedTabIndex = tab, containerColor = Navy, contentColor = Teal) {
            Kind.entries.forEachIndexed { i, k ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(k.label) })
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = query, onValueChange = { query = it },
            label = { Text("Rechercher") },
            singleLine = true, modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            item { GroupChip("Tout", group == null) { group = null } }
            items(groups) { g -> GroupChip(g, group == g) { group = g } }
        }
        Spacer(Modifier.height(8.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(shown) { e ->
                Column(
                    Modifier.fillMaxWidth().clickable { onPlay(e) }.padding(vertical = 10.dp)
                ) {
                    Text(e.name, color = Cream, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(e.group, color = Color.Gray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                HorizontalDivider(color = Panel)
            }
        }
    }
}

@Composable
private fun GroupChip(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) Navy else Cream,
        maxLines = 1,
        modifier = Modifier
            .background(if (selected) Teal else Panel, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}

@Composable
fun CastPicker(cast: CastViewModel) {
    if (!cast.picking) return
    AlertDialog(
        onDismissRequest = cast::closePicker,
        confirmButton = { TextButton(onClick = cast::closePicker) { Text("Fermer") } },
        title = { Text("Caster sur…") },
        text = {
            if (cast.routes.isEmpty()) {
                Text("Recherche d'appareils sur le Wi-Fi…")
            } else {
                Column {
                    cast.routes.forEach { route ->
                        TextButton(onClick = { cast.pick(route) }) { Text(route.name) }
                    }
                }
            }
        }
    )
}

@Composable
fun CastPanel(cast: CastViewModel) {
    Column(
        Modifier.fillMaxWidth().background(Panel, RoundedCornerShape(12.dp)).padding(16.dp)
    ) {
        Text("Diffusion sur ${cast.deviceName}", color = Cream, fontSize = 18.sp)
        Spacer(Modifier.height(16.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Conversion audio (AAC)", color = Cream, modifier = Modifier.weight(1f))
            Switch(checked = cast.converting, onCheckedChange = cast::setConvert)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            if (cast.converting) {
                "Le téléphone convertit le son et sert le flux au Chromecast. Plus gourmand en batterie ; gardez l'application ouverte."
            } else {
                "Flux envoyé tel quel. Pas de son sur le Chromecast ? Activez la conversion."
            },
            color = Color.LightGray, fontSize = 13.sp
        )
        cast.status?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = Teal)
        }
        Spacer(Modifier.height(16.dp))
        OutlinedButton(onClick = cast::stopCasting, modifier = Modifier.fillMaxWidth()) {
            Text("Arrêter la diffusion")
        }
    }
}
