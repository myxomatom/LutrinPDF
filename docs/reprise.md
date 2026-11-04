# Reprendre le développement

> Point d'entrée d'une nouvelle session (personne ou agent). Mis à jour à la fin de chaque
> session de travail. **Dernière mise à jour : 2026-11-04.**
> L'état détaillé par jalon et par vue est dans [`ROADMAP.md` §0](../ROADMAP.md) ; ce fichier dit
> où on en est, ce qui reste à faire en premier, et les pièges déjà rencontrés.

## 1. Où en est le projet

| Jalon | État |
|-------|------|
| M0 Fondations | ✅ Terminé (sauf `:benchmark` et la vérification de reproductibilité F-Droid, reportés) |
| M1 Moteur PDF | ✅ Terminé, audit du 2026-11-04 corrigé |
| M2 Lecteur de base | 🚧 Environ aux deux tiers |
| M3 à M8 | ⏳ À faire (E1 et E3 existent déjà en partie) |

Ce qui est dans `main` et fonctionne sur appareil (vérifié par le mainteneur avec l'APK de debug) :

- **E1 Bibliothèque**, état vide de la maquette : « Ouvrir un fichier » (sélecteur système, PDF
  seulement), icône Paramètres. Pas encore de récents (M3).
- **E2 Lecteur** : défilement continu, zoom au pincement et double tap de 25 à 800 % (corrigé
  dans la PR #9), rendu net de la partie visible, plein écran au tap, curseur et indicateur de page.
- **V8 Erreur** : document endommagé, non PDF, inaccessible, protégé par mot de passe.
- **E3 Paramètres**, section « Apparence » seulement : interrupteur « Couleurs dynamiques »,
  activé par défaut, enregistré avec DataStore (ADR 0010).
- Moteur MuPDF 1.28.6 compilé depuis les sources, sans fuite à l'ouverture ni à la fermeture.

Historique des PR : #4 M1, #6 lecteur, #7 audit et bibliothèque, #9 zoom et Dependabot,
#10 fermeture sans plantage. La PR qui suit apporte E3 et ce fichier.

## 2. Prochaines tâches, dans l'ordre conseillé

1. **Finir M2** (`ROADMAP.md` §2) :
   - mode page par page horizontal (option de F03) ;
   - module `:benchmark` : Macrobenchmark (démarrage, ouverture, défilement) et Baseline
     Profile (point ouvert 12) ; vérifier 60 images/s sur 500 pages, et le gros PDF de test
     sur appareil.
2. **M3 Fichiers et récents** :
   - « Ouvrir avec » et partage entrant (`ACTION_VIEW`, `ACTION_SEND`), avec validation des
     URI et des types MIME (`AGENTS.md` §9) ;
   - permissions persistantes, récents avec vignettes (Room), reprise de la dernière page ;
   - dialogue de mot de passe (V4), « Retirer des récents » dans V8 ;
   - repli par copie dans `cacheDir` pour les URI non seekables (point ouvert 11).
3. Petits restes de l'audit (`docs/audits/2026-11-04-audit-m1.md`, lignes ⏳) :
   - épingler les actions GitHub par SHA ;
   - tester la build minifiée (R8) ;
   - tester `verifyReleasePermissions` de bout en bout ;
   - passer `ContentOpener` et `DocumentDispatcherFactory` en `internal`.

## 3. Décisions à connaître

- Toutes les décisions sont dans `docs/adr/` (0001 à 0010) et dans les points tranchés de
  `docs/open-questions.md`. Les plus récentes :
  - **ADR 0008** : rendu du lecteur (rendu de base et détail de la partie visible) ;
  - **ADR 0009** : écarts assumés avec les maquettes ;
  - **ADR 0010** : couleurs dynamiques désactivables, activées par défaut ; police du système
    conservée (pas de Roboto embarquée).
- Fermeture d'un document : un échec natif est intercepté et signalé, jamais relancé (point 15).
- MuPDF reste épinglé sur un tag publié ; Dependabot ne le suit pas (point ouvert 14).
- Points encore ouverts : 1, 2, 3, 6, 8, 11 à 14 (`docs/open-questions.md`).

## 4. Pièges déjà rencontrés

- **Une seule session de développement à la fois.** Deux sessions ont réalisé le même lecteur
  en parallèle (PR #6 et une branche abandonnée), avec des conflits et deux ADR 0008. Avant de
  commencer, vérifier les PR ouvertes et les branches `ccr-*` récentes.
- **Merger une PR seulement quand sa CI est verte.** Une PR mergée avant la fin de sa CI ne
  prouve rien.
- **CI GitHub** :
  - l'émulateur API 26 ne démarre pas toujours (`EmulatorStartFailedException`) : relancer le
    job avant de chercher un bug ;
  - une file d'attente sans fin vient souvent d'un incident GitHub Actions
    (https://www.githubstatus.com).
- **Dependabot sur le sous-module MuPDF** : il propose la pointe de la branche de développement,
  refusée par `verifyMupdfVersion`. Fermer ces PR sans les merger (suivi retiré de
  `.github/dependabot.yml`).
- **LeakCanary** (APK de debug seulement) ajoute le raccourci « Lutrin Leaks » à l'icône :
  c'est normal, l'application publiée ne l'a pas.

## 5. Préparer un conteneur cloud (session Claude Code sur le web)

Le conteneur n'a ni SDK Android ni sources de MuPDF au départ :

1. **SDK Android** : outils en ligne de commande dans `/opt/android-sdk/cmdline-tools/latest`,
   puis `sdkmanager "platform-tools" "platforms;android-37.0" "build-tools;37.0.0"
   "ndk;28.2.13676358" "cmake;3.22.1"`. Créer ensuite `local.properties` avec
   `sdk.dir=/opt/android-sdk`. Ce fichier est ignoré par Git.
2. **MuPDF** : lancer `./scripts/init-mupdf.sh`.
3. **Maven Central derrière le proxy** : si Gradle échoue avec des erreurs 429, ajouter un
   script d'initialisation **local** (jamais dans le dépôt),
   `~/.gradle/init.d/central-mirror.init.gradle.kts`. Il ajoute en premier le miroir
   `https://maven-central.storage-download.googleapis.com/maven2/` à `pluginManagement` et à
   `dependencyResolutionManagement`, et remet `gradlePluginPortal()` quand `pluginManagement` n'a
   pas d'autre dépôt.
4. **Vérifier** : `./gradlew check`. Compter 5 à 10 minutes au premier lancement, à cause de la
   compilation de MuPDF pour la machine hôte.
5. **Captures d'écran** : après un changement visuel voulu, `./gradlew recordRoborazziDebug`,
   puis relire les images avant de les committer.

## 6. Mettre ce fichier à jour

À la fin de chaque session :
- mettre à jour la section 1 et la date ;
- rayer ou retirer ce qui est fait en section 2 et ajouter ce qui a été découvert ;
- noter en section 4 tout nouveau piège.

`ROADMAP.md` §0, `CHANGELOG.md` et `docs/open-questions.md` restent les sources détaillées.
