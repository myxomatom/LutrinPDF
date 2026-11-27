# 0032 — Rotation de l'affichage des pages

- **Statut** : accepté
- **Date** : 2026-11-27
- **Décideurs** : choix fonctionnels par le mainteneur (affichage seulement, document entier, icône
  « Tourner » dans la barre du haut avec repli dans ⋮, +90° par toucher en cycle, rotation mémorisée
  par document, impression, partage et vignettes sans rotation, rotation portée par `RenderOptions`) ;
  détails techniques par l'agent de codage
- **Références** : `SPEC.md` F27, F12, §5 (`ReadingPosition`) ; `AGENTS.md` §3, §11 ; `ROADMAP.md`
  M5 (lot 5) ; ADR 0008 (rendu et caches), 0011 (modes de défilement), 0015 et 0017 (schéma Room),
  0022 à 0024 (liens, recherche, sélection), 0026 (impression), 0027 (menu ⋮), 0028
  (`RenderOptions`), 0029 (ajustement, `FitGeometry.kt`)

## Contexte

F27 demande de tourner l'affichage par quarts de tour, pour lire une page numérisée à l'envers ou un
tableau en paysage, sans jamais modifier le fichier, la rotation étant retenue par document comme
la dernière page.

Tourner l'affichage touche tout ce qui place quelque chose sur une page : le rendu (pages, tuiles de
détail, aperçu du curseur), l'ajustement Largeur / Page entière (une page tournée d'un quart échange
largeur et hauteur), le zoom, et surtout les données du document exprimées en coordonnées de page :
liens, résultats de recherche, sélection et ses poignées. Une erreur d'un seul côté se voit tout de
suite (un lien qui ouvre le voisin, un surlignage à côté du mot).

## Options envisagées

### Où tourner l'image
- Tourner la vue Compose (`graphicsLayer { rotationZ }`) : rien à changer au moteur, mais la mise
  en page, l'ajustement, le défilement et les gestes voient une page droite sous une image tournée ;
  tout serait à corriger à côté.
- **Le moteur dessine la page tournée** (retenu, demandé par le mainteneur) : la rotation est dans
  `RenderOptions`, MuPDF la reçoit dans sa matrice de dessin, une seule passe, aucun coût de plus
  qu'une page droite. Le lecteur voit des pages dont les côtés sont échangés et les met en page
  comme n'importe quelles pages.

### Où convertir les coordonnées
- Un document « tourné » (décorateur de `PdfDocument`) qui convertit tout : les résultats de
  recherche et la sélection gardés par les contrôleurs deviendraient faux à chaque changement
  d'angle, et l'impression doit voir le document droit.
- **Une seule frontière dans l'écran** (retenu) : les contrôleurs (liens, recherche, sélection) et
  le ViewModel restent en coordonnées du document ; les vues travaillent dans l'espace affiché ;
  `PageFrames` convertit à la frontière, avec les fonctions pures de `PageRotation`.

### Bouton
- Dans la feuille « Affichage » : une action qu'on répète (deux touchers pour 180°) serait à deux
  niveaux de profondeur.
- **Icône « Tourner » dans la barre du haut** (retenu par le mainteneur), entre « Rechercher » et
  la table des matières ; dans ⋮, en premier, quand la barre manque de place.

## Décision

1. **Domaine** (`core:domain`) : `PageRotation` (`NONE`, `QUARTER`, `HALF`, `THREE_QUARTERS` ;
   `degrees`, `next`, `swapsSides`, `displaySize`, `toDisplay` pour un point, un rectangle et un
   quad, `toPage`, `transform` : la matrice affine page → affichage ; `fromDegrees` ramène tout
   multiple de 90 dans 0..270, une autre valeur se lit 0). `RenderOptions(colorMode, rotation)`.
   Deux espaces : **page** (celui du moteur, `/Rotate` du PDF déjà appliqué, origine en haut à
   gauche) et **affichage** (la page tournée, même unité). Tests sur les quatre angles, aller-retour
   compris.
2. **Moteur** (`core:pdf`) : `RenderSpec.transform(page)` compose la rotation, le décalage à la
   région et l'échelle ; `drawPixels` la donne à MuPDF (`Matrix(a, b, c, d, e, f)`). La région
   demandée est dans l'espace affiché. Rien d'autre ne change dans `PdfDocument` : texte, liens,
   recherche et sélection restent en coordonnées de page.
3. **Rendu du lecteur** : `PageRenderer` prend la taille affichée de chaque page
   (`options.rotation.displaySize`) pour les pages, les détails et les aperçus ; `RenderKey`
   contient les options, et la première demande avec d'autres options vide les deux caches
   (ADR 0028) : changer d'angle invalide proprement les bitmaps.
4. **État** : `ReaderUiState.Ready.rotation`, `displayPageSizes` (pour la mise en page) à côté de
   `pageSizes` (le document, pour l'impression et les contrôleurs) ; `renderOptions(darkTheme)`
   porte la rotation. `ReaderViewModel.onRotate()` avance d'un quart et enregistre.
5. **Frontière** (`PageFrames`, `feature:reader`) : les vues reçoivent les tailles affichées ; un
   toucher ou un appui long est ramené en coordonnées de page (`toPage(hit)`) **avant** la
   recherche du lien ou du mot ; les surlignages de recherche, la sélection et ses poignées sont
   tournés avant d'être dessinés (`SearchHighlights`, `ReaderSelection.shownIn`) ; une poignée
   déplacée est ramenée dans la page avant `onHandleDrag`. `ContinuousNavigator` reçoit les
   `PageFrames` : `reveal` (recherche) tourne la zone du résultat ; `goTo` (sommaire, liens) avec
   un décalage montre, sur une page tournée, le **haut de la page affichée** (voir conséquences).
6. **Ajustement, zoom, page courante** : l'ajustement (ADR 0029) calcule sur les tailles affichées,
   donc une page tournée d'un quart est ajustée sur sa largeur affichée. Le zoom revient à 1 à
   chaque changement d'angle, comme à un changement d'ajustement. La page courante (la dernière
   signalée au ViewModel) est remise à l'écran après la rotation (`KeepPageAfterRotation`).
7. **Mémorisation** : colonne `rotation` (degrés, `INTEGER NOT NULL DEFAULT 0`) de
   `recent_documents`, **schéma Room 3**, migration automatique 2 → 3 (la valeur par défaut 0
   pour les entrées existantes) testée avec `MigrationTestHelper` ; `RecentDocument.rotation`,
   `SaveRotationUseCase`, `GetSavedRotationUseCase`. Une réouverture garde la rotation (comme la
   page) ; un document retiré des récents l'oublie. `ReadingHistory.saveRotation` écrit dans la
   portée de l'application, **après** l'enregistrement de l'ouverture (sinon celui-ci l'écraserait),
   et rien pour un document que le stockage n'a pas pu enregistrer.
8. **Bouton** : icône « Rotate right » (Material, Apache 2.0, dessinée dans `RotateAction.kt` comme
   `OpenInNewIcon`), cible de 48 dp, description « Tourner les pages, actuellement à 90° » (l'angle
   obtenu). `rotationFitsInBar` : l'icône reste dans la barre tant que le titre garde au moins
   64 dp × échelle de police après le bouton retour (72 dp avec les marges) et les icônes ; sinon
   elle passe en tête du menu ⋮ (« Tourner les pages (90°) »). Sur un téléphone de 360 dp, elle
   reste dans la barre jusqu'à 150 % de police et passe dans ⋮ à 200 % ; sur un écran de 280 dp,
   dans ⋮ dès 100 %.
9. **Hors du lecteur, jamais de rotation** : impression (fichier d'origine ou images rendues avec
   `RenderOptions.ORIGINAL`), partage (le fichier tel quel), vignettes de la bibliothèque. La
   transition de la vignette vers la page (ADR 0019) est désactivée quand la page est tournée : la
   vignette, droite, se fondrait mal dans une page tournée ; la page apparaît en fondu.

## Écarts avec les maquettes

- **E2** : la maquette n'a pas d'icône « Tourner » (F27 ajoutée après elle) ; le titre est donc
  tronqué un peu plus tôt (« Contrat de lo… » sur 360 dp).
- La poignée de sélection garde son dessin (goutte vers le bas, à gauche ou à droite selon le bout
  de la ligne) sur une page tournée : sa pointe est au bon endroit, mais sa forme ne suit pas le
  sens du texte tourné.

## Conséquences

- Positives : une seule conversion, pure et testée, entre l'écran et le document ; le moteur ne fait
  qu'une passe ; les contrôleurs (liens, recherche, sélection) n'ont pas changé ; l'impression et le
  partage restent justes sans y penser.
- Négatives :
  - une destination de sommaire ou de lien avec une position dans la page mène, sur une page tournée,
    au haut de la page affichée et non à la position exacte (à 90° et 270°, la ligne visée court le
    long de l'écran ; à 180°, le haut du document est en bas) ;
  - toutes les captures du lecteur changent (nouvelle icône dans la barre).
- Tests : `PageRotationTest` (quatre angles, aller-retour, rectangles, quads, matrice, lecture des
  degrés), `RenderTransformTest` et un test du moteur sous Linux (encre tournée), test instrumenté
  `MuPdfEngineInstrumentedTest.rendersTurnedPages`, migration 2 → 3 et 1 → 3, DAO, dépôt
  (rotation gardée à la réouverture, oubliée au retrait), cas d'usage, ViewModel (cycle, reprise,
  stockage en échec), `PageRenderer` (côtés échangés, région d'une tuile, caches vidés),
  `PageFramesTest`, surlignages tournés, Robolectric : lien et appui long sur une page tournée,
  icône et repli dans ⋮ ; captures à 90° et 180° en continu et page par page, barre et menu à 200 %,
  en clair et en sombre.
