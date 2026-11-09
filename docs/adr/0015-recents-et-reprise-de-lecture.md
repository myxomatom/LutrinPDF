# 0015 — Récents, vignettes et reprise de lecture

- **Statut** : accepté
- **Date** : 2026-11-09
- **Décideurs** : cadrage par la session d'orchestration ; détails techniques par l'agent de
  codage
- **Références** : `SPEC.md` F11, F12, §2.4 (`documentId`), §4.1 (persistance), §4.3 (modules),
  §5 (`RecentDocument`, `ReadingPosition`, identification d'un document), §6 (« Fichier
  introuvable / permission révoquée ») ; `AGENTS.md` §3, §8, §9 ; `ROADMAP.md` M3, vue E1 ;
  ADR 0001 (moteur), 0008 (lecteur), 0009 (écarts avec les maquettes), 0012 (benchmarks),
  0014 (« Ouvrir avec », §5 et §7) ; maquette `docs/design/png/E1-Bibliotheque.png`

## Contexte

Le jalon M3 demande la liste des documents récents avec vignettes (F11) et la reprise de la
dernière page (F12). Jusqu'ici, la bibliothèque n'affichait que son état vide et aucune
permission n'était gardée : l'ADR 0014 (§7) avait reporté `takePersistableUriPermission` à
cette étape, pour ne pas accumuler des autorisations que l'utilisateur ne pourrait pas retirer
(Android en garde au plus 128 par application, 512 depuis Android 11).

Il faut décider où et comment stocker les récents, comment identifier un document
(`documentId`, utilisé par les assistants en v1.1, `SPEC.md` §2.4), combien en garder et quand
rendre les permissions, où dessiner et ranger les vignettes sans exposer MuPDF hors de
`core:pdf`, et quand enregistrer la page lue sans écrire à chaque image. Cette étape ne couvre
pas « Retirer des récents » ni « Vider les récents » (étape suivante de M3), ni le dialogue de
mot de passe (V4).

## Options envisagées

**Identifiant d'un document**
- **A — UUID aléatoire** attribué à la première ouverture : opaque, mais il faut injecter un
  générateur pour des tests déterministes, et un document qui sort de la liste puis revient
  change d'identifiant.
- **B — Empreinte du contenu** (premiers et derniers Ko + taille, `SPEC.md` §5) : retrouve la
  position même si l'URI change, mais demande de lire le fichier à chaque ouverture et de
  fusionner deux entrées de même contenu (laquelle garde sa permission ?).
- **C — Condensé de l'URI** (SHA-256 tronqué) : déterministe, stable, opaque ; ne reconnaît pas
  un même fichier ouvert par deux URI différentes.

**Emplacement des vignettes**
- `cacheDir` : le système peut le vider ; une vignette d'un document reçu par « Ouvrir avec »
  ne pourrait plus être redessinée (permission perdue).
- `filesDir` : sauvegardé si la sauvegarde est un jour activée (ADR 0004).
- `noBackupFilesDir` : privé, gardé, jamais sauvegardé.

**Moment où la vignette est dessinée**
- **À l'ouverture**, par le lecteur ou juste après lui : le document est ouvert une deuxième
  fois pendant que le lecteur rend ses premières pages (concurrence sur le CPU, mesure
  d'ouverture de l'ADR 0012 faussée), et les tests qui comptent les ouvertures deviennent
  dépendants de l'ordonnancement.
- **Quand la bibliothèque montre la ligne** et que sa vignette manque ou date d'avant la
  dernière ouverture : rien ne tourne pendant la lecture, seules les lignes visibles coûtent.

**Enregistrement de la page** : à chaque changement de page ; seulement à la fermeture du
lecteur ; ou après un temps calme, plus une dernière écriture à la fermeture.

## Décision

1. **Room 2.8.5 dans `core:data`** (Apache-2.0, `docs/dependencies.md`), base `lutrin.db`,
   **version 1**, table `recent_documents` :

   | Colonne | Type | Rôle |
   |---------|------|------|
   | `document_id` | texte, clé primaire | identifiant (point 2) |
   | `uri` | texte, unique | URI `content://` d'ouverture |
   | `display_name` | texte | nom affiché (point 6) |
   | `size_bytes` | entier, nul possible | taille donnée par le fournisseur |
   | `page_count` | entier | nombre de pages à la dernière ouverture |
   | `last_opened_at` | entier (ms), indexé | date de la dernière ouverture réussie |
   | `last_page` | entier | page lue, à partir de 0 (F12) |
   | `thumbnail_file` | texte, nul possible | nom du fichier de la vignette |
   | `thumbnail_drawn_at` | entier (ms), nul possible | date du dessin de la vignette |

   Le schéma est exporté dans `core/data/schemas/` et versionné ; toute nouvelle version vient
   avec sa migration et son test, jamais avec une migration destructive. Les requêtes tournent
   sur le dispatcher d'E/S injecté (`setQueryCoroutineContext`). Le dépôt
   (`RoomRecentDocumentsRepository`) implémente l'interface `RecentDocumentsRepository` de
   `core:domain` ; un faux est dans `core:testing`.
2. **`documentId` = option C** : les 128 premiers bits du SHA-256 de l'URI, en hexadécimal
   (`documentIdFor`, `core:domain`). La position est rattachée à l'URI. L'empreinte du contenu
   de `SPEC.md` §5 est **reportée** (nouveau point ouvert 16) : elle sera nécessaire quand un même
   fichier arrivera par deux URI (fichier créé par les outils, M6) ; l'identifiant pourra alors
   rester celui de l'entrée existante.
3. **Pas de table `ReadingPosition` séparée** : la page vit dans la ligne du récent. Elle est
   perdue quand le document sort de la liste. **Le zoom et le décalage ne sont pas enregistrés**
   (F12 partiel) : le zoom est un état du composable, il n'a pas le même sens selon le mode
   (1 = « largeur » en défilement continu, « page entière » page par page, ADR 0011), et le
   rétablir sans la position dans la page n'aurait guère d'intérêt. Ils arriveront avec une
   colonne et une migration quand le lecteur exposera cet état (suivi dans `ROADMAP.md`).
4. **Au plus 50 récents** (`RecentDocument.MAX_COUNT`). Au-delà, les entrées les moins récemment
   ouvertes sortent de la liste : leur permission persistante est rendue
   (`releasePersistableUriPermission`) et leur vignette supprimée. 50 reste loin de la limite
   de 128 permissions d'Android 10. Les permissions orphelines (base effacée, plantage entre
   la prise et l'écriture) ne sont pas recherchées : un tel nettoyage rendrait aussi, en M6, la
   permission du dossier de sortie des outils.
5. **Permissions** : chaque ouverture réussie appelle `takePersistableUriPermission` en
   lecture, pendant que la permission du sélecteur ou de l'intent est encore valide. Celles du
   sélecteur système (`ACTION_OPEN_DOCUMENT`) sont persistables ; celles d'« Ouvrir avec » et de
   « Partager » ne le sont presque jamais : l'échec (`SecurityException`) est attendu, le
   document est **enregistré quand même**, et sa réouverture depuis la bibliothèque après la fin
   de la permission affiche l'écran d'erreur V8 « inaccessible » (« Retirer des récents » arrive
   à l'étape suivante). Reprendre une permission déjà persistée rafraîchit sa date ; le système
   retire d'abord les plus anciennes.
6. **Nom affiché** : `OpenableColumns.DISPLAY_NAME` sans l'extension `.pdf` (comme la
   maquette), sinon le titre du PDF, sinon le dernier segment de l'URI ; la taille vient de
   `OpenableColumns.SIZE`. Le titre de la barre du lecteur ne change pas (titre du PDF d'abord).
7. **Vignettes** : dessinées par `PdfThumbnailRenderer` (`core:data`) **à travers `PdfEngine`**
   (`core:data` dépend désormais de `core:pdf` ; MuPDF reste caché derrière `PdfEngine`, et
   aucun module `feature:*` ne dépend de `core:data`). La première page tient dans
   176 × 232 px (44 × 58 dp de la maquette à la densité xxxhdpi), fond blanc, PNG de quelques
   dizaines de Ko, dans `noBackupFilesDir/thumbnails/<documentId>-<date>.png`. Le fichier est
   écrit dans un fichier temporaire puis déplacé : jamais de fichier partiel. Le nom change à
   chaque dessin, ce qui suffit à rafraîchir l'écran. Les vignettes sont dessinées **quand la
   bibliothèque montre une ligne** dont la vignette manque ou date d'avant la dernière ouverture
   (le fichier a pu changer), une à la fois (verrou du dépôt), une seule demande par ouverture ;
   l'ancienne image reste affichée jusqu'au nouveau dessin. Le moteur est injecté
   paresseusement : la bibliothèque ne charge pas la bibliothèque native de MuPDF au démarrage.
   La bibliothèque décode seulement les fichiers des lignes visibles (`ThumbnailLoader`, sur le
   dispatcher d'E/S injecté).
8. **Page de départ** (`GetStartPageUseCase`) : la page demandée explicitement (argument de
   navigation, extra `page` d'« Ouvrir avec »), sinon la page enregistrée, sinon la première,
   **toujours ramenée au document** (qui peut avoir rétréci depuis). Ceci **amende l'ADR 0014
   §5** : un extra `page` absent, nul, négatif ou d'un autre type ne vaut plus « première page »
   mais « pas de page demandée » (`IncomingDocumentResult.Accepted.pageIndex` devient nullable,
   argument de navigation `NO_PAGE` = -1). L'activité des benchmarks demande toujours la page 1,
   pour que chaque itération parte du même endroit.
9. **Enregistrement de la page** (`ReadingHistory`, `feature:reader`) : une seconde après le
   dernier changement de page (jamais à chaque image ni à chaque page survolée), et une dernière
   fois à la fermeture du lecteur, dans une portée d'application injectée (`@ApplicationScope`) :
   le `viewModelScope` est déjà annulé quand `onCleared` s'exécute. Rien n'est écrit si la page
   n'a pas changé.
10. **Ce qui est enregistré** : seule une ouverture réussie ajoute ou remonte le document en tête
    de liste ; un document endommagé, non PDF, protégé ou inaccessible n'y entre pas. Une base
    pleine (`SQLiteException`) ne ferme pas le document : il n'est simplement pas enregistré.
11. **`LibraryViewModel` est rattaché à `MainActivity`** (et non à la destination de la
    bibliothèque) : `LutrinApp` reçoit son état sous forme de `StateFlow`, collecté seulement
    dans la destination, et reste un composable testable sans Hilt.
12. **Écarts avec la maquette E1** (complètent l'ADR 0009) :
    - pas de bouton « grille / liste » (miniatures, V10 en v1.x) ni « Outils » (M6) ;
    - pas de menu « ⋮ » par ligne : son action « Retirer des récents » arrive à l'étape suivante ;
    - pas de pastille de cadenas (documents protégés, avec V4) ;
    - jamais « 4 pages · Hier » : seul un document ouvert avec succès entre dans la liste, la
      ligne dit donc toujours « Page x / n » ;
    - l'heure suit la langue (« 20:41 » en français, « 8:41 PM » en anglais), pas le réglage
      12 / 24 h de l'appareil ; une date d'une autre année porte l'année (« 19 oct. 2025 ») ;
    - état « chargement » sans indicateur (la lecture prend quelques millisecondes ; un libellé
      reste annoncé par les lecteurs d'écran) et état « erreur » (liste illisible), absents de la
      maquette ;
    - la barre de progression est décorative pour les lecteurs d'écran (la page est déjà dite) ;
      la miniature est décrite (« Première page de … »).

## Conséquences

- Positives :
  - parcours « ouvrir → lire → revenir → rouvrir à la même page », depuis le sélecteur, la liste
    ou « Ouvrir avec » ; testé de bout en bout sous Robolectric (`RecentDocumentsTest`) ;
  - aucune permission nouvelle ; le nombre de permissions persistées reste borné ;
  - MuPDF reste derrière `PdfEngine`, l'ouverture du lecteur ne partage pas le CPU avec une
    vignette, et le démarrage ne charge pas MuPDF.
- Négatives / dette acceptée :
  - un document reçu par « Ouvrir avec » sans permission persistable apparaît dans la liste,
    souvent sans vignette, et peut ne plus s'ouvrir après la fermeture de l'application
    appelante (écran V8) ;
  - un même fichier ouvert par deux URI donne deux entrées (point ouvert 16) ;
  - zoom et décalage non restaurés ;
  - une vignette est dessinée au premier affichage de la bibliothèque qui suit l'ouverture, donc
    avec un léger délai (l'image d'attente de la maquette s'affiche entre-temps).
- Actions de suivi :
  - « Retirer des récents » (V8 et menu de ligne) et « Vider les récents » (E3) ;
  - essais sur appareil : permission persistée après redémarrage, liste de plus de 50
    documents, document supprimé ou permission retirée depuis « Fichiers » ;
  - empreinte du contenu (point ouvert 16), zoom et décalage (F12).
