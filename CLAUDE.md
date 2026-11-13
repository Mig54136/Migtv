# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Projet

MigTV : prototype Android natif (Kotlin / Jetpack Compose) avec lecteur Media3/ExoPlayer (HLS inclus). Version 0.1 : l'utilisateur saisit une URL vidéo HTTP(S) et la lit. Les étapes suivantes prévues (voir `README.md`) : connexion Xtream Codes et catalogue, EPG, favoris, pistes audio, Google Cast, transcodage MP2 → AAC. Le dépôt ne doit contenir aucun identifiant IPTV.

## Architecture

- Module unique `:app` (`fr.migtv.app`, minSdk 26, compileSdk/targetSdk 35, Java/Kotlin 17).
- Tout le code est dans `app/src/main/java/fr/migtv/app/MainActivity.kt` : `MainActivity` (ComponentActivity), le composable `MigApp` (saisie d'URL + état) et `VideoPlayer` (encapsule ExoPlayer/PlayerView).
- Pas de ressources ni de tests pour l'instant. Le manifeste autorise `INTERNET` et le trafic en clair (`usesCleartextTraffic`).
- Versions : AGP 8.7.3, Kotlin 2.0.21 (plugin Compose), Compose BOM 2024.12.01, Media3 1.5.1.

## Compilation

- Pas de Gradle wrapper dans le dépôt : la CI utilise Gradle 8.9 installé via `gradle/actions/setup-gradle` (JDK 17). En local, utiliser un Gradle 8.9 installé ou Android Studio : `gradle :app:assembleDebug`.
- La CI (`.github/workflows/android.yml`) se déclenche sur push vers `main` ou manuellement, et publie l'artefact `MigTV-debug-apk`.
- Aucun lint ni test configuré. Selon `IMPORT_GITHUB.md`, le code n'a pas encore été confirmé compilable : la CI fait foi.
- Le dépôt local n'est pas initialisé avec git ; le dépôt distant est décrit dans `IMPORT_GITHUB.md`.
