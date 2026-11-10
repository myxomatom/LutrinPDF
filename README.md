# Lutrin

**Lecteur PDF libre pour Android — rapide, privé, entièrement hors ligne.**
Aucune publicité, aucun traqueur, aucune permission réseau.

*Lutrin is a free and open-source PDF reader for Android. Fast, private and fully
offline: no ads, no tracking, no network permission. Read, search, merge and convert
PDFs, and let AI assistants drive it on your device. Built with Kotlin and Jetpack Compose.*

> 🚧 **Statut : jalon M3 (fichiers et récents) terminé, M4 (navigation et texte) à venir.** On peut ouvrir un PDF depuis la
> bibliothèque, ou depuis une autre application avec « Ouvrir avec » ou « Partager », et le
> lire : défilement continu ou page par page, zoom, plein écran. La bibliothèque liste les
> documents récents, avec leur vignette, et les rouvre à la page où la lecture s'est arrêtée ;
> un document se retire de la liste (menu « ⋮ » ou écran d'erreur) et la liste se vide depuis
> les paramètres. Un PDF protégé par mot de passe s'ouvre après saisie du mot de passe, qui
> n'est jamais enregistré. État détaillé et écarts avec les maquettes : `ROADMAP.md` §0 et ADR 0009.
>
> Pour essayer : chaque exécution de la CI publie un artefact **`lutrin-debug-apk`** (onglet
> *Actions* de GitHub) ; installez `app-arm64-v8a-debug.apk` sur la plupart des téléphones.

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

**Ouvrir un document depuis une autre application** : Lutrin répond à `ACTION_VIEW` (type
`application/pdf`, URI `content://` seulement) et à `ACTION_SEND` (un PDF dans `EXTRA_STREAM`).
L'extra facultatif `page` (`Int`, à partir de 1) ouvre `ACTION_VIEW` à cette page, par exemple
`adb shell am start -a android.intent.action.VIEW -t application/pdf -d content://… --ei page 12`.
Règles de validation : ADR 0014.

## Documentation du projet

| Fichier | Rôle |
|---------|------|
| [`SPEC.md`](SPEC.md) | Ce que fait l'application : fonctionnalités, architecture, critères d'acceptation |
| [`AGENTS.md`](AGENTS.md) | Comment le code est écrit : règles absolues, tests, documentation, définition de « terminé » |
| [`ROADMAP.md`](ROADMAP.md) | Inventaire des vues, jalons, risques, état d'avancement (§0) |
| [`docs/reprise.md`](docs/reprise.md) | Reprendre le développement : état, prochaines tâches, pièges connus |
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

Prérequis : JDK 21, le SDK Android (variable `ANDROID_HOME`) ; le wrapper télécharge Gradle
et AGP télécharge le NDK. Les tests JVM du moteur compilent aussi MuPDF pour la machine
(Linux seulement) : il faut `make`, un compilateur C, `pkg-config` et les paquets de développement
de freetype2, OpenSSL et zlib (`sudo apt install libfreetype-dev libssl-dev zlib1g-dev pkg-config`).

```bash
./scripts/init-mupdf.sh                # une fois : récupère MuPDF (sous-module épinglé)
./gradlew assembleDebug                # APK de debug (app/build/outputs/apk/debug/)
./gradlew check                        # tout ce que vérifie la CI, voir ci-dessous
./gradlew assembleRelease              # APK de release (non signé)
./gradlew :dokkaGenerate               # documentation (build/dokka/html/)
./gradlew ciGroupDebugAndroidTest      # tests instrumentés sur émulateurs API 26 et 37 (KVM requis)
./gradlew recordRoborazziDebug         # réenregistrer les captures de référence
```

`./gradlew check` lance ktlint, detekt, Android Lint, les tests unitaires, Robolectric et
captures d'écran, les seuils de couverture Kover, la vérification des licences (Licensee)
et l'absence de permission réseau dans le manifeste fusionné (`verifyReleasePermissions`).

Les sources C des polices et motifs de césure embarqués par MuPDF (`libmupdf/generated/`) sont
produites par la tâche Gradle `generateMupdfResources` : ni `make` ni `bash` ne sont
nécessaires pour l'APK (ADR 0013).

### Compiler sous Windows

Prérequis :

- **Git Bash** (Git pour Windows), pour lancer `./gradlew` et `./scripts/init-mupdf.sh` ;
- un dépôt cloné dans un **chemin sans espace** (par exemple `D:\Dev\LutrinPDF`) : ndk-build
  ne gère pas les espaces ;
- le SDK Android (`ANDROID_HOME` ou `local.properties`) ; AGP télécharge le NDK ;
- **JDK 21** pour exécuter Gradle (celui de la CI) : sous JDK 17, les tests Robolectric échouent
  (`Android SDK 36 requires Java 21`) ; sous JDK 25, detekt 1.23.8 échoue
  (`IllegalArgumentException: 25.0.3`, son compilateur Kotlin embarqué ne reconnaît pas ce JDK) ;
- `make` n'est **pas** nécessaire.

Ce qui ne tourne pas sous Windows (ADR 0013) :

- la compilation de MuPDF pour la machine (`buildMupdfHostJni`) et les **tests JVM du moteur**
  de `core:pdf` sont sautés, avec un message ; la CI (Linux) les exécute toujours ;
- le test de comparaison avec `hexdump.sh` se saute si `bash` est introuvable ;
- `DataStoreSettingsRepositoryTest` (`core:data`) tourne sous Robolectric : sur une JVM nue,
  DataStore remplace son fichier avec `File.renameTo`, qui ne peut pas écraser sous Windows.

Pour lancer les benchmarks sur un téléphone Xiaomi / HyperOS, voir les pièges notés dans
[`docs/reprise.md`](docs/reprise.md) §4.

### Mesures de performance et Baseline Profile (sur appareil réel)

Le module `:benchmark` (ADR 0012) mesure le démarrage, l'ouverture du gros PDF de test
(600 pages) et la fluidité du défilement. Il se lance **sur un téléphone branché en USB**
(débogage USB activé, écran allumé et déverrouillé), jamais en CI ni sur un émulateur, dont les
temps n'ont pas de sens. La CI le compile seulement. Les builds de mesure ont le même identifiant
que l'application publiée mais la clé de debug : désinstaller d'abord une version publiée de
Lutrin présente sur le téléphone.

```bash
./gradlew :benchmark:assemble                              # compile les APK de mesure (fait par la CI)
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest  # tous les benchmarks
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.myxomatom.lutrin.benchmark.ScrollBenchmark
./gradlew :app:generateBaselineProfile                     # Baseline Profile (Android 13+, ou 9+ rooté)
```

Résultats : `benchmark/build/outputs/connected_android_test_additional_output/benchmarkRelease/connected/<appareil>/`
(fichiers `*-benchmarkData.json` et traces Perfetto). Le profil généré est écrit dans
`app/src/release/generated/baselineProfiles/` (`baseline-prof.txt`, `startup-prof.txt`) et
doit être committé ; le build fonctionne sans lui.

## Licence

- Le code de ce dépôt est publié sous **GNU GPL v3 ou ultérieure**
  (`SPDX-License-Identifier: GPL-3.0-or-later`) — voir [`LICENSE`](LICENSE).
- L'application distribuée intègre **MuPDF** (Artifex, AGPL v3) ; l'ensemble est donc
  distribué selon les termes de la **GNU AGPL v3** (combinaison permise par la
  section 13 de la GPL v3) — voir [`LICENSES/AGPL-3.0-or-later.txt`](LICENSES/AGPL-3.0-or-later.txt).
- Le code source de chaque version publiée, y compris la version exacte de MuPDF
  utilisée, est disponible dans ce dépôt (tags `vX.Y.Z`).
- Les polices Roboto des maquettes (`docs/design/assets/fonts/`) sont sous licence OFL 1.1.
- La police des titres, **Fraunces** (poids 600, embarquée dans l'application), est sous
  licence OFL 1.1 — voir [`LICENSES/OFL-1.1-Fraunces.txt`](LICENSES/OFL-1.1-Fraunces.txt).
