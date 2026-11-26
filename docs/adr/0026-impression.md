# 0026 — Impression

- **Statut** : accepté
- **Date** : 2026-11-25
- **Décideurs** : choix fonctionnels par le mainteneur (bouton « Imprimer » à la place du bouton
  « pleine largeur » de la barre flottante, PDF d'origine pour un document normal, images des pages
  pour un document ouvert avec un mot de passe, PDF déchiffré jamais écrit, interdiction
  d'impression respectée par défaut, réglage « Ignorer les restrictions du document » qui couvre la
  copie et l'impression, rotation F27 ignorée) ; détails techniques par l'agent de codage
- **Références** : `SPEC.md` F15, F27 ; `AGENTS.md` §1.8, §3, §9, §10, §11 ; `ROADMAP.md` M5
  (lot 1) ; ADR 0017 (mot de passe), 0018 (design E2), 0020 (permissions du document), 0024
  (sélection et copie, réglage amendé ici)

## Contexte

F15 demande l'impression par le `PrintManager` du système. Un PDF peut être chiffré et ouvert
avec un mot de passe (ADR 0017), et son auteur peut interdire l'impression dans les permissions
du document, comme il peut interdire la copie (ADR 0020, 0024). Il fallait : une place dans
l'interface, une manière d'envoyer le document au service d'impression sans jamais écrire le PDF
déchiffré, une mémoire bornée quelle que soit la longueur du document, et une règle pour
l'interdiction d'impression.

## Options envisagées

### Emplacement du bouton
- Dans la feuille « Affichage » (V6) ou un menu « ⋮ » : une action de plus à chercher.
- **Dans la barre flottante, à la place du bouton « pleine largeur »** (retenue par le
  mainteneur) : à côté du curseur, toujours à portée de pouce. L'ajustement à la largeur ira dans
  la feuille « Affichage » au lot 4 de M5 ; le double toucher remet déjà le zoom à 1.

### Ce qu'on envoie au service d'impression
- **Document ouvert sans mot de passe : le fichier PDF d'origine** (retenue), copié tel quel
  depuis son URI. Qualité d'origine (texte vectoriel), aucun rendu, aucune mémoire.
- **Document ouvert avec son mot de passe : des images des pages** (retenue). Copier le fichier
  enverrait un PDF chiffré que le service d'impression ne sait pas ouvrir ; écrire le PDF déchiffré
  est exclu (`AGENTS.md` §9, ADR 0017).

### Format du PDF d'images
- **`android.graphics.pdf.PdfDocument`** (piste initiale du mainteneur) : le code natif
  d'Android enregistre chaque page dans une `SkPicture` qui garde une copie du bitmap jusqu'à
  `writeTo`. Pour un document de 600 pages à 150 ppp (8,7 Mo par page A4), cela ferait plusieurs
  gigaoctets : la mémoire n'est pas bornée.
- **Un petit écrivain de PDF d'images en Kotlin** (retenue) : `ImagePdfWriter` écrit chaque page
  dès qu'elle est dessinée (image RVB compressée en Flate, sans perte), puis l'arbre des pages et
  la table des références. Une page en mémoire à la fois, testable sur la JVM. C'est un écart à la
  consigne initiale, demandé par la consigne de mémoire bornée qu'elle accompagnait.

### Pages choisies (« Pages : 2-5 »)
- Filtrer le PDF d'origine : il faudrait réécrire le PDF (fusion, extraction : `PdfWriter`, M6).
- **Fichier d'origine entier, déclaré `PageRange.ALL_PAGES`** (retenue) : le service
  d'impression du système garde lui-même les pages choisies (le `PrintSpooler` d'AOSP retire les
  pages non choisies d'un document qui en contient plus ; à vérifier sur appareil). Pour les images, seules les pages demandées sont dessinées, et elles
  sont déclarées comme écrites.

## Décision

1. **Bouton « Imprimer »** dans la barre flottante (E2), à la place du bouton « pleine largeur »,
   qui est retiré (chaînes `reader_fit_*` et icône supprimées). Il disparaît avec la barre en
   plein écran et pendant une recherche (la carte d'extrait remplace la barre) : accepté. Icône
   d'imprimante dessinée au trait comme les autres icônes de E2. Cible de 48 dp, nommé
   « Imprimer » pour TalkBack. **Écart avec la maquette E2** (`docs/design/proposals/`), qui
   montre le bouton « pleine largeur » : consigné ici. Le double toucher zoome toujours (testé).
2. **Lancement depuis la route** (`ReaderRoute`) : `startPrinting(context, job)` avec le `Context`
   de l'activité, `PrintManager.print(nom, adaptateur, null)`. Le ViewModel ne voit jamais de
   `Context` : il fournit seulement `printJob()` (`PrintJob` : nom, source, dispatcher d'E/S
   injecté). Sans service d'impression (certaines builds d'Android), un message le dit.
3. **Nom** : le nom du document dans les récents ou chez son fournisseur, sinon le dernier segment
   de l'URI, sans « .pdf » ; c'est le nom du travail d'impression, et `<nom>.pdf` celui du
   document (proposé par « Enregistrer au format PDF »).
4. **Adaptateur** `DocumentPrintAdapter` (paquet `print` de `feature:reader`) :
   - `onLayout` : nombre de pages connu, type « document » ; le contenu ne dépend jamais du
     papier (le service ajuste chaque page au papier), donc seul le premier calcul le change ;
   - `onWrite` sur le dispatcher d'E/S injecté, une seule réponse par demande (terminé, échec ou
     annulé), y compris pour une annulation avant le début ; `onFinish` annule l'écriture en
     cours ;
   - **original** : copie par blocs de 64 Kio, annulation vérifiée entre deux blocs ; un fichier
     illisible, une permission retirée, un fournisseur muet ou une destination impossible à écrire
     donnent un échec ;
   - **document protégé** : pages demandées seulement, dans l'ordre, chacune dessinée par
     `PdfDocument.renderPage` (sur le thread du document) à **150 ppp**, abaissés pour qu'une page
     ne dépasse pas 8 millions de pixels (32 Mo) ; fond blanc ; un seul bitmap, réutilisé tant que
     la taille ne change pas et recyclé à la fin ; annulation vérifiée avant chaque page ; un
     document fermé entre-temps (lecteur quitté) donne un échec.
5. **Le PDF déchiffré n'est jamais écrit** et le mot de passe n'est jamais gardé (ADR 0017) : seul
   le document déjà ouvert dessine ses pages. Le flux d'images va directement au descripteur donné
   par le service d'impression, sans fichier temporaire.
6. **Permission d'impression** : `PdfDocument.canPrint` (`core:pdf`, `Document.PERMISSION_PRINT`
   de MuPDF, lue à l'ouverture comme `canCopyText`), rapportée et non appliquée par le moteur.
   `AppSettings.allowsPrinting(documentAllowsPrinting)` (`core:domain`) combine la permission et le
   réglage ; `ReaderUiState.Ready.canPrint` la suit en direct ; `printJob()` la relit à chaque
   appui. `encrypted_owner_password_only.pdf` interdit l'impression (permissions « accessibilité »
   seulement), vérifié par `MuPdfEncryptionTest`, `MuPdfEngineInstrumentedTest` et
   `PrintInstrumentedTest`.
7. **Interdiction respectée par défaut** : le bouton reste visible mais atténué (38 %), lu
   « Désactivé, interdit par l'auteur » par TalkBack, et un appui explique « L'auteur de ce
   document interdit son impression ».
8. **Réglage « Ignorer les restrictions du document »** (E3 › Lecture), ancien « Ignorer
   l'interdiction de copie » (ADR 0024, amendé) : il couvre la copie **et** l'impression. Les
   identifiants deviennent `ignoreDocumentRestrictions`, `setIgnoreDocumentRestrictions`,
   `SetIgnoreDocumentRestrictionsUseCase` ; **la clé DataStore `ignore_copy_restriction` est
   gardée** : un utilisateur qui l'avait activé la garde activée, sans migration. Le réglage
   couvrant désormais l'impression, ce choix étend en silence sa portée pour ceux qui l'avaient
   activé ; c'est voulu (un seul réglage, désactivé par défaut, explication mise à jour).
9. **Rotation de l'affichage (F27)** : jamais appliquée à l'impression, qui imprime le fichier tel
   qu'il est (pour un document protégé : chaque page avec la rotation enregistrée dans le fichier).
10. **Tests** : écrivain de PDF (structure, références croisées, pixels sans perte, lecture ligne
    par ligne), adaptateur sous Robolectric (mise en page, original entier ou plage, pages
    choisies dessinées, annulation avant, pendant le dessin, pendant la copie et à la fermeture du
    dialogue, erreurs d'E/S, document fermé), ViewModel (permission × réglage, source selon le mot
    de passe, nom), Compose (bouton, plein écran, double toucher, interdiction expliquée, réglage
    qui l'active, appareil sans impression, français), captures Roborazzi (barre claire, sombre,
    200 %, française, interdite ; E3), test instrumenté qui ouvre la boîte d'impression du
    système avec UiAutomator (paquet `com.android.printspooler`).

## Conséquences

- Positives : impression fidèle (fichier d'origine) dans le cas courant ; un document protégé
  s'imprime sans jamais écrire son contenu déchiffré, en mémoire bornée ; une seule règle de
  restriction, dans le domaine, pour la copie et l'impression.
- Négatives / dette acceptée :
  - un document protégé s'imprime en images à 150 ppp : texte non sélectionnable dans un
    « Enregistrer au format PDF », fichier plus lourd (Flate sans perte), qualité inférieure à
    l'original sur une imprimante de 600 ppp ;
  - pour un document protégé, le PDF d'images de tout le document peut peser plusieurs centaines
    de Mo dans le service d'impression (c'est le service qui le garde, pas l'application) ;
  - le service d'impression demande d'abord un aperçu : pour un long document protégé, toutes
    les pages sont dessinées avant que l'aperçu apparaisse ;
  - l'ajustement à la largeur n'a plus de bouton jusqu'au lot 4 (feuille « Affichage ») ; le
    double toucher et le pincement restent ;
  - le bouton disparaît pendant une recherche ;
  - le test instrumenté suppose le paquet `com.android.printspooler` (AOSP, émulateurs, la plupart
    des appareils) ; un constructeur qui le renomme ferait échouer ce test, pas l'impression.
- Actions de suivi : essais sur appareil (vraie imprimante ou « Enregistrer au format PDF »,
  document protégé, impression interdite puis permise par le réglage, TalkBack) ; lot 4 :
  « Ajuster à la largeur » dans la feuille « Affichage ».

## Amendement du 2026-11-26 — écart à `AGENTS.md` §10

L'audit du 2026-11-26 (`docs/audits/2026-11-26-audit-complet.md`, constat X1) relève que
`ImagePdfWriter` enfreint deux règles que cet ADR ne citait pas :

- `AGENTS.md` §10 : toute écriture de PDF passe par `PdfWriter` ;
- `AGENTS.md` §3 : la manipulation du format PDF reste dans `core:pdf`.

L'écart est maintenu jusqu'au jalon M6, qui crée `PdfWriter` (dont `fromImages`, `SPEC.md` §4),
pour les raisons de la décision ci-dessus : la mémoire doit rester bornée, et `PdfWriter`
n'existe pas encore. Sa portée est limitée à l'impression d'un document protégé. Le fichier
produit n'est remis qu'au service d'impression du système et n'est jamais enregistré par
l'application.

- **Action de suivi (M6)** : remplacer `ImagePdfWriter` par `PdfWriter.fromImages` dans
  `core:pdf`, en gardant l'écriture page par page, puis supprimer le paquet d'écriture de
  `feature:reader`. Aucun autre écrivain de PDF ne doit être ajouté hors de `core:pdf` d'ici là.
