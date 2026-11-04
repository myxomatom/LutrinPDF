# 0010 — Couleurs dynamiques désactivables et police du système

- **Statut** : accepté
- **Date** : 2026-11-04
- **Références** : `SPEC.md` F13 et §4.1, §5 ; ADR 0004 et 0009 ; points ouverts 9 et 10

## Contexte

`SPEC.md` §4.1 prévoit les couleurs dynamiques (Material You) : sur Android 12+, la palette
est tirée du fond d'écran et remplace celle des jetons de design (`#2A5B8C`…). L'application
ne ressemble alors aux maquettes que sous Android 11 ou moins (ADR 0009). Les maquettes
utilisent aussi Roboto, alors que l'application utilise la police du système, qui n'est pas
Roboto chez tous les constructeurs.

## Options envisagées

Couleurs : toujours dynamiques (SPEC actuelle) ; jamais (identité visuelle fixe) ; un
interrupteur dans les paramètres, activé ou désactivé par défaut.

Police : embarquer Roboto (OFL 1.1, environ 0,3 à 0,5 Mo pour les graisses utilisées,
identique aux maquettes partout, mais ignore la police choisie par l'utilisateur) ; garder la
police du système.

## Décision

Décisions du mainteneur du 2026-11-04 :

1. **Interrupteur « Couleurs dynamiques »**, section « Apparence » des paramètres (E3),
   **activé par défaut**. Désactivé, l'application utilise la palette Lutrin. Avant Android 12,
   l'interrupteur est affiché désactivé, avec la raison (« Disponible à partir d'Android 12 »).
   `SPEC.md` F13 est complété en conséquence.
2. Le réglage est stocké avec **DataStore Preferences** (`core:data`, `SPEC.md` §5), dans les
   fichiers privés de l'application, jamais sauvegardés (ADR 0004). Il est appliqué à toute
   l'application dès qu'il change (`MainActivity` observe les paramètres).
3. **E3 en avance sur M5** : l'écran Paramètres existe désormais avec la seule section
   « Apparence » ; il est ouvert par l'icône engrenage de la bibliothèque (E1), comme dans la
   maquette. Les autres réglages (thème, rendu du document, lecture, données) arrivent en M5.
4. **Police du système conservée** : pas de Roboto embarquée. L'APK ne grossit pas et la
   police choisie par l'utilisateur est respectée ; sur Pixel et Android de base, c'est Roboto.

## Conséquences

- `core:domain` gagne `AppSettings`, `SettingsRepository` et deux cas d'usage ;
  `core:data` son premier code (`DataStoreSettingsRepository`), donc son seuil de couverture
  de 80 % devient effectif.
- Nouvelle dépendance : `androidx.datastore:datastore-preferences` (Apache-2.0).
- Les captures d'écran de référence restent faites avec la palette Lutrin (`dynamicColor = false`).
- Points ouverts 9 et 10 tranchés.
