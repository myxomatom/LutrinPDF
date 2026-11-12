# 0023 — Recherche dans le lecteur

- **Statut** : accepté
- **Date** : 2026-11-12
- **Décideurs** : choix fonctionnels par le mainteneur (ouverture par la barre du haut, retour
  qui ferme, options de session, lancement à la validation ou après 400 ms, résultats au fil de
  l'eau depuis la page courante, « 1 000+ », navigation avec bouclage, surlignage dans les deux
  modes, carte d'extrait, annonces TalkBack) ; détails techniques par l'agent de codage
- **Références** : `SPEC.md` F07, F18 ; `AGENTS.md` §3 (MVVM, effets ponctuels, dispatchers
  injectés), §8 (pas de travail lourd en composition), §11 (accessibilité) ; `ROADMAP.md` M4
  (lot 5) ; ADR 0008 (rendu du lecteur), 0011 (page par page), 0018 (nouveau design), 0020 (API
  de recherche du moteur), 0021 (table des matières), 0022 (liens) ; maquette
  `docs/design/screens/V1-Recherche.html`

## Contexte

Depuis l'ADR 0020, `PdfDocument.search(query, options, startPage)` émet un `SearchPageResult`
par page, de la page de départ à la fin puis du début à la page de départ, avec des
occurrences (`SearchHit` : zones `PdfQuad`, extrait `before` / `match` / `after`) et un plafond
de 1 000 résultats. Restait à en faire l'écran V1 : comment lancer la recherche sans bloquer la
frappe ni le fil principal, dans quel ordre numéroter des résultats qui arrivent dans le
désordre, comment les surligner sans alourdir la composition des pages, et comment les annoncer
à TalkBack sans le saturer pendant le parcours d'un long document.

## Options envisagées

### Ordre des résultats
- **A — Ordre de découverte** (depuis la page courante) : le compteur ne bouge pas quand
  arrivent les pages d'avant, mais « 1 / 5 » n'est pas le premier résultat du document.
- **B — Ordre du document** (retenue) : les résultats d'une page sont insérés à leur place ;
  le rang du résultat courant augmente quand arrivent, après le bouclage, ceux des pages
  précédentes. Le compteur final se lit comme dans tout lecteur.

### Résultat courant
- Un index dans la liste : il se décale à chaque insertion avant lui.
- **Une référence (page, rang dans la page)** (retenue, `HitRef`) : stable pendant le parcours ;
  son rang global se calcule par recherche dichotomique.

### Fusion des résultats
- Sur le fil principal, à chaque page : jusqu'à 600 copies de listes sur le fil de l'interface.
- **Sur le dispatcher injecté**, en amont de `flowOn` (retenue) : le fil principal ne reçoit que
  des `SearchResults` immuables, déjà fusionnés.

### Surlignage
- Un calque unique au-dessus de la liste, recalculant la position des pages visibles.
- **Un calque dans chaque `PdfPage`** (retenue, `SearchHighlightLayer`), à la taille affichée :
  il suit la page dans les deux modes, au zoom et pendant un pincement, sans calcul de position.

### Annonces TalkBack
- Une région vivante sur le texte du compteur : annoncée à chaque page qui ajoute un résultat.
- **Une région vivante polie dont la description ne change qu'aux moments utiles** (retenue) :
  « Recherche en cours… » au départ, le nombre de résultats à la fin, puis « Résultat 3 sur 5,
  page 4 » après « suivant » ou « précédent ». Le compteur visible, lui, change à chaque page.

## Décision

1. **Contrôleur** : `SearchController` (`feature:reader`, paquet `search`), détenu par
   `ReaderViewModel` comme `LinkController` (lot 4), expose `state: StateFlow<SearchState>` et
   `effects` (`SearchEffect.Reveal`). Il lance la recherche **400 ms après la dernière frappe**
   (`SEARCH_DEBOUNCE`), **aussitôt** à la validation du clavier ou au changement d'une option,
   et annule toujours la précédente. Une requête vide efface les résultats. La recherche part
   de la **page courante** (bornée au document), les pages sont lues et les résultats fusionnés
   sur le dispatcher injecté (`flowOn`). Fermer la recherche (flèche ou retour) l'annule, vide le
   champ et les surlignages ; la recherche est aussi annulée avec le ViewModel, avant la
   fermeture du document.
2. **Options** « Respecter la casse » et « Respecter les accents », désactivées par défaut,
   gardées dans le contrôleur le temps de la session (ni DataStore ni `SavedStateHandle`).
   L'écart avec la maquette, qui montre « Ignorer les accents » coché, est voulu : les deux
   options ont le même sens (« respecter ») et le même état par défaut.
3. **Résultats** : `SearchResults`, immuable, en ordre du document ; le premier résultat trouvé
   devient le résultat courant et le lecteur y va aussitôt (`Reveal`). Le compteur affiche
   « 2 / 5… » pendant le parcours (avec une barre de progression), « 2 / 5 » à la fin,
   « 2 / 1 000+ » au plafond. « Suivant » et « précédent » bouclent sur les résultats connus.
4. **Centrage** : `PageNavigator.reveal(page, area)`. En défilement continu, le milieu de la
   zone va au milieu de la partie libre de la zone de lecture (hauteur moins la marge de fin
   réservée aux commandes flottantes) ; page par page, la page s'affiche entière, comme `goTo`.
   Au zoom, seul l'axe vertical est recentré.
5. **Surlignage** : `SearchHighlightLayer` dans `PdfPage`, alimenté par `SearchHighlights`
   (recherche dichotomique de la page). Les chemins sont construits dans `drawWithCache`, une
   fois par taille et par ensemble de zones : aucune géométrie en composition. Couleurs des
   jetons `color.reader` (`LutrinReaderColors`, `core:ui`) : la page reste du papier blanc
   quel que soit le thème, donc les couleurs ne suivent pas le schéma ; le résultat courant a en
   plus un contour, pour ne pas reposer sur la seule teinte.
6. **Carte d'extrait** en bas, à la place de la barre flottante : « Page 5 · section » (titre de
   la section si le document a un sommaire, ADR 0021) et l'extrait, la correspondance en gras.
   Couleur de la correspondance choisie selon la luminance de la carte (surface inverse : sombre
   en thème clair, claire en thème sombre), contraste AA dans les deux cas.
7. **États** : recherche en cours (barre de progression, compteur suivi de « … ») ; aucun
   résultat (carte « Aucun résultat » et une phrase sur les documents numérisés) ; erreur
   (« La recherche a échoué », résultats déjà trouvés gardés ; la validation du clavier relance).
8. **Document numérisé sans texte** : il donne simplement 0 résultat. Le détecter demanderait
   d'extraire le texte de chaque page avant ou pendant la recherche (l'API ne dit pas si une
   page a une couche texte) : pas simple ni gratuit sur 600 pages. La carte « Aucun résultat »
   dit donc toujours qu'un document numérisé sans texte ne peut pas être recherché.
9. **TalkBack** : le compteur est une région vivante polie dont la description suit la règle
   ci-dessus ; boutons étiquetés (« Fermer la recherche », « Résultat précédent », « Résultat
   suivant »), puces lues comme sélectionnées ou non ; cibles de 48 dp ; la barre de progression
   est cachée à l'accessibilité (elle répéterait la recherche page par page).
10. **Pendant la recherche**, un toucher sur la page ne passe pas en plein écran (la barre de
    recherche reste visible) ; les liens restent actifs.

## Conséquences

- Positives : résultats visibles dès la première page trouvée, même sur 600 pages ; fil
  principal libre (fusion et recherche hors de lui) ; surlignage exact au zoom, pendant un
  pincement et dans les deux modes ; logique testée sans interface (`SearchControllerTest`,
  `SearchResultsTest`) et écran testé sous Robolectric avec le vrai contrôleur
  (`ReaderSearchTest`), captures V1 (clair, sombre, 200 %, français, aucun résultat).
- Négatives / dette acceptée :
  - le rang du résultat courant peut augmenter pendant le parcours (ordre du document) ;
  - au zoom en défilement continu, un résultat hors de la largeur affichée n'est pas recentré
    horizontalement ; page par page, un résultat de la page déjà zoomée ne déplace pas la vue ;
  - une page du document est recomposée à chaque page qui ajoute des résultats tant que la
    recherche court (le calque seul est redessiné ; mesurer sur appareil avec le PDF de
    600 pages) ;
  - un document numérisé n'est pas signalé comme tel ;
  - les options ne survivent pas à la mort du processus (voulu : session seulement).
- Actions de suivi : essais sur appareil (performance du parcours de `large_600pages.pdf`,
  TalkBack, clavier physique, rotation pendant une recherche), test instrumenté
  `SearchInstrumentedTest` ; liste des résultats (V1 ne la montre pas, F07 ne la demande pas).
