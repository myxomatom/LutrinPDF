# 0011 — Mode de lecture page par page et options d'affichage

- **Statut** : accepté
- **Date** : 2026-11-04
- **Décideurs** : choix produit fixés par la session d'orchestration ; détails techniques par
  l'agent de codage
- **Références** : `SPEC.md` F03, F04, F05, F14 et §5 ; `ROADMAP.md` M2 ; ADR 0008 (rendu du
  lecteur), 0009 (écarts avec les maquettes), 0010 (paramètres DataStore) ; maquettes
  `docs/design/png/E2-Lecteur.png` et `V6-Options.png`

## Contexte

F03 prévoit, en plus du défilement vertical continu, un mode page par page horizontal en
option. C'est le dernier élément fonctionnel du lecteur de M2. Il faut décider où l'utilisateur
choisit le mode, comment ce choix est enregistré, et comment le mode page par page cohabite avec
le zoom de 25 à 800 % (F04), le curseur et l'indicateur de page (F05), le plein écran (F14) et
le rendu net de la partie visible (ADR 0008).

## Options envisagées

**Emplacement du choix**
- **A — Paramètres (E3) seulement** : cohérent avec `SPEC.md` §4 (« mode de défilement » dans
  les paramètres), mais il faut quitter le document pour changer de mode.
- **B — Options d'affichage du lecteur (V6)**, ouvertes par « Plus d'options » de la barre du
  lecteur (maquette E2) : le changement se voit tout de suite, sur le document ouvert.

**Portée du choix** : par document (avec la reprise de lecture, F12, en M3) ou préférence
globale.

**Zoom dans le mode page par page**
- **A — Changer de page au bord** : zoomé, le glissement déplace la page, et ce qui dépasse
  au bord de la page fait tourner la page (défilement imbriqué vers le `HorizontalPager`).
  Naturel, mais un geste en diagonale peut à la fois déplacer la page et commencer à tourner,
  la page voisine apparaît zoomée pendant le glissement, et le comportement dépend des
  détails du défilement imbriqué de Compose.
- **B — Pas de changement de page tant que la page est zoomée** : zoomé au-delà de
  « page entière », les glissements ne font que déplacer la page ; un double tap ou le bouton
  « Ajuster la page à l'écran » la ramène, et le glissement entre pages revient. Le curseur
  change toujours de page.

## Décision

1. **Choix dans V6** (option B) : « Plus d'options » (icône ⋮, description « Plus d'options »)
   apparaît dans la barre du lecteur, comme dans la maquette E2. Il ouvre une feuille modale
   « Affichage » reprise de V6, réduite pour M2 à la section **« Défilement »** : boutons
   segmentés « Continu » / « Page par page ». La feuille reste ouverte après un choix ; le
   document change de mode derrière elle. Les autres sections de V6 (« Ajustement »,
   « Couleurs du document », « Plein écran », « Verrouiller l'orientation ») sont
   **volontairement absentes** jusqu'à M5, avec leurs fonctions. Cette décision complète le
   point 3 de l'ADR 0009 pour l'action « Plus d'options ». Le réglage rejoindra aussi E3 avec
   la section « Lecture » (M5).
2. **Préférence globale**, la même pour tous les documents, **continu par défaut**. Elle est
   enregistrée avec les autres paramètres (ADR 0010) : `AppSettings.scrollMode`
   (`ScrollMode.CONTINUOUS` / `PAGED`, `core:domain`), `SetScrollModeUseCase`,
   `DataStoreSettingsRepository` (clé `scroll_mode`, valeurs écrites en toutes lettres
   `continuous` / `paged` pour qu'un renommage dans le code ne réinitialise pas le choix ; une
   valeur inconnue se lit comme le défaut).
3. **Mode page par page** : un `HorizontalPager`, une page par écran, **page entière ajustée à
   l'écran** avec une marge de 16 dp (le zoom 1 vaut « page entière », alors qu'il vaut
   « largeur » en continu). Le bouton de la barre du bas ramène le zoom à 1 dans les deux
   modes ; son libellé devient « Ajuster la page à l'écran » en page par page.
4. **Zoom** : option B. Pincement et double tap (mêmes gestes, partagés avec le mode continu)
   agissent dans la page, de 25 à 800 % de « page entière ». Zoomé au-delà de 1, la page se
   déplace dans les deux sens et le glissement entre pages est désactivé ; à 1 ou moins, il
   est actif. Le zoom revient à 1 dès qu'une autre page est atteinte (glissement ou curseur),
   et au changement de mode (le zoom 1 n'a pas le même sens dans les deux modes). L'option A
   pourra être reprise après des essais sur appareil.
5. **Surface zoomable** : l'écran multiplié par le zoom, page et marges comprises, la page
   centrée. Tout grandit avec le zoom, ce qui garde sous les doigts le point pincé avec le même
   calcul qu'en continu (`scrollAfterGesture`).
6. **Rendu** : aucun code de rendu nouveau. Chaque page passe par le même composable que le
   mode continu (`PdfPage`) : rendu de base au plus à la largeur ajustée (cache LRU
   `BitmapCache`), rendu net de la partie visible 150 ms après le dernier geste (ADR 0008).
   Seules les pages à l'écran sont composées (deux pendant un glissement) : une page qui quitte
   l'écran annule ses rendus (`AGENTS.md` §8).
7. **Page courante** : l'écran signale la page affichée au `ReaderViewModel`, qui la garde dans
   `ReaderUiState.Ready.currentPage` ; au changement de mode, la nouvelle vue s'ouvre sur cette
   page. L'indicateur, le curseur et le plein écran passent par une même abstraction
   (`PageNavigator`) dans les deux modes.
8. **Sens de lecture** : les pages se suivent toujours de gauche à droite, même si
   l'interface est de droite à gauche (`supportsRtl`) : le sens de lecture appartient au
   document, pas à la langue de l'application. Pas de mode droite à gauche (mangas, documents
   en arabe ou en hébreu) pour l'instant.

## Conséquences

- Positives : F03 est complet ; le mode continu reste le défaut, rien ne change pour qui ne
  touche pas aux options. Les gestes et le rendu sont partagés entre les deux modes : un seul
  endroit à corriger. La page courante connue du ViewModel servira à la reprise de lecture
  (F12, M3).
- Négatives / dette acceptée :
  - zoomé, il faut revenir à la page entière pour tourner la page au doigt ;
  - à fort zoom, on peut déplacer la vue jusque dans les bandes vides autour d'une page dont
    les proportions diffèrent de celles de l'écran ;
  - la feuille V6 n'a qu'une section jusqu'à M5 ;
  - pas de lecture de droite à gauche.
- Correction trouvée en chemin : après un saut de page (curseur, changement de mode),
  l'indicateur du mode continu pouvait afficher la page précédente ; il lit désormais la page
  à laquelle appartient le décalage de défilement.
- À vérifier sur appareil réel : fluidité du glissement sur un gros document, enchaînement
  pincement / glissement, rendu net après un zoom en page par page.
- Documents mis à jour : `ROADMAP.md` §0, ADR 0008
  (« Restent pour la suite »), `CHANGELOG.md`, `docs/architecture.md`, `README.md`.
