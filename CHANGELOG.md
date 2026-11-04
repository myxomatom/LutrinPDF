# Journal des modifications

Toutes les modifications notables de ce projet sont consignées ici.

Le format suit [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) et le projet
adhère au [versionnage sémantique](https://semver.org/lang/fr/). Les signatures des
fonctions exposées aux assistants (AppFunctions) sont une API publique : tout
changement y est signalé.

## [Non publié]

### Ajouté
- Jalon M2 (première partie) : lecteur (E2). Défilement vertical continu, zoom à deux doigts
  et double tap de 25 % à 800 %, rendu net de la partie visible au-delà de la largeur de
  l'écran, bouton « Ajuster à la largeur », curseur et indicateur de page, plein écran au tap
  (barres système masquées), écran d'erreur (V8) en français et en anglais (ADR 0008).
- Bibliothèque (E1) dans son état vide, comme la maquette : illustration, « Ouvrir un fichier »
  (sélecteur système, PDF seulement) et mention hors ligne. Elle remplace l'écran d'accueil
  provisoire (ADR 0009). Composant partagé `StatusMessage` et dimensions `LutrinDimens` (`core:ui`).
- Rapport d'audit `docs/audits/2026-11-04-audit-m1.md`, ADR 0009 (écarts avec les maquettes),
  points ouverts 9 à 13.
- CI : APK de debug par ABI téléchargeables dans les artefacts de chaque exécution.
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
- Moteur PDF : un échec natif pendant la fermeture d'un document ne fait plus planter
  l'application ; il est intercepté et signalé (type d'exception seul dans le journal).
- Lecteur : le zoom au pincement hésitait entre zoomer et déplacer la page. Le geste gardait
  le zoom de son premier affichage et relançait la mise en page à chaque mouvement ; les pages
  sont désormais agrandies visuellement pendant le geste et le zoom est appliqué une seule fois,
  au lâcher, en gardant sous les doigts le point pincé. Le doigt restant après un pincement ne
  fait plus défiler la page.
- Moteur PDF : plus de fuite du document natif ni du descripteur de fichier quand l'ouverture
  est annulée ou échoue de façon inattendue ; `PdfError.Closed` au lieu d'une annulation
  silencieuse quand un appel croise `close()` ; fermeture robuste des ressources ; l'enveloppe
  `PDFDocument` de MuPDF est détruite à l'ouverture (elle gardait le fichier ouvert jusqu'au GC).
- Dates PDF : forme `Z00'00'` reconnue, décalages horaires invalides refusés.

### Modifié
- APK sans bloc de métadonnées de dépendances chiffré par Google (gênant pour F-Droid).

### Sécurité
- JavaScript PDF désactivé à l'ouverture ; messages de MuPDF jamais journalisés.
- Sauvegarde Android et transfert entre appareils désactivés (ADR 0004).
