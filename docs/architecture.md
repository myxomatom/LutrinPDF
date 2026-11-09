# Architecture

> Architecture décrite par `SPEC.md` §4. Les 11 modules existent depuis le jalon M0 et se
> remplissent au fil des jalons (`ROADMAP.md` §0). État au 2026-11-09 : `feature:tools` et
> `feature:assistant` sont encore vides ; le module de test `:benchmark` s'y ajoute en M2
> (ADR 0012).

## Principes

- **Clean Architecture + MVVM**, flux de données unidirectionnel (UDF).
- `core:domain` est en Kotlin pur (aucune dépendance Android).
- Un module `feature:*` ne dépend jamais d'un autre module `feature:*`.
- MuPDF n'est visible que dans `core:pdf`, derrière `PdfEngine` et `PdfWriter`.
- Les dispatchers sont injectés ; aucun travail lourd sur le thread principal.

## Modules

| Module | Rôle |
|--------|------|
| `:app` | Point d'entrée, navigation, assemblage de l'injection de dépendances |
| `:core:domain` | Modèles, interfaces de repository, cas d'usage (`operator fun invoke`) |
| `:core:data` | Repositories, Room (récents, positions, signets), DataStore (préférences) |
| `:core:pdf` | `PdfEngine` / `PdfWriter` et leur implémentation MuPDF |
| `:third-party:mupdf` | MuPDF compilé depuis son sous-module (Java + JNI, NDK) ; seul `core:pdf` en dépend (ADR 0007) |
| `:core:ui` | Thème Material 3 (jetons de `docs/design/design-tokens.json`), composants partagés |
| `:core:testing` | Fakes, utilitaires de test, jeu de PDF de test (`SPEC.md` §9) |
| `:feature:library` | Bibliothèque / récents (E1) |
| `:feature:reader` | Lecteur, recherche, table des matières, dialogues (E2, V1–V8) |
| `:feature:settings` | Paramètres, À propos, licences (E3, E4, E10) |
| `:feature:tools` | Fusion, extraction, conversion, progression (E5–E9) |
| `:feature:assistant` | AppFunctions, intents et raccourcis pour assistants IA |
| `:benchmark` | Module de test (`com.android.test`) : Macrobenchmark et générateur du Baseline Profile, lancés sur appareil contre les builds `benchmarkRelease` / `nonMinifiedRelease` de `:app` (ADR 0012) |

## Dépendances entre modules

```mermaid
flowchart TD
    app[":app"] --> fl[":feature:library"]
    app --> fr[":feature:reader"]
    app --> fs[":feature:settings"]
    app --> ft[":feature:tools"]
    app --> fa[":feature:assistant"]
    app --> data[":core:data"]
    app --> pdf[":core:pdf"]

    fl & fr & fs & ft --> ui[":core:ui"]
    fl & fr & fs & ft & fa --> domain[":core:domain"]

    data --> domain
    data -->|"vignettes (PdfEngine)"| pdf
    pdf --> domain
    pdf --> mupdf[":third-party:mupdf"]

    testing[":core:testing"] -.tests.-> domain
    bench[":benchmark"] -.mesure.-> app
```

`core:pdf` et `core:data` implémentent les interfaces déclarées dans `core:domain` ;
`:app` les relie par injection de dépendances. Les features ne connaissent que le domaine
(et `core:pdf` pour le lecteur). `core:data` utilise `PdfEngine` pour dessiner les vignettes
des récents, sans voir aucun type MuPDF (ADR 0015).

## Moteur PDF (`core:pdf`)

```mermaid
flowchart LR
    caller["Feature / cas d'usage"] -->|"open(source, password)"| engine["PdfEngine (MuPdfEngine)"]
    engine -->|"Result&lt;PdfDocument&gt;"| caller
    caller -->|"renderPage, search, outline…"| doc["PdfDocument (MuPdfDocument)"]
    doc -->|"withContext"| thread["Thread dédié au document"]
    thread --> fitz["MuPDF (JNI)"]
```

- Un thread par document : tous les objets MuPDF y vivent et y sont détruits (`close()`).
- Les erreurs natives deviennent des `PdfError` (`core:domain`) ; aucune n'atteint l'UI en crash.
- Tests : sur la JVM avec la bibliothèque MuPDF compilée pour la machine de build, et
  instrumentés sur émulateur pour la bibliothèque Android.

## Lecteur (`feature:reader`)

- `ReaderViewModel` ouvre le document (URI reçue par la navigation) via `PdfEngine` et le ferme
  avec lui ; il expose `ReaderUiState` : `Loading`, `Error` (V8) ou `Ready`.
- `PageRenderer` produit les bitmaps : rendu de base de chaque page (cache LRU `BitmapCache`) et
  rendu net de la partie visible au-delà de « ajuster à la largeur » (ADR 0008).
- `ReaderScreen` / `PdfPagesView` (Compose) : `LazyColumn` des pages, gestes de zoom, barres,
  plein écran. Les calculs (zoom, partie visible, page courante) sont des fonctions pures de
  `ReaderGeometry.kt`, testées sur la JVM.
- Zoom au pincement : pendant le geste, la liste n'est que transformée visuellement
  (`PinchTransform` dans un `graphicsLayer`, sans nouvelle mise en page ni rendu) ; au lâcher,
  le zoom est appliqué une fois et `listPositionAfterGesture` calcule la page et le décalage qui
  gardent le contenu à l'endroit où le geste l'a laissé (`requestScrollToItem`, même image).
  Le double tap passe par le même chemin. Un geste à deux doigts est consommé jusqu'au bout :
  le doigt restant ne fait pas défiler la page.
- Mode page par page (ADR 0011) : `PdfPagerView`, un `HorizontalPager` dont chaque page est
  une surface zoomable (l'écran multiplié par le zoom, défilable dans les deux sens), avec les
  mêmes gestes (`ReaderGestures.kt`) et le même `PdfPage` que le mode continu. Le zoom 1 y
  vaut « page entière » (`PagedGeometry.kt`).
- `PageNavigator` donne la page courante et le saut de page au curseur, à l'indicateur et au
  ViewModel, quel que soit le mode. Le mode vient des paramètres :

```mermaid
flowchart LR
    v6["Feuille Affichage (V6)"] -->|"onScrollModeChange"| rvm["ReaderViewModel"]
    rvm -->|"SetScrollModeUseCase"| repo["SettingsRepository (DataStore)"]
    repo -->|"Flow&lt;AppSettings&gt;.scrollMode"| rvm
    screen["ReaderScreen"] -->|"onPageChange"| rvm
    rvm -->|"Ready(scrollMode, currentPage)"| screen
    screen -->|"CONTINUOUS"| list["PdfPagesView (LazyColumn)"]
    screen -->|"PAGED"| pager["PdfPagerView (HorizontalPager)"]
```

## Paramètres (`feature:settings`, `core:data`)

```mermaid
flowchart LR
    e3["SettingsScreen (E3)"] -->|"onDynamicColorChange"| vm["SettingsViewModel"]
    vm -->|"SetDynamicColorUseCase"| repo["SettingsRepository"]
    repo --> ds["DataStore Preferences (fichiers privés)"]
    repo -->|"Flow&lt;AppSettings&gt;"| act["MainActivity → LutrinTheme(dynamicColor)"]
    repo -->|"Flow&lt;AppSettings&gt;"| vm
```

- `AppSettings`, `SettingsRepository` et les cas d'usage sont dans `core:domain` ;
  `DataStoreSettingsRepository` dans `core:data` (une seule instance de DataStore par fichier,
  sur le dispatcher d'I/O injecté).
- `MainActivity` observe les paramètres : le thème change dès que l'interrupteur bouge, sans
  redémarrer (ADR 0010).
- Le mode de défilement du lecteur (`AppSettings.scrollMode`, ADR 0011) est stocké de la même
  façon ; le lecteur le lit et l'écrit par ses propres cas d'usage, sans dépendre de
  `feature:settings`.

## Récents et reprise de lecture (`feature:library`, `feature:reader`, `core:data`)

```mermaid
flowchart LR
    picker["Sélecteur / Ouvrir avec / Partager"] --> rvm["ReaderViewModel"]
    rvm -->|"GetStartPageUseCase"| repo["RecentDocumentsRepository (Room)"]
    rvm -->|"ReadingHistory : RecordDocumentOpening, SaveLastPage (1 s, fermeture)"| repo
    repo -->|"takePersistableUriPermission / release"| cr["ContentResolver"]
    repo -->|"Flow&lt;List&lt;RecentDocument&gt;&gt;"| lvm["LibraryViewModel"]
    lvm -->|"LibraryUiState"| e1["LibraryScreen (E1)"]
    e1 -->|"ligne sans vignette à jour"| lvm
    lvm -->|"UpdateThumbnailUseCase"| repo
    repo -->|"PdfThumbnailRenderer"| engine["PdfEngine"]
    repo --> files["noBackupFilesDir/thumbnails/*.png"]
    e1 -->|"toucher : URI"| rvm
```

- Table `recent_documents` (schéma exporté dans `core/data/schemas/`), au plus 50 entrées ;
  une entrée qui sort rend sa permission persistante et supprime sa vignette.
- Le lecteur part de la page demandée (extra `page`), sinon de la page enregistrée, ramenée
  au document ; il enregistre la page une seconde après le dernier changement et à sa
  fermeture (portée `@ApplicationScope`).
- Les vignettes sont dessinées quand la bibliothèque montre une ligne dont la vignette manque
  ou date d'avant la dernière ouverture, jamais pendant l'ouverture du lecteur.
- `LibraryViewModel` est rattaché à `MainActivity` ; `LutrinApp` reçoit son état en
  `StateFlow` et reste testable sans Hilt.

## Flux de données d'un écran

```mermaid
sequenceDiagram
    participant UI as Composable (sans état)
    participant VM as ViewModel
    participant UC as Cas d'usage (domain)
    participant R as Repository / PdfEngine
    UI->>VM: action (UiEvent)
    VM->>UC: invoke(...)
    UC->>R: appel suspendu (dispatcher injecté)
    R-->>UC: Result / Flow
    UC-->>VM: Result métier
    VM-->>UI: StateFlow<UiState> (immuable)
    VM--)UI: effet ponctuel (Channel / SharedFlow)
```

## Build

- `build-logic/` (build inclus) contient les plugins de convention appliqués par chaque module :

  | Plugin | Appliqué à | Ajoute |
  |--------|-----------|--------|
  | `lutrin.android.application` | `:app` | AGP application, Licensee, `verifyReleasePermissions`, émulateurs gérés |
  | `lutrin.android.library` | modules Android `core:*` | AGP bibliothèque |
  | `lutrin.android.compose` | `app`, `core:ui`, `feature:*` | Compose (BOM, compilateur) |
  | `lutrin.android.feature` | `feature:*` | bibliothèque + Compose + Hilt + `core:ui` / `core:domain` |
  | `lutrin.jvm.library` | `core:domain` | Kotlin/JVM pur |
  | `lutrin.hilt` | `app`, `feature:*` | Hilt + KSP |
  | `lutrin.android.robolectric` | `app`, `core:ui`, `core:data`, `feature:library`, `feature:reader`, `feature:settings` | Robolectric hors ligne (jars Android résolus par Gradle) |

  Tous appliquent ktlint, detekt, Kover et Dokka, `allWarningsAsErrors` et Java 17.
- Versions : uniquement dans `gradle/libs.versions.toml`. Dépôts : uniquement dans
  `settings.gradle.kts` (`FAIL_ON_PROJECT_REPOS`).
- Identifiant d'application : `io.github.myxomatom.lutrin` (`.debug` en debug, ADR 0006).

