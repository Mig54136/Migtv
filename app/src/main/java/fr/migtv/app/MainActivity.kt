package fr.migtv.app

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MigApp() }
    }
}

@Composable
private fun MigApp(vm: PlayerViewModel = viewModel(), cat: CatalogViewModel = viewModel(),
    cast: CastViewModel = viewModel()
) {
    val activity = LocalContext.current as ComponentActivity
    val landscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    val fullscreen = vm.playing && landscape && !cast.casting

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { vm.onAppStopped() }
    LifecycleEventEffect(Lifecycle.Event.ON_START) { vm.onAppStarted() }

    // Barres système masquées en plein écran.
    DisposableEffect(fullscreen) {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        if (fullscreen) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        }
        onDispose { controller.show(WindowInsetsCompat.Type.systemBars()) }
    }

    // Quitter le lecteur ramène en portrait libre.
    fun leavePlayer() {
        cast.stopCasting()
        vm.stop()
        activity.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
    BackHandler(enabled = vm.playing) { leavePlayer() }

    // Pendant un cast, le lecteur local est mis en pause.
    LaunchedEffect(cast.casting) { if (cast.casting) vm.player.pause() }
    CastPicker(cast)

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Teal, background = Navy,
            surface = Panel, onBackground = Cream
        )
    ) {
        if (fullscreen) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                VideoPlayer(vm, Modifier.fillMaxSize())
                PlayerError(vm, Modifier.align(Alignment.Center))
            }
        } else {
            Column(Modifier.fillMaxSize().background(Navy).padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("MIG", color = Cream, fontSize = 27.sp)
                    Text("TV", color = Teal, fontSize = 27.sp)
                    Spacer(Modifier.weight(1f))
                    Text("0.2", color = Color.LightGray)
                }
                Spacer(Modifier.height(24.dp))
                if (vm.playing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = ::leavePlayer) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Retour")
                        }
                        Spacer(Modifier.weight(1f))
                        val current = vm.entry
                        if (cast.available && current != null && !cast.casting) {
                            TextButton(onClick = { cast.openPicker(current) }) { Text("Caster") }
                        }
                        TextButton(onClick = {
                            activity.requestedOrientation =
                                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        }) { Text("Plein écran") }
                    }
                    if (cast.casting) {
                        CastPanel(cast)
                    } else {
                        Box(Modifier.fillMaxWidth().height(260.dp)) {
                            VideoPlayer(vm, Modifier.fillMaxSize())
                            PlayerError(vm, Modifier.align(Alignment.Center))
                        }
                        cast.status?.let { Text(it, color = Color.LightGray) }
                    }
                } else if (cat.formProfile != null) {
                    ProfileForm(cat, cat.formProfile!!)
                } else if (cat.active == null) {
                    ProfilesScreen(cat)
                } else {
                    CatalogScreen(cat, onPlay = { vm.play(it) })
                }
            }
        }
    }
}

/** Affiche le lecteur du ViewModel ; ne le libère pas (il appartient au ViewModel). */
@Composable
private fun VideoPlayer(vm: PlayerViewModel, modifier: Modifier) {
    AndroidView(
        factory = { PlayerView(it).apply { player = vm.player } },
        update = { it.player = vm.player },
        onRelease = { it.player = null },
        modifier = modifier
    )
}

@Composable
private fun PlayerError(vm: PlayerViewModel, modifier: Modifier) {
    val message = vm.error ?: return
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(message, color = Cream)
        Spacer(Modifier.height(8.dp))
        Button(onClick = vm::retry) { Text("Réessayer") }
    }
}
