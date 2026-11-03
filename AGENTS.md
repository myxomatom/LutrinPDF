# Règles pour l'agent de codage

Ce fichier définit comment travailler sur ce projet. **Il prime sur tes habitudes par défaut.** Lis aussi `SPEC.md` et `ROADMAP.md` avant toute tâche, ainsi que les ADR de `docs/adr/` et les points ouverts de `docs/open-questions.md`.

---

## 1. Règles absolues (ne jamais enfreindre)

1. **Jamais** de publicité, de SDK d'analyse, de télémétrie, de rapport de crash distant.
2. **Jamais** de permission `INTERNET` ou `ACCESS_NETWORK_STATE`, ni de dépendance qui l'ajoute au manifeste fusionné.
3. **Jamais** de dépendance propriétaire (Google Play Services, Firebase, SDK fermés).
4. **Jamais** de dépendance dont la licence est incompatible avec la GPL v3 et l'AGPL v3. Vérifie la licence avant tout ajout et note-la dans `docs/dependencies.md`. Chaque fichier source commence par `SPDX-License-Identifier: GPL-3.0-or-later`.
5. **Jamais** de code livré sans tests ni documentation.
6. **Jamais** désactiver, supprimer ou affaiblir un test pour faire passer la CI. Corrige le code ou explique pourquoi le test est faux.
7. **Jamais** de secret, clé ou mot de passe dans le dépôt.
8. Ne jamais journaliser de contenu de document, de mot de passe ou de chemin de fichier en build release.

---

## 2. Méthode de travail

Pour chaque tâche :

1. **Comprendre** : relis la section concernée de `SPEC.md`. Si une exigence est ambiguë, pose la question au lieu de deviner. Si tu dois trancher, documente ta décision dans un ADR.
2. **Planifier** : liste les fichiers à créer / modifier et les tests à écrire.
3. **Tester d'abord** (TDD recommandé) : écris les tests qui décrivent le comportement attendu, vérifie qu'ils échouent.
4. **Implémenter** le minimum pour faire passer les tests, puis refactoriser.
5. **Vérifier** : lance `./gradlew check` (lint, detekt, ktlint, tests unitaires) et les tests instrumentés concernés.
6. **Documenter** : KDoc, README, CHANGELOG, ADR si nécessaire.
7. **Commit** : petits commits atomiques au format Conventional Commits.

Travaille par petites étapes livrables. Une fonctionnalité = une branche = une PR.

---

## 3. Architecture

- Respecte la structure de modules décrite dans `SPEC.md` §4.3.
- **Clean Architecture** :
  - `core:domain` : Kotlin pur, **aucune** dépendance Android. Modèles, interfaces de repository, cas d'usage (`UseCase` avec une seule méthode publique `operator fun invoke`).
  - `core:data` : implémentations des repositories, Room, DataStore.
  - `feature:*` : écrans Compose + ViewModels. Un module feature ne dépend jamais d'un autre module feature.
- **MVVM + UDF** : chaque écran expose un `StateFlow<UiState>` immuable et reçoit des `UiEvent` / actions. Les effets ponctuels passent par un `Channel` ou `SharedFlow`.
- Le moteur PDF (MuPDF) est **toujours** accédé via les interfaces `PdfEngine` et `PdfWriter`. Aucun type ni appel MuPDF en dehors de `core:pdf`.
- Les objets MuPDF ne sont pas thread-safe : chaque document est manipulé depuis un dispatcher mono-thread dédié, et ses ressources natives sont libérées explicitement, y compris en cas d'annulation.
- Les composables sont **sans état** quand c'est possible (state hoisting) ; les ViewModels ne référencent jamais de `Context`, de `View` ou de composable.
- Les opérations lourdes (rendu, recherche, I/O) s'exécutent hors du thread principal via des `CoroutineDispatcher` **injectés** (jamais `Dispatchers.IO` codé en dur), pour être testables.

---

## 4. Style de code

- Kotlin idiomatique, conforme au [guide de style Kotlin pour Android](https://developer.android.com/kotlin/style-guide).
- Formatage : **ktlint** ; analyse statique : **detekt** ; **Android Lint** sans avertissement ignoré sans justification.
- Zéro avertissement de compilation (`allWarningsAsErrors = true`).
- Principes : SOLID, DRY sans sur-abstraction, KISS, YAGNI.
- Fonctions courtes (≈ < 30 lignes), un seul niveau d'abstraction par fonction.
- Noms explicites en anglais pour le code ; pas d'abréviations obscures.
- Immutabilité par défaut (`val`, collections immuables, `data class`).
- Pas de `!!`. Gère la nullabilité explicitement.
- Erreurs : utilise `Result` ou une hiérarchie `sealed` d'erreurs métier ; pas d'exception silencieusement avalée.
- Pas de nombres ou chaînes « magiques » : constantes nommées, ressources `strings.xml`, `dimens`.
- Toutes les chaînes visibles par l'utilisateur sont traduisibles (FR + EN).
- Gestion des ressources natives : tout objet `Closeable` (document, page, bitmap) est libéré (`use {}`), y compris en cas d'annulation de coroutine.
- Dépendances déclarées uniquement dans `gradle/libs.versions.toml`.

---

## 5. Tests — obligatoires pour tout code

### 5.1 Types de tests

| Type | Outils | Où | Quoi |
|------|--------|-----|------|
| Unitaires | JUnit, MockK (ou fakes), Truth / AssertK, `kotlinx-coroutines-test`, Turbine | `src/test` | Cas d'usage, ViewModels, repositories, utilitaires, mapping |
| Robolectric | Robolectric | `src/test` | Code dépendant d'Android sans émulateur |
| Base de données | Room in-memory | `src/test` ou `src/androidTest` | DAO, migrations |
| UI Compose | `compose-ui-test` | `src/androidTest` | Chaque écran : états chargement / vide / erreur / contenu, interactions |
| Captures d'écran | Roborazzi ou Paparazzi | `src/test` | Composants et écrans, thèmes clair / sombre, tailles de police |
| Intégration moteur PDF | Tests instrumentés | `core:pdf/src/androidTest` | Ouverture de chaque PDF du jeu de test (`SPEC.md` §9) |
| Bout en bout | Espresso / Compose test + UI Automator | `app/src/androidTest` | Parcours : ouvrir un fichier, lire, rechercher, reprendre la lecture |
| Performance | Macrobenchmark, Baseline Profiles | `:benchmark` | Démarrage, ouverture de document, défilement |
| Accessibilité | `AccessibilityChecks` (Espresso) | `src/androidTest` | Écrans principaux |

### 5.2 Règles

- **Chaque fonctionnalité et chaque correction de bug** vient avec ses tests. Un bug corrigé = un test de non-régression.
- Teste les cas nominaux, **les cas limites** (document vide, 0 résultat, page hors limites, très gros fichier) **et les cas d'erreur** (PDF corrompu, mot de passe faux, permission révoquée).
- Nommage : `fonction_condition_resultatAttendu` ou phrases avec backticks : `` `search returns empty list when query is blank` ``.
- Structure **Arrange / Act / Assert** (ou Given / When / Then).
- Tests déterministes : pas de `Thread.sleep`, pas de dépendance à l'heure réelle (injecte une `Clock`), dispatchers de test.
- Préfère les **fakes** aux mocks pour les repositories ; place-les dans `core:testing`.
- Couverture mesurée avec **Kover** (ou JaCoCo) : ≥ 80 % sur `core:domain` et `core:data`, ≥ 70 % globale. La CI échoue en dessous.
- Tous les tests doivent passer avant chaque commit sur la branche principale.

---

## 6. Documentation — tout est documenté

- **KDoc** sur chaque classe, interface et fonction publique ou `internal` : rôle, paramètres, valeur de retour, exceptions / erreurs possibles, exemple si utile.
- Commentaires dans le code pour expliquer le **pourquoi**, pas le quoi.
- `README.md` : présentation, captures, fonctionnalités, compilation, lancement des tests, architecture résumée, licence.
- `docs/architecture.md` : modules, flux de données, diagramme (Mermaid).
- `docs/adr/NNNN-titre.md` : **un ADR pour chaque décision structurante** (choix du moteur PDF, DI, licence, stockage…), format : contexte, options, décision, conséquences.
- `docs/dependencies.md` : chaque dépendance avec version, licence, justification.
- `CHANGELOG.md` au format [Keep a Changelog](https://keepachangelog.com/fr/), mis à jour à chaque fonctionnalité.
- `CONTRIBUTING.md` : comment contribuer, conventions, lancer les vérifications.
- `PRIVACY.md` : déclaration explicite qu'aucune donnée n'est collectée.
- Documentation générée avec **Dokka**, sans avertissement.

---

## 7. Git et intégration continue

- **Conventional Commits** : `feat:`, `fix:`, `test:`, `docs:`, `refactor:`, `perf:`, `build:`, `ci:`, `chore:`.
- Messages de commit en anglais, à l'impératif, expliquant le pourquoi si non évident.
- Branches : `feat/…`, `fix/…`, `docs/…`.
- La CI exécute à chaque push : ktlint, detekt, Android Lint, tests unitaires, tests de captures, couverture, build release, **vérification de l'absence de permission réseau** dans le manifeste fusionné, vérification des licences des dépendances.
- Tests instrumentés exécutés sur émulateur (Gradle Managed Devices) au minimum sur l'API `minSdk` et la dernière API.

---

## 8. Performance et mémoire

- Rendu des pages en tuiles, à la résolution visible uniquement ; cache LRU de bitmaps borné en fonction de la mémoire disponible.
- Annulation des rendus des pages sorties de l'écran.
- Aucun travail lourd dans une fonction `@Composable` ; utilise `remember`, clés stables, `LazyColumn` avec `key`.
- Surveiller les fuites avec LeakCanary (build debug uniquement).
- Générer et embarquer un Baseline Profile.

---

## 9. Sécurité et vie privée

- Ouverture de fichiers **uniquement via SAF / intents** ; pas de `MANAGE_EXTERNAL_STORAGE`.
- Mots de passe de PDF en mémoire uniquement, jamais persistés ni journalisés.
- JavaScript PDF jamais exécuté.
- Fichiers temporaires dans `cacheDir`, supprimés à la fermeture du document.
- Validation des URI et des types MIME reçus via intent.
- `android:allowBackup` configuré pour exclure les données sensibles ; activités exportées limitées au strict nécessaire.

---

## 10. Outils PDF et intégration avec les assistants IA

### Outils (fusion, extraction, conversion)
- Toute écriture passe par `PdfWriter` ; aucune écriture directe ailleurs.
- **Ne jamais écraser** un fichier : génère un nom unique, écris d'abord dans un fichier temporaire, puis déplace vers la destination une fois l'opération réussie. En cas d'échec ou d'annulation, supprime le fichier partiel.
- Traite les fichiers en flux / page par page pour ne jamais charger tout un document ou toutes les images en mémoire ; sous-échantillonne les images trop grandes.
- Opérations longues dans WorkManager, annulables, avec progression.
- Le HTML local est rendu **sans aucun accès réseau** (ressources distantes bloquées).

### AppFunctions (`:feature:assistant`)
- Une AppFunction = une fine couche qui valide les paramètres, vérifie les autorisations utilisateur, appelle **un** cas d'usage du domaine et mappe le résultat. Aucune logique métier ici.
- Rédige des descriptions de fonctions et de paramètres précises, en anglais, avec unités et valeurs par défaut : c'est ce que l'assistant lit pour choisir l'outil.
- Retourne toujours des types structurés et des codes d'erreur stables (voir `SPEC.md` §2.4) ; jamais d'exception non gérée vers l'appelant.
- Vérifie **à chaque appel** les réglages « Autoriser les assistants » et « Autoriser la lecture du contenu ».
- N'accède qu'aux documents désignés par `documentId` (récents) ou par un URI accordé par l'appelant.
- Ne journalise jamais le contenu des documents dans l'historique des actions.
- Garde la parité : chaque fonction exposée a un équivalent dans l'interface utilisateur.
- Tout changement de signature d'une fonction exposée est documenté dans le CHANGELOG (c'est une API publique).

### Tests spécifiques
- **Outils** : fusion de 2 et de N fichiers, ordre respecté, plages de pages, PDF chiffré en entrée, fichier corrompu en entrée, annulation en cours de route (pas de fichier partiel), espace disque insuffisant, limites dépassées, nom en collision. Vérifie le nombre et le contenu des pages du résultat.
- **Conversion** : chaque format d'entrée, orientation EXIF, très grande image, caractères non latins, HTML avec ressource distante bloquée.
- **AppFunctions** : pour chaque fonction, test nominal, chaque code d'erreur, paramètres invalides, réglages désactivés (doit renvoyer `CONTENT_ACCESS_DISABLED` ou équivalent), document introuvable. Tests unitaires avec des fakes des cas d'usage, plus au moins un test instrumenté par fonction sur API 36+.
- **Repli** : tests des intents et raccourcis sur `minSdk`.

---

## 11. Accessibilité et UX

- `contentDescription` sur chaque élément interactif non textuel.
- Cibles tactiles ≥ 48 dp, contraste WCAG AA, support des grandes polices et du mode sombre.
- Chaque écran gère explicitement les états : chargement, vide, erreur, contenu.
- Suivre Material 3 et les recommandations grands écrans.
- Avant d'implémenter ou de modifier un écran, consulte sa maquette dans
  `docs/design/png/` (capture) et `docs/design/screens/` (structure, textes).
  Les maquettes sont une référence visuelle : SPEC.md fait foi, et tout écart
  volontaire est documenté dans un ADR.
- Les couleurs, tailles de texte et formes du thème `core:ui` reprennent
  `docs/design/design-tokens.json`.
- Les fichiers de `docs/design/` ne sont **jamais** embarqués dans l'APK ni copiés
  dans `src/main/assets`.

---

## 12. Définition de « terminé »

Une tâche est terminée seulement si **tout** est vrai :

- [ ] Le comportement correspond à `SPEC.md` (ou l'écart est documenté dans un ADR).
- [ ] Tests unitaires et, si applicable, tests UI / instrumentés écrits et verts.
- [ ] Cas limites et cas d'erreur testés.
- [ ] `./gradlew check` passe sans avertissement.
- [ ] Couverture maintenue au-dessus des seuils.
- [ ] KDoc, README, CHANGELOG (et ADR si besoin) à jour.
- [ ] Chaînes traduites en FR et EN.
- [ ] Aucune nouvelle permission, aucune dépendance non vérifiée.
- [ ] Commit(s) au format Conventional Commits.

---

## 13. Communication

- À la fin de chaque tâche, fournis un résumé : ce qui a été fait, fichiers modifiés, tests ajoutés, décisions prises, points ouverts.
- Si tu bloques ou si une règle de ce fichier entre en conflit avec une demande, **signale-le** au lieu de la contourner.
