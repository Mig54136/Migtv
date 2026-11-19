package fr.migtv.app

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontFamily
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

    val version = remember {
        runCatching { activity.packageManager.getPackageInfo(activity.packageName, 0).versionName }
            .getOrNull().orEmpty()
    }

    // Fond gris clair : icônes sombres dans les barres système.
    SideEffect {
        val controller = WindowCompat.getInsetsController(activity.window, activity.window.decorView)
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
    }

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

    MigTheme {
        CastPicker(cast)
        if (fullscreen) {
            Box(Modifier.fillMaxSize().background(Color.Black)) {
                VideoPlayer(vm, Modifier.fillMaxSize())
                PlayerError(vm, Modifier.align(Alignment.Center))
            }
        } else {
            val title = when {
                vm.playing -> vm.entry?.name ?: "Lecture"
                cat.starting -> "MigTV"
                cat.formProfile != null -> if (cat.formProfile?.id.isNullOrEmpty()) "Nouveau profil" else "Modifier le profil"
                cat.active == null -> "Mes profils"
                else -> cat.active?.name.orEmpty()
            }
            WinWindow(
                title = title,
                modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(6.dp),
                fill = true,
                trailing = {
                    Text(
                        "v$version", color = Hilite,
                        fontFamily = FontFamily.Monospace, fontSize = 12.sp
                    )
                }
            ) {
                if (vm.playing) {
                    Column(Modifier.fillMaxSize()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            WinButton("< Retour", onClick = ::leavePlayer, compact = true)
                            Spacer(Modifier.weight(1f))
                            val current = vm.entry
                            if (cast.available && current != null && !cast.casting) {
                                WinButton("Caster", onClick = { cast.openPicker(current) }, compact = true)
                                Spacer(Modifier.width(6.dp))
                            }
                            WinButton("Plein écran", onClick = {
                                activity.requestedOrientation =
                                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            }, compact = true)
                        }
                        Spacer(Modifier.height(8.dp))
                        if (cast.casting) {
                            CastPanel(cast)
                        } else {
                            Box(Modifier.fillMaxWidth().height(260.dp).background(Color.Black)) {
                                VideoPlayer(vm, Modifier.fillMaxSize())
                                PlayerError(vm, Modifier.align(Alignment.Center))
                            }
                            cast.status?.let {
                                Spacer(Modifier.height(8.dp))
                                Text(it, color = InkSoft)
                            }
                        }
                    }
                } else if (cat.starting) {
                    StartingScreen()
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
        Text(message, color = Color.White)
        Spacer(Modifier.height(8.dp))
        WinButton("Réessayer", onClick = vm::retry)
    }
}
