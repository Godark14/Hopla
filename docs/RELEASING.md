# Publier une release

## Une seule fois : créer le keystore

```bash
keytool -genkeypair -v -keystore hopla-release.jks -alias hopla -keyalg RSA -keysize 4096 -validity 10000
```

Range-le **hors du dépôt** et sauvegarde-le avec ses mots de passe (gestionnaire de mots de passe,
clé USB…). ⚠️ Sans lui, impossible de publier une mise à jour installable par-dessus la version
déjà installée chez les utilisateurs.

Puis, dans `local.properties` (jamais versionné) :

```properties
release.storeFile=C:/chemin/vers/hopla-release.jks
release.storePassword=...
release.keyAlias=hopla
release.keyPassword=...
```

## À chaque version

1. Dans `app/build.gradle.kts`, incrémente `versionCode` (+1) et `versionName` (ex. `1.1.0`).
2. Dans `CHANGELOG.md`, déplace le contenu de « Non publié » sous une nouvelle section datée.
3. Compile : `./gradlew assembleRelease` → `app/build/outputs/apk/release/app-release.apk`.
4. Commit, puis tag : `git tag v1.1.0 && git push --tags`.
5. Sur GitHub, crée une release depuis le tag : colle la section du CHANGELOG et joins l'APK.
