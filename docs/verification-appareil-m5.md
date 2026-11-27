# Liste de vérification sur appareil et bilan des critères (clôture de M5)

> Liste unique des essais à faire **sur un téléphone réel** avant de clore M5, des benchmarks à
> relancer, et état des critères d'acceptation du MVP (`SPEC.md` §8). À cocher au fil des essais.
> Mise à jour : 2026-11-27 (lot de clôture de M5, fait sans appareil).
> Sources : `docs/reprise.md` §2 et §2 bis, ADR 0026 à 0032, `ROADMAP.md`,
> `docs/audits/2026-11-26-*.md`.

**Déjà validé, à ne pas refaire** : tout ce qui est coché dans
[`verification-appareil-m4.md`](verification-appareil-m4.md) (53 essais, Xiaomi 23013PC75G,
Android 15 / HyperOS, 2026-11-21). Rien de M5 n'a encore été essayé sur appareil.

## 0. Préparation

Comme pour M4 : installer l'APK de debug (`./gradlew :app:installDebug`), puis copier le jeu de test.
Les PDF sont dans `core/testing/src/main/assets/pdfs/` (voir `core/testing/README.md`).

```bash
adb push core/testing/src/main/assets/pdfs/. /sdcard/Download/lutrin-tests/
```

Mots de passe (données de test, pas des secrets) : `encrypted_password_lutrin.pdf` = `lutrin`,
`encrypted_rc4_128.pdf` = `lutrin-rc4`, `encrypted_aes_128.pdf` = `lutrin-aes128`,
`encrypted_owner_password_only.pdf` : aucun (mot de passe propriétaire `lutrin-owner`).

Ordre conseillé : d'abord les tests instrumentés (§1), qui détectent les plantages et les défauts
d'accessibilité mesurables, puis les essais à la main (§2), puis les benchmarks (§3).

## 1. Tests instrumentés à lancer (téléphone branché)

Ils tournent aussi sur la CI (émulateurs API 26 et dernière API) ; les lancer sur le vrai téléphone
confirme ce que l'émulateur ne montre pas (polices du système, `PrintManager`, HyperOS).

```bash
./gradlew :app:connectedDebugAndroidTest :core:pdf:connectedDebugAndroidTest
```

- [ ] `AccessibilityInstrumentedTest` (**nouveau**, 12 tests : bibliothèque avec un récent, lecteur,
      recherche, table des matières, « Affichage », « Propriétés », « Aller à la page », lien
      externe, mot de passe, erreur, paramètres, À propos). Chaque échec nomme la vue et la règle
      de l'*Accessibility Test Framework* (cible tactile, nom, contraste). Verts sur la CI
      (émulateurs API 26 et 37) depuis la PR #57, où ils ont trouvé la poignée de 32 dp des feuilles ;
      à relancer ici pour confirmer sur le téléphone. Un défaut trouvé se
      corrige dans l'interface, sans désactiver de règle (`AGENTS.md` §5) ;
- [ ] `SearchInstrumentedTest`, `SelectionInstrumentedTest`, `LinksInstrumentedTest` (assertions
      explicites ajoutées, audit T2) ;
- [ ] `PrintInstrumentedTest` (lot 1), `ShareInstrumentedTest` (lot 2), `ReadingOptionsInstrumentedTest`
      (lot 4), `MuPdfEngineInstrumentedTest` dont `rendersInNightAndSepiaColours` (lot 3) et
      `rendersTurnedPages` (lot 5), `CjkFallbackFontInstrumentedTest` ;
- [ ] **Si la page endommagée change un test** : `MuPdfEngineInstrumentedTest` ouvre
      `page_tree_count_mismatch.pdf` (PR #52).

## 2. Essais à la main

Les listes des lots sont reprises de `docs/reprise.md` §2 et §2 bis (qui renvoient désormais ici).
Cocher au fil de l'eau ; noter l'appareil et la version d'Android à côté.

### 2.1 Correction de l'audit : page endommagée (PR #52, ADR 0031)

- [ ] ouvrir `page_tree_count_mismatch.pdf` : aucun plantage, pages 1 et 2 dessinées ;
- [ ] aller à la page 3, faire glisser le curseur, zoomer, passer en page par page : le message
      « Page endommagée » apparaît sur la page, mis à l'échelle de la page, sans plantage ;
- [ ] TalkBack sur le message : lu en une phrase (titre puis explication) ;
- [ ] grande police (200 %), mode Nuit et Sépia : message lisible ;
- [ ] tourner les pages (lot 5) : le message suit la rotation.

### 2.2 Lot 1, impression (F15, ADR 0026)

- [ ] imprimer `simple_1page.pdf` et `large_600pages.pdf` vers une vraie imprimante et vers
      « Enregistrer au format PDF » (pages choisies « 2-5 » : le fichier d'origine est envoyé entier,
      le service doit garder la plage) ;
- [ ] un PDF chiffré ouvert avec son mot de passe : images à 150 ppp, aperçu qui arrive après le dessin
      de toutes les pages, mémoire stable ;
- [ ] `encrypted_owner_password_only.pdf` : bouton atténué et message ; puis Paramètres › Lecture ›
      « Ignorer les restrictions du document » : impression permise ;
- [ ] TalkBack sur le bouton « Imprimer » (état « Désactivé, interdit par l'auteur » quand il l'est) ;
- [ ] quitter le lecteur pendant l'impression d'un document protégé : échec propre ;
- [ ] `PrintInstrumentedTest` (§1).

### 2.3 Lot 2, partage et propriétés (F15, F16, V5, ADR 0027)

- [ ] ⋮ › « Partager » vers une messagerie, un gestionnaire de fichiers et « Enregistrer sur
      l'appareil », pour un document ouvert par le sélecteur, par « Ouvrir avec » et pour un PDF
      protégé (il part chiffré) ;
- [ ] une application ou un fournisseur qui refuse l'URI : message « Ce fichier ne peut pas être
      partagé » ;
- [ ] ⋮ › « Propriétés » : valeurs, « Non renseigné », dates dans la langue et le fuseau du téléphone,
      grande police, TalkBack, rotation de l'écran ;
- [ ] « Imprimer » depuis la feuille, y compris sur `encrypted_owner_password_only.pdf` ;
- [ ] `ShareInstrumentedTest` (§1).

### 2.4 E4, « À propos et licences » (ADR 0030)

- [ ] Paramètres › « À propos et licences » : version affichée, phrase « Aucune donnée collectée »,
      licence « GNU GPL v3 ou ultérieure, distribuée sous GNU AGPL v3 » et son explication ;
- [ ] « Code source » : une confirmation s'ouvre, « Annuler » n'ouvre rien, « Ouvrir » lance le
      navigateur du téléphone ;
- [ ] « Politique de confidentialité » : texte local, retour par la flèche et par le geste ;
- [ ] licences tierces : MuPDF, FreeType, HarfBuzz, polices URW++ et Fraunces présentes ; une fiche
      montre le texte complet de la licence (OFL, AGPL...) ;
- [ ] avec TalkBack, grande police (200 %), thème sombre ;
- [ ] sur un build `release`, la liste est présente (ressource `aboutlibraries` non retirée par R8) ;
- [ ] `adb shell dumpsys package io.github.myxomatom.lutrin` ne montre aucune permission réseau.

### 2.5 Lot 3, thèmes et couleurs du document (F13, ADR 0028)

- [ ] premier lancement après mise à jour : thème « Système », couleurs du document d'origine,
      « Suivre le thème sombre » désactivé ; un document en thème sombre reste **blanc** ;
- [ ] Paramètres › Apparence › « Sombre » sur un téléphone en thème clair, puis « Clair » sur un
      téléphone en thème sombre : toute l'application change aussitôt, icônes de la barre d'état et de
      la barre de navigation lisibles ; « Système » suit un changement du thème sans relancer ;
- [ ] couleurs dynamiques activées puis désactivées avec chaque thème (Android 12+) ;
- [ ] ⋮ › « Affichage » › « Nuit » : les pages visibles changent sous la feuille ouverte, fond presque
      noir, texte clair, photos reconnaissables ; « Sépia » : papier crème, texte brun ; « Normal » :
      retour aux couleurs d'origine ;
- [ ] le choix survit à la fermeture du document, à un autre document, à la rotation de l'écran et au
      redémarrage ;
- [ ] en Nuit et en Sépia : zoom à 400 % (jamais de rectangle blanc), défilement rapide sur
      `large_600pages.pdf` (pas de page aux anciennes couleurs après un changement), curseur avec
      aperçu aux couleurs du mode, page par page, recherche (surlignage visible), sélection (poignées
      visibles) ;
- [ ] `page_tree_count_mismatch.pdf` en Nuit : message « Page endommagée » clair sur papier sombre ;
- [ ] plein écran en Nuit avec l'application en thème clair : numéro de page lisible en bas ;
- [ ] mémoire : alterner dix fois Normal, Nuit, Sépia sur 600 pages, puis défiler ; pas de saccade
      durable ni de plantage (LeakCanary ne signale rien) ;
- [ ] « Suivre le thème sombre » activé, thème « Système » : le document passe en Nuit avec le
      téléphone sombre, revient en Normal en clair ; « Sépia » choisi reste en Sépia ; réactiver
      l'interrupteur : il suit de nouveau le thème ;
- [ ] l'impression d'un document protégé, le partage et les vignettes restent en couleurs d'origine ;
- [ ] grande police (200 %) : thème en boutons radio, « Affichage » avec les couleurs l'une sous l'autre ;
- [ ] TalkBack : « Thème de l'application » lu comme un choix, chaque échantillon lu « Nuit, case
      d'option, sélectionné », interrupteur « Suivre le thème sombre ».

### 2.6 Lot 4, options de lecture et langue (F04, F14, F17, F19, ADR 0029)

- [ ] premier lancement après mise à jour : rien ne change (continu à la largeur, page par page à la
      page entière, écran qui s'éteint, orientation libre, langue du système) ;
- [ ] ⋮ › « Affichage » › « Ajustement » › « Page entière » en continu : les pages rétrécissent aussitôt
      pour que la plus haute tienne entière, toutes de la même largeur (document de formats mêlés,
      `large_600pages.pdf`, un PDF paysage) ; double toucher : zoom puis retour ; pincer puis
      « Largeur » : zoom à 100 % ;
- [ ] « Largeur » en page par page : la page remplit la largeur, une page haute défile verticalement, le
      balayage horizontal change de page au zoom de départ, double toucher et pincement zooment, un
      lien et un appui long sur un mot restent justes sur une page défilée ;
- [ ] le choix survit à la fermeture, à un autre document et au redémarrage ; changer de mode de
      défilement garde le choix ;
- [ ] rotation du téléphone avec un ajustement choisi, recherche et sélection (surlignage et poignées à
      la bonne place) dans chaque ajustement ;
- [ ] « Plein écran » : l'interrupteur montre l'état, l'activer masque les barres du lecteur et du système
      feuille ouverte, un toucher sur la page revient et l'interrupteur suit ; rouvrir un document le
      montre avec ses barres ;
- [ ] « Verrouiller l'orientation » : activer en portrait puis tourner (rien ne bouge), recommencer en
      paysage ; quitter vers la bibliothèque : l'écran tourne ; rouvrir un document réapplique le
      verrouillage ; le désactiver rend la rotation automatique ; rotation automatique du système éteinte
      et verrou éteint : la rotation reste celle du système ;
- [ ] Paramètres › Lecture › « Garder l'écran allumé » : un document ouvert garde l'écran allumé au-delà
      du délai de veille, la bibliothèque et les paramètres non ; le désactiver rend le délai ;
- [ ] Paramètres › Apparence › « Langue de l'application » (Android 13 et plus) : « Français » puis
      « English » changent toute l'application aussitôt (bibliothèque, lecteur, E3, E4), page et zoom
      gardés ; « Système » suit de nouveau le téléphone ; les réglages du système (Applications ›
      Lutrin › Langue) montrent français et anglais et sont suivis par la ligne de Lutrin ; Android 12
      ou moins : la ligne n'existe pas ;
- [ ] grande police (200 %) : « Affichage » en boutons radio, rien de coupé, la feuille défile jusqu'aux
      deux interrupteurs ; E3 montre thème et langue en boutons radio ;
- [ ] TalkBack : chaque choix lu « case d'option », interrupteurs lus avec leur état, ajustement et
      défilement atteignables sans les voir ;
- [ ] `ReadingOptionsInstrumentedTest` (§1).

### 2.7 Lot 5, rotation de l'affichage (F27, ADR 0032)

- [ ] premier lancement après mise à jour : la migration Room 2 → 3 garde les récents et leur page,
      aucun document n'est tourné ;
- [ ] icône « Tourner » entre « Rechercher » et la table des matières ; chaque toucher tourne de 90° dans
      le sens horaire, le quatrième revient droit ; TalkBack lit « Tourner les pages, actuellement à
      90° » ;
- [ ] police à 200 % (et écran étroit) : l'icône disparaît de la barre, « Tourner les pages (0°) » est en
      tête du menu ⋮ et fait la même chose ;
- [ ] à 90°, 180° et 270°, en continu puis en page par page : le texte est net (zoom à 400 %), la page
      affichée reste à l'écran après chaque toucher, « Largeur » et « Page entière » ajustent la page
      tournée, double toucher et pincement zooment autour du doigt ;
- [ ] liens : un toucher ouvre le bon lien d'une page tournée (deux liens voisins), y compris en page par
      page sur une page autre que la première ; une entrée de sommaire mène au haut de la page tournée ;
- [ ] recherche : surlignages sur les bons mots à chaque angle, « suivant » amène le résultat au milieu ;
- [ ] sélection : appui long sur un mot, poignées aux deux bouts, déplacer une poignée étend la sélection
      dans le sens du texte tourné, « Copier » copie le bon texte ;
- [ ] aller à une page, curseur avec aperçu tourné, page illisible tournée, Nuit et Sépia tournés ;
- [ ] fermer, rouvrir le document (bibliothèque et « Ouvrir avec ») : rotation et page retrouvées ; un
      autre document reste droit ; retiré des récents puis rouvert : droit ;
- [ ] impression, partage et vignette d'un document tourné sont droits ;
- [ ] un PDF dont les pages ont déjà un `/Rotate` (paysage) : la rotation s'ajoute à celle du fichier ;
- [ ] `MuPdfEngineInstrumentedTest.rendersTurnedPages` (§1).

### 2.8 Accessibilité et langues (F18, F19), lot de clôture

À faire avec TalkBack, puis à 200 % de taille de police, puis en thème sombre. Les contrôles
automatiques (cibles de 48 dp, noms, contraste des couleurs du thème) tournent à chaque `check` ;
ce qui suit est ce qu'ils ne voient pas.

- [ ] **TalkBack, ordre de lecture** : bibliothèque (carte « Reprendre la lecture » avant la liste,
      chaque ligne lue en une phrase avec son menu), lecteur (barre du haut, page, barre du bas), feuilles
      « Affichage » et « Propriétés » (le titre d'abord), dialogues (le titre d'abord, le focus entre
      dans le dialogue et revient au bouton qui l'a ouvert) ;
- [ ] **TalkBack sur la page** : « Document, page 1 sur 8 » ; actions « Lien : page 3 » et « Copier le
      texte de la page » dans le menu des actions ; le curseur de page se règle page par page ;
- [ ] **TalkBack, dialogue « Aller à la page »** : l'erreur « Entre 1 et 8 » est annoncée à la saisie ;
      **mot de passe** : « Mot de passe incorrect » est annoncé, le champ n'est pas lu en clair ;
- [ ] **Grande police (200 %)** sur chaque écran : E1 (vide, récents, erreur), E2, V1 à V8, E3, E4 :
      rien n'est coupé ni superposé, tout reste atteignable ;
- [ ] **Contraste** (Paramètres du téléphone › Accessibilité › Contraste élevé, puis couleurs inversées) :
      textes lisibles dans les deux thèmes, surtout le texte atténué (légende des vignettes, aperçu de
      page, résultats de recherche) ;
- [ ] **Cibles** : tout ce qui se touche se touche au pouce sans erreur, en particulier les poignées de
      sélection et le bouton de la page dans la barre du bas ;
- [ ] **Langue** : parcourir E1 à E4 et V1 à V8 en français puis en anglais (Paramètres › Langue), relire
      les textes à l'écran : pas de texte coupé, pas de mot de l'autre langue, espaces insécables avant
      « : ; ? ! » respectées (pas de retour à la ligne avant un « ? ») ;
- [ ] **Android 12 ou moins** (si un appareil existe) : la ligne « Langue de l'application » n'apparaît pas,
      la langue du système s'applique.

### 2.9 Reste de M4 et divers

- [ ] LeakCanary : aucune fuite signalée pendant tous les essais ci-dessus (une fuite d'un `PopupLayout`
      a été vue une fois le 2026-11-10, point ouvert 17) ;
- [ ] `adb shell dumpsys package io.github.myxomatom.lutrin | grep permission` : aucune permission réseau ;
- [ ] documents corrompus du jeu de test (`corrupted_truncated.pdf`, `corrupted_garbage.pdf`,
      `not_a_pdf.pdf`) : message d'erreur V8, aucun plantage, « Retirer des récents » proposé pour un
      document de la liste.

## 3. Benchmarks et Baseline Profile à relancer

Le lot de clôture n'a touché ni le Baseline Profile ni les benchmarks. M5 a ajouté du travail dans le
lecteur (rotation, ajustement à la page entière, couleurs du document, impression) : les mesures de
M4 (`docs/adr/0012*`, « Mesures M4 ») sont à refaire pour vérifier qu'aucun scénario n'a régressé.

Préparation : Xiaomi / HyperOS, voir `docs/reprise.md` §4 (« Benchmarks sur Xiaomi / HyperOS » :
« Installer via USB » et « Débogage USB (paramètres de sécurité) », confirmation de chaque
installation à accepter vite). **Vérifier que les fichiers de sortie existent** : une installation
échouée affiche quand même BUILD SUCCESSFUL.

```bash
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest   # benchmarks, sortie : benchmarkData.json
./gradlew :app:generateBaselineProfile                       # profil, sortie : baselineProfiles/*.txt
```

| Scénario | Référence M4 (2026-11-21) | À surveiller |
|----------|---------------------------|--------------|
| `ScrollBenchmark.continuousScroll` | `frameDurationCpuMs` P50 3,4 / P90 4,7 / P99 11,7 ms | P90 sous 16 ms |
| `ScrollBenchmark.pageByPage` | P90 7,6 / P99 14,6 ms | P90 sous 16 ms |
| `OpenDocumentBenchmark.openLargeDocumentCold` | `timeToFullDisplayMs` 819 ms | pas de hausse > 15 % |
| `StartupBenchmark` à froid (avec profil) | 426,8 ms | **tâche ouverte** : +20 % depuis M2 (ADR 0012) ; viser le retour sous 400 ms |
| `StartupBenchmark` à chaud | 148,6 ms | idem |
| `SearchBenchmark.nextResultsThenScroll` | P90 5,4 / P99 12,0 ms | P90 sous 16 ms |
| `OutlineBenchmark.openAndJump` | P90 10,4 / P99 27,1 ms | P90 sous 16 ms |

Scénarios à ajouter si le temps le permet (non écrits, c'est du code de benchmark) : lecture en
**Nuit** (le rendu applique un filtre de couleurs, ADR 0028), lecture **tournée de 90°** (ADR 0032),
et ouverture de la **bibliothèque avec 50 récents** (vignettes). Si un scénario régresse, régénérer le
Baseline Profile (`generateBaselineProfile`, qui parcourt déjà démarrage, lecture, recherche, table des
matières et sélection) puis refaire la série avant et après, comme en M4.

- [ ] séries refaites, tableau ci-dessus mis à jour dans l'ADR 0012 (« Mesures M5 ») ;
- [ ] Baseline Profile régénéré et committé (`app/src/release/generated/`) si les temps l'exigent ;
- [ ] taille des APK par ABI sous 15 Mo (`SPEC.md` §3) : `ls -l app/build/outputs/apk/release/`.

## 4. État des critères d'acceptation du MVP (`SPEC.md` §8)

Constaté le 2026-11-27 sur la branche `chore/m5-closing`, sans appareil. ✅ vérifié, 🟡 partiel,
⏳ à faire avec le téléphone, ⏸ reporté.

| Critère (`SPEC.md` §8) | État | Constat |
|------------------------|------|---------|
| F01 à F19 et F27 implémentées et testées | 🟡 | Toutes sont dans `main` (M1 à M5 : PR #4 à #56) et ont leurs tests JVM (ViewModels, écrans Compose sous Robolectric, captures Roborazzi) et, pour le moteur, la recherche, la sélection, les liens, l'impression, le partage, les options de lecture et la rotation, des tests instrumentés. **Pas encore essayées sur appareil : les lots 1 à 5 de M5** (impression F15, partage et propriétés F15 F16, thèmes F13, options F04 F14 F17 F19, rotation F27) et E4. F18 et F19 sont vérifiées par les tests de ce lot (voir plus bas) mais pas avec TalkBack. À cocher après §1 et §2 |
| Aucune permission réseau (CI) | ✅ | `check` exécute `verifyReleasePermissions` (build-logic, `ForbiddenPermissions`, test `ForbiddenPermissionsTest`) sur le manifeste fusionné ; la build `release` est dans la CI. Reste de l'audit M1 : essayer cette tâche de bout en bout avec une permission ajoutée exprès (`docs/reprise.md` §2, point 3). À confirmer sur appareil : `dumpsys package` (§2.4) |
| Aucune dépendance propriétaire ni SDK de suivi | ✅ | Revue de `docs/dependencies.md` (corrigé par l'audit B1) : ni Google Play Services, ni Firebase, ni publicité, ni télémétrie ; LeakCanary est en `debugImplementation` seulement. Ce lot n'ajoute aucune dépendance (les bibliothèques de test Compose, Truth et la plateforme BOM sont déjà au catalogue). Le *scanner* de F-Droid n'a pas encore été lancé : jalon de publication |
| Tests en CI ; couverture ≥ 80 % sur `core:domain` et `core:data`, ≥ 70 % globale | ✅ | `check` passe en local et `koverVerify` aussi (seuil global 70 %, 80 % sur `core:domain` et `core:data`). Couverture des lignes, mesurée le 2026-11-27 sous Windows : **globale 89,96 %**, `core:domain` 98,48 %, `core:data` 98,88 %, `core:ui` 84,05 %, `feature:reader` 98,58 %, `feature:library` 98,60 %, `feature:about` 96,43 %, `feature:settings` 92,31 %, `app` 82,89 %, `core:pdf` 39,73 %. Le faible chiffre de `core:pdf` vient de ce que les tests JVM du moteur (bibliothèque MuPDF hôte) ne tournent que sous Linux (CI) et que le reste est couvert par des tests instrumentés, que Kover ne compte pas ; `feature:tools` et `feature:assistant` n'ont pas de code (M6, M7) |
| Ouverture correcte du jeu de PDF de test (§9) | ✅ (M4) · ⏳ | Les 20 PDF et les 8 fichiers d'outils de `core/testing/src/main/assets/` couvrent chaque ligne de §9. `MuPdfEngineInstrumentedTest.opensEveryValidPdf` les ouvre ; validé sur le Xiaomi le 2026-11-21 (21 tests de `core:pdf`). À relancer en §1 après les lots de M5 |
| Aucun crash sur les PDF corrompus | ✅ (M4) · ⏳ | `corrupted_truncated.pdf`, `corrupted_garbage.pdf`, `not_a_pdf.pdf` : écran V8, tests du ViewModel et du moteur ; `page_tree_count_mismatch.pdf` : message « Page endommagée » (A1, PR #52). Essai à la main en §2.1 et §2.9 |
| Documentation à jour (README, ADR, KDoc) | ✅ | ADR 0001 à 0032, KDoc vérifiée par `:dokkaGenerate` (avertissements = erreurs), `CHANGELOG.md`, `ROADMAP.md`, `docs/reprise.md` et cette liste mis à jour par ce lot. Les captures du `README.md` seront refaites au jalon de publication |
| Build release signé et reproductible | ⏸ **reporté** | Relève du **jalon de publication** (`ROADMAP.md`) : clé de signature, reproductibilité F-Droid (reportée depuis M0). Non coché |

**Ce que ce lot a ajouté sur F18 et F19** (sans appareil) :

- *F18* : audit JVM de chaque contrôle (nom pour TalkBack, cible de 48 dp) sur 30 états d'écrans et de dialogues (3 + 5 + 5 + 17), dont 200 % de police ; contraste AA des couleurs des deux thèmes (`ThemeContrastTest`) ; les contrôles de l'*Accessibility Test Framework* (`AccessibilityInstrumentedTest`) sont verts sur la CI (API 26 et 37) mais **pas encore lancés sur un téléphone**; ils ont fait corriger la poignée des feuilles (32 dp, maintenant 48 dp) ; la lecture TalkBack (ordre, annonces) reste à faire à la main (§2.8) ;
- *F19* : mêmes clés en français et en anglais dans les cinq modules, mêmes variables, typographie française (espaces insécables), orthographe anglaise unifiée, aucun texte visible dans le code (`StringResourcesTest`, Lint) ;
- *Captures* : chaque écran et chaque vue de l'interface (E1 à E4, V1 à V8) a ses captures en clair, sombre, 200 % de police et français ; les états secondaires (nuit, sépia, rotation, page endommagée, etc.) ont celles qui leur sont utiles.

## 5. Pièges notés pendant ce lot

- **Tests Robolectric de `app` qui échouent par délai de 5 s** (`ComposeTimeoutException` dans
  `MainActivityTest`, `MainActivityIncomingIntentTest`, `AboutTest`) quand plusieurs Gradle tournent
  sur la machine en même temps (autres worktrees) : relancer `:app:testDebugUnitTest` seul avant de
  chercher un bug. `--max-workers=3` aide.
- **Captures Roborazzi sous Windows** : `verifyRoborazziDebug` écrit `build/outputs/roborazzi/*_actual.png`
  pour toute capture différente **ou sans référence** ; relire l'image, puis la copier à la main dans
  `src/test/screenshots/`. Les anciens `_actual.png` d'une exécution précédente restent dans `build/` :
  se fier à l'heure du fichier.

