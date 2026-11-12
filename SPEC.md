# Spécification — Lecteur PDF Android libre et sans publicité

> Nom du projet : **Lutrin** — dépôt : <https://github.com/myxomatom/LutrinPDF>
> Version du document : 1.0
> Public : agent de codage et contributeurs

---

## 1. Vision et principes

Un lecteur PDF pour Android **rapide, léger, respectueux de la vie privée, sans publicité ni traqueur**, publié sous licence libre.

Principes non négociables :

1. **Aucune publicité, aucun traqueur, aucune télémétrie.** Aucun SDK d'analyse (Firebase, Crashlytics, etc.).
2. **Aucune permission réseau** (`android.permission.INTERNET` absente du manifeste). L'application fonctionne 100 % hors ligne. C'est une garantie vérifiable par l'utilisateur.
3. **Aucune dépendance propriétaire** (pas de Google Play Services). L'application doit être publiable sur **F-Droid** (build reproductible).
4. **Open source** : code public, licence compatible avec toutes les dépendances (voir §7).
5. **Accès minimal aux fichiers** : utilisation du Storage Access Framework (SAF), pas de permission `MANAGE_EXTERNAL_STORAGE`.

---

## 2. Périmètre

### 2.1 MVP (v1.0) — obligatoire

| ID | Fonctionnalité | Détails |
|----|----------------|---------|
| F01 | Ouvrir un PDF | Via le sélecteur système (SAF), via « Ouvrir avec » (intent `ACTION_VIEW`, MIME `application/pdf`), via partage (`ACTION_SEND`). |
| F02 | Affichage des pages | Rendu net à tous les niveaux de zoom (rendu en tuiles à haute résolution lors du zoom). |
| F03 | Navigation | Défilement vertical continu ; mode page par page horizontal (option). |
| F04 | Zoom | Pincer pour zoomer, double tap (zoom / dézoom), ajustement largeur / page entière. Zoom 25 % à 800 %. |
| F05 | Aller à la page | Saisie d'un numéro de page, barre de défilement rapide avec indicateur de page. |
| F06 | Table des matières | Affichage de l'arborescence (outline) du PDF si présente, navigation au clic. |
| F07 | Recherche de texte | Recherche dans tout le document, surlignage des résultats, navigation suivant / précédent, nombre d'occurrences. Insensible à la casse et aux accents (option). |
| F08 | Sélection et copie | Sélection de texte par appui long, copie dans le presse-papier. |
| F09 | Liens | Liens internes (navigation dans le document). Liens externes : ouverture via intent système après confirmation (l'app elle-même n'accède pas au réseau). |
| F10 | PDF protégés | Saisie du mot de passe pour les PDF chiffrés. Le mot de passe n'est jamais stocké. |
| F11 | Fichiers récents | Liste des documents récents (URI persistés via `takePersistableUriPermission`), avec vignette, possibilité de retirer un élément ou de vider la liste. |
| F12 | Reprise de lecture | Mémorisation de la dernière page et du zoom par document. |
| F13 | Thèmes | Clair, sombre, suivre le système. Couleurs dynamiques (Material You, Android 12+) désactivables, activées par défaut (ADR 0010). **Mode nuit du document** (inversion des couleurs du rendu) et mode sépia. |
| F14 | Plein écran | Mode immersif (barres masquées), réaffichage au tap. |
| F15 | Partager / imprimer | Partage du fichier via intent ; impression via `PrintManager`. |
| F16 | Propriétés | Titre, auteur, nombre de pages, taille, version PDF, dates. |
| F17 | Orientation | Rotation automatique, verrouillage d'orientation (option). |
| F18 | Accessibilité | Compatible TalkBack (contenus décrits), tailles de cible ≥ 48 dp, contraste AA. |
| F19 | Internationalisation | Français et anglais au minimum ; toutes les chaînes dans `strings.xml`. |

### 2.2 v1.x — souhaitable

| ID | Fonctionnalité |
|----|----------------|
| F20 | Signets utilisateur (ajout, liste, suppression), stockés localement. |
| F21 | Annotations : surlignage, soulignement, note, dessin libre — enregistrées **dans le PDF** (copie ou écrasement après confirmation). |
| F22 | Remplissage de formulaires (AcroForms) et enregistrement. |
| F23 | Mode lecture « reflow » du texte (si le moteur le permet). |
| F24 | Vue en grille des miniatures de pages. |
| F25 | Lecture à voix haute (TextToSpeech système). |
| F26 | Support tablette / grand écran : mode deux pages, panneaux latéraux. |

### 2.3 Outils PDF (v1.1) — fusion et conversion

Ces outils sont utilisables depuis l'interface **et** depuis les assistants IA (§2.4). Tout fonctionne hors ligne, sur l'appareil.

| ID | Fonctionnalité | Détails |
|----|----------------|---------|
| T01 | Fusionner des PDF | Sélection de 2 à N fichiers, réordonnancement par glisser-déposer, choix optionnel de plages de pages par fichier (ex. « 1-3, 7 »). Conservation de la table des matières si possible. |
| T02 | Extraire des pages | Créer un nouveau PDF à partir d'une sélection de pages d'un document. |
| T03 | Images → PDF | JPEG, PNG, WebP, HEIC (si supporté par l'appareil) ; une image par page ; options : format de page (A4, Lettre, taille de l'image), orientation, marges, qualité / compression. Respect de l'orientation EXIF. |
| T04 | Texte → PDF | Fichiers `.txt` et `.md` : mise en page avec police, taille et marges configurables, gestion des caractères non latins. |
| T05 | HTML local → PDF | Fichiers `.html` locaux rendus sans accès réseau (ressources distantes bloquées). |
| T06 | Suivi et annulation | Barre de progression, annulation possible, notification à la fin pour les opérations longues. |

Règles communes :
- **Jamais d'écrasement** d'un fichier existant : le résultat est toujours un nouveau fichier.
- Enregistrement dans un **dossier de sortie** choisi par l'utilisateur (`ACTION_OPEN_DOCUMENT_TREE`, permission persistée), configurable dans les paramètres ; à défaut, demande via `ACTION_CREATE_DOCUMENT`.
- Nom de fichier par défaut explicite (ex. `fusion_2026-11-03_2054.pdf`), sans collision.
- Le fichier créé est ajouté aux récents et peut être ouvert immédiatement.
- Limites documentées et vérifiées (ex. 200 fichiers ou 2 Go au total par opération) avec message clair si dépassées.
- Implémentation : fusion, extraction et conversion via MuPDF (greffe de pages, création de documents, écriture), exposées par l'interface `PdfWriter`.

### 2.4 Intégration avec les assistants IA (v1.1)

L'application expose ses capacités aux assistants (dont **Gemini**) via **AppFunctions** (Android 16+, bibliothèque Jetpack `androidx.appfunctions`), avec une solution de repli par **intents et raccourcis d'application** pour les versions antérieures et les autres assistants.

> AppFunctions est encore expérimental : l'intégration doit être isolée dans le module `:feature:assistant`, pouvoir être désactivée, et l'agent doit vérifier dans un ADR que la bibliothèque n'ajoute ni dépendance propriétaire ni permission réseau.

#### Fonctions exposées

| Fonction | Paramètres | Retour | Exemple de demande |
|----------|-----------|--------|--------------------|
| `listRecentDocuments` | `limit` (défaut 10) | liste `{documentId, nom, pages, date}` | « Quels PDF ai-je ouverts récemment ? » |
| `openDocument` | `documentId` ou `uri`, `page` optionnelle | statut | « Ouvre le contrat de location » |
| `goToPage` | `page` | statut | « Va à la page 12 » |
| `getDocumentInfo` | `documentId` | métadonnées, nombre de pages, table des matières | « Combien de pages fait ce document ? » |
| `readPages` | `documentId`, `fromPage`, `toPage` | texte extrait, page par page | « Lis-moi la page 3 », « Résume le chapitre 2 » |
| `searchInDocument` | `documentId`, `query`, `limit` | liste `{page, extrait}` | « Où est mentionnée la date de résiliation ? » |
| `readAloud` | `documentId`, `fromPage` | statut (lecture via TextToSpeech système) | « Lis ce document à voix haute » |
| `mergePdfs` | liste `{documentId ou uri, plages}` ordonnée, `outputName` | `{documentId, uri, pages}` | « Fusionne la facture et le devis en un seul PDF » |
| `extractPages` | `documentId`, `pages`, `outputName` | `{documentId, uri, pages}` | « Crée un PDF avec les pages 2 à 5 » |
| `convertToPdf` | liste d'`uri` (images, txt, md, html), options de page, `outputName` | `{documentId, uri, pages}` | « Transforme ces photos en PDF » |

Règles :
- Chaque fonction est une **fine couche** qui appelle les cas d'usage de `core:domain` : aucune logique métier dupliquée.
- Descriptions des fonctions et paramètres **claires et précises** (c'est ce que lit l'assistant pour choisir l'outil).
- Erreurs **structurées** (`DOCUMENT_NOT_FOUND`, `PERMISSION_DENIED`, `PASSWORD_REQUIRED`, `UNSUPPORTED_FORMAT`, `LIMIT_EXCEEDED`, `CONTENT_ACCESS_DISABLED`…) avec un message lisible.
- Les fichiers sont désignés par `documentId` (issu des récents) ou par un `content://` URI accordé par l'appelant ; aucune lecture hors de ces accès.
- Le mot de passe d'un PDF chiffré n'est **jamais** demandé ni transmis via l'assistant : l'app renvoie `PASSWORD_REQUIRED` et l'utilisateur l'ouvre manuellement.

#### Vie privée et contrôle utilisateur

Dans les paramètres, section « Assistants IA » :
- Interrupteur général « Autoriser les assistants » (activé par défaut pour la navigation et les outils).
- Interrupteur « Autoriser la lecture du contenu des documents » (`readPages`, `searchInDocument`) **désactivé par défaut**, avec un avertissement : l'assistant peut transmettre le texte à un service en ligne.
- Historique local des dernières actions effectuées par un assistant (fonction, date, appelant), consultable et effaçable.

#### Repli sans AppFunctions

- Intents documentés : `ACTION_VIEW` avec extra `page`, actions personnalisées pour la recherche et les outils (ouvrant l'écran correspondant prérempli, avec validation par l'utilisateur).
- Raccourcis d'application (App Shortcuts) : « Ouvrir un PDF », « Fusionner des PDF », « Convertir en PDF », « Dernier document ».

### 2.5 Hors périmètre

- Édition du contenu (texte, images) des PDF.
- Conversion des formats bureautiques (Word, Excel, PowerPoint, ODT) : nécessiterait un moteur très lourd (type LibreOffice) — à réévaluer plus tard.
- Export de PDF vers d'autres formats.
- Synchronisation cloud, comptes utilisateur.
- Formats autres que PDF : hors v1, mais MuPDF permettra d'ajouter EPUB, XPS, CBZ et FB2 plus tard sans changer de moteur.

---

## 3. Exigences non fonctionnelles

| Domaine | Exigence |
|---------|----------|
| Performance | Première page affichée en < 1 s pour un PDF de 10 Mo sur un appareil milieu de gamme. Défilement à 60 fps sans saccade. |
| Mémoire | Pas d'`OutOfMemoryError` sur un PDF de 1 000 pages / 200 Mo. Cache de bitmaps borné (LRU), recyclage des bitmaps hors écran. |
| Robustesse | Un PDF corrompu ou malformé ne fait jamais planter l'app : message d'erreur clair. Le moteur natif est isolé derrière une interface. |
| Taille | APK < 15 Mo par ABI (splits par ABI : arm64-v8a, armeabi-v7a, x86_64). |
| Compatibilité | `minSdk` 26 (Android 8.0), `targetSdk` = dernière version stable. |
| Vie privée | Aucune donnée ne quitte l'appareil. Pas de permission réseau. Pas de journalisation de chemins de fichiers en release. |
| Sécurité | Fichiers temporaires dans le cache privé, nettoyés à la fermeture. Pas de rendu JavaScript PDF. |
| Batterie | Aucun travail en arrière-plan hors document ouvert. |

---

## 4. Architecture technique

### 4.1 Pile technologique

- **Langage** : Kotlin (100 %), coroutines + Flow.
- **UI** : Jetpack Compose, Material 3 (avec couleurs dynamiques Material You).
- **Architecture** : Clean Architecture + MVVM, flux de données unidirectionnel (UDF).
- **Injection de dépendances** : Hilt (ou Koin si Hilt pose problème pour F-Droid — à documenter dans un ADR).
- **Persistance** : Room (récents, signets, positions de lecture), DataStore (préférences).
- **Navigation** : Navigation Compose.
- **Build** : Gradle Kotlin DSL, catalogue de versions (`libs.versions.toml`).

### 4.2 Moteur de rendu PDF

**Décision : MuPDF** (Artifex, AGPL v3) — voir `docs/adr/0001-moteur-pdf-mupdf.md`.

Raisons :
- Couvre dans une seule bibliothèque la lecture **et** l'écriture : rendu, texte structuré, recherche, liens, table des matières, annotations, formulaires, fusion, extraction de pages, conversion d'images et de texte en PDF (outils T01–T05).
- Mode reflow et support d'autres formats (EPUB, XPS, CBZ, FB2) ouvrant une évolution future sans changer de moteur.
- Bibliothèque Android maintenue par l'éditeur (Artifex), plutôt qu'un binding communautaire.

Contraintes :
- **Licence** : l'application distribuée est soumise à l'AGPL v3 (voir §7).
- **F-Droid** : MuPDF doit être **compilé depuis les sources** (sous-module Git + NDK), avec une version épinglée, pour un build reproductible ; ne pas dépendre d'un binaire précompilé tiers.
- **Isolation** : MuPDF n'est accessible que via l'interface `PdfEngine` / `PdfWriter` dans le module `core:pdf`. Aucun type MuPDF ne doit apparaître hors de ce module, afin de pouvoir changer d'implémentation (PDFium reste l'alternative documentée dans l'ADR).
- **Fil d'exécution** : les objets MuPDF ne sont pas thread-safe ; chaque document est manipulé depuis un dispatcher dédié à un seul thread, et les ressources natives sont libérées explicitement (`destroy()` / `use {}`).

Interface indicative :

```kotlin
interface PdfEngine {
    suspend fun open(source: DocumentSource, password: String? = null): Result<PdfDocument>
}

interface PdfDocument : Closeable {
    val pageCount: Int
    val metadata: PdfMetadata
    suspend fun pageSize(index: Int): PageSize
    suspend fun renderPage(index: Int, target: Bitmap, region: RectF, scale: Float, nightMode: Boolean)
    val canCopyText: Boolean                       // permission de copie (ADR 0020)
    suspend fun outline(): List<OutlineItem>       // destinations avec position (ADR 0020)
    fun search(query: String, options: SearchOptions): Flow<SearchPageResult>  // un résultat par page
    suspend fun selectText(index: Int, start: PdfPoint, end: PdfPoint, mode: SelectionMode): TextSelection
    suspend fun links(index: Int): List<PdfLink>
}

/** Création et assemblage de PDF (outils T01–T05). */
interface PdfWriter {
    suspend fun merge(inputs: List<MergeInput>, output: DocumentTarget, progress: (Float) -> Unit): Result<CreatedDocument>
    suspend fun extractPages(source: DocumentSource, pages: PageSelection, output: DocumentTarget): Result<CreatedDocument>
    suspend fun fromImages(images: List<DocumentSource>, options: PageOptions, output: DocumentTarget, progress: (Float) -> Unit): Result<CreatedDocument>
    suspend fun fromText(source: DocumentSource, format: TextFormat, options: PageOptions, output: DocumentTarget): Result<CreatedDocument>
}
```

Les opérations longues des outils s'exécutent dans un **WorkManager** (ou un service au premier plan) pour survivre à la fermeture de l'écran, avec annulation.

### 4.3 Modules Gradle

```
:app                     -> point d'entrée, navigation, DI
:core:pdf                -> interface PdfEngine + implémentation
:core:data               -> repositories, Room, DataStore
:core:domain             -> modèles et cas d'usage (Kotlin pur, sans dépendance Android)
:core:ui                 -> thème, composants Compose partagés
:core:testing            -> fakes, utilitaires de test, PDF de test
:feature:library         -> écran d'accueil / fichiers récents
:feature:reader          -> visionneuse, recherche, table des matières
:feature:settings        -> préférences, à propos, licences
:feature:tools           -> écrans fusion, extraction, conversion
:feature:assistant       -> AppFunctions, intents et raccourcis pour assistants IA
```

### 4.4 Écrans

> Maquettes de référence : `docs/design/` (captures dans `docs/design/png/`). Référence visuelle seulement : cette spécification fait foi.

1. **Bibliothèque** : liste / grille des récents, bouton « Ouvrir un fichier », état vide explicatif.
2. **Lecteur** : document, barre supérieure (titre, recherche, menu), barre inférieure (page x / n, curseur), panneau table des matières.
3. **Recherche** : champ dans la barre supérieure, résultats surlignés, compteur, flèches suivant/précédent.
4. **Paramètres** : thème, mode nuit du document, mode de défilement, maintien de l'écran allumé, orientation, langue, vider les récents.
5. **À propos** : version, licence, lien vers le code source, liste des licences tierces.
6. **Outils** : accès à la fusion, à l'extraction et à la conversion ; écran de sélection et d'ordre des fichiers, options, progression, résultat (ouvrir / partager).

---

## 5. Données stockées (locales uniquement)

| Entité | Champs |
|--------|--------|
| `RecentDocument` | uri, nom affiché, taille, nombre de pages, date d'ouverture, chemin vignette |
| `ReadingPosition` | uri (ou empreinte du fichier), page, zoom, décalage |
| `Bookmark` (v1.x) | id, documentId, page, libellé, date |
| Préférences (DataStore) | thème, couleurs dynamiques, mode nuit, mode défilement, écran allumé, orientation, dossier de sortie, autorisations assistants |
| `AssistantAction` (v1.1) | id, fonction, paquet appelant, date, résultat (sans contenu de document) |

Identification d'un document : empreinte (hash) des premiers et derniers Ko + taille, pour retrouver la position même si l'URI change.

---

## 6. Gestion des erreurs

| Cas | Comportement attendu |
|-----|----------------------|
| Fichier introuvable / permission révoquée | Message + proposition de retirer l'élément des récents. |
| PDF corrompu | Message « Impossible d'ouvrir ce document », pas de crash. |
| Mot de passe incorrect | Message, nouvelle saisie possible. |
| Mémoire insuffisante | Réduction de la qualité de rendu, purge du cache, pas de crash. |
| Type de fichier non PDF | Message explicite. |

---

## 7. Licence et distribution

- Licence du code du projet : **GPL-3.0-or-later** (en-tête `SPDX-License-Identifier: GPL-3.0-or-later` dans chaque fichier source).
- L'application distribuée intègre MuPDF (AGPL v3) : l'ensemble est distribué selon les termes de l'**AGPL v3** (combinaison autorisée par la section 13 de la GPL v3). Le `README` et l'écran « À propos » le mentionnent, avec un lien vers le code source de chaque version publiée, y compris la version exacte de MuPDF utilisée.
- Toutes les autres dépendances doivent être compatibles avec la GPL v3 et l'AGPL v3 (Apache 2.0, MIT, BSD, LGPL : oui ; licences propriétaires ou non commerciales : non).
- Fichiers : `LICENSE`, `README.md`, `CONTRIBUTING.md`, `CHANGELOG.md`, `PRIVACY.md` (déclaration : aucune donnée collectée).
- Écran des licences tierces généré automatiquement (plugin de licences compatible F-Droid).
- Métadonnées F-Droid au format Fastlane (`fastlane/metadata/android/fr-FR`, `en-US`).
- CI (GitHub Actions ou Forgejo/GitLab CI) : lint, tests, build release, vérification que la permission `INTERNET` est absente du manifeste fusionné.

---

## 8. Critères d'acceptation du MVP

- [ ] Toutes les fonctionnalités F01 à F19 sont implémentées et testées.
- [ ] Le manifeste fusionné ne contient **aucune** permission réseau (vérifié en CI).
- [ ] Aucune dépendance propriétaire ni SDK de suivi (vérifié par revue des dépendances).
- [ ] Les tests passent en CI ; couverture ≥ 80 % sur `core:domain` et `core:data`, ≥ 70 % globale.
- [ ] Ouverture correcte de la suite de PDF de test (§9).
- [ ] Aucun crash sur les PDF corrompus de la suite de test.
- [ ] Documentation à jour (README, ADR, KDoc).
- [ ] Build release signé et reproductible.

---

## 9. Jeu de PDF de test

À placer dans `core/testing/src/main/assets/pdfs/` (uniquement des fichiers libres de droits ou générés) :

- PDF simple 1 page, texte seul
- PDF de 500+ pages
- PDF avec table des matières imbriquée
- PDF avec liens internes et externes
- PDF chiffré (mot de passe connu)
- PDF avec formulaire
- PDF scanné (images, sans texte)
- PDF avec pages de tailles et orientations différentes
- PDF avec texte accentué / non latin (arabe, chinois)
- PDF corrompu / tronqué
- Fichier non PDF renommé en `.pdf`
- Pour les outils : images JPEG/PNG/WebP de tailles variées, photo avec orientation EXIF, image très grande (> 50 Mpx), fichiers `.txt` et `.md` UTF-8 avec accents et emoji, fichier HTML local référençant une ressource distante (qui doit être bloquée)

---

## 10. Feuille de route suggérée

> Le plan détaillé (inventaire des vues, jalons, risques) est dans `ROADMAP.md`. Résumé :

1. Squelette du projet, modules, CI, règles de qualité.
2. Compilation de MuPDF depuis les sources + implémentation de `PdfEngine` + tests.
3. Visionneuse : rendu, défilement, zoom (F02–F04).
4. Ouverture de fichiers et récents (F01, F11, F12).
5. Navigation, table des matières, liens (F05, F06, F09).
6. Recherche et sélection de texte (F07, F08).
7. Thèmes, plein écran, paramètres (F13, F14, F17).
8. Mot de passe, partage, impression, propriétés (F10, F15, F16).
9. Accessibilité, traductions, performance (F18, F19, §3).
10. Préparation de la publication F-Droid.

**v1.1**

11. `PdfWriter` : fusion et extraction (T01, T02) + tests.
12. Conversion images, texte, HTML local (T03–T05) + progression et annulation (T06).
13. Écrans Outils et dossier de sortie.
14. Module `:feature:assistant` : intents et raccourcis (repli), puis AppFunctions.
15. Paramètres « Assistants IA », historique des actions, tests de bout en bout.

### Critères d'acceptation v1.1

- [ ] T01 à T06 implémentés, testés, sans écrasement de fichier possible.
- [ ] Toutes les fonctions du tableau §2.4 exposées et testées, avec erreurs structurées.
- [ ] Lecture du contenu par les assistants désactivée par défaut et respectée par toutes les fonctions concernées.
- [ ] Repli par intents et raccourcis fonctionnel sur `minSdk`.
- [ ] Toujours aucune permission réseau ni dépendance propriétaire.
