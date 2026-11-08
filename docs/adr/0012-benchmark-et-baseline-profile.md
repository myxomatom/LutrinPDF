# 0012 — Module `:benchmark`, ouverture d'un document sans sélecteur et Baseline Profile

- **Statut** : accepté
- **Date** : 2026-11-05
- **Décideurs** : cadrage par la session d'orchestration ; détails techniques par l'agent de
  codage
- **Références** : `AGENTS.md` §5.1 (ligne « Performance »), §8, §9 ; `ROADMAP.md` M2
  (« 60 fps sur un document de 500 pages, aucun crash mémoire sur le gros PDF de test ») ;
  point ouvert 12 ; ADR 0005 (outillage), 0008 (rendu du lecteur), 0011 (page par page)

## Contexte

`AGENTS.md` demande des mesures de performance (Macrobenchmark) et un Baseline Profile
embarqué ; M2 demande des benchmarks d'ouverture et de défilement. Aucun module ne les portait
(point ouvert 12). Quatre questions se posent :

1. **Outillage** : quelle version de Macrobenchmark et du plugin Gradle `androidx.baselineprofile`
   avec AGP 9.5, Kotlin 2.4 et `compileSdk` 37, sans avertissement (`allWarningsAsErrors`) ?
2. **Ouvrir un document dans un benchmark** : l'application n'ouvre un fichier que par le
   sélecteur système (F01), qu'un benchmark ne sait pas piloter de façon fiable.
   « Ouvrir avec » (`ACTION_VIEW`) est prévu en M3, avec la validation des URI et des types MIME
   d'`AGENTS.md` §9 : le faire maintenant, à la hâte, pour un benchmark serait une mauvaise
   raison.
3. **Gros document de test** : il faut environ 500 pages, sans alourdir le dépôt.
4. **CI** : des mesures sur l'émulateur de la CI (sans accélération graphique fiable, machine
   partagée) n'ont pas de sens.

## Options envisagées

### Ouverture du document

- **A — Implémenter `ACTION_VIEW` dès maintenant** : c'est le travail de M3 ; il ouvre une
  entrée publique de l'application release qui doit être validée avec soin. Refusé.
- **B — Le benchmark pousse le PDF sur l'appareil** (`/data/local/tmp`, stockage partagé) et
  passe son chemin ou un URI à l'application : dépend de SELinux et des règles de stockage selon
  la version d'Android, et l'application devrait accepter un chemin ou un URI venu de dehors.
- **C — Un fournisseur de contenu dans l'APK de test** qui sert le PDF à l'application :
  il faut exporter ce fournisseur, déclarer `<queries>` côté application et lui faire lire un
  URI reçu par intent — encore une entrée à valider.
- **D — Une activité réservée aux builds de benchmark**, qui embarque le PDF de test dans ses
  propres assets et l'ouvre dans le lecteur : aucune donnée reçue de l'extérieur hormis le mode
  de lecture, vérifié contre une liste fermée ; rien de tout cela n'existe dans le build release.

### Build mesuré et génération du profil

- **A — Type de build `benchmark` écrit à la main** (copie de `release`, signé avec la clé de
  debug, `profileable`) et profil copié à la main : il faut aussi un second type de build non
  minifié pour générer le profil (sinon les noms sont obscurcis), c'est-à-dire refaire le plugin.
- **B — Plugin `androidx.baselineprofile` 1.5.0** : il crée `benchmarkRelease` (minifié comme
  `release`) et `nonMinifiedRelease` (génération), les signe avec la clé de debug s'ils n'ont pas
  de signature, les rend `profileable` et non débogables, et fournit
  `:app:generateBaselineProfile`. Il est testé jusqu'à AGP 10.0.0-alpha01 exclu, donc avec
  AGP 9.5, et tolère l'absence de profil.

### Gros document

- **A — Le générer au moment des tests ou par un script** : inutile, il existe déjà.
- **B — Réutiliser `core:testing/src/main/assets/pdfs/large_600pages.pdf`** (600 pages de texte,
  413 Ko, généré par `core/testing/scripts/generate_test_files.py`).

## Décision

1. **Module `:benchmark`** (`com.android.test`, cible `:app`, auto-instrumenté), avec la
   convention `lutrin.android.test` (mêmes niveaux de SDK et réglages du compilateur, ktlint et
   detekt ; ni Kover ni Dokka : il n'y a ni code livré ni API ; AGP ne lance pas Android Lint
   sur un module `com.android.test`). Dépendances : Macrobenchmark
   `benchmark-macro-junit4` 1.5.0 et UiAutomator 2.4.0 (Apache-2.0). Tests :
   - `StartupBenchmark` : démarrage froid (avec et sans compilation préalable) et tiède sur la
     bibliothèque, `StartupTimingMetric` ;
   - `OpenDocumentBenchmark` : ouverture à froid du gros document, `StartupTimingMetric` ;
     **la mesure est `timeToFullDisplayMs`** ;
   - `ScrollBenchmark` : `FrameTimingMetric` en défilement continu (lancers) et en page par page
     (glissements) sur le gros document ;
   - `BaselineProfileGenerator` : démarrage (aussi dans le profil de démarrage), puis lecture du
     gros document dans les deux modes.
2. **Fin de l'ouverture = premières pages rendues** : `PdfPage` (`feature:reader`) appelle
   `ReportDrawnWhen { rendu de base présent }`. L'activité se déclare « entièrement dessinée »
   quand toutes les pages affichées en premier ont leur rendu ; c'est ce que mesure
   `timeToFullDisplayMs`. L'appel est gratuit et sans effet après le premier signalement ou hors
   d'une activité ; il reste dans le build release. Pas de section de trace `androidx.tracing`,
   inutile ici.
3. **Option D pour l'ouverture** : `BenchmarkReaderActivity`, dans `app/src/benchmark/`
   (code et manifeste), ajoutée par l'API des variantes d'AGP **aux seuls types de build
   `benchmarkRelease` et `nonMinifiedRelease`**. Le PDF de `core:testing` est copié dans les
   assets de ces deux builds par la tâche `copyBenchmarkDocument`. L'activité est exportée
   (Macrobenchmark la lance par `am start` depuis le shell), copie le PDF une fois dans
   `cacheDir`, enregistre au besoin le mode de lecture reçu en extra (`CONTINUOUS` / `PAGED`,
   toute autre valeur est ignorée), puis ouvre le lecteur par l'API publique de
   `feature:reader`. Le build release ne contient ni ce code, ni cette activité, ni le PDF.
4. **Option B pour les builds** : plugin `androidx.baselineprofile` 1.5.0 sur `:app` et
   `:benchmark`, `automaticGenerationDuringBuild = false` : la génération n'a lieu que sur
   demande, avec un appareil. Le profil est écrit dans
   `app/src/release/generated/baselineProfiles/` (`baseline-prof.txt` et `startup-prof.txt`) et
   **doit être committé**. `androidx.profileinstaller` 1.4.1 (Apache-2.0) l'installe sur les
   appareils sans Google Play (F-Droid, installation directe). Tant qu'il n'existe pas, le
   build passe sans lui.
5. **Gros document : option B** (600 pages, au-delà des 500 de M2). Aucun binaire ajouté.
6. **CI** : le module est compilé (`./gradlew :benchmark:assemble`, étape dédiée du workflow)
   et vérifié par `check` (ktlint, detekt),
   mais **les benchmarks ne tournent jamais en CI** : sur l'émulateur, les temps n'ont pas de
   sens. Les appareils gérés par Gradle (`api26`, `api37`, groupe `ci`) existent aussi pour
   `:benchmark` par la convention commune, mais aucune tâche de `check` ni du workflow ne les
   lance. Les mesures se font sur un appareil réel, par le mainteneur.

## Conséquences

- Positives : M2 a ses mesures (démarrage, ouverture, 60 images/s) et le Baseline Profile est
  prêt à être généré ; aucune entrée nouvelle dans l'application publiée, aucune permission
  ajoutée (`verifyReleasePermissions` inchangé) ; `ACTION_VIEW` reste pour M3, fait proprement.
- Négatives / dette acceptée :
  - l'ouverture mesurée part d'un fichier (`file://` dans `cacheDir`), pas d'un URI SAF : le
    coût d'un fournisseur de contenu n'est pas compté ;
  - `BenchmarkReaderActivity` n'a pas de test unitaire : ce sont les benchmarks eux-mêmes qui
    l'exercent, sur appareil ;
  - `app/src/benchmark/kotlin` est analysé par detekt et compilé avec les avertissements en
    erreur, mais **ktlint ne le voit pas** (le plugin ktlint ne lit que les ensembles de sources
    du DSL, et ce dossier est ajouté par l'API des variantes). Après une modification de ce
    dossier, le vérifier à la main en ajoutant temporairement
    `android.sourceSets.getByName("main").kotlin.srcDir("src/benchmark/kotlin")` à
    `app/build.gradle.kts`, puis `./gradlew :app:ktlintMainSourceSetCheck`, et retirer la ligne ;
  - `check` contient `verifyReleaseNoBenchmarkEntryPoint`, qui échoue si l'activité de mesure
    apparaît dans le manifeste fusionné du build release ;
  - les builds de mesure ont l'identifiant d'application du build publié
    (`io.github.myxomatom.lutrin`) mais sont signés avec la clé de debug : ils ne s'installent
    pas par-dessus une version publiée de Lutrin, qu'il faut d'abord désinstaller de
    l'appareil de mesure ;
  - le mode de lecture est une préférence globale : chaque benchmark du lecteur le fixe
    explicitement, et une exécution peut laisser l'application de mesure en mode page par page ;
  - les résultats dépendent de l'appareil : il faut noter le modèle avec les chiffres.
- Actions de suivi : le mainteneur lance les benchmarks et génère le profil sur un appareil
  (commandes dans `README.md` et `docs/reprise.md`), committe le profil et reporte les mesures
  dans `ROADMAP.md` §0. Quand M3 ajoutera `ACTION_VIEW`, on pourra mesurer aussi l'ouverture
  par un URI de contenu.
- Documents mis à jour : `docs/open-questions.md` (point 12 tranché), `docs/dependencies.md`,
  `docs/architecture.md`, `README.md`, `CHANGELOG.md`, `ROADMAP.md` §0, `docs/reprise.md`.

## Mesures (2026-11-08)

Appareil : Xiaomi 23013PC75G, Android 15 / HyperOS OS3.0.2.0, build `benchmarkRelease`.
Aucun benchmark n'a planté. Objectif de M2 (P90 < 16 ms, pas de plantage) atteint.

| Benchmark | Résultat |
|-----------|----------|
| `ScrollBenchmark.continuousScroll` (PDF de 600 pages, environ 1924 images) | `frameDurationCpuMs` P50 2,9 / P90 4,0 / P95 6,0 / P99 8,7 ms ; `frameOverrunMs` P90 -2,7 / P99 -1,5 ms |
| `ScrollBenchmark.pageByPage` | `frameDurationCpuMs` P50 3,3 / P90 6,6 / P95 8,8 / P99 11,8 ms ; `frameOverrunMs` P99 0,9 ms |
| `OpenDocumentBenchmark.openLargeDocumentCold` | `timeToFullDisplayMs` médiane 1050 (min 869, max 1578) ; `timeToInitialDisplayMs` médiane 457 ms |
| `StartupBenchmark`, sans profil existant | à froid 369,4 ms, `coldWithoutProfile` 372,5 ms, tiède 129,5 ms (médianes) |
| `StartupBenchmark`, avec le Baseline Profile | `coldStartup` 357,8 ms (min 347, max 396), `coldStartupWithoutProfile` 367,1 ms (min 339,5, max 464,5), tiède 117,7 ms (médianes) |

Le profil réduit le démarrage à froid d'environ 2,5 % en médiane, surtout sur la queue de
distribution (maximum 396 contre 465 ms). Le gain est modeste parce que l'écran de démarrage
(E1, bibliothèque) est petit ; il grandira avec M3.

Le profil généré (`app/src/release/generated/baselineProfiles/baseline-prof.txt`, 17 344 lignes,
et `startup-prof.txt`, 11 320 lignes) contient aussi des règles pour `BenchmarkReaderActivity`,
classe absente du build release : ces règles sont ignorées, sans conséquence.
