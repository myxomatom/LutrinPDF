# Liste de vérification sur appareil (clôture de M4)

> Liste unique des essais encore à faire **sur un téléphone réel** avant de clore M4 et de
> commencer M5. À cocher au fil des essais. Mise à jour : 2026-11-21.
> Sources : `docs/reprise.md`, ADR 0017 à 0025, `ROADMAP.md`.

**Déjà validé, à ne pas refaire** (Xiaomi 23013PC75G, Android 15, HyperOS) :

- tests instrumentés de `core:pdf` (21) et de `app` (12), verts le 2026-11-21 ;
- bug de l'appui long en page par page : corrigé et validé à la main par le mainteneur (PR #39) ;
- essais de M3 du 2026-11-10 : ouverture par le sélecteur, récents, reprise, PDF chiffré réel.

## 0. Préparation

Installer l'APK de debug (`./gradlew :app:installDebug`), puis copier le jeu de test. Les PDF
sont dans `core/testing/src/main/assets/pdfs/` (voir `core/testing/README.md`).

```bash
adb push core/testing/src/main/assets/pdfs/. /sdcard/Download/lutrin-tests/
adb shell ls /sdcard/Download/lutrin-tests/
```

Mots de passe (données de test, pas des secrets) : `encrypted_password_lutrin.pdf` = `lutrin`,
`encrypted_rc4_128.pdf` = `lutrin-rc4`, `encrypted_aes_128.pdf` = `lutrin-aes128`,
`encrypted_owner_password_only.pdf` : aucun (mot de passe propriétaire `lutrin-owner`).
Ouvrir ensuite les fichiers par « Ouvrir un fichier » (dossier Téléchargements › `lutrin-tests`).

Commandes utiles pendant les essais :

```bash
adb logcat -s AndroidRuntime:E               # plantages
adb shell am kill io.github.myxomatom.lutrin  # mort du processus (arrière-plan requis)
adb shell dumpsys activity permissions | grep -i lutrin   # permissions d'URI persistées
adb shell am start -a android.intent.action.VIEW -t application/pdf \
  -d <uri content://> --ei page 3             # « Ouvrir avec » avec une page
```

## 1. Données et ouverture (M3, non essayé)

- [x] **Migration Room 1→2** : installer une ancienne version (schéma 1, avec des récents), puis
  la version courante par-dessus (`adb install -r`). Attendu : récents conservés, aucun plantage.
  Pour obtenir un schéma 1 : compiler le commit d'avant l'ADR 0017 (`git log -- core/data/schemas`).
- [x] **« Ouvrir avec » depuis Fichiers, Gmail (pièce jointe) et Drive**, puis **« Partager »** :
  le document s'ouvre seul dans le lecteur, le retour ramène à l'application appelante, une
  rotation pendant l'ouverture ne rouvre rien en double (ADR 0014).
- [x] **Document d'« Ouvrir avec » Gmail ou Drive dans la liste après fermeture** de l'application :
  attendu, écran V8 « inaccessible » avec « Retirer des récents ».
- [x] **Temps d'ouverture par une vraie URI SAF** reçue d'une autre application (Drive) : le
  noter, les benchmarks ouvrent un fichier du cache.
- [x] **PDF chiffrés** : `encrypted_password_lutrin.pdf`, `encrypted_rc4_128.pdf`,
  `encrypted_aes_128.pdf` par le sélecteur et par « Ouvrir avec » ; mauvais mot de passe annoncé
  par TalkBack ; clavier sans suggestions ; aucun gestionnaire de mots de passe ne propose
  d'enregistrer ; rotation pendant la saisie (champ vide) ; `adb shell am kill` en lecture (mot de
  passe redemandé) ; cadenas et absence de vignette dans les récents (ADR 0017).
- [x] **`encrypted_owner_password_only.pdf`** : s'ouvre sans mot de passe, sans cadenas.
- [x] **Retirer des récents** et **Vider les récents** (ADR 0016) : « Annuler » pendant 10 s,
  retrait après le délai, rotation pendant le message, permissions rendues
  (`dumpsys activity permissions`), V8 « Retirer des récents » sur un fichier supprimé depuis
  « Fichiers ».
- [x] **Reprise de lecture** après redémarrage du téléphone et après `am kill` (ADR 0015).

## 2. Design et lecteur (ADR 0018, 0019, 0011)

- [x] **Transition de la vignette au lecteur** : ouvrir depuis la carte et depuis une ligne
  (simple fondu) ; revenir par la flèche, le bouton et le geste de retour **prédictif** (lent, puis
  annulé) : la page se replie vers la carte ; revenir aussitôt : simple fondu ; PDF lent : le rond
  de chargement reste visible.
- [x] **Paramètres › Lecture › « Aperçu de la page »** : couper, revenir dans un document ouvert,
  le curseur suit en continu ; rallumer.
- [x] **Clavier physique sur le curseur** (flèches, Début, Fin, Page préc./suiv.) : non couvert par
  un test.
- [x] **Mode page par page** : glissement entre pages, zoom dans la page (pas de changement de page
  tant que zoomée), rendu net après zoom, bascule de mode (V6).
- [x] **Fuite LeakCanary** (point ouvert 17) : menu « ⋮ » d'une ligne des récents, puis ouvrir un
  document, en debug. Attendu : aucune alerte ; sinon noter les étapes exactes.

## 3. Aller à la page (F05, V3)

- [x] Appui sur « 5 / 600 » : le clavier numérique s'ouvre seul ; page hors limites et champ vide :
  erreurs annoncées ; valider mène à la page.
- [x] Rotation avec le dialogue ouvert (saisie conservée). TalkBack : bouton « Page x sur n ».

## 4. Table des matières (F06, V2, ADR 0021)

Fichiers : `large_600pages.pdf` (12 parties, 60 chapitres), `nested_outline.pdf` (3 niveaux),
`links_internal_external.pdf` (entrée à position, entrée externe).

- [x] **`large_600pages.pdf`** : tiroir fluide, défilement, « Partie 12 » mène à la page 551, la
  section courante est dépliée et surlignée, le titre apparaît dans l'aperçu du curseur.
- [x] **`nested_outline.pdf`** : déplier et replier, niveaux annoncés par TalkBack (niveau, déplié,
  page cible).
- [x] **`links_internal_external.pdf`** : l'entrée vers le milieu de la page 4 arrive à la bonne
  position en continu ; l'entrée externe ouvre V7.
- [x] Mode page par page (page entière) ; langue de droite à gauche (arabe dans les réglages
  système) ; grande police.
- [x] Un PDF sans sommaire : l'icône est absente.

## 5. Liens (F09, V7, ADR 0022)

Fichier : `links_internal_external.pdf`.

- [x] Lien interne page 1 → page 3 : « Vous étiez à la page 1. » puis « Revenir » ramène.
- [x] Lien vers la destination nommée `chapitre-3` et vers le milieu de la page 4 (position
  correcte au zoom et en page par page).
- [x] Lien `https://example.org/` : V7 affiche le lien complet, « Ouvrir » lance le navigateur,
  « Annuler » ferme.
- [x] **Lien `mailto:lutrin@example.org`** : « Ouvrir » lance l'application de courriel.
- [x] **« Copier le lien »** : le lien est dans le presse-papiers, confirmation du système dès
  Android 13.
- [x] `javascript:`, `file://` et lien relatif : refusés sans ouvrir. Téléphone sans navigateur :
  message d'erreur.
- [x] TalkBack : une action « Lien : … » par lien de la page courante.
- [x] `rotated_destinations.pdf` : les liens de la page 1 arrivent à la bonne position dans la
  page tournée (page 2) et dans la page droite (page 3).

## 6. Recherche (F07, V1, ADR 0023)

Fichiers : `large_600pages.pdf`, `search_many_hits.pdf`, `text_selection.pdf`,
`scanned_images_only.pdf`, `non_latin_text.pdf`.

- [x] **`large_600pages.pdf`, mot « Contenu »** : fluidité pendant le parcours, rang qui change
  après le bouclage, premier résultat atteint au plus vite, « 1 000+ » à la limite, surlignage
  pendant le défilement.
- [x] **`search_many_hits.pdf`** (800 « lutrin » sur une page) : surlignage fluide **au zoom**
  (25 % puis 800 %), suivant et précédent sans à-coup.
- [x] **`text_selection.pdf`** : « bibliothèque » (mot coupé en fin de ligne) trouvé sur deux
  lignes ; recherche en arabe (« مرحبا »).
- [x] **`scanned_images_only.pdf`** : « Aucun résultat » et l'indication d'un document sans texte.
- [x] Options « Respecter la casse » et « Respecter les accents » ; Entrée au clavier physique ;
  rotation pendant une recherche ; mode page par page ; carte d'extrait à la place de la barre
  flottante.
- [x] TalkBack : annonce du nombre puis du résultat.

## 7. Sélection et copie (F08, ADR 0024)

Fichiers : `text_selection.pdf`, `encrypted_owner_password_only.pdf`,
`encrypted_password_lutrin.pdf`, `scanned_images_only.pdf`.

- [x] `text_selection.pdf` : appui long sur un mot latin, poignées de 48 dp, poignées qui se
  croisent, « Tout sélectionner », « Copier » ; au zoom **et** en page par page.
- [x] **Sélection en arabe** (phrase « مرحبا بكم في مكتبة كبيرة. ») : mot choisi, poignées dans le
  bon sens, texte copié dans l'ordre logique.
- [x] **Copie interdite puis autorisée par le réglage** : sur `encrypted_owner_password_only.pdf`,
  « Copier » refusé par défaut ; E3 › Lecture › « Ignorer l'interdiction de copie » activé, la
  copie réussit ; le désactiver, de nouveau refusée.
- [x] **Aperçu masqué du presse-papiers** pour un PDF protégé par mot de passe
  (`encrypted_password_lutrin.pdf`), sous **Android 13+** : le texte copié n'apparaît pas dans
  l'aperçu du système.
- [x] Confirmation de copie du système (13+) ; « Texte copié » **avant** Android 13 seulement
  (autre appareil ou émulateur).
- [x] `scanned_images_only.pdf` : rien n'est sélectionné, sans erreur.
- [x] Fermeture de la sélection : toucher ailleurs, retour, changement de page, recherche.
- [x] TalkBack : action « Copier le texte de la page ».

## 8. Polices CJK (ADR 0025)

- [x] **`cjk_not_embedded.pdf`** : chinois simplifié et traditionnel, japonais, coréen dessinés
  avec les polices du système (pas de carrés). Noter le modèle et la langue du système.

## 9. Transversal

- [x] **TalkBack sur chaque vue** : E1 (liste, carte, menu « ⋮ »), E2 (barre, curseur, indicateur),
  E3, V2, V3, V4, V7, V8, V1, sélection.
- [x] **Grande police** (échelle maximale) : aucune coupure ni cible sous 48 dp, sur E1, E2 et
  chaque dialogue.
- [x] **Mode sombre** : contrastes lisibles, surlignage de recherche et de sélection visibles.
- [x] **Paysage** : lecteur, tiroir de la table des matières, barre de recherche, dialogues.
- [x] **Couleurs dynamiques** activées puis désactivées (E3).
- [x] Barre de recherche et clavier : le clavier ne cache pas la barre.

## 10. Benchmarks et Baseline Profile

À faire sur le téléphone du mainteneur (ADR 0012), jamais en CI. Les scénarios nouveaux sont
`SearchBenchmark` (dix « résultat suivant » puis défilement), `OutlineBenchmark` (tiroir puis saut à
« Partie 12 ») et, dans `BaselineProfileGenerator`, `search`, `outline` et `selection`.

**Réglages HyperOS** (`docs/reprise.md` §4) :

- options pour les développeurs : « Installer via USB » **et** « Débogage USB (paramètres de
  sécurité) », puis **redémarrer** le téléphone ; vérifier
  `adb shell getprop persist.security.adbinput` = `1` ;
- à chaque installation, accepter vite la confirmation affichée sur le téléphone, sinon
  `INSTALL_FAILED_USER_RESTRICTED` ;
- désinstaller d'abord toute version publiée de Lutrin (clés de signature différentes) ;
- écran déverrouillé, allumé, batterie non économe, téléphone frais (pas de chauffe).

**Commandes, dans l'ordre** :

```bash
./gradlew :benchmark:assemble --no-daemon                     # compile (déjà fait par la CI)
adb devices                                                   # un seul appareil, « device »
./gradlew :app:generateBaselineProfile --no-daemon            # 1. profil (Android 13+)
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest --no-daemon   # 2. tous les benchmarks
# un seul scénario :
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest --no-daemon \
  -Pandroid.testInstrumentationRunnerArguments.class=io.github.myxomatom.lutrin.benchmark.SearchBenchmark
```

Générer le profil **avant** de mesurer, puis committer le profil.

**Vérifier les fichiers de sortie** (la tâche affiche BUILD SUCCESSFUL même si l'installation a
échoué et qu'aucun test n'a tourné) :

- [x] `benchmark/build/outputs/connected_android_test_additional_output/benchmarkRelease/connected/<appareil>/`
  contient un `*-benchmarkData.json` récent et des traces `.perfetto-trace` ;
- [x] `app/src/release/generated/baselineProfiles/baseline-prof.txt` et `startup-prof.txt` ont
  changé (`git diff --stat`) et contiennent des classes de `feature.reader.search`, `ui/Outline*` (le
  tiroir est dans `ui`) et `selection` (`grep -c "feature/reader/search" baseline-prof.txt`) ;
- [x] aucun test en échec : en cas d'échec d'un sélecteur (« The Search button is missing »), la
  langue du téléphone n'est ni le français ni l'anglais, ou le document ne s'est pas ouvert.
  Les sélecteurs de `LutrinTarget.kt` passent par les descriptions de contenu (UiAutomator ne voit
  pas les `testTag`) : le compteur de recherche est décrit « 1 000 résultats ou plus », pas
  « Résultat 1 sur … » (celui-ci n'existe que pour TalkBack sur la carte d'extrait) ; le bouton de
  la table des matières n'apparaît qu'une fois le sommaire lu, d'où `waitForOutlineButton()`.

**Chiffres de référence** (Xiaomi 23013PC75G, Android 15, build `benchmarkRelease`, 2026-11-08,
ADR 0012) :

| Benchmark | Référence |
|-----------|-----------|
| `ScrollBenchmark.continuousScroll` | `frameDurationCpuMs` P50 2,9 / P90 4,0 / P99 8,7 ms ; objectif P90 < 16 ms |
| `ScrollBenchmark.pageByPage` | P50 3,3 / P90 6,6 / P99 11,8 ms |
| `OpenDocumentBenchmark.openLargeDocumentCold` | `timeToFullDisplayMs` médiane 1050 ms |
| `StartupBenchmark` avec profil | à froid 357,8 ms |
| `SearchBenchmark.nextResultsThenScroll` | M4 (2026-11-21) : P50 3,4 / P90 5,4 / P99 12,0 ms |
| `OutlineBenchmark.openAndJump` | M4 (2026-11-21) : P50 3,9 / P90 10,4 / P99 27,1 ms |

Les chiffres de M4 (défilement, ouverture, démarrage, après la refonte du design) sont dans
l'ADR 0012, section « Mesures M4, 2026-11-21 ».

- [x] Reporter les mesures (modèle, version d'Android) dans l'ADR 0012 ; les benchmarks existants
  ont été refaits depuis la refonte du design (PR #24).
- [x] Reporter les mesures dans `ROADMAP.md` §0 (à la fermeture de M4, par le mainteneur).

Total : 53 cases à cocher.
