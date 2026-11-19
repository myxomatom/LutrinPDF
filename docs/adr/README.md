# Décisions d'architecture (ADR)

Une décision structurante = un fichier `NNNN-titre-court.md`, créé à partir de
[`0000-modele.md`](0000-modele.md) (contexte, options, décision, conséquences).
Un ADR accepté n'est pas réécrit : on le remplace par un nouvel ADR.

| N° | Décision | Statut |
|----|----------|--------|
| [0001](0001-moteur-pdf-mupdf.md) | Moteur PDF : MuPDF | Accepté |
| [0002](0002-injection-dependances-hilt.md) | Injection de dépendances : Hilt | Accepté |
| [0003](0003-licences-tierces.md) | Licences : Licensee (CI) et AboutLibraries (écran, M5) | Accepté |
| [0004](0004-sauvegarde-desactivee.md) | Sauvegarde Android désactivée | Accepté |
| [0005](0005-outillage-build-qualite.md) | Outillage de build, de qualité et de test | Accepté |
| [0006](0006-identifiant-application.md) | Identifiant d'application `io.github.myxomatom.lutrin` | Accepté |
| [0007](0007-integration-mupdf.md) | Intégration de MuPDF : compilation et interface `PdfEngine` | Accepté |
| [0008](0008-rendu-du-lecteur.md) | Rendu des pages dans le lecteur (base + détail visible) | Accepté |
| [0009](0009-ecarts-maquettes-m2.md) | Écarts assumés entre les maquettes et l'application (M2) | Accepté |
| [0010](0010-couleurs-dynamiques-et-police.md) | Couleurs dynamiques désactivables et police du système | Accepté |
| [0011](0011-lecture-page-par-page.md) | Mode de lecture page par page et options d'affichage (V6) | Accepté |
| [0012](0012-benchmark-et-baseline-profile.md) | Module `:benchmark`, ouverture d'un document sans sélecteur, Baseline Profile | Accepté |
| [0013](0013-build-sous-windows.md) | Build sous Windows : ressources de MuPDF générées par Gradle, tests JVM du moteur sous Linux seulement | Accepté |
| [0014](0014-ouvrir-avec-et-partage-entrant.md) | « Ouvrir avec » et partage entrant : routage des intents, mode de lancement, validation des URI | Accepté |
| [0015](0015-recents-et-reprise-de-lecture.md) | Récents (Room), `documentId`, permissions persistées, vignettes, reprise de la dernière page | Accepté |
| [0016](0016-retirer-et-vider-les-recents.md) | Retirer un document des récents (menu de ligne avec annulation, V8) et vider les récents (E3) | Accepté |
| [0017](0017-mot-de-passe-des-documents-proteges.md) | Mot de passe des documents protégés (V4) : dialogue, mot de passe jamais gardé, récents protégés (schéma 2) | Accepté |
| [0018](0018-nouveau-design-visuel.md) | Nouveau design visuel (E1, E2, E9) : titres en Fraunces, accent chaud, carte de reprise, barre flottante, aperçu optionnel du curseur | Accepté |
| [0019](0019-transition-vignette-lecteur.md) | Transition animée vignette → lecteur : éléments partagés, retour prédictif, cas de repli ; amendé : l'aller est un simple fondu, seul le retour replie la page vers la vignette | Accepté, amendé |
| [0020](0020-api-texte-et-destinations.md) | API texte et destinations du moteur : position des liens et du sommaire, sélection entre deux points (mot, ligne, arabe), recherche page par page avec extraits et plafond, permission de copie | Accepté |
| [0021](0021-presentation-table-des-matieres.md) | Table des matières (V2) : tiroir latéral, sommaire aplati dans l'état, section courante dépliée, liste paresseuse, entrées externes reportées sur V7 | Accepté |
| [0022](0022-liens-externes.md) | Liens du document : schémas `http`, `https`, `mailto` seulement, confirmation V7, ouverture par `ACTION_VIEW` sans accès réseau, « Revenir » après un lien interne, toucher et TalkBack | Accepté |
| [0023](0023-recherche-dans-le-lecteur.md) | Recherche dans le lecteur : contrôleur dédié, 400 ms après la frappe, depuis la page courante, résultats en ordre du document, calque de surlignage dans chaque page, carte d'extrait, annonces TalkBack sans répétition | Accepté |
| [0024](0024-selection-et-copie.md) | Sélection et copie : appui long sur un mot, poignées, « Copier » / « Tout sélectionner », une page à la fois, permission de copie respectée sauf réglage « Ignorer l'interdiction de copie », contenu sensible si protégé, action TalkBack « Copier le texte de la page » | Accepté |
| [0025](0025-police-de-secours-cjk.md) | Texte CJK non incorporé : polices CJK du système, pas de police embarquée (DroidSansFallback mesurée puis écartée) | Accepté |

ADR attendus (voir `SPEC.md`, `ROADMAP.md` et [`../open-questions.md`](../open-questions.md)) :

- AppFunctions : statut, dépendances, compatibilité F-Droid — M7.
- Emplacement du réglage « dossier de sortie » — M6.
