# Dose Juste — direction artistique

## 1. Intention
Un carnet calme et fiable : on note une prise, on la relit et on la retrouve. La couleur de marque est un bleu-pétrole profond, avec un accent ocre discret. Les fonds sont légèrement teintés. Le rendu est sobre et très lisible, sans aucun code « pharma » : ni croix, ni caducée, ni symbole médical réglementé. Le rouge est réservé aux erreurs et n'est jamais décoratif.

## 2. Palette Material 3 (`lightColorScheme` / `darkColorScheme`)
| Token | Clair | Sombre |
|---|---|---|
| primary | #1B5E66 | #8CD0D8 |
| onPrimary | #FFFFFF | #00363C |
| primaryContainer | #CDE8EC | #164E55 |
| onPrimaryContainer | #0B3A40 | #CDE8EC |
| secondary | #4A6366 | #B1CBCE |
| onSecondary | #FFFFFF | #1C3437 |
| secondaryContainer | #D3E6E8 | #334B4E |
| onSecondaryContainer | #142B2E | #CDE7EA |
| tertiary | #7A5520 | #EDC08A |
| onTertiary | #FFFFFF | #452B00 |
| tertiaryContainer | #FCE0BC | #5F4110 |
| onTertiaryContainer | #2B1700 | #FFDDB3 |
| error | #B3261E | #FFB4AB |
| onError | #FFFFFF | #690005 |
| errorContainer | #F9DEDC | #93000A |
| onErrorContainer | #410E0B | #FFDAD6 |
| background | #F7FAFA | #0F1415 |
| onBackground | #181C1D | #DEE3E4 |
| surface | #F7FAFA | #0F1415 |
| onSurface | #181C1D | #DEE3E4 |
| surfaceVariant | #DAE4E5 | #3F494A |
| onSurfaceVariant | #3F494A | #BFC8C9 |
| surfaceDim | #D7DBDB | #0F1415 |
| surfaceBright | #F7FAFA | #353A3B |
| surfaceContainerLowest | #FFFFFF | #0A0F10 |
| surfaceContainerLow | #F1F4F4 | #171D1E |
| surfaceContainer | #EBEFEF | #1B2122 |
| surfaceContainerHigh | #E5E9E9 | #252B2C |
| surfaceContainerHighest | #E0E3E3 | #303637 |
| inverseSurface | #2D3132 | #DEE3E4 |
| inverseOnSurface | #EEF1F1 | #2B3132 |
| inversePrimary | #8CD0D8 | #1B5E66 |
| outline | #6F797A | #899393 |
| outlineVariant | #BFC8C9 | #3F494A |
| scrim | #000000 | #000000 |
| surfaceTint | #1B5E66 | #8CD0D8 |

Pas de couleur dynamique (`dynamicColor = false`) : l'identité reste stable. `outlineVariant` ne sert qu'aux séparateurs décoratifs.

## 3. Contrastes WCAG (luminance relative, (L1+0,05)/(L2+0,05))
| Paire (texte / fond) | Clair | Sombre |
|---|---|---|
| onPrimary / primary | 7.40 | 7.61 |
| onPrimaryContainer / primaryContainer | 9.65 | 7.25 |
| onSurface / surface | 16.37 | 14.32 |
| onSurfaceVariant / surface | 8.84 | 10.88 |
| onSurfaceVariant / surfaceContainer | 8.01 | 9.57 |
| onSurface / surfaceContainerLow | 15.53 | 13.17 |
| primary / surface (TextButton) | 7.05 | 10.72 |
| primary / surfaceContainer (TextButton en carte) | 6.38 | 9.42 |
| error / surface | 6.23 | 10.93 |
| error / surfaceContainer | 5.64 | 9.61 |
| onErrorContainer / errorContainer | 12.77 | 7.24 |
| onSecondaryContainer / secondaryContainer | 11.49 | 7.18 |
| onTertiaryContainer / tertiaryContainer | 13.49 | 7.22 |
| outline / surface (composant, seuil 3:1) | 4.26 | 5.88 |
| error / surfaceContainerLow (cartes de saisie) | ≈ 5.9 | ≈ 10.2 |
| onSurface / surfaceContainerHigh (dialogues) | ≈ 7.3 | ≈ 8.5 |
| primary / surfaceContainerHigh (TextButton de dialogue) | ≈ 6.0 | ≈ 8.6 |

Bordure des `FilterChip` non sélectionnés : `outline` (et non `outlineVariant`, ≈ 1,6:1) pour tenir 3:1. Puce sélectionnée : contour `primary` 2 dp (primary / surfaceContainer ≥ 6:1), car le fond secondaryContainer seul ne se distingue pas de la carte (≈ 1,1:1).

Toutes les paires de texte dépassent 4,5:1 (AA) et la plupart 7:1 (AAA). Les luminances de référence sont surface 0,9505 / 0,0066 et primary 0,0920 / 0,5565. Toute nouvelle paire texte/fond doit être calculée de la même façon avant usage.

## 4. Typographie
Uniquement des polices système : `FontFamily.Default` (Roboto) partout, et `FontFamily.Monospace` pour le texte intégral des licences. **Aucune police embarquée** : elle ajouterait une notice de licence et casserait le test des licences.

| Style | sp | interligne sp | poids | letterSpacing sp |
|---|---|---|---|---|
| displayLarge | 57 | 64 | W400 | -0.25 |
| displayMedium | 45 | 52 | W400 | 0 |
| displaySmall | 36 | 44 | W400 | 0 |
| headlineLarge | 32 | 40 | W500 | 0 |
| headlineMedium | 28 | 36 | W500 | 0 |
| headlineSmall | 24 | 32 | W500 | 0 |
| titleLarge | 22 | 28 | W500 | 0 |
| titleMedium | 18 | 26 | W500 | 0.1 |
| titleSmall | 15 | 22 | W500 | 0.1 |
| bodyLarge | 17 | 26 | W400 | 0.15 |
| bodyMedium | 16 | 24 | W400 | 0.2 |
| bodySmall | 14 | 20 | W400 | 0.3 |
| labelLarge | 16 | 22 | W500 | 0.1 |
| labelMedium | 14 | 20 | W500 | 0.4 |
| labelSmall | 12 | 16 | W500 | 0.5 |

Usage : le titre de la top bar en titleLarge ; les titres de section (« Totaux », etc.) en titleMedium ; les lignes de prise et les totaux en bodyLarge avec `fontFeatureSettings = "tnum"` (chiffres tabulaires, les heures s'alignent) ; les textes d'aide en bodyMedium + onSurfaceVariant ; les badges en labelMedium ; les licences en Monospace 13 sp / 19 sp. Ne jamais forcer une taille en `dp` : respecter l'échelle de police du système.

## 5. Formes (`Shapes`)
extraSmall 6 dp · small 10 dp · medium 14 dp · large 20 dp · extraLarge 28 dp.
- Badges et champs de texte : extraSmall.
- Chips, FilterChips et boutons : small. Les boutons gardent leur hauteur ≥ 48 dp ; la forme « pilule » M3 par défaut reste acceptable.
- Cartes (totaux, liste, réglages, notices) : medium.
- Bandeau d'avertissement et barre fixe de saisie (coins hauts seulement) : large.
- Dialogues (Relecture) : extraLarge.

## 6. Espacements
Échelle : 4 · 8 · 12 · 16 · 24 · 32 dp.
- Marges d'écran : 16 dp horizontales et 16 dp en haut sous la top bar. Contenu limité à `widthIn(max = 600.dp)` et centré sur tablette.
- Entre sections : 24 dp. Entre éléments d'une même section : 8 dp. Padding interne des cartes : 16 dp, et 12 dp entre les lignes de la liste.
- Toutes les cibles tactiles font ≥ 48 × 48 dp (`heightIn(min = 48.dp)`, comme aujourd'hui). Les radios et les chips gardent leur zone minimale.
- Ajouter `navigationBarsPadding()`/`imePadding()` sur la barre fixe de saisie.

## 7. Composants par écran
**Commun.** `Scaffold` + `TopAppBar` (Small) avec `containerColor = surface` et `scrolledContainerColor = surfaceContainer`. Les écrans secondaires (Historique, Réglages, À propos, Licences) placent en `navigationIcon` un `TextButton` (icône flèche + texte « Retour ») qui garde `testTag(Tags.RETOUR_ECRAN)` : le texte « Retour » reste visible. Le titre de l'écran n'est pas dans la barre (hauteur fixe, rognage à 200 %) mais en tête du contenu défilant, en headlineSmall + `heading()`. Icônes : vector drawables locaux `res/drawable/ic_*.xml` (24 dp, `tint = LocalContentColor`). N'ajouter `material-icons-*` que si sa notice est déjà couverte par l'écran Licences. Les icônes à côté d'un texte sont décoratives (`contentDescription = null`).

**Avant de commencer.** Titre en headlineSmall et texte en bodyLarge, dans une carte surfaceContainerLow. Bouton `Button` pleine largeur « J'ai compris » en bas.

**Aujourd'hui.**
1. En-tête : nom de l'app en headlineSmall (primary).
2. Bandeau d'avertissement : `Surface(color = secondaryContainer, shape = large)`, icône « i » 20 dp, et la phrase existante en bodyMedium (onSecondaryContainer). Pas d'action, pas de fermeture.
3. « Nouvelle prise » : `Button` pleine largeur, 56 dp, icône « + ». C'est l'unique occurrence de ce texte : pas de FAB en doublon.
4. Une rangée de 3 `FilledTonalButton` (`weight(1f)`) : Historique, Réglages, À propos, avec une icône au-dessus ou à gauche. Les textes restent visibles et la navigation ne change pas.
5. Totaux : `Card` (surfaceContainer), titre titleMedium, une ligne par médicament (« Testoline : 2 mg »), tnum.
6. Liste : `Card` (surfaceContainer). Chaque ligne a l'heure et la quantité en bodyLarge tnum (« 08:00 — 2 mg de Testoline »), séparées par un `HorizontalDivider(outlineVariant)`. « Annuler cette prise » est un `TextButton` (primary, pas error) aligné à droite.
7. Badge « annulée » : contour 1 dp outline, texte labelMedium onSurfaceVariant, sans fond rouge. La ligne de la prise passe en onSurfaceVariant avec `TextDecoration.LineThrough` via le style uniquement : le nœud `Text` garde exactement le même texte. Badge « réattribuée » : fond tertiaryContainer, texte onTertiaryContainer. Les badges sont des `Text` séparés de la ligne.
8. État vide : icône carnet 48 dp (outline), puis le texte existant en bodyLarge onSurfaceVariant, centré, avec 32 dp de marge verticale.

**Saisie.** Chaque groupe (médicament, quantité, unité, jour, heure) est une carte surfaceContainerLow avec un titre titleSmall. Les radios en lignes `selectable` font 48 dp. Le champ libellé est un `OutlinedTextField` suivi d'un `OutlinedButton` « Ajouter à ma liste ». Les chips Aujourd'hui/Hier sont des `FilterChip`. L'heure est un `OutlinedButton` avec icône horloge et la valeur en tnum. Barre fixe : `Surface(color = surfaceContainer, tonalElevation 3 dp)`, avec « Retour » (`OutlinedButton`) à gauche et « Ajouter » (`Button`) à droite. Les tags RELIRE et RETOUR ne changent pas.

**Relecture.** `AlertDialog` (surfaceContainerHigh, extraLarge) : le récapitulatif en bodyLarge tnum, puis « Modifier » (`TextButton`) et « Enregistrer » (`Button`).

**Historique.** Sous la top bar, la date en titleMedium, centrée. « Journée précédente » / « Journée suivante » en `OutlinedButton` (`weight(1f)`). Totaux et liste comme sur Aujourd'hui ; la réattribution est un `TextButton`.

**Réglages.** Carte titrée par le texte existant « Heure pivot » : texte d'aide bodyMedium, puis `FlowRow` de 24 `FilterChip` (0 à 23 h) avec 8 dp d'espacement. Le chip sélectionné est en secondaryContainer.

**À propos / Licences.** La version tient dans une carte et le bouton « Licences » est un `OutlinedButton`. Chaque notice est une carte surfaceContainerLow ; son texte intégral se déplie (icône chevron) en Monospace et défile horizontalement si besoin.

**Chargement / erreur.** `CircularProgressIndicator` (primary) centré. Erreur : carte errorContainer, icône « ! » et texte onErrorContainer. C'est le seul usage du rouge.

## 8. Icône de lanceur adaptative (viewport 108 × 108, zone sûre : cercle r 33 centré en 54,54)
Symbole : une gélule bicolore inclinée à 45°, posée au-dessus d'un trait de mesure ocre. Elle évoque « la prise notée, juste ». Pas de croix, de caducée, ni de texte.
- **Fond** (`ic_launcher_background`, remplace #1B4332) : #1B5E66 uni.
- **Gélule** : dessinée horizontalement (x 30→78, y 40→60, rayon 10), dans `<group android:rotation="-45" android:pivotX="54" android:pivotY="50">` (l'extrémité droite monte).
  - Moitié gauche, #FFFFFF : `M54,40 L40,40 A10,10 0 0 0 40,60 L54,60 Z`
  - Moitié droite, #8CD0D8 (4,27:1 sur le fond) : `M54,40 L68,40 A10,10 0 0 1 68,60 L54,60 Z`
  - Une fois tournée, elle s'étend de (36,9 ; 69,9) à (71,1 ; 30,1). Le point le plus éloigné du centre est à 27,1 dp.
- **Trait de mesure**, hors groupe, #EDC08A : rectangle arrondi x 42→66, y 76→80, rayon 2 :
  `M44,76 H64 A2,2 0 0 1 66,78 A2,2 0 0 1 64,80 H44 A2,2 0 0 1 42,78 A2,2 0 0 1 44,76 Z` (coin le plus éloigné : 28,6 dp ; 6 dp d'écart avec la gélule).
- **Monochrome** (`ic_launcher_monochrome`) : mêmes géométries, une seule couleur #FFFFFF (le système la teinte). La gélule est une silhouette pleine sans séparation : `M40,40 H68 A10,10 0 0 1 68,60 H40 A10,10 0 0 1 40,40 Z` (même groupe tourné), plus le trait de mesure.

## 9. Fenêtre et barres système
- `window_background` : #F7FAFA, et dans `values-night/colors.xml` #0F1415 (égal à background). Ça évite le flash au démarrage. Le parent du thème XML devient `android:Theme.Material.Light.NoActionBar` en clair et `android:Theme.Material.NoActionBar` dans `values-night/themes.xml`.
- Appeler `enableEdgeToEdge()` dans `onCreate` : barres d'état et de navigation transparentes, icônes sombres en clair et claires en sombre (gérées par `SystemBarStyle.auto`). Le contenu respecte les insets via `Scaffold`/`WindowInsets.safeDrawing`.

## 10. Garde-fous (contenu)
- L'app est un journal. Aucune phrase ne propose, ne calcule, ne recommande ni ne borne une quantité. Les seuls exemples sont fictifs (« Testoline »), jamais de médicament réel.
- Mots interdits dans **tout** `app/src/main/java/fr/dosejuste/app/ui/` (code **et** commentaires, sans tenir compte de la casse) : `restant`, `maximum`, `limite`, `peut encore`, `recommandé`, `recommande`, `dose conseillée`, `objectif`, `dépass`, et le mot isolé `trop`.
- Interdits en plus dans les littéraux Kotlin et `strings.xml` : `max` (mot entier), `encore`, `conseillé`, `reste à`, `autorisé`.
- Nommage technique : `widthIn(max = …)` est permis (paramètre, pas un littéral) ; préférer des noms neutres (`largeurContenu`, etc.). Relancer les tests de vocabulaire après chaque changement d'interface.
- Textes : aucun texte visible nouveau hors `strings.xml` ; la refonte réutilise les textes existants (nom de l'app et phrase d'avertissement restent visibles partout où ils l'étaient).

## 11. Captures
`CapturesDesignTest` (tests JVM Robolectric, rendu natif) écrit les PNG dans `app/build/captures-design/` : `./gradlew testDebugUnitTest --tests fr.dosejuste.app.ui.CapturesDesignTest`. Aucune comparaison d'image.
