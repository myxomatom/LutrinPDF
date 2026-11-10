# Design adopté — E1, E2 et E9 (avant / après)

> **Statut : adopté le 2026-11-10 ([ADR 0018](../../adr/0018-nouveau-design-visuel.md)).**
> Pour **E1 (bibliothèque), E2 (lecteur) et E9 (progression et résultat d'un outil)**, les
> maquettes de ce dossier sont la référence visuelle et **priment** sur `docs/design/screens/E1*`,
> `E2*` et `E9*` (et leurs captures de `docs/design/png/`). Les autres écrans gardent leurs
> maquettes d'origine. `SPEC.md` fait toujours foi (`AGENTS.md` §11) ; tout écart avec ces
> maquettes est documenté dans un ADR. E9 n'est pas encore implémenté (jalon M6).
> Comme le reste de `docs/design/`, ces fichiers ne sont jamais embarqués dans l'APK.

Les trois écrans, avant et après. Les planches « Avant » montrent les anciennes maquettes, qui
restent dans `docs/design/screens/` à titre d'historique. Ouvrir `index.html` dans un navigateur
pour la comparaison complète.

## Contenu

```
docs/design/proposals/
├── README.md        ← ce fichier
├── index.html       ← comparaison avant / après
├── proposal.css     ← styles communs aux écrans proposés
├── screens/*.html   ← un écran par page (hors ligne, sans script)
├── png/*.png        ← captures à 2× (780 × 1688 px) et planches avant-apres-*.png
└── fonts/           ← Fraunces 500 et 600 (OFL 1.1, @fontsource/fraunces 5.3.0)
```

## E1 · Bibliothèque

![E1 avant / après](png/avant-apres-E1.png)

- Carte « Reprendre la lecture » pour le dernier document ouvert ; toute la carte est la
  cible tactile. Sa vignette est l'élément partagé d'une transition vers le lecteur
  (`SharedTransitionLayout` de Compose, aucune dépendance nouvelle).
- Vignettes de 52 × 68 dp au lieu de 44 × 58 dp.
- Barre de progression seulement pour une lecture en cours : plus de barre vide (document
  jamais ouvert) ni de barre pleine (document d'une page).
- Le nombre affiché compte les documents sous la carte (« 5 autres documents »).

## E2 · Lecteur

![E2 avant / après](png/avant-apres-E2.png)

- Barre flottante en pilule : mêmes commandes que la barre actuelle (page, curseur,
  ajustement), le document défile dessous.
- Curseur fin (piste de 4 dp, pouce rond). L'application compilée affiche aujourd'hui le
  curseur par défaut de Material 3 (piste épaisse, poignée verticale, point d'arrêt), plus
  lourd que celui de la maquette de référence ; il se remplace par les paramètres `thumb`
  et `track` de `Slider` dans `PageBar` (`feature/reader/.../ReaderScreen.kt`).
- `E2-Lecteur-Curseur` : pendant le glissement, aperçu de la page visée avec son numéro, et
  le titre de section si le PDF a une table des matières. Le document ne change de page
  qu'au relâchement.
- **Décision du mainteneur (2026-11-10, ADR 0018)** : l'aperçu est une option, **activée par
  défaut**, dans la feuille « Affichage » (V6) du lecteur. Désactivée, le curseur garde le
  comportement actuel (le document suit le curseur en continu, sans aperçu).
- La bulle de défilement rapide à droite disparaît, l'aperçu la remplace.

## E9 · Progression et résultat

![E9 progression avant / après](png/avant-apres-E9-Progression.png)
![E9 résultat avant / après](png/avant-apres-E9-Resultat.png)

- Progression : les feuilles sources glissent sous la feuille centrale ; pourcentage en
  grand. Textes et actions inchangés.
- Résultat : première page du PDF créé (vignette réelle) à la place de la coche ; pastille
  et mention « Ajouté aux récents » dans l'accent chaud ; « Ouvrir » passe à 56 dp.

## Thème sombre

![Thème sombre](png/theme-sombre.png)

Chaque écran a sa variante `*-Sombre` (même page, classe `dark`), avec le schéma sombre de
`design-tokens.json`. Les pages et les vignettes restent blanches : le thème de
l'application ne change pas le rendu du document, qui dépend du réglage « Couleurs du
document » (F13). La barre flottante et l'aperçu utilisent `surfaceContainerHigh` pour se
détacher du fond du lecteur.

## Changements de jetons (appliqués, ADR 0018)

| Jeton | Avant | Maintenant (`design-tokens.json`, `core:ui`) |
|-------|-------|----------------------------------------------|
| Police des titres (`titleLarge`, `headlineSmall`, `headlineLarge`, `displayLarge`) | Roboto 400 | Fraunces 600, embarquée dans `core:ui` |
| `tertiary` / `tertiaryContainer` / `onTertiaryContainer` (clair) | `#6B5778` / `#F3DAFF` / `#523F5F` | `#7C5800` / `#FFDEA6` / `#271900` |
| `tertiary` / `tertiaryContainer` / `onTertiaryContainer` (sombre) | `#D7BEE4` / `#523F5F` / `#F3DAFF` | `#F5BF48` / `#5D4200` / `#FFDEA6` |
| Vignette de liste | 44 × 58 dp | 52 × 68 dp |

## Points relevés à l'adoption

- **Couleurs dynamiques** : sur Android 12+, la palette vient du fond d'écran. L'accent
  chaud n'existe donc que dans le schéma de repli ; l'identité repose surtout sur la
  typographie et les transitions.
- **Schéma sombre** : l'accent chaud sombre (`tertiary` `#F5BF48`, `tertiaryContainer`
  `#5D4200`) est choisi à la main (il correspond aux tons d'une palette ambre de teinte ≈ 82°,
  voir `design-tokens.json`), pas généré avec la graine du reste du schéma.
- **Fraunces** : `fonts/` ne contient que le sous-ensemble latin en WOFF2, pour les maquettes.
  L'application embarque un TTF statique 600 couvrant latin et latin étendu
  (`docs/dependencies.md`).
- **Animations** : l'état fixe s'affiche quand les animations du système sont réduites.
- **Contraste** : mesuré, toutes les paires ambre dépassent 4,5 (ADR 0018).

## Régénérer les captures

Chaque écran est capturé sur l'élément `.phone` (390 × 844) à l'échelle 2, fond
transparent ; les planches `avant-apres-*.png` et `theme-sombre.png` sont les sections de
`index.html`.
