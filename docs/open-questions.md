# Points ouverts

Incohérences ou décisions en attente relevées dans les documents du projet.
Chaque point est tranché par un ADR ou une modification de `SPEC.md` / `ROADMAP.md`,
puis déplacé dans la section « Tranchés ».

| # | Sujet | Constat | À trancher avant |
|---|-------|---------|------------------|
| 1 | `readAloud` vs F25 | `readAloud` est exposé aux assistants en v1.1 (SPEC §2.4), mais la lecture à voix haute (F25) est prévue en v1.x. La règle de parité (`AGENTS.md` §10) impose d'avancer F25 en v1.1 ou de retirer `readAloud` de la v1.1. | M7 |
| 2 | Grille de miniatures | V10 est prévue en v1.x, mais E7 (Extraction, v1.1) en a besoin : le composant doit exister dès M6. | M6 |
| 3 | Dossier de sortie | ROADMAP le place dans E10 (Assistants IA), SPEC dans les paramètres, les maquettes dans E5 (Outils). | M6 |
| 8 | Polices de secours CJK | Le build Android de MuPDF exclut les polices de secours (Noto, CJK, SIL) : un texte chinois / japonais / coréen dont la police n'est pas incorporée au PDF s'affiche en carrés. Les ajouter coûterait plusieurs Mo par ABI (limite de 15 Mo, `SPEC.md` §3). | M4 |
| 11 | URI non seekables | L'ouverture d'un `content://` suppose un descripteur seekable ; un fournisseur qui renvoie un tube (pipe) donne « Document indisponible ». Repli possible : copie dans `cacheDir`, supprimée à la fermeture (`AGENTS.md` §9). | M3 |
| 13 | Reproductibilité F-Droid | Pas de drapeaux de compilation déterministe pour MuPDF (`-ffile-prefix-map`, `--build-id=none`) ni de job CI qui compile deux fois et compare les APK (`ROADMAP.md` §4). | Publication v1.0 |
| 14 | Suivi des versions de MuPDF | Dependabot ne sait suivre un sous-module que sur la pointe de sa branche, pas sur les tags publiés (PR #8 refusée par `verifyMupdfVersion`). Option : un job CI planifié qui compare le tag épinglé au dernier tag `1.x.y` d'Artifex et ouvre une issue. | Publication v1.0 |
| 16 | Empreinte des documents | `SPEC.md` §5 identifie un document par une empreinte (premiers et derniers Ko + taille) pour retrouver sa position si l'URI change ; l'ADR 0015 rattache la position et le `documentId` à l'URI. Un même fichier ouvert par deux URI donne deux récents. À trancher : calcul de l'empreinte à l'ouverture, fusion de deux entrées (laquelle garde sa permission), effet sur le `documentId` des assistants. | M6 (fichiers créés par les outils) ou M7 |
| 17 | Fuite signalée par LeakCanary après la refonte | Vue une fois par le mainteneur le 2026-11-10 sur un Xiaomi (Android 15), build de debug, **non reproduite**. Objet retenu : un `PopupLayout` de Compose détaché (`View#onDetachedFromWindow` reçu), 73 Ko, retenu depuis 2,7 s au moment du rapport. Chaîne : `AndroidComposeView.layoutNodes` → `LayoutNode` → `lookaheadPassDelegate._childDelegates` (trois niveaux) → `LayoutNode` dont la `measurePolicy` est celle du `Popup` → `PopupLayout`. **Analyse (probable, non prouvée)** : le seul popup de l'application est le menu « ⋮ » des récents (`DropdownMenu`, `RecentDocuments.kt`) ; la passe *lookahead* n'existe que parce que `SharedTransitionLayout` (ADR 0019) enveloppe toute la navigation ; la liste mise en cache des enfants d'un nœud lookahead garderait le nœud du menu fermé jusqu'à la prochaine mesure de son parent. Ce serait donc une rétention passagère interne à Compose (BOM 2026.09.00), pas une référence tenue par le code de Lutrin, et sans effet visible. Pistes : reproduire (ouvrir et fermer le menu « ⋮ », attendre 10 s, voir si LeakCanary signale et si l'objet finit libéré) ; essayer une version plus récente de Compose ; si le mainteneur renonce au repli animé du retour, retirer `SharedTransitionLayout` supprime aussi la passe lookahead. Capture du rapport fournie par le mainteneur dans la conversation, non versionnée. | Avant la publication v1.0 |
| 6 | Licence affichée dans E4 | La maquette E4 indique « GNU GPL v3 » ; SPEC §7 demande de mentionner la distribution sous AGPL v3. | M5 |

## Tranchés

| # | Sujet | Décision |
|---|-------|----------|
| 4 | Injection de dépendances | Hilt — [ADR 0002](adr/0002-injection-dependances-hilt.md) |
| 5 | Licences tierces | Licensee (CI) et AboutLibraries (écran, M5) — [ADR 0003](adr/0003-licences-tierces.md) |
| 9 | Réglage « Couleurs dynamiques » | Interrupteur dans E3, activé par défaut — [ADR 0010](adr/0010-couleurs-dynamiques-et-police.md) |
| 10 | Police | Police du système conservée pour les textes courants, pas de Roboto embarquée — [ADR 0010](adr/0010-couleurs-dynamiques-et-police.md) ; **les titres passent en Fraunces 600 embarquée** — [ADR 0018](adr/0018-nouveau-design-visuel.md) |
| 15 | Échec natif à la fermeture d'un document | Intercepté (décision du 2026-11-04) : chaque étape de libération s'exécute même si la précédente échoue ; l'échec est signalé à `ReleaseFailureReporter`, qui écrit seulement le type d'exception dans le journal (`AGENTS.md` §1.8), au lieu de faire planter l'application |
| 12 | Benchmark et Baseline Profile | Module `:benchmark` (Macrobenchmark 1.5.0) et plugin `androidx.baselineprofile` ; ouverture du gros PDF de test par une activité présente dans les seuls builds de benchmark ; pas de benchmark en CI ; profil généré sur appareil par le mainteneur — [ADR 0012](adr/0012-benchmark-et-baseline-profile.md) |
| 7 | Schéma de couleurs sombre | Schéma M3 complet généré depuis `#2A5B8C`, dans `design-tokens.json` et `core:ui` |
