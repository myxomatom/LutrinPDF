# Reprendre le développement

> Point d'entrée d'une nouvelle session (personne ou agent). Mis à jour à la fin de chaque
> session de travail. **Dernière mise à jour : 2026-11-11.**
> L'état détaillé par jalon et par vue est dans [`ROADMAP.md` §0](../ROADMAP.md) ; ce fichier dit
> où on en est, ce qui reste à faire en premier, et les pièges déjà rencontrés.

## 1. Où en est le projet

| Jalon | État |
|-------|------|
| M0 Fondations | ✅ Terminé (sauf la vérification de reproductibilité F-Droid, reportée) |
| M1 Moteur PDF | ✅ Terminé, audit du 2026-11-04 corrigé |
| M2 Lecteur de base | ✅ Terminé : benchmarks mesurés sur appareil le 2026-11-08 (P90 4,0 ms en défilement continu), Baseline Profile committé (ADR 0012) |
| M3 Fichiers et récents | ✅ Terminé le 2026-11-10 : « Ouvrir avec » et partage entrant faits (ADR 0014) ; récents, vignettes, permissions persistées et reprise de la dernière page faits (ADR 0015, PR #16) ; « Retirer des récents » et « Vider les récents » faits (ADR 0016, PR #18) ; dialogue de mot de passe (V4) et documents protégés dans les récents faits (ADR 0017, branche `feat/pdf-password`) ; validé sur un Xiaomi 23013PC75G (Android 15) : tests instrumentés de `core:pdf` (9, dont les PDF chiffrés) et de `app` (6) verts, essais manuels du mainteneur réussis, dont un vrai PDF chiffré. Non essayée sur appareil : la migration Room 1→2 d'une base existante |
| M4 Navigation, recherche, sélection | 🚧 En cours : lot 1 « API texte et destinations du moteur » fait (ADR 0020, branche `feat/pdf-text-navigation-api`) ; lot 2 « aller à la page » en parallèle ; lots 3 (recherche, sélection) et 4 (sommaire, liens) à faire |
| M5 à M8 | ⏳ À faire (E3 existe déjà en partie) |

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

Sur la branche `feat/open-with` (pas encore sur appareil) : **« Ouvrir avec » et partage
entrant** (F01, ADR 0014). `MainActivity` répond à `ACTION_VIEW` (`content://`, PDF) et
`ACTION_SEND` (un PDF) ; le document s'ouvre seul dans le lecteur et le retour ramène à
l'application appelante ; extra `page` à partir de 1 ; refus (`file://`, lien web, autre type,
URI de Lutrin) avec un message sur la bibliothèque. Validation dans
`ParseIncomingDocumentUseCase` (`core:domain`). **À essayer sur appareil** : ouvrir un PDF depuis
Fichiers, Gmail et Drive avec « Ouvrir avec » puis avec « Partager », le retour arrière, une
rotation pendant l'ouverture, et `adb shell am start -a android.intent.action.VIEW -t
application/pdf -d <uri content://> --ei page 3`. **Mesurer aussi le temps d'ouverture par une
vraie URI SAF** reçue d'une autre application (les benchmarks de l'ADR 0012 ouvrent un fichier du
cache).

Sur la branche `feat/recents` (pas encore sur appareil) : **récents et reprise de lecture**
(F11, F12, ADR 0015). Base Room `lutrin.db` (`core:data`, schéma exporté dans
`core/data/schemas/`), au plus 50 récents ; chaque ouverture réussie prend une permission
persistante quand le système l'accorde (sélecteur) ; la bibliothèque liste les récents comme la
maquette E1 et dessine les vignettes (par `PdfEngine`, dans `noBackupFilesDir/thumbnails/`)
quand elle les montre ; le lecteur reprend à la page enregistrée (une seconde après le dernier
changement, et à la fermeture), l'extra `page` d'« Ouvrir avec » restant prioritaire. **À
essayer sur appareil** : ouvrir par le sélecteur, quitter, redémarrer le téléphone, rouvrir
depuis la liste (permission persistée) ; un document d'« Ouvrir avec » Gmail ou Drive dans la
liste après fermeture de l'application (attendu : écran V8 « inaccessible ») ; un fichier
supprimé depuis « Fichiers » ; la vignette qui apparaît au retour dans la bibliothèque ; la
reprise après rotation et après la mort du processus ; grande police et thème sombre.

Sur la branche `feat/remove-recents` (pas encore sur appareil) : **retirer un document des
récents et vider la liste** (F11, ADR 0016). Menu « ⋮ » de chaque ligne avec « Retirer des
récents » : la ligne disparaît, un message propose « Annuler » pendant 10 s, puis le retrait a
lieu (permission rendue, vignette supprimée, page oubliée), sauf si le document a été rouvert
entre-temps. L'écran V8 propose « Retirer des récents » pour un document de la liste qui ne
s'ouvre plus ; les paramètres (E3) ont « Vider les récents » avec un dialogue de confirmation,
désactivé quand la liste est vide. **À essayer sur appareil** : retirer puis « Annuler », retirer
puis attendre, retirer puis tourner l'écran (le retrait est confirmé), le tout avec TalkBack ;
« Vider les récents » puis vérifier que les permissions sont rendues
(`adb shell dumpsys activity permissions` ou `contentResolver.persistedUriPermissions`) ;
V8 « Retirer des récents » sur un fichier supprimé depuis « Fichiers ».

Sur la branche `feat/pdf-password` (pas encore sur appareil) : **documents protégés par mot de
passe** (F10, V4, ADR 0017). Un PDF chiffré ouvre le dialogue « Document protégé » au-dessus du
lecteur ; un mauvais mot de passe affiche une erreur annoncée et garde le texte sélectionné ;
essais illimités. Le mot de passe n'est jamais gardé (ni état, ni `SavedStateHandle`, ni Room,
ni journaux) ; le champ n'est pas sauvegardable, donc vide après une rotation. Un document
ouvert avec son mot de passe entre dans les récents avec un cadenas, sans vignette (schéma Room
**version 2**, colonne `is_protected`, migration automatique testée avec `MigrationTestHelper`).
Trois PDF chiffrés de plus dans le jeu de test (RC4, AES-128, mot de passe propriétaire seul).
**À essayer sur appareil** : ouvrir chacun des PDF chiffrés du jeu de test (mots de passe dans
`core/testing/README.md`) par le sélecteur et par « Ouvrir avec » ; mauvais mot de passe avec
TalkBack (annonce de l'erreur) ; clavier sans suggestions ; aucun gestionnaire de mots de passe
(Google, Bitwarden…) ne propose d'enregistrer le mot de passe ; rotation pendant la saisie
(champ vide attendu) ; `adb shell am kill` pendant la lecture d'un document protégé (le mot de
passe est redemandé) ; mise à jour depuis une version avec des récents (migration 1 → 2) ;
lancer `:core:pdf:connectedDebugAndroidTest` (nouveaux tests des PDF chiffrés, jamais
exécutés).

Historique des PR : #4 M1, #6 lecteur, #7 audit et bibliothèque, #9 zoom et Dependabot,
#10 fermeture sans plantage, #11 E3 et ce fichier. La branche suivante apporte le mode page
par page, l'épinglage des actions GitHub par SHA (B9) et la visibilité `internal` (C13).

## 2. Prochaines tâches, dans l'ordre conseillé

0. **M4, lots 3 et 4**, sur l'API du lot 1 (ADR 0020) : `PdfDestination` pour le sommaire et
   les liens (n'ouvrir que `http`, `https` et `mailto`, après confirmation) ; recherche avec
   progression (`SearchPageResult`), extraits et « 1 000 résultats ou plus » ; sélection par
   appui long (`selectText`, `SelectionMode.Words`), copie refusée si `canCopyText` est faux.
   Tests instrumentés de `core:pdf` du lot 1 jamais exécutés sur appareil (CI seulement).
1. **M3 Fichiers et récents** :
   - ~~« Ouvrir avec » et partage entrant (`ACTION_VIEW`, `ACTION_SEND`), avec validation des
     URI et des types MIME (`AGENTS.md` §9)~~ **Fait** (ADR 0014, branche `feat/open-with`) ;
   - ~~permissions persistantes, récents avec vignettes (Room), nom réel du document dans la
     liste, reprise de la dernière page~~ **Fait** (ADR 0015, branche `feat/recents`) ; le titre
     de la barre du lecteur reste le titre du PDF, sinon le dernier segment de l'URI ;
   - ~~« Retirer des récents » dans V8 et dans un menu « ⋮ » de ligne, « Vider les récents »~~
     **Fait** (ADR 0016, branche `feat/remove-recents`) ;
   - ~~dialogue de mot de passe (V4)~~ **Fait** (ADR 0017, branche `feat/pdf-password`) ;
   - ~~essais sur appareil de tout M3~~ **Fait** le 2026-11-10 (voir §1) ;
   - reportés par l'ADR 0015 : zoom et décalage de F12, empreinte des documents (point ouvert 16) ;
   - repli par copie dans `cacheDir` pour les URI non seekables (point ouvert 11).
2. ~~**Windows** : `DataStoreSettingsRepositoryTest`~~ **Résolu** : le test tourne sous Robolectric
   (SDK_INT 36) ; sur JVM nue, DataStore retombe sur `File.renameTo`, qui n'écrase pas sous Windows.
3. Petits restes de l'audit (`docs/audits/2026-11-04-audit-m1.md`, lignes ⏳) :
   - tester la build minifiée (R8) ;
   - tester `verifyReleasePermissions` de bout en bout ;

**Nouveau design visuel (ADR 0018 et 0019), fusionné dans `main` le 2026-11-10 (PR #24)** et
essayé sur appareil par le mainteneur : globalement validé. Deux retours corrigés ensuite sur la
branche `ccr-d7c547a8-psu5t2` (pas encore sur appareil) :

- l'animation « aller » (la vignette qui grandissait jusqu'au lecteur) était buggée : l'aller est
  maintenant un simple fondu ; seul le retour replie la page vers sa vignette, le document
  n'étant lié qu'une fois le lecteur posé (`DocumentLink`, ADR 0019 amendé). La page d'attente a
  disparu, donc le rond de chargement reste visible pendant une ouverture lente ;
- le réglage « Aperçu de la page » a quitté la feuille « Affichage » du lecteur pour l'écran
  Paramètres, nouvelle section « Lecture », à côté des couleurs dynamiques (ADR 0018 §6 amendé).

**À essayer sur appareil** :

1. ouvrir depuis la carte et depuis une ligne : simple fondu ; revenir par la flèche, le bouton
   et le geste (lent, puis annulé) : la page se replie vers la carte ; revenir aussitôt après le
   toucher : simple fondu ; un PDF lent : le rond de chargement reste visible ;
2. Paramètres › Lecture › « Aperçu de la page » : couper, revenir dans un document ouvert, le
   curseur suit le document en continu ; rallumer ;
3. toujours dus depuis la PR #24 : Macrobenchmarks de l'ADR 0012 et Baseline Profile à refaire
   (`SharedTransitionLayout` enveloppe toujours toute la navigation) ; clavier physique sur le
   curseur (non couvert par un test) ; retour depuis « Ouvrir avec ».

**Fuite signalée par LeakCanary** (une fois, non reproduite) : un `PopupLayout` de Compose
retenu par la passe *lookahead* de `SharedTransitionLayout`, probablement le menu « ⋮ » des
récents. Documentée, pas corrigée : analyse et pistes au point ouvert 17 de
`docs/open-questions.md`.

## 3. Décisions à connaître

- Toutes les décisions sont dans `docs/adr/` (0001 à 0017) et dans les points tranchés de
  `docs/open-questions.md`. Les plus récentes :
  - **ADR 0008** : rendu du lecteur (rendu de base et détail de la partie visible) ;
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
- Points encore ouverts : 1, 2, 3, 6, 8, 11, 13, 14 et 16 (`docs/open-questions.md`).

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
    `baselineProfiles/*.txt`).
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
  - `search_for` de PyMuPDF ne trouve pas un mot coupé en fin de ligne, alors que la recherche
    Java de MuPDF (texte extrait avec `dehyphenate`) le trouve : ne pas vérifier le jeu de test
    avec `search_for` ;
  - avec `python -I`, `PYTHONIOENCODING` est ignoré : utiliser `-X utf8` pour afficher de
    l'arabe dans une console Windows.
- **Worktree au chemin long sous Windows** (constaté le 2026-11-11) : dans
  `.claude/worktrees/agent-…`, `configureNdkBuild*` de `:third-party:mupdf` échoue
  (`make.exe: Interrupt/Exception caught (code = 0xc0000005)`), les chemins d'objets devenant trop
  longs. Un lecteur `subst` n'y change rien (Gradle résout le vrai chemin). Pour
  `assembleRelease`, copier l'arbre vers un chemin court (`robocopy <worktree> D:\lw /MIR /XD
  build .gradle .kotlin /XF .git`) et y lancer Gradle ; les tâches sans code natif (`check` d'un
  module, tests JVM) passent dans le worktree. Le démon Gradle garde le `java.io.tmpdir` de son
  premier lancement : si ce dossier disparaît, la compilation Kotlin échoue avec
  `NoSuchFileException …kotlin-compiler-in-…alive`.

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
