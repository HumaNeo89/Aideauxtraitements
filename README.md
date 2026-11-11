# Dose Juste

Application Android personnelle de suivi des prises de médicaments.
État : squelette (E1), domaine journée/quantité (E2), base locale chiffrée (E3) saisie d'une prise avec annulation (E4) et écrans Aujourd'hui, Historique, Réglages, Avertissement, Licences (E5), build release préparée : signature conditionnelle et R8 (E6).

Build release signée : voir [docs/release.md](docs/release.md).

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

`android:allowBackup="false"`. `dataExtractionRules` et `fullBackupContent` excluent les domaines root, file, database, sharedpref et external, pour la sauvegarde cloud et pour le transfert d'appareil. La base chiffrée (domaine database) et le fichier de clé chiffrée (dossier `no_backup`, sous root, jamais sauvegardé par Android) sont donc exclus ; `EmplacementsSauvegardeTest` le vérifie.

SQLCipher et Room n'ajoutent aucune permission (manifeste fusionné vérifié) : aucun `tools:node="remove"` n'a été nécessaire en E3.

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

## Base chiffrée (E3)

**Architecture.**

| Paquet | Rôle |
|---|---|
| `security/` | `DbKeyManager` (phrase de passe de la base), `ChiffreurCle` (interface), `KeystoreChiffreur` (Android Keystore). |
| `data/` | Room : entités `medicament`, `prise`, `alias` ; `Convertisseurs` ; DAO ; `DoseJusteDatabase` (version 1) ; `BaseChiffree` (fabrique SQLCipher) ; `PrisesRepository` (opérations en types du domaine). |

Room 2.8.5 (compilateur via KSP 2.3.13, qui s'applique avec le Kotlin intégré d'AGP 9 sans plugin `kotlin-android`) et SQLCipher pour Android 4.20.0 (`net.zetetic:sqlcipher-android`, ouvert par `SupportOpenHelperFactory`). androidx.sqlite est déclaré en 2.7.1 : SQLCipher 4.20.0 exige au moins 2.7.0, Room 2.8.5 accepte cette version.

**Où est la clé.**
1. Au premier lancement (ni fichier de clé ni base), `DbKeyManager` tire 32 octets aléatoires (`SecureRandom`) : c'est la phrase de passe SQLCipher.
2. Elle est chiffrée en AES-256-GCM par une clé de l'Android Keystore (alias `dosejuste_db_kek_v1`, non exportable, StrongBox si disponible, sinon Keystore matériel standard, sans authentification de la personne en phase 1).
3. Seuls l'IV et le texte chiffré sont écrits dans `no_backup/dosejuste_db_key.bin` (dossier privé de l'appli), par fichier temporaire puis renommage atomique. La phrase n'est jamais écrite en clair, ni en SharedPreferences, ni dans un journal.
4. Aux lancements suivants, le fichier est relu et déchiffré par le Keystore. Les copies intermédiaires sont mises à zéro ; SQLCipher garde la phrase en mémoire tant que la base est ouverte.

**Ce qui est chiffré.** Tout le fichier `databases/dosejuste.db` et ses journaux (pages, index, en-tête) : médicaments, prises, alias. Sans la phrase, le fichier ne commence même pas par l'en-tête « SQLite format 3 » (test instrumenté).

**Cas d'erreur : jamais d'écrasement.** Si le fichier de clé existe mais que la clé Keystore a disparu, si le Keystore refuse de le déchiffrer (clé invalidée, fichier altéré), si le fichier est tronqué, ou si la base existe sans fichier de clé, `DbKeyManager` lève une `DonneesIllisiblesException` (une sous-classe par cas). Rien n'est régénéré, effacé ni remplacé ; l'interface (étape ultérieure) affichera « données illisibles ». Seul le cas « ni fichier de clé ni base » crée une nouvelle clé.

**Perte de données (attendu en phase 1, aucune sauvegarde).**

| Événement | Effet |
|---|---|
| Désinstallation | Base, fichier de clé et clé Keystore supprimés : données perdues. |
| Réglages, Stockage, Effacer les données | Même effet : l'appli repart vide. |
| Réinitialisation du téléphone | Données perdues. |
| Changement ou retrait du code de verrouillage | Sans effet en phase 1 : la clé n'exige pas d'authentification, elle n'est pas invalidée. |
| Nouveau téléphone, sauvegarde Google | Rien n'est transféré ni sauvegardé (règles d'exclusion, `allowBackup="false"`). |
| Perte de la seule clé Keystore ou du seul fichier de clé | Base conservée mais illisible ; erreur explicite, aucune réparation possible. |

Il n'existe ni export ni copie de secours en phase 1 : ne pas compter sur l'appli comme seule trace d'un traitement.

**Schéma.** Tables `medicament` (libellé unique, non vide), `prise` (clé étrangère vers `medicament`, `ON DELETE RESTRICT` ; quantité en centièmes ; unité et statut stockés par nom d'énumération ; `horodatage_utc_ms` = instant réel en millisecondes UTC ; `journee_forcee` ISO `yyyy-MM-dd` ou NULL ; `cree_le`, `maj_le`), `alias` (vide en phase 1). Index sur l'horodatage, le médicament et la journée forcée. Room ne génère pas de CHECK : des déclencheurs SQL refusent quantité nulle ou négative, unité ou statut inconnus, journée forcée mal formée, libellé vide. À la lecture, une valeur inconnue lève `ValeurStockeeInvalideException`. Une prise n'est jamais supprimée : l'annulation passe son statut à `ANNULEE`.

**Pas de table « journée ».** La journée se recalcule depuis l'horodatage réel et l'heure pivot (E2). La stocker figerait le pivot de la saisie : un changement de pivot laisserait des journées fausses. Seule la réattribution manuelle est enregistrée (`journee_forcee`).

**Requête par journée.** `PriseDao.deLaJournee(debut, fin, journee)`, avec `[debut, fin)` = `JourneeCalculator.bornes(journee)`, renvoie les prises non réattribuées de l'intervalle et celles réattribuées à cette journée (même si leur horodatage est loin). Le repository refiltre avec `prisesDuJour` du domaine, qui fait foi. `PriseDao.entre(debut, fin)` reste disponible pour un intervalle brut.

**Migrations.** `DoseJusteDatabase` est en version 1, schéma exporté dans `app/schemas/` (versionné, ajouté aux assets des tests instrumentés). Migration destructive interdite : toute évolution ajoute une `Migration` à `DoseJusteDatabase.MIGRATIONS` (vide aujourd'hui) et un test `MigrationTestHelper`. Les déclencheurs sont recréés à chaque ouverture (`IF NOT EXISTS`) ; une migration qui change leur définition doit d'abord les supprimer.

**Tests.**
- JVM (`./gradlew test`) : converters, mapping entité ↔ domaine, états de `DbKeyManager` avec un faux chiffreur, DAO et repository avec Robolectric sur une base Room en mémoire **non chiffrée** (la bibliothèque native SQLCipher ne tourne pas sur la JVM), emplacements et règles de sauvegarde, garde-fous de vocabulaire et de journaux sur `data/` et `security/`. Robolectric tourne en API 34 (les images 35 et plus exigent un JDK 21).
- Instrumentés (`app/src/androidTest`) : base SQLCipher réelle (en-tête chiffré, réouverture, mauvaise phrase refusée), `DbKeyManager` avec le vrai Keystore, création et validation de la version 1 depuis le schéma exporté. Ils utilisent leurs propres alias Keystore et fichiers, jamais ceux de l'appli.

Lancer les tests instrumentés sur le téléphone (débogage USB activé, une seule machine branchée) :

```bash
./gradlew connectedDebugAndroidTest
```

**Attention :** cette tâche installe l'APK debug par-dessus l'appli debug existante et la **désinstalle à la fin**, ce qui efface sa base. Ne pas la lancer sur un téléphone où l'appli debug contient des données à garder.

## Saisie (E4)

Code dans `app/src/main/java/fr/dosejuste/app/ui/` : une seule activité, Compose Material 3, pas de bibliothèque de navigation (un état d'écran suffit), pas d'injection de dépendances.

**Parcours.** Écran « Aujourd'hui » : nom de l'appli et phrase d'avertissement toujours visibles, bouton « Nouvelle prise », liste des prises de la journée (« 14:05 — 0,25 mg de Testoline », ligne barrée et badge « annulée » si annulée ; design dans `docs/design.md`). Depuis E5, totaux et liste viennent de `JourneeViewModel` (voir « Écrans (E5) ») ; `SaisieViewModel` ne garde que le formulaire et l'annulation rapide. Une prise saisie à 01:30 « aujourd'hui » compte pour la veille avec le pivot 5 h.

Formulaire : médicament (liste personnelle, vide au départ ; un libellé s'ajoute par un champ texte, refus du vide et des doublons sans tenir compte de la casse), quantité (clavier décimal, `Quantite.analyser`, un message français par motif de refus, neutre : « Saisie trop grande », jamais de conseil), unité (liste fermée : mg, comprimé, goutte, ml), jour « Aujourd'hui » ou « Hier » (jour civil) et heure (TimePicker 24 h, par défaut maintenant à la minute). Une heure après maintenant est refusée. « Ajouter » (anciennement « Relire » ; identifiants internes `relire` inchangés) ouvre un dialogue de relecture : « 2 mg de Testoline à 14:05 » (« hier à 01:30 » si hier). **Rien n'est écrit avant « Enregistrer »**, « Modifier » revient au formulaire avec les valeurs saisies.

**Règle des 10 secondes.** Après « Enregistrer », la prise est en base (CONFIRMEE) et un Snackbar « Prise enregistrée : … » propose « Annuler » pendant 10 s exactement. Le délai est piloté par le ViewModel (`delay(10 s)`), pas par `SnackbarDuration` : le Snackbar est affiché sans durée propre tant que l'état `annulationRapide` existe. À 9,9 s, « Annuler » passe la prise en ANNULEE (la ligne reste, exclue des totaux). À 10,0 s l'action disparaît et plus rien ne l'exécute. Un nouvel enregistrement remplace l'annulation rapide de la précédente.

**Annulation depuis la liste.** Toute prise confirmée de la liste a « Annuler cette prise », avec dialogue de confirmation. Elle fonctionne sans limite de temps.

**Écran « Données illisibles ».** La base est ouverte hors du fil principal (`Dispatchers.IO`), écran « Chargement » pendant ce temps. Si `BaseChiffree.ouvrir` lève `DonneesIllisiblesException`, l'écran explique sans jargon que la clé de chiffrement est perdue ou invalide, que l'appli ne peut pas lire les données et ne les efface pas d'elle-même. Seul bouton : « Fermer ». Aucun effacement automatique, aucun bouton qui efface (une réinitialisation explicite sera décidée plus tard). Toute autre erreur d'ouverture donne un écran générique, sans détail technique ; rien n'est journalisé.

Totaux du jour, historique, avertissement de premier lancement et écran Licences : voir « Écrans (E5) ». Pas d'export, pas d'INTERNET.

**Tests.** JVM : `SaisieViewModelTest` (faux journal en mémoire, horloge fixe 10/11/2026 Europe/Paris, temps virtuel), `OuvertureViewModelTest`, `SaisieIntegrationTest` (Robolectric, vraie base Room en mémoire non chiffrée : annulation persistée, prise annulée exclue de `totauxDuJour`), `UiGardeFousTest` (vocabulaire interdit dans `ui/` et `strings.xml` ; « trop » n'est toléré que dans « Saisie trop grande », exception écrite dans le test). Instrumentés : `SaisieUiTest` (Compose, faux journal : jamais la vraie base ni l'alias Keystore de production).

Dépendances ajoutées : `androidx.lifecycle` 2.9.4 (viewmodel-compose, runtime-compose ; version déjà résolue par Compose/Activity), `kotlinx-coroutines-test` 1.9.0 (tests, aligné sur coroutines-core), `ui-test-junit4` et `ui-test-manifest` (via le BOM Compose ; manifeste de test ajouté en debug seulement, `MergedManifestPermissionsTest` reste vert).

## Écrans (E5)

**Ce qui existe.** Navigation par un état d'écran (`Ecran`) et `BackHandler` : aucune bibliothèque de navigation. Après l'ouverture réussie de la base :
1. **Avertissement** (premier lancement) : « Journal personnel, sans garantie. Ce n'est pas un avis médical. Cette application ne donne aucun conseil de dose. » et un seul bouton « J'ai compris ». Tant qu'il n'est pas cliqué, aucun autre écran n'est accessible. L'écran « Données illisibles » d'E4 garde la priorité et n'a toujours aucun bouton d'effacement.
2. **Aujourd'hui** : pour la journée en cours (heure pivot), total des prises **confirmées** par médicament et par unité (« Testoline : 2 mg »), jamais additionnées entre médicaments ni entre unités ; puis la liste horodatée, annulées visibles et marquées « annulée ». Accès à « Nouvelle prise », Historique, Réglages, À propos.
3. **Historique** : journée par journée (précédente / suivante, jamais au-delà d'aujourd'hui), mêmes totaux et liste. Sur une prise confirmée : annuler (avec confirmation), réattribuer à la journée précédente, à la suivante (si elle n'est pas dans le futur) ou rétablir la journée calculée. Une prise réattribuée est signalée « réattribuée » ; les totaux des deux journées suivent. Réattribuer vers la journée que l'horodatage donne déjà efface simplement la réattribution.
4. **Réglages** : heure pivot de 0 h à 23 h (5 h par défaut). Le changement recalcule tout de suite les journées partout (rien n'est figé en base).
5. **À propos > Licences** : nom, version, notices tierces avec texte complet dépliable.

**Règles.** Journal personnel : aucun conseil, objectif, comparaison ni quantité « permise » ; l'appli ne calcule et n'affiche jamais ce que la personne pourrait prendre. `UiGardeFousTest` balaie toutes les chaînes de `strings.xml` et tous les littéraux de `ui/` (insensible à la casse et aux accents) ; seules exceptions, écrites dans le test : la phrase d'avertissement (« aucun conseil de dose ») et « Saisie trop grande ». `AucunLogAvecDonneesTest` échoue si `app/src/main` contient `android.util.Log`, `Log.`, `println(`, `printStackTrace(` ou `Timber`.

**Préférences.** `SharedPreferences` privées `dosejuste_reglages` (`shared_prefs/dosejuste_reglages.xml`), ouvertes par la seule classe `ReglagesPrefs` (`prefs/Reglages.kt`). Contenu : un entier (`pivot_heure`) et un booléen (`avertissement_accepte`), rien d'autre. Domaine de sauvegarde `sharedpref`, déjà exclu par `data_extraction_rules.xml` et `backup_rules.xml` ; `EmplacementsSauvegardeTest` le vérifie. Effacer les données de l'appli remet pivot 5 h et réaffiche l'avertissement.

**Base.** Schéma Room inchangé (version 1, pas de migration) : la journée se calcule, la réattribution utilisait déjà `journee_forcee`.

**Saisie corrigée.** « Ajouter » (ouvre la relecture) et « Retour » sont dans une barre d'actions fixe sous la zone défilante, au-dessus du clavier (`consumeWindowInsets` + `imePadding`) : les messages d'erreur ne les repoussent plus. Le champ libellé demande une majuscule initiale au clavier (`KeyboardCapitalization.Sentences`) ; aucune transformation de casse à l'affichage (la saisie « Testoline » reste « Testoline » en relecture, liste et total ; l'unicité sans tenir compte de la casse ne modifie pas le libellé stocké).

**Absent.** Export, sauvegarde, rappels, statistiques, comparaisons, thème réglable, réseau, permissions.

**Tests.** JVM : `JourneeViewModelTest` (totaux, pivot, navigation, réattribution), `ReglagesTest`, `PhrasePriseEtLicencesTest`, garde-fous. Instrumentés (`EcransUiTest`, `SaisieUiTest`, à lancer sur le Pixel) : avertissement bloquant, total du jour, pivot 01:30, historique, Licences (« ZETETIC LLC »), « Ajouter » (tag `relire`) visible avec erreur.

## Licences tierces

| Composant | Licence | Livré dans l'APK |
|---|---|---|
| AndroidX (Room, SQLite, Activity, Lifecycle, Compose, Test) | Apache 2.0 | oui (Test : non ; `ui-test-manifest` : debug seulement) |
| Kotlin, kotlinx.coroutines (dont coroutines-test, tests seulement) | Apache 2.0 | oui (coroutines-test : non) |
| SQLCipher for Android Community Edition 4.20.0 (Zetetic LLC, copyright 2008-2023) | BSD-style (Zetetic) | oui |
| LibTomCrypt (cryptographie embarquée dans `libsqlcipher.so`, vérifié dans l'AAR) | The LibTom license / Unlicense (domaine public) | oui |
| SQLite (inclus dans SQLCipher) | domaine public | oui |
| KSP | Apache 2.0 | non (compilation) |
| Robolectric | MIT | non (tests JVM) |
| JUnit 4 | EPL 1.0 | non (tests) |

Les notices sont **affichées dans l'appli** (À propos, Licences), textes complets dans `app/src/main/assets/licences/` : `sqlcipher.txt`, `libtomcrypt.txt`, `apache-2.0.txt` (AndroidX, Kotlin, kotlinx.coroutines). E5 n'ajoute aucune dépendance.

## Compiler

JDK 17 ou 21, et un Android SDK avec la plateforme `android-37.0` (ou les licences acceptées, pour que le plugin Gradle la télécharge).

```bash
./gradlew assembleDebug assembleDebugAndroidTest lint test
```

`assembleDebugAndroidTest` compile les tests instrumentés sans téléphone. L'APK debug est `app/build/outputs/apk/debug/app-debug.apk`. La CI GitHub Actions lance la même commande sur les pull requests, sans secret, et publie cet APK comme artefact `dose-juste-debug-apk` (conservé 14 jours). Ce n'est pas un APK release signé.

## Contrôle sur un Pixel 7 Pro

1. Installer l'APK debug. L'écran affiche « Dose Juste », la phrase qui refuse tout conseil médical ou de dose et « Nouvelle prise ». Aucune autorisation n'est demandée.
2. Réglages, Applications, Dose Juste, Autorisations : aucune autorisation n'est demandée ni accordée.
3. Mode avion, rouvrir l'appli : le même écran, sans erreur réseau.
4. Ajouter un médicament fictif (« Testoline »), saisir 2 mg, « Ajouter » (dialogue de relecture), « Enregistrer », puis « Annuler » dans les 10 s : la prise apparaît « annulée ».

La base chiffrée de production est désormais ouverte et créée au premier lancement : ne pas y entrer un vrai traitement avant la fin de la phase de test. Attention, `connectedDebugAndroidTest` désinstalle l'appli debug et efface cette base.
