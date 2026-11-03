# Contribuer à Lutrin

Merci de votre intérêt ! Avant toute contribution, lisez :

- [`SPEC.md`](SPEC.md) — périmètre et exigences (ce qui est hors périmètre sera refusé : édition du contenu des PDF, conversion bureautique, cloud, comptes) ;
- [`AGENTS.md`](AGENTS.md) — règles de code, de tests et de documentation (elles s'appliquent aussi aux contributions humaines).

## Règles non négociables

- Aucune publicité, télémétrie, SDK d'analyse ou rapport de crash distant.
- Aucune permission `INTERNET` / `ACCESS_NETWORK_STATE`, ni dépendance qui l'ajouterait.
- Aucune dépendance propriétaire ; toute dépendance doit être compatible GPL v3 / AGPL v3
  et être consignée dans [`docs/dependencies.md`](docs/dependencies.md).
- Chaque fichier source commence par `SPDX-License-Identifier: GPL-3.0-or-later`.
- Pas de code sans tests ni documentation.

## Flux de travail

1. Ouvrez (ou commentez) une *issue* pour discuter du changement.
2. Créez une branche : `feat/…`, `fix/…`, `docs/…` — une fonctionnalité = une branche = une PR.
3. Commits au format [Conventional Commits](https://www.conventionalcommits.org/fr/), en anglais, à l'impératif
   (`feat(reader): add double-tap zoom`).
4. Vérifiez localement : `./gradlew check` (disponible à partir du jalon M0).
5. Mettez à jour `CHANGELOG.md` (section *Non publié*) et la documentation concernée.
6. Ouvrez la PR en remplissant le modèle (définition de « terminé », `AGENTS.md` §12).

Une décision structurante (dépendance, architecture, écart à la spec ou aux maquettes)
fait l'objet d'un ADR dans [`docs/adr/`](docs/adr/) à partir de `0000-modele.md`.

## Traductions

Toutes les chaînes visibles sont dans `strings.xml`, en français et en anglais au minimum.

## Licence des contributions

En contribuant, vous acceptez que votre contribution soit publiée sous
GPL-3.0-or-later (et distribuée, dans l'application, selon les termes de l'AGPL v3).
