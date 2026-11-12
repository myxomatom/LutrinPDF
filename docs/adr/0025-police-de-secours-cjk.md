# 0025 — Texte CJK non incorporé : polices du système, pas de police embarquée

- **Statut** : accepté
- **Date** : 2026-11-12
- **Décideurs** : mainteneur
- **Références** : `SPEC.md` §3 (taille), `docs/open-questions.md` point 8, ADR 0007, ADR 0013,
  PR #28

## Contexte

Un PDF peut citer une police sans l'incorporer. Pour le latin, MuPDF la remplace par une des 14
polices standard, toujours compilées. Pour le chinois, le japonais et le coréen (CJK), il lui
faut une police CJK : `pdf_load_substitute_cjk_font` (`source/pdf/pdf-font.c`) demande d'abord
une police au système (`fz_load_system_cjk_font`), puis aux polices compilées dans la
bibliothèque ; s'il n'en trouve aucune, il se rabat sur une police latine et le texte s'affiche
en carrés ou en glyphes faux.

Le build Android d'Artifex (`platform/java/Android.mk`, que nous ne modifions pas) définit
`TOFU_NOTO`, `TOFU_CJK` et `TOFU_SIL` : aucune police de secours n'est compilée. La recherche
dans le système, elle, ne dépend d'aucun de ces drapeaux : `Android.mk` compile toujours
`mupdf_native.c` avec `-DHAVE_ANDROID`, qui inclut `jni/android/androidfonts.c` et installe ses
chargeurs (`jni/context.c`, `fz_install_load_system_font_funcs`). Pour une police CID chinoise,
japonaise ou coréenne, ce chargeur lit `NotoSerifCJK-Regular`, `NotoSansCJK-Regular` puis
`DroidSansFallback` dans `/system/fonts`.

Polices CJK fournies par MuPDF 1.28.6, si l'on voulait en embarquer une :

| Police | Taille | Couverture | Drapeau qui la retire |
|--------|--------|------------|-----------------------|
| DroidSansFallback | 3 556 308 octets | 20 902 idéogrammes unifiés de base, 11 172 syllabes hangûl, kana, bopomofo | `TOFU_CJK` |
| DroidSansFallbackFull | 5 074 864 octets | idem + extension A (6 582 idéogrammes rares) | `TOFU_CJK_EXT` |
| Source Han Serif (TTC, 4 langues) | 24 810 724 octets | formes propres à chaque langue | `TOFU_CJK_LANG` |

## Options envisagées

### Option A — Polices CJK du système seulement (build d'Artifex inchangé)
- Avantages : APK inchangé ; rendu avec les polices de l'appareil (Noto CJK a des formes propres
  à chaque langue).
- Inconvénients : carrés sur un appareil sans police CJK.

### Option B — Embarquer DroidSansFallback (`-UTOFU_CJK -DTOFU_CJK_EXT`, `ADD_SOURCE_FILES`)
- Avantages : rendu correct même sans police CJK dans le système.
- Inconvénients : +3,4 Mio par ABI pour une police qui ne sert que sur ces appareils rares, la
  recherche dans le système passant en premier ; limite de taille à relever.

### Option C — DroidSansFallbackFull ou Source Han Serif
- Inconvénients : +4,8 Mio ou +23,7 Mio par ABI.

## Décision

**Option A.** Le build d'Artifex est gardé tel quel : pas de police CJK embarquée, le texte CJK
non incorporé est dessiné avec les polices du système. L'option B a été réalisée puis retirée
dans la même PR (#28) : son coût touche chaque installation, son bénéfice les seuls appareils
sans police CJK. La limite de `SPEC.md` §3 reste à **15 Mo par ABI**.

Tailles mesurées avec l'option B (APK release non signés, `assembleRelease`, NDK 28.2) :

| ABI | Sans police CJK | Avec DroidSansFallback | Écart |
|-----|-----------------|------------------------|-------|
| arm64-v8a | 12 821 001 octets | 16 378 089 octets | +3 557 088 |
| armeabi-v7a | 9 523 643 octets | 13 080 515 octets | +3 556 872 |
| x86_64 | 13 253 312 octets | 16 810 400 octets | +3 557 088 |

Les bibliothèques natives sont stockées sans compression dans l'APK (`minSdk` 26) : l'écart est la
taille de la police. Avec elle, deux APK dépassaient 15 Mo.

`MUPDF_EMBEDDED_RESOURCES` (`build-logic`) reproduit `FONT_BIN` du `Makefile` avec les drapeaux
d'`Android.mk` : polices URW et `hyph-all.zip` seulement. `generateMupdfResources` n'écrit donc
plus les quelque 200 Mo de sources des polices Noto, CJK et SIL, jamais compilées. La
bibliothèque de bureau des tests JVM (`build-host-jni.sh`, Linux) est compilée avec les mêmes
drapeaux et n'a pas de chargeur de polices système : elle se comporte comme un appareil sans
police CJK.

### Autres polices de secours (non retenues)

Les polices Noto de MuPDF (arabe, hébreu, devanagari…, 14 Mo) ne se choisissent pas une à une
(`TOFU_NOTO` les retire toutes) et `androidfonts.c` cherche déjà ces écritures dans
`/system/fonts`. Charis SIL (`TOFU_SIL`, ≈ 190 Ko) est la police par défaut du moteur HTML / EPUB
de MuPDF : à revoir au jalon M6 avec la conversion HTML / texte vers PDF (T05).

## Conséquences

- Positives : APK inchangés ; rendu CJK avec les polices de l'appareil.
- **Limite connue** : sur un appareil sans police CJK (ROM réduite ou personnalisée), le texte
  CJK dont la police n'est pas incorporée s'affiche en carrés ou en glyphes faux. Le texte CJK
  dont la police est incorporée, cas le plus courant, n'est pas concerné. À revoir si un
  utilisateur rencontre le cas : l'option B est documentée et mesurée ci-dessus.
- Tests : `cjk_not_embedded.pdf` (jeu de test) contient du chinois simplifié et traditionnel, du
  japonais et du coréen dans quatre polices CID non incorporées (GB1, CNS1, Japan1, Korea1).
  `CjkFallbackFontCheck` (`core/pdf/src/sharedTest`) vérifie que les polices ne sont pas
  incorporées, que chaque caractère est dessiné par une police qui a son glyphe et que chaque
  ligne laisse de l'encre. `CjkFallbackFontInstrumentedTest` le lance sur appareil (polices du
  système). `CjkFallbackFontTest` (JVM, Linux) vérifie la limite connue : sans police CJK, le
  texte n'est pas dessiné avec des glyphes CJK. Il prouve aussi que la vérification distingue
  des carrés d'un vrai texte, et il échouerait si une police CJK était de nouveau compilée
  sans que cet ADR soit revu.
- Suivi : revoir `MUPDF_EMBEDDED_RESOURCES` et `build-host-jni.sh` face aux drapeaux
  d'`Android.mk` à chaque mise à jour de MuPDF. Aucune vérification automatique de la taille des
  APK n'existe (proposition : une tâche Gradle `verifyReleaseApkSize`, lancée par la CI après
  `assembleRelease`, qui échoue au-delà de la limite de `SPEC.md`).
