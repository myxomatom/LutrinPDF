# Journal des modifications

Toutes les modifications notables de ce projet sont consignées ici.

Le format suit [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) et le projet
adhère au [versionnage sémantique](https://semver.org/lang/fr/). Les signatures des
fonctions exposées aux assistants (AppFunctions) sont une API publique : tout
changement y est signalé.

## [Non publié]

### Ajouté
- Build sous Windows (ADR 0013) : les sources C des ressources embarquées par MuPDF sont
  générées par une tâche Gradle en Kotlin (`generateMupdfResources`, plugin `lutrin.mupdf`)
  au lieu de `make generate`, avec une sortie identique octet pour octet à `hexdump.sh`
  (vérifié en CI) ; `make` n'est plus nécessaire pour l'APK. Hors Linux, la bibliothèque JNI
  de la machine et les tests JVM du moteur de `core:pdf` sont sautés avec un message. Section
  « Compiler sous Windows » dans le README.
- Baseline Profile généré et embarqué (`app/src/release/generated/baselineProfiles/`), et mesures
  de fin de M2 sur appareil réel (Xiaomi 23013PC75G, Android 15) : défilement continu P90 de
  4,0 ms par image, page par page 6,6 ms, ouverture à froid du PDF de 600 pages en 1050 ms
  (médiane), démarrage à froid 357,8 ms avec le profil contre 367,1 ms sans. Détail dans
  l'ADR 0012.
- Module `:benchmark` (ADR 0012, point ouvert 12) : Macrobenchmark 1.5.0 pour le démarrage à
  froid et tiède, l'ouverture du gros PDF de test (600 pages) jusqu'aux premières pages
  rendues, et la fluidité du défilement continu et page par page ; générateur du Baseline
  Profile (plugin `androidx.baselineprofile`). Lancés sur appareil réel par le mainteneur,
  jamais en CI. Les builds `benchmarkRelease` et `nonMinifiedRelease` ont une activité
  réservée qui ouvre le PDF de test sans sélecteur ; le build release ne la contient pas
  (vérifié par `verifyReleaseNoBenchmarkEntryPoint` dans `check`).
- `androidx.profileinstaller` : le Baseline Profile, une fois généré, est installé aussi hors
  de Google Play.
- Lecteur : l'activité se déclare « entièrement dessinée » quand les premières pages sont
  rendues (`ReportDrawnWhen`), ce qui donne le temps d'ouverture d'un document.
- Lecteur : mode **page par page** horizontal (F03, ADR 0011). Une page par écran, ajustée à
  l'écran, changée d'un glissement ; zoom au pincement et double tap dans la page (le
  glissement entre pages reprend une fois revenu à la page entière) ; curseur, indicateur,
  plein écran et rendu net de la partie visible dans les deux modes. Le changement de mode
  garde la page courante.
- Lecteur : bouton « Plus d'options » et feuille « Affichage » (V6), réduite pour l'instant à
  la section « Défilement » (Continu / Page par page). Le choix est enregistré pour tous les
  documents ; continu par défaut.
- Paramètres (E3), section « Apparence » : interrupteur « Couleurs dynamiques » (Material You,
  Android 12+), activé par défaut ; désactivé, l'application reprend la palette Lutrin des
  maquettes. Ouvert par l'icône engrenage de la bibliothèque (ADR 0010).
- Préférences enregistrées sur l'appareil avec DataStore (`core:data`).
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
- Les 3 tests de `DataStoreSettingsRepositoryTest` passent sous Windows : ils tournent sous
  Robolectric, car DataStore remplace son fichier avec `File.renameTo` (qui n'écrase pas sous
  Windows) quand `Build.VERSION.SDK_INT` vaut 0, comme sur une JVM nue.
- detekt : `jvmTarget` fixé à la version Java du projet (17) ; il échouait quand Gradle
  tourne sous JDK 25 (`Invalid value (25) passed to --jvm-target`). detekt 1.23.8 reste
  incompatible avec JDK 25 pour une autre raison : utiliser JDK 21.
- Build : sous Windows, la compilation native de MuPDF n'échoue plus avec `make (e=87)` à
  l'archivage de `libmupdf_core.a` (ndk-build en commandes courtes, `APP_SHORT_COMMANDS`).
- Lecteur : après un saut de page avec le curseur, l'indicateur et la description TalkBack
  affichaient parfois la page précédente.
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
- CI : les actions GitHub sont épinglées par SHA de commit (et non plus par tag mutable), la version restant en commentaire ; Dependabot mettra ces épinglages à jour.
- APK sans bloc de métadonnées de dépendances chiffré par Google (gênant pour F-Droid).
- `ContentOpener` et `DocumentDispatcherFactory` (core:pdf) passent en `internal` : le moteur PDF n'expose plus ces détails d'implémentation.
- Android Gradle Plugin 9.5.0 → 9.5.1 ; synchronisation Gradle en parallèle dans Android Studio (`org.gradle.tooling.parallel`).

### Sécurité
- JavaScript PDF désactivé à l'ouverture ; messages de MuPDF jamais journalisés.
- Sauvegarde Android et transfert entre appareils désactivés (ADR 0004).
