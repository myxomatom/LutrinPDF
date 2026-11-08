# Reprendre le développement

> Point d'entrée d'une nouvelle session (personne ou agent). Mis à jour à la fin de chaque
> session de travail. **Dernière mise à jour : 2026-11-08.**
> L'état détaillé par jalon et par vue est dans [`ROADMAP.md` §0](../ROADMAP.md) ; ce fichier dit
> où on en est, ce qui reste à faire en premier, et les pièges déjà rencontrés.

## 1. Où en est le projet

| Jalon | État |
|-------|------|
| M0 Fondations | ✅ Terminé (sauf la vérification de reproductibilité F-Droid, reportée) |
| M1 Moteur PDF | ✅ Terminé, audit du 2026-11-04 corrigé |
| M2 Lecteur de base | ✅ Terminé : benchmarks mesurés sur appareil le 2026-11-08 (P90 4,0 ms en défilement continu), Baseline Profile committé (ADR 0012) |
| M3 à M8 | ⏳ À faire (E1 et E3 existent déjà en partie) |

Ce qui est dans `main` et fonctionne sur appareil (vérifié par le mainteneur avec l'APK de debug) :

- **E1 Bibliothèque**, état vide de la maquette : « Ouvrir un fichier » (sélecteur système, PDF
  seulement), icône Paramètres. Pas encore de récents (M3).
- **E2 Lecteur** : défilement continu, zoom au pincement et double tap de 25 à 800 % (corrigé
  dans la PR #9), rendu net de la partie visible, plein écran au tap, curseur et indicateur de page.
- **V8 Erreur** : document endommagé, non PDF, inaccessible, protégé par mot de passe.
- **E3 Paramètres**, section « Apparence » seulement : interrupteur « Couleurs dynamiques »,
  activé par défaut, enregistré avec DataStore (ADR 0010).
- Moteur MuPDF 1.28.6 compilé depuis les sources, sans fuite à l'ouverture ni à la fermeture.

Sur la branche de la session suivante, testé seulement sous Robolectric : **mode page par page**
(F03, ADR 0011) et feuille « Affichage » (V6, section « Défilement » seulement) ouverte par
« Plus d'options », choix enregistré avec DataStore. **À essayer sur appareil en priorité** :
glissement entre pages, zoom dans la page, rendu net après un zoom, changement de mode.

Module `:benchmark` (ADR 0012, exécuté le 2026-11-08 sur un Xiaomi 23013PC75G sous Android 15,
chiffres dans l'ADR) : Macrobenchmark (démarrage, ouverture du PDF de 600 pages, défilement continu
et page par page) et générateur du Baseline Profile. Les builds `benchmarkRelease` /
`nonMinifiedRelease` de `:app` ont une activité réservée, `BenchmarkReaderActivity`
(`app/src/benchmark/`), qui ouvre le PDF de test embarqué sans sélecteur ; `check` vérifie
qu'elle n'est pas dans le build release.

Historique des PR : #4 M1, #6 lecteur, #7 audit et bibliothèque, #9 zoom et Dependabot,
#10 fermeture sans plantage, #11 E3 et ce fichier. La branche suivante apporte le mode page
par page, l'épinglage des actions GitHub par SHA (B9) et la visibilité `internal` (C13).

## 2. Prochaines tâches, dans l'ordre conseillé

1. **M3 Fichiers et récents** :
   - « Ouvrir avec » et partage entrant (`ACTION_VIEW`, `ACTION_SEND`), avec validation des
     URI et des types MIME (`AGENTS.md` §9) ;
   - permissions persistantes, récents avec vignettes (Room), reprise de la dernière page ;
   - dialogue de mot de passe (V4), « Retirer des récents » dans V8 ;
   - repli par copie dans `cacheDir` pour les URI non seekables (point ouvert 11).
2. **Windows** : `DataStoreSettingsRepositoryTest` (3 tests, `core:data`) échoue sous Windows
   seulement (`Unable to rename ...preferences_pb.tmp`) ; décision du mainteneur attendue.
3. Petits restes de l'audit (`docs/audits/2026-11-04-audit-m1.md`, lignes ⏳) :
   - tester la build minifiée (R8) ;
   - tester `verifyReleasePermissions` de bout en bout ;

## 3. Décisions à connaître

- Toutes les décisions sont dans `docs/adr/` (0001 à 0012) et dans les points tranchés de
  `docs/open-questions.md`. Les plus récentes :
  - **ADR 0008** : rendu du lecteur (rendu de base et détail de la partie visible) ;
  - **ADR 0009** : écarts assumés avec les maquettes ;
  - **ADR 0010** : couleurs dynamiques désactivables, activées par défaut ; police du système
    conservée (pas de Roboto embarquée) ;
  - **ADR 0012** : module `:benchmark`, activité d'ouverture réservée aux builds de mesure,
    plugin `androidx.baselineprofile`, pas de benchmark en CI ;
  - **ADR 0011** : mode page par page (préférence globale, choisie dans V6), pas de changement
    de page tant que la page est zoomée, pages toujours de gauche à droite.
- Fermeture d'un document : un échec natif est intercepté et signalé, jamais relancé (point 15).
- MuPDF reste épinglé sur un tag publié ; Dependabot ne le suit pas (point ouvert 14).
- Points encore ouverts : 1, 2, 3, 6, 8, 11, 13 et 14 (`docs/open-questions.md`).

## 4. Pièges déjà rencontrés

- **Une seule session de développement à la fois.** Deux sessions ont réalisé le même lecteur
  en parallèle (PR #6 et une branche abandonnée), avec des conflits et deux ADR 0008. Avant de
  commencer, vérifier les PR ouvertes et les branches `ccr-*` récentes.
- **`./gradlew check` ne suffit pas** : la CI lance aussi `assembleRelease` et `:dokkaGenerate`.
  Dokka échoue sur toute déclaration publique sans KDoc, y compris les `override` des fakes de
  `core:testing` (PR #11). Avant de pousser :
  `./gradlew check assembleRelease :dokkaGenerate`.
- **Merger une PR seulement quand sa CI est verte.** Une PR mergée avant la fin de sa CI ne
  prouve rien.
- **CI GitHub** :
  - l'émulateur API 26 ne démarre pas toujours (`EmulatorStartFailedException`) : relancer le
    job avant de chercher un bug ;
  - une file d'attente sans fin vient souvent d'un incident GitHub Actions
    (https://www.githubstatus.com).
- **Dependabot sur le sous-module MuPDF** : il propose la pointe de la branche de développement,
  refusée par `verifyMupdfVersion`. Fermer ces PR sans les merger (suivi retiré de
  `.github/dependabot.yml`).
- **Tests Compose sous Robolectric** :
  - `waitForIdle()` n'avance pas l'horloge à travers un `delay()` (rendu de détail après
    150 ms) : utiliser `composeRule.mainClock.advanceTimeBy(…)` puis `waitForIdle()` ;
  - le qualificatif `ldrtl` ne passe pas Compose en droite à gauche dans un module de
    bibliothèque : fournir `LocalLayoutDirection` dans le test ;
  - ne pas lancer deux Gradle en même temps sur le même module (le premier échoue avec
    `EOFException` sur `testDebugUnitTest`).
- **Macrobenchmark** : les APK de mesure se compilent dans le conteneur
  (`./gradlew :benchmark:assemble`), mais rien ne s'y exécute (ni KVM ni appareil). Ne pas
  essayer de lancer les benchmarks en CI : sur émulateur, les temps n'ont pas de sens.
- **Build natif sous Windows** : `make (e=87): Paramètre incorrect` sur `libmupdf_core.a`
  vient de la limite de 32 767 caractères d'une ligne de commande Windows (la commande `ar`
  liste des centaines d'objets en chemin absolu). `third-party/mupdf/build.gradle.kts` passe
  `APP_SHORT_COMMANDS=true` à ndk-build sous Windows seulement (fichiers de réponse) ; ne pas
  modifier l'`Android.mk` du sous-module.
- **Build sous Windows, autres pièges** (ADR 0013) :
  - `make` n'est plus nécessaire : `generateMupdfResources` est une tâche Kotlin de
    `build-logic` qui reproduit `scripts/hexdump.sh` octet pour octet ; à chaque mise à jour de
    MuPDF, revoir sa liste (`MUPDF_EMBEDDED_RESOURCES`) face à `FONT_BIN` / `HYPH_BIN` du
    `Makefile` ;
  - avec `core.autocrlf=true`, Git réécrit en CRLF les fichiers de `libmupdf/generated/`
    versionnés par MuPDF, puis la tâche les réécrit en LF : le sous-module apparaît modifié
    (`git status`) sans différence de contenu (`git diff` vide). C'est sans conséquence ;
  - `buildMupdfHostJni` et les tests JVM du moteur (`core:pdf`) sont sautés hors Linux, avec
    un avertissement ; seule la CI les exécute ;
  - detekt échouait avec `Invalid value (25) passed to --jvm-target` quand Gradle tourne sous
    JDK 25 : son `jvmTarget` est désormais fixé à 17 dans `build-logic`. Mais detekt 1.23.8
    échoue ensuite sous JDK 25 avec `IllegalArgumentException: 25.0.3` (son compilateur Kotlin
    embarqué ne sait pas lire cette version) : exécuter Gradle avec un **JDK 21**. Sous JDK 17,
    ce sont les tests Robolectric qui échouent (`Android SDK 36 requires Java 21`).
- **Benchmarks sur Xiaomi / HyperOS** (constaté le 2026-11-08) :
  - activer dans les options pour les développeurs « Installer via USB » **et** « Débogage USB
    (paramètres de sécurité) », sinon `SecurityException ... INSTALL_GRANT_RUNTIME_PERMISSIONS`
    (le lanceur de tests installe avec `-g`) ; le réglage ne prend effet qu'après un
    redémarrage du téléphone (vérifier : `adb shell getprop persist.security.adbinput` vaut 1) ;
  - chaque installation affiche une confirmation sur le téléphone, à accepter vite, sinon
    `INSTALL_FAILED_USER_RESTRICTED` ; `DELETE_FAILED_INTERNAL_ERROR` à la désinstallation veut
    seulement dire que le paquet est absent ;
  - **si l'installation échoue, `connectedBenchmarkReleaseAndroidTest` et
    `generateBaselineProfile` affichent quand même BUILD SUCCESSFUL sans exécuter aucun test** :
    toujours vérifier que les fichiers de sortie existent (`benchmarkData.json`,
    `baselineProfiles/*.txt`).
- **LeakCanary** (APK de debug seulement) ajoute le raccourci « Lutrin Leaks » à l'icône :
  c'est normal, l'application publiée ne l'a pas.

## 5. Préparer un conteneur cloud (session Claude Code sur le web)

Le conteneur n'a ni SDK Android ni sources de MuPDF au départ :

1. **SDK Android** : outils en ligne de commande dans `/opt/android-sdk/cmdline-tools/latest`
   (archive `commandlinetools-linux-14742923_latest.zip`, 172 Mo : l'index XML de
   dl.google.com ne liste que d'anciennes versions, il faut le nom exact),
   puis `sdkmanager "platform-tools" "platforms;android-37.0" "build-tools;37.0.0"
   "ndk;28.2.13676358" "cmake;3.22.1"`. Créer ensuite `local.properties` avec
   `sdk.dir=/opt/android-sdk`. Ce fichier est ignoré par Git.
2. **MuPDF** : lancer `./scripts/init-mupdf.sh`.
3. **Maven Central derrière le proxy** : si Gradle échoue avec des erreurs 429, ajouter un
   script d'initialisation **local** (jamais dans le dépôt),
   `~/.gradle/init.d/central-mirror.init.gradle.kts`. Il ajoute en premier le miroir
   `https://maven-central.storage-download.googleapis.com/maven2/` à `pluginManagement` et à
   `dependencyResolutionManagement`, et remet `gradlePluginPortal()` quand `pluginManagement` n'a
   pas d'autre dépôt. Sans ce miroir, le symptôme n'est pas forcément une erreur 429 : la
   configuration échoue avec `Plugin [id: 'io.github.takahirom.roborazzi', …] was not found`.
4. **Vérifier** : `./gradlew check assembleRelease :dokkaGenerate`, comme la CI. Compter 5 à
   10 minutes au premier lancement, à cause de la compilation de MuPDF pour la machine hôte
   et pour les trois ABI. Un agent dont les commandes au premier plan sont limitées à
   10 minutes doit lancer cette première compilation en arrière-plan.
5. **Captures d'écran** : après un changement visuel voulu, `./gradlew recordRoborazziDebug`,
   puis relire les images avant de les committer.

## 6. Compiler sous Windows

Git Bash, dépôt dans un chemin sans espace, SDK Android, **JDK 21** (ni 17 ni 25, voir §4) ; `make` n'est pas
nécessaire (ADR 0013). Même commande que la CI :
`./gradlew check assembleRelease :dokkaGenerate :benchmark:assemble --continue`. Attendu :
tout passe sauf les 3 tests de `DataStoreSettingsRepositoryTest` (échec connu, §2) ; les
tests JVM du moteur de `core:pdf` et `buildMupdfHostJni` sont sautés. Détails dans le README
(« Compiler sous Windows ») et les pièges en §4.

## 7. Mettre ce fichier à jour

À la fin de chaque session :
- mettre à jour la section 1 et la date ;
- rayer ou retirer ce qui est fait en section 2 et ajouter ce qui a été découvert ;
- noter en section 4 tout nouveau piège.

`ROADMAP.md` §0, `CHANGELOG.md` et `docs/open-questions.md` restent les sources détaillées.
