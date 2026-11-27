# 0028 — Thème de l'application, couleurs du document et `RenderOptions`

- **Statut** : accepté
- **Date** : 2026-11-27
- **Décideurs** : choix fonctionnels par le mainteneur (thème « Clair / Sombre / Système » dans E3,
  « Système » par défaut ; couleurs du document « Normal / Nuit / Sépia » choisies dans la feuille
  « Affichage », « Normal » par défaut même en thème sombre, jamais de mode nuit tout seul ; réglage
  facultatif « Suivre le thème sombre » désactivé par défaut ; choix global ; impression, partage et
  vignettes en couleurs d'origine ; `RenderOptions` prévu pour la rotation F27) ; détails techniques
  par l'agent de codage
- **Références** : `SPEC.md` F13, F27 ; `AGENTS.md` §3, §8, §11 ; `ROADMAP.md` M5 (lot 3) ; ADR
  0008 (rendu et caches), 0010 (couleurs dynamiques), 0011 (feuille « Affichage »), 0018 (design,
  aperçu du curseur), 0026 (impression), 0027 (menu ⋮), 0031 (page illisible, papier de nuit) ;
  maquettes `E3-Parametres`, `V6-Options`, `E2-Lecteur-Nuit` ; `docs/design/design-tokens.json`
  (`color.reader`)

## Contexte

F13 demande trois choses distinctes : le thème de l'interface (clair, sombre, système), les
couleurs dynamiques (déjà faites, ADR 0010) et les couleurs du document lui-même (mode nuit, sépia).
Jusqu'ici l'interface suivait le système sans réglage, et le moteur savait déjà inverser la
luminosité d'un rendu (`nightMode`), sans que rien ne l'active ; le papier du mode nuit existait pour
la page illisible (ADR 0031).

Il fallait décider où se choisissent les couleurs du document, comment elles se combinent au thème,
comment elles descendent jusqu'au moteur sans rendre une page avec de mauvaises couleurs ni garder
en mémoire les bitmaps des anciennes, et comment dessiner la nuit et le sépia.

Les maquettes ne s'accordent pas entre elles : E3 montre un choix « Rendu du document » (Normal /
Nuit / Sépia) dans les paramètres, V6 le même choix « Couleurs du document » dans la feuille
« Affichage ».

## Options envisagées

### Où choisir les couleurs du document
- Dans E3 et dans V6 (les deux maquettes) : deux endroits pour un même réglage.
- **Dans V6 seulement** (retenu par le mainteneur) : le choix se fait en lisant, en voyant la page
  changer sous la feuille. E3 garde le thème et, au plus, un lien avec le thème sombre.

### Lien avec le thème sombre
- Aucun : le document ne change jamais avec le thème.
- Nuit automatique en thème sombre : refusé, le mode nuit ne doit jamais s'activer seul.
- **Réglage facultatif « Suivre le thème sombre », désactivé par défaut** (retenu) : s'il est
  activé, un document sans couleurs choisies passe en nuit quand l'application est sombre. Un choix
  explicite dans V6 (même « Normal ») l'emporte toujours. Activer le réglage oublie le choix déjà
  fait, sinon il n'aurait aucun effet visible ; le désactiver le garde.

### Comment porter les couleurs jusqu'au rendu
- Un état du `PageRenderer` changé par l'écran : les compositions doivent quand même se relancer.
- Un `ColorFilter` de Compose au moment du dessin : aucun nouveau rendu, mais deux mécanismes (la
  nuit est déjà dans le moteur), le papier, l'aperçu et la page illisible à filtrer chacun, et des
  bitmaps qui ne disent pas ce qu'ils montrent.
- **Un objet `RenderOptions` passé à chaque rendu** (retenu, demandé par le mainteneur) : il descend
  de l'écran au moteur pour les pages, les détails et les aperçus ; chaque cache s'en sert de clé.

### Technique de la nuit
- Inversion simple des couleurs : les photos deviennent des négatifs.
- **Inversion de la luminosité en gardant les teintes** (`Pixmap.invertLuminance` de MuPDF, déjà
  utilisée), **puis projection sur le papier et l'encre de nuit** (`Pixmap.tint`) : le noir va au
  papier `#16181C`, le blanc à l'encre `#E1E2E8`. Les images restent reconnaissables (un ciel bleu
  reste bleu), le contraste est adouci (≈ 14:1 au lieu de 21:1) et le papier rendu rejoint celui de
  la maquette E2-Lecteur-Nuit. C'est le plus simple qui reste lisible.
- Ne pas inverser les images (détection des images dans la page) : plus juste, mais il faut un
  périphérique de dessin à soi ; écarté.

### Technique du sépia
- Une matrice de couleur de Compose : voir plus haut.
- **Un filtre de couleur sur les pixels rendus** (retenu) : `Pixmap.tint` de MuPDF projette chaque
  canal entre l'encre `#3D2E14` et le papier `#F4ECD8` ; une passe sur les pixels, dans le même
  rendu que la page, pas un second rendu.

## Décision

1. **Domaine** (`core:domain`) : `AppTheme` (`LIGHT`, `DARK`, `SYSTEM`, défaut `SYSTEM`) ;
   `DocumentColorMode` (`NORMAL`, `NIGHT`, `SEPIA`) avec son papier et son encre, tirés des jetons
   `color.reader` (`pageNormal`, `pageNight`, `textNight` ajouté, `pageSepia`, `textSepia`) et
   vérifiés par un test qui lit `design-tokens.json` et le contraste AA ; `DocumentColors` (choix
   explicite ou `null`, `followDarkTheme`) avec `modeFor(darkTheme)` ; `RenderOptions(colorMode)`.
   Cas d'usage `SetAppThemeUseCase`, `SetDocumentColorModeUseCase`, `SetFollowDarkThemeUseCase`.
2. **Stockage** (DataStore, `core:data`) : clés `app_theme` (`light`, `dark`, `system`),
   `document_colors` (`normal`, `night`, `sepia`, **absente tant que l'utilisateur n'a rien
   choisi**, ce qui distingue « Normal » par défaut de « Normal » choisi) et `follow_dark_theme`.
   Une valeur inconnue se lit comme la valeur par défaut.
3. **Thème de l'application** : `SettingsTheme` (`app`) calcule `appTheme.isDark(système)`, le donne
   à `LutrinTheme`, qui l'expose aux écrans par `LocalDarkTheme` (`core:ui`). Les icônes des barres
   système suivent le thème de l'application (`enableEdgeToEdge` avec `SystemBarStyle.auto`), pas
   seulement celui du système. Les couleurs dynamiques gardent leur réglage.
4. **Couleurs du document** : `ReaderUiState.Ready.documentColors` suit les paramètres en direct ;
   l'écran les résout avec `LocalDarkTheme` (`renderOptions(darkTheme)`), car le ViewModel ne
   connaît pas le thème. Un seul choix pour tous les documents.
5. **`RenderOptions` jusqu'au moteur** : `PdfDocument.renderPage(…, options)` remplace le booléen
   `nightMode` ; `PageRenderer.renderPage`, `renderPart` et `renderPreview` prennent les options ;
   `RenderKey` les contient. **La première demande avec d'autres options vide les deux caches** (pages
   et aperçus) : les bitmaps des anciennes couleurs ne seraient plus affichés, ils sont rendus au
   ramasse-miettes tout de suite au lieu d'occuper la mémoire jusqu'à leur éviction. Un rendu en
   cours au moment du changement est rendu à son appelant mais n'entre pas dans le cache. Côté
   Compose, les options font partie des clés de `produceState` et `LaunchedEffect` : une page n'est
   rendue à nouveau que si elles changent (une donnée `data class`, égale d'une recomposition à
   l'autre), jamais à chaque recomposition ; le détail d'une page est oublié avec les anciennes options.
6. **Écran** : papier de la page avant son rendu et sous une page illisible aux couleurs du mode ;
   message de page illisible en couleurs sombres sur le papier de nuit (ADR 0031) ; carte d'aperçu du
   curseur au papier du mode ; en nuit, fond de lecture `reader.backgroundNight` (`#0B0D10`) quel que
   soit le thème, et numéro de page du plein écran dans la couleur du schéma sombre pour rester
   lisible dessus.
7. **Feuille « Affichage » (V6)** : section « Couleurs du document » sous « Défilement », trois
   échantillons de papier de 72 dp (Normal, Nuit, Sépia), chacun dans ses couleurs, bord épais de la
   couleur primaire pour le choix courant (lisible sans la couleur), groupe de boutons radio pour
   TalkBack. La feuille défile en grande police. Choisir laisse la feuille ouverte : la page change
   dessous.
8. **Paramètres (E3)** : « Thème de l'application » (Clair / Sombre / Système) en tête de
   « Apparence », puis « Couleurs dynamiques », puis l'interrupteur « Suivre le thème sombre ».
9. **Hors du lecteur, couleurs d'origine** (`RenderOptions.ORIGINAL`) : impression d'un document
   protégé (ADR 0026), partage (le fichier tel quel, ADR 0027), vignettes de la bibliothèque.

## Écarts avec les maquettes

- **E3** : pas de choix « Rendu du document » ; à sa place, l'interrupteur « Suivre le thème
  sombre ». Les couleurs se choisissent dans V6 (décision du mainteneur).
- **E3** : la maquette n'a pas « Couleurs dynamiques » (ADR 0010) ; il reste sous le thème.
- **V6** : seules les sections « Défilement » et « Couleurs du document » existent ; « Ajustement »,
  « Plein écran » et « Verrouiller l'orientation » viennent au lot 4.
- **V6** : le libellé de l'échantillon choisi est en graisse moyenne et son bord suit la couleur
  primaire du thème (dynamique ou non), au lieu du bleu fixe de la maquette.
- **Jeton ajouté** : `color.reader.textNight` (`#E1E2E8`, la couleur du texte de l'échantillon
  « Nuit » de V6).

## Conséquences

- Positives : un seul chemin pour les options de rendu, que la rotation F27 (lot 5) rejoindra
  (`RenderOptions.rotation`) ; aucun bitmap d'anciennes couleurs gardé en mémoire ; aucun rendu en
  plus en couleurs d'origine ; le mode nuit ne démarre jamais seul.
- Négatives / dette acceptée :
  - en nuit, une photo a sa luminosité inversée (teintes gardées) : lisible, mais pas fidèle ;
  - changer de couleurs redessine les pages visibles (comme un changement de zoom) ;
  - le blanc inversé vaut (4, 4, 4) avant projection : le papier rendu de nuit est à quelques unités
    du papier affiché avant le rendu (invisible à l'œil, tolérance dans les tests) ;
  - au démarrage à froid, la fenêtre (`windowBackground`) et la première image suivent le thème du
    système, avant la lecture des paramètres ; un thème forcé apparaît une fraction de seconde plus
    tard (comme les couleurs dynamiques depuis l'ADR 0010) ;
  - un « Normal » choisi explicitement désactive de fait « Suivre le thème sombre » jusqu'à ce que
    l'interrupteur soit réactivé (le résumé de l'interrupteur le dit).
- Tests : domaine (résolution des couleurs, jetons et contraste, cas d'usage), DataStore (clés et
  valeurs stables, valeurs inconnues), moteur (JVM sous Linux et instrumenté : papier et encre de
  nuit et de sépia), `PageRenderer` (options transmises, caches vidés, rendu tardif non mis en cache),
  ViewModels, écrans Robolectric et captures Roborazzi (E3, V6, lecteur en Normal, Nuit et Sépia,
  clair et sombre, 200 %, français et anglais).
- À essayer sur appareil : voir la liste de M5 dans `docs/reprise.md`.
