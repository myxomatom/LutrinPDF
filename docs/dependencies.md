# Dépendances

Toute dépendance est déclarée dans `gradle/libs.versions.toml` **et** consignée ici
avant d'être ajoutée (`AGENTS.md` §1.4). Licences acceptées : Apache 2.0, MIT, BSD,
LGPL, GPL v3, AGPL v3. Refusées : propriétaires, non commerciales, SDK fermés,
Google Play Services, Firebase, et toute bibliothèque qui ajoute une permission réseau.

| Dépendance | Version | Licence (SPDX) | Module(s) | Justification | Vérifiée le |
|------------|---------|----------------|-----------|---------------|-------------|
| MuPDF (Artifex) | à épingler (M1) | AGPL-3.0 — identifiant exact à vérifier | `core:pdf` | Moteur de rendu et d'écriture PDF (ADR 0001) | — |

## Ressources de documentation (non embarquées)

| Ressource | Licence | Emplacement |
|-----------|---------|-------------|
| Roboto, Roboto Mono | OFL-1.1 | `docs/design/assets/fonts/` |
