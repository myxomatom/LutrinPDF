# Journal des modifications

Toutes les modifications notables de ce projet sont consignées ici.

Le format suit [Keep a Changelog](https://keepachangelog.com/fr/1.1.0/) et le projet
adhère au [versionnage sémantique](https://semver.org/lang/fr/). Les signatures des
fonctions exposées aux assistants (AppFunctions) sont une API publique : tout
changement y est signalé.

## [Non publié]

### Ajouté
- **Table des matières** (M4, lot 3, F06, V2, ADR 0021) : une icône « Table des matières » dans
  la barre du lecteur (cachée pour un document sans sommaire) ouvre un **tiroir latéral**, de
  droite pour une écriture de droite à gauche. Il s'ouvre sur la **section courante**, dépliée et
  surlignée ; chaque entrée donne sa page, un chevron déplie ses sous-sections, un tap mène à la
  page (à la position visée en défilement continu) et ferme le tiroir. La liste est paresseuse
  (grands sommaires). Le titre de la section visée s'affiche sous « Page N » dans l'aperçu du
  curseur. TalkBack annonce le niveau, l'état déplié et la page cible ; cibles de 48 dp. Les
  entrées qui mènent hors du document portent une icône « lien externe » et appellent
  `ReaderViewModel.onExternalOutlineEntry(uri)`, **qui ne fait rien pour l'instant** : le
  dialogue de confirmation V7 (lot 4) la branchera ; le lecteur n'ouvre jamais d'URI seul.
- **Texte CJK dont la police n'est pas incorporée** (ADR 0025) : dessiné avec les polices CJK du
  système, que MuPDF cherche dans `/system/fonts` ; aucune police CJK n'est embarquée (APK
  inchangés, limite de `SPEC.md` §3 gardée à 15 Mo ; DroidSansFallback, essayée, ajoutait 3,4 Mio
  par ABI). Sur un appareil sans police CJK, ce texte s'affiche en carrés. Nouveau fichier de test
  `cjk_not_embedded.pdf`, vérifié sur appareil (`CjkFallbackFontInstrumentedTest`) et, pour la
  limite sans police CJK, sur la JVM. `generateMupdfResources` n'écrit plus que les ressources
  compilées (≈ 200 Mo de sources C en moins) ; la bibliothèque des tests JVM a les mêmes polices
  que l'application.
- **API texte et destinations du moteur PDF** (M4, lot 1, ADR 0020), sans écran pour l'instant :
  les liens internes et les entrées du sommaire donnent la **position** visée dans la page
  (`PdfDestination(pageIndex, x, y)`, destinations nommées comprises) ; **sélection de texte**
  entre deux points (`selectText`, modes caractères, mot pour l'appui long, ligne), avec une
  zone par ligne et le texte en ordre de lecture, juste en arabe ; **recherche page par page**
  (`SearchPageResult`, une émission par page, même sans résultat, pour suivre la progression),
  insensible à la casse et aux accents par défaut, arrêtée à **1 000 résultats**, chaque
  résultat avec un **extrait** (« …la bibliothèque municipale… ») et un mot coupé en fin de ligne
  trouvé ; **permission de copie** du PDF exposée (`canCopyText`, rapportée sans être
  appliquée). Les liens externes gardent leur URI brute : le lecteur filtrera les schémas.
  Après relecture : recherche à partir d'une page donnée, avec bouclage (`startPage`) ;
  zones des résultats en `PdfQuad` ; poignées de sélection dans l'ordre de lecture
  (`TextSelection.start` / `end`) ; `isOnText` pour ignorer un appui long hors du texte ;
  taille de la zone des destinations `FitR` ; destinations à une coordonnée vers une page
  tournée d'un quart de tour ramenées au haut de la page (MuPDF perd la valeur) ; la recherche
  n'évince plus les pages affichées du cache de rendu et ne calcule plus d'extrait pour les
  résultats au-delà du plafond. `FakePdfDocument` (`core:testing`) se règle : sommaire, liens,
  résultats, sélections, zones de texte, permission de copie, délai entre les pages d'une
  recherche ; il lève `PageOutOfRange` et `Closed` comme le moteur. Jeu de test enrichi :
  `text_selection.pdf` (mot coupé, phrase arabe), `rotated_destinations.pdf`,
  `search_many_hits.pdf`, liens vers le milieu d'une page, destination nommée, liens `mailto:`,
  `javascript:`, `file:` et relatif, entrée de sommaire externe, sommaire du PDF de 600 pages.
- **Aller à la page** (F05, V3) : un appui sur l'indicateur « 5 / 8 » de la barre flottante (une
  cible de 48 dp au moins, lue par TalkBack « Page 5 sur 8, bouton » avec l'action « Aller à une
  page ») ouvre un dialogue avec un champ numérique rempli de la page affichée, sélectionnée,
  clavier ouvert. « Aller » et l'action du clavier ne s'activent que pour une page du document ;
  sinon le champ explique pourquoi (« Saisissez un numéro de page », « Entre 1 et 8 » pour 0 ou
  une page trop grande), message annoncé par les lecteurs d'écran. Seuls les chiffres sont
  acceptés ; la saisie survit à la rotation. Fonctionne en défilement continu et en page par
  page. Le curseur fin reste la « barre de défilement rapide » de F05. Pas d'entrée de menu en
  plus. Pas encore essayé sur appareil.
- `PageNavigator.goTo(page, yOffset)` : en défilement continu, amène un point de la page (distance
  depuis le haut, en points PDF) en haut de l'écran ; en page par page, montre la page entière.
  Servira à la table des matières et aux liens (F06, F09).
- **Transition lecteur → vignette** (ADR 0018 §7, ADR 0019 amendé) : en touchant la carte
  « Reprendre la lecture » ou une ligne des récents, le lecteur apparaît par un simple fondu (la
  vignette qui s'agrandissait, essayée d'abord, était buggée sur appareil ; le rond de
  chargement n'est plus recouvert). Une fois le lecteur posé, le document est lié à sa
  vignette : au retour (bouton, flèche ou geste de retour, qui pilote l'animation sous
  Android 14 et plus), la page se replie vers la vignette si elle est visible, en se penchant
  pour la carte, sinon simple fondu. Un document protégé,
  une page zoomée, le sélecteur de fichier, « Ouvrir avec » et le partage gardent un simple
  fondu ; avec « Supprimer les animations », l'écran change aussitôt. Les écrans passent de
  l'un à l'autre par un fondu de 300 ms (700 ms avant). Le manifeste active le retour
  prédictif pour Android 13 à 15.
- Lecteur (E2) au nouveau design (ADR 0018) : la barre du bas devient une **barre flottante** en
  pilule, au-dessus du document qui défile dessous jusqu'en bas de l'écran, à l'écart de la barre
  de navigation, de la zone des gestes et de l'encoche ; la dernière page peut toujours défiler
  au-dessus d'elle (et tient au-dessus d'elle en page par page). **Curseur fin** : piste de 4 dp,
  pouce rond, sans point d'arrêt, cible tactile de 48 dp ; TalkBack lit « Aller à la page » et
  « Page 3 sur 8 ». Titre de la barre du haut en Fraunces 20 sp. Elle disparaît en plein écran
  comme avant ; un document d'une page garde la barre, sans curseur.
- **Aperçu de la page pendant le glissement du curseur** (ADR 0018), activé par défaut : le
  document reste où il est, atténué, une carte au-dessus du pouce montre la page visée (rendue
  par le moteur, page blanche le temps du rendu) et « Page 6 », l'indicateur passe à « 6 / 8 » en
  couleur primaire ; le document y va au relâchement. Un appui sur la piste, le clavier (non essayé sur appareil) et
  TalkBack vont directement à la page ; un geste annulé reste sur la page de départ. Fonctionne
  en défilement continu comme en page par page. Interrupteur « Aperçu de la page » dans les
  **Paramètres, section « Lecture »**, à côté des couleurs dynamiques (d'abord placé dans la
  feuille « Affichage » du lecteur), enregistré avec DataStore ; désactivé, le document suit le
  curseur comme avant.
- Bibliothèque (E1) au nouveau design (ADR 0018) : titre « Lutrin » en grand (Fraunces, 32 sp) ;
  carte **« Reprendre la lecture »** pour le dernier document ouvert, avec sa première page en
  grand, légèrement inclinée, son titre, « Page 3 sur 8 · aujourd'hui, 20:41 », une barre de
  progression et « Continuer » ; toute la carte ouvre le document (une seule cible pour
  TalkBack, décrite « Reprendre la lecture : …, page 3 sur 8 »), et son bouton « ⋮ » propose
  « Retirer des récents » comme les lignes (le document suivant prend alors la carte, « Annuler »
  le remet). Sous la carte, « Récents » et « N autres documents » (absent s'il n'y a qu'un
  document), vignettes de 52 × 68 dp et titres en `titleMedium`. La barre de progression ne
  s'affiche plus que pour une lecture en cours : un document d'une page ou lu jusqu'au bout
  montre « 4 pages · hier » ou « 1 page · 19 oct. ». Bouton « Ouvrir un fichier » en couleur
  primaire. Les pages restent blanches en thème sombre. En grande police, le texte de la carte
  passe sous la page.
- Nouveau design visuel, fondations (ADR 0018) : les titres (barres, feuilles, dialogues, grands
  titres) passent en **Fraunces 600**, police embarquée dans `core:ui` (TTF statique de 71 Kio,
  latin et latin étendu, SIL OFL 1.1, aucun téléchargement) ; les textes courants restent sur la
  police du système. Nouveaux styles `headlineLarge` (32 sp), `displayLarge` (56 sp) et deux
  variantes `titleLargeCompact` (20 sp) et `titleMediumSerif` (18 sp) de `Typography`. Le schéma
  de repli (clair et sombre) a un **accent chaud** ambre pour les rôles `tertiary*` à la place du
  violet ; avec les couleurs dynamiques (activées par défaut), l'accent vient du fond d'écran
  comme avant. Les jetons de `docs/design/design-tokens.json` suivent (vignette de liste
  52 × 68 dp) et un test les compare au thème. Les maquettes de `docs/design/proposals/` sont la
  référence de E1, E2 et E9. Les captures d'écran de référence sont
  régénérées.
- Documents protégés par mot de passe (F10, ADR 0017) : un PDF chiffré (RC4, AES-128, AES-256)
  ouvre le dialogue « Document protégé » (V4) au lieu de l'écran d'erreur : champ masqué avec un
  bouton pour afficher le mot de passe, « Ouvrir » ou la touche du clavier, « Annuler » pour
  revenir. Un mauvais mot de passe affiche « Mot de passe incorrect. Réessayez. », annoncé par
  TalkBack, et garde le texte sélectionné ; les essais ne sont pas limités. Le mot de passe n'est
  jamais enregistré ni journalisé, ni gardé après l'essai ; il faut le retaper après une rotation
  pendant la saisie, à chaque réouverture et après l'arrêt de l'application. Le dialogue reste
  hors du remplissage automatique des gestionnaires de mots de passe. Un PDF restreint par son
  seul mot de passe propriétaire s'ouvre sans rien demander.
- Un document ouvert avec son mot de passe entre dans les récents avec la pastille de cadenas de
  la maquette E1, sans vignette, et reprend à sa page ; le rouvrir depuis la liste redemande le
  mot de passe. Base Room en **version 2** (colonne `is_protected`, migration automatique sans
  perte, testée).
- Jeu de test : PDF chiffrés en RC4 128 bits et AES-128, et PDF avec mot de passe propriétaire
  seul, générés par `generate_test_files.py encrypted_variants` ; tests : `room-testing`
  (`MigrationTestHelper`).
- Retirer un document des récents (F11, ADR 0016) : chaque ligne de la bibliothèque (E1) a un
  menu « ⋮ » avec « Retirer des récents » ; la ligne disparaît aussitôt et un message propose
  « Annuler » pendant 10 s ; le retrait n'a lieu qu'ensuite, et pas si le document a été rouvert
  entre-temps. L'écran d'erreur (V8) propose aussi « Retirer des récents » quand le document qui
  ne s'ouvre plus est dans la liste. Un retrait rend la permission persistante, supprime la
  vignette et oublie la page enregistrée ; le fichier n'est jamais touché.
- « Vider les récents » dans les paramètres (E3, section « Données et confidentialité »), avec
  un dialogue de confirmation ; désactivé quand la liste est vide.
- Documents récents (F11, ADR 0015) : la bibliothèque (E1) liste les 50 derniers documents
  ouverts, du plus récent au plus ancien, avec la vignette de la première page, le nom donné par
  le fournisseur, la page atteinte, la date de dernière ouverture et une barre de progression ;
  un toucher rouvre le document. Chaque ouverture réussie (sélecteur, « Ouvrir avec »,
  « Partager ») ajoute ou remonte le document et garde une permission de lecture persistante
  quand le système l'accorde ; un document qui sort de la liste rend sa permission et perd sa
  vignette. Base Room (`core:data`, schéma 1 exporté dans `core/data/schemas/`), vignettes PNG
  dessinées par `PdfEngine` dans `noBackupFilesDir`, quand la bibliothèque les montre. États
  chargement, vide, erreur et liste ; français et anglais ; captures d'écran clair, sombre,
  grande police et français.
- Reprise de lecture (F12, ADR 0015) : le lecteur rouvre un document à la page où l'utilisateur
  s'est arrêté, ramenée au document s'il a changé ; la page est enregistrée une seconde après
  le dernier changement et à la fermeture du lecteur. Le zoom n'est pas encore restauré.
- « Ouvrir avec » et partage entrant (F01, ADR 0014) : un PDF envoyé par une autre application
  (`ACTION_VIEW`, `ACTION_SEND` avec un seul document) s'ouvre directement dans le lecteur, et le
  retour ramène à cette application. L'extra `page` (`Int`, à partir de 1) d'`ACTION_VIEW` ouvre
  le document à cette page (`SPEC.md` §2.4), ramenée à la dernière page si elle la dépasse. Seules
  les URI `content://` sont acceptées ; `file://`, les liens web, les URI des fournisseurs de
  Lutrin et les types autres que PDF sont refusés avec un message sur la bibliothèque. Sans type
  ou avec un type générique, l'en-tête `%PDF-` décide. Une rotation ne rouvre pas le document, un
  nouvel intent remplace le document ouvert. Validation dans `ParseIncomingDocumentUseCase`
  (`core:domain`), type demandé au fournisseur par `ContentResolverDocumentTypeResolver`
  (`core:data`).
- Lecteur : argument de navigation `page` (page de départ, ramenée au document).
- Tests : `hilt-android-testing` pour remplacer le moteur MuPDF par un faux dans les tests
  Robolectric de l'application ; fournisseur de documents de test et test instrumenté
  d'« Ouvrir avec » et du partage.
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
- Les tests unitaires ne remplissent plus le dossier temporaire du système : Robolectric y
  laissait à chaque exécution son runtime natif (~200 Mo par JVM de test sous Windows). Le
  `java.io.tmpdir` des tâches `Test` est désormais `<module>/build/tmp/robolectric`.
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
- Moteur PDF (ADR 0020) : `OutlineItem.pageIndex` et `PdfLink.Internal.pageIndex` deviennent
  `destination: PdfDestination` ; `PdfDocument.search` émet des `SearchPageResult` (une par page)
  au lieu de `SearchHit` ; `textAt(index, PdfRect)`, qui lisait en fait le texte entre deux
  points et non celui d'un rectangle, est remplacé par `selectText`. Le PDF de 600 pages du jeu
  de test (aussi utilisé par `:benchmark`) a désormais un sommaire.
- **Build** : `:app` tolère les avertissements Dokka (`failOnWarning` reste actif ailleurs), car
  Dokka 2.3 signale un lien KDoc non résolu dans `androidx` (`MenuHost`, hérité de
  `MainActivity`). Prépare la montée à Dokka 2.3.
- `:benchmark` : les gestes de défilement laissent libre le quart bas de l'écran, où se trouve
  désormais la barre flottante du lecteur (ADR 0018) ; les mesures de l'ADR 0012 sont à refaire.
- L'écran d'erreur (V8) ne traite plus les documents protégés : le lecteur demande leur mot de
  passe (ADR 0017).
- « Ouvrir avec » (ADR 0014, amendé par l'ADR 0015) : sans extra `page` valide, le document
  s'ouvre à la page enregistrée, sinon à la première ; l'extra `page` reste prioritaire.
  L'argument de navigation `page` du lecteur vaut `NO_PAGE` (-1) par défaut.
- CI : les actions GitHub sont épinglées par SHA de commit (et non plus par tag mutable), la version restant en commentaire ; Dependabot mettra ces épinglages à jour.
- APK sans bloc de métadonnées de dépendances chiffré par Google (gênant pour F-Droid).
- `ContentOpener` et `DocumentDispatcherFactory` (core:pdf) passent en `internal` : le moteur PDF n'expose plus ces détails d'implémentation.
- Android Gradle Plugin 9.5.0 → 9.5.1 ; synchronisation Gradle en parallèle dans Android Studio (`org.gradle.tooling.parallel`).

### Sécurité
- JavaScript PDF désactivé à l'ouverture ; messages de MuPDF jamais journalisés.
- Sauvegarde Android et transfert entre appareils désactivés (ADR 0004).
