# 0002 — Injection de dépendances : Hilt

- **Statut** : accepté
- **Date** : 2026-11-03
- **Décideurs** : responsable du projet (choix de Hilt), agent de codage (rédaction)
- **Références** : `SPEC.md` §4.1, `AGENTS.md` §3, `docs/open-questions.md` (point 4)

## Contexte

`SPEC.md` §4.1 prévoit Hilt, « ou Koin si Hilt pose problème pour F-Droid ». Il faut
trancher avant de créer les modules (jalon M0) : le choix structure tout le code.

## Options envisagées

### Option A — Hilt (Dagger)
- Avantages : standard Android recommandé par Google ; graphe vérifié **à la compilation**
  (une dépendance manquante casse le build, pas l'application) ; intégration native avec
  ViewModel, WorkManager et Navigation Compose ; licence Apache 2.0 ; bibliothèque Java/Kotlin
  pure, sans service Google Play, donc compatible F-Droid.
- Inconvénients : génération de code (KSP) qui allonge la compilation ; erreurs parfois
  verbeuses ; classes générées à exclure de la couverture.

### Option B — Koin
- Avantages : DSL Kotlin simple, pas de génération de code, compilation plus rapide.
- Inconvénients : résolution **à l'exécution** (une erreur de câblage n'apparaît qu'au lancement
  ou dans un test dédié) ; moins d'intégration officielle AndroidX.

## Décision

**Hilt**, avec son processeur d'annotations exécuté par **KSP**. Le câblage est centralisé
dans `:app` ; les modules `feature:*` l'appliquent via le plugin de convention `lutrin.hilt`.

## Conséquences

- Positives : erreurs de câblage détectées par le build ; ViewModels injectés sans code manuel.
- Négatives : temps de compilation ; les classes générées (`*_Factory`, `Hilt_*`, …) sont
  exclues de Kover (`build-logic/.../Quality.kt`).
- Les modules `feature:*` encore vides désactivent `failOnNoDiscoveredTests` : Hilt y génère
  des classes de test même sans test. À retirer quand chaque module aura ses tests.
- `core:domain` reste en Kotlin pur : il peut utiliser `javax.inject` (JVM pur) si besoin,
  jamais Hilt ni Android.
