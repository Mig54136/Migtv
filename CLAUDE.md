# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Projet

MigTV : client IPTV Android natif (Kotlin / Jetpack Compose) avec lecteur Media3/ExoPlayer. Objectif principal : lire les flux IPTV Xtream Codes sur Chromecast 1re génération, dont l'audio (MP2, AC3...) n'est pas décodé. Priorité : conversion audio vers AAC via un relais local (FFmpeg -> HLS servi sur le Wi-Fi -> Google Cast). Le décalage audio n'est pas prioritaire. Le dépôt ne doit contenir aucun identifiant IPTV.

## Architecture

- Module unique `:app` (`fr.migtv.app`, minSdk 26, compileSdk/targetSdk 35, Java/Kotlin 17). Pas de ressources ni de tests.
- `Xtream.kt` : login via `player_api.php`, téléchargement de `get.php?type=m3u_plus` puis parsing M3U ; une entrée est classée Chaînes/Films/Séries d'après `/movie/` et `/series/` dans l'URL.
- `SessionStore.kt` : identifiants chiffrés (EncryptedSharedPreferences). La playlist est en cache dans `filesDir/playlist.m3u`.
- `CatalogViewModel` (connexion, catalogue) et `PlayerViewModel` (possède l'unique `ExoPlayer`, survit à la rotation ; pause à `ON_STOP`). `MainActivity` n'a que la navigation (connexion -> catalogue -> lecteur) et le plein écran ; `Screens.kt` contient les écrans.
- Le manifeste déclare `configChanges` : l'activité n'est pas recréée à la rotation. Trafic en clair autorisé (serveurs Xtream souvent en http).
- Versions : AGP 8.7.3, Kotlin 2.0.21 (plugin Compose), Compose BOM 2024.12.01, Media3 1.5.1.

## Compilation

- Pas de Gradle wrapper dans le dépôt : la CI utilise Gradle 8.9 installé via `gradle/actions/setup-gradle` (JDK 17). En local, utiliser un Gradle 8.9 installé ou Android Studio : `gradle :app:assembleDebug`.
- La CI (`.github/workflows/android.yml`) se déclenche sur push vers `main` ou manuellement, et publie l'artefact `MigTV-debug-apk`.
- Aucun lint ni test configuré : la CI fait foi pour la compilation (aucun build local n'est possible ici).
- Dépôt personnel : `https://github.com/Mig54136/Migtv` (branche `main`), identité git locale `Mig54136` (la config globale est celle de l'entreprise : ne pas pousser avec).
