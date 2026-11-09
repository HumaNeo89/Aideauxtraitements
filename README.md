# Dose Juste

Application Android personnelle de suivi des prises de médicaments.
État : squelette (E1) et domaine journée/quantité (E2), testé sur la JVM. Il n'y a encore rien à saisir ni à enregistrer.

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

## Domaine (E2)

Code Kotlin pur dans `app/src/main/java/fr/dosejuste/app/domain/` (aucune dépendance Android, `java.time` seulement), testé sur la JVM. Pas encore d'écran ni de stockage.

**Quantité.** Valeur exacte en centièmes d'unité dans un `Long` (100 = 1), jamais en nombre à virgule flottante, toujours strictement positive. Les additions sont exactes et refusent le débordement. Affichage français sans zéro inutile : « 0,25 », « 0,4 », « 1 », « 10,5 », sans séparateur de milliers.

**Unités** (liste fermée) : mg, comprimé, goutte, ml. « comprimé » et « goutte » prennent un « s » à partir de 2 (« 0,5 comprimé », « 1 comprimé », « 2 comprimés ») ; mg et ml sont invariables.

**Saisie.** Acceptée : chiffres ASCII, virgule ou point décimal suivi d'1 ou 2 chiffres, espaces autour ignorés (`0,25`, `0.4`, `1`, ` 1,5 `, `10,50`, `0,50`). Refusée :

| Saisie | Motif |
|---|---|
| vide ou espaces | `VIDE` |
| `,5`, `5,`, `+1`, `1e3`, `1 000`, `1.000,5`, lettres, chiffres non ASCII | `FORMAT` |
| `0,125` (plus de 2 décimales) | `TROP_DE_DECIMALES` |
| `0`, `0,00`, `-1` | `NUL_OU_NEGATIF` |
| au-delà de 100 000 | `TROP_GRAND` |

Le plafond de 100 000 est une simple garde contre les fautes de frappe, pas une valeur médicale.

**Journée.** Fuseau par défaut `Europe/Paris`, heure pivot réglable à la minute (défaut 05:00). Une prise dont l'heure locale est avant le pivot compte pour la veille : avec 05:00, une prise à 00:59 ou 04:59 compte pour la veille, une prise à 05:00 pour le jour même. Avec 23:00, la journée D va du jour D à 23:00 au jour D+1 à 22:59:59.

Une journée dure 24 h, 23 h ou 25 h autour des changements d'heure. Elle commence au premier instant où l'heure locale atteint le pivot :
- pivot dans l'heure qui n'existe pas (ex. 02:30 le 29/03/2026) : la journée commence au saut, à 03:00 ;
- pivot dans l'heure vécue deux fois (ex. 02:30 le 25/10/2026) : la journée commence à la première occurrence ; la seconde ne la renvoie pas à la veille.

Chaque instant appartient ainsi à une seule journée ; un test le vérifie quart d'heure par quart d'heure de mars à fin octobre 2026, pour plusieurs pivots.

**Prises.** L'horodatage réel n'est jamais modifié. Une prise peut être réattribuée manuellement à une autre journée, puis rétablie. Les totaux du jour additionnent les prises confirmées par médicament et par unité ; les prises annulées et les unités différentes ne sont jamais mélangées. Aucun total n'est comparé à quoi que ce soit.

## Compiler

JDK 17 ou 21, et un Android SDK avec la plateforme `android-37.0` (ou les licences acceptées, pour que le plugin Gradle la télécharge).

```bash
./gradlew assembleDebug lint test
```

L'APK debug est `app/build/outputs/apk/debug/app-debug.apk`. La CI GitHub Actions lance la même commande sur les pull requests, sans secret, et publie cet APK comme artefact `dose-juste-debug-apk` (conservé 14 jours). Ce n'est pas un APK release signé.

## Contrôle sur un Pixel 7 Pro

1. Installer l'APK debug. L'écran affiche « Dose Juste » et la phrase qui refuse tout conseil médical ou de dose. Aucune autorisation n'est demandée.
2. Réglages, Applications, Dose Juste, Autorisations : aucune autorisation n'est demandée ni accordée.
3. Mode avion, rouvrir l'appli : le même écran, sans erreur réseau.

Il n'y a pas encore de saisie. Ne pas y entrer un vrai traitement.
