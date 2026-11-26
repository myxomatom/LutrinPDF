# Audit du 2026-11-26 — dérives en cours de M5

Audit complet du dépôt, au commit `dbe7bf0` (impression F15 fusionnée, M5 en cours), mené par six
revues indépendantes en lecture seule : moteur PDF et ressources natives, dérive fonctionnelle et
documentaire, règles absolues et sécurité, architecture et qualité du code, tests, build et CI.
Les constats retenus ont été relus dans le code par l'orchestrateur. Deux constats ont été
écartés comme faux positifs (§8).

Limite : aucun build n'a pu être lancé dans le conteneur d'audit, faute de SDK Android, de
sous-module MuPDF et de cache Gradle. Tout est vérifié par lecture. Le comportement natif a été
vérifié dans les sources amont de MuPDF 1.28.6.

Légende du suivi : ✅ corrigé · 📌 suivi ailleurs (point ouvert, jalon) · ⏳ à faire.

## 1. Synthèse

Le projet ne dérive pas sur l'essentiel : aucune violation de `AGENTS.md` §1, frontières de modules
respectées, aucun type MuPDF hors de `core:pdf`, aucun `!!`, dispatchers injectés, aucun test
désactivé, fakes plutôt que mocks, seuils Kover appliqués, aucune baseline detekt ou lint, aucune
régression des corrections de l'audit M1. Les dérives portent sur trois points :

1. **Robustesse** : une page illisible dans un document qui s'ouvre fait planter le lecteur (A1).
2. **Une entorse aux règles d'écriture** : `ImagePdfWriter` écrit un PDF hors de `PdfWriter` et de
   `core:pdf`, sans que l'ADR 0026 cite la règle enfreinte (X1).
3. **La documentation prend du retard** sur le code : `dependencies.md`, README, `architecture.md`,
   `reprise.md` et la décision F12 propagée à moitié (B1 à B5).

## 2. Moteur PDF (`core:pdf`)

| # | Gravité | Constat | Suivi |
|---|---------|---------|-------|
| A1 | Haute | Un PDF qui s'ouvre mais dont une page ne se charge pas (par exemple `/Count` supérieur au nombre de `/Kids`) lève `PdfError.Unreadable`. Aucun appelant ne l'intercepte : `ReaderViewModel.kt:284` (`pageSize` de toutes les pages dans `show()`, lancé depuis `viewModelScope`), `PdfPage.kt:83` et `:182`, `PagePreviewCard.kt:175`. L'application plante, contrairement à SPEC §3. Le KDoc de `PdfDocument` ne mentionne pas cette erreur. Correction : KDoc, taille de repli, page vierge en erreur, PDF de test dédié, tests du moteur et du ViewModel | ✅ PR #52 (message « Page endommagée » au lieu d'une page vierge, ADR 0031) |
| A2 | Moyenne | `renderPage` alloue trois fois la taille de la cible : pixmap natif (`PageRendering.kt:38`), `IntArray` Java (`:57`) et bitmap (`MuPdfDocument.kt:72-73`). Seul `createBitmap` est protégé contre le manque de mémoire : une page très haute peut provoquer un `OutOfMemoryError` | ⏳ |
| A3 | Moyenne | Le cache natif de MuPDF garde sa taille par défaut (256 Mo), n'est jamais réduit (pas de `shrinkStore`, pas de réaction à `onTrimMemory`) et n'est pas compté dans le budget de l'ADR 0008 | ⏳ |
| A4 | Basse | La copie des pixels (`setPixels`) se fait sur le thread appelant, le thread principal, contrairement à AGENTS §3 | ⏳ |
| A5 | Basse | Le test d'annulation ne prouve pas qu'un rendu en cours est interrompu. Les tests promis par l'audit M1 manquent toujours : annulation de la recherche, éviction de `PageCache`, permission révoquée sur la JVM | ⏳ |
| A6 | Basse | Point ouvert n° 11 (URI non adressables) prévu « avant M3 », toujours ouvert alors que M3 est terminé | 📌 open-questions n° 11 |

Conformes : un thread dédié par document, libération des ressources natives y compris à
l'annulation, `close()` concurrent, journaux MuPDF neutralisés, JavaScript désactivé, messages
d'erreur sans contenu ni chemin.

## 3. Écart transversal

| # | Gravité | Constat | Suivi |
|---|---------|---------|-------|
| X1 | Moyenne | `feature/reader/.../print/ImagePdfWriter.kt` écrit un PDF d'images à la main dans un module feature. AGENTS §10 impose que toute écriture passe par `PdfWriter`, et §3 réserve la manipulation du PDF à `core:pdf`. L'ADR 0026 (l. 43-46) assume l'écart pour borner la mémoire, sans citer ces règles ni prévoir de migration. Action : amender l'ADR 0026, puis déplacer cet écrivain derrière `PdfWriter` dans `core:pdf` au jalon M6 | ✅ ADR 0026 amendé · 📌 migration en M6 |

Relevé par trois revues (moteur, dérive, architecture), avec des gravités allant de basse à haute.
La gravité moyenne a été retenue parce que l'écart est délibéré et en partie documenté.

## 4. Sécurité et vie privée

| # | Gravité | Constat | Suivi |
|---|---------|---------|-------|
| S1 | Basse (latent) | `ParseIncomingDocumentUseCase.kt:131-137` compare l'autorité brute pour refuser les URI de Lutrin. Elle n'est pas décodée (`%2E`), et le code coupe au premier `@` là où Android coupe au dernier. `content://io.github.myxomatom.lutrin%2Eprovider/…` passerait donc le contrôle. Sans effet tant que Lutrin n'expose aucun fournisseur. À corriger avant le partage sortant ou les outils (M6) | ⏳ |
| S2 | Basse | `IncomingIntents.kt:28,36` lit les extras sans `try/catch` : un extra `Parcelable` inconnu peut faire planter l'activité exportée. À confirmer sur API 26-32 | ⏳ |
| S3 | Basse | La vérification des permissions est une liste noire de 8 noms, pas une liste blanche. Il n'existe ni garde-fou nominal contre GMS ou Firebase, ni contrôle des composants exportés du manifeste fusionné (`ProfileInstallReceiver`), ni règle detekt contre `Log` et `println` | ⏳ |
| S4 | Basse | Pas de `FLAG_SECURE` ni de `setRecentsScreenshotEnabled(false)` pour un document protégé : sa page apparaît dans l'aperçu des applications récentes. `mailto:…?attach=` est accepté par les liens externes | ⏳ |
| S5 | Basse | L'écran E4 « À propos et licences » n'existe pas encore : les notices Apache, MIT, IJG et OFL (polices de MuPDF) ne sont pas livrées. Bloquant pour la v1.0 | ✅ E4 livrée par la PR #49 (ADR 0030) |

Conformes : aucune pub, télémétrie ou dépendance propriétaire ; aucun `<uses-permission>`, avec un
contrôle branché sur `check` ; en-tête SPDX partout ; aucun secret ; mot de passe ni persisté ni
journalisé ; liens externes limités à http, https et mailto, avec confirmation ; copie et
impression respectent les restrictions du document ; sauvegarde désactivée ;
`BenchmarkReaderActivity` absente du build release.

## 5. Architecture et qualité du code

| # | Gravité | Constat | Suivi |
|---|---------|---------|-------|
| Q1 | Moyenne | `ReaderUiState.Ready` (`ReaderUiState.kt:58-69`) porte des objets à état (`PageRenderer` avec son cache de bitmaps, `PageLinks` avec son cache synchronisé) : l'UiState n'est pas immuable. Son défaut `Dispatchers.Unconfined` (`:66`) est un dispatcher codé en dur | ⏳ |
| Q2 | Moyenne | Échecs Room avalés dans `RoomRecentDocumentsRepository.kt` : `saveLastPage` silencieux, `remove` et `clear` dont l'échec se confond avec « absent » ou « liste vide » (AGENTS §4). Le `null` de `recordOpening` est documenté par son contrat : écarté | ⏳ |
| Q3 | Basse | `ReaderViewModel` (357 lignes, 7 dépendances) partage un état mutable (`document`, `isProtected`) avec 3 contrôleurs. 13 composables dépassent 35 lignes (`PdfPage`, 55 lignes). 28 `@Suppress("LongParameterList")` | ⏳ |
| Q4 | Basse | Types Android dans les ViewModels : `android.net.Uri` dans `ReaderViewModel`, `ImageBitmap` et `IntSize` exposés par `LibraryViewModel:140`. `@Suppress` sans justification dans `PdfDates.kt:25` | ⏳ |

Conformes : `core:domain` sans Android, aucun feature qui dépend d'un autre, UseCases avec une
seule méthode `invoke`, aucune chaîne visible en dur, KDoc quasi complet, `LazyColumn` avec `key`,
aucun fichier au-dessus de 450 lignes.

## 6. Tests

| # | Gravité | Constat | Suivi |
|---|---------|---------|-------|
| T1 | Moyenne | Un seul test `AccessibilityChecks`, sur la bibliothèque vide (`MainActivityInstrumentedTest.kt:24-30`). Le lecteur, les réglages, la recherche et les dialogues n'en ont aucun (AGENTS §5.1) | ⏳ |
| T2 | Moyenne | `SearchInstrumentedTest`, `SelectionInstrumentedTest` et `LinksInstrumentedTest.internalLinkActionShowsItsPage` (l'autre test de liens a un vrai `intended`) n'ont aucune assertion explicite, seulement des `waitUntil` | ⏳ |
| T3 | Basse | Quatre tests de captures sans assertion (Selection, ExternalLinkDialog, GoToPageDialog, PasswordDialog) : ils ne protègent que si `roborazzi.test.verify=true`. `ReaderPrintTest.kt:174` n'a qu'un `isNotNull()` (les autres `isNotNull()` relevés sont suivis d'assertions : écartés) | ⏳ |
| T4 | Basse | Les tests instrumentés de la CI ne couvrent que `:app` et `:core:pdf`. (« Aucun test pour `ReaderOptionsSheet` ni `PagePreviewCard` » : écarté, couverts par `ReaderScreenTest` et `FloatingPageBarTest`) | ⏳ |

Conformes : aucun `@Ignore` ; aucun `Thread.sleep` ni heure réelle ; un seul test supprimé dans
l'historique, retiré avec sa fonctionnalité (ADR 0019) ; migration Room 1→2 testée ; impression
bien couverte ; exclusions Kover limitées au code généré.

## 7. Dérive documentaire

| # | Gravité | Constat | Suivi |
|---|---------|---------|-------|
| B1 | Moyenne | `docs/dependencies.md` a 7 versions qui ne correspondent plus au catalogue (AGP 9.5.1 au lieu de 9.5.2, Gradle, Dokka, KSP, DataStore, Roborazzi, compose-rules) et n'a pas `javax.inject` (embarqué dans l'APK), `espresso-core` ni Robolectric android-all (AGENTS §1.4, §6) | ✅ |
| B2 | Moyenne | Le README s'arrête à « M3 terminé, M4 à venir ». `architecture.md` (2026-11-09) ignore M4 et M5, et son diagramme n'a pas l'arête `feature:reader → core:pdf` | ✅ |
| B3 | Moyenne | La décision F12 « zoom non mémorisé » n'a changé que `SPEC.md:40`. SPEC §5 (l. 240), ROADMAP (l. 19), `reprise.md` (l. 134) et l'ADR 0015 parlent encore du zoom et du décalage | ✅ |
| B4 | Moyenne | `docs/reprise.md` décrit encore 7 branches fusionnées et validées comme « pas encore sur appareil », cite « ADR 0001 à 0025 » et omet les points ouverts 17 et 18 | ✅ |
| B5 | Basse | CHANGELOG avec deux sections « Modifié » et aucune montée de version après AGP 9.5.1. ADR 0009, 0010, 0014 et 0015 amendés sans renvoi depuis le texte d'origine. Point ouvert n° 11 encore « à trancher avant M3 ». F27 absent des critères d'acceptation (SPEC §8) | ✅ |

Conformes : chaînes FR et EN complètes dans les 4 modules ; thème `core:ui` identique à
`design-tokens.json` ; écarts aux maquettes couverts par les ADR 0015, 0018 et 0026 ; aucun code
anticipé de M6 ou M7, en dehors de X1.

## 8. Build et CI

| # | Gravité | Constat | Suivi |
|---|---------|---------|-------|
| F1 | Moyenne | Dokka tolère les avertissements dans `:app` (`Quality.kt:131`). Justifié par un défaut amont dans androidx, mais contraire à AGENTS §6 : à suivre en amont | ⏳ |
| F2 | Moyenne | ktlint ne lit pas `app/src/benchmark/kotlin` (ADR 0012), qui n'est vérifié qu'à la main | ⏳ |
| F3 | Moyenne | `verify…Permissions` ne s'applique qu'à la variante release, alors que le debug est publié comme artefact de la CI | ⏳ |
| F4 | Moyenne | `build-logic` n'est pas analysé par detekt | ⏳ |
| F5 | Basse | `jvmTarget` du catalogue inutilisé (JVM 17 codé en dur à 3 endroits), `espresso-accessibility` inutilisé, detekt `LongMethod` à 40 lignes contre environ 30 dans AGENTS §4, `persist-credentials` non désactivé (`ci.yml:29,91`) | ⏳ |

Conformes : `allWarningsAsErrors` partout ; lint avec `warningsAsErrors` et `abortOnError` ; aucune
coordonnée en dur ni version dynamique ; wrapper vérifié par SHA-256 ; actions de la CI épinglées
par SHA ; aucun `continue-on-error` ni `|| true` ; seuils Kover 70 % et 80 % ; SDK 26 et 37
conformes à SPEC.

**Faux positifs écartés** : Licensee non rattaché à `check`, et `koverVerify` non rattaché à
`check`. Les deux plugins s'y rattachent eux-mêmes, et la CI lance `./gradlew check` (`ci.yml:48`).
Confirmation possible avec `./gradlew check --dry-run`.

## 9. Ordre de correction proposé

1. **A1** (plantage sur un PDF malformé), avec son PDF de test : avant toute nouvelle
   fonctionnalité de M5.
2. **X1** : amender l'ADR 0026 maintenant, et planifier la migration vers `PdfWriter` en M6.
3. **B1 à B4** : une PR de documentation.
4. **A2, A3, Q1, Q2, T1, T2**, puis les points de gravité basse au fil de l'eau. S1 et S5 avant la
   v1.0.

Les corrections de code, de tests et de build sont détaillées, lot par lot, dans
[`2026-11-26-plan-de-correction.md`](2026-11-26-plan-de-correction.md). La documentation
(B1 à B5, X1 côté ADR) a été corrigée le 2026-11-26.
