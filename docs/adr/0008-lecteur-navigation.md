# 0008 — Lecteur : dépendance à `core:pdf` et navigation Compose

- **Statut** : accepté
- **Date** : 2026-11-04
- **Références** : `SPEC.md` §4.3 et §4.4, `AGENTS.md` §3 et §8, ADR 0001 et 0007, `ROADMAP.md` M2

## Contexte

Le lecteur (E2, `feature:reader`) dessine des pages : il a besoin de `PdfEngine` et
`PdfDocument`, qui manipulent des `Bitmap` Android et vivent donc dans `core:pdf`, et non
dans `core:domain` (Kotlin pur). `docs/architecture.md` disait jusqu'ici que « les features ne
connaissent que le domaine ».

L'application passe aussi d'un écran unique à plusieurs destinations (bibliothèque, lecteur,
puis paramètres, outils…). Chaque destination a besoin de son propre `ViewModel`, détruit en
quittant l'écran, pour fermer le document et libérer ses ressources natives.

## Options envisagées

1. **`feature:reader` dépend de `core:pdf`** (interfaces publiques seulement ; tout le code
   MuPDF y est `internal`).
2. Déplacer les interfaces du moteur dans `core:domain` avec un type d'image abstrait : le
   domaine resterait pur, mais chaque rendu demanderait une copie de pixels supplémentaire.
3. Un module `core:pdf-api` séparé : un module de plus pour une seule interface.

Pour la navigation : Navigation Compose (AndroidX, Apache-2.0), Navigation 3 (plus récent,
API encore mouvante), ou une navigation maison (à refaire pour chaque nouvel écran).

## Décision

- Option 1 : `feature:reader` dépend de `core:pdf` en `implementation`. MuPDF reste invisible
  (`:third-party:mupdf` est une dépendance `implementation` de `core:pdf`, ses types et
  l'implémentation `MuPdfEngine` sont `internal`). Les autres features continuent de ne
  dépendre que de `core:domain` et `core:ui`, sauf `feature:tools` qui utilisera `PdfWriter`
  au jalon M6.
- **Navigation Compose 2.10** avec des routes textuelles (`ReaderDestination`) et
  **`hilt-lifecycle-viewmodel-compose`** pour `hiltViewModel()` : chaque entrée de navigation
  a son `ViewModel`, fermé avec elle. Les routes typées (sérialisation Kotlin) pourront venir
  plus tard sans changer les écrans.
- Le titre affiché est le nom du fichier fourni par le système (`OpenableColumns.DISPLAY_NAME`),
  lu dans `:app` (qui a un `Context`) et passé en argument de navigation : le `ViewModel` ne
  touche jamais au `Context`.

## Conséquences

- Positives : rendu direct dans un `Bitmap` sans copie ; un document ouvert par entrée de
  navigation, fermé dans `ReaderViewModel.onCleared()`.
- Négatives : les tests de `feature:reader` utilisent Robolectric (`Uri`, `Bitmap`) ; les
  fakes du moteur (`FakePdfEngine`, `FakePdfDocument`) sont dans `core:testing`, qui dépend
  désormais de `core:pdf`.
- `docs/architecture.md` et `docs/dependencies.md` sont mis à jour.
