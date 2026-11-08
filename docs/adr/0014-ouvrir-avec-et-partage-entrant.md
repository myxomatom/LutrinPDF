# 0014 — « Ouvrir avec » et partage entrant : routage des intents, mode de lancement, validation des URI

- **Statut** : accepté
- **Date** : 2026-11-08
- **Décideurs** : cadrage par la session d'orchestration ; détails techniques par l'agent de
  codage
- **Références** : `SPEC.md` F01, §2.4 (« Repli sans AppFunctions » : `ACTION_VIEW` avec extra
  `page`), §6 ; `AGENTS.md` §9 ; `ROADMAP.md` M3 ; ADR 0008 (lecteur), 0012 (activité réservée
  aux benchmarks) ; point ouvert 11 (URI non seekables, hors de cette décision)

## Contexte

F01 demande d'ouvrir un PDF depuis une autre application : « Ouvrir avec » (`ACTION_VIEW`,
MIME `application/pdf`) et « Partager » (`ACTION_SEND`). Jusqu'ici, l'application n'avait qu'une
activité, `MainActivity`, lancée depuis l'icône, et un seul chemin d'ouverture : le sélecteur
système (SAF) de la bibliothèque, qui pousse le lecteur sur la pile de navigation Compose.

Recevoir des intents d'autres applications ouvre une surface d'attaque (`AGENTS.md` §9) : l'URI,
le type MIME et les extras viennent de l'expéditeur et ne sont pas fiables. Il faut aussi décider
comment l'intent atteint l'activité unique, où va le retour arrière, et comment éviter de traiter
deux fois le même intent après une rotation ou la mort du processus. Les permissions accordées
par `ACTION_VIEW` / `ACTION_SEND` (`FLAG_GRANT_READ_URI_PERMISSION`) sont temporaires : elles
valent tant que l'activité qui les a reçues vit.

`SPEC.md` §2.4 documente l'extra `page` sans dire s'il compte à partir de 0 ou de 1.

## Options envisagées

**Activité qui reçoit les intents**
- **A — `MainActivity` elle-même**, avec ses filtres d'intent : une seule activité exportée, le
  même graphe de navigation, le même thème.
- **B — Une activité « visionneuse » dédiée** : isolerait le lecteur, mais dupliquerait le thème
  et la navigation, et ajouterait un composant exporté.

**Mode de lancement**
- **`standard`** (défaut) : l'instance qui reçoit « Ouvrir avec » s'ouvre dans la tâche de
  l'application appelante (Fichiers, Gmail…) ; retour arrière = retour à cette application,
  comme le veut la convention Android. Une nouvelle instance par document.
- **`singleTop`** : comme `standard`, plus `onNewIntent` quand l'activité est déjà au sommet ;
  ne change rien au cas courant.
- **`singleTask` / `singleInstance`** : une seule instance dans la tâche de Lutrin ; le document
  d'une autre application quitte la tâche de celle-ci, le retour ne ramène plus à elle et les
  documents de plusieurs applications se mélangent dans une même pile.

**Pile de navigation d'un document reçu**
- **Bibliothèque puis lecteur** : retour depuis le document = bibliothèque, puis un second retour
  pour revenir à l'application appelante.
- **Lecteur seul** : retour depuis le document = sortie de l'activité, donc retour direct à
  l'application appelante.

**Validation** : dans l'activité (Android), ou dans un cas d'usage pur Kotlin de `core:domain`
alimenté par une copie des champs de l'intent.

**Permissions persistantes** : appeler `takePersistableUriPermission` dès maintenant quand
l'intent le permet, ou attendre les récents.

## Décision

1. **`MainActivity` reçoit les intents** (option A), en mode de lancement **`standard`**. Elle
   traite aussi `onNewIntent`, utilisé quand l'appelant ajoute `FLAG_ACTIVITY_SINGLE_TOP` ou
   `FLAG_ACTIVITY_CLEAR_TOP` alors qu'une instance est au sommet. Elle reste le **seul composant
   exporté**.
2. **Filtres d'intent précis** : `ACTION_VIEW` + catégorie `DEFAULT` + schéma `content` + MIME
   `application/pdf` ; `ACTION_SEND` + `DEFAULT` + MIME `application/pdf`. Ni `file://`, ni
   `http(s)://`, ni `BROWSABLE`, ni type joker (`*/*`, `application/octet-stream`), pour que
   Lutrin ne soit pas proposé pour tous les fichiers. Pas d'`ACTION_SEND_MULTIPLE` : il revient
   à l'outil de fusion (M6).
3. **Un document reçu est la seule entrée de la pile** : le lecteur remplace toute la pile de
   navigation de cette instance, et le retour (geste système ou flèche de la barre) quitte
   l'activité, donc revient à l'application appelante. Un document reçu par `onNewIntent`
   remplace de même le document ouvert, qui est fermé. Ouvert depuis la bibliothèque, le lecteur
   garde son comportement : retour à la bibliothèque. Si l'appelant lance l'intent avec
   `FLAG_ACTIVITY_NEW_TASK` alors qu'une tâche de Lutrin existe, la nouvelle instance s'empile
   au-dessus de cette tâche, et le retour ramène à l'écran qui s'y trouvait.
4. **Un intent n'est traité qu'une fois** :
   - `onCreate` ne le traite que si `savedInstanceState` est nul ; après une rotation ou la mort
     du processus, la pile de navigation restaurée rouvre le document avec ses arguments
     (le `ReaderViewModel` survit à la rotation ; après la mort du processus, la permission
     temporaire, tenue par l'enregistrement de l'activité dans le système, vaut encore) ;
   - la demande vérifiée est gardée dans `MainViewModel` (qui survit aux changements de
     configuration) jusqu'à ce que la navigation la marque traitée ;
   - un lancement depuis les applications récentes (`FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY`)
     n'est pas traité : l'utilisateur n'a rien redemandé et la permission d'origine peut avoir
     disparu ; la bibliothèque s'affiche.
5. **Validation dans `core:domain`**, par `ParseIncomingDocumentUseCase`, testé sur JVM pure.
   L'activité copie les champs de l'intent dans `IncomingIntent` (action, données,
   `EXTRA_STREAM`, type, extra `page`, nom du paquet) sans rien valider. Règles :
   - seules `ACTION_VIEW` (URI des données) et `ACTION_SEND` (`EXTRA_STREAM`) demandent un
     document ; une autre action (l'icône) ne change rien ;
   - l'URI doit être une URI `content://` bien formée, avec une autorité ; sont refusées l'absence
     d'URI, les URI sans schéma ou sans autorité, les caractères de contrôle, et tout autre schéma
     (`file://` contourne SAF et lève `FileUriExposedException` chez l'expéditeur, `http(s)://`
     demanderait le réseau) ;
   - les URI des **fournisseurs de Lutrin lui-même** (autorité égale au nom du paquet ou
     commençant par « nom du paquet. ») sont refusées : une autre application ne doit pas faire
     lire à Lutrin ses propres fichiers privés (attaque de l'« adjoint confus »). Le préfixe
     `N@` d'une URI d'un autre profil (profil professionnel) est ignoré pour ce contrôle, et ces
     URI sont acceptées ;
   - **type MIME** : accepté si le type déclaré par l'expéditeur est PDF (`application/pdf`,
     `application/x-pdf`, sans tenir compte de la casse ni des paramètres) ; sinon, le
     fournisseur est interrogé (`ContentResolver.getType`, sur le dispatcher d'E/S injecté) et
     son type PDF suffit. Si les deux types sont absents ou génériques
     (`application/octet-stream`, `binary/octet-stream`, `application/*`, `*/*`), le document
     est accepté et **le moteur tranche** : il vérifie déjà l'en-tête `%PDF-` de toute source et
     répond « Ce fichier n'est pas un PDF » (V8). Tout autre type (image, texte…) est refusé ;
   - l'extra **`page` compte à partir de 1**, comme les numéros de page lus par l'utilisateur et
     comme `goToPage(page)` (« Va à la page 12 », §2.4). Il n'est lu que pour `ACTION_VIEW`
     (seul cas documenté par §2.4) et doit être un `Int` ; absent, d'un autre type, nul ou
     négatif, il est ignoré (première page) ; au-delà de la dernière page, le lecteur ouvre la
     dernière page (il est le seul à connaître le nombre de pages).
6. **Erreurs** : un intent refusé ramène à la bibliothèque (la pile est vidée) avec un message
   (snackbar, français et anglais) selon la raison : aucun document reçu ; lien impossible à
   ouvrir (URI mal formée, autre schéma, URI de Lutrin) ; « Lutrin n'ouvre que les documents
   PDF ». Une fois l'intent accepté, les échecs d'ouverture (fichier introuvable, permission
   retirée, non PDF, document endommagé, mot de passe) sont ceux du moteur et s'affichent dans
   l'écran d'erreur V8 existant ; rien ne plante.
7. **Pas de `takePersistableUriPermission` dans cette étape** : les permissions de « Ouvrir
   avec » et « Partager » ne sont presque jamais persistables, et prendre une permission
   persistante sans récents pour la libérer accumulerait des autorisations (le système en limite
   le nombre) que l'utilisateur ne pourrait pas retirer. Elle arrive avec les récents (étape
   suivante de M3). Le document est ouvert par le lecteur de l'activité qui a reçu la
   permission, pendant qu'elle est valide ; une fois ouvert, le descripteur de fichier reste
   lisible.
8. Pour que les tests Robolectric de l'application remplacent le moteur MuPDF (sa bibliothèque
   native ne se charge pas sous Robolectric), la liaison de `PdfEngine` passe dans un module
   Hilt public, `PdfEngineModule` (`core:pdf`), remplacé par `@TestInstallIn` ; le reste du
   moteur reste `internal`. `hilt-android-testing` est ajouté aux dépendances de test.

## Conséquences

- Positives :
  - Lutrin apparaît dans « Ouvrir avec » et « Partager » pour les PDF seulement, et le retour
    ramène à l'application d'origine ;
  - la politique de validation est concentrée dans un cas d'usage pur Kotlin, testé cas par cas
    (chaque raison de refus, chaque type, l'extra `page`) ;
  - aucune nouvelle permission, un seul composant exporté.
- Négatives / dette acceptée :
  - un document reçu montre la bibliothèque pendant une image, le temps de vérifier l'intent,
    avant le lecteur ;
  - le titre affiché est le dernier segment de l'URI, souvent un identifiant opaque pour un
    document reçu (Gmail, Drive) ; le nom réel (`OpenableColumns.DISPLAY_NAME`) viendra avec
    les récents ;
  - les documents envoyés en `application/octet-stream` sans type de fournisseur ne proposent
    pas Lutrin dans « Ouvrir avec » (filtre volontairement précis) ; ils restent ouvrables par
    « Ouvrir un fichier » ;
  - les URI non seekables (flux de certaines applications) échouent encore : point ouvert 11,
    traité dans une étape suivante (copie dans `cacheDir`).
- Actions de suivi :
  - permissions persistantes, récents (Room, vignettes) et reprise de la dernière page ;
  - mesurer sur appareil le temps d'ouverture par une vraie URI SAF reçue d'une autre
    application (ADR 0012) ;
  - tests manuels sur appareil : « Ouvrir avec » depuis Fichiers, Gmail et Drive, partage depuis
    ces mêmes applications, retour arrière, rotation pendant l'ouverture.
