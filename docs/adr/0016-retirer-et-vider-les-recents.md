# 0016 — Retirer un document des récents, vider les récents

- **Statut** : accepté
- **Date** : 2026-11-09
- **Décideurs** : cadrage par la session d'orchestration ; détails techniques par l'agent de
  codage
- **Références** : `SPEC.md` F11, §6 (« Fichier introuvable / permission révoquée »), §4.3
  (modules) ; `AGENTS.md` §3, §9, §11 ; `ROADMAP.md` M3, vues E1, E3, V8 ; ADR 0009 (écarts avec
  les maquettes), 0014 (« Ouvrir avec »), 0015 (récents, dont c'est l'étape annoncée) ;
  maquettes `docs/design/png/E1-Bibliotheque.png`, `E3-Parametres.png`, `V8-Erreur.png`

## Contexte

L'ADR 0015 a livré la liste des récents sans moyen d'en retirer un document : un document
reçu par « Ouvrir avec » sans permission persistable, un fichier supprimé ou déplacé, restent
dans la liste et ouvrent l'écran d'erreur V8. `SPEC.md` F11 demande de « retirer un élément ou
de vider la liste », et §6 de proposer le retrait quand le fichier est introuvable.

Retirer une entrée a des effets qu'on ne peut pas défaire : la permission persistante rendue au
système (`releasePersistableUriPermission`) ne peut pas être reprise sans que l'utilisateur
choisisse à nouveau le fichier dans le sélecteur ; la vignette et la page enregistrée sont
perdues. Il faut donc décider comment confirmer un retrait, où placer les actions, comment
garder `feature:reader` indépendant de `feature:library`, et ce que devient un lecteur ouvert
sur un document retiré.

Ce choix est assez distinct de l'ADR 0015 (déjà accepté et appliqué) pour mériter son propre
ADR plutôt qu'un amendement : il introduit un mécanisme d'annulation différée, une nouvelle
section des paramètres et une règle sur les retraits concurrents.

## Options envisagées

**Confirmation d'un retrait depuis la bibliothèque**
- **A — Dialogue de confirmation** : simple, mais une étape de plus pour une action fréquente
  et peu grave (le fichier n'est jamais supprimé).
- **B — Retrait immédiat, « Annuler » rétablit l'entrée** : la permission déjà rendue ne peut
  pas être reprise ; l'annulation rendrait une entrée qui ne s'ouvre plus.
- **C — Ligne masquée tout de suite, retrait effectif quand l'offre d'annulation se termine** :
  même confort que B, et « Annuler » ne touche à rien puisque rien n'a encore été retiré.

**« Vider les récents »** : retrait immédiat avec « Annuler » (même difficulté que B, pour
jusqu'à 50 permissions) ; ou dialogue de confirmation, comme pour toute action destructive et
irréversible.

**Retrait depuis V8** : appeler `feature:library` (interdit, `AGENTS.md` §3) ; ou passer par
un cas d'usage de `core:domain`.

## Décision

1. **Domaine** : `RecentDocumentsRepository.remove(documentId, openedAt?)` et `clear()`, avec
   les cas d'usage `RemoveRecentDocumentUseCase`, `ClearRecentDocumentsUseCase` et
   `IsRecentDocumentUseCase` (`core:domain`). Le dépôt Room retire chaque entrée par **le même
   chemin que l'éviction au-delà de 50** (ADR 0015 §4) : la ligne d'abord (et avec elle la page
   enregistrée, qui vit dans la ligne), puis la permission rendue (une `SecurityException`,
   quand elle n'a jamais été persistée, est ignorée), puis le fichier de la vignette (absent :
   sans effet). Une erreur de la base (`SQLiteException`) laisse l'entrée entière. Retirer un
   identifiant inconnu ou vider une liste vide ne fait rien. Les retraits prennent le verrou
   des vignettes, l'éviction aussi désormais : une vignette dessinée pendant le retrait de son
   entrée n'est jamais laissée sur le disque.
2. **Bibliothèque : option C.** Chaque ligne a un bouton « ⋮ » (cible de 48 dp, décrit
   « Plus d'actions pour <nom> ») qui ouvre un menu avec « Retirer des récents ». La ligne
   disparaît aussitôt et un message « « nom » retiré des récents » propose « Annuler »
   (`SnackbarDuration.Long`, 10 s, allongée par le système quand un lecteur d'écran est actif).
   Le retrait effectif (`LibraryViewModel.onConfirmRemoval`) a lieu quand le message se ferme
   sans annulation : délai écoulé, glissement, nouveau retrait, ou message qui quitte l'écran
   (navigation, rotation, fermeture de l'activité, et `onCleared` en dernier recours). Il
   s'exécute dans la portée d'application (`@ApplicationScope`), pour ne jamais s'arrêter à
   mi-chemin.
3. **Document rouvert pendant l'annulation** : le retrait différé porte la date de dernière
   ouverture vue par la bibliothèque (`PendingRemoval.openedAt`) ; si le document a été rouvert
   entre-temps (par le sélecteur ou « Ouvrir avec »), le dépôt le garde, et la ligne réapparaît
   (elle n'est masquée que pour cette date d'ouverture).
4. **V8** : quand le document qui ne s'ouvre pas est dans la liste
   (`ReaderUiState.Error.inRecents`), l'écran propose « Retirer des récents » au-dessus du
   bouton de retour, comme la maquette. Le bouton est proposé **pour toutes les causes
   d'erreur**, pas seulement « introuvable / permission révoquée » : la maquette le montre sur
   un document endommagé, et un document qui ne s'ouvre plus n'a pas sa place dans la liste.
   Pas d'annulation ici : l'utilisateur vient de constater que le document ne s'ouvre pas. Le
   retrait passe par `ReadingHistory` (`IsRecentDocumentUseCase`, `RemoveRecentDocumentUseCase`),
   s'exécute dans la portée d'application, puis le lecteur se ferme comme avec « Retour » :
   bibliothèque, ou application appelante pour un document reçu d'une autre application
   (ADR 0014). `feature:reader` ne dépend toujours pas de `feature:library`.
5. **Paramètres (E3)** : section « Données et confidentialité » de la maquette, avec « Vider les
   récents » (icône corbeille). Un **dialogue de confirmation** (« Vider les récents ? »,
   nombre de documents, « les fichiers eux-mêmes ne sont pas supprimés », « Vider » /
   « Annuler ») précède le vidage, qui s'exécute dans la portée d'application. Quand la liste
   est vide (ou illisible), la ligne reste visible mais **désactivée**, avec « Aucun document
   récent » en dessous : l'utilisateur sait où se trouve l'action.
6. **Lecteur ouvert sur un document retiré** (par une autre tâche, « Ouvrir avec » dans
   l'application appelante, ou un vidage) : il continue de fonctionner ; ses enregistrements de
   page (après 1 s, et à la fermeture) ne font qu'une mise à jour (`UPDATE ... WHERE uri = ?`)
   et ne recréent jamais l'entrée. Seule une nouvelle ouverture réussie la recrée, sans
   l'ancienne page.
7. **Écarts avec les maquettes** (complètent les ADR 0009 et 0015) :
   - E1 : le menu « ⋮ » ne contient que « Retirer des récents » (d'autres actions viendront
     avec leurs fonctionnalités : propriétés V5, partage) ;
   - E3 : la section « Données et confidentialité » ne contient que « Vider les récents »
     (« Assistants IA » en v1.1, « À propos et licences » avec E4) ; un séparateur la sépare
     d'« Apparence » comme sur la maquette ; la ligne vide porte un sous-titre absent de la
     maquette ;
   - V8 : le bouton de retour garde le libellé « Retour » (il ramène aussi à l'application
     appelante, ADR 0014), au lieu de « Retour à la bibliothèque ». Les deux boutons ont la
     hauteur de 48 dp de la maquette.

## Conséquences

- Positives :
  - un retrait se fait en deux touchers et s'annule sans perte ; aucune permission n'est rendue
    tant que l'utilisateur peut encore annuler ;
  - toutes les sorties de la liste (éviction, retrait, vidage) passent par un seul chemin du
    dépôt : permission rendue, vignette supprimée, page oubliée ;
  - testé : cas d'usage, faux dépôt, dépôt Room avec Robolectric (permission rendue au
    `ContentResolver`, `SecurityException` tolérée, vignette absente, identifiant inconnu, liste
    vide, retrait concurrent d'un dessin de vignette, page enregistrée après retrait),
    ViewModels, écrans, captures, et de bout en bout dans `app` (`RecentDocumentsTest`).
- Négatives / dette acceptée :
  - si le processus est tué pendant les 10 s de l'offre d'annulation, le retrait est perdu et
    la ligne réapparaît au prochain lancement (rien n'est retiré à tort) ;
  - une rotation pendant l'offre confirme le retrait (le message quitte l'écran) : l'annulation
    n'est plus proposée après la rotation ;
  - un vidage ne peut pas être annulé, d'où le dialogue.
- Actions de suivi :
  - essais sur appareil avec TalkBack (menu de ligne, message d'annulation, dialogue) ;
  - vérifier sur appareil que `persistedUriPermissions` diminue après un retrait.
