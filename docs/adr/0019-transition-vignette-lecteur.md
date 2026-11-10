# 0019 — Transition animée vignette → lecteur

- **Statut** : accepté
- **Date** : 2026-11-10
- **Décideurs** : le mainteneur (demande de la transition, ADR 0018 §7) ; règles d'attente, de
  repli et mise en œuvre par l'agent de codage
- **Références** : `AGENTS.md` §3 (modules), §8 (performance), §11 (accessibilité) ; `SPEC.md`
  F11, F12 ; ADR 0012 (mesures de démarrage, `ReportDrawnWhen`, Baseline Profile), 0014
  (« Ouvrir avec » : lecteur sans bibliothèque derrière), 0015 (`documentId`, reprise de page),
  0017 (mot de passe), 0018 §7 (transition annoncée), dont cet ADR précise la mise en œuvre

## Contexte

L'ADR 0018 a adopté une transition où la vignette d'un document de la bibliothèque (carte
« Reprendre la lecture » ou ligne des récents) s'agrandit pour devenir la page du lecteur, et où
la page se replie vers la vignette au retour. Plusieurs contraintes la rendent délicate :

- **La bibliothèque et le lecteur sont deux modules `feature:*`** qui ne se connaissent pas
  (`AGENTS.md` §3) ; seule `app` les réunit, dans un `NavHost`.
- **Le lecteur n'est pas prêt quand la navigation commence** : il ouvre le document (de quelques
  dizaines de millisecondes à plusieurs centaines pour 600 pages, ADR 0012), demande parfois un
  mot de passe (V4, ADR 0017) ou affiche une erreur (V8). Au premier instant de la transition,
  il n'y a encore aucune page à viser.
- **La page reprise n'est pas forcément la première** (ADR 0015), alors que la vignette montre la
  première page.
- **D'autres entrées** dans le lecteur n'ont aucune vignette : sélecteur de fichier, « Ouvrir
  avec » et partage (ADR 0014), activité recréée (rotation, mort du processus).
- **Performance** : rien ne doit retarder l'ouverture ni `ReportDrawnWhen` (mesures de
  l'ADR 0012), ni garder une image en mémoire après la transition.
- **Accessibilité** : rien d'animé quand l'utilisateur a supprimé les animations.

Compose 1.12 (BOM 2026.09.00) fournit l'API d'éléments partagés (`SharedTransitionLayout`,
`sharedBounds`) **sans opt-in expérimental** : aucune déclaration de l'API ne porte plus
`ExperimentalSharedTransitionApi`. Navigation Compose 2.10 pilote la transition du `NavHost` par
le geste de retour prédictif (`SeekableTransitionState`). Aucune dépendance nouvelle n'est
nécessaire : `androidx.compose.animation` était déjà tirée par Material 3 ; `core:ui` la déclare
désormais explicitement parce qu'il expose ses portées (`docs/dependencies.md`).

## Options envisagées

**Cible de la vignette dans le lecteur**

- **A — La page réelle du lecteur seulement** (le `PdfPage` de la page courante). Fidèle, mais
  absente au début de presque toutes les ouvertures (état `Loading`) : la vignette n'aurait rien
  à viser, puis trouverait sa cible en pleine course (saut) ou jamais. Rejetée pour l'aller,
  **retenue pour le retour**, où la page est là.
- **B — Tout l'écran du lecteur** (« container transform »). Toujours présent, mais la vignette
  deviendrait l'écran et non la page, et les barres seraient déformées par les bornes animées.
  Rejetée.
- **C — Une page d'ouverture** : une page blanche posée là où la première page s'affiche à
  « ajuster à la largeur », présente dès la première image, cible de l'agrandissement, qui
  attend tant que le document s'ouvre et s'efface ensuite. **Retenue.**
- **D — Attendre l'ouverture avant de naviguer** : `NavHost` ne sait pas différer une
  transition, et le toucher semblerait ignoré. Rejetée.

**Passage des portées de transition aux écrans**

- **Paramètres** à travers chaque écran, ligne, page de liste paresseuse et `NavGraphBuilder` :
  explicite, mais il faudrait faire traverser `SharedTransitionScope` et la portée de chaque
  destination à tous les composables intermédiaires des deux features.
- **`CompositionLocal` internes à `core:ui`**, derrière une petite API : **retenue**. Les
  features ne voient que `rememberDocumentTransition(documentId)`, qui rend `null` hors de
  l'application (aperçus, tests, autres activités) : les écrans restent utilisables et testables
  sans transition. Les deux `CompositionLocal` sont ajoutés à la liste autorisée de la règle
  `CompositionLocalAllowlist` de detekt (`config/detekt/detekt.yml`).

## Décision

### 1. Architecture

- **`core:ui`, paquet `transition`** :
  - `DocumentTransitionLayout(linkedDocumentId)` : le `SharedTransitionLayout` autour de la
    navigation ; il fournit la portée et le document lié ;
  - `AnimatedVisibilityScope.DocumentTransitionDestination { }` : appelé avec la portée
    `AnimatedContentScope` de chaque destination (bibliothèque dans `app`, lecteur dans
    `readerScreen`) ;
  - `rememberDocumentTransition(documentId)` : la transition d'un élément, ou `null` ;
    `Modifier.sharedDocumentPage(transition)` : l'élément partagé (sans effet pour `null`) ;
    `animateWithDocumentTransition(…)` : une valeur qui suit la transition (l'inclinaison) ;
  - `DocumentTransitionPolicy` : les règles pures (animations actives, document lié) ;
  - `DocumentPageKey(documentId)` : la **clé partagée**, construite des deux côtés avec
    `documentIdFor(uri)` (`core:domain`, ADR 0015) : `RecentDocumentItem.documentId` dans la
    bibliothèque, `ReaderViewModel.documentId` (nouveau, connu avant l'ouverture) dans le
    lecteur ;
  - `LutrinMotion` : durées et courbe (jetons de mouvement de Material 3, voir §3).
- **`app`** garde, dans un `rememberSaveable`, le **document touché** : l'identifiant du
  document ouvert par un toucher sur sa vignette, remis à `null` par le sélecteur de fichier et
  par une demande d'une autre application. Le document lié est ce document touché, si les
  animations sont actives.
- **`feature:library`** : la vignette de la carte et celle des lignes portent l'élément partagé
  (`RecentThumbnail(transition = …)`), sauf pour un document protégé
  (`RecentDocumentItem.transitionDocumentId`).
- **`feature:reader`** : la page d'ouverture (`OpeningPage`, `OpeningPagePhase`) puis la page
  affichée (`SharedPage`, paramètre `pageModifier` de `PdfPage`). Jamais les deux à la fois :
  chacune a son propre état d'élément partagé et la page affichée ne le porte qu'une fois la
  page d'ouverture partie.

### 2. Ce qui se passe

- **Aller** (carte ou ligne) : la vignette s'agrandit jusqu'à la page d'ouverture, posée sous
  la barre du haut, entre les marges de 16 dp, à la forme des vignettes (104 × 138). La vignette
  reste visible pendant le trajet ; la page d'ouverture, blanche, apparaît par-dessus dans la
  dernière partie (fondu de 150 à 350 ms). L'inclinaison de −3° de la carte se redresse pendant
  le trajet. Le contenu de chaque côté est mis à l'échelle des bornes, jamais remesuré.
- **Attente** (règle retenue) :

  | Phase | Quand | Page d'ouverture |
  |-------|-------|------------------|
  | Partagée | la navigation (ou le geste de retour) est en cours | cible de l'élément partagé |
  | En attente | navigation finie, document encore en ouverture (`Loading`) | page blanche immobile, à sa place, au-dessus de l'indicateur de chargement |
  | Partie | le lecteur montre autre chose : document, dialogue de mot de passe, écran d'erreur | fondu de 150 ms, puis retirée pour de bon |

  Une fois le document ouvert, ses pages sont elles-mêmes blanches jusqu'à leur rendu : la page
  d'ouverture s'efface sur une page blanche au même endroit (en défilement continu), puis le
  rendu apparaît comme aujourd'hui. Pas de saut, pas d'écran vide, rien de bloqué (la page
  d'ouverture laisse passer les touchers).
- **Retour** (bouton retour, flèche de la barre, geste) : la page affichée se replie vers la
  vignette du document si celle-ci est à l'écran dans la bibliothèque — en général la carte,
  puisque le document ouvert devient le plus récent ; la vignette réapparaît par-dessus dans la
  dernière partie du trajet et reprend son inclinaison. Si la vignette n'est pas visible (liste
  défilée), simple fondu. **Retour pendant l'ouverture** : la page d'ouverture se replie vers la
  vignette.
- **Geste de retour prédictif** : Navigation Compose 2.10 fait suivre au doigt la transition du
  `NavHost`, éléments partagés et inclinaison compris ; un geste annulé ramène le lecteur. Il
  faut que le système envoie la progression du geste à l'application : c'est le cas par défaut
  sous Android 16 et plus (cible 37), et le manifeste porte désormais
  `android:enableOnBackInvokedCallback="true"` pour Android 13 à 15. Android 13 ne transmet pas
  la progression (`OnBackAnimationCallback` arrive avec Android 14) : le retour y joue la
  transition complète au relâchement, comme avant Android 13.
- **Animations réduites** : si `Settings.Global.ANIMATOR_DURATION_SCALE` vaut 0 (réglage
  d'accessibilité « Supprimer les animations », ou option pour les développeurs), aucun
  document n'est lié : pas d'élément partagé, et Compose saute ses animations à cette échelle,
  donc le changement d'écran est immédiat. Le réglage est relu à chaque retour au premier plan.
- **Écrans** : toutes les destinations passent de l'une à l'autre par un fondu de 300 ms (avant :
  700 ms, valeur par défaut de Navigation Compose). Les barres et le bouton flottant
  apparaissent et disparaissent avec leur écran, sans être déformés : ils ne sont jamais dans
  les bornes partagées.

### 3. Mouvement

Material 3 pour Compose garde ses jetons de durée internes et son `MotionScheme` public ne
donne que des ressorts, dont on ne fixe pas la durée ; le fondu croisé a besoin d'une durée
connue. `LutrinMotion` reprend donc les jetons : trajet et inclinaison **400 ms** (`medium4`)
avec la courbe **emphasized** `(0,2 ; 0 ; 0 ; 1)` ; fondu des écrans **300 ms** (`medium2`) ;
fondu court **150 ms** (`short3`).

### 4. Cas de repli

| Cas | Comportement |
|-----|--------------|
| Document protégé | pas d'élément partagé (pas d'image à agrandir, le lecteur demande d'abord le mot de passe) : fondu |
| Document dont la vignette manque encore | élément partagé quand même : le cadre aux lignes grises est un dessin de page, il grandit comme une vignette |
| Sélecteur de fichier, « Ouvrir avec », partage | aucun document lié : fondu, comme avant (même si le document est dans la carte) |
| Rotation ou mort du processus en pleine transition | le lecteur est restauré sans transition et sans page d'ouverture ; le document lié est gardé, donc le retour replie encore la page vers la vignette |
| Mot de passe (V4), erreur (V8) | la page d'ouverture finit son trajet, puis s'efface sur le dialogue ou l'écran d'erreur |
| Page zoomée au retour | fondu (une page zoomée dépasse l'écran et rétrécirait depuis l'extérieur) |
| Mode page par page | la page d'ouverture est à la place du défilement continu (le mode n'est connu qu'une fois le document ouvert) ; elle s'efface ensuite sur la page centrée : fondu croisé entre deux positions, assumé |
| Vignette hors de l'écran au retour | fondu |

### 5. Performance

- Rien n'est recomposé pendant l'animation : l'opacité de la page d'ouverture et l'inclinaison
  sont lues dans des `graphicsLayer` ; les états lus en composition ne changent qu'au début et
  à la fin d'une navigation.
- L'ouverture du document et `ReportDrawnWhen` ne changent pas : la page d'ouverture est un
  calque à part, sans image ni rendu.
- Aucune image n'est gardée : la vignette appartient à la destination de la bibliothèque, libérée
  à la fin de la transition ; la page d'ouverture n'en dessine aucune.
- `SharedTransitionLayout` est un `LookaheadScope` autour de toute la navigation : il ajoute une
  passe de pré-mesure dont le coût n'a pas été mesuré. L'activité de mesure de `:benchmark`
  (`BenchmarkReaderActivity`) est enveloppée de la même façon, pour que les mesures de
  l'ADR 0012 le comprennent.

## Conséquences

- **Positives** : la transition voulue par l'ADR 0018, sans dépendance ni opt-in expérimental ;
  les deux features restent indépendantes et utilisables sans transition ; les règles de
  décision sont des fonctions pures testées ; les captures d'écran de référence n'ont pas
  changé (l'état au repos est identique).
- **Limites acceptées** :
  - **l'animation n'a jamais été vue** : aucun appareil ni émulateur pendant la mise en œuvre.
    Les tests Robolectric vérifient ce qui est composé et apparié image par image, pas le rendu ;
  - en mode page par page, fondu croisé entre la page d'ouverture et la page centrée ;
  - le coût de la pré-mesure de `SharedTransitionLayout` sur le défilement est inconnu.
- **Suivi** :
  - essais sur appareil (liste ci-dessous) ;
  - refaire les mesures de l'ADR 0012 (défilement, ouverture) et **régénérer le Baseline
    Profile**, qui ne couvre pas encore le code des éléments partagés.

### Essais manuels à faire sur appareil

1. Carte « Reprendre la lecture » d'un document de plusieurs pages arrêté page 3 : la vignette
   grandit en se redressant, la page 3 apparaît ; retour par la flèche, puis par le bouton
   retour, puis par le geste (lent, puis annulé à mi-course).
2. Une ligne des récents, même chose ; au retour la page se replie vers la carte (le document
   est monté en tête).
3. Le PDF de 600 pages (ouverture lente) : la page blanche attend sans saut ni écran vide.
4. Un PDF protégé (dialogue), un fichier supprimé (V8) : fondu propre.
5. Toucher puis retour immédiat ; toucher puis rotation pendant la transition.
6. « Supprimer les animations » activé (Accessibilité) : changement immédiat ; désactivé et
   retour dans l'application : transition revenue.
7. Sélecteur de fichier et « Ouvrir avec » sur le document de la carte : fondu seulement.
8. Mode page par page, thème sombre, grande police, paysage, appareil lent.
