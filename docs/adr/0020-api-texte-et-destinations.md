# 0020 — API texte et destinations du moteur PDF

- **Statut** : accepté
- **Date** : 2026-11-11
- **Décideurs** : cadrage et choix fonctionnels par le mainteneur (sélection limitée à une page,
  recherche insensible à la casse et aux accents par défaut, plafond de 1 000 résultats, extrait
  de contexte, schémas de liens autorisés, permission de copie) ; détails techniques par l'agent
  de codage
- **Références** : `SPEC.md` F06–F09, §4 (interface indicative du moteur), §9 (jeu de test) ;
  `AGENTS.md` §3 (moteur derrière `PdfEngine`, thread du document, ressources natives), §9
  (sécurité) ; `ROADMAP.md` M4 (lot 1) ; ADR 0001 (MuPDF), 0007 (intégration de MuPDF), 0013
  (tests JVM du moteur sous Linux seulement)

## Contexte

Le jalon M4 (table des matières, recherche, sélection, liens) s'appuie sur le moteur. Celui-ci
exposait déjà `outline()`, `links()`, `search()` et `textAt()`, mais :

- un lien ou une entrée de sommaire ne donnait que la page visée, pas la position dans la page
  (`Document.resolveLinkDestination` n'était pas utilisé) ;
- `textAt(index, PdfRect)` appelait `StructuredText.copy(a, b)`, qui lit le texte **dans l'ordre
  de lecture entre deux points**, pas le texte d'un rectangle : l'API promettait autre chose que
  ce qu'elle faisait, et ne rendait ni les zones à surligner ni l'accrochage au mot ;
- la recherche n'émettait rien pour une page sans résultat : impossible d'afficher une
  progression sur un long document ; aucun extrait pour une liste de résultats ; aucun plafond ;
- la permission de copie des PDF chiffrés n'était pas lue ;
- les liens externes étaient rendus tels quels, ce qui est voulu, mais aucune règle n'était
  écrite sur qui les filtre.

En essayant MuPDF 1.28.6 (le même que l'application, via PyMuPDF), deux pièges sont apparus :

- `snapSelection` puis `highlight` / `copy` font un aller-retour entre positions de caret et
  points ; en texte arabe (droite à gauche), le point renvoyé pour le début d'un mot retombe
  après sa première lettre : un appui long sur « مكتبة » sélectionnait « كتبة ».
- le marcheur de `StructuredText` (et donc `getBlocks()`) crée un objet Java `Font` par
  caractère et un `Image` par bloc d'image, libérés seulement par le ramasse-miettes, hors du
  thread du document.

## Options envisagées

### Sélection
- **A — `snapSelection` + `highlight` + `copy` de MuPDF** : peu de code ; mais le défaut en
  arabe ci-dessus, et des zones par caractère pour le texte droite à gauche.
- **B — Sélection calculée en Kotlin** sur les caractères extraits (code, quad, sens bidi,
  direction de la ligne), avec des carets en ordre logique : plus de code, mais exact en arabe,
  testable sans bibliothèque native (donc aussi sous Windows), et libère explicitement les
  objets natifs du marcheur.

### Progression de la recherche
- **A — `Flow<SearchHit>`** inchangé et un rappel `onPageSearched` : deux canaux pour un même
  flux, rappel appelé hors du collecteur.
- **B — Un événement scellé** (résultat ou page terminée) : le lecteur doit trier les événements.
- **C — Un `SearchPageResult` par page**, avec ses résultats (éventuellement aucun) : les
  résultats arrivent toujours page par page, et la progression se lit sur le même flux.

### Destination
- Page seule ; page et `y` ; page, `x` et `y` facultatifs.

### Filtrage des schémas de lien
- Dans le moteur (ne rendre que `http`, `https`, `mailto`) ; ou dans le lecteur, le moteur
  rendant l'URI brute.

## Décision

1. **Destinations** : `PdfDestination(pageIndex, x: Float?, y: Float?)`, en coordonnées de page
   (points, origine en haut à gauche, rotation appliquée : MuPDF les convertit). `OutlineItem`
   porte `destination: PdfDestination?` (au lieu de `pageIndex`) et `PdfLink.Internal`
   `destination: PdfDestination` (au lieu de `pageIndex`). Une coordonnée n'est gardée que si
   le type de destination la fixe (`XYZ`, `FitR` : x et y ; `FitH`, `FitBH` : y ; `FitV`,
   `FitBV` : x ; `Fit`, `FitB` : aucune) et qu'elle est finie (`/XYZ null null` donne NaN).
   Les destinations nommées sont résolues par MuPDF. Un lien interne qui ne se résout pas est
   écarté, une entrée de sommaire garde son titre avec une destination `null`.
   - **Pages tournées d'un quart de tour** (`/Rotate 90` ou `270`) : pour `FitH`, `FitV`, leurs
     variantes `B` et un `XYZ` à une seule coordonnée, MuPDF 1.28.6 tourne le point (0, y) ou
     (x, 0) avec la page et ne garde que l'axe d'origine (`populate_destination`) : la valeur
     tombe sur l'autre axe et se perd (constaté : `/FitH 300` donne y = 0). Ces destinations
     donnent alors la page seule ; la page n'est chargée pour ce contrôle que dans ce cas, hors
     du cache du lecteur. `XYZ` complet et `FitR` restent exacts.
   - **`FitR`** : `PdfDestination` porte aussi `width` et `height` de la zone à afficher. Le
     `LinkDestination` Java de MuPDF 1.28.6 les perd (son constructeur affecte `width` et
     `height` à eux-mêmes) : ils sont lus dans l'URI `#page=…&viewrect=x,y,l,h` ; pour une
     destination nommée vers un `FitR`, la taille reste inconnue (`null`). `y` est bien le bord
     haut (MuPDF prend le minimum des deux coins transformés).
2. **Liens externes** : le moteur rend l'URI **brute**, quel que soit le schéma (`mailto:`,
   `javascript:`, `file:`…). MuPDF transforme une URI relative en `file://…`. Le **lecteur**
   (lot 4) n'ouvre que `http`, `https` et `mailto`, après confirmation (F09).
3. **Sélection** : `selectText(index, start: PdfPoint, end: PdfPoint, mode: SelectionMode)`
   remplace `textAt`, et renvoie `TextSelection(pageIndex, quads: List<PdfQuad>, text)` : une
   zone par ligne (qui suit la direction de la ligne, donc le texte tourné), le texte en ordre
   logique, lignes séparées par `\n`. Modes `Characters` (glisser une poignée ; deux points
   égaux ne sélectionnent rien), `Words` (appui long), `Lines`. Option B : le calcul est fait en
   Kotlin (`PageText`), à partir des caractères lus par un marcheur qui libère aussitôt chaque
   `Font` et `Image` sur le thread du document. Un point hors du texte est ramené au caret le
   plus proche de la ligne la plus proche ; `isOnText(index, point)` dit si un point est sur
   une ligne de texte, pour qu'un appui long dans une marge ne sélectionne rien. Quand deux
   lignes se chevauchent, le point va à celle dont le milieu est le plus proche.
   `TextSelection.start` et `end` sont les coins bas où la sélection commence et finit dans
   l'ordre de lecture (à droite pour un texte de droite à gauche) : là où dessiner les poignées,
   et les points à redonner à `selectText` quand l'autre poignée bouge. **La sélection est
   limitée à une page en M4.** Le texte de la dernière page sélectionnée est gardé (une seule
   page) pour que le glissement d'une poignée ne réextraie pas la page à chaque mouvement.
4. **Recherche** : `search(query, options, startPage = 0): Flow<SearchPageResult>`, option C. Un
   `SearchPageResult(pageIndex, hits, isLimitReached)` par page, y compris sans résultat, de
   `startPage` à la dernière page puis de la première à `startPage - 1` : le résultat suivant
   la page affichée arrive en premier. Une requête vide n'émet rien ; un document fermé lève
   `PdfError.Closed`, même pour une requête vide. Les pages sont lues hors du cache de rendu
   (4 pages) : la recherche n'en évince pas les pages affichées. `SearchHit.areas` est une
   liste de `PdfQuad`, comme la sélection (`SearchHit.bounds` donne le rectangle englobant). **Insensible à la casse et aux accents par défaut**
   (`SearchOptions`), **plafond de 1 000 résultats** (`SearchOptions.maxHits`, constante
   `MAX_SEARCH_HITS`) : la dernière émission porte `isLimitReached = true` et les pages
   suivantes ne sont pas parcourues. La recherche passe par `Page.search`, qui extrait le texte
   avec l'option `dehyphenate` et trouve un mot coupé en fin de ligne (le résultat a alors une
   zone par ligne) ; `StructuredText.search` n'est pas utilisable dans MuPDF 1.28.6 (son JNI
   échoue toujours). Les caractères de la page ne sont extraits, pour les extraits, que si elle
   a un résultat. Le JNI de `Page.search` ne plafonne pas le nombre de résultats d'une page
   (vérifié : 800 occurrences sur une page, `search_many_hits.pdf`).
5. **Extrait de contexte** : `SearchHit.excerpt: SearchExcerpt(before, match, after)`, lu sur
   une ligne (lignes jointes par une espace, ou sans rien après un trait d'union, qui disparaît,
   sauf si le résultat finit sur lui), au plus 40 caractères de chaque côté, coupés sur un mot
   entier quand c'est possible. Les caractères retenus sont ceux dont le centre est dans une
   zone du résultat, sans les espaces en bordure ; seules les lignes que touchent les zones sont
   parcourues, et les centres des caractères sont calculés une fois par page. Les résultats
   au-delà du plafond, que la recherche ne rend pas, n'ont pas d'extrait calculé.
6. **Permission de copie** : `PdfDocument.canCopyText`, lue à l'ouverture
   (`Document.hasPermission(PERMISSION_COPY)`). Le moteur la **rapporte sans l'appliquer** :
   `selectText` rend toujours le texte. C'est au lecteur de refuser la copie ; un réglage
   utilisateur permettra plus tard de l'ignorer (hors de ce lot).

## Conséquences

- Positives : le lecteur peut aller à la position exacte d'un lien ou d'une entrée de sommaire,
  afficher la progression d'une recherche et une liste de résultats lisible, sélectionner un
  mot par appui long y compris en arabe ; la logique de sélection et d'extrait est couverte par
  des tests JVM qui tournent partout (`PageTextTest`).
- Négatives / dette acceptée :
  - API changée (`OutlineItem.pageIndex`, `PdfLink.Internal.pageIndex`, `search`, `textAt`) :
    aucun appelant hors de `core:pdf` et des tests (le lecteur ne les utilisait pas encore) ;
    l'interface indicative de `SPEC.md` §4 est mise à jour ;
  - la zone d'une ligne couvre l'étendue de la sélection sur la ligne : une ligne qui mêle
    arabe et latin peut être surlignée un peu au-delà du texte choisi ;
  - le plafond est atteint aussi quand il tombe pile sur le dernier résultat du document : le
    lecteur affichera « 1 000 résultats ou plus » ;
  - une page qui a un résultat est extraite deux fois (par `Page.search`, puis pour les
    extraits) : un peu plus de travail qu'avant, sur le thread du document ;
  - limites connues de la sélection, non corrigées en M4 : en mode `Words`, un texte sans
    espaces entre les mots (chinois, japonais, thaï) sélectionne toute la ligne, faute de
    découpage en mots ; un mot coupé par un trait d'union en fin de ligne n'est sélectionné qu'à
    moitié par un appui long, et une sélection qui le traverse se copie « li-\nbrary » ;
  - sur une page tournée d'un quart de tour, `FitH`, `FitV` et un `XYZ` incomplet mènent en
    haut de la page plutôt qu'à la position voulue, et une destination nommée vers un `FitR`
    n'a pas de taille (bogues de MuPDF 1.28.6, à revoir à chaque mise à jour).
- Actions de suivi :
  - lot 2 (lecteur, « aller à la page ») et lot 4 (liens) : utiliser `PdfDestination`, filtrer
    les schémas ;
  - lot 3 (recherche, sélection) : afficher la progression, les extraits, « 1 000 résultats ou
    plus », bloquer la copie si `canCopyText` est faux ;
  - jeu de test enrichi (`core/testing/README.md`) : lien vers le milieu d'une page, destination
    nommée, liens à refuser, entrée de sommaire externe, `text_selection.pdf` (mot coupé, arabe),
    sommaire du PDF de 600 pages, `rotated_destinations.pdf` (destinations explicites vers une
    page tournée et une page droite), `search_many_hits.pdf` (800 résultats sur une page) ;
  - `FakePdfDocument` suit le moteur (`PageOutOfRange` et `Closed` partout) et peut ralentir la
    recherche (`searchPageDelay`) pour tester la progression et l'annulation ;
  - tests JVM du moteur (`MuPdfDocumentTest`, `MuPdfEncryptionTest`) exécutés en CI Linux, tests
    instrumentés de `core:pdf` sur émulateur.
