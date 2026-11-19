# 0024 — Sélection et copie

- **Statut** : accepté
- **Date** : 2026-11-12
- **Décideurs** : choix fonctionnels par le mainteneur (une page à la fois, appui long sur un
  mot, poignées, barre « Copier » / « Tout sélectionner » sans « Partager », fermeture, copie
  depuis la route, contenu sensible pour un document protégé, permission de copie et réglage
  qui l'ignore, accès TalkBack) ; détails techniques par l'agent de codage
- **Références** : `SPEC.md` F08, §4.4 (Paramètres) ; `AGENTS.md` §3 (MVVM, pas de `Context`
  dans le ViewModel, dispatchers injectés), §9, §11 ; `ROADMAP.md` M4 (lot 6) ; ADR 0017 (mot
  de passe), 0018 (design), 0020 (API `selectText`, `isOnText`, `canCopyText`), 0022 (liens),
  0023 (recherche)

## Contexte

L'ADR 0020 a donné au moteur la sélection d'un texte entre deux points (`selectText`, modes
`Characters`, `Words`, `Lines`, début et fin logiques justes en arabe), le test « ce point
est-il sur du texte » (`isOnText`) et la permission de copie du document (`canCopyText`), que le
moteur rapporte sans l'appliquer. Restait à en faire le geste du lecteur sans casser les
autres (pincement, double toucher, défilement, liens, recherche), à décider qui applique la
permission de copie, et à rendre la copie accessible sans appui long.

## Options envisagées

### Où dessiner la sélection et ses poignées
- **Un calque unique au-dessus de la liste**, qui recalcule la position des pages : du calcul
  à chaque défilement, et deux systèmes de coordonnées à garder d'accord.
- **Dans chaque `PdfPage`** (retenue), comme le calque de la recherche : les zones dans la page
  (qui coupe ce qui déborde), les poignées dans la boîte qui l'entoure (qui ne coupe pas), en
  coordonnées de la page. Elles suivent le défilement, le zoom et le pincement sans calcul.

### Barre d'actions
- Dans la page : coupée par la liste ou par le défilement page par page, cachée sous la barre
  du haut quand la sélection est en haut de l'écran.
- **Une `Popup`** (retenue) placée par un `PopupPositionProvider` : au-dessus de la sélection,
  sinon en dessous (au-delà des poignées), toujours dans la fenêtre. C'est ce que fait la
  sélection de texte de Compose.

### Permission de copie
- Dans le moteur : refuser le texte. Mais la sélection visuelle doit rester possible, et le
  réglage doit pouvoir lever l'interdiction.
- **Dans le lecteur** (retenue) : `AppSettings.allowsTextCopy(documentAllowsCopy)` (`core:domain`)
  combine la permission du document et le réglage ; lue à chaque copie.

## Décision

1. **Contrôleur** : `SelectionController` (`feature:reader`, paquet `selection`), détenu par
   `ReaderViewModel` comme `LinkController` et `SearchController`. Il expose la sélection
   (`StateFlow<TextSelection?>`, jamais vide) et des effets (`Copy`, `CopyRefused`,
   `NothingToCopy`). Le texte est lu sur le dispatcher injecté.
2. **Une page à la fois** (ADR 0020). **Appui long sur du texte** : sélectionne le mot
   (`Words`) et affiche deux poignées placées sur `TextSelection.start` et `end` ; la poignée de
   début pend du côté opposé au texte sélectionné (à gauche en latin, à droite en arabe), celle de
   fin aussi (`selectionHandles`, testé dans les deux sens). **Appui long hors du texte**
   (`isOnText` faux) ou sur une page sans texte (document numérisé) : rien n'est sélectionné, et
   l'appui garde le comportement d'avant, celui d'un toucher (lien, sinon plein écran).
3. **Poignées** (`TextSelectionHandles.kt`) : une goutte de 22 dp dans une cible de 48 dp, nommée
   pour TalkBack (« Début de la sélection », « Fin de la sélection »). Le glissement sélectionne
   caractère par caractère (`Characters`) de l'autre extrémité, fixée au début du geste, jusqu'au
   point visé ; l'écart entre le doigt et la pointe est gardé, pour que le doigt ne cache pas le
   texte. Les positions arrivées pendant une lecture sont fusionnées (seule la dernière compte) ;
   une position qui ne sélectionne rien garde la sélection précédente. Les poignées peuvent se
   croiser. Elles restent en coordonnées de page de gauche à droite, même dans une interface de
   droite à gauche (la barre d'actions, elle, suit la langue).
4. **Barre d'actions** au-dessus de la sélection : « Copier » et « Tout sélectionner » (toutes
   les lignes de la page : `Lines` du coin haut gauche au coin bas droit). Pas de « Partager ».
   « Copier » ferme la sélection.
5. **Fermeture** : un toucher ailleurs sur les pages (qui ne fait alors rien d'autre : ni lien,
   ni plein écran), le retour (`BackHandler`, prioritaire sur celui de la recherche), un
   changement de page (y compris en défilement continu) ou l'ouverture de la recherche.
6. **Gestes** : l'appui long s'ajoute à `detectTapGestures` (`readerGestures`) ; le pincement, le
   double toucher, le défilement, les liens et la recherche restent inchangés (tests
   `ReaderSelectionTest`, `ReaderLinksTest`). Une poignée consomme son appui : il ne fait ni
   défiler ni toucher la page.
7. **Copie dans le presse-papiers par l'écran** (`SelectionEffectsHandler`, `LocalClipboard`) :
   jamais de `Context` dans le ViewModel. Sur Android 13+, le texte d'un document **ouvert avec
   un mot de passe** (ADR 0017) est marqué sensible (`ClipDescription.EXTRA_IS_SENSITIVE`) : le
   système le masque dans son aperçu. Tout autre texte est copié normalement. Le message « Texte
   copié » ne s'affiche que **sous Android 13** : à partir de l'API 33, le système affiche sa
   propre confirmation de copie (comportement du système, documenté par Android ; à vérifier sur
   appareil).
8. **Permission de copie** : par défaut, si `canCopyText` est faux, « Copier » reste visible mais
   atténué, lu « Interdit par l'auteur » par TalkBack, et explique au toucher : « L'auteur de ce
   document interdit la copie du texte ». La sélection visuelle reste possible. Le PDF du jeu de
   test `encrypted_owner_password_only.pdf` interdit la copie (vérifié par
   `MuPdfEncryptionTest` et `SelectionInstrumentedTest`).
9. **Réglage « Ignorer l'interdiction de copie »** (E3, section « Lecture ») : désactivé par
   défaut, avec un texte d'explication qui rappelle les droits de l'auteur. Stocké dans DataStore
   (`ignore_copy_restriction`, absent = désactivé : pas de migration), exposé par `AppSettings`,
   `SettingsRepository`, `SetIgnoreCopyRestrictionUseCase` et `SettingsViewModel`. Le lecteur le
   suit en direct (`ReaderUiState.Ready.canCopyText`) ; le contrôleur le relit à chaque copie.
10. **TalkBack** : une action personnalisée de la zone du document, « Copier le texte de la
    page », copie tout le texte de la page affichée sans appui long, sous la même règle de
    permission (refus expliqué, « Aucun texte sur cette page » pour une page numérisée). Les
    boutons de la barre ont des cibles d'au moins 48 dp.
11. **Couleurs** : jetons `color.reader.selection` (bleu de la marque à 30 %) et
    `color.reader.selectionHandle` (`LutrinReaderColors`), la page restant du papier blanc
    quel que soit le thème (ADR 0023).

## Conséquences

- Positives : sélection et copie en latin et en arabe, au zoom et dans les deux modes ; la
  règle de copie est en un seul endroit du domaine, testée ; la copie est accessible sans geste
  fin ; les gestes existants restent couverts par leurs tests.
- Négatives / dette acceptée :
  - « Tout sélectionner » prend les lignes du coin haut gauche au coin bas droit : sur une page
    dont l'ordre de lecture ne commence pas en haut à gauche, il peut manquer des lignes (le
    moteur n'a pas d'opération « toute la page ») ;
  - un appui long hors du texte agit comme un toucher, mais au moment de l'appui long et non au
    relâchement ;
  - en défilement continu, faire défiler jusqu'à changer de page courante ferme la sélection,
    même si sa page reste visible ;
  - pendant un pincement, la barre d'actions (fenêtre à part) ne suit pas l'échelle visuelle
    avant la fin du geste ;
  - après un appui long, glisser le doigt sans le lever fait défiler la page au lieu d'étendre
    la sélection (seules les poignées l'étendent) ;
  - les limites de l'ADR 0020 restent (mots sans espaces, mot coupé en fin de ligne).
- Actions de suivi : essais sur appareil (appui long et poignées au doigt, zoomé, page par
  page, arabe de `text_selection.pdf`, confirmation du système dès Android 13 et message
  avant, aperçu masqué d'un document protégé, TalkBack), `SelectionInstrumentedTest`.
