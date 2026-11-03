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

ADR attendus (voir `SPEC.md`, `ROADMAP.md` et [`../open-questions.md`](../open-questions.md)) :

- AppFunctions : statut, dépendances, compatibilité F-Droid — M7.
- Emplacement du réglage « dossier de sortie » — M6.
