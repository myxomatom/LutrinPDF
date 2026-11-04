# 0008 — Rendu des pages dans le lecteur

- **Statut** : accepté
- **Date** : 2026-11-04
- **Références** : `SPEC.md` F02–F05, F14 et §3 (performance, mémoire), `AGENTS.md` §8, ADR 0007, `ROADMAP.md` M2

## Contexte

Le lecteur doit être net à tous les niveaux de zoom (25 % à 800 %), défiler à 60 images/s et
ne jamais manquer de mémoire, y compris sur un document de 1 000 pages. Rendre chaque page
entière à la résolution du zoom est impossible au-delà de quelques pages (une page A4 à 800 %
sur un téléphone dépasse 300 Mo).

## Options envisagées

- **A — Grille de tuiles** (comme une carte) : chaque page découpée en carrés rendus selon le
  zoom. Très précis, mais beaucoup de rendus et de bitmaps à gérer, et un découpage visible
  pendant le défilement.
- **B — Rendu de base + détail de la partie visible** (approche de pdf.js) : chaque page est
  rendue une fois en entier, au plus à la largeur de l'écran ; au-delà de « ajuster à la
  largeur », la partie visible est rendue à pleine résolution une fois le geste terminé et
  posée par-dessus.

## Décision

**B**, avec :
- défilement vertical continu dans une `LazyColumn` (les pages hors écran ne sont ni composées
  ni rendues ; leurs rendus en cours sont annulés) ;
- zoom à deux doigts (centroïde fixe) et double tap (×2,5 / retour à la largeur) qui
  redimensionnent la mise en page ; le rendu de base est étiré pendant le geste ;
- rendus de base à quelques largeurs discrètes (huitièmes de l'écran) pour ne pas relancer un
  rendu à chaque image d'un pincement ;
- cache LRU des rendus de base borné à un quart du tas, au plus 128 Mo ; en cas de manque de
  mémoire, le cache est vidé et le rendu sauté (`SPEC.md` §6) ;
- rendu de détail 150 ms après la fin du dernier geste, non mis en cache.

## Conséquences

- Mémoire bornée quel que soit le document ou le zoom : le cache plus un détail par page visible.
- Pendant un pincement au-delà de 100 %, l'image est brièvement floue jusqu'au rendu de détail.
- Toutes les tailles de page sont lues à l'ouverture (`pageSize` sur chaque page) ; pour les très
  gros documents, cela allonge l'ouverture. À mesurer avec les benchmarks (suite de M2) et, si
  besoin, lire les tailles à la demande.
- La copie `Pixmap` → `Bitmap` (ADR 0007) est conservée en attendant ces mesures.
- Restent pour la suite de M2 : ~~mode page par page horizontal (F03)~~ (fait, ADR 0011, qui
  réutilise ce rendu), module `:benchmark` (Macrobenchmark : ouverture, défilement),
  vérification des 60 images/s sur un appareil réel.
