# Lutrin

**Lecteur PDF libre pour Android — rapide, privé, entièrement hors ligne.**
Aucune publicité, aucun traqueur, aucune permission réseau.

*Lutrin is a free and open-source PDF reader for Android. Fast, private and fully
offline: no ads, no tracking, no network permission. Read, search, merge and convert
PDFs, and let AI assistants drive it on your device. Built with Kotlin and Jetpack Compose.*

> 🚧 **Statut : en préparation.** Le dépôt contient la spécification, les règles de
> développement et les maquettes. Le code arrive avec le jalon M0 (voir `ROADMAP.md`).

---

## Principes

1. Aucune publicité, aucune télémétrie, aucun SDK d'analyse.
2. Aucune permission réseau : l'application fonctionne entièrement hors ligne.
3. Aucune dépendance propriétaire (ni Google Play Services, ni Firebase) : publiable sur F-Droid.
4. Code libre sous GPL-3.0-or-later ; application distribuée selon les termes de l'AGPL v3 (voir [Licence](#licence)).
5. Accès minimal aux fichiers (Storage Access Framework, pas de `MANAGE_EXTERNAL_STORAGE`).

## Fonctionnalités prévues

- **v1.0** — ouverture (sélecteur, « Ouvrir avec », partage), rendu net à tout zoom,
  défilement continu ou page par page, table des matières, recherche, sélection et
  copie, liens, PDF protégés, récents et reprise de lecture, thèmes et mode nuit /
  sépia du document, plein écran, partage, impression, propriétés.
- **v1.1** — outils hors ligne : fusion, extraction de pages, images / texte / HTML
  local vers PDF ; intégration aux assistants IA (AppFunctions, avec repli par
  intents et raccourcis), lecture du contenu **désactivée par défaut**.
- **v1.x** — signets, annotations, formulaires, miniatures, lecture à voix haute, mode tablette.

Détails : [`SPEC.md`](SPEC.md).

## Documentation du projet

| Fichier | Rôle |
|---------|------|
| [`SPEC.md`](SPEC.md) | Ce que fait l'application : fonctionnalités, architecture, critères d'acceptation |
| [`AGENTS.md`](AGENTS.md) | Comment le code est écrit : règles absolues, tests, documentation, définition de « terminé » |
| [`ROADMAP.md`](ROADMAP.md) | Inventaire des vues, jalons, risques |
| [`docs/design/`](docs/design/) | Maquettes des écrans (ouvrir `docs/design/index.html`) et jetons de design |
| [`docs/adr/`](docs/adr/) | Décisions d'architecture (ADR) |
| [`docs/architecture.md`](docs/architecture.md) | Modules et flux de données |
| [`docs/dependencies.md`](docs/dependencies.md) | Dépendances, versions, licences |
| [`docs/open-questions.md`](docs/open-questions.md) | Points ouverts à trancher |
| [`CONTRIBUTING.md`](CONTRIBUTING.md) | Contribuer |
| [`PRIVACY.md`](PRIVACY.md) | Politique de confidentialité |

## Architecture (résumé)

Kotlin, Jetpack Compose, Material 3, Clean Architecture + MVVM (flux unidirectionnel),
Hilt, Room, DataStore, coroutines et Flow. Moteur PDF : **MuPDF**, compilé depuis les
sources et isolé derrière les interfaces `PdfEngine` / `PdfWriter` du module `core:pdf`.
Voir [`docs/architecture.md`](docs/architecture.md) et l'[ADR 0001](docs/adr/0001-moteur-pdf-mupdf.md).

## Compilation et tests

*À compléter au jalon M0.* Les commandes prévues :

```bash
./gradlew assembleDebug        # construire l'APK de debug
./gradlew check                # ktlint, detekt, Android Lint, tests unitaires
./gradlew koverVerify          # seuils de couverture
```

## Licence

- Le code de ce dépôt est publié sous **GNU GPL v3 ou ultérieure**
  (`SPDX-License-Identifier: GPL-3.0-or-later`) — voir [`LICENSE`](LICENSE).
- L'application distribuée intègre **MuPDF** (Artifex, AGPL v3) ; l'ensemble est donc
  distribué selon les termes de la **GNU AGPL v3** (combinaison permise par la
  section 13 de la GPL v3) — voir [`LICENSES/AGPL-3.0-or-later.txt`](LICENSES/AGPL-3.0-or-later.txt).
- Le code source de chaque version publiée, y compris la version exacte de MuPDF
  utilisée, est disponible dans ce dépôt (tags `vX.Y.Z`).
- Les polices Roboto des maquettes (`docs/design/assets/fonts/`) sont sous licence OFL 1.1.
