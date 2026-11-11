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
   plus proche de la ligne la plus proche (c'est au lecteur de décider qu'un appui long dans une
   marge ne sélectionne rien). **La sélection est limitée à une page en M4.** Le texte de la
   dernière page sélectionnée est gardé (une seule page) pour que le glissement d'une poignée ne
   réextraie pas la page à chaque mouvement.
4. **Recherche** : `search(query, options): Flow<SearchPageResult>`, option C. Un
   `SearchPageResult(pageIndex, hits, isLimitReached)` par page, dans l'ordre, y compris sans
   résultat ; une requête vide n'émet rien. **Insensible à la casse et aux accents par défaut**
   (`SearchOptions`), **plafond de 1 000 résultats** (`SearchOptions.maxHits`, constante
   `MAX_SEARCH_HITS`) : la dernière émission porte `isLimitReached = true` et les pages
   suivantes ne sont pas parcourues. Le texte structuré est extrait avec l'option `dehyphenate`,
   comme la recherche de MuPDF, pour trouver un mot coupé en fin de ligne ; le résultat a alors
   une zone par ligne.
5. **Extrait de contexte** : `SearchHit.excerpt: SearchExcerpt(before, match, after)`, lu sur
   une ligne (lignes jointes par une espace, ou sans rien après un trait d'union, qui disparaît),
   au plus 40 caractères de chaque côté, coupés sur un mot entier quand c'est possible. Les
   caractères retenus sont ceux dont le centre est dans une zone du résultat, sans les espaces
   en bordure.
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
  - la recherche extrait le texte de chaque page (et ses caractères quand elle y trouve un
    résultat) : un peu plus de travail qu'avant, sur le thread du document.
- Actions de suivi :
  - lot 2 (lecteur, « aller à la page ») et lot 4 (liens) : utiliser `PdfDestination`, filtrer
    les schémas ;
  - lot 3 (recherche, sélection) : afficher la progression, les extraits, « 1 000 résultats ou
    plus », bloquer la copie si `canCopyText` est faux ;
  - jeu de test enrichi (`core/testing/README.md`) : lien vers le milieu d'une page, destination
    nommée, liens à refuser, entrée de sommaire externe, `text_selection.pdf` (mot coupé, arabe),
    sommaire du PDF de 600 pages ;
  - tests JVM du moteur (`MuPdfDocumentTest`, `MuPdfEncryptionTest`) exécutés en CI Linux, tests
    instrumentés de `core:pdf` sur émulateur.
