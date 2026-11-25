# 0030 — Écran « À propos et licences » (E4) et liste des licences tierces

- **Statut** : accepté
- **Date** : 2026-11-25
- **Décideurs** : choix fonctionnels par le mainteneur (module dédié, contenu de l'écran,
  AboutLibraries, lien confirmé, pas de bouton de don) ; détails techniques par l'agent de codage
- **Références** : `SPEC.md` §2 (E4), §7 ; `AGENTS.md` §1 (aucun accès réseau, licences), §3
  (modules, ViewModel sans `Context`), §6, §11 ; `docs/open-questions.md` point 6 ; ADR 0003
  (licences tierces), 0006 et 0007 (MuPDF, AGPL), 0022 (liens externes), 0025 (polices) ;
  maquette `docs/design/screens/E4-APropos.html`

## Contexte

E4 doit dire ce qu'est l'application et sous quelle licence elle est distribuée, donner le code
source, la politique de confidentialité et les licences de tout ce qu'elle embarque. Trois
écarts avec la maquette et trois problèmes techniques :

- la maquette annonce « GNU GPL v3 » et cite PDFium ; or le code est sous GPL-3.0-or-later,
  **distribué sous AGPL v3 à cause de MuPDF** (`SPEC.md` §7, point ouvert 6), et le moteur est
  MuPDF ;
- la maquette a un bouton de don : abandonné par le mainteneur ;
- Gradle ne voit que les artefacts Maven. MuPDF est compilé depuis ses sources, avec ses
  bibliothèques natives (FreeType, HarfBuzz, jbig2dec, Little CMS 2, libjpeg, OpenJPEG, Brotli,
  cmark-gfm, Gumbo, extract, MuJS), les motifs de césure de tex-hyphen et les polices URW
  (`generateMupdfResources`, ADR 0013) ; Fraunces est une police de `core:ui`. Aucun outil ne
  les voit, et la licence OFL, la licence IJG et l'AGPL imposent de les citer ;
- l'application n'a pas de permission réseau : tout se lit sur l'appareil, et le lien vers le
  code source ne peut qu'être confié à une autre application.

## Options envisagées

### Génération de la liste
- **Plugin AboutLibraries (version 15.3.0, Apache-2.0)**, avec sa seule bibliothèque `core`
  pour lire le JSON généré, et une interface de Lutrin (retenu) : liste produite par Gradle,
  entrées manuelles et textes de licence pris en charge par la configuration du plugin
  (`libraries/` et `licenses/` du dossier de configuration).
- AboutLibraries avec son composant `aboutlibraries-compose-m3` : une dépendance de plus, une
  interface qui ne reprend ni la maquette ni les jetons de `core:ui`.
- Licensee seul : il vérifie les licences en CI mais ne produit rien d'affichable.
- Liste écrite à la main : elle se périme à chaque mise à jour d'une dépendance. Réservée à ce
  que Gradle ne voit pas.

### Où vit la liste
- Le plugin est appliqué à `:app` seulement : lui seul a toutes les bibliothèques du classpath
  de `release` (appliqué à `feature:about`, il ne verrait que les siennes). Il génère
  `res/raw/aboutlibraries.json` pour chaque variante.
- `feature:about` ne peut ni dépendre de `app` ni lire son `R` : il définit deux interfaces
  (`LicensesSource`, `AppVersionProvider`) que l'application implémente et lie avec Hilt
  (`AboutModule`, `R.raw.aboutlibraries` cité dans le code et dans `res/raw/keep.xml`, car AGP 9
  retire les ressources inutilisées). Le ViewModel ne voit jamais un `Context`.

### Textes de licence hors ligne
Le plugin va chercher les textes SPDX sur le réseau, sauf en mode hors ligne ; la compilation de
F-Droid doit pouvoir s'en passer et donner le même résultat à chaque fois.
- **Mode hors ligne, textes fournis par le dépôt** (retenu) : `offlineMode = true`, et un fichier
  `licenses/*.json` par texte, avec les vrais fichiers de licence des sources (celui de
  FreeType, de HarfBuzz, de libjpeg…) et pas des modèles SPDX sans mention de copyright.
- Mode en ligne : résultat qui dépend du réseau et de la date du fichier distant.

## Décision

1. **Module `feature:about`** (`AboutRoute`, `AboutScreen`, `AboutViewModel`), atteint par la
   dernière ligne « À propos et licences » de E3. E3 ne dépend pas de E4 : `settingsScreen`
   reçoit `onOpenAbout`, et `app` fait la navigation.
2. **Contenu de l'écran** : nom, version (`PackageInfo`, via `AppVersionProvider`), « Aucune
   donnée collectée », licence, code source, politique de confidentialité, liste des licences
   tierces. **Écarts avec la maquette**, consignés ici :
   - la ligne « Licence » indique « GNU GPL v3 ou ultérieure, distribuée sous GNU AGPL v3 » et
     explique pourquoi (MuPDF) ; elle n'ouvre rien, le texte est toujours visible ;
   - MuPDF remplace PDFium ; la liste n'est plus fixe mais générée ;
   - pas de bouton de don ; l'icône du bouclier devient une coche (`material-icons-core`).
3. **Code source** : `https://github.com/myxomatom/LutrinPDF`. Un toucher ouvre une
   confirmation sur le principe de V7 (ADR 0022) : adresse complète, « Lutrin n'accède pas à
   Internet », « Annuler » / « Ouvrir ». « Ouvrir » envoie l'effet `OpenSourceCode` ; la route
   lance `ACTION_VIEW` avec `CATEGORY_BROWSABLE`. Sans application pour l'ouvrir, un message le
   dit. Pas de bouton « Copier le lien » (YAGNI) et aucun filtrage de schéma : l'adresse est une
   constante du code, pas une donnée reçue.
4. **Politique de confidentialité** : texte local, en ressources `strings.xml` (FR et EN),
   reprise de `PRIVACY.md`. **À garder synchronisé** avec lui : un changement de l'un se
   répercute dans l'autre.
5. **Liste des licences** : une ligne par composant (nom à gauche, licence à droite ; avec une
   grande police, la licence passe sous le nom), triée par nom sans tenir compte de la casse,
   puis par identifiant (ordre stable). Un toucher ouvre la fiche : version, auteurs, description,
   site (texte, jamais ouvert) et **le texte complet de chaque licence**. Une liste qu'on
   ne peut pas lire (fichier absent, JSON invalide, aucune bibliothèque) devient un message.
   Le parseur d'AboutLibraries rend une liste vide au lieu de lever une exception, et l'écrit
   dans le journal système (jamais de contenu de document) : une liste vide est donc traitée
   comme illisible, la liste de l'application n'étant jamais vide (MuPDF y est toujours).
6. **Entrées manuelles** (`app/aboutlibraries/`, écrites par `scripts/generate-licenses-config.py`
   à partir du sous-module MuPDF et de `LICENSES/`, à relancer à chaque mise à jour de MuPDF ou
   d'une police) : MuPDF (AGPL-3.0-or-later), Brotli (MIT), cmark-gfm (BSD-2-Clause et autres),
   extract (AGPL), FreeType (FTL ou GPL-2.0-or-later, option GPL), Gumbo (Apache-2.0), HarfBuzz
   (MIT), jbig2dec (AGPL), Little CMS 2 (MIT), libjpeg IJG, MuJS (ISC), OpenJPEG (BSD-2-Clause),
   polices URW++ Base 35 (OFL-1.1) et Fraunces (OFL-1.1). Les motifs de césure n'y sont plus (voir
   « Amendement »). Les textes
   d'Apache-2.0, de BSD-3-Clause (protobuf, dans AndroidX DataStore) et d'AGPL-3.0-or-later
   sont ceux des fichiers du dépôt. La licence IJG demande de dire que « this software is based
   in part on the work of the Independent JPEG Group » : la phrase est dans la description de
   la fiche de libjpeg et dans le texte de sa licence. zlib n'est pas listée : c'est celle
   d'Android (`-lz`), pas distribuée dans l'APK.
7. **Vérifications** : `AboutTest` (module `app`, données réelles) vérifie que la liste contient
   MuPDF, FreeType, HarfBuzz, OpenJPEG, Brotli, MuJS, les polices URW et Fraunces, que chaque
   composant a une licence avec son texte, que MuPDF est sous AGPL-3.0-or-later et que la phrase
   de l'IJG est là. `verifyReleasePermissions` (dans `check`) prouve que le plugin et
   `aboutlibraries-core` n'ajoutent aucune permission.

## Conséquences

- **Déterminisme** : le fichier généré ne contient ni date ni chemin ; deux générations
  successives avec `--rerun-tasks` donnent le même fichier octet pour octet (vérifié le
  2026-11-25 sur Windows). Non vérifié entre deux machines ni dans le job de reproductibilité
  de F-Droid (point ouvert 13).
- **Nouvelles dépendances** (`docs/dependencies.md`) : `aboutlibraries-core` 15.3.0 (Apache-2.0)
  et, à travers lui, `kotlinx-serialization-json` 1.11.0 (Apache-2.0) ; plugin Gradle
  `com.mikepenz.aboutlibraries.plugin.android` 15.3.0 (Apache-2.0, outil de build seulement).
  Aucune permission, aucun SDK propriétaire.
- **Poids** : le JSON fait 182 Ko, dans l'APK en ressource brute compressée par le format APK.
- **Variante debug** : la liste générée pour `debug` contient aussi LeakCanary et les outils de
  test ; seule celle de `release` est publiée.
- **Sécurité des liens** : E4 n'ouvre jamais rien sans confirmation, et les sites des
  composants ne sont affichés qu'en texte.
- Le point ouvert 6 est tranché : E4 dit « GNU GPL v3 ou ultérieure, distribuée sous GNU AGPL v3 ».

## Amendement du 2026-11-25 : motifs de césure retirés (point ouvert 19)

`hyph-all.zip`, compilé dans MuPDF (ADR 0013), réunissait les motifs de césure de 48 langues, dont
sept (ca, hr, is, ru, sv, th, tr) ne sont publiées que sous LPPL, dont la compatibilité avec la
GPL v3 et l'AGPL v3 n'est pas établie. Ces motifs ne servent qu'à la mise en page HTML et EPUB de
MuPDF, que Lutrin ne fait pas : lire un PDF n'y touche pas. **Le mainteneur a décidé de les retirer
du build pour la v1.0.**

- `hyph-all.zip` sort de `MUPDF_EMBEDDED_RESOURCES` (`build-logic`) : `generateMupdfResources` ne
  l'écrit plus, et supprime la source déjà générée.
- MuPDF est compilé avec `-DFZ_ENABLE_HYPHEN=0` (drapeau de `include/mupdf/fitz/config.h`, que
  `Makefile` reconnaît aussi) : passé à ndk-build par `MUPDF_EXTRA_CFLAGS` dans
  `third-party/mupdf/build.gradle.kts`, que l'`Android.mk` d'Artifex reprend pour chaque
  bibliothèque sans être modifié ; et à `make` par `XCFLAGS` dans `build-host-jni.sh` (tests JVM).
  Le code du hyphénateur est ainsi compilé hors de la bibliothèque : aucun symbole ne pointe vers
  une ressource absente.
- Les motifs quittent la liste des licences (`scripts/generate-licenses-config.py`).
- **Pour M6** (conversion HTML vers PDF), deux options : réintégrer un `hyph-all.zip` réduit aux
  langues sous licence compatible (sans ca, hr, is, ru, sv, th, tr), généré par `build-logic` et
  décrit de nouveau dans E4 ; ou rester sans césure.
