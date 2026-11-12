# 0007 — Intégration de MuPDF : compilation et interface `PdfEngine`

- **Statut** : accepté
- **Date** : 2026-11-03
- **Références** : ADR 0001, `SPEC.md` §3 et §4.2, `AGENTS.md` §3, §5 et §9, `ROADMAP.md` M1

## Contexte

L'ADR 0001 retient MuPDF compilé depuis les sources. Il reste à décider comment le compiler
(reproductible, compatible F-Droid), comment l'isoler et comment le tester sans dépendre
uniquement d'un émulateur, alors que la couverture Kover ne compte que les tests JVM.

## Décisions

1. **Sous-module Git** `third-party/mupdf/libmupdf`, épinglé sur le tag **1.28.6**
   (`da5f5eb89`). Ses propres sous-modules ne sont récupérés que pour les 11 bibliothèques
   que la compilation Android utilise (`scripts/init-mupdf.sh`) : OCR (Tesseract, Leptonica),
   codes-barres, curl et freeglut ne sont ni récupérés ni compilés.
2. **Module Gradle `:third-party:mupdf`** : bibliothèque Android qui compile les liaisons Java
   (`platform/java/src`) et la bibliothèque JNI avec **ndk-build** (`platform/java/Android.mk`
   d'Artifex, sans modification), NDK **28.2.13676358** (celui avec lequel Artifex construit
   `mupdf-android-fitz` 1.28), ABI `arm64-v8a`, `armeabi-v7a`, `x86_64`. Les ressources
   embarquées sont générées par `make generate`. Nos règles de qualité ne s'y appliquent pas
   (code tiers) ; seul `core:pdf` en dépend.
3. **Plutôt que `mupdf-android-fitz`** (dépôt Artifex qui ne fait qu'envelopper ces mêmes
   fichiers dans un projet Gradle 8 / AGP 8.5) : un sous-module de moins, et le build suit
   nos versions d'AGP.
4. **Tests sur la JVM** : `:third-party:mupdf:buildMupdfHostJni` compile la même bibliothèque
   JNI pour la machine de build (Linux, `build-host-jni.sh`). Les tests unitaires de `core:pdf`
   pilotent ainsi le vrai moteur sur tout le jeu de test, rapidement et en comptant dans la
   couverture. Des tests instrumentés vérifient en plus la bibliothèque Android, les URI de
   contenu et le rendu dans un `Bitmap`.
5. **Interface** (`core:pdf`) : `PdfEngine.open(DocumentSource, password)` renvoie un
   `Result<PdfDocument>` ; les erreurs sont les `PdfError` de `core:domain` (Kotlin pur), comme
   les modèles (`PageSize`, `PdfRect`, `OutlineItem`, `PdfLink`, `SearchHit`…). Écarts avec
   l'interface indicative de `SPEC.md` §4.2 : la région à rendre est un `PdfRect` du domaine
   (et non un `RectF` Android), et `search` renvoie directement un `Flow` froid.
6. **Fil d'exécution** : chaque document possède un thread dédié (`DocumentDispatcherFactory`,
   injectée) ; tous les objets MuPDF y sont créés, utilisés et détruits. Les pages chargées sont
   gardées dans un cache LRU de 4 pages. L'annulation d'un rendu déclenche `Cookie.abort()`.
   Seule exception : le `Cookie` d'un rendu est créé et détruit sur le fil de l'appelant. C'est
   un simple bloc de compteurs natif, lié à aucun document, que MuPDF prévoit de partager entre
   fils (le fil du document le lit pendant le dessin, `abort()` est appelé depuis le
   gestionnaire d'annulation) ; le posséder côté appelant garantit qu'il survit au dessin et
   n'est détruit qu'une fois. À l'ouverture comme à la fermeture, le document natif, son
   descripteur de fichier et son thread sont libérés quelle que soit l'issue (erreur métier,
   annulation de l'appelant, erreur inattendue).
7. **Rendu** : MuPDF dessine dans un `Pixmap` BGRA dont les pixels sont copiés dans le `Bitmap`
   (`setPixels`). Plus simple et testable sur JVM que `AndroidDrawDevice` ; le coût de la copie
   sera mesuré au jalon M2 (tuiles), avec repli sur `AndroidDrawDevice` si nécessaire.
   Le mode nuit utilise `invertLuminance()` : la luminosité est inversée, les teintes conservées.
8. **Sécurité** : le JavaScript PDF est désactivé à l'ouverture (`disableJs`) ; les messages de
   MuPDF sont ignorés (`Context.setLog`), car ils peuvent citer le contenu du document.
   Un fichier sans en-tête `%PDF-` dans son premier kilo-octet est refusé (`UnsupportedFormat`)
   avant tout analyseur, ce qui distingue « pas un PDF » de « PDF endommagé ».
9. **R8** : les classes `com.artifex.mupdf.fitz.**` sont conservées (JNI par nom),
   via les règles consommateur du module.

## Conséquences

- **Taille** : `libmupdf_java.so` fait 7,6 Mo (armeabi-v7a) à 11,3 Mo (x86_64) ; les APK de
  release par ABI font 8,4 à 12,1 Mo, sous la limite de 15 Mo. Le build d'Artifex exclut les
  polices de secours Noto, CJK et SIL (`TOFU_*`) : un texte CJK dont la police n'est **pas**
  incorporée au PDF s'affiche en carrés (voir `docs/open-questions.md`). **Précisé par l'ADR 0025** : seulement
  sur un appareil sans police CJK, car MuPDF prend d'abord celles de `/system/fonts` ; aucune
  police CJK n'est embarquée.
- **Build** : la compilation native ajoute plusieurs minutes (3 ABI Android + la version hôte) ;
  il faut `make`, un compilateur C et les fichiers de développement de freetype2, OpenSSL et
  zlib pour les tests JVM (Linux). La CI les installe.
- **Licences** : MuPDF et deux de ses bibliothèques (jbig2dec, extract) sont sous AGPL-3.0 ;
  les autres sous licences permissives (`docs/dependencies.md`). FreeType est utilisé sous
  sa licence GPLv2-ou-ultérieure.
- **Mise à jour de MuPDF** : changer le commit du sous-module (tag officiel), relancer
  `scripts/init-mupdf.sh`, la CI complète, et mettre à jour `docs/dependencies.md`.
