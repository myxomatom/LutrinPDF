# Journal des modifications

Toutes les modifications notables de ce projet sont consignées ici.

Le format suit [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) et le projet
adhère au [versionnage sémantique](https://semver.org/lang/fr/). Les signatures des
fonctions exposées aux assistants (AppFunctions) sont une API publique : tout
changement y est signalé.

## [Non publié]

### Ajouté
- Jalon M0 : squelette des 11 modules (`SPEC.md` §4.3) et plugins de convention (`build-logic/`).
- Écran d'accueil provisoire (FR / EN) dans le thème Material 3 de Lutrin (`core:ui`),
  couleurs dynamiques sur Android 12+, schémas clair et sombre issus des jetons de design.
- Injection de dépendances Hilt (ADR 0002).
- Qualité : ktlint, detekt (+ règles Compose, KDoc obligatoire), Android Lint sans
  avertissement, `allWarningsAsErrors`, couverture Kover (70 % global, 80 % domain / data), Dokka.
- Vérifications automatiques : licences des dépendances (Licensee, ADR 0003) et absence de
  permission réseau dans le manifeste fusionné (`verifyReleasePermissions`).
- Tests : unitaires, Robolectric, captures d'écran (Roborazzi), instrumentés avec contrôles
  d'accessibilité sur émulateurs gérés API 26 et 37.
- Jeu de fichiers de test généré (`core:testing`, `SPEC.md` §9).
- CI GitHub Actions et Dependabot.
- ADR 0002 à 0006 (Hilt, licences, sauvegarde désactivée, outillage, identifiant d'application).

### Sécurité
- Sauvegarde Android et transfert entre appareils désactivés (ADR 0004).

- Spécification (`SPEC.md`), règles de développement (`AGENTS.md`) et feuille de route (`ROADMAP.md`).
- Maquettes des écrans et jetons de design (`docs/design/`).
- ADR 0001 (moteur PDF MuPDF), accepté, et modèle d'ADR.
- Documents du projet : `README.md`, `CONTRIBUTING.md`, `PRIVACY.md`, `SECURITY.md`,
  `docs/architecture.md`, `docs/dependencies.md`, `docs/open-questions.md`.
- Textes des licences GPL v3 et AGPL v3 (`LICENSES/`).
- Modèles d'issues et de pull request GitHub, `.editorconfig`, `.gitattributes`.
- Métadonnées F-Droid initiales (`fastlane/metadata/android/fr-FR`, `en-US`).
