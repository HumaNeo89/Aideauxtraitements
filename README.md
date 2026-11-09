# Dose Juste

Application Android personnelle de suivi des prises de médicaments.
Cette étape (E1) est seulement le squelette : un écran, rien à saisir, rien à enregistrer.

## Avertissement

Dose Juste ne donne **aucun conseil médical ni de dose**. Ce n'est pas un dispositif médical et ce n'est pas un avis. L'application ne propose pas, ne compare pas et ne limite aucune quantité.

## Dépôt public

Ce dépôt est **public**. Il ne doit contenir aucune donnée réelle, aucun nom de médicament réel, aucun secret, aucune clé, aucun keystore et aucun `local.properties`. Les exemples sont fictifs (par exemple « Testoline »).

Aucun fichier de signature n'est versionné (`.gitignore` : `*.jks`, `*.keystore`, `keystore.properties`, `local.properties`, `.env*`). Il n'y a pas de licence libre : tous droits réservés.

## SDK

| Réglage | Valeur | Pourquoi |
|---|---|---|
| `minSdk` | 34 (Android 14) | L'appli ne vise que le Pixel 7 Pro, qui tourne aujourd'hui sous Android 17. Le plan de phase 1 part d'Android 14, version où Health Connect est intégré au système (utile plus tard, pas dans E1). |
| `targetSdk` | 36 (Android 16) | Au-dessus de 35. Depuis le 31 août 2026, c'est le niveau exigé par Google Play pour une application nouvelle. Android 17 (API 37) est stable mais pas encore exigé : le viser maintenant activerait des changements de comportement inutiles pour ce squelette. |
| `compileSdk` | 37 (Android 17) | Dernière plateforme stable. Compiler contre 37 ne change pas le comportement tant que `targetSdk` reste 36. |

`applicationId` : `fr.dosejuste.app`. Nom affiché : « Dose Juste ».

## Permissions et sauvegarde

Aucune permission système. Le manifeste **fusionné** de debug ne contient pas `android.permission.INTERNET`. Le test `MergedManifestPermissionsTest` échoue s'il la trouve, ou s'il trouve une permission qui n'est pas sur sa liste autorisée.

AndroidX (`androidx.core`, tiré par Activity et Compose) ajoute une seule permission, de niveau `signature`, définie par l'appli elle-même : `fr.dosejuste.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`. Elle sert à ce que d'autres applications ne puissent pas envoyer certains broadcasts internes. Elle n'apparaît pas dans Réglages, Autorisations. Le test l'autorise explicitement et vérifie que son `protectionLevel` est `signature`.

Compose apporte aussi le récepteur `ProfileInstallReceiver`. Il est protégé par `android.permission.DUMP` : l'appli ne demande pas cette permission, elle exige que l'émetteur (le système ou le shell) la possède déjà.

Aucun `tools:node="remove"` n'est nécessaire aujourd'hui. Si une dépendance future injecte une permission système, la retirer dans `app/src/main/AndroidManifest.xml` :

```xml
<uses-permission
    android:name="android.permission.INTERNET"
    tools:node="remove" />
```

Le manifeste source devra alors déclarer `xmlns:tools="http://schemas.android.com/tools"`. Le test lit le manifeste fusionné : la permission doit y avoir disparu.

`android:allowBackup="false"`. `dataExtractionRules` et `fullBackupContent` excluent les domaines root, file, database, sharedpref et external, pour la sauvegarde cloud et pour le transfert d'appareil. Cette étape ne stocke encore aucune donnée.

## Compiler

JDK 17 ou 21, et un Android SDK avec la plateforme `android-37.0` (ou les licences acceptées, pour que le plugin Gradle la télécharge).

```bash
./gradlew assembleDebug lint test
```

L'APK debug est `app/build/outputs/apk/debug/app-debug.apk`. La CI GitHub Actions lance la même commande sur les pull requests, sans secret.

## Contrôle sur un Pixel 7 Pro

1. Installer l'APK debug. L'écran affiche « Dose Juste » et la phrase qui refuse tout conseil médical ou de dose. Aucune autorisation n'est demandée.
2. Réglages, Applications, Dose Juste, Autorisations : aucune autorisation n'est demandée ni accordée.
3. Mode avion, rouvrir l'appli : le même écran, sans erreur réseau.

Il n'y a pas encore de saisie. Ne pas y entrer un vrai traitement.
