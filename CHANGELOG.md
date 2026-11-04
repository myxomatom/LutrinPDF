# Journal des modifications

Toutes les modifications notables de ce projet sont consignées ici.

Le format suit [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) et le projet
adhère au [versionnage sémantique](https://semver.org/lang/fr/). Les signatures des
fonctions exposées aux assistants (AppFunctions) sont une API publique : tout
changement y est signalé.

## [Non publié]

### Ajouté
- Jalon M2 (en cours) : lecteur (E2) en défilement continu, zoom au pincement et au double tap
  (25 à 800 %), « Ajuster à la largeur », plein écran au tap (barres système masquées),
  curseur et indicateur de page « n / total » ; cache de pages borné en mémoire et annulation
  du rendu des pages sorties de l'écran.
- Vue d'erreur (V8) : document illisible, indisponible ou protégé par mot de passe (FR / EN).
- Bibliothèque (E1) dans son état vide, avec « Ouvrir un fichier » (sélecteur système, PDF
  seulement) et la mention hors ligne ; elle remplace l'écran d'accueil provisoire.
- Navigation entre écrans (Navigation Compose, ADR 0008) ; composants partagés `StatusMessage`,
  `ErrorIllustration`, dimensions `LutrinDimens` et couleurs du lecteur `ReaderColors` (`core:ui`).
- Fakes du moteur `FakePdfEngine` / `FakePdfDocument` (`core:testing`).
- ADR 0008 (lecteur et navigation) et 0009 (écarts assumés avec les maquettes), rapport
  d'audit `docs/audits/2026-11-04-audit-m1.md`, état d'avancement dans `ROADMAP.md` §0.
- Jalon M1 : moteur PDF. MuPDF 1.28.6 compilé depuis son sous-module Git (NDK, 3 ABI) dans
  `:third-party:mupdf` (ADR 0007).
- `PdfEngine` / `PdfDocument` (`core:pdf`) : ouverture (fichier, URI de contenu, mémoire),
  mot de passe, métadonnées, tailles de page, rendu d'une région (mode nuit), table des
  matières, liens, recherche (casse et accents), extraction du texte d'une sélection.
- Modèles et erreurs du domaine (`core:domain`) : `PdfError`, `PdfRect`, `OutlineItem`,
  `SearchHit`… et analyse des dates PDF.
- Tests du moteur sur tout le jeu de fichiers de test, sur la JVM (bibliothèque MuPDF hôte)
  et instrumentés ; vérification précoce de la fusion et de la conversion d'image en PDF.
- APK de release par ABI (arm64-v8a, armeabi-v7a, x86_64), codes de version distincts.
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
- Spécification (`SPEC.md`), règles de développement (`AGENTS.md`) et feuille de route (`ROADMAP.md`).
- Maquettes des écrans et jetons de design (`docs/design/`).
- ADR 0001 (moteur PDF MuPDF), accepté, et modèle d'ADR.
- Documents du projet : `README.md`, `CONTRIBUTING.md`, `PRIVACY.md`, `SECURITY.md`,
  `docs/architecture.md`, `docs/dependencies.md`, `docs/open-questions.md`.
- Textes des licences GPL v3 et AGPL v3 (`LICENSES/`).
- Modèles d'issues et de pull request GitHub, `.editorconfig`, `.gitattributes`.
- Métadonnées F-Droid initiales (`fastlane/metadata/android/fr-FR`, `en-US`).

### Corrigé
- Moteur PDF : plus de fuite du document natif ni du descripteur de fichier quand l'ouverture
  est annulée ou échoue de façon inattendue ; `PdfError.Closed` au lieu d'une annulation
  silencieuse quand un appel croise `close()` ; fermeture robuste des ressources.
- Dates PDF : forme `Z00'00'` reconnue, décalages horaires invalides refusés.

### Modifié
- APK sans bloc de métadonnées de dépendances chiffré par Google (gênant pour F-Droid).
- Dependabot suit aussi le sous-module MuPDF.

### Sécurité
- JavaScript PDF désactivé à l'ouverture ; messages de MuPDF jamais journalisés.
- Sauvegarde Android et transfert entre appareils désactivés (ADR 0004).
