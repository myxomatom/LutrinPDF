# 0029 — Options de lecture, feuille « Affichage » et langue de l'application

- **Statut** : accepté
- **Date** : 2026-11-27
- **Décideurs** : choix fonctionnels de la demande du lot (ajustement « Largeur » ou « Page
  entière » dans V6, plein écran et verrouillage d'orientation dans V6, écran allumé dans E3 ou V6,
  langue du système, français ou anglais) ; détails techniques par l'agent de codage
- **Références** : `SPEC.md` F04, F14, F17, F19 ; `AGENTS.md` §3, §9, §11 ; `ROADMAP.md` M5 (lot
  4) ; ADR 0011 (mode page par page, feuille « Affichage »), 0026 (le bouton « pleine largeur » est
  remplacé par « Imprimer »), 0027 (menu ⋮), 0028 (couleurs du document dans V6, thème dans E3) ;
  maquettes `V6-Options`, `E3-Parametres`

## Contexte

Le lot 4 de M5 termine la feuille « Affichage » (V6) et les paramètres de lecture (E3) : ce que le
zoom « 100 % » représente (F04), le plein écran (F14), l'orientation (F17), l'écran allumé et la
langue de l'application (F19).

Ce qui existait :

- le zoom 1 valait « pleine largeur » en défilement continu et « page entière » en page par page
  (ADR 0011), sans réglage ; depuis l'ADR 0026 le bouton qui ramenait à la largeur n'existe plus ;
- le plein écran est un état local du lecteur (`controlsVisible`), basculé par un toucher sur la
  page, qui masque les barres du système et celles du lecteur (`ImmersiveMode`) ;
- l'activité suit la rotation du téléphone, sans réglage ;
- la langue suit celle du système : les chaînes sont déjà en français et en anglais.

Les deux maquettes ne se recoupent pas : V6 montre « Ajustement », « Plein écran » et « Verrouiller
l'orientation » ; E3 montre « Garder l'écran allumé » et « Verrouiller l'orientation » dans
« Lecture », et n'a pas de choix de langue.

## Options envisagées

### Valeur par défaut de l'ajustement
- « Largeur » partout : change le mode page par page, qui montrait la page entière.
- « Page entière » partout : change le défilement continu, le mode par défaut.
- **Aucun choix tant que l'utilisateur n'en a pas fait un** (retenu) : l'ajustement suit alors le
  mode de défilement, comme avant (largeur en continu, page entière page par page). Même schéma que
  les couleurs du document (ADR 0028) : une clé absente se distingue d'un choix explicite.

### Ce que « Page entière » veut dire en défilement continu
- Largeur propre à chaque page : le défilement saute d'une page à l'autre et le zoom ne veut plus
  rien dire d'uniforme.
- **Une largeur unique pour toutes les pages, calculée sur la plus haute** (rapport hauteur sur
  largeur maximal du document) et bornée par la largeur de l'écran (retenu) : toute page tient
  entière entre deux interlignes, rien n'est jamais coupé, le défilement reste régulier. Dans un
  document aux pages de formats très différents, une page très haute rétrécit toutes les autres :
  accepté, c'est ce que l'utilisateur demande.

### Ce que « Largeur » veut dire en page par page
- Interdire le mode : refusé, la demande dit que l'ajustement fonctionne dans les deux modes.
- **La page remplit la largeur (moins la marge) ; si elle est plus haute que l'écran, la zone de
  la page est plus haute que l'écran et se fait défiler verticalement** (retenu), le balayage
  horizontal changeant toujours de page tant que le zoom vaut 1. Le zoom, le double toucher et
  les gestes gardent le même sens : tout se compte à partir de ce « 100 % ».

### Emplacement des réglages
- Tout dans E3 : le plein écran et l'ajustement se règlent en lisant, comme les couleurs
  (ADR 0028).
- **V6 : ajustement, plein écran, verrouillage de l'orientation ; E3 : écran allumé et langue**
  (retenu). Le verrouillage d'orientation de E3 (maquette) n'est pas répété : un seul endroit par
  réglage, celui de la demande (V6).

### Plein écran
- Un réglage mémorisé : refusé, le plein écran est un état de lecture qui se défait d'un toucher.
- **Un interrupteur qui reflète l'état du lecteur et le pilote** (retenu), sans stockage : rouvrir
  un document le montre avec ses barres, comme avant. La feuille reste ouverte quand on l'active.

### Orientation
- Imposer portrait ou paysage : refusé, l'utilisateur verrouille ce qu'il tient.
- **`SCREEN_ORIENTATION_LOCKED` : verrouille l'orientation actuelle** (retenu), demandée à
  l'activité par un effet du lecteur (`OrientationLock`), jamais par le ViewModel. L'effet rend la
  demande précédente de l'activité quand le lecteur quitte l'écran ou que le réglage est coupé :
  la bibliothèque et les paramètres tournent comme avant. Un réglage mémorisé s'applique à chaque
  ouverture d'un document, dans l'orientation où le téléphone est alors tenu.

### Écran allumé
- Indicateur de fenêtre `FLAG_KEEP_SCREEN_ON` posé sur l'activité : à défaire à la main.
- **`View.keepScreenOn` sur la vue du lecteur** (retenu, un `DisposableEffect`) : la fenêtre ne le
  suit que tant que la vue est affichée, et il se défait tout seul à la sortie du lecteur.

### Langue de l'application
- Langue mémorisée dans DataStore et appliquée par `attachBaseContext` : fonctionne sur toutes les
  versions, mais impose de lire le réglage avant la première composition (lecture bloquante ou
  fichier à part), un second endroit où la langue est gardée et un risque de désaccord avec les
  réglages du système.
- `AppCompatDelegate.setApplicationLocales` : demande la dépendance `androidx.appcompat` et une
  activité `AppCompatActivity`, alors que l'application est en Compose sur `ComponentActivity`.
- **Langue par application du système (`LocaleManager`, Android 13 et plus)** (retenu) : aucune
  dépendance, aucune permission, rien de stocké par l'application ; le système garde le choix,
  recrée les écrans dans la langue et l'affiche dans ses propres réglages (« Langues de
  l'application »).
- **Avant Android 13, l'application suit la langue du système** et n'offre pas de choix
  (limite assumée) : le français et l'anglais y sont déjà couverts par la langue du téléphone. La
  ligne n'est pas montrée.

## Décision

1. **Domaine** : `FitMode` (`WIDTH`, `PAGE`) avec `FitMode.defaultFor(scrollMode)` ;
   `AppSettings.chosenFitMode` (`null` tant que rien n'est choisi), `AppSettings.fitMode(scrollMode)` ;
   `keepScreenOn` et `lockOrientation` (faux par défaut) ; `AppLanguage` (`SYSTEM`, `FRENCH`,
   `ENGLISH`, avec son tag BCP 47 et `fromLanguageTags`) ; interface `AppLanguageController`
   (`isSupported`, `current`, `set`) ; cas d'usage `SetFitModeUseCase`, `SetKeepScreenOnUseCase`,
   `SetLockOrientationUseCase`, `GetAppLanguageUseCase` (nul quand le système ne laisse pas choisir),
   `SetAppLanguageUseCase`.
2. **Stockage** (DataStore, `core:data`) : clés `fit_mode` (`width`, `page`, **absente tant que
   l'utilisateur n'a rien choisi**), `keep_screen_on`, `lock_orientation`. Une valeur inconnue se
   lit comme « aucun choix ». La langue n'est pas stockée par l'application :
   `LocaleManagerAppLanguageController` la lit et l'écrit dans le système, hors du thread principal,
   et n'agit pas avant Android 13.
3. **Géométrie** (fonctions pures, `FitGeometry.kt`) : `continuousFitWidth` (largeur des pages à
   zoom 1 en continu), `pagedFitWidth` (en page par page), `tallestAspect` ; `pagedPageLayout` prend
   l'ajustement et rend `scrollsVertically`. Zoom 1 est toujours « l'ajustement » : l'état du zoom,
   le double toucher et la réinitialisation au changement de page sont inchangés. Le zoom est remis
   à 1 quand le mode de défilement ou l'ajustement change : le choix s'applique tout de suite, sert
   de zoom de départ et de cible du double toucher.
4. **Feuille V6** : « Défilement », « Ajustement », « Couleurs du document », « Plein écran »,
   « Verrouiller l'orientation » dans l'ordre de la maquette ; les deux interrupteurs sont des lignes
   de 48 dp entières. Les boutons segmentés deviennent une liste de boutons radio de 48 dp quand la
   police est à 150 % ou plus (comme le thème dans E3), ce qui corrige la coupure à 200 %.
5. **Lecteur** : `ReaderUiState.Ready` porte `chosenFitMode` (avec `fitMode` résolu),
   `keepScreenOn` et `lockOrientation`, suivis en direct ; `ReaderViewModel.onFitModeChange` et
   `onLockOrientationChange` ; `OrientationLock` et `KeepScreenOn` (`ReaderWindow.kt`) sont posés
   par `ReaderContent`, donc seulement tant qu'un document est affiché.
6. **Paramètres (E3)** : « Langue de l'application » (Système / Français / English) sous le thème,
   seulement quand le système la laisse choisir ; « Garder l'écran allumé » en tête de « Lecture ».
   Les noms des langues sont écrits dans leur langue, quelle que soit celle de l'interface.
   `SettingChoice` factorise le choix du thème et celui de la langue.
7. **Manifeste** : `android:localeConfig` et `res/xml/locales_config.xml` (`en`, `fr`) : le système
   liste les deux langues ; un test vérifie qu'elles sont celles de `AppLanguage` et que le
   manifeste le référence.

## Écarts avec les maquettes

- **E3** : pas de « Verrouiller l'orientation » (il est dans V6, comme le demande le lot) ;
  « Mode de défilement » n'est pas dans E3 non plus (il est dans V6, ADR 0011) ; « Langue de
  l'application » est ajoutée, la maquette n'en a pas.
- **E3** : « Garder l'écran allumé » est la première ligne de « Lecture », au-dessus de l'aperçu de
  la page et de « Ignorer les restrictions du document ».
- **V6** : les deux interrupteurs ont l'aspect Material 3 du thème (pastille et couleur primaire),
  pas les pastilles de la maquette.
- **V6** : sous une police à 150 % ou plus, les boutons segmentés sont des boutons radio, un par
  ligne (la maquette ne prévoit pas les grandes polices).

## Conséquences

- Positives : aucun changement pour qui ne touche à rien (ajustement hérité du mode, orientation
  libre, écran qui s'éteint, langue du système) ; aucune dépendance ni permission ; la langue
  n'est gardée qu'à un endroit, le système ; le plein écran et l'orientation se défont tout seuls
  à la sortie du lecteur.
- Négatives / dette acceptée :
  - avant Android 13, pas de choix de langue (la langue du téléphone s'applique) ;
  - « Page entière » en défilement continu est calé sur la page la plus haute du document ;
  - choisir un ajustement oublie la différence entre les modes : « Largeur » choisi en page par
    page vaut aussi en continu, jusqu'à un nouveau choix ;
  - le changement de langue recrée l'activité (le lecteur reprend sa page et son zoom, comme après une
    rotation) ;
  - l'orientation verrouillée s'applique dans l'orientation du moment où le lecteur s'affiche, pas
    dans celle où elle fut activée la première fois.
- Tests : domaine (ajustement par défaut et choisi, langues, cas d'usage), DataStore (clés et valeurs
  stables, valeur inconnue, relecture), contrôleur de langue (Robolectric : lecture, écriture,
  retour au système, version trop ancienne), géométrie (largeurs de chaque ajustement, zone de la
  page, point touché dans une page défilée), vues (rendu en largeur et en page entière, balayage,
  double toucher), feuille V6 (choix, interrupteurs, grande police), fenêtre (orientation et écran
  allumé posés et rendus), E3 (lignes, FR, grande police), `locales_config.xml`, captures
  Roborazzi (V6 et E3 : clair, sombre, 200 %, français), tests instrumentés (orientation verrouillée
  sur l'activité, langue par application sur API 33 et plus).
- À essayer sur appareil : voir la liste de M5 dans `docs/reprise.md`.
