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
   activée.** Le réglage se place dans la feuille « Affichage » du lecteur (V6), section
   « Défilement », et s'enregistre avec DataStore comme les autres réglages d'affichage
   (ADR 0011). Libellé et description à fixer par l'implémentation (FR et EN, à relire par le
   mainteneur) ; la maquette de V6 d'origine reste la référence de la feuille.
7. **Transition animée vignette → lecteur** : la vignette de la carte de reprise (et des lignes
   de la liste) est l'élément partagé d'une transition vers le lecteur (`SharedTransitionLayout`
   de Compose, aucune dépendance nouvelle). Elle fait partie du design adopté ; une autre étape
   l'implémente. Quand les animations du système sont réduites ou désactivées, l'état final
   s'affiche sans animation.
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
que le défilement continu (page atteinte au relâchement), à confirmer par l'implémentation.

## Conséquences

- **Positives** : une identité reconnaissable sans coût de dépendance Maven ni de permission ; les
  styles typographiques sont des rôles de thème (`MaterialTheme.typography.xxx`), donc les écrans
  suivants n'embarquent aucune valeur en dur ; le curseur allégé corrige un défaut visible.
- **Coûts acceptés** : environ 71 Ko de police dans l'APK (limite de 15 Mo, `SPEC.md` §3) ;
  toutes les captures d'écran de référence changent (police) ; la police ne couvre pas tous les
  scripts (un titre non latin, par exemple un nom de document chinois, retombe sur la police
  du système) ; `AlertDialog` et `TopAppBar` prennent automatiquement les titres en serif.
- **Point de vigilance** : les vignettes des récents sont dessinées à 44 × 58 dp
  (`PdfThumbnailRenderer`) ; la vignette de la carte de reprise et celles de 52 × 68 dp
  demandent une résolution supérieure (étape E1). Le contraste des paires ambre a été mesuré
  (blanc sur `#7C5800` : 6,5 ; `#271900` sur `#FFDEA6` : 13,3 ; `#412D00` sur `#F5BF48` : 7,8 ;
  `#FFDEA6` sur `#5D4200` : 7,2), tous au-dessus de 4,5 (WCAG AA).
- **Suite** : E1, E2 (avec le réglage de l'aperçu dans V6 et son test) et la transition
  vignette → lecteur sont réalisés par des étapes séparées ; E9 au jalon M6 ; le point ouvert
  n° 10 est mis à jour ; `docs/design/design-tokens.json` est synchronisé avec le thème par un
  test (`DesignTokensTest`) ; les maquettes de `proposals/` gardent leurs fichiers, y compris
  `fonts/` (documentation, jamais embarquée dans l'APK).
