# 0018 — Nouveau design visuel : titres en Fraunces, accent chaud, carte de reprise, barre flottante

- **Statut** : accepté
- **Date** : 2026-11-10
- **Décideurs** : le mainteneur (choix du design et des points ci-dessous) ; mise en forme et
  détails techniques par l'agent de codage
- **Références** : `AGENTS.md` §11 ; `SPEC.md` F02–F05, F11, F12, F13, T06 ; `ROADMAP.md` M3, M6 ;
  ADR 0009 (écarts avec les maquettes), 0010 (couleurs dynamiques, police du système), 0011
  (feuille « Affichage » V6), 0015 (vignettes des récents) ; maquettes
  `docs/design/proposals/` (`README.md`, `png/`, `screens/`, `proposal.css`) face à
  `docs/design/screens/E1-*`, `E2-*`, `E9-*`

## Contexte

Les maquettes d'origine (`docs/design/screens/`) sont fidèles à Material 3 mais neutres : titres
en police système, liste uniforme, barre de lecteur pleine largeur avec le curseur par défaut
de Material 3, qui s'avère lourd dans l'application compilée (piste épaisse, poignée verticale,
point d'arrêt). Le 2026-11-10, des propositions avant / après ont été dessinées pour trois écrans :
E1 (bibliothèque), E2 (lecteur) et E9 (progression et résultat d'un outil), en thème clair et
sombre (`docs/design/proposals/`). Elles étaient « non adoptées » ; le mainteneur les a validées.

Adopter ce design touche des choix déjà actés : la police du système (ADR 0010 §4, point ouvert
n° 10), la maquette de référence de trois écrans (`AGENTS.md` §11) et le comportement du curseur
du lecteur. L'ADR 0010 dit que l'identité visuelle ne s'appuie pas sur Roboto embarquée ; elle
s'appuiera ici sur une police de titres, ce qui change la règle. D'où cet ADR.

## Options envisagées

**Portée**
- **A — Tout adopter** : titres en serif, accent chaud, carte de reprise, barre flottante,
  curseur fin, aperçu au glissement, nouveau résultat d'outil.
- **B — Adopter seulement la typographie et les couleurs** (fondations), laisser les écrans
  d'origine : cohérence visuelle faible, l'accent chaud n'aurait pas d'usage.
- **C — Ne rien adopter** : garde la neutralité, mais renonce au caractère voulu par le
  mainteneur.

**Police des titres**
- **Fraunces 600 embarquée** (OFL 1.1) : la police des maquettes ; coût d'environ 71 Ko.
- **Une police du système en serif** (`FontFamily.Serif`) : gratuit, mais le rendu varie d'un
  constructeur à l'autre et ne ressemble pas aux maquettes.
- **Police téléchargeable (Google Fonts)** : impossible, l'application n'a pas et n'aura jamais la
  permission `INTERNET` (`AGENTS.md` §1.2).

**Aperçu de page pendant le glissement du curseur**
- **Toujours actif** : plus simple, mais retire le défilement continu avec le curseur à ceux qui
  le préfèrent.
- **Option, activée par défaut** : garde le comportement actuel pour qui le désactive.
- **Option, désactivée par défaut** : la nouveauté serait cachée.

## Décision

Option **A**, décisions du mainteneur du 2026-11-10 :

1. **Le nouveau design est adopté pour E1, E2 et E9.** Pour ces trois écrans, les maquettes de
   `docs/design/proposals/` sont la référence (elles priment sur `screens/E1-*`, `E2-*`, `E9-*`).
   Les autres écrans gardent leurs maquettes d'origine. Les maquettes ne sont ni déplacées ni
   supprimées. **E9 n'existe pas encore dans le code** (jalon M6) : ses maquettes deviennent la
   référence, sans implémentation maintenant.
2. **Titres en Fraunces 600**, police embarquée dans `core:ui` (TTF statique, poids 600, latin et
   latin étendu, licence OFL 1.1, détails dans `docs/dependencies.md`). Aucun téléchargement à
   l'exécution. Les corps de texte, libellés et métadonnées restent sur la police du système.
   Styles Material 3 concernés : `displayLarge` (56 sp, grand pourcentage), `headlineLarge`
   (32 sp : titre de l'application, « PDF créé »), `headlineSmall` (24 sp : dialogues, écrans
   d'outils), `titleLarge` (22 sp : barres, feuilles, titre de la carte de reprise), plus deux
   variantes de `Typography` : `titleLargeCompact` (20 sp, titre de la barre du lecteur) et
   `titleMediumSerif` (18 sp, légende « Page 6 » de l'aperçu). `titleMedium` (titres de
   lignes de liste) reste en police du système, 500. Cet ADR **amende l'ADR 0010 §4** : la police
   du système reste celle des textes courants, mais les titres ont une police embarquée.
3. **Accent chaud** : les rôles `tertiary*` du schéma de repli passent du violet généré à l'ambre
   de `proposal.css` (clair : `#7C5800` / `#FFDEA6` / `#271900` ; sombre : `#F5BF48` / `#412D00` /
   `#5D4200` / `#FFDEA6`). **Les couleurs dynamiques restent activées par défaut (ADR 0010
   inchangé)** : avec elles, l'accent vient du fond d'écran et n'est chaud que dans le schéma de
   repli (Android < 12, ou interrupteur désactivé). L'identité repose donc surtout sur la
   typographie et les transitions ; aucun écran ne doit dépendre de la teinte ambre pour être
   compris.
4. **E1 — carte « Reprendre la lecture »** pour le dernier document ouvert (toute la carte est la
   cible tactile, vignette plus grande), vignettes de liste 52 × 68 dp (avant 44 × 58 dp), barre
   de progression seulement pour une lecture en cours (ni barre vide ni barre pleine), compteur
   « N autres documents » sous la carte, bouton « Ouvrir un fichier » en couleur primaire (avant
   tonal), titre « Lutrin » en `headlineLarge`.
5. **E2 — barre flottante en pilule** (mêmes commandes qu'aujourd'hui : page, curseur, ajustement ;
   le document défile dessous), **curseur fin** (piste de 4 dp, pouce rond) à la place du
   curseur par défaut de Material 3, **bulle de défilement rapide supprimée** (l'aperçu la
   remplace ; l'ADR 0009 §3 la prévoyait pour M4).
6. **Aperçu de page pendant le glissement : une option.** Activée, le document ne change de page
   qu'au relâchement et un aperçu (vignette de la page visée, « Page N », titre de section si le
   PDF a une table des matières) s'affiche au-dessus de la barre ; désactivée, comportement
   actuel (le document suit le curseur en continu, pas d'aperçu). **Valeur par défaut :
   activée.** Le réglage s'enregistre avec DataStore comme les autres réglages d'affichage
   (ADR 0011). Libellé et description à fixer par l'implémentation (FR et EN, à relire par le
   mainteneur). **Amendé le 2026-11-10 après essai sur appareil** : le réglage quitte la feuille
   « Affichage » du lecteur (V6) pour l'écran Paramètres (E3), section « Lecture », à côté des
   couleurs dynamiques, à la demande du mainteneur ; la feuille V6 ne garde que le mode de
   défilement. La section « Lecture » est celle de la maquette E3 d'origine.
7. **Transition animée vignette → lecteur** : la vignette de la carte de reprise (et des lignes
   de la liste) est l'élément partagé d'une transition vers le lecteur (`SharedTransitionLayout`
   de Compose, aucune dépendance nouvelle). Elle fait partie du design adopté ; une autre étape
   l'implémente. Quand les animations du système sont réduites ou désactivées, l'état final
   s'affiche sans animation. **Réalisée** : voir l'ADR 0019 (retour vers la vignette, geste de
   retour prédictif, cas de repli). **Amendée le 2026-11-10** : après essai sur appareil, l'aller
   est un simple fondu ; seul le retour replie la page vers la vignette.
8. **E9 — progression et résultat** : les feuilles sources glissent sous la feuille centrale,
   pourcentage en grand (`displayLarge`) ; au résultat, la première page du PDF créé (vignette
   réelle) remplace la coche, pastille et mention « Ajouté aux récents » dans l'accent chaud,
   « Ouvrir » passe à 56 dp de haut. Textes et actions inchangés.

### Écarts avec les maquettes de référence d'origine

| Écran | Maquette d'origine | Maquette adoptée |
|-------|--------------------|------------------|
| E1 | Liste uniforme, vignette 44 × 58 dp, titre « Lutrin » 22 sp / 400, « N documents », barre de progression sur chaque ligne, bouton tonal | Carte de reprise, vignette 52 × 68 dp, titre 32 sp Fraunces, « N autres documents », barre seulement en cours de lecture, bouton primaire |
| E2 | Barre inférieure pleine largeur, curseur Material 3 par défaut, bulle de défilement rapide, titre de barre 22 sp / 400 | Barre flottante, curseur fin, pas de bulle, aperçu optionnel, titre 20 sp Fraunces |
| E9 | Coche de réussite, pourcentage courant, titres en police système | Vignette réelle du PDF créé, pourcentage en 56 sp, feuilles qui glissent, pastille chaude, « Ouvrir » 56 dp |

Écarts de l'ADR 0009 modifiés : §5 (titre « Lutrin » 22 sp / 500 : devient 32 sp Fraunces 600) et
§7 (police système partout : désormais seulement pour les textes courants). Les autres écarts
de l'ADR 0009 restent valables.

**Écrans sans maquette dans `proposals/`** : `E1-Bibliotheque-Vide` et `E2-Lecteur-Nuit` restent
la référence du contenu et de la structure de leur état ; ils prennent la typographie et les
principes ci-dessus (titres Fraunces, bouton primaire, barre flottante, curseur fin). L'agent
qui les implémente tranche le détail et le consigne s'il s'écarte de cet ADR. Le comportement de
l'aperçu en lecture page par page (ADR 0011) n'est pas dessiné non plus : il suit la même règle
que le défilement continu (page atteinte au relâchement) ; **confirmé par l'implémentation**
(étape E2, voir « Choix de l'étape E2 ») : le pager ne tourne pas pendant le glissement et va
directement à la page visée au relâchement.

## Conséquences

- **Positives** : une identité reconnaissable sans coût de dépendance Maven ni de permission ; les
  styles typographiques sont des rôles de thème (`MaterialTheme.typography.xxx`), donc les écrans
  suivants n'embarquent aucune valeur en dur ; le curseur allégé corrige un défaut visible.
- **Coûts acceptés** : environ 71 Ko de police dans l'APK (limite de 15 Mo, `SPEC.md` §3) ;
  toutes les captures d'écran de référence changent (police) ; la police ne couvre pas tous les
  scripts (un titre non latin, par exemple un nom de document chinois, retombe sur la police
  du système) ; `AlertDialog` et `TopAppBar` prennent automatiquement les titres en serif.
- **Vignettes (étape E1, 2026-11-10)** : `PdfThumbnailRenderer` dessine désormais la première
  page dans 416 × 552 px (la vignette de 104 × 138 dp de la carte en xxxhdpi, × 4) au lieu de
  176 × 232 px : **un seul fichier par document**, que les lignes (52 × 68 dp) décodent
  sous-échantillonné (`inSampleSize`, jamais plus petit que l'affichage). Coût mesuré sur des
  pages de texte : 60 à 90 Ko de PNG par vignette (contre 9 à 20 Ko), soit 4,5 Mo au plus pour
  50 récents ; en mémoire, environ 0,9 Mo pour la carte et 0,23 Mo par ligne visible en
  xxxhdpi. Les fichiers portent leur format (`<id>-<date>.v2.png`) : une vignette de l'ancien
  format (sans `.v2`) compte comme périmée, la bibliothèque la redessine en la montrant (l'ancienne
  image reste affichée le temps du dessin) et l'ancien fichier est supprimé. Pas de migration
  Room. Ceci **amende l'ADR 0015 §7**.
- **Choix de l'étape E1 (2026-11-10)** :
  - la carte présente toujours le **document le plus récent**, même protégé (page blanche à
    lignes et cadenas, jamais de vignette), d'une seule page ou lu jusqu'au bout ; retirer ce
    document fait monter le suivant dans la carte (« Annuler » le remet, ADR 0016) ;
  - **« Lecture en cours »** = arrêté avant la dernière page d'un document de plusieurs pages
    (`RecentDocument.isReadingInProgress`) : « Page 3 sur 8 » et barre ; sinon « 4 pages · hier »
    ou « 1 page · 19 oct. », sans barre. La page 1 d'un document de plusieurs pages est une
    lecture en cours (« Page 1 sur 2 » de la maquette). Le cas « jamais ouvert » de la maquette
    n'existe pas encore : seule une ouverture réussie entre dans la liste (ADR 0015 §10) ; il
    apparaîtra avec les documents créés par les outils (« Ajouté aux récents », E9, M6) ;
  - **options du document de la carte** : un bouton « ⋮ » (cible de 48 dp, « Plus d'actions
    pour … ») à droite de « Continuer », avec le même menu que les lignes. Il est visible et
    atteignable par TalkBack comme un élément distinct, contrairement à un appui long ;
  - **cible unique** : toute la carte ouvre le document ; « Continuer » n'est qu'un dessin de
    bouton. Sa description (« Reprendre la lecture : Contrat de location, page 3 sur 8 », puis
    « document protégé » s'il y a lieu) est ce que dit TalkBack ; les textes restent fusionnés
    dans la carte pour le contrôle vocal ;
  - titre sur deux lignes au plus, coupé par « … » ; **grande police** (échelle ≥ 1,5) : seuls
    le surtitre et le titre restent à côté de la page, le reste passe dessous sur toute la
    largeur ; les pages (vignettes, page d'attente) restent blanches dans le thème sombre.
  - **Écarts restants avec la maquette E1** : pas d'icônes « grille » ni « outils » (fonctions
    à venir) ; la carte mesure 10 dp de plus (la ligne de « Continuer » a la hauteur du bouton
    « ⋮ », 48 dp) ; le bouton flottant garde la marge de 16 dp de Material 3 (24 dp sur la
    maquette) ; l'icône des paramètres reste l'engrenage de Material.
- **Choix de l'étape E2 (2026-11-10)** :
  - **réglage** : `AppSettings.pagePreview` (DataStore, clé `page_preview`, activé par défaut),
    interrupteur de l'écran Paramètres, section « Lecture » (d'abord placé dans la feuille
    « Affichage », déplacé le 2026-11-10, voir §6) : « Aperçu de la page » / « Montre la page
    pendant que vous faites glisser le curseur du lecteur ; le document s'y rend quand vous
    relâchez. » (EN : « Page preview » / « Shows the page while you drag the slider of the
    reader; the document moves there when you let go. ») ; toute la ligne bascule
    l'interrupteur ;
  - **aperçu activé** (`PageScrubber`) : pendant le glissement, le document reste où il est,
    atténué par un voile du fond du lecteur à 45 % (la maquette l'affiche à 55 % d'opacité ; un
    voile évite une couche hors écran sur la liste), l'indicateur passe à la page visée en
    couleur primaire, le pouce grossit (24 dp dans un halo de 40 dp) et la carte d'aperçu
    s'affiche au-dessus du pouce. Au relâchement, le document va à la page visée sans animation
    (haut de la page en continu, la page en page par page) ; l'indicateur et le pouce gardent la
    page visée jusqu'à l'arrivée, pour ne pas revenir une image sur l'ancienne page. Un appui
    simple sur la piste, les touches du clavier et le réglage de TalkBack (« régler la valeur »)
    vont directement à la page. **Annulation** : un geste annulé par le système (Compose le
    termine par des pointeurs relevés déjà consommés, repérés avant le curseur) ou une barre
    retirée en plein geste ne change pas de page ; relâcher sur la page de départ non plus ;
  - **aperçu désactivé** : comportement d'avant (le document suit le curseur), sans carte ni
    voile ;
  - **carte d'aperçu** (`PagePreviewCard`) : 148 dp de large, page ajustée dans 128 × 180 dp
    (moins dans une fenêtre trop basse, en paysage : la carte tient toujours au-dessus de la
    barre), « Page 6 » en `titleMediumSerif`, centrée sur le pouce mais jamais hors de la barre
    aux extrémités. Le **titre de section** n'est pas affiché : la table des matières arrive au
    jalon M4 ; `FloatingPageBar` a déjà un paramètre optionnel `sectionTitle` que la carte affiche
    sous « Page N ». La carte n'est pas une région « live » : TalkBack lit la page visée comme
    valeur du curseur (« Page 6 sur 8 »), une annonce par page et non par pixel ;
  - **rendu de l'aperçu** : `PageRenderer.renderPreview`, par `PdfDocument` (jamais de type
    MuPDF hors de `core:pdf`), bitmap alloué sur le dispatcher injecté, dessin sur le thread du
    document, à la taille affichée (128 dp de large : 352 × 498 px, 0,7 Mo à 2,75×, 1,5 Mo à 4×).
    Viser une autre page annule le rendu en cours (le `Cookie` de MuPDF interrompt le dessin) ;
    une page déjà rendue s'affiche aussitôt ; pendant le rendu, une page blanche bordée. Les
    aperçus ont leur **propre cache LRU de 8 Mo** (5 à 11 aperçus), séparé de celui des pages
    pour ne jamais en chasser une page affichée ; comme pour les pages, un bitmap sorti du cache
    n'est pas recyclé explicitement (il peut encore être à l'écran) : le ramasse-miettes le
    libère. Un manque de mémoire vide les deux caches. Aucun rendu hors du glissement, et pendant
    le glissement le document est immobile : le défilement (ADR 0012) n'est pas touché ;
  - **barre** : pilule de 64 dp, coins pleins, ombre de 6 dp, à 16 dp des bords de la **zone
    sûre** (barre de navigation, zone des gestes, encoche, côtés en paysage) : elle ne déborde
    jamais sur la zone des gestes du système, où un glissement horizontal changerait
    d'application. Fond `surfaceBright` (le plus clair des fonds dans les deux thèmes, aussi avec
    les couleurs dynamiques) sur le fond du lecteur `surfaceContainerHigh`, inchangé ; indicateur
    en `titleSmall` (14 sp, 500), d'au moins 52 dp ; bouton d'ajustement en `onSurfaceVariant`.
    Document d'une page : même barre, sans curseur. En plein écran, la barre disparaît comme
    l'ancienne. **Fin du document** : les pages reçoivent en bas l'espace de la barre
    (`floatingPageBarClearance()` : marge système du bas + 16 + 64 + 16 dp) ; en continu, la
    dernière page peut défiler au-dessus de la barre ; en page par page, la page à zoom 1 tient
    au-dessus de la barre et, zoomée, peut défiler au-dessus d'elle ;
  - **curseur** : `Slider` de Material 3 avec ses paramètres `thumb` et `track`
    (`SliderDefaults.Track`, encore expérimental en Material 3 1.4, sans point d'arrêt ni
    graduation ni espace autour du pouce, piste de 4 dp `primary` / `primaryContainer`). Le pouce
    est dessiné à 20 dp mais occupe 20 × 48 dp : le curseur de Material a la hauteur de son
    pouce, et c'est ce qui garde la cible tactile à 48 dp. Libellé « Aller à la page », valeur
    « Page x sur n » (nouvelle), réglable au clavier et par TalkBack ;
  - **benchmark** : `:benchmark` laisse désormais un quart de la hauteur de l'écran libre en bas
    des gestes de défilement (`setGestureMargins`), sinon le geste commencerait sur la barre
    flottante et déplacerait le curseur. Les mesures de l'ADR 0012 sont à refaire sur appareil ;
  - **écarts restants avec la maquette E2** : pas d'icônes « Rechercher » ni « Table des
    matières » (M4, ADR 0009 §3), pas de titre de section dans l'aperçu (M4) ; en thème sombre,
    la barre est `surfaceBright` (#36393E) sur le fond actuel du lecteur (#272A2F) au lieu de
    #272A2F sur #0B0E13 : le contraste entre les deux est voisin (1,23 contre 1,34) et le fond du
    lecteur reste celui des jetons ; la barre est à 16 dp au-dessus de la zone sûre et non à
    20 dp du bord de l'écran ; en très grande police, « Page 600 » peut passer sur deux lignes
    dans la carte, de largeur fixe.
- **Contraste** : le contraste des paires ambre a été mesuré
  (blanc sur `#7C5800` : 6,5 ; `#271900` sur `#FFDEA6` : 13,3 ; `#412D00` sur `#F5BF48` : 7,8 ;
  `#FFDEA6` sur `#5D4200` : 7,2), tous au-dessus de 4,5 (WCAG AA).
- **Suite** : E1, E2 (avec le réglage de l'aperçu dans V6 et son test) et la transition
  vignette → lecteur (ADR 0019) sont réalisés par des étapes séparées ; E9 au jalon M6 ; le point ouvert
  n° 10 est mis à jour ; `docs/design/design-tokens.json` est synchronisé avec le thème par un
  test (`DesignTokensTest`) ; les maquettes de `proposals/` gardent leurs fichiers, y compris
  `fonts/` (documentation, jamais embarquée dans l'APK).
