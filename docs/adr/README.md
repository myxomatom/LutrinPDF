# Décisions d'architecture (ADR)

Une décision structurante = un fichier `NNNN-titre-court.md`, créé à partir de
[`0000-modele.md`](0000-modele.md) (contexte, options, décision, conséquences).
Un ADR accepté n'est pas réécrit : on le remplace par un nouvel ADR.

| N° | Décision | Statut |
|----|----------|--------|
| [0001](0001-moteur-pdf-mupdf.md) | Moteur PDF : MuPDF | Proposé |

ADR attendus (voir `SPEC.md`, `ROADMAP.md` et [`../open-questions.md`](../open-questions.md)) :

- Injection de dépendances : Hilt ou Koin (compatibilité F-Droid) — M0.
- Plugin de génération de l'écran des licences tierces (compatible F-Droid) — M0.
- AppFunctions : statut, dépendances, compatibilité F-Droid — M7.
- Emplacement du réglage « dossier de sortie » — M6.
