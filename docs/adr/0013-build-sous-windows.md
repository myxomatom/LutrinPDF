# 0013 — Build sous Windows : ressources de MuPDF générées par Gradle, tests JVM du moteur sous Linux seulement

- **Statut** : accepté
- **Date** : 2026-11-08
- **Décideurs** : cadrage par la session d'orchestration ; détails techniques par l'agent de
  codage
- **Références** : `AGENTS.md` §1 (règle 6), §5 ; ADR 0005 (outillage), ADR 0007 (intégration
  de MuPDF) ; `docs/reprise.md` §4

## Contexte

Le mainteneur compile aussi sous Windows 10 (Git Bash). Trois étapes du build supposaient Linux :

1. `generateMupdfResources` lançait `make generate` dans le sous-module : `make`, `bash`, `od`
   et `sed` transforment les polices et motifs de césure de MuPDF en sources C
   (`libmupdf/generated/`, script `scripts/hexdump.sh`). `make` n'existe pas par défaut sous
   Windows.
2. `buildMupdfHostJni` compile MuPDF pour la machine (`build-host-jni.sh` : `make`, `cc`,
   `pkg-config`, bibliothèques freetype2, OpenSSL, zlib du système) afin que `core:pdf` teste
   le moteur sur la JVM. Le script vise Linux (`include/linux`, `.so`).
3. detekt prend par défaut la version du JDK qui exécute Gradle comme `--jvm-target` et refuse
   les JDK récents (25).

La sortie du build Linux (CI) ne doit pas changer, et aucun test ne doit être affaibli.

## Options envisagées

### Génération des ressources
- **A — Garder `make`** et demander MSYS2/`make` sous Windows : prérequis lourd, fragile.
- **B — Tâche Gradle en Kotlin** (`build-logic`) qui reproduit `hexdump.sh` octet pour octet :
  aucun outil externe, incrémentale, compatible avec le cache de configuration. Il faut
  suivre la liste des ressources du `Makefile` à chaque mise à jour de MuPDF.

### Tests JVM du moteur hors Linux
- **A — Porter `build-host-jni.sh` sous Windows** (MSVC ou MinGW, dépendances système
  à fournir) : beaucoup de travail pour un poste de développement.
- **B — Sauter ces tests hors Linux**, avec un message explicite, et compter sur la CI (Linux)
  qui les exécute toujours.
- **C — Les supprimer** : interdit (`AGENTS.md` §1, règle 6).

## Décision

- **Ressources : option B.** `generateMupdfResources` est une tâche typée de `build-logic`
  (`GenerateMupdfResourcesTask`, plugin `lutrin.mupdf`) ; la liste reprend `FONT_BIN` et
  `HYPH_BIN` du `Makefile` sans option `TOFU*`, comme l'appel précédent (depuis l'ADR 0025 : avec
  les drapeaux de polices du build Android, soit URW, DroidSansFallback et `hyph-all.zip`). Les autres cibles de
  `generate` (profils ICC, CMaps, `util.js.h`, `css-properties.h`) produisent des en-têtes que
  MuPDF versionne dans `source/` : ils sont utilisés tels quels. Un test compare la sortie à
  celle de `hexdump.sh` sur toutes les ressources ; il tourne en CI (il échoue s'il ne peut
  pas tourner quand `CI=true`) et se saute ailleurs si `bash` manque.
- **Tests JVM du moteur : option B.** Hors Linux, `buildMupdfHostJni` est sauté (avertissement
  dans le journal) et les classes de test qui appellent la bibliothèque native se sautent par
  une hypothèse JUnit (`assumeHostEngine()`, propriété `lutrin.hostEngine`). Sous Linux, elles
  s'exécutent et échouent si la bibliothèque manque. Les tests purs de `core:pdf` tournent
  partout.
- **detekt** : `jvmTarget` fixé à la version Java du projet (17) dans le plugin de convention.

## Conséquences

- Positives : `./gradlew check` fonctionne sous Windows sans `make` ; la génération ne refait
  que les ressources modifiées ; la CI vérifie l'équivalence avec `hexdump.sh`.
- Négatives / dette acceptée : sous Windows, le moteur n'est testé ni sur la JVM ni en local
  (seulement par la CI et les tests instrumentés) ; la couverture de `core:pdf` mesurée
  localement y est plus basse qu'en CI. La liste des ressources est à revoir à chaque mise à
  jour de MuPDF.
- Sous Windows, avec `core.autocrlf=true`, Git réécrit en CRLF les fichiers de `generated/`
  versionnés par MuPDF ; la tâche les réécrit en LF (comme `hexdump.sh`), ce qui ne change pas
  leur contenu pour Git.
- Actions de suivi : README et `docs/reprise.md` (section « Compiler sous Windows ») ; revoir
  `MUPDF_EMBEDDED_RESOURCES` lors d'une mise à jour de MuPDF.
