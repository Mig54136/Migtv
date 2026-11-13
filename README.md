# MigTV

Prototype Android natif en Kotlin / Jetpack Compose, avec lecteur Media3/ExoPlayer.

## Fonctionnel en version 0.1
- Interface sombre et minimaliste
- Saisie d'une URL vidéo HTTP(S)
- Lecture sur téléphone via Media3, y compris HLS
- GitHub Actions pour générer un APK debug

## Non implémenté (étapes suivantes)
- Connexion Xtream Codes et catalogue chaînes / films / séries
- EPG, favoris et persistance
- Pistes audio, décalage audio et format d'image
- Google Cast direct
- Transcodage MP2 → AAC local et serveur HTTP accessible au Chromecast

## Compilation
Un push sur `main` lance `.github/workflows/android.yml`.
Dans GitHub > Actions > Android APK > exécution réussie > Artifacts, télécharger `MigTV-debug-apk`.

Le dépôt ne doit contenir aucun identifiant IPTV. N'utilisez que des flux autorisés.
