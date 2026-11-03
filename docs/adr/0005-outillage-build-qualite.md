# 0005 — Outillage de build, de qualité et de test

- **Statut** : accepté
- **Date** : 2026-11-03
- **Références** : `AGENTS.md` §4 à §7, `ROADMAP.md` M0

## Contexte

`AGENTS.md` impose ktlint, detekt, Android Lint, Kover, Dokka, des tests de captures et zéro
avertissement. Plusieurs choix de versions et d'outils restaient à faire.

## Décisions

| Sujet | Choix | Raison |
|-------|-------|--------|
| Build | Gradle 9.8 (wrapper avec somme SHA-256), AGP 9.5 et son **Kotlin intégré**, Kotlin 2.4, KSP 2.3 | Dernières versions stables au 2026-11-03 |
| Conventions | Plugins de convention dans `build-logic/` (`lutrin.android.application`, `…library`, `…compose`, `…feature`, `lutrin.jvm.library`, `lutrin.hilt`) | Une seule source de configuration ; chaque module tient en quelques lignes |
| SDK | `minSdk` 26, `compileSdk` / `targetSdk` 37 | `SPEC.md` §3 ; 37 = dernière API stable |
| Zéro avertissement | `allWarningsAsErrors` (Kotlin), `warningsAsErrors` (Lint) | `AGENTS.md` §4 |
| Lint | Règles de fraîcheur des versions et `ObsoleteLintCustomCheck` désactivées, justifiées dans `config/lint/lint.xml` | Sinon chaque publication amont casserait la CI ; Dependabot propose les mises à jour |
| detekt | **1.23.8** (stable) + `compose-rules` 0.4.27, KDoc obligatoire sur l'API publique | detekt 2.0 est encore en alpha ; `compose-rules` 0.5+ exige detekt 2.0. Classpath de detekt forcé sur Kotlin 2.0.21 (son compilateur embarqué) |
| ktlint | Plugin `org.jlleitschuh.gradle.ktlint`, ktlint 1.8, style `android_studio` (`.editorconfig`) | Formatage automatique (`ktlintFormat`) |
| Couverture | Kover : ≥ 70 % global, ≥ 80 % `core:domain` et `core:data` ; classes générées exclues ; `core:testing` hors agrégat | `AGENTS.md` §5.2 |
| Documentation | Dokka 2, `failOnWarning`, KDoc des modules dans `Module.md`, **sans lien vers des docs externes** | Build sans accès réseau, reproductible |
| Captures d'écran | **Roborazzi** (sur Robolectric), références dans `src/test/screenshots/`, vérifiées à chaque `test` (`roborazzi.test.verify=true`) | Réutilise Robolectric ; Paparazzi dépend de layoutlib, plus fragile avec les nouvelles versions d'AGP |
| Robolectric | 4.17, SDK 36 par défaut (`robolectric.properties`), `--add-exports java.base/jdk.internal.access` | Robolectric ne prend pas encore en charge l'API 37 ; l'export est requis sur JDK 21 |
| Tests instrumentés | Gradle Managed Devices : API 26 (`aosp`) et API 37 (`aosp-atd`), groupe `ci` | `AGENTS.md` §7 |
| Accessibilité | `ui-test-junit4-accessibility` (Accessibility Test Framework) dans les tests instrumentés | `AGENTS.md` §5.1 |
| Permissions | Tâche maison `verifyReleasePermissions` (dans `check`) sur le manifeste fusionné | `SPEC.md` §1, testée dans `build-logic` |
| Fichiers de test | Générés par un script Python (PyMuPDF, Pillow) versionné dans `core/testing/scripts/` | Libres de droits et régénérables ; outil de développement, jamais embarqué |
| Fuites mémoire | LeakCanary 2.14, `debugImplementation` uniquement | `AGENTS.md` §8 |

## Conséquences

- **Licences des outils de test** : JUnit 4 est sous **EPL-1.0**, incompatible avec la GPL
  pour une œuvre combinée distribuée. Il n'est utilisé qu'à l'exécution des tests et n'est
  jamais distribué ni lié à l'APK ; nous considérons donc la règle `AGENTS.md` §1.4 comme
  respectée (elle vise le code distribué, contrôlé par Licensee). *Ceci n'est pas un avis
  juridique.* Robolectric impose JUnit 4.
- Le jalon M1 ajoutera la compilation native (NDK) de MuPDF et les splits par ABI.
- Baseline Profile et Macrobenchmark (`:benchmark`) arrivent avec les premiers écrans réels
  (M2 pour les mesures, M5 pour le profil embarqué, `ROADMAP.md`).
