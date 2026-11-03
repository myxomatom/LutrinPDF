# Points ouverts

Incohérences ou décisions en attente relevées dans les documents du projet.
Chaque point est tranché par un ADR ou une modification de `SPEC.md` / `ROADMAP.md`,
puis retiré de cette liste.

| # | Sujet | Constat | À trancher avant |
|---|-------|---------|------------------|
| 1 | `readAloud` vs F25 | `readAloud` est exposé aux assistants en v1.1 (SPEC §2.4), mais la lecture à voix haute (F25) est prévue en v1.x. La règle de parité (`AGENTS.md` §10) impose d'avancer F25 en v1.1 ou de retirer `readAloud` de la v1.1. | M7 |
| 2 | Grille de miniatures | V10 est prévue en v1.x, mais E7 (Extraction, v1.1) en a besoin : le composant doit exister dès M6. | M6 |
| 3 | Dossier de sortie | ROADMAP le place dans E10 (Assistants IA), SPEC dans les paramètres, les maquettes dans E5 (Outils). | M6 |
| 4 | Injection de dépendances | Hilt ou Koin selon la compatibilité F-Droid (SPEC §4.1) : ADR attendu. | M0 |
| 5 | Plugin de licences tierces | Choisir un plugin compatible F-Droid pour l'écran des licences (SPEC §7). | M0 |
| 6 | Licence affichée dans E4 | La maquette E4 indique « GNU GPL v3 » ; SPEC §7 demande de mentionner la distribution sous AGPL v3. | M5 |
| 7 | Schéma de couleurs sombre | `design-tokens.json` ne contient qu'une esquisse du thème sombre : générer un schéma M3 complet depuis `primary`. | M0 |
