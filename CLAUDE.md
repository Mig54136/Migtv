# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Projet

MigTV : client IPTV Android natif (Kotlin / Jetpack Compose) avec lecteur Media3/ExoPlayer. Objectif principal : lire les flux IPTV Xtream Codes sur Chromecast 1re génération, dont l'audio (MP2, AC3...) n'est pas décodé. Priorité : conversion audio vers AAC via un relais local (FFmpeg -> HLS servi sur le Wi-Fi -> Google Cast). Le décalage audio n'est pas prioritaire. Le dépôt ne doit contenir aucun identifiant IPTV.

## Architecture

- Module unique `:app` (`fr.migtv.app`, minSdk 26, compileSdk/targetSdk 35, Java/Kotlin 17). Pas de ressources ni de tests.
- `Xtream.kt` : login via `player_api.php`, téléchargement de `get.php?type=m3u_plus` puis parsing M3U (sans regex, catégories dédupliquées) ; une entrée est classée Chaînes/Films/Séries d'après `/movie/` et `/series/` dans l'URL. `Catalog` regroupe la playlist par type avec ses catégories (calculé une fois au chargement).
- Interface : style Windows 95 sobre (gris, biseaux, barre de titre bleu marine, coins carrés). `Theme.kt` (palette + `MigTheme`) et `Win95.kt` (composants maison `WinWindow`, `WinButton`, `WinField`, `WinTabs`, `WinCheck`, `WinProgress`, `WinDialog`, `Modifier.bevel`) ; les écrans n'utilisent que `Text` de Material 3. UX inchangée : profils -> catalogue -> lecteur.
- `SessionStore.kt` (créé hors du thread principal par `CatalogViewModel`, `starting` tant que le chargement initial n'est pas fini) : profils Xtream multiples (nom, serveur, utilisateur, mot de passe) chiffrés (EncryptedSharedPreferences). Chaque profil a sa playlist en cache `filesDir/playlist_<id>.m3u`. Navigation : formulaire de profil -> liste des profils -> catalogue -> lecteur.
- `CatalogViewModel` (connexion, catalogue) et `PlayerViewModel` (possède l'unique `ExoPlayer`, survit à la rotation ; pause à `ON_STOP`). `MainActivity` n'a que la navigation (connexion -> catalogue -> lecteur) et le plein écran ; `Screens.kt` contient les écrans.
- Cast : `CastViewModel` pilote le SDK Google Cast (récepteur par défaut) ; le sélecteur d'appareils est un dialogue Compose basé sur `MediaRouter` (pas de `MediaRouteButton`, qui exige un thème AppCompat). Par défaut l'URL du flux est envoyée telle quelle ; l'interrupteur « Conversion audio » (mémorisé par chaîne, clé = hash de l'URL car elle contient les identifiants) passe par `AudioRelay` : FFmpegKit (fork `dev.ffmpegkit-maintained`) copie la vidéo, convertit l'audio en AAC et écrit du HLS dans le cache, servi par NanoHTTPD ; `RelayService` est le service de premier plan qui maintient le relais en vie.
- Le manifeste déclare `configChanges` : l'activité n'est pas recréée à la rotation. Trafic en clair autorisé (serveurs Xtream souvent en http).
- Versions : AGP 8.7.3, Kotlin 2.0.21 (plugin Compose), Compose BOM 2024.12.01, Media3 1.5.1.

## Compilation

- Pas de Gradle wrapper dans le dépôt : la CI utilise Gradle 8.9 installé via `gradle/actions/setup-gradle` (JDK 17). En local, le build fonctionne (vérifié le 2026-11-20) avec Gradle 8.9 (zip `services.gradle.org/distributions/gradle-8.9-bin.zip`, pas dans le PATH : le `gradle` du PATH est le 7.4.2), JDK 21 (`C:\Program Files\Eclipse Adoptium\jdk-21.0.11.10-hotspot`) en `JAVA_HOME` et `ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk` : `gradle :app:assembleDebug --no-daemon`, APK dans `app/build/outputs/apk/debug/`. Attention : un APK local est signé avec une autre clé de debug que celui de la CI ; l'installer par-dessus l'APK de la CI exige de désinstaller (perte des profils).
- La CI (`.github/workflows/android.yml`) se déclenche sur push vers `main` ou manuellement, et publie l'artefact `MigTV-debug-apk`.
- Aucun lint ni test configuré. `adb` : `%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe` (hors PATH).
- Dépôt personnel : `https://github.com/Mig54136/Migtv` (branche `main`), identité git locale `Mig54136` (la config globale est celle de l'entreprise : ne pas pousser avec).
