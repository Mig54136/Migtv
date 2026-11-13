# Importer MigTV sur GitHub

Le dépôt GitHub ne décompresse PAS automatiquement un ZIP envoyé via « Upload files ».

## Méthode recommandée sur Windows (sans Android Studio)
1. Téléchargez et décompressez `MigTV-source.zip`.
2. Installez Git si nécessaire : https://git-scm.com/downloads
3. Ouvrez PowerShell dans le dossier `MigTV` extrait.
4. Exécutez :

   git init
   git branch -M main
   git add .
   git commit -m "Initial MigTV Android prototype"
   git remote add origin https://github.com/Mig54136/Migtv.git
   git push -u origin main

Si Git réclame une authentification, utilisez la connexion GitHub du navigateur.
Si Git indique « remote origin already exists », sautez l'étape `git remote add origin`.

## APK
Sur GitHub, ouvrez Actions > Android APK > dernière exécution.
Si elle est verte, téléchargez l'artefact `MigTV-debug-apk`.

## Limites
Le code n'a pas été compilé dans cet environnement. Le workflow doit encore confirmer la compilation.
