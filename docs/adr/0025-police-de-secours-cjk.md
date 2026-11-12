# 0025 — Police de secours CJK embarquée dans MuPDF

- **Statut** : accepté
- **Date** : 2026-11-12
- **Décideurs** : mainteneur (embarquer les polices CJK, limite relevée à 18 Mo au plus 20)
- **Références** : `SPEC.md` §3 (taille), `docs/open-questions.md` point 8, ADR 0007, ADR 0013,
  `AGENTS.md` §1.4

## Contexte

Un PDF peut citer une police sans l'incorporer. Pour le latin, MuPDF la remplace par une des 14
polices standard, toujours compilées. Pour le chinois, le japonais et le coréen (CJK), il lui
faut une police CJK : d'abord celle du système, puis une police compilée dans la bibliothèque.

Le build Android d'Artifex (`platform/java/Android.mk`, que nous ne modifions pas) définit
`TOFU_NOTO`, `TOFU_CJK` et `TOFU_SIL` : aucune police de secours n'est compilée. La recherche
dans le système existe pourtant : `androidfonts.c` (compilé avec `HAVE_ANDROID`) cherche
`NotoSerifCJK-Regular`, `NotoSansCJK-Regular` puis `DroidSansFallback` dans `/system/fonts`.
Un appareil Android ordinaire en a une ; un appareil qui n'en a pas (ROM réduite, système
personnalisé, appareil sans langue asiatique installée par le constructeur) affiche ces textes
avec des glyphes manquants ou faux. Le mainteneur veut un rendu correct dans tous les cas.

Polices CJK fournies par MuPDF 1.28.6 :

| Police | Taille | Couverture | Drapeau qui la retire |
|--------|--------|------------|-----------------------|
| DroidSansFallback | 3 556 308 octets | 20 902 idéogrammes unifiés de base, 11 172 syllabes hangûl, kana, bopomofo | `TOFU_CJK` |
| DroidSansFallbackFull | 5 074 864 octets | idem + extension A (6 582 idéogrammes rares) | `TOFU_CJK_EXT` |
| Source Han Serif (TTC, 4 langues) | 24 810 724 octets | formes propres à chaque langue | `TOFU_CJK_LANG` |

## Options envisagées

### Option A — Ne rien embarquer, compter sur les polices du système
- Avantages : rien à faire, APK inchangé.
- Inconvénients : carrés ou glyphes faux sur un appareil sans police CJK ; le rendu dépend de
  l'appareil.

### Option B — DroidSansFallback (`-UTOFU_CJK -DTOFU_CJK_EXT`)
- Avantages : la plus petite des trois ; couvre les quatre écritures demandées ; Apache-2.0.
- Inconvénients : +3,4 Mio par ABI ; une seule forme des idéogrammes (chinoise) pour le japonais
  ; pas d'extension A.

### Option C — DroidSansFallbackFull ou Source Han Serif
- Avantages : idéogrammes rares (C), formes propres à chaque langue (Source Han Serif).
- Inconvénients : +4,8 Mio ou +23,7 Mio par ABI : au-delà de 20 Mo.

## Décision

**Option B.** `third-party/mupdf/build.gradle.kts` passe à ndk-build :

- `MUPDF_EXTRA_CFLAGS=-UTOFU_CJK -DTOFU_CJK_EXT` : ajouté après les drapeaux d'`Android.mk`, il
  annule `TOFU_CJK` et garde `TOFU_NOTO` et `TOFU_SIL` ; `noto.c` référence alors
  DroidSansFallback seulement ;
- `ADD_SOURCE_FILES=…/generated/resources/fonts/droid/DroidSansFallback.ttf.c` : `Android.mk`
  ne compile que les sources générées des polices URW et des motifs de césure ; ce point
  d'extension prévu par Artifex ajoute la police à `libmupdf_java.so`.

`MUPDF_EMBEDDED_RESOURCES` (`build-logic`) reproduit désormais `FONT_BIN` du `Makefile` avec ces
drapeaux (URW, DroidSansFallback, `hyph-all.zip`) : `generateMupdfResources` n'écrit plus les
quelque 200 Mo de sources des polices Noto, SIL, Han et DroidSansFallbackFull, jamais compilées.

L'ordre de recherche ne change pas : les polices du système d'abord, la police embarquée
ensuite. Elle ne sert donc que sur un appareil sans police CJK.

La bibliothèque de bureau des tests JVM (`build-host-jni.sh`, Linux) est compilée avec les mêmes
drapeaux (`-DTOFU_NOTO -DTOFU_CJK_EXT -DTOFU_SIL`). Elle n'a pas de recherche dans le système :
ses tests voient exactement les polices embarquées dans l'application.

### Tailles mesurées (APK release non signés, `assembleRelease`, Windows, NDK 28.2)

| ABI | Avant | Après | Écart | `libmupdf_java.so` après |
|-----|-------|-------|-------|--------------------------|
| arm64-v8a | 12 821 001 octets | 16 378 089 octets | +3 557 088 | 14 418 960 octets |
| armeabi-v7a | 9 523 643 octets | 13 080 515 octets | +3 556 872 | 11 121 380 octets |
| x86_64 | 13 253 312 octets | 16 810 400 octets | +3 557 088 | 14 851 280 octets |

Les bibliothèques natives sont stockées sans compression dans l'APK (`minSdk` 26, alignées pour
être lues sans extraction) : l'écart est la taille de la police, à quelques octets près.

**Limite de `SPEC.md` §3 : 18 Mo par ABI** (1 Mo = 10⁶ octets). Le plus gros APK (x86_64, surtout
pour les émulateurs) garde 1,2 Mo de marge, arm64-v8a 1,6 Mo. 20 Mo n'est pas nécessaire ; la
limite sera revue si les jalons suivants (M5 à M8) la rapprochent.

### Autres polices de secours (non retenues)

Les polices Noto de MuPDF (arabe, hébreu, devanagari, thaï…, 161 fichiers, 14 Mo) ne se
choisissent pas une à une : `TOFU_NOTO` les retire toutes, et en garder une seule demanderait
de modifier `noto.c` ou `Android.mk`. Leur intérêt est faible pour un lecteur PDF : un texte non
CJK dont la police n'est pas incorporée est rare (les écritures simples passent par les 14
polices standard), et MuPDF cherche déjà ces écritures dans `/system/fonts` (Noto Naskh Arabic,
Noto Sans Hebrew, Noto Sans Devanagari… présentes sur tout Android). Charis SIL (`TOFU_SIL`,
≈ 190 Ko) est la police par défaut du moteur HTML / EPUB de MuPDF, que le lecteur n'utilise pas.
**Recommandation : ne pas les ajouter**, sauf retour d'utilisateur montrant un cas réel ; revoir
la question au jalon M6 avec la conversion HTML / texte vers PDF (T05), qui passe par ce moteur
(sans Charis SIL, il se rabat sur les polices standard).

## Conséquences

- Positives : un PDF CJK sans police incorporée s'affiche sur tout appareil ; `generateMupdfResources`
  écrit 200 Mo de moins ; la bibliothèque des tests JVM a les polices de l'application.
- Négatives / dette acceptée : +3,4 Mio par ABI (installation et téléchargement) ; le japonais
  utilise des formes chinoises d'idéogrammes quand le système n'a pas de police japonaise ; pas
  d'idéogrammes rares (extension A et au-delà) sans police système.
- Licence : DroidSansFallback est sous Apache-2.0 (« Copyright (c) 2005-2008, The Android Open
  Source Project »), compatible GPL v3 et AGPL v3 ; inscrite dans `docs/dependencies.md`, à citer
  dans l'écran des licences (E4, M5).
- Tests : `cjk_not_embedded.pdf` (jeu de test) contient du chinois simplifié et traditionnel, du
  japonais et du coréen dans quatre polices CID non incorporées (GB1, CNS1, Japan1, Korea1).
  `CjkFallbackFontCheck` (`core/pdf/src/sharedTest`) vérifie que les polices ne sont pas
  incorporées, que chaque caractère est dessiné par une police qui a son glyphe (sans police
  CJK, MuPDF se rabat sur une police latine qui ne l'a pas) et que chaque ligne laisse de
  l'encre. Il tourne sur la JVM (`CjkFallbackFontTest`, Linux) et sur appareil
  (`CjkFallbackFontInstrumentedTest`), où un `Font.installFontLoader` qui ne trouve rien masque
  ensuite les polices du système pour prouver que la police embarquée est là.
- Suivi : revoir les drapeaux, `MUPDF_EMBEDDED_RESOURCES` et `build-host-jni.sh` à chaque mise
  à jour de MuPDF ; aucune vérification automatique de la taille des APK n'existe encore
  (proposition : une tâche Gradle `verifyReleaseApkSize`, lancée par la CI après
  `assembleRelease`, qui échoue au-delà de 18 Mo).
