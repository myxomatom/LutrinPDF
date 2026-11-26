# Plan de correction de l'audit du 2026-11-26

Destinataire : un agent de codage lancé par le mainteneur dans une session **locale** (SDK
Android, sous-module MuPDF extrait, appareil ou émulateur possible). Ce plan se suffit à
lui-même. Il dérive de [`2026-11-26-audit-complet.md`](2026-11-26-audit-complet.md), audit mené au
commit `dbe7bf0` en lecture seule, sans aucun build. Chaque constat a été relu dans le code avant
d'écrire la tâche. Les numéros de ligne sont ceux du commit `0e6a96d` (code identique à `dbe7bf0`), à revérifier
s'ils ont bougé.

Périmètre : le code, les tests, le build et la CI pour A1 à A5, Q1 à Q4, T1 à T4, S1 à S4 et F1 à
F5. La documentation (B1 à B5) et l'amendement de l'ADR 0026 (X1) sont faits ailleurs (§4).

## 0. Comment travailler

1. Lire `docs/reprise.md` (dont §4, « Pièges »), `AGENTS.md`, puis la section de `SPEC.md` et les
   ADR cités par la tâche. `AGENTS.md` prime en cas de doute.
2. Préparer le dépôt : `./scripts/init-mupdf.sh`, puis `./gradlew check` sur `main` **avant toute
   modification**, pour connaître l'état de départ. Un échec déjà présent est signalé, il n'est
   pas corrigé en douce.
3. Un lot = une branche `fix/…` (ou `test/…`, `build/…`, `ci/…` selon le lot) = une PR. Suivre
   l'ordre des lots, sauf si le mainteneur en décide autrement.
4. **TDD** : écrire d'abord le test qui reproduit le défaut, le voir échouer (le noter dans la
   PR), puis corriger. Un bug corrigé = un test de non-régression.
5. Vérifier : `./gradlew check` sans avertissement, puis les tests instrumentés concernés
   (`./gradlew :core:pdf:connectedDebugAndroidTest`, `./gradlew :app:connectedDebugAndroidTest`
   sur appareil, ou `ciGroupDebugAndroidTest` sur les appareils gérés). Après un changement
   visuel voulu : `./gradlew recordRoborazziDebug`, relire les images, puis les committer.
6. **Ne jamais affaiblir un test** (AGENTS §1.6). Un test existant qui casse est corrigé dans le
   code, ou bien sa fausseté est expliquée dans la PR.
7. Commits au format Conventional Commits, en anglais (`fix(reader): …`, `test(pdf): …`). KDoc à
   jour, chaînes FR et EN, aucune dépendance nouvelle sans licence vérifiée et notée dans
   `docs/dependencies.md`.
8. En fin de lot :
   - dans `docs/audits/2026-11-26-audit-complet.md`, passer la ligne du constat de ⏳ à ✅, avec
     la PR. Un constat écarté après vérification porte « ✅ écarté : raison » ;
   - ajouter une entrée « Corrigé » (ou « Modifié ») sous `[Non publié]` dans `CHANGELOG.md` ;
   - créer un ADR (numéro libre suivant, à partir de 0027) pour chaque choix structurant
     signalé « ADR » ci-dessous ;
   - mettre à jour `docs/reprise.md` (§2 et pièges nouveaux).
9. Les points marqués **❓ Décision** (§3) ne se tranchent pas seuls : poser la question au
   mainteneur, puis faire le reste du lot.

## 1. Constats écartés ou corrigés après vérification

| Constat | Vérification |
|---------|--------------|
| Licensee et `koverVerify` non rattachés à `check` | Faux positifs, déjà écartés par l'audit (§8) |
| T4, première moitié : « aucun test pour `ReaderOptionsSheet` ni `PagePreviewCard` » | **Écarté.** La feuille est testée en interaction par `ReaderScreenTest` (« more options opens the display options… », « choosing page by page… », l. 196-250) et en capture (`ReaderScreenshotTest.options`, `optionsDark`). La carte d'aperçu est testée par `FloatingPageBarTest` (« with the preview on, dragging previews the page… », l. 94-130) et par `readerDragging*`. Seul le cas d'erreur manque, il est ajouté par A1 |
| T2 pour `LinksInstrumentedTest` | **En partie faux.** `confirmedExternalLinkIsHandedToABrowsableActivity` (l. 100-114) a une vraie assertion, `intended(matcher)`. Seul `internalLinkActionShowsItsPage` (l. 86-97) n'a qu'un `waitUntil` |
| T3, seconde moitié : « `isNotNull()` seuls » | **Presque entièrement faux.** `PdfThumbnailRendererTest.kt:75`, `DocumentTransitionTest.kt:138` et `:250`, `RoomRecentDocumentsRepositoryTest.kt:265` (précondition) et `:314` sont suivis d'autres assertions. Restent faibles : `ReaderPrintTest.kt:174` et, à la marge, `RoomRecentDocumentsRepositoryTest.kt:436` |
| Numéros de ligne du rapport A (A1, A2) | Faux : `PageRendering.kt` n'a que 61 lignes. Les bons numéros sont donnés dans les tâches |
| Q2 pour `recordOpening` | Le `null` est documenté dans le contrat (`RecentDocumentsRepository.kt:32-34`) : ce n'est pas un échec silencieux. Restent `saveLastPage` (silencieux) ainsi que `remove` et `clear`, dont l'échec se confond avec « absent » ou « liste vide » |

## 2. Lots

| Lot | Branche suggérée | Contenu | Priorité |
|-----|------------------|---------|----------|
| 1 | `fix/unreadable-page` | A1 | Avant toute nouvelle fonctionnalité de M5 |
| 2 | `fix/render-memory` | A2, A3, A4, A5a (annulation d'un rendu en cours) | Haute |
| 3 | `fix/reader-state-and-recents-errors` | Q1, Q2 | Moyenne |
| 4 | `test/audit-2026-11-26` | A5b, T1, T2, T3, T4 | Moyenne |
| 5 | `fix/incoming-intents-and-privacy` | S1, S2, S4 | Basse, S1 avant M6 |
| 6 | `build/quality-guards` | F4, S3, F3, F2, F1, F5 (deux PR si c'est trop gros : S3 + F3, puis le reste) | Moyenne |
| 7 | `refactor/reader-viewmodel` | Q3, Q4 (facultatif) | Basse |

Écarts avec la proposition de départ :
- le test d'annulation d'un rendu en cours (A5a) passe au lot 2, parce que ce lot réécrit le
  rendu et que le test doit porter sur le nouveau chemin ;
- S2 rejoint S1 au lot 5, parce que les deux touchent les intents entrants ;
- S3 rejoint le lot 6, parce qu'il modifie le même code de `build-logic` que F3. F4 passe en
  tête du lot, pour que ce nouveau code soit analysé par detekt.

---

### Lot 1 — Page illisible dans un document qui s'ouvre

#### A1 — Haute — Une page qui ne se charge pas fait planter le lecteur

**Problème.**
- `core/pdf/.../mupdf/MuPdfEngine.kt:107` : `document.countPages()`. MuPDF lit `Root/Pages/Count`
  sans le vérifier (amont `source/pdf/pdf-page.c:47-66`), sauf s'il est négatif ou
  supérieur ou égal à la taille de la table xref.
- `core/pdf/.../mupdf/PageCache.kt:31` : `document.loadPage(index)`. MuPDF lève « cannot find
  page N in page tree » (`pdf-page.c:278`, `:301`), et `translatingMuPdfErrors`
  (`MuPdfErrors.kt:21-22`) le traduit en `PdfError.Unreadable`.
- `core/pdf/.../PdfEngine.kt:62-67` et `:91-104` : le KDoc ne cite que `PageOutOfRange` et
  `Closed`.
- Appelants sans `catch` :
  - `feature/reader/.../ReaderViewModel.kt:284` (`pageSize` de toutes les pages dans `show()`,
    appelé depuis `viewModelScope.launch { open() }`, l. 165) ;
  - `ui/PdfPage.kt:82-84` (`produceState` → `renderer.renderPage`) et `:182`
    (`LaunchedEffect` → `renderPart`) ;
  - `ui/PagePreviewCard.kt:174-176` (`produceState` → `renderPreview`).
  `render/PageRenderer.kt:94-99` laisse passer l'exception. Interceptent déjà l'erreur :
  `Thumbnails.kt:104-110`, `SearchController`, `SelectionController.kt:223-228`, `PageLinks.kt:58`
  et `DocumentPrintAdapter.kt:116-117` (`onWriteFailed`).

**Scénario.** Un PDF déclare `/Count 3` mais n'a que 2 `/Kids`. `open()` réussit avec
`pageCount = 3`, puis `show()` → `pageSize(2)` lève `Unreadable` dans `viewModelScope` : l'app
plante dès l'ouverture. Même plantage si l'erreur survient au rendu, dans `produceState` ou dans
`LaunchedEffect`. C'est contraire à SPEC §3, « Robustesse ».

**Correction attendue.**
1. KDoc de `PdfDocument` (l. 62-67) : toute fonction qui prend un index de page peut lever
   `PdfError.Unreadable` (page endommagée, mémoire native épuisée). Le dire aussi sur
   `pageSize`, `renderPage`, `links`, `selectText` et `isOnText`.
2. `ReaderViewModel.show()` : lire chaque taille avec un repli sur `PdfError.Unreadable`
   **seulement** (ni `Closed` ni annulation) : la taille de la page précédente, ou A4
   (595 × 842 pt) pour la première. Si **toutes** les pages échouent, fermer le document et
   passer à `ReaderUiState.Error(ReaderError.UNREADABLE)`. Extraire une petite fonction
   (`pageSizesOrFallback`) pour rester sous 30 lignes.
3. `PageRenderer` : intercepter `PdfError` autour de `document.renderPage` (l. 98).
   **Choix de conception** : garder `Bitmap?` (`null` = rien à afficher), ou renvoyer un type
   scellé (`Rendered`, `Unreadable`, `OutOfMemory`) pour que l'interface sache si un nouvel
   essai a un sens. Le type scellé est conseillé : il sert aussi à A2. Le bitmap alloué est
   rendu (`recycle`) en cas d'échec.
4. `PdfPage`, `rememberDetail` et `rememberPreview` : une page en erreur s'affiche vierge (fond
   `PAPER` ou `PAPER_NIGHT` déjà dessiné), sans plantage. `ReportDrawnWhen` (`PdfPage.kt:88`)
   doit aussi se déclencher quand la page est en erreur, sinon le démarrage mesuré par
   `:benchmark` n'aboutit jamais.
5. Message visible sur la page en erreur : **❓ Décision D1** (§3). Par défaut, page vierge
   seule.

**PDF de test à ajouter** : `core/testing/src/main/assets/pdfs/page_tree_count_mismatch.pdf`.
- Fonction `page_tree_count_mismatch()` dans `core/testing/scripts/generate_test_files.py`, à
  ajouter à la liste de `main` (l. ~410). Elle écrit le PDF **octet par octet**, avec des
  décalages xref calculés, et non avec pymupdf, qui pourrait réparer `/Count` à
  l'enregistrement. Objets : 1 Catalog ; 2 Pages, `/Kids [3 0 R 4 0 R] /Count 3` ; 3 et 4 Page A4
  avec un flux de contenu (5 et 6) qui écrit « Page 1 » et « Page 2 » ; 7 police Helvetica.
  La taille xref vaut alors 8, supérieure à 3, ce qui évite le refus « Invalid number of pages »
  de `pdf_count_pages`.
- Si MuPDF ne se comporte pas comme prévu (ouverture refusée, ou 2 pages), remplacer par une
  variante : un troisième kid `8 0 R` qui renvoie à un objet absent. Noter le résultat dans le
  README.
- Ajouter la ligne dans `core/testing/README.md` (« `/Count` 3, 2 pages réelles : la page 3 est
  illisible, le document reste lisible ») et la constante `TestFiles.Pdf.PAGE_TREE_COUNT_MISMATCH`.

**Tests à écrire.**
- `core:pdf`, JVM (`MuPdfEngineTest` ou `MuPdfDocumentTest`) :
  - `opens a PDF whose page count exceeds its page tree` : `pageCount == 3` ;
  - `reports a page missing from the page tree as unreadable` : `pageSize(2)` et
    `renderPixels(2, …)` lèvent `PdfError.Unreadable` ;
  - `the document stays usable after an unreadable page` : `pageSize(0)` et le rendu de la
    page 1 réussissent après l'erreur, et `close()` ne signale rien (`RecordingReleaseFailures`).
- `core:pdf`, instrumenté (`MuPdfEngineInstrumentedTest`) : même contrôle avec
  `renderPage(2, bitmap, …)` sur un vrai `Bitmap`.
- `core:testing` : `FakePdfDocument` gagne `var unreadablePages: Set<Int>`, et ses fonctions par
  page lèvent `PdfError.Unreadable` pour ces pages. Test dans `FakePdfDocumentTest`.
- `feature:reader`, ViewModel (`ReaderViewModelTest`) :
  - `a page whose size cannot be read takes the size of the previous page` ;
  - `an unreadable first page takes the A4 size` ;
  - `a document without any readable page shows the unreadable error` (et le document est fermé).
- `feature:reader`, `PageRendererTest` : `renderPage returns no image for an unreadable page and
  caches nothing`, et de même pour `renderPart` et `renderPreview`.
- `feature:reader`, interface (Robolectric, `ReaderScreenTest`, `FloatingPageBarTest`) :
  - `an unreadable page shows blank in continuous mode`, puis en mode page par page (les autres
    pages sont dessinées : `FakePdfDocument.renders`) ;
  - `previewing an unreadable page does not crash` (glissement du curseur sur la page 2).
- `app`, instrumenté (`OpenWithInstrumentedTest`) : ouvrir le nouveau PDF par
  `TestDocumentProvider`, aller à la page 3, vérifier que le lecteur reste affiché.

**Critères d'acceptation.**
- Les tests ci-dessus échouent avant la correction (plantage ou exception), puis passent.
- `./gradlew check` et les tests instrumentés de `core:pdf` et de `app` passent.
- Ouvrir le PDF sur appareil, aller à la page 3, faire glisser le curseur, zoomer : aucun
  plantage. La page 3 est vierge, les pages 1 et 2 sont dessinées.

---

### Lot 2 — Mémoire et fil du rendu

#### A2 — Moyenne — `renderPage` alloue trois fois la cible et seule l'allocation du bitmap est protégée

**Problème.** `PageRendering.kt:38` alloue un `Pixmap` natif de `w × h × 4` octets.
`PageRendering.kt:57` renvoie `pixmap.pixels`, un `IntArray` de même taille créé **sur le tas
Java** par la JNI. `MuPdfDocument.kt:72-73` copie ensuite ces pixels dans le `Bitmap`.
`PageRenderer.kt:102-111` n'intercepte `OutOfMemoryError` qu'autour de `createBitmap`.

**Scénario.** Une page de 595 × 6 000 pt affichée sur 1 440 px donne un rendu de base de
1 440 × 14 500 px, soit 83 Mo. Le bitmap passe dans le tas natif, mais l'`IntArray` échoue
dans le tas Java (192 à 256 Mo au total) avec un `OutOfMemoryError` que rien n'intercepte :
plantage. Si c'est le `malloc` natif qui échoue, la page est déclarée `Unreadable` pour un
simple manque passager de mémoire.

**Correction attendue** (choix à consigner dans un **ADR**, qui amende les ADR 0007 et 0008) :
1. **Filet obligatoire** : intercepter `OutOfMemoryError` autour de `document.renderPage` dans
   `PageRenderer`, comme pour l'allocation. Vider les deux caches et rendre le bitmap alloué.
   Avec le type scellé de A1, renvoyer `OutOfMemory`, pour qu'un essai ultérieur reste possible.
2. **Supprimer la copie pleine taille**, au choix :
   - (a) `com.artifex.mupdf.fitz.android.AndroidDrawDevice`, déjà compilé avec les sources Java
     de MuPDF (`third-party/mupdf/build.gradle.kts:52`, JNI `androiddrawdevice.c`), dessine
     directement dans le `Bitmap`. Limites : il n'est pas testable sur la JVM
     (`libjnigraphics`), et le mode nuit (`invertLuminance` sur le `Pixmap`) est à refaire
     autrement. Vérifier aussi le fond blanc ;
   - (b) des bandes : une `DisplayList` de la page, puis un `Pixmap` de N lignes (≈ 1 Mo) par
     bande, inversé si besoin, et copié par `setPixels` avec un décalage de ligne. Cette option
     reste testable sur la JVM (`renderPixels` assemblé par bandes) et borne la mémoire
     temporaire.
   L'option (b) est conseillée, parce qu'elle garde les tests JVM. Mesurer le temps de rendu
   avant et après (`:benchmark`, défilement continu) et noter les chiffres dans l'ADR.

**Tests.**
- `PageRendererTest` : `an out of memory error while drawing empties the caches and returns no
  image`. Un faux document dont `renderPage` lève `OutOfMemoryError` ; vérifier les caches vides
  et l'absence de plantage, puis le succès d'un nouvel essai.
- Avec (b), en JVM : `renders a page in bands identical to a single pass` (les pixels sont égaux
  pour `mixed_page_sizes.pdf`, mode jour et mode nuit) et `a band boundary inside a glyph is
  seamless`.
- Instrumenté : rendu d'une page très haute (fichier fabriqué dans le test, ou nouveau PDF du jeu
  de test, par exemple `tall_page.pdf` de 595 × 14 400 pt) à 1 440 px de large, sans
  `OutOfMemoryError`.

**Critères.** Aucun `IntArray` ni `Pixmap` à la taille pleine de la cible. Un
`OutOfMemoryError` ne fait plus planter le lecteur. Pas de régression mesurée au-delà de ce que
l'ADR accepte.

#### A3 — Moyenne — Le cache natif de MuPDF (256 Mo) n'est ni borné ni vidé

**Problème.** Le contexte JNI est créé avec `FZ_STORE_DEFAULT = 256 << 20` (amont
`platform/java/jni/context.c:120-157`, `include/mupdf/fitz/context.h:315`), sauf si
`FZ_JAVA_STORE_SIZE` est défini. Ni `third-party/mupdf/build.gradle.kts:36-46` ni
`build-host-jni.sh` ne le définissent. Aucun appel à `Context.shrinkStore` ou `emptyStore`
(`Context.java:79-80`) et aucun `onTrimMemory` dans le dépôt (vérifié par grep). Le budget de
l'ADR 0008 (`BitmapCache.kt:8-11`, `:51` : un quart du tas, 128 Mo au plus) ignore ce cache.

**Scénario.** Sur un document numérisé (images de 26 Mo décodées), l'empreinte native dépasse
400 Mo sur un appareil de 3 à 4 Go. Le processus est tué en arrière-plan.

**Correction attendue.**
1. Borner le cache à la compilation sans toucher l'`Android.mk` d'Artifex : ajouter
   `MUPDF_EXTRA_CFLAGS=-DFZ_JAVA_STORE_SIZE=<octets>` aux arguments `ndkBuild`. La variable est
   documentée par l'`Android.mk` amont (l. 13) et appliquée au module `mupdf_java` (l. 251).
   Ajouter le même `-D` à la commande `cc` de `mupdf_native.c` dans `build-host-jni.sh`. Taille
   à fixer et justifier dans l'ADR (64 ou 96 Mo).
2. Exposer dans `PdfEngine` une fonction sans type MuPDF, par exemple
   `fun trimMemory(level: MemoryPressure)`, avec `MemoryPressure` dans `core:domain` ou
   `core:pdf`. Elle appelle `Context.shrinkStore(percent)` ou `emptyStore()`. **Attention au fil
   d'exécution** : le store est partagé par tous les contextes clonés, donc ces appels statiques
   passent par le contexte de base. Vérifier dans `context.c` sur quel thread on peut les
   appeler, et le documenter dans le KDoc.
3. Brancher `ComponentCallbacks2.onTrimMemory` dans `:app` (`LutrinApplication`, ou un
   `ComponentCallbacks2` enregistré par le module DI). Y vider aussi les `BitmapCache` du lecteur
   ouvert si c'est simple, sinon le laisser hors périmètre et le noter.
4. ADR : taille choisie, mesure de l'empreinte (`adb shell dumpsys meminfo`) sur
   `scanned_images_only.pdf` et `large_600pages.pdf` avant et après, temps de rendu.

**Tests.**
- JVM `core:pdf` : `trimMemory does not break an open document` (rendu, `trimMemory`, nouveau
  rendu identique) et `trimMemory with no document open is harmless`.
- `app` (Robolectric) : `onTrimMemory forwards the pressure to the engine`, avec un faux
  `PdfEngine` qui enregistre les appels.
- Vérification du build : une tâche ou un test qui contrôle que le drapeau est passé à
  `ndkBuild`, ou à défaut une vérification manuelle (`strings` ou une valeur lue par JNI)
  documentée dans l'ADR.

**Critères.** Le cache est borné à la valeur de l'ADR, et l'empreinte mesurée baisse. Le
lecteur réagit à `onTrimMemory`.

#### A4 — Basse — La copie des pixels se fait sur le thread principal

**Problème.** `MuPdfDocument.kt:73` (`target.setPixels`) tourne hors de `onDocumentThread`.
`PageRenderer.kt:98` appelle `renderPage` hors du `withContext(dispatcher)` (l. 95-97), qui ne
couvre que l'allocation. Les appelants sont `produceState` et `LaunchedEffect`, donc le
`AndroidUiDispatcher`. C'est contraire à AGENTS §3.

**Correction attendue.** Si A2 dessine directement dans le `Bitmap` (option a) ou par bandes
sur le fil du document, la copie quitte déjà le thread principal. Sinon, déplacer
`setPixels` dans `onDocumentThread`. Dans les deux cas :
- KDoc de `renderPage` : « `target` est écrit sur le fil du document ; l'appelant ne le lit, ne
  le recycle ni ne le réutilise avant le retour » ;
- vérifier l'impression (`DocumentPrintAdapter.kt:165-180`, bitmap réutilisé puis recyclé
  après le retour) et `Thumbnails.kt:116-123`.

**Tests.** `PageRendererTest` ou `MuPdfDocumentTest` : `renderPage writes the target off the
calling thread`. Un `Bitmap` sous Robolectric ou un faux document qui note le thread de
l'écriture ; l'appel vient de `Dispatchers.Main` sous test, et la copie ne doit pas y avoir lieu.

**Critères.** Aucun travail par pixel sur le thread principal. Pas de régression des benchmarks
de défilement (P90, et P99 si le benchmark le donne).

#### A5a — Basse — Le test d'annulation ne prouve pas qu'un rendu **en cours** est interrompu

**Problème.** `core/pdf/src/test/.../MuPdfCancellationTest.kt:18-33` annule le rendu aussitôt
après `async`, probablement avant que le rendu n'atteigne le fil du document. Le test passerait
encore sans `cookie.abort()` (`MuPdfDocument.kt:93-94`).

**Test à écrire** (sur le chemin de rendu issu de A2) : `cancelling a rendering in progress
aborts it`.
- Lancer un rendu à ×8 de `scanned_images_only.pdf`. Attendre qu'il ait commencé, sans
  `sleep` : un `DocumentDispatcherFactory` de test qui signale l'entrée de la tâche, ou une
  attente sur `Cookie.progress` si elle est exposée.
- Annuler, puis mesurer qu'un `pageSize(1)` revient bien avant la durée d'un rendu complet,
  mesurée au préalable dans le même test. Garder une marge large pour la CI, ou comparer à un
  multiple de cette durée.
- Contre-épreuve à faire **une fois à la main**, et à noter dans la PR : le test échoue si
  l'on commente `cookie.abort()`.
- Remarque de l'audit A : l'abandon n'a lieu qu'après un passage par le dispatcher de
  l'appelant. Utiliser `invokeOnCompletion(onCancelling = true)` si le test le montre.

---

### Lot 3 — État du lecteur et erreurs des récents

#### Q1 — Moyenne — `ReaderUiState.Ready` porte des objets à état et un dispatcher codé en dur

**Problème.** `feature/reader/.../ReaderUiState.kt:59-70` porte `renderer: PageRenderer` (caches
de bitmaps) et `links: PageLinks` (cache synchronisé). L'UiState n'est donc pas immuable
(AGENTS §3). La valeur par défaut `PageLinks(document = null, dispatcher = Dispatchers.Unconfined)`
(l. 66) code un dispatcher en dur. Les deux objets sont construits dans
`ReaderViewModel.kt:289-295` et lus dans `ui/ReaderDocumentArea.kt:95, 97, 165, 182, 269`.
13 fichiers de test construisent `ReaderUiState.Ready(…)`.

**Correction attendue.**
1. **Obligatoire** : supprimer la valeur par défaut `Dispatchers.Unconfined`. Les tests passent
   un dispatcher de test, au moyen d'une fabrique de test `readyState(…)` dans les sources de
   test de `feature:reader`, pour limiter les modifications.
2. **Choix de conception (ADR)** pour sortir les objets à état :
   - (a) un porteur `DocumentPages(renderer, links)`, annoté `@Stable`, exposé par le
     ViewModel à côté de l'UiState (par exemple `StateFlow<DocumentPages?>`), créé à
     l'ouverture et libéré à la fermeture. `Ready` ne garde alors que des données immuables ;
   - (b) garder les objets dans `Ready`, mais derrière une poignée `@Immutable` à égalité
     d'identité, et documenter l'écart dans un ADR.
   L'option (a) est conseillée, parce qu'elle est conforme à AGENTS §3.
3. Faire ce lot **après** le lot 1, qui modifie déjà `PageRenderer` et `PdfPage`.

**Tests.** `ReaderViewModelTest` : `ready state holds no renderer` (garde-fou, si l'option (a)
est retenue) ; `the document pages are released when the reader closes`. Les tests d'écran
existants passent sans changement de comportement, et les captures Roborazzi restent
identiques.

**Critères.** Aucun `Dispatchers.*` dans `feature:reader/src/main` hors injection (grep).
`ReaderUiState` est une `data class` d'immuables. `check` passe, sans nouvelle image Roborazzi.

#### Q2 — Moyenne — Échecs de Room avalés dans les récents

**Problème.** `core/data/.../recents/RoomRecentDocumentsRepository.kt` :
- `:105` (`saveLastPage`) : l'échec est silencieux ;
- `:115` (`remove`) : renvoie `false`, comme si l'entrée n'existait pas ;
- `:127` (`clear`) : renvoie `0`, comme une liste vide ;
- `:96` (`recordOpening`) : `null`, documenté par le contrat (voir §1).
C'est contraire à AGENTS §4 (« `Result` ou `sealed` ; pas d'exception silencieusement
avalée »).

**Correction attendue.**
1. Dans `core:domain` (`recents/RecentDocumentsRepository.kt`), distinguer l'échec de stockage.
   Par exemple `Result<Boolean>`, `Result<Int>` et `Result<Unit>` avec une
   `RecentsError.StorageUnavailable` scellée, ou un type `RecentsWrite` dédié. Le choix et son
   KDoc sont à documenter ; faire de même pour `recordOpening`, par cohérence, si cela reste
   simple.
2. Mettre à jour les cas d'usage (`RecentDocumentsUseCases.kt:28, 59, 112, 123`), le faux
   `FakeRecentDocumentsRepository` (qui gagne un interrupteur `failWrites`), `ReadingHistory`,
   `LibraryViewModel` et `SettingsViewModel`.
3. Les appelants gardent le comportement actuel (le document reste ouvert, la page n'est pas
   gardée). Afficher un message : **❓ Décision D2**.
4. Ne rien journaliser du contenu ni de l'URI. Au plus le type de l'exception, et en debug
   seulement (AGENTS §1.8).

**Tests.** `RoomRecentDocumentsRepositoryTest` (base fermée ou `SupportSQLiteOpenHelper`
défaillant) : `saveLastPage reports a storage failure`, `remove reports a storage failure
instead of not found`, `clear reports a storage failure instead of an empty list`. Tests des cas
d'usage et des ViewModels avec `failWrites = true`. Couverture de `core:data` et `core:domain`
au moins à 80 %.

**Critères.** Plus aucun `catch (SQLiteException)` qui renvoie une valeur ambiguë. Contrats
documentés. Tests verts.

---

### Lot 4 — Tests manquants

#### A5b — Basse — Tests promis par l'audit M1, toujours absents

1. **Annulation d'une recherche longue** (`MuPdfDocumentTest`) : `cancelling a search stops
   before the last page`. Collecter `search("lutrin")` sur `large_600pages.pdf`, annuler après
   la première émission, puis vérifier que le nombre de pages émises est bien inférieur à 600 et
   que le document reste utilisable.
2. **Éviction du `PageCache`** (`PageCacheTest`) : `evicting a page destroys it`. Avec
   `capacity = 2` : `val first = cache[0]; cache[1]; cache[2]`, puis `first.bounds` doit lever
   une exception (« cannot use already destroyed Page », amont `jni/wrap.c:966`). Le test
   existant (l. 41-50) ne vérifie que l'ordre.
3. **`FromUri` avec permission révoquée** : `a revoked permission reports the document as not
   accessible`. Un `ContentOpener` qui lève `SecurityException`, puis `FileNotFoundException`,
   doit donner `PdfError.NotAccessible` (`MuPdfEngine.kt:127-157`). Difficulté : il faut un
   `android.net.Uri` sur la JVM, et les tests de `core:pdf` ne sont pas Robolectric (le
   `android.jar` de test ne construit pas d'`Uri`). Options :
   - appliquer `lutrin.android.robolectric` à `core:pdf` pour cette seule classe, après avoir
     vérifié que la bibliothèque JNI hôte se charge sous Robolectric ;
   - ou écrire un test instrumenté (`MuPdfEngineInstrumentedTest`) avec un `ContentOpener` qui
     lève l'exception.
   Pas de MockK sans l'ajouter à `docs/dependencies.md`.

#### T1 — Moyenne — Un seul test `AccessibilityChecks`

**Problème.** `app/src/androidTest/.../MainActivityInstrumentedTest.kt:22-31` est le seul test,
sur la bibliothèque vide. AGENTS §5.1 demande les écrans principaux.

**Correction.** Activer `composeRule.enableAccessibilityChecks()` et appeler
`onRoot().tryPerformAccessibilityChecks()` dans les tests instrumentés de `app` :
- lecteur affiché (`SearchInstrumentedTest` ou un nouveau `ReaderAccessibilityInstrumentedTest`) ;
- barre de recherche ouverte avec des résultats ;
- sélection avec poignées (`SelectionInstrumentedTest`) ;
- dialogue de lien externe V7 (`LinksInstrumentedTest`) ;
- dialogue « Aller à la page » ;
- dialogue de mot de passe V4 (`TestFiles.Pdf.ENCRYPTED` par `TestDocumentProvider`) ;
- écran d'erreur V8 (`CORRUPTED_GARBAGE`) ;
- réglages E3 (navigation depuis la bibliothèque) ;
- bibliothèque avec au moins un récent.

Un défaut trouvé par ATF se corrige dans l'interface (cible de 48 dp, contraste, description).
Un faux positif avéré (par exemple sur l'image rendue de la page PDF) peut être écarté par un
`setSuppressingResultMatcher` **ciblé et commenté**, cité dans la PR. On n'en supprime jamais
une catégorie entière.

**Critères.** Au moins un contrôle ATF par écran ou dialogue de la liste, verts sur API 26 et 37
(appareils gérés `ci`).

#### T2 — Moyenne — Tests instrumentés sans assertion explicite

**Problème.** `SearchInstrumentedTest.kt:61-77` (2 tests), `SelectionInstrumentedTest.kt:68-87`
(2 tests) et `LinksInstrumentedTest.kt:86-97` (1 test, voir §1) ne vérifient que par
`waitUntil` : un échec n'apparaît que comme un dépassement de délai.

**Correction.** Garder le `waitUntil` pour la synchronisation, puis affirmer explicitement :
- recherche : `onNodeWithText(counter).assertIsDisplayed()`, et pour le document numérisé,
  `onNodeWithText(noResult).assertIsDisplayed()` ;
- sélection : `assertThat(clipboardText()).contains(ARABIC_WORD)`. Pour la copie interdite,
  placer d'abord un texte témoin dans le presse-papiers, puis vérifier qu'il **n'a pas changé**,
  en plus du message (c'est le vrai contrat, ADR 0024) ;
- lien interne : `onNodeWithContentDescription(document(3)).assertIsDisplayed()`, ou
  l'indicateur de page.

#### T3 — Basse — Captures sans garde-fou si la vérification est désactivée

**Problème.** Tous les tests Roborazzi (et pas seulement Selection, ExternalLinkDialog,
GoToPageDialog et PasswordDialog) n'ont d'effet que grâce à `roborazzi.test.verify=true`
(`gradle.properties:12`). Il est aussi faible d'affirmer seulement
`assertThat(jobs.single()).isNotNull()` (`feature/reader/.../ReaderPrintTest.kt:174`).

**Correction.**
1. CI : après `./gradlew check`, ajouter une étape qui échoue si des références de capture ont
   changé ou sont apparues :
   `git status --porcelain -- '**/src/test/screenshots/**'` doit être vide.
2. Vérifier localement ce que fait Roborazzi en mode vérification quand une référence manque
   (supprimer une image, lancer le test). S'il ne fait pas échouer le test, l'étape 1 le couvre ;
   le noter dans `CONTRIBUTING.md`.
3. `ReaderPrintTest.kt:174` : affirmer le contenu du travail (nom, type de source). Faire de même,
   si c'est utile, pour `RoomRecentDocumentsRepositoryTest.kt:436` : le chemin est dans le
   dossier des vignettes et le fichier existe.

**Critères.** Une modification volontaire d'une capture, sans la committer, fait échouer la CI.

#### T4 — Basse — Tests instrumentés de la CI limités à `:app` et `:core:pdf`

La première moitié est écartée (§1). Reste `ci.yml:114` : seuls ces deux modules ont un
`src/androidTest`, et la CI lance exactement ceux-là. Petite tâche : faire en sorte qu'un futur
`src/androidTest` d'un autre module soit lancé automatiquement. Par exemple, lancer la tâche
racine `ciGroupDebugAndroidTest` après avoir vérifié qu'un module sans tests instrumentés ne la
fait pas échouer, ou ajouter une vérification qui compare la liste de la CI aux modules qui ont
un `src/androidTest`. Savoir si les écrans de `feature:*` doivent avoir des tests instrumentés
en plus de Robolectric : **❓ Décision D3**.

---

### Lot 5 — Intents entrants et vie privée

#### S1 — Basse (latent, à faire avant M6) — Contrôle « URI de Lutrin » contournable

**Problème.** `core/domain/.../document/ParseIncomingDocumentUseCase.kt:127-140` compare
l'autorité brute. L. 131 : `substringAfter('@')` coupe au **premier** `@`, alors qu'Android coupe
au dernier. L'autorité n'est pas décodée, alors qu'Android résout le fournisseur avec
`Uri.getAuthority()`, qui l'est. Ainsi `content://io.github.myxomatom.lutrin%2Eprovider/x` et
`content://a@b@io.github.myxomatom.lutrin.provider/x` passent le contrôle de la l. 137.

**Correction.** Dans le domaine (Kotlin pur), refuser comme `MALFORMED_URI` toute autorité qui
contient `%` ou plus d'un `@`, puis couper avec `substringAfterLast('@')`. Le refus est plus sûr
et plus simple qu'un décodage. Garder la comparaison actuelle ensuite. Amender l'ADR 0014 d'une
ligne.

**Tests** (`ParseIncomingDocumentUseCaseTest`) :
- rejet : `%2E` et `%2e` dans l'autorité, `a@b@<pkg>.provider`, `10@<pkg>.provider` (toujours
  `APP_OWN_URI`) ;
- acceptation : `10@com.android.providers.downloads.documents` ;
- cas limites : `content://@/x` (vide) et `content://<pkg>` sans chemin.

#### S2 — Basse — Extras lus sans protection dans l'activité exportée

**Problème.** `app/src/main/kotlin/.../IncomingIntents.kt:28` (`IntentCompat.getParcelableExtra`)
et `:35-36` (`hasExtra`, `getIntExtra`), appelés depuis `MainActivity.kt:77`. Un extra
`Parcelable` inconnu peut lever `BadParcelableException` (ou une autre `RuntimeException`) au
dépliage du `Bundle` : l'activité exportée plante. Le risque est réel sur API 26 à 32 ; à partir
de l'API 33, le dépliage est paresseux.

**Correction.** Lire les extras dans un `try/catch (RuntimeException)` limité à cette lecture
(commentaire : déni de service par un intent hostile). En cas d'échec, `stream = null` et
`page = null` ; `action`, `data` et `type` restent lus, car ils ne touchent pas aux extras.
`ACTION_SEND` donne alors le refus habituel « aucun document ».

**Tests** (Robolectric, `IncomingIntentsTest`) : une sous-classe de test d'`Intent` dont
`hasExtra` et `getParcelableExtra` lèvent `BadParcelableException`.
- `an intent whose extras cannot be read keeps its data and has no page` ;
- `a share whose extras cannot be read has no stream` ;
- `MainActivityIncomingIntentTest` : l'activité ne plante pas.
Essai manuel facultatif sur API 26 : `adb shell am start` avec un extra sérialisé inconnu.

#### S4 — Basse — Document protégé visible dans les applications récentes ; `mailto:?attach=`

1. **Aperçu des récents** : aucun `FLAG_SECURE` ni `setRecentsScreenshotEnabled(false)` dans le
   dépôt (grep). Un document ouvert avec son mot de passe apparaît dans la vignette des
   applications récentes. Comportement : **❓ Décision D4**. Mise en œuvre une fois la décision
   prise :
   - dans `ReaderScreen`, un `DisposableEffect(isProtected)` qui pose le drapeau sur la fenêtre
     de l'activité (`LocalActivity`) et le retire en quittant ;
   - le ViewModel expose seulement `isProtected` dans l'état, et ne touche jamais la fenêtre ;
   - tests Robolectric : le drapeau est posé pour un document protégé, absent sinon, retiré au
     retour à la bibliothèque. Amender l'ADR 0017.
2. **`mailto:` avec pièce jointe** : `core/domain/.../links/CheckExternalLinkUseCase.kt:14` et
   `:83-90` acceptent tout `mailto:` non vide. Refuser un `mailto:` dont la requête contient un
   paramètre `attach` ou `attachment`, sans tenir compte de la casse, **après** décodage
   pourcent du nom de paramètre (`%61ttach`). Tests : `mailto:a@b.c?attach=/sdcard/x`,
   `?ATTACH=`, `?subject=x&attachment=y` et `?%61ttach=` sont refusés ; `?subject=Bonjour` est
   accepté. Amender l'ADR 0022.

---

### Lot 6 — Garde-fous de build et de CI

Commencer par F4, pour que le code ajouté ensuite dans `build-logic` soit analysé.

#### F4 — Moyenne — `build-logic` n'est pas analysé par detekt

**Problème.** `build-logic/convention/build.gradle.kts:4-7` n'applique que `kotlin-dsl` et
ktlint ; detekt n'y est qu'en `compileOnly` (l. 29).

**Correction.** Appliquer `alias(libs.plugins.detekt)` avec `config/detekt/detekt.yml`. Garder
le classpath de detekt sur `detektKotlin` (2.0.21), comme `Quality.kt:93-98`, et le
`jvmTarget` du projet (`Quality.kt:99-103`). Brancher la tâche sur le `check` du build principal,
par exemple `tasks.named("check") { dependsOn(gradle.includedBuild("build-logic").task(":convention:detekt")) }`,
et vérifier qu'elle tourne bien dans `./gradlew check --dry-run`. Corriger les constats ; aucune
baseline.

**Critères.** Une violation volontaire dans `build-logic` (fonction de 60 lignes) fait échouer
`./gradlew check`. On retire ensuite la violation.

#### S3 — Basse — Permissions en liste noire, aucun garde-fou contre GMS, composants exportés et `Log`

**Problème.**
- `build-logic/.../permissions/ForbiddenPermissions.kt:13-23` : 8 noms interdits. `:26` lit
  `uses-permission` et `uses-permission-sdk-23`.
- `AndroidApplicationConventionPlugin.kt:60` : Licensee seul ne garantit pas l'absence de
  GMS ou de Firebase (certains POM déclarent Apache-2.0).
- Aucun contrôle des composants exportés du manifeste fusionné.
- `config/detekt/detekt.yml` n'a aucune règle contre `android.util.Log` ni `println`.

**Correction.**
1. **Liste blanche** des permissions du manifeste fusionné. Autorisée seulement :
   `<applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` (ajoutée par `androidx.core` ;
   lire l'`applicationId` de la variante). Toute autre permission fait échouer la tâche, avec le
   même message d'aide. Garder la liste noire comme message explicite pour les permissions
   réseau.
2. **Composants exportés** : faire échouer `check` sur tout `activity`, `service`, `receiver` ou
   `provider` en `android:exported="true"` hors liste blanche. Liste de départ : `MainActivity`,
   plus `androidx.profileinstaller.ProfileInstallReceiver` **si** le manifeste fusionné la
   contient, elle qui est protégée par `android.permission.DUMP`. L'inspecter d'abord :
   `./gradlew :app:processReleaseMainManifest`, puis le fichier sous
   `app/build/intermediates/merged_manifest/release/`. Corriger la phrase « seul composant
   exporté » de l'ADR 0014 si besoin.
3. **GMS et Firebase** : une tâche branchée sur `check` qui échoue si
   `releaseRuntimeClasspath` contient un groupe `com.google.android.gms`, `com.google.firebase`
   ou `com.google.android.play`.
4. **detekt** : `style>ForbiddenImport` sur `android.util.Log`, avec une exception pour
   `ReleaseFailureReporter.kt`, seul `Log.w` légitime. Attention : `ForbiddenMethodCall` exige la
   résolution de types, et la tâche `detekt` lancée par `check` ne la fait pas. Pour `println`
   et `printStackTrace`, choisir une règle qui fonctionne sans résolution de types, ou une
   petite tâche de recherche par motif. Vérifier dans tous les cas avec une violation volontaire.

**Tests** (`build-logic/convention/src/test`, déjà doté de JUnit, de Truth et de TestKit) :
manifestes de test contenant `READ_PHONE_STATE`, `uses-permission-sdk-23`, la permission
dynamique autorisée, un `receiver` exporté inconnu ou un `MainActivity` seul ; classpath de test
avec un artefact GMS fictif. Contre-épreuve manuelle : ajouter
`<uses-permission android:name="android.permission.WAKE_LOCK"/>` au manifeste de `app`, puis
`./gradlew check` échoue.

#### F3 — Moyenne — Permissions vérifiées sur la seule variante release

**Problème.** `AndroidApplicationConventionPlugin.kt:45` :
`onVariants(selector().withBuildType("release"))`. Le debug est pourtant publié par la CI
(`ci.yml:61-72`) et embarque `leakcanary-android` et `ui-test-manifest`
(`app/build.gradle.kts:179`, `:193`).

**Correction.** Appliquer le contrôle à **toutes** les variantes, au moins à `debug`. Avant cela,
inspecter `./gradlew :app:processDebugMainManifest`. Si LeakCanary ou un autre outil de debug
apporte des permissions non réseau, la conduite à tenir est la **❓ Décision D5**. Les
permissions réseau restent interdites partout (AGENTS §1.2). Mettre à jour le commentaire de
`app/src/main/AndroidManifest.xml:3-4`.

**Critères.** `./gradlew check --dry-run` liste `verifyDebugPermissions`, ou son équivalent. Une
permission `INTERNET` ajoutée par `debugImplementation` fait échouer `check`.

#### F2 — Moyenne — ktlint ne lit pas `app/src/benchmark/kotlin`

**Problème.** Le dossier est ajouté par l'API des variantes (`app/build.gradle.kts:151-153`,
`addStaticSourceDirectory`), et le plugin ktlint ne lit que les ensembles de sources du DSL.
L'ADR 0012 (l. 115-120) prévoit une vérification manuelle.

**Correction.** Faire lire ce dossier par ktlint de façon permanente, au choix :
- le déclarer dans le DSL, dans les ensembles de sources des types de build `benchmarkRelease`
  et `nonMinifiedRelease` (`android.sourceSets.matching { it.name in benchmarkBuildTypes }`),
  ces types étant créés par le plugin Baseline Profile, puis retirer `addStaticSourceDirectory`
  si c'est redondant ;
- ou enregistrer une tâche `KtLintCheckTask` dédiée, rattachée à `check`.
Mettre à jour l'ADR 0012 en supprimant la procédure manuelle.

**Critères.** Une faute de formatage volontaire dans
`app/src/benchmark/kotlin/.../BenchmarkReaderActivity.kt` fait échouer `./gradlew check`. Le
build release ne contient toujours pas l'activité (`verifyReleaseNoBenchmarkEntryPoint`).

#### F1 — Moyenne — Dokka tolère les avertissements dans `:app`

**Problème.** `build-logic/.../Quality.kt:127-132` : `failOnWarning.set(path != ":app")`, à
cause d'un lien KDoc non résolu dans androidx (`Lifecycle.State` dans `MenuHost`, hérité par
`MainActivity`). C'est contraire à AGENTS §6 et à l'intitulé de `ci.yml:58`.

**Correction.** Pistes à essayer dans l'ordre, en mesurant à chaque fois :
1. Rendre `MainActivity` `internal`. Elle reste publique en bytecode, donc compatible avec le
   manifeste et Hilt, mais Dokka, qui ne documente que le public par défaut, ne la traite plus.
   Vérifier `@AndroidEntryPoint`, les tests et Lint.
2. `suppressInheritedMembers` ou `documentedVisibilities` au niveau du module `:app`.
3. Une mise à jour de Dokka ou d'androidx qui corrige le lien.
Si rien ne marche : **❓ Décision D6**.

**Critères.** `failOnWarning` à `true` partout, et `./gradlew :dokkaGenerate` passe.

#### F5 — Basse — Hygiène du build

1. **`jvmTarget` du catalogue inutilisé** (`gradle/libs.versions.toml:11`). JVM 17 est codé en
   dur dans `build-logic/convention/build.gradle.kts:12-13` et `:18`, dans
   `lutrin/convention/KotlinAndroid.kt:13` et dans `third-party/mupdf/build.gradle.kts:63-64`.
   Lire `libs.versions.jvmTarget` partout (dans `build-logic/convention/build.gradle.kts`, le
   catalogue est accessible), ou supprimer la clé et commenter la source unique.
2. **`androidx-test-espresso-accessibility`** (`libs.versions.toml:131`) n'est référencé nulle
   part. Le supprimer après T1, qui utilise les contrôles Compose (`ui-test-junit4-accessibility`),
   puis mettre à jour `docs/dependencies.md`.
3. **`persist-credentials`** : ajouter `with: persist-credentials: false` aux deux
   `actions/checkout` (`ci.yml:29`, `:91`). Vérifier que `./scripts/init-mupdf.sh`, qui clone
   des dépôts publics, fonctionne toujours.
4. **`LongMethod` à 40** (`config/detekt/detekt.yml:17-19`) contre « ≈ 30 » dans AGENTS §4 :
   **❓ Décision D7**, liée à Q3.

---

### Lot 7 — Qualité (facultatif)

#### Q3 — Basse — `ReaderViewModel` et composables trop longs

**Constat vérifié.** `ReaderViewModel.kt` fait 357 lignes, porte
`@Suppress("LongParameterList", "TooManyFunctions")` (l. 76) et partage un état mutable
(`document`, `isProtected`, `printName`, l. 95-104) avec `LinkController`, `SearchController`
et `SelectionController` par des lambdas (l. 116-140). Il y a 28 `@Suppress("LongParameterList")`
dans `src/main`, et 13 composables dépassent 35 lignes (rapport D, M5).

**Correction suggérée.**
- Extraire une `DocumentSession` (ouverture, document, protection, fermeture) injectée dans les
  trois contrôleurs ; elle se combine bien avec le porteur de Q1 (a).
- Regrouper les callbacks des composables en objets d'actions, comme `SearchBar` le fait déjà.
- Découper les composables longs.
Aucun changement de comportement : les tests existants et les captures ne bougent pas.

#### Q4 — Basse — Types Android dans les ViewModels, `@Suppress` sans justification

- `ReaderViewModel.kt:4-5, 253, 273, 344` (`android.net.Uri`, `toUri`, `Uri.decode`). Déplacer
  `displayName` dans le domaine en reproduisant exactement `Uri.decode` : **pas**
  `URLDecoder`, qui décode `+` en espace. Le tester avec `a+b%20c.pdf`. Pour `FromUri`, choisir
  entre une fabrique de `DocumentSource` injectée et un `DocumentSource` à partir d'une
  `String` dans `core:pdf`. Ce second choix modifie l'API du moteur, donc un ADR.
- `LibraryViewModel.kt:4-5, 140` : `loadThumbnail` expose `ImageBitmap` et `IntSize`. Donner le
  `ThumbnailLoader` à l'écran (paramètre ou `CompositionLocal`) au lieu de passer par le
  ViewModel.
- `core/domain/.../pdf/PdfDates.kt:25` : `@Suppress("SwallowedException")` sans commentaire.
  Ajouter la justification : une date invalide est un résultat attendu, `null` est documenté.

---

## 3. Décisions à demander au mainteneur

| # | Question | Éléments |
|---|----------|----------|
| D1 | A1 : une page illisible est-elle seulement vierge, ou affiche-t-elle un texte du genre « Cette page ne peut pas être affichée » ? | Avec un texte, il faut une chaîne FR et EN, de nouvelles captures (clair, sombre, police 200 %) et un ADR, aucune maquette n'existant pour cet état. La page vierge suffit pour SPEC §3 |
| D2 | Q2 : faut-il afficher un message quand « Retirer des récents » ou « Vider les récents » échoue faute de stockage ? | Aujourd'hui, rien ne s'affiche. Un message demande des chaînes FR et EN et un test d'écran. Sinon, comportement inchangé avec des types d'erreur explicites |
| D3 | T4 : les écrans de `feature:*` doivent-ils avoir des tests Compose instrumentés en plus des tests Robolectric ? | AGENTS §5.1 place les tests UI dans `src/androidTest`, mais le projet les écrit sous Robolectric (plus rapide, captures). Si Robolectric suffit, le noter dans un ADR ou dans AGENTS |
| D4 | S4 : pour un document protégé, `FLAG_SECURE` (pas de capture d'écran, pas de partage d'écran, vignette des récents masquée), `setRecentsScreenshotEnabled(false)` (API 33+, vignette masquée seulement), ou rien, la limite étant documentée dans l'ADR 0017 ? | `FLAG_SECURE` gêne l'utilisateur qui veut capturer son propre document, et les essais manuels. Faut-il un réglage dans E3 ? |
| D5 | F3 : si un outil de debug (LeakCanary) ajoute une permission non réseau au manifeste debug, faut-il l'accepter dans une liste blanche propre au debug, la retirer avec `tools:node="remove"`, ou retirer l'outil ? | Les permissions réseau restent interdites partout (AGENTS §1.2) |
| D6 | F1 : si aucune piste ne supprime l'avertissement Dokka de `:app`, faut-il garder l'exception, documentée dans un ADR avec un signalement amont, ou rendre `:dokkaGenerate` bloquant malgré tout ? | AGENTS §6 exige « sans avertissement » |
| D7 | F5 : faut-il abaisser `LongMethod` à 30, et donc découper les composables (Q3), ou garder 40 en l'inscrivant comme écart dans un ADR ? | Avant de décider, compter les fonctions concernées en lançant detekt avec un seuil à 30 sur une copie locale |
| D8 | A3 : quelle taille donner au cache natif de MuPDF (64 Mo, 96 Mo, ou une valeur fonction de la RAM de l'appareil, ce qui impose un appel à l'exécution plutôt qu'une constante de compilation) ? | Proposer des mesures avant de trancher |

## 4. Hors périmètre ou suivi ailleurs

- **X1** (`feature/reader/.../print/ImagePdfWriter.kt` écrit un PDF hors de `PdfWriter` et de
  `core:pdf`) : l'orchestrateur amende l'ADR 0026. La migration de l'écrivain derrière
  `PdfWriter`, dans `core:pdf`, est **à faire en M6**, avec l'interface `PdfWriter`. Ne rien
  migrer dans ces lots, mais ne pas aggraver l'écart : les lots 1 et 2 ne touchent à
  `DocumentPrintAdapter` que pour le KDoc de A4.
- **S5** (écran E4 « À propos et licences », notices Apache, MIT, IJG et OFL) : jalon **M5**,
  bloquant pour la v1.0.
- **A6** (URI non adressables, repli par copie dans `cacheDir`) : point ouvert **n° 11** de
  `docs/open-questions.md`, échéance repoussée à la publication v1.0.
- **B1 à B5** (documentation) : corrigés dans une PR de documentation séparée. Ne pas les
  reprendre ici, en dehors des mises à jour liées à chaque lot : CHANGELOG, ADR amendés,
  `reprise.md`.
- Constats des rapports détaillés absents du rapport consolidé (par exemple
  `failOnNoDiscoveredTests` pour tous les `feature:*`, ou les tests moteur sur
  `Dispatchers.Default`) : hors de ce plan.
