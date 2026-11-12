# 0021 — Présentation de la table des matières

- **Statut** : accepté
- **Date** : 2026-11-12
- **Décideurs** : cadrage et choix fonctionnels par le mainteneur (tiroir latéral, section
  courante dépliée et surlignée, liste paresseuse, entrées externes confirmées par V7, titre de la
  section dans l'aperçu du curseur) ; détails techniques par l'agent de codage
- **Références** : `SPEC.md` F06, F09, F18 ; `AGENTS.md` §3, §4, §11 ; `ROADMAP.md` M4 (lot 3) ;
  ADR 0011 (page par page), 0018 (nouveau design, barre du lecteur E2), 0020 (API du moteur :
  `OutlineItem`, `PdfDestination`) ; maquettes `docs/design/png/V2-TableMatieres.png`,
  `docs/design/screens/V2-TableMatieres.html` et, pour la barre du lecteur,
  `docs/design/proposals/png/E2-Lecteur.png`

## Contexte

Le moteur rend le sommaire d'un PDF sous forme d'arbre (`OutlineItem`, avec une destination
`PdfDestination(pageIndex, x, y)` ou une URI) et le lecteur sait aller à une page et à une
position (`PageNavigator.goTo(page, yOffset)`, lot 2). Il reste à décider comment l'afficher et
comment il se relie au lecteur : la forme de l'écran V2, ce que le ViewModel garde, quelle est la
« section courante », ce que devient un document sans sommaire, et quoi faire d'une entrée qui
mène hors du document, alors que la confirmation V7 n'existe pas encore (lot 4).

## Décision

1. **Tiroir latéral** (`ModalNavigationDrawer`), comme la maquette V2 : 320 dp, coins arrondis du
   côté du contenu, fond `surfaceContainerLow`, titre, « titre du document · N pages », liste.
   En écriture de droite à gauche il glisse depuis la droite (Material 3 suit la direction de
   mise en page). Il s'ouvre par l'icône « Table des matières » de la barre du haut (E2
   proposée, avant « Plus d'options »), pas par un glissement depuis le bord : celui-ci appartient
   aux pages (mode page par page) et au geste « retour » du système (`gesturesEnabled` n'est vrai
   que tiroir ouvert, pour le fermer en glissant). Il se ferme par un tap à côté, la croix, le
   glissement ou « retour ». Le titre du tiroir suit le style des titres de panneaux du thème
   (`titleLarge`, empattement), non la police sans empattement de la maquette V2, plus ancienne
   que l'ADR 0018. Le contenu n'est composé que tiroir ouvert ou en mouvement.
2. **Sommaire aplati dans l'état de l'écran** (`ReaderUiState.Ready.outline: ReaderOutline`) :
   liste d'`OutlineEntry` dans l'ordre du document (parcours en profondeur, itératif : un
   sommaire profond ne fait pas déborder la pile), avec `id` (position, clé stable de la
   `LazyColumn`), `level`, `parentId`, `hasChildren` et `target`. Le ViewModel le lit **une
   fois le document affiché**, hors du fil principal, par le répartiteur injecté : les pages
   n'attendent pas le sommaire. Un sommaire illisible (`PdfError`) laisse le document ouvert sans
   sommaire.
3. **Cible d'une entrée** (`OutlineTarget`) : `Page(pageIndex, yOffset)` si la destination existe
   et reste dans le document (`y` absent = haut de la page) ; sinon `External(uri)` si une URI
   est donnée ; sinon aucune cible. Une destination au-delà de la dernière page est écartée.
4. **Section courante** : parmi les entrées qui mènent à une page, celle de la plus grande page
   inférieure ou égale à la page affichée, et, à égalité, la **dernière dans l'ordre du
   document** (donc « 2.2 » plutôt que « 2. » quand les deux commencent page 2). On va par la page
   et non par l'ordre pour qu'un sommaire mal ordonné (une annexe listée en premier) ne prenne pas
   les pages qui la précèdent. Le calcul est au niveau de la page, pas de la position : une page
   qui porte deux sections montre la dernière. Pas de section avant la première entrée.
5. **État déplié** : `ReaderOutline.expanded` (ensemble d'`id`), tenu par le ViewModel
   (`onOutlineToggle`). `onOutlineOpened`, appelé avant l'ouverture, y **ajoute le chemin jusqu'à
   la section courante** et la section elle-même si elle a des sous-sections : elle est dépliée
   et surlignée, ce qui diffère de la maquette (qui montre « 3. » repliée). Ce que l'utilisateur
   a déplié reste déplié. Le tiroir s'ouvre **défilé sur la section courante** (une ligne de
   contexte au-dessus), car une table de plusieurs centaines d'entrées ne doit pas s'ouvrir sur
   sa première ligne. Les entrées visibles sont celles sans parent replié (`visibleEntries`).
6. **Ligne d'entrée** : au moins 48 dp. Chevron (cible 48 × 48 dp séparée, pour ne pas confondre
   « déplier » et « aller ») pour une entrée à sous-sections ; titre sur 3 lignes au plus ; « p. N »
   à droite ; décalage de 12 dp par niveau, plafonné au niveau 6. Une entrée sans cible est
   affichée, non cliquable ; si elle a des sous-sections, le tap sur le titre les déplie. Un titre
   vide devient « Section sans titre ». Le sens du texte suit le contenu du titre
   (`TextDirection.Content`), pas celui de l'application : un titre latin dans une application en
   arabe ne se retrouve pas avec son numéro à l'envers.
7. **Aller à une entrée** : un tap sur une entrée du document appelle
   `PageNavigator.goTo(page, y)` puis ferme le tiroir. En défilement continu la position est
   appliquée ; page par page elle est ignorée (la page est montrée entière, ADR 0011).
8. **Document sans sommaire** : l'icône est **cachée**. La maquette V2 ne dessine pas d'état
   vide, et une icône qui ouvre un panneau vide est une impasse ; la SPEC (F06) dit « si
   présente ». L'icône apparaît quand le sommaire est lu (il l'est juste après l'affichage).
9. **Titre de la section dans l'aperçu du curseur** : `FloatingPageBar.sectionTitle` reçoit
   `ReaderOutline.sectionTitleAt(page)` (la même règle que la section courante) ; rien si le
   titre est vide ou la page précède la première entrée.
10. **Entrées externes** : affichées avec l'icône « lien externe » (`OpenInNewIcon`, tracé de
    Material Symbols, Apache 2.0, dessiné dans `feature:reader` pour ne pas tirer
    `material-icons-extended`) et annoncées « lien externe ». Un tap appelle
    `ReaderViewModel.onExternalOutlineEntry(uri)` (via le rappel `onExternalOutlineEntry` de
    `ReaderScreen`), **qui ne fait rien pour l'instant** : le lecteur n'ouvre jamais une URI par
    lui-même. Le lot 4 (liens, V7) le branchera sur le dialogue de confirmation, en filtrant les
    schémas comme le décide l'ADR 0020 (`http`, `https`, `mailto`). Le tiroir reste ouvert.
11. **Accessibilité** : la ligne annonce son titre et la page visée (« 2.1 Date…, page 2 »), son
    niveau (`stateDescription` « Niveau 2 ») et si c'est la section courante (`selected`) ;
    le chevron annonce « Sous-sections de … », « Déplié » ou « Replié » et l'action « Déplier » ou
    « Replier » ; le titre du panneau est un en-tête ; la croix a 48 dp.

## Options écartées

- **Feuille basse ou écran plein** pour V2 : le mainteneur a choisi le tiroir, comme la maquette.
- **État déplié dans l'interface** (`rememberSaveable`) : le ViewModel connaît déjà l'arbre et la
  section courante ; le garder là rend le « chemin vers la section courante » testable sans
  interface.
- **Aplatir l'arbre à chaque ouverture** : coûteux pour un sommaire de milliers d'entrées ; fait
  une fois, hors du fil principal.
- **Section par la position (`y`)** : plus exacte, mais il faudrait suivre le défilement dans la
  page ; le niveau de la page suffit pour surligner une section et nommer l'aperçu.
- **Ouvrir les liens externes ici** : refusé, le dialogue de confirmation (V7) est au lot 4.

## Conséquences

- Positives : navigation par sections en défilement continu et page par page, grandes tables
  tenues par la liste paresseuse, TalkBack complet, droite à gauche, et le curseur nomme la section
  visée.
- Dette acceptée :
  - une entrée externe est cliquable sans effet tant que le lot 4 n'a pas branché V7 ;
  - la section courante est celle de la page (deux sections sur une page : la dernière) ;
  - l'icône n'apparaît qu'après la lecture du sommaire (une fraction de seconde, plus sur un très
    gros sommaire) ;
  - sans `material-icons-extended`, les icônes de la table sont les icônes du jeu « core »
    (liste, chevrons, croix) plus un tracé local ; à 200 % de police, les titres longs passent
    sur trois lignes au plus et un mot long peut être coupé.
- À essayer sur appareil : voir `docs/reprise.md`.
