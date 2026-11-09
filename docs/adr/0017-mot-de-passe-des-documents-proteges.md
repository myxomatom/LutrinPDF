# 0017 — Mot de passe des documents protégés (V4)

- **Statut** : accepté
- **Date** : 2026-11-09
- **Décideurs** : cadrage par la session d'orchestration ; détails techniques par l'agent de
  codage
- **Références** : `SPEC.md` F10, §2.4 (`PASSWORD_REQUIRED`), §6 (« Mot de passe incorrect »),
  §9 (« PDF chiffré (mot de passe connu) ») ; `AGENTS.md` §1.8, §3, §9, §11 ; `ROADMAP.md` M3,
  vues V4 et E1 ; ADR 0001 (moteur), 0008 (lecteur), 0009 (écarts avec les maquettes), 0014
  (« Ouvrir avec »), 0015 (récents, dont le §10 est amendé ici), 0016 (retrait des récents) ;
  maquettes `docs/design/png/V4-MotDePasse.png` (et `screens/V4-MotDePasse.html`) et la pastille
  de cadenas de « Relevé de compte » dans `docs/design/png/E1-Bibliotheque.png`

## Contexte

Le moteur sait ouvrir un PDF chiffré : `PdfEngine.open(source, password)` renvoie
`PdfError.PasswordRequired` sans mot de passe et `PdfError.IncorrectPassword` avec un mauvais
(ADR 0007). Jusqu'ici, le lecteur affichait pour ces deux erreurs l'écran V8 « protégé par un
mot de passe… arrive bientôt », et l'ADR 0015 (§10) tenait les documents protégés hors des
récents. F10 demande la saisie du mot de passe, qui « n'est jamais stocké » ; `AGENTS.md`
interdit de le journaliser (§1.8) et de le persister (§9).

Il faut décider : dialogue ou écran ; ce qu'il advient d'un mauvais mot de passe (limite,
délai) ; où vit le mot de passe tapé et comment il reste hors de l'état, des journaux et du
stockage ; ce qui se passe après une rotation ou la mort du processus ; le remplissage
automatique ; et si un document déverrouillé entre dans les récents.

## Options envisagées

**Présentation**
- **Écran** dédié dans le lecteur : plus de place, mais la maquette V4 et la ROADMAP
  (« Mot de passe (dialogue) ») décrivent un dialogue.
- **Dialogue** au-dessus du lecteur : conforme à la maquette ; le fond n'est pas la
  bibliothèque (voir les écarts).
- **Destination de navigation** de type dialogue avant le lecteur : le chiffrement n'est connu
  qu'à l'ouverture par le moteur, donc dans le lecteur ; il faudrait ouvrir deux fois ou
  partager le document ouvert entre destinations.

**Mauvais mot de passe**
- Nombre d'essais limité, puis retour ; délai croissant entre les essais ; ou ni l'un ni
  l'autre.

**Où vit le mot de passe tapé**
- Dans l'état de l'écran (`UiState`) ou le `SavedStateHandle` : simple, mais il apparaîtrait
  dans `toString()`, dans les journaux de débogage d'état, et dans le `Bundle` sauvegardé (sur
  disque après la mort du processus).
- Dans un `rememberTextFieldState` : sauvegardable, donc aussi dans le `Bundle`.
- Dans un `TextFieldState` **non sauvegardable** du dialogue, lu une seule fois au moment de
  l'essai.

**Récents** : un document déverrouillé entre dans la liste (avec un drapeau « protégé ») ; ou
il en reste exclu.

## Décision

1. **Dialogue** (`PasswordDialog`, `feature:reader`), affiché par le lecteur dans un nouvel état
   `ReaderUiState.PasswordRequired(documentName, isIncorrect, isChecking)` sur un lecteur vide
   (barre du haut avec « Retour »). Cadenas, « Document protégé », « « Relevé de compte » est
   chiffré. Saisissez son mot de passe pour l'ouvrir. », champ masqué « Mot de passe » avec un
   bouton œil (afficher / masquer, 48 dp, décrit), « Le mot de passe n'est jamais enregistré. »,
   « Annuler » et « Ouvrir ». « Ouvrir » est désactivé tant que le champ est vide ou pendant
   l'essai ; l'action du clavier (`ImeAction.Done`) fait la même chose qu'« Ouvrir ».
   « Annuler », le geste retour et la flèche du lecteur quittent le lecteur comme « Retour » ;
   un toucher hors du dialogue ne le ferme pas (une saisie n'est jamais perdue par mégarde). Le
   contenu défile avec les très grandes polices.
2. **Mauvais mot de passe** : message « Mot de passe incorrect. Réessayez. » sous le champ en
   erreur, région active (`liveRegion`) pour TalkBack ; le texte tapé est **gardé et
   sélectionné**, pour le retaper d'un coup. Le message disparaît pendant l'essai suivant et
   réapparaît s'il échoue : il est donc annoncé à chaque échec. **Pas de limite d'essais ni de
   délai** : le fichier est sur l'appareil, et quiconque veut deviner le mot de passe peut le
   copier et l'attaquer hors de Lutrin, sans limite ; une limite ou un délai ne gêneraient que
   l'utilisateur légitime (faute de frappe, mots de passe à essayer). Seul garde-fou : un essai à
   la fois (`isChecking`).
3. **Même chemin d'ouverture** : chaque essai rappelle `PdfEngine.open(source, password)` dans
   `ReaderViewModel.open`, comme une ouverture normale : page de départ (extra `page`, sinon page
   enregistrée), enregistrement dans les récents, suivi de la page, fermeture du document avec
   le lecteur. Une ouverture réussie **avec** mot de passe est enregistrée comme protégée
   (`DocumentOpening.isProtected`). Un document restreint par son seul mot de passe
   propriétaire (mot de passe utilisateur vide) s'ouvre sans rien demander et n'est pas
   « protégé ».
4. **Le mot de passe n'est jamais gardé** :
   - il n'est **pas dans l'état** : `PasswordRequired` ne contient que le nom du document et deux
     booléens ; aucun `toString()` d'état ne peut donc le montrer (testé) ;
   - il n'est **ni dans le `SavedStateHandle` ni dans les arguments de navigation** (qui restent
     l'URI et la page, testé), **ni dans Room ni DataStore** (le drapeau `is_protected` dit
     seulement qu'il en fallait un), **ni dans les journaux** (rien ne le journalise ; MuPDF est
     déjà muet, ADR 0007) ;
   - ce que l'utilisateur tape vit dans un `TextFieldState` du dialogue, créé par `remember`
     (**pas** `rememberTextFieldState`, qui le mettrait dans le `Bundle` sauvegardé), vidé
     (`clearText`) quand le dialogue disparaît (document ouvert, « Annuler », retour) ;
   - à l'essai, le `ViewModel` reçoit le `CharSequence` du champ, le convertit en `String` pour
     l'appel au moteur et n'en garde aucune référence : ni propriété, ni état ; la chaîne n'existe
     que dans la coroutine de l'essai (un test parcourt par réflexion les champs du `ViewModel`
     et de ses objets après un essai raté et après un essai réussi) ;
   - **limites assumées** : une `String` Kotlin est immuable et ne peut pas être effacée ; elle
     reste en mémoire jusqu'au passage du ramasse-miettes. L'API Java de MuPDF
     (`authenticatePassword(String)`) impose une `String`, et le tampon interne du
     `TextFieldState` et celui du clavier (IME) ne sont pas effaçables par l'application non
     plus. Passer un `CharArray` jusqu'à `core:pdf` ne ferait que déplacer la conversion, sans
     effacer ces copies : nous ne l'avons pas fait. La protection porte donc sur le **stockage**
     et les **journaux**, pas sur une lecture de la mémoire du processus (qui suppose un appareil
     déjà compromis).
5. **Rotation et mort du processus** : après une rotation, le `ViewModel` survit et redemande
   le mot de passe avec un **champ vide** (le champ n'est pas sauvegardable, par choix). Après
   la mort du processus, le lecteur rouvre le document depuis son URI et redemande. Un document
   déjà ouvert survit à une rotation (le `ViewModel` le garde) ; après la mort du processus, il
   redemande le mot de passe : c'est le prix de ne rien enregistrer.
6. **Clavier et remplissage automatique** : champ `OutlinedSecureTextField` (sémantique
   « mot de passe »), clavier `KeyboardType.Password`, correction automatique désactivée : le
   clavier n'apprend ni ne suggère. Le mot de passe est masqué en entier
   (`TextObfuscationMode.Hidden`, pas d'affichage furtif du dernier caractère) ; l'œil l'affiche
   à la demande. **Le dialogue reste hors du remplissage automatique** : sa fenêtre est marquée
   `IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS` et la session d'autofill est annulée
   (`AutofillManager.cancel()`) à l'essai et à la fermeture, pour qu'aucun gestionnaire de mots de
   passe ne propose d'enregistrer le mot de passe. Raison : un mot de passe de document n'est pas
   un identifiant de compte, il n'a pas de domaine auquel le rattacher, et un gestionnaire le
   stockerait (souvent synchronisé dans un nuage) hors de tout contrôle de l'application, contre
   F10. L'utilisateur qui le garde dans son gestionnaire peut toujours le coller. **Limite** :
   Compose déclare de lui-même ce champ comme champ de mot de passe à l'autofill
   (`ContentType.Password`) ; l'exclusion de la fenêtre et l'annulation sont un effort au mieux,
   le dernier mot revient au service d'autofill. À vérifier sur appareil.
7. **Récents** (amende l'ADR 0015 §10) : un document **ouvert avec son mot de passe entre dans la
   liste**, avec le drapeau `is_protected` :
   - base Room en **version 2** : colonne `is_protected INTEGER NOT NULL DEFAULT 0`, ajoutée par
     une migration automatique (`@AutoMigration(from = 1, to = 2)`) qui ne perd aucune entrée ;
     schéma 2 exporté dans `core/data/schemas/`, migration testée avec `MigrationTestHelper`
     (`room-testing`, Apache-2.0, `docs/dependencies.md`) ;
   - **aucune vignette** pour un document protégé : le dépôt ne demande jamais au moteur de
     l'ouvrir (il ne pourrait pas sans le mot de passe, et réessaierait à chaque affichage), la
     requête d'écriture d'une vignette ignore une entrée protégée (même devenue protégée pendant
     le dessin), et la vignette d'un document qui devient protégé est supprimée à son ouverture :
     une image de la première page montrerait ce que le mot de passe protège ;
   - la ligne montre la **pastille de cadenas** de la maquette E1 (décrite « Document protégé »,
     lue avec la ligne) sur l'image d'attente ;
   - la **page est reprise** comme pour tout document ; rouvrir depuis la liste **redemande le
     mot de passe** ; « Retirer des récents » et « Vider les récents » fonctionnent de même ;
   - un document rouvert plus tard sans mot de passe (protection retirée) perd le drapeau et
     retrouve sa vignette.
   Garder ces documents hors de la liste aurait privé l'utilisateur de la reprise de lecture
   (F12) pour ses documents les plus sensibles, sans rien protéger de plus : la liste ne contient
   que le nom, la taille et la page, déjà visibles pour les autres documents.
8. **Nom du document dans la question** : celui de la liste des récents, sinon le nom donné par
   le fournisseur (`DocumentNameResolver`, `core:domain`, implémenté dans `core:data` par la même
   requête `OpenableColumns.DISPLAY_NAME` que les récents), sinon le dernier segment de l'URI,
   sans l'extension `.pdf`.
9. **Assistants** (`SPEC.md` §2.4, v1.1) : rien n'est construit maintenant. Le domaine garde
   `PdfError.PasswordRequired` et `PdfError.IncorrectPassword`, que la future couche
   `feature:assistant` traduira en `PASSWORD_REQUIRED` ; le drapeau `RecentDocument.isProtected`
   lui permettra de répondre sans même ouvrir le document. Le mot de passe n'est jamais demandé
   ni transmis par un assistant : l'utilisateur ouvre le document lui-même.
10. **Jeu de test** (`SPEC.md` §9) : en plus du PDF AES-256 existant, trois PDF générés par
    `core/testing/scripts/generate_test_files.py` (fonction `encrypted_variants`) : RC4 128 bits,
    AES-128, et AES-128 avec mot de passe propriétaire seul. Mots de passe de test documentés dans
    `core/testing/README.md`, jamais de vrai secret. Tests du moteur sous JVM (Linux, CI) et
    instrumentés (appareil).

### Écarts avec la maquette V4 (complètent l'ADR 0009)

- Le dialogue s'affiche au-dessus du **lecteur vide** (flèche « Retour »), pas de la
  bibliothèque : le chiffrement n'est connu qu'une fois le lecteur ouvert (option
  « destination de dialogue » écartée ci-dessus).
- Après un mauvais mot de passe, le texte est **sélectionné** (surlignage visible), ce que la
  maquette ne montre pas.
- « Ouvrir » est grisé tant que le champ est vide.
- Le titre peut passer sur deux lignes, alignées au début, avec une police à 200 %.

## Conséquences

- Positives :
  - les documents chiffrés (RC4, AES-128, AES-256) s'ouvrent, se reprennent à la bonne page et
    figurent dans les récents ; parcours testé de bout en bout sous Robolectric
    (`ProtectedDocumentsTest`) ;
  - aucune copie du mot de passe dans l'état, le stockage, les arguments ou les journaux ;
  - aucune vignette d'un document protégé, aucune ouverture inutile par la bibliothèque.
- Négatives / dette acceptée :
  - le mot de passe reste en mémoire sous forme de `String` jusqu'au ramasse-miettes (MuPDF) ;
  - il faut le retaper après la mort du processus et à chaque réouverture depuis la liste ;
  - l'exclusion de l'autofill dépend du service installé ;
  - le nom et la page d'un document protégé sont dans la base, comme pour les autres documents.
- Actions de suivi :
  - essais sur appareil : TalkBack (annonce de l'erreur), clavier (pas de suggestions), Google
    Password Manager / Bitwarden (aucune proposition d'enregistrement), rotation pendant la
    saisie, mort du processus (`adb shell am kill`) ;
  - exécuter les tests instrumentés de `core:pdf` sur les trois nouveaux PDF chiffrés ;
  - `feature:assistant` (v1.1) : traduire `PasswordRequired` en `PASSWORD_REQUIRED`.
