package fr.migtv.app

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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
fun LoginScreen(vm: CatalogViewModel) {
    var server by rememberSaveable { mutableStateOf(vm.saved?.server.orEmpty()) }
    var user by rememberSaveable { mutableStateOf(vm.saved?.user.orEmpty()) }
    var pass by rememberSaveable { mutableStateOf("") }

    Column(Modifier.fillMaxWidth()) {
        Text("Connexion Xtream", color = Cream, fontSize = 23.sp)
        Spacer(Modifier.height(6.dp))
        Text("La playlist M3U est téléchargée avec votre compte.", color = Color.LightGray)
        Spacer(Modifier.height(24.dp))
        OutlinedTextField(
            value = server, onValueChange = { server = it },
            label = { Text("Serveur") },
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
            onClick = { vm.connect(server, user, pass) },
            enabled = !vm.loading && server.isNotBlank() && user.isNotBlank() && pass.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(10.dp)
        ) {
            if (vm.loading) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Téléchargement de la playlist…")
            } else {
                Text("Se connecter")
            }
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
            Text("${vm.entries.size} éléments", color = Color.LightGray, modifier = Modifier.weight(1f))
            TextButton(onClick = vm::refresh, enabled = !vm.loading) { Text("Actualiser") }
            TextButton(onClick = vm::logout) { Text("Déconnexion") }
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
