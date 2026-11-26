# Reprendre le développement

> Point d'entrée d'une nouvelle session (personne ou agent). Mis à jour à la fin de chaque
> session de travail. **Dernière mise à jour : 2026-11-26.**
> L'état détaillé par jalon et par vue est dans [`ROADMAP.md` §0](../ROADMAP.md) ; ce fichier dit
> où on en est, ce qui reste à faire en premier, et les pièges déjà rencontrés.

## 1. Où en est le projet

| Jalon | État |
|-------|------|
| M0 Fondations | ✅ Terminé (sauf la vérification de reproductibilité F-Droid, reportée) |
| M1 Moteur PDF | ✅ Terminé, audit du 2026-11-04 corrigé |
| M2 Lecteur de base | ✅ Terminé : benchmarks mesurés sur appareil le 2026-11-08 (P90 4,0 ms en défilement continu), Baseline Profile committé (ADR 0012) |
| M3 Fichiers et récents | ✅ Terminé le 2026-11-10 : « Ouvrir avec » et partage entrant (ADR 0014), récents, vignettes et reprise de la dernière page (ADR 0015), retrait et vidage des récents (ADR 0016), mot de passe des documents protégés (ADR 0017) |
| M4 Navigation, recherche, sélection | ✅ Terminé le 2026-11-21 (ADR 0020 à 0025) : table des matières, aller à la page, liens, recherche, sélection et copie, polices CJK du système |
| M5 Finitions MVP | 🟡 En cours : lot 2 **partage et propriétés** (F15, F16, ADR 0027) en PR ; lot 1 **impression** (F15, ADR 0026) fusionné dans `main` (PR #50), pas encore essayé sur appareil ; E4 **« À propos et licences »** fusionnée (ADR 0030, PR #49), pas encore essayée sur appareil ; **corrections de l'audit du 2026-11-26 à faire avant les lots suivants** (§2) |
| M6 à M8 | ⏳ À faire |

M0 à M4 ont été validés sur un Xiaomi 23013PC75G (Android 15 / HyperOS) : tests instrumentés de
`core:pdf` (21) et de `app` (12) verts, benchmarks et Baseline Profile régénérés le 2026-11-21,
et la liste [`verification-appareil-m4.md`](verification-appareil-m4.md) entièrement validée par
le mainteneur (53 essais, dont la migration Room 1→2 d'une base existante et le temps d'ouverture
par une vraie URI SAF). **Cette liste fait foi** pour ce qui a été essayé sur appareil.

Ce qui est dans `main` :

- **E1 Bibliothèque** : récents (Room, 50 au plus, vignettes, progression, cadenas des documents
  protégés), carte « Reprendre la lecture », menu « ⋮ » « Retirer des récents » avec « Annuler »,
  « Ouvrir un fichier » (sélecteur système), « Ouvrir avec » et partage entrant, accès aux
  paramètres.
- **E2 Lecteur** : défilement continu ou page par page (F03), zoom 25 à 800 % net, plein écran au
  tap, curseur et indicateur de page, reprise de la dernière page, aller à la page (V3), table des
  matières (V2), liens internes et externes (V7), recherche (V1), sélection et copie (F08),
  **impression** depuis la barre flottante (F15, ADR 0026, pas encore essayée sur appareil).
- **V4** mot de passe, **V8** erreurs, **V6** feuille « Affichage » (section « Défilement »
  seulement ; l'ajustement à la largeur n'a plus de bouton depuis l'ADR 0026 et y reviendra au
  lot 4 de M5).
- **E3 Paramètres** (`feature/settings`) : « Apparence » (couleurs dynamiques), « Lecture »
  (aperçu de la page, « Ignorer les restrictions du document »), « Données et confidentialité »
  (« Vider les récents »).
- Moteur MuPDF 1.28.6 compilé depuis les sources ; texte CJK non incorporé dessiné avec les
  polices du système (ADR 0025). Module `:benchmark` (ADR 0012) : l'activité réservée
  `BenchmarkReaderActivity` (`app/src/benchmark/`) n'existe que dans les builds de mesure, `check`
  vérifie qu'elle n'est pas dans le build release.

Historique des PR (les PR de Dependabot, #5, #20 à #23, #34 à #36, #38, #45, sont omises) :

- 2026-11-03 à 05 : #1 spécification, #2 M0, #4 M1, #6 lecteur, #7 audit M1 et bibliothèque,
  #9 zoom, #10 fermeture sans plantage, #11 E3 et ce fichier, #12 actions GitHub épinglées par SHA ;
- 2026-11-08 à 10 : #13 M2 clos (benchmarks, Baseline Profile), #14 build sous Windows, #15 « Ouvrir
  avec », #16 récents, #17 Robolectric et `%TEMP%`, #18 retrait des récents, #19 mots de passe
  (M3 clos), #24 et #25 nouveau design (ADR 0018, 0019 ; validé sur appareil) ;
- 2026-11-11 et 12 : #26 API texte et destinations, #27 aller à la page, #28 polices CJK, #29
  Dokka 2.3, #30 table des matières, #31 liens, #32 recherche ;
- 2026-11-19 à 22 : #33 sélection et copie, #39 appui long en page par page, #40 et #41 clôture de
  M4 (benchmarks), #42 M4 terminé, #43 rotation F27 ajoutée au MVP ;
- 2026-11-25 et 26 : #50 impression (M5, lot 1), #48 zoom retiré de F12, puis l'audit du 2026-11-26
  (`0e6a96d`).

## 2. Prochaines tâches, dans l'ordre conseillé

0. **État au 2026-11-26 : M4 terminé, M5 en cours.** Dans l'ordre :
   1. ~~Benchmarks sur appareil~~ **faits le 2026-11-21** (ADR 0012, « Mesures M4 ») : reste à
      décider d'une tâche sur le démarrage à froid (+20 % depuis M2, voir l'ADR) ;
   2. ~~essais de [`verification-appareil-m4.md`](verification-appareil-m4.md)~~ **faits** (53
      essais validés le 2026-11-21) ;
   3. **Corrections de l'audit du 2026-11-26** : les corrections de code sont décrites dans
      [`docs/audits/2026-11-26-plan-de-correction.md`](audits/2026-11-26-plan-de-correction.md) ;
      **à faire en premier**. Lot 1 (**A1**, page illisible) : branche `fix/unreadable-page`,
      PR #52 à fusionner une fois la CI verte (ADR 0031 ; décision D1 du mainteneur : message
      « Page endommagée » sur la page). **À essayer sur appareil** : ouvrir
      `page_tree_count_mismatch.pdf`, aller à la page 3, faire glisser le curseur, zoomer, passer
      en page par page, TalkBack sur le message : aucun plantage, pages 1 et 2 dessinées. Ensuite
      le lot 2 (mémoire et fil du rendu, qui s'appuie sur `RenderResult`). La documentation a été mise à jour le
      2026-11-26 ; le constat complet est dans
      [`docs/audits/2026-11-26-audit-complet.md`](audits/2026-11-26-audit-complet.md) ;
   4. **M5**, lot 1 **impression (F15)** : **dans `main` (PR #50), pas encore essayée sur
      appareil** (ADR 0026). **À essayer** : imprimer `simple_1page.pdf` et le PDF de 600 pages
      vers une vraie imprimante et vers « Enregistrer au format PDF » (pages choisies « 2-5 » : le
      fichier d'origine est envoyé entier, le service doit garder la plage) ; un PDF chiffré
      ouvert avec son mot de passe (images à 150 ppp, aperçu qui arrive après le dessin de toutes
      les pages, mémoire stable) ; `encrypted_owner_password_only.pdf` (bouton atténué, message),
      puis E3 › Lecture › « Ignorer les restrictions du document » (impression permise) ;
      TalkBack sur le bouton ; quitter le lecteur pendant l'impression d'un document protégé
      (échec propre attendu) ; test instrumenté `PrintInstrumentedTest`. L'ajustement à la largeur
      n'a plus de bouton : il ira dans la feuille « Affichage » au lot 4 ;
   5. **E4 « À propos et licences »** : dans `main` (PR #49), à essayer sur appareil (paragraphe
      « E4 sur appareil » ci-dessous) ;
   6. **M5 lot 2, partage et propriétés (F15, F16, V5)** : PR `feat/share-properties` (ADR 0027), pas
      encore essayé sur appareil. **À essayer** : ⋮ › « Partager » vers une messagerie, un
      gestionnaire de fichiers et « Enregistrer sur l'appareil », pour un document ouvert par le
      sélecteur, par « Ouvrir avec » et pour un PDF protégé (il part chiffré) ; une application ou
      un fournisseur qui refuse l'URI (message « Ce fichier ne peut pas être partagé ») ; ⋮ ›
      « Propriétés » (valeurs, « Non renseigné », dates dans la langue et le fuseau du téléphone,
      grande police, TalkBack, rotation de l'écran) ; « Imprimer » depuis la feuille, y compris sur
      `encrypted_owner_password_only.pdf` ; `ShareInstrumentedTest` ;
   7. lots suivants de M5 (voir `ROADMAP.md`) : thèmes, feuille « Affichage » (lot 4, ouverte par
      ⋮ › « Affichage » depuis le lot 2), rotation F27 (lot 5).
   - **Dependabot #37 (Robolectric 4.18)** : casse 274 tests sur 379 ; il faut adapter le projet
     ou ignorer cette version (`ignore` dans `.github/dependabot.yml`). Ne pas fusionner en l'état.
   - **Test instable en CI** : `NoSuchMethodException ReaderViewModel.<init>()` (Hilt) dans les
     tests Robolectric de `app`, vu sur les PR #31 et #38, disparaît à la relance
     (`gh run rerun --failed`). Cause probable : code Hilt ou KSP périmé repris d'un cache.
1. **Restes de M3, reportés** :
   - le **décalage dans la page** de F12 (le zoom est **abandonné**, décision du 2026-11-25,
     PR #48 : `SPEC.md` F12 ne mémorise que la page) ; l'empreinte des documents (point ouvert 16) ;
   - repli par copie dans `cacheDir` pour les URI non seekables (point ouvert 11), à trancher
     **avant la publication v1.0**.
2. ~~**Windows** : `DataStoreSettingsRepositoryTest`~~ **Résolu** : le test tourne sous Robolectric
   (SDK_INT 36) ; sur JVM nue, DataStore retombe sur `File.renameTo`, qui n'écrase pas sous Windows.
3. Petits restes de l'audit M1 (`docs/audits/2026-11-04-audit-m1.md`, lignes ⏳) :
   - tester la build minifiée (R8) ;
   - tester `verifyReleasePermissions` de bout en bout.

**Fuite signalée par LeakCanary** (une fois le 2026-11-10, non reproduite ; l'essai de la liste de
M4 n'a rien signalé) : un `PopupLayout` de Compose retenu par la passe *lookahead* de
`SharedTransitionLayout`, probablement le menu « ⋮ » des récents. Documentée, pas corrigée :
analyse et pistes au point ouvert 17 de `docs/open-questions.md`.

**E4 sur appareil (ADR 0030, dans `main` par la PR #49, pas encore essayée sur appareil)** :
Paramètres › « À propos et licences » (dernière ligne). Version affichée, phrase « Aucune donnée
collectée », licence « GNU GPL v3 ou ultérieure, distribuée sous GNU AGPL v3 » avec son
explication, « Code source » (une confirmation s'ouvre, « Annuler » n'ouvre rien, « Ouvrir »
lance le navigateur du téléphone), « Politique de confidentialité » (texte local ; retour par la
flèche et par le geste), liste des licences tierces : MuPDF, FreeType, HarfBuzz, les polices
URW++ et Fraunces y sont ; une fiche montre le texte complet de la licence (OFL, AGPL…) ; avec
TalkBack, grande police (200 %) et thème sombre ; sur un build `release`, la liste est présente
(ressource `aboutlibraries` non retirée par R8) ; `adb shell dumpsys package` ne montre aucune
permission réseau.

**Repères sur le code de M4 et M5** (les détails sont dans les ADR) :

- `PageNavigator.goTo(page, yOffset)` : décalage en points PDF depuis le haut de la page, appliqué
  en défilement continu seulement ; `PageNavigator.reveal` centre un résultat de recherche.
- Les entrées externes de la table des matières passent par `onExternalOutlineEntry`, puis
  `LinkController.onExternalLink` et le filtre des schémas (`CheckExternalLinkUseCase`).
- Recherche : `SearchController` (400 ms après la frappe) ; sélection : `SelectionController`
  (copie par `SelectionEffectsHandler`) ; permission : `AppSettings.allowsTextCopy`, réglage
  « Ignorer les restrictions du document » (copie et impression, ADR 0026 qui renomme l'ancien
  « Ignorer l'interdiction de copie » de l'ADR 0024).
- Polices CJK (ADR 0025) : pas de police embarquée, carrés sur un appareil sans police CJK (limite
  connue) ; limite de taille de `SPEC.md` gardée à 15 Mo. L'écran des licences (E4, PR #49) cite
  les polices URW (`docs/dependencies.md`). Pas encore de vérification automatique de la taille
  des APK (proposition dans l'ADR 0025).
- Un document protégé ouvert avec son mot de passe : récents avec cadenas et sans vignette (schéma
  Room 2, ADR 0017) ; jeu de test chiffré dans `core/testing/README.md`.

## 3. Décisions à connaître

- Toutes les décisions sont dans `docs/adr/` (0001 à 0027, 0030 et 0031) et dans les points tranchés de
  `docs/open-questions.md`. Les plus récentes :
  - **ADR 0008** : rendu du lecteur (rendu de base et détail de la partie visible) ;
  - **ADR 0031** : page illisible dans un document qui s'ouvre : taille de repli, message
    « Page endommagée » mis à l'échelle de la page, `PageRenderer` renvoie `RenderResult` ;
  - **ADR 0009** : écarts assumés avec les maquettes ;
  - **ADR 0010** : couleurs dynamiques désactivables, activées par défaut ; police du système
    conservée (pas de Roboto embarquée) ;
  - **ADR 0012** : module `:benchmark`, activité d'ouverture réservée aux builds de mesure,
    plugin `androidx.baselineprofile`, pas de benchmark en CI ;
  - **ADR 0014** : « Ouvrir avec » et partage entrant ; `MainActivity` en mode `standard`, un
    document reçu est la seule entrée de la pile (retour = application appelante), intent traité
    une seule fois (`savedInstanceState`, `MainViewModel`), validation des URI et des types
    dans `core:domain` ; `PdfEngineModule` public pour que les tests Robolectric de `app`
    remplacent le moteur (`@TestInstallIn`) ;
  - **ADR 0015** : récents dans Room (`core:data`), `documentId` = SHA-256 tronqué de l'URI,
    50 récents au plus (permission rendue et vignette supprimée au-delà), vignettes dessinées par
    `PdfEngine` quand la bibliothèque les montre, page enregistrée après 1 s de calme et à la
    fermeture (`@ApplicationScope`), extra `page` absent = « pas de page » (amende l'ADR 0014) ;
    `LibraryViewModel` rattaché à `MainActivity` ;
  - **ADR 0016** : retrait d'un récent depuis la bibliothèque masqué aussitôt, effectif à la fin
    du message « Annuler » (portée d'application, ignoré si le document a été rouvert) ; V8
    propose le retrait pour toute erreur d'un document de la liste ; « Vider les récents » dans
    E3 avec confirmation ; toute sortie de la liste passe par un seul chemin du dépôt ;
  - **ADR 0017** : mot de passe dans un dialogue (V4) au-dessus du lecteur, essais illimités,
    mot de passe jamais gardé (champ non sauvegardable, aucune copie dans l'état ni le stockage ;
    limite : `String` immuable imposée par MuPDF), dialogue hors de l'autofill ; un document
    ouvert avec son mot de passe entre dans les récents avec un cadenas et sans vignette
    (schéma Room 2, amende l'ADR 0015 §10) ;
  - **ADR 0011** : mode page par page (préférence globale, choisie dans V6), pas de changement
    de page tant que la page est zoomée, pages toujours de gauche à droite.
- Fermeture d'un document : un échec natif est intercepté et signalé, jamais relancé (point 15).
- MuPDF reste épinglé sur un tag publié ; Dependabot ne le suit pas (point ouvert 14).
- Points encore ouverts : 1, 2, 3, 6, 11, 13, 14, 16, 17 et 18 (`docs/open-questions.md`).

## 4. Pièges déjà rencontrés

- **Une seule session de développement à la fois.** Deux sessions ont réalisé le même lecteur
  en parallèle (PR #6 et une branche abandonnée), avec des conflits et deux ADR 0008. Avant de
  commencer, vérifier les PR ouvertes et les branches `ccr-*` récentes.
- **`./gradlew check` ne suffit pas** : la CI lance aussi `assembleRelease` et `:dokkaGenerate`.
  Dokka échoue sur toute déclaration publique sans KDoc, y compris les `override` des fakes de
  `core:testing` (PR #11). Avant de pousser :
  `./gradlew check assembleRelease :dokkaGenerate`.
- **Dokka 2.3 : liens non résolus hérités d'androidx.** `MainActivity` hérite de `MenuHost`,
  dont la KDoc cite `Lifecycle.State` ; Dokka 2.3 le signale (`... is unresolved in
  androidx.core.view/MenuHost/...`) et `failOnWarning` fait échouer `:app:dokkaGenerateModuleHtml`.
  `suppressInheritedMembers` et un `perPackageOption` sur `androidx` n'y changent rien : seul
  `:app` tolère donc les avertissements (`configureDokka`, `Quality.kt`). Les autres modules
  restent en `failOnWarning`. À revoir quand Dokka corrigera l'analyse des classes externes.
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
- **Erreurs par page du moteur** : toute fonction de `PdfDocument` qui prend un index de page
  peut lever `PdfError.Unreadable` sur un document qui s'est pourtant ouvert (ADR 0031). Un nouvel
  appelant l'intercepte ; dans les tests, `FakePdfDocument.unreadablePages` simule ces pages.
- **Tests Robolectric de `app` qui ouvrent un document** : la bibliothèque native de MuPDF ne se
  charge pas sous Robolectric (`UnsatisfiedLinkError` dès la construction de `MuPdfEngine`).
  Écrire un test `@HiltAndroidTest` avec `@Config(application = HiltTestApplication::class)` :
  `FakePdfEngineModule` (`app/src/test`) remplace alors `PdfEngineModule` par un
  `FakePdfEngine` injectable (ADR 0014). Les tests sans `@HiltAndroidTest` utilisent le vrai
  graphe et ne doivent pas ouvrir de document.
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
    `Makefile` **avec les drapeaux de polices d'`Android.mk`** (ADR 0025 ; les mêmes sont
    dans `build-host-jni.sh`) ;
  - passer `MUPDF_EXTRA_CFLAGS` à ndk-build recompile toute la bibliothèque native (≈ 8 min pour les trois
    ABI sous Windows) ;
  - `git submodule update --reference` refuse un dépôt de référence superficiel (« reference
    repository … is shallow ») : dans un nouveau worktree, récupérer les bibliothèques de
    `libmupdf/thirdparty` avec `--depth 1`, comme `scripts/init-mupdf.sh` ;
  - `./gradlew --stop` lancé depuis un autre checkout arrête aussi les démons des autres
    worktrees (même version de Gradle) : leur build échoue avec « Gradle build daemon has been
    stopped » ; relancer ;
  - avec `core.autocrlf=true`, Git réécrit en CRLF les fichiers de `libmupdf/generated/`
    versionnés par MuPDF, puis la tâche les réécrit en LF : le sous-module apparaît modifié
    (`git status`) sans différence de contenu (`git diff` vide). C'est sans conséquence ;
  - `buildMupdfHostJni` et les tests JVM du moteur (`core:pdf`) sont sautés hors Linux, avec
    un avertissement ; seule la CI les exécute ;
  - detekt échouait avec `Invalid value (25) passed to --jvm-target` quand Gradle tourne sous
    JDK 25 : son `jvmTarget` est désormais fixé à 17 dans `build-logic`. Mais detekt 1.23.8
    échoue ensuite sous JDK 25 avec `IllegalArgumentException: 25.0.3` (son compilateur Kotlin
    embarqué ne sait pas lire cette version) : exécuter Gradle avec un **JDK 21**. Sous JDK 17,
    ce sont les tests Robolectric qui échouent (`Android SDK 36 requires Java 21`) ;
  - **Robolectric remplissait `%TEMP%`** (constaté le 2026-11-09, jalon M3) : chaque JVM de
    test extrait son runtime natif (`robolectric-nativeruntime*`, ~200 Mo) et ses fichiers de
    bac à sable (`robolectric-*`) dans le dossier temporaire du système ; sous Windows la DLL
    est encore verrouillée quand Robolectric tente de la supprimer, donc rien n'était effacé.
    Deux `testDebugUnitTest` ont laissé ~1,4 Go et saturé `C:` (tests, Dokka et cache Gradle en
    échec). Robolectric n'a pas de propriété dédiée (il appelle `Files.createTempDirectory`) :
    `configureTestTempDir()` (`build-logic`, `TestTempDir.kt`) fixe `java.io.tmpdir` de chaque
    tâche `Test` à `<module>/build/tmp/robolectric`, vidé au début de chaque exécution et
    supprimé par `clean`. Les anciens dossiers `robolectric*` de `%TEMP%` peuvent être
    supprimés à la main.
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
    `baselineProfiles/*.txt`) ;
  - **sélecteurs UiAutomator** (constaté le 2026-11-21) : seules les descriptions de contenu sont
    visibles (pas les `testTag`). Le compteur de recherche est décrit « 1 000 résultats ou plus »
    (le mot « Contenu » dépasse le plafond de 1 000 résultats) ; le bouton de la table des
    matières n'existe qu'une fois le sommaire lu (`waitForOutlineButton()`) ; chaque itération
    des scénarios de recherche et de table des matières tue le processus (`killProcess()`), sinon
    l'itération suivante repart d'un document resté à la page 551. Pour diagnostiquer :
    `adb shell uiautomator dump /sdcard/x.xml` puis `adb pull` (sous Git Bash, préfixer par
    `MSYS_NO_PATHCONV=1`, sinon `/sdcard` devient `C:/Program Files/Git/sdcard`) ;
  - `generateBaselineProfile` dure environ 18 minutes sur le Xiaomi et demande une installation
    par APK : rester près du téléphone.
- **Fournisseur de contenu des tests instrumentés** (`TestDocumentProvider`, `app/src/androidTest`) :
  il tourne dans le processus de l'APK de test, qui ne contient pas la bibliothèque standard
  Kotlin (AGP retire les dépendances communes avec l'application). Écrit en Kotlin, il plantait
  avec `NoClassDefFoundError: kotlin.jvm.internal.Intrinsics` et l'application affichait
  « fichier introuvable ». Il est donc **en Java** (`src/androidTest/java`) : ne pas le
  réécrire en Kotlin. Son autorité ne doit pas commencer par le paquet de l'application (ADR 0014).
- **Tests et récents** (ADR 0015) :
  - `FakePdfEngine.openedSources` compte aussi les ouvertures faites pour dessiner une vignette,
    déclenchées par la bibliothèque quand elle montre une ligne sans vignette à jour ; un test qui
    compte les ouvertures ne doit pas afficher une telle ligne ;
  - dans les tests Robolectric de `app`, la liste est lue par Room sur un vrai thread :
    attendre le texte (`waitUntil`) avant de vérifier « Aucun document récent » ;
  - `advanceUntilIdle()` n'exécute pas les tâches de `backgroundScope` : passer la `TestScope`
    elle-même comme portée d'application dans les tests du lecteur ;
  - `ExtendedFloatingActionButton(text = …, icon = …)` : sous Robolectric, le libellé n'était
    pas fusionné dans le bouton (bouton sans nom) ; la bibliothèque utilise la variante à
    contenu.
- **Tests du retrait des récents** (ADR 0016) :
  - le message « Annuler » dure 10 s : `composeRule.mainClock.advanceTimeBy(11_000)` pour le
    faire expirer ; le retrait part ensuite sur les vrais threads de la portée d'application et
    de Room : attendre la liste avec `waitUntil` ;
  - `IconButton` mesure 40 dp en Material 3 récent, sa cible tactile 48 dp : vérifier avec
    `assertTouchWidthIsEqualTo(48.dp)`, pas `assertWidthIsAtLeast` ;
  - menus et dialogues sont d'autres fenêtres : `LocalDensity` ne les grossit pas ; pour une
    capture à 200 %, ajouter `@Config(fontScale = 2f)` (et `captureScreenRoboImage`) ;
  - les chaînes françaises utilisent des espaces insécables autour des guillemets et avant
    « ? » : écrire `\u00A0` dans les textes attendus des tests ;
  - un nom de test entre accents graves ne doit pas contenir « : » (refusé par le compilateur).
- **Disque système plein sous Windows** (constaté le 2026-11-09) : Robolectric écrit ses
  dossiers de données et sa bibliothèque native dans `%TEMP%` ; avec un disque C: plein, tous les
  tests Robolectric échouent (`Espace insuffisant sur le disque`, `Unable to load Robolectric
  native runtime library`). Rediriger `TMP` et `TEMP` vers un autre disque avant Gradle, par
  exemple `export TMP='D:\Dev\LutrinPDF\build\test-tmp' TEMP="$TMP"`.
- **Documents protégés** (ADR 0017) :
  - `MigrationTestHelper` de Room 2.8 avec son constructeur par nom de base échoue sous Windows
    (`This driver is configured to open a database named …` : `SupportSQLiteDriver` compare les
    noms avec `/`) : utiliser le constructeur avec un fichier et `AndroidSQLiteDriver`
    (`LutrinDatabaseMigrationTest`) ;
  - les schémas Room sont des assets des tests Robolectric de `core:data`
    (`sourceSets["test"].assets`) ;
  - un champ `OutlinedSecureTextField` expose le texte en clair dans sa sémantique
    `EditableText` : pour vérifier le masquage, lire le texte dessiné
    (`SemanticsActions.GetTextLayoutResult`, `PasswordDialogTest`) ;
  - ne jamais utiliser `rememberTextFieldState` pour un mot de passe : il est sauvegardable ;
  - `FakePdfEngine(password = …)` simule un document chiffré ; il enregistre les mots de passe
    reçus (`givenPasswords`) pour les tests seulement.
- **Plusieurs sessions Gradle en parallèle sous Windows** (constaté le 2026-11-11) : le démon
  Kotlin/Gradle est partagé entre les arbres de travail, et celui d'une autre session garde son
  propre `TMP` (`NoSuchFileException: W:uild	est-tmp\kotlin-compiler-in-utrin….alive`).
  Lancer Gradle avec `--no-daemon` pour isoler la session.
- **Nouveau worktree : sous-modules imbriqués de MuPDF** (constaté le 2026-11-12) :
  `git submodule update --init --depth 1` à la racine n'initialise que `libmupdf` ; ses propres
  sous-modules (`thirdparty/freetype`…) restent vides et `configureNdkBuild*` échoue avec
  `No rule to make target '…/thirdparty/freetype/src/base/ftbase.c'`. Lancer aussi
  `git submodule update --init --depth 1` dans `third-party/mupdf/libmupdf` (ou
  `scripts/init-mupdf.sh`).
- **Tests des liens sous Robolectric** (ADR 0022) : un toucher simple n'est confirmé qu'après le
  délai du double toucher (`mainClock.advanceTimeBy(500)`) ; la barre flottante couvre le bas
  de la zone du document (≈ 96 dp) : un toucher dans cette bande touche la barre, pas la page ;
  `shadowOf(application).checkActivities(true)` fait lever `ActivityNotFoundException` pour
  simuler l'absence d'application.
- **Tests instrumentés de `app` et reprise de lecture** (constaté le 2026-11-12, PR #31) : les
  récents survivent d'un test à l'autre (même installation). Un document rouvert reprend la page
  enregistrée par le test précédent (ADR 0015) : un test qui attend une page précise doit la
  demander (`EXTRA_PAGE`), sinon il attend une page qui n'apparaît jamais (échec selon l'ordre
  des tests).
- **Tests Compose** : avec un champ de texte en focus, le premier « retour » (`Espresso.pressBack`)
  ferme le clavier, le second ferme le dialogue ; `StateRestorationTester` (API v2) se construit
  avec `runComposeUiTest` de `androidx.compose.ui.test.v2` et `emulateSaveAndRestore()`.
- **LeakCanary** (APK de debug seulement) ajoute le raccourci « Lutrin Leaks » à l'icône :
  c'est normal, l'application publiée ne l'a pas.
- **API texte du moteur** (ADR 0020, constaté le 2026-11-11) :
  - `StructuredText.snapSelection` puis `highlight` / `copy` perdent la première lettre d'un mot
    arabe (aller-retour caret → point → caret) : la sélection est calculée en Kotlin
    (`PageText`), testable sans la bibliothèque native (`PageTextTest` tourne aussi sous
    Windows) ;
  - le marcheur de `StructuredText` (et `getBlocks()`) crée un `Font` Java par caractère et un
    `Image` par bloc : `GlyphCollector` les détruit aussitôt, sur le thread du document ;
  - `StructuredText.search` de MuPDF 1.28.6 échoue toujours (`NullPointerException` dans
    `ArrayList.toArray` : son JNI appelle `toArray(T[])` sans argument) : utiliser
    `Page.search`, qui extrait le texte avec la même option `dehyphenate` ; à revoir à chaque
    mise à jour de MuPDF ;
  - le constructeur Java de `LinkDestination` (MuPDF 1.28.6) affecte `width` et `height` à
    eux-mêmes : ils valent toujours 0 ; la taille d'un `FitR` est lue dans l'URI (`viewrect=`) ;
  - sur une page `/Rotate 90`, MuPDF perd la coordonnée de `/FitH`, `/FitV` et d'un `/XYZ`
    incomplet (`populate_destination` tourne (0, y) et ne garde que l'axe d'origine) ;
  - `FakePdfDocument` lève `Closed` comme le moteur et compte ses ouvertures : chaque
    `FakePdfEngine.open` après la première en ajoute une, chaque `close` en retire une ; un test
    qui partage un faux entre la vignette et le lecteur reste donc correct ;
  - `search_for` de PyMuPDF ne trouve pas un mot coupé en fin de ligne, alors que la recherche
    Java de MuPDF (texte extrait avec `dehyphenate`) le trouve : ne pas vérifier le jeu de test
    avec `search_for` ;
  - avec `python -I`, `PYTHONIOENCODING` est ignoré : utiliser `-X utf8` pour afficher de
    l'arabe dans une console Windows.
- **Tests de la recherche** (lot 5, ADR 0023) :
  - le premier résultat trouvé est envoyé dans le canal des effets avant qu'un test ne le
    collecte : `effects.test { awaitItem() }` le reçoit d'abord (`SearchControllerTest`) ;
  - sous Robolectric, un `SearchController` créé dans la composition avec
    `rememberCoroutineScope()` suit l'horloge du test : `mainClock.advanceTimeBy(500)` passe le
    délai de 400 ms après la frappe (`ReaderSearchTest`) ;
  - une `FilterChip` expose `Selected`, pas `ToggleableState` : `assertIsSelected()` ;
  - avant `Espresso.pressBack()`, valider le champ (`performImeAction()`) pour fermer le
    clavier, sinon le premier retour ne ferme pas la recherche ;
  - l'icône « Rechercher » de la barre du haut a changé les captures du lecteur
    (`reader_*`, `outline_preview_section_light`), réenregistrées.
- **Partage et propriétés** (M5 lot 2, ADR 0027) :
  - le bouton ⋮ ouvre un menu (« Affichage », « Partager », « Propriétés ») : un test qui voulait
    la feuille « Affichage » clique « Plus d'options » **puis** « Display » ; les messages du menu
    et des feuilles passent par le `SnackbarHostState` de `ReaderContent`, partagé avec la zone du
    document ;
  - la feuille « Propriétés » est une fenêtre à part : un test Robolectric qui clique ses boutons
    doit annoncer un écran assez haut (`@Config(qualifiers = "w360dp-h800dp")`), sinon les boutons
    sortent de l'écran ; un `@Config(qualifiers = …)` sur une méthode **remplace** celui de la
    classe (écrire `fr-w360dp-h800dp`) ;
  - `startActivity` depuis le contexte de l'application exige `FLAG_ACTIVITY_NEW_TASK` : le test
    de `startSharing` passe par une activité (`Robolectric.buildActivity`) ;
  - les captures de V5 forcent le fuseau UTC (`TimeZone.setDefault`) : les dates dépendent sinon de la
    machine ; les dates et la taille suivent la langue (« 422 ko », « 12 sept. 2026 10:14 »).
- **Impression** (M5 lot 1, ADR 0026) :
  - `android.graphics.pdf.PdfDocument` garde chaque page (une `SkPicture` avec une copie du
    bitmap) jusqu'à `writeTo` : sa mémoire croît avec le document. Le PDF d'images d'un document
    protégé est donc écrit en flux par `ImagePdfWriter` (paquet `print`) ;
  - les constructeurs de `PrintDocumentAdapter.LayoutResultCallback` et `WriteResultCallback` sont
    cachés : les faux des tests sont dans le paquet `android.print` (`src/test/kotlin/android/print`) ;
  - `CoroutineStart.ATOMIC` est une API « delicate » (avertissement, donc erreur) : la réponse à
    une écriture annulée avant son début passe par `invokeOnCompletion` ;
  - captures Roborazzi sous Windows : `recordRoborazziDebug` a pu ne réécrire aucune image (tâche
    de test reprise du cache) ; `verifyRoborazziDebug` puis copier les `build/outputs/roborazzi/*_actual.png`
    des seuls tests en échec marche, et confirme que Windows et la CI dessinent pareil (les autres
    captures n'ont pas bougé). Pour comparer deux PNG avec Pillow, convertir en `RGB` :
    `getbbox()` d'une image `RGBA` ne regarde que l'alpha.
- **Tests de la sélection** (lot 6, ADR 0024) :
  - dans une interface de droite à gauche, `Modifier.offset` inverse x : les poignées, en
    coordonnées de page, sont posées sous `LocalLayoutDirection` gauche à droite (la barre
    d'actions retrouve la direction de l'interface) ;
  - la barre d'actions est une `Popup` : capture avec `captureScreenRoboImage` ;
  - Robolectric n'a que l'API 36 hors ligne (`@Config(sdk = [32])` échoue, jar absent) : la
    version est passée en paramètre (`selectionClip(sdkInt)`, `systemConfirmsCopy`) ;
  - dans `ReaderRoute`, le délai de 400 ms de la recherche tourne sur le looper principal :
    valider le champ (`performImeAction()`) au lieu d'avancer l'horloge ;
  - `--tests "*Settings*"` est développé par le shell sous Git Bash (`settings.gradle.kts`) :
    donner le nom qualifié (`io.github…*Settings*`) ;
  - les captures des Paramètres ont changé (nouvel interrupteur), réenregistrées ;
  - `check assembleRelease :dokkaGenerate` ne compile pas les tests instrumentés : une API de
    test expérimentale sans `@OptIn(ExperimentalTestApi::class)` n'échoue qu'en CI. Lancer aussi
    `:app:compileDebugAndroidTestKotlin` avant de pousser.
- **Tests Compose du tiroir** (lot 3) : `@Config(qualifiers = "+fr")` (avec le `+`) ajoute la
  langue à celle de la classe, sans `+` elle échoue (« unrecognized trailing qualifier ») ; pour
  une capture à 200 %, `@Config(fontScale = 2f)` garde la taille des icônes, alors que
  `LocalDensity provides Density(…, fontScale)` les rétrécit ; en droite à gauche, un titre latin
  doit avoir `TextDirection.Content`, sinon son numéro passe à la fin ; après la fusion du lot 4,
  deux modifications de `ReaderViewModel.uiState` et de `ReaderContent` sont à fusionner à la main.
- **Sous-modules MuPDF d'un nouveau worktree** : `git submodule update --init --depth 1` ne prend
  pas `libmupdf/thirdparty/*` ; lancer `git -C third-party/mupdf/libmupdf submodule update --init
  --depth 1 thirdparty/brotli … ` (liste dans `scripts/init-mupdf.sh`), sinon ndk-build échoue
  (« No rule to make target … freetype/src/base/ftbase.c »).
- **Worktree au chemin long sous Windows** (constaté le 2026-11-11) : dans
  `.claude/worktrees/agent-…`, `configureNdkBuild*` de `:third-party:mupdf` échoue
  (`make.exe: Interrupt/Exception caught (code = 0xc0000005)`), les chemins d'objets devenant trop
  longs. Un lecteur `subst` n'y change rien (Gradle résout le vrai chemin). Pour
  `assembleRelease`, copier l'arbre vers un chemin court (`robocopy <worktree> D:\lw /MIR /XD
  build .gradle .kotlin /XF .git`) et y lancer Gradle ; les tâches sans code natif (`check` d'un
  module, tests JVM) passent dans le worktree. Le démon Gradle garde le `java.io.tmpdir` de son
  premier lancement : si ce dossier disparaît, la compilation Kotlin échoue avec
  `NoSuchFileException …kotlin-compiler-in-…alive`. Arrêter la commande shell d'un Gradle lancé
  en arrière-plan n'arrête pas son client Java : le build continue, verrouille des fichiers
  (`bundleLibRuntimeToJarDebug … utilisé par un autre processus`) et écrit dans le même journal ;
  attendre sa fin avant de relancer. Après une synchronisation de la copie courte suivie d'un
  merge, des tests de `app` peuvent échouer avec `NoSuchMethodException ReaderViewModel.<init>()`
  (code Hilt périmé) : `:app:clean` avant de conclure.

- **Les tests en page par page n'appuyaient que sur la page d'index 0** : `hitTest` cherchait la
  taille de la page par `PagePlacement.pageIndex` dans la liste reçue, et le mode page par page
  lui passait une liste d'une page : tout appui (long ou court, lien compris) sur une page
  d'index ≥ 1 ne donnait aucune position, et l'appui long retombait sur le basculement du plein
  écran. Corrigé par `pageHit` (un placement, une taille). Pour tout test de geste en page par
  page, **aller d'abord à la page 3** (action TalkBack « Lien : page 3 ») et appuyer là ; la
  tolérance de doigt de l'appui long est dans `isOnText` (ADR 0024 amendé).

- **Césure de MuPDF retirée (ADR 0030, amendement)** : `-DFZ_ENABLE_HYPHEN=0` (`MUPDF_EXTRA_CFLAGS` d'ndk-build, `XCFLAGS` de `build-host-jni.sh`) et plus de `hyph-all.zip` dans
  `MUPDF_EMBEDDED_RESOURCES`. Changer ce drapeau recompile toute la bibliothèque native. Pour M6
  (HTML vers PDF) : un `hyph-all.zip` réduit aux langues sous licence compatible, ou pas de césure.
- **Licences tierces (E4, ADR 0030)** :
  - la liste est générée à la compilation par le plugin AboutLibraries (`:app`, hors ligne) et
    complétée à la main pour ce que Gradle ne voit pas : MuPDF, ses bibliothèques natives, les
    polices URW et Fraunces (`app/aboutlibraries/`). **Après toute mise à
    jour de MuPDF, d'une police ou d'une bibliothèque native**, relancer
    `python scripts/generate-licenses-config.py` (sous-modules MuPDF initialisés) et committer ;
  - le parseur d'AboutLibraries (`Libs.Builder().withJson`) ne lève rien sur un JSON invalide :
    il écrit dans `Log.e` et rend une liste vide ; sur JVM nue, `Log.e` lève `not mocked` : les
    tests qui l'appellent tournent sous Robolectric ;
  - AGP 9 retire les ressources inutilisées : `res/raw/keep.xml` garde `aboutlibraries` ;
  - ajouter une dépendance sans licence connue du plugin fait apparaître un avertissement
    (« does not contain the license text ») : fournir un fichier dans `app/aboutlibraries/licenses/`.

## 5. Préparer un conteneur cloud (session Claude Code sur le web)

Le conteneur n'a ni SDK Android ni sources de MuPDF au départ :

1. **SDK Android** : outils en ligne de commande dans `/opt/android-sdk/cmdline-tools/latest`
   (archive `commandlinetools-linux-14742923_latest.zip`, 172 Mo : l'index XML de
   dl.google.com ne liste que d'anciennes versions, il faut le nom exact),
   puis `sdkmanager "platform-tools" "platforms;android-37.0" "build-tools;37.0.0"
   "ndk;28.2.13676358" "cmake;3.22.1"`. Créer ensuite `local.properties` avec
   `sdk.dir=/opt/android-sdk`. Ce fichier est ignoré par Git.
2. **MuPDF** : lancer `./scripts/init-mupdf.sh`.
3. **Maven Central derrière le proxy** : si Gradle échoue avec des erreurs 429, ou avec
   `Plugin [id: 'io.github.takahirom.roborazzi', …] was not found`, ajouter un script
   d'initialisation **local** (jamais dans le dépôt),
   `~/.gradle/init.d/central-mirror.init.gradle.kts` :
   ```kotlin
   val mirror = "https://maven-central.storage-download.googleapis.com/maven2/"
   beforeSettings {
       pluginManagement { repositories { maven(url = mirror) } }
       dependencyResolutionManagement { repositories { maven(url = mirror) } }
   }
   settingsEvaluated {
       if (pluginManagement.repositories.size == 1) {
           pluginManagement.repositories.gradlePluginPortal()
       }
   }
   ```
   Le portail des plugins n'est remis que si le miroir est le seul dépôt (cas de `build-logic`) :
   placé avant `google()`, il répond 429, Gradle le désactive et toute la résolution échoue. Le
   miroir ne contient pas `com.android.tools.build:*`, qui vient de `google()`.
4. **Vérifier** : `./gradlew check assembleRelease :dokkaGenerate`, comme la CI. Compter 5 à
   10 minutes au premier lancement, à cause de la compilation de MuPDF pour la machine hôte
   et pour les trois ABI. Un agent dont les commandes au premier plan sont limitées à
   10 minutes doit lancer cette première compilation en arrière-plan, dans un journal terminé par
   `echo EXIT $?`, et attendre avec `timeout 570 bash -c 'until grep -q "^EXIT" journal; do sleep 10; done'`
   (un `sleep` long au premier plan est refusé). Mesuré le 2026-11-10 : 14 min 39 s à froid,
   2 à 4 min ensuite.
5. **Captures d'écran** : après un changement visuel voulu, `./gradlew recordRoborazziDebug`,
   puis relire les images avant de les committer.

## 6. Compiler sous Windows

Git Bash, dépôt dans un chemin sans espace, SDK Android, **JDK 21** (ni 17 ni 25, voir §4) ; `make` n'est pas
nécessaire (ADR 0013). Même commande que la CI :
`./gradlew check assembleRelease :dokkaGenerate :benchmark:assemble --continue`. Attendu :
tout passe ; les tests JVM du moteur de `core:pdf` et `buildMupdfHostJni` sont sautés. Détails dans le README
(« Compiler sous Windows ») et les pièges en §4.

## 7. Mettre ce fichier à jour

À la fin de chaque session :
- mettre à jour la section 1 et la date ;
- rayer ou retirer ce qui est fait en section 2 et ajouter ce qui a été découvert ;
- noter en section 4 tout nouveau piège.

`ROADMAP.md` §0, `CHANGELOG.md` et `docs/open-questions.md` restent les sources détaillées.
