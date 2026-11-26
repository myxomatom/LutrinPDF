# 0027 — Partage du document et feuille « Propriétés » (V5)

- **Statut** : accepté
- **Date** : 2026-11-26
- **Décideurs** : choix fonctionnels par le mainteneur (le fichier est partagé tel quel, y compris
  protégé ; pas de copie ni de fournisseur ; rotation ignorée ; liste des propriétés) ; détails
  techniques par l'agent de codage
- **Références** : `SPEC.md` F15, F16 ; `AGENTS.md` §1, §3, §9, §11 ; `ROADMAP.md` M5 (lot 2) ;
  ADR 0014 (« Ouvrir avec » et partage entrant), 0015 (récents), 0017 (documents protégés), 0026
  (impression) ; audit du 2026-11-26, constat S1 ; maquette `docs/design/screens/V5-Proprietes.html`

## Contexte

F15 demande le partage du fichier par intent, F16 les propriétés du document (titre, auteur,
nombre de pages, taille, version PDF, dates). La maquette V5 est une feuille « Propriétés » avec
deux boutons, « Partager » et « Imprimer », sans dire comment on l'ouvre. Il fallait décider : ce
qu'on envoie à l'autre application (un PDF protégé en particulier), si Lutrin doit exposer un
fournisseur de contenu, où placer les actions, et d'où viennent les valeurs.

## Options envisagées

### Ce que reçoit l'autre application
- **Une copie dans `cacheDir`, servie par un `FileProvider`** : l'URI est toujours transmissible,
  mais Lutrin exposerait un fournisseur (surface d'attaque, constat S1 de l'audit), copierait le
  fichier (espace disque, fichier temporaire à nettoyer) et, pour un document protégé, devrait
  choisir entre un chiffré copié et un déchiffré écrit (exclu, ADR 0017).
- **Le fichier tel quel, par son URI `content://` d'origine** (retenue) : `ACTION_SEND`, type
  `application/pdf`, `FLAG_GRANT_READ_URI_PERMISSION`, dans `Intent.createChooser`. Aucune copie,
  aucun fournisseur, un document protégé reste chiffré et son mot de passe n'est pas concerné.

### Où placer les actions
- Dans la barre flottante : occupée par « Imprimer » (ADR 0026) ; non retenue.
- **Dans le menu « Plus d'options » du lecteur** (retenue) : le bouton ⋮ ouvrait directement la
  feuille « Affichage » (V6) ; il ouvre maintenant un petit menu : « Affichage », « Partager »,
  « Propriétés ». La maquette V5 ne dit pas comment on l'atteint ; elle montre la feuille avec
  « Partager » et « Imprimer », reprise telle quelle.

### Valeur absente
- L'omettre : la liste change de forme d'un document à l'autre, et un titre ou un auteur absent
  est une information.
- **« Non renseigné »** (retenue), en couleur secondaire, lu avec son libellé par TalkBack.

## Décision

1. **Partage** : `ReaderViewModel.shareRequest()` fournit l'URI d'origine du document et son nom ;
   l'écran (`ReaderRoute`) lance `startSharing(context, request)`, comme pour l'impression : le
   ViewModel ne voit jamais de `Context`. L'intent porte l'URI en `EXTRA_STREAM` **et** en
   `ClipData` (c'est le `ClipData` qui porte l'autorisation de lecture), rien d'autre ; aucune
   autorisation d'écriture. Le chooser du système exclut les récepteurs de Lutrin lui-même
   (`EXTRA_EXCLUDE_COMPONENTS`) : partager un document vers le lecteur qui le détient n'a pas de
   sens. **Aucun fournisseur de contenu n'est ajouté** : le constat S1 de l'audit (contrôle « URI de
   Lutrin » des intents entrants) n'est pas bloquant pour ce lot.
2. **Cas où le partage échoue** : sans application capable de recevoir le fichier
   (`ActivityNotFoundException`) le message « Aucune application ne peut recevoir ce fichier »
   s'affiche ; si le fournisseur refuse de transmettre son document (`SecurityException` : URI
   non transmissible) ou si l'URI n'est pas de schéma `content` (une URI `file://` lèverait
   `FileUriExposedException`), « Ce fichier ne peut pas être partagé ». Jamais de plantage. **Limite
   assumée** : tant que l'application ne déclare pas de `<queries>` pour `ACTION_SEND`, elle ne peut
   pas savoir à l'avance (Android 11+) qu'aucune application ne peut recevoir un PDF ; c'est le
   sélecteur du système qui l'annonce dans ce cas.
3. **La rotation de l'affichage (F27) ne s'applique jamais au partage** (le fichier est envoyé
   tel quel).
4. **Feuille « Propriétés »** (V5) : feuille modale au-dessus du lecteur, le ViewModel garde les
   propriétés (`ReaderViewModel.properties`, `onPropertiesRequested`, `onPropertiesDismissed`) pour
   que la feuille survive à une rotation de l'écran. Lignes, dans l'ordre de la maquette : Fichier,
   Titre, Auteur, Pages, Taille, Version PDF, Créé le, Modifié le. Les deux boutons sont fixes en
   bas ; seuls le titre et les lignes défilent (grande police). « Imprimer » reste visible mais
   atténué quand le document l'interdit, avec le même message que la barre flottante (ADR 0026) ;
   « Partager » et « Imprimer » ferment la feuille avant d'agir, pour que le message ou la boîte
   du système s'affichent au-dessus du document.
5. **Origine des valeurs** : le moteur lisait déjà tout ce qu'il faut (`PdfMetadata` : titre, auteur,
   dates de création et de modification, `format` = `PDF 1.7`) : **aucun ajout à `PdfDocument`**.
   Le nom et la taille du fichier ne viennent que du fournisseur : nouvelle interface de domaine
   `DocumentInfoResolver`, implémentée dans `core:data` par `ContentResolverDocumentInfoResolver`
   au-dessus de `DocumentAccess` (la même requête `OpenableColumns` que les récents). Le cas
   d'usage `GetDocumentPropertiesUseCase` assemble `DocumentProperties` ; la version « 1.7 » est
   extraite de `format` ; les textes vides et les tailles négatives sont traités comme inconnus.
   Le nom du fichier est celui du fournisseur, **extension comprise** (la maquette montre
   `contrat_location_2026.pdf`), alors que `DocumentNameResolver` le donne sans « .pdf ».
6. **Formats** : dates par `DateTimeFormatter.ofLocalizedDateTime(MEDIUM, SHORT)` dans la langue et
   le fuseau de l'appareil (une date PDF sans fuseau est lue en UTC par `parsePdfDate` et montrée
   dans le fuseau de l'appareil) ; taille par `android.text.format.Formatter.formatFileSize`
   (« 412 ko » en français, la maquette écrit « Ko »).
7. **Pas de journal** du nom, du chemin ni du contenu ; aucune permission ni dépendance ajoutée.
8. **Tests** : cas d'usage et résolveur (domaine, données), ViewModel (propriétés, annulation,
   demande de partage pour un document protégé, URI `file://`), intent et chooser sous Robolectric
   (autorisation de lecture seule, rien d'autre dans l'intent, récepteurs de Lutrin exclus, échec
   sans chooser, échec sur refus d'autorisation), Compose (menu, feuille, valeurs absentes, dates,
   messages, impression interdite, FR), captures Roborazzi de V5 (clair, sombre, 200 %, français,
   valeurs absentes, impression interdite), test instrumenté de l'intent avec Espresso-Intents
   (`ShareInstrumentedTest`) et des propriétés lues par le vrai moteur
   (`MuPdfEngineInstrumentedTest`).

## Conséquences

- Positives : partage sans copie ni fournisseur ; un document protégé ne sort jamais déchiffré ;
  une feuille de propriétés sans nouvelle dépendance ni permission ; le moteur n'a pas changé.
- Négatives / dette acceptée :
  - **le partage ne marche que si le fournisseur d'origine accepte de transmettre son URI**
    (c'est le cas des fournisseurs du Storage Access Framework ; un fournisseur tiers qui refuse
    donne « Ce fichier ne peut pas être partagé »). Si ce cas se révèle fréquent sur appareil, il
    faudra une copie dans `cacheDir` et un `FileProvider`, donc corriger S1 d'abord ;
  - l'ouverture du lecteur par « ⋮ » demande un geste de plus pour « Affichage » ;
  - « Partager » pendant que le fournisseur est lent n'a pas d'indicateur : l'action ne lit pas le
    fichier, c'est l'application destinataire qui le lit.
- À essayer sur appareil : partage vers une messagerie, vers un gestionnaire de fichiers, vers
  « Enregistrer sur l'appareil », d'un document ouvert par le sélecteur (SAF), par « Ouvrir avec »
  (autorisation temporaire) et d'un PDF protégé ; recherche d'une application qui refuse l'URI.
