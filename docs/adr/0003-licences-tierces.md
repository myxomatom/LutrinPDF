# 0003 — Licences des dépendances : vérification et écran des licences

- **Statut** : accepté
- **Date** : 2026-11-03
- **Références** : `SPEC.md` §7, `AGENTS.md` §1.4 et §7, `docs/open-questions.md` (point 5)

## Contexte

Deux besoins distincts :
1. **Vérifier en CI** que chaque dépendance embarquée a une licence compatible GPL v3 / AGPL v3.
2. **Afficher** les licences tierces dans l'écran « À propos » (E4, jalon M5), généré
   automatiquement et compatible F-Droid.

## Options envisagées

| Outil | Licence | Vérification CI | Écran | Remarques |
|-------|---------|-----------------|-------|-----------|
| **Licensee** (Cash App) | Apache-2.0 | Oui : liste blanche SPDX, échoue sinon | Non | Plugin Gradle seul, rien d'embarqué |
| **AboutLibraries** (mikepenz) | Apache-2.0 | Partielle | Oui, composant Compose | Très utilisé par les applications F-Droid |
| `oss-licenses-plugin` (Google) | — | Non | Oui | Requiert `play-services-oss-licenses`, sous licence Android SDK (propriétaire) : **exclu** |

## Décision

- **Licensee** dès M0 pour la vérification : appliqué à `:app`, il échoue si une dépendance
  du classpath d'exécution n'a pas une licence de la liste blanche `Apache-2.0`, `MIT`,
  `BSD-2-Clause`, `BSD-3-Clause` (`AndroidApplicationConventionPlugin`). Toute autre licence
  compatible (LGPL, GPL, AGPL — par exemple MuPDF au jalon M1) est ajoutée explicitement,
  avec sa justification dans `docs/dependencies.md`.
- **AboutLibraries** au jalon M5 pour l'écran des licences (YAGNI d'ici là).

## Conséquences

- La règle `AGENTS.md` §1.4 est automatisée pour le code distribué. Les outils de test et de
  build (jamais embarqués dans l'APK) sont recensés à part dans `docs/dependencies.md`.
- Une dépendance sans licence déclarée dans son POM fait échouer la CI : il faut alors
  vérifier sa licence à la main et l'autoriser nommément (`allowDependency`), avec une raison.
