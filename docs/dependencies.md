# Dépendances

Toute dépendance est déclarée dans `gradle/libs.versions.toml` **et** consignée ici
avant d'être ajoutée (`AGENTS.md` §1.4). Licences acceptées : Apache 2.0, MIT, BSD,
LGPL, GPL v3, AGPL v3. Refusées : propriétaires, non commerciales, SDK fermés,
Google Play Services, Firebase, et toute bibliothèque qui ajoute une permission réseau.

La CI vérifie automatiquement les deux gardes-fous (`./gradlew check`) :
- **Licensee** échoue si une dépendance embarquée n'a pas une licence de la liste blanche (ADR 0003) ;
- **`verifyReleasePermissions`** échoue si le manifeste fusionné demande une permission réseau
  ou un accès large au stockage.

Licences vérifiées le 2026-11-03 dans les POM publiés (Maven Central, dépôt Maven de Google).

## Embarquées dans l'application (classpath d'exécution de `release`)

| Dépendance | Version | Licence (SPDX) | Module(s) | Justification |
|------------|---------|----------------|-----------|---------------|
| MuPDF (Artifex) | 1.28.6 (`da5f5eb89`) | AGPL-3.0-or-later | `third-party:mupdf` → `core:pdf` | Moteur de rendu et d'écriture PDF (ADR 0001, 0007) |
| Kotlin stdlib, kotlinx-coroutines | 2.4.21 / 1.11.0 | Apache-2.0 | tous | Langage, asynchronisme |
| AndroidX Core KTX | 1.19.1 | Apache-2.0 | `app`, `core:data`, `feature:reader` | Extensions Kotlin d'Android |
| AndroidX Activity Compose | 1.13.0 | Apache-2.0 | `app` | Activité hôte de Compose |
| AndroidX Lifecycle Runtime Compose | 2.11.0 | Apache-2.0 | `feature:*` | Collecte d'état sensible au cycle de vie |
| Jetpack Compose (BOM) : UI, Material 3, Animation | 2026.09.00 | Apache-2.0 | `core:ui`, `app`, `feature:*` | Interface (`SPEC.md` §4.1). `animation` (éléments partagés de la transition vignette → lecteur, ADR 0019) était déjà tirée par Material 3 ; `core:ui` la déclare parce qu'il expose ses portées |
| Hilt / Dagger | 2.60.1 | Apache-2.0 | `app`, `feature:*` | Injection de dépendances (ADR 0002) |
| AndroidX DataStore Preferences | 1.2.1 | Apache-2.0 | `core:data` | Préférences enregistrées sur l'appareil (`SPEC.md` §5, ADR 0010) |
| AndroidX Room (`room-runtime`) | 2.8.5 | Apache-2.0 | `core:data` | Base locale des récents et des positions de lecture (`SPEC.md` §4.1, §5, ADR 0015). Tire AndroidX SQLite 2.6.2 (`sqlite`, `sqlite-framework`, SQLite du système, pas de SQLite embarqué), `room-common`, AndroidX Collection et Annotation, tous Apache-2.0 ; aucune permission. Licence vérifiée le 2026-11-09 dans le POM publié (dépôt Maven de Google) |
| AndroidX Navigation Compose | 2.10.2 | Apache-2.0 | `app`, `feature:reader` | Navigation entre écrans (`SPEC.md` §4.1) |
| AndroidX Lifecycle ViewModel Compose | 2.11.0 | Apache-2.0 | `feature:reader` | ViewModels dans Compose |
| AndroidX Hilt Lifecycle ViewModel Compose | 1.4.0 | Apache-2.0 | `feature:reader` | `hiltViewModel()` |
| Compose Material Icons Core | BOM (1.7.8) | Apache-2.0 | `feature:reader` | Icônes standard (retour, alerte) |
| AndroidX ProfileInstaller | 1.4.1 | Apache-2.0 | `app` | Installe le Baseline Profile sur les appareils sans Google Play (F-Droid, installation directe) ; aucune permission demandée (ADR 0012) |
| AndroidX Activity Compose (`ReportDrawnWhen`) | 1.13.0 | Apache-2.0 | `feature:reader` | Signale la fin de l'ouverture d'un document (premières pages rendues), mesurée par `:benchmark` (ADR 0012) |

### Police embarquée (ressource, pas une dépendance Maven)

| Police | Version | Licence (SPDX) | Module | Poids | Justification |
|--------|---------|----------------|--------|-------|---------------|
| Fraunces SemiBold (600), `core/ui/src/main/res/font/fraunces_semibold.ttf` | 1.000 (`Version 1.000;[b76b70a41]`), fichier du paquet npm `@expo-google-fonts/fraunces` 0.4.1 (`600SemiBold/Fraunces_600SemiBold.ttf`), sha-256 `9f03c0fe70d1670d22f392a138ac02eceedc5eee5f1cd8d7486ba2aed843b8aa` | OFL-1.1 (« Copyright 2020 The Fraunces Project Authors », github.com/undercasetype/Fraunces) ; texte dans `LICENSES/OFL-1.1-Fraunces.txt` | `core:ui` | 72 504 octets (70,8 Kio) | Police des titres du nouveau design (ADR 0018). TTF statique, non modifié (le test `TitleFontTest` en vérifie la somme de contrôle), 683 glyphes : latin de base, Latin-1 et latin étendu, donc le français et l'anglais ; ne couvre pas U+202F, U+2009 ni `→`, qui retombent sur la police du système. Embarquée : l'application n'a aucun accès réseau. La licence OFL autorise l'embarquement et la redistribution avec le logiciel sous une autre licence, à condition de garder la mention de copyright et le texte de la licence (présents dans `LICENSES/` et dans les métadonnées de la police) et de ne pas vendre la police seule |

La somme de contrôle de l'archive npm (`npm view @expo-google-fonts/fraunces@0.4.1 dist.integrity`) est
`sha512-gYc9P92ObyWvz1rWPOeWSvVKyBFIuA8hgOdhvo4DSiPBaWLbFA6v8uLIEIBwW+0oWTlXbBzjeBReHQI2H7UNjQ==`.
Le paquet npm est sous « MIT AND OFL-1.1 » : MIT pour les scripts d'Expo (non copiés ici), OFL-1.1 pour la
police. Licensee ne voit pas cette police (ce n'est pas un artefact Maven) : elle est vérifiée ici, comme
les bibliothèques compilées avec MuPDF.

Le rapport complet (108 artefacts, tous Apache-2.0 au jalon M0) est produit par
`./gradlew :app:licenseeAndroidRelease` dans `app/build/reports/licensee/`.

### Bibliothèques compilées avec MuPDF (sous-modules de `libmupdf/thirdparty`)

Elles sont compilées depuis les sources par `:third-party:mupdf` et liées dans `libmupdf_java.so`.
Licensee ne les voit pas (ce ne sont pas des artefacts Maven) : elles sont vérifiées ici à
chaque mise à jour de MuPDF.

| Bibliothèque | Rôle | Licence |
|--------------|------|---------|
| brotli | Décompression Brotli | MIT |
| cmark-gfm | Markdown (conversion, v1.1) | BSD-2-Clause |
| extract | Extraction de structure | AGPL-3.0-or-later |
| FreeType | Rendu des polices | FTL ou GPL-2.0-or-later (option GPL retenue) |
| gumbo-parser | Analyse HTML (conversion, v1.1) | Apache-2.0 |
| HarfBuzz | Mise en forme du texte (scripts complexes) | MIT (« Old MIT ») |
| jbig2dec | Images JBIG2 | AGPL-3.0-or-later |
| Little CMS 2 | Gestion des couleurs | MIT |
| libjpeg (IJG) | Images JPEG | IJG |
| MuJS | Interpréteur JavaScript requis par la compilation de MuPDF ; **jamais activé** (ADR 0007) | ISC |
| OpenJPEG | Images JPEG 2000 | BSD-2-Clause |

La bibliothèque zlib est celle d'Android (`-lz`). Le NDK utilisé est la 28.2.13676358.

### Polices de MuPDF compilées dans `libmupdf_java.so`

Fichiers du sous-module (`libmupdf/resources/fonts/`), transformés en tableaux C par
`generateMupdfResources` (ADR 0013). Licensee ne les voit pas : elles sont vérifiées ici à
chaque mise à jour de MuPDF. Les autres polices de MuPDF (Noto, Charis SIL, polices CJK) ne sont pas compilées : le texte
CJK non incorporé utilise les polices du système (ADR 0025).

| Police | Licence | Poids | Rôle |
|--------|---------|-------|------|
| URW Base 35 réduites aux 14 polices du PDF et Nimbus Boxes (`urw/*.cff`) | OFL-1.1 (« Copyright 2016 by (URW)++ Design & Development », `urw/OFL.txt`) | 611 954 octets | Les 14 polices standard du PDF (Base 14), toujours présentes |

OFL-1.1 est compatible avec la GPL v3 et l'AGPL v3 pour une police embarquée ; elle demande de
joindre la mention de copyright et le texte de la licence : **l'écran des licences (E4, jalon M5,
ADR 0003) doit citer ces polices**, qu'AboutLibraries ne détecte pas.

## Debug uniquement (jamais dans l'APK publié)

| Dépendance | Version | Licence | Justification |
|------------|---------|---------|---------------|
| LeakCanary | 2.14 | Apache-2.0 | Détection des fuites mémoire (`AGENTS.md` §8) |
| Compose UI Tooling, ui-test-manifest | BOM | Apache-2.0 | Aperçus, activité de test Compose |

## Tests (jamais distribués)

| Dépendance | Version | Licence | Justification |
|------------|---------|---------|---------------|
| JUnit 4 | 4.13.2 | EPL-1.0 | Exécuteur de tests imposé par Robolectric. EPL-1.0 est incompatible avec la GPL pour une œuvre distribuée, mais JUnit n'est jamais distribué ni lié à l'APK (ADR 0005) |
| Truth | 1.4.5 | Apache-2.0 | Assertions |
| Turbine | 1.2.1 | Apache-2.0 | Tests de `Flow` |
| kotlinx-coroutines-test | 1.11.0 | Apache-2.0 | Dispatchers de test |
| Robolectric | 4.17 | MIT | Tests Android sur JVM |
| Roborazzi | 1.77.0 | Apache-2.0 | Tests de captures d'écran |
| AndroidX Test (runner, rules, ext-junit) | 1.7.0 / 1.7.0 / 1.3.0 | Apache-2.0 | Tests instrumentés |
| Compose UI Test (+ accessibility) | BOM | Apache-2.0 | Tests d'interface, Accessibility Test Framework |
| Hilt Android Testing (`hilt-android-testing`) | 2.60.1 | Apache-2.0 | Tests Robolectric de `app` : remplace le moteur MuPDF, que Robolectric ne peut pas charger, par un faux (`@TestInstallIn`, ADR 0014). Même projet et même licence que Hilt |
| AndroidX Macrobenchmark (`benchmark-macro-junit4`) | 1.5.0 | Apache-2.0 | Module `:benchmark` : démarrage, ouverture, défilement, génération du Baseline Profile (ADR 0012). Tire Perfetto / tracing, Wire et JSpecify, tous Apache-2.0 |
| AndroidX Test UiAutomator | 2.4.0 | Apache-2.0 | Gestes et attentes des benchmarks (`:benchmark`) |
| AndroidX Room Testing (`room-testing`) | 2.8.5 | Apache-2.0 | `core:data` : `MigrationTestHelper`, test de chaque migration de la base contre les schémas exportés (`core/data/schemas/`, ADR 0017). Même projet et même version que `room-runtime` ; licence vérifiée le 2026-11-09 dans le POM publié (dépôt Maven de Google) |

## Build et qualité (outils, jamais distribués)

| Outil | Version | Licence |
|-------|---------|---------|
| Gradle | 9.8.2 | Apache-2.0 |
| Android Gradle Plugin | 9.5.1 | Apache-2.0 |
| KSP | 2.3.13 | Apache-2.0 |
| Room compiler (`room-compiler`, via KSP) et plugin Gradle Room (`androidx.room`, export du schéma dans `core/data/schemas/`) | 2.8.5 | Apache-2.0 |
| ktlint / plugin Gradle ktlint | 1.8.0 / 14.2.0 | MIT |
| detekt / compose-rules | 1.23.8 / 0.4.27 | Apache-2.0 |
| Kover | 0.9.11 | Apache-2.0 |
| Dokka | 2.2.0 | Apache-2.0 |
| Licensee | 1.14.1 | Apache-2.0 |
| Plugin Gradle Baseline Profile (`androidx.baselineprofile`) | 1.5.0 | Apache-2.0 (testé avec AGP < 10.0.0-alpha01) |
| PyMuPDF / Pillow (génération des fichiers de test) | 2.1 / 13.0.0 | AGPL-3.0 / MIT-CMU |
| Android NDK | 28.2.13676358 | Apache-2.0 (licence du SDK Android pour la distribution des binaires de l'outil) |
| freetype2, OpenSSL, zlib du système (build hôte, tests JVM uniquement) | paquets Ubuntu | FTL/GPL-2.0+, Apache-2.0, Zlib |

## Ressources de documentation (non embarquées)

| Ressource | Licence | Emplacement |
|-----------|---------|-------------|
| Roboto, Roboto Mono | OFL-1.1 | `docs/design/assets/fonts/` |
| Fraunces 500 et 600, sous-ensemble latin en WOFF2 (`@fontsource/fraunces` 5.3.0, pour les maquettes seulement ; l'application embarque le TTF ci-dessus) | OFL-1.1 | `docs/design/proposals/fonts/` |

## Prévues

| Dépendance | Jalon | Licence | Usage |
|------------|-------|---------|-------|
| AboutLibraries | M5 | Apache-2.0 | Écran des licences tierces (ADR 0003) |
