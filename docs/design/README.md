# Maquettes UI — Lutrin

Maquettes de référence des écrans et vues de Lutrin (inventaire : `ROADMAP.md` §1).
Format téléphone 390 × 844 dp, Material 3, thème clair (plus une vue en mode nuit).

> **Statut : référence visuelle, pas spécification.** `SPEC.md` fait foi. En cas de
> divergence entre une maquette et la spécification, la spécification l'emporte ;
> tout écart volontaire est documenté dans un ADR.

## Contenu

```
docs/design/
├── README.md            ← ce fichier
├── index.html           ← galerie de toutes les maquettes (ouvrir dans un navigateur)
├── design-tokens.json   ← couleurs, typographie, formes, espacements
├── screens/*.html       ← une page HTML autonome par écran (hors ligne, sans script)
├── png/*.png            ← captures à 2× (780 × 1688 px), lisibles par l'agent
└── assets/fonts/        ← Roboto + Roboto Mono (OFL 1.1), pour un rendu hors ligne
```

## Intégration dans le dépôt

- Les règles d'utilisation des maquettes sont reprises dans `AGENTS.md` §11.
- Ces fichiers relèvent de la documentation : ils ne doivent **jamais** être
  embarqués dans l'APK ni copiés dans `src/main/assets`.

## Règles d'interprétation pour l'implémentation

- **Composants Material 3 Compose** : chaque élément correspond à un composant
  standard (`TopAppBar`, `ExtendedFloatingActionButton`, `SingleChoiceSegmentedButtonRow`,
  `Switch`, `FilterChip`, `ModalBottomSheet`, `AlertDialog`, `ModalNavigationDrawer`,
  `LinearProgressIndicator`, `Slider`, `OutlinedTextField`…). Ne pas recréer à la main
  ce que Material 3 fournit.
- **Couleurs dynamiques** : la palette de `design-tokens.json` est le schéma de repli
  (Android < 12 ou couleurs dynamiques désactivées). Sur Android 12+, Material You
  remplace les teintes ; les rôles (`primary`, `surfaceContainer`…) restent les mêmes.
- **Textes** : les libellés des maquettes sont la base des `strings.xml` FR. Les
  contenus de documents (noms de fichiers, dates, tailles) sont des exemples.
- **Rendu des pages PDF** : les rectangles gris représentent le rendu du moteur
  (`PdfEngine`), pas des composants à reproduire.
- **Accessibilité** : cibles ≥ 48 dp, `contentDescription` sur chaque bouton icône
  (les libellés `aria-label` des maquettes donnent le texte attendu).
- **États** : chaque écran gère chargement / vide / erreur / contenu (`AGENTS.md` §11),
  même quand la maquette n'en montre qu'un. E1 vide et V8 donnent le modèle.

## Inventaire

| ID | Fichier | Version | Jalon | Couvre | À noter |
|----|---------|---------|-------|--------|---------|
| E1 | `E1-Bibliotheque` | MVP | M3 | F01, F11, F12 | Barre de progression de lecture par document ; badge cadenas = PDF chiffré |
| E1 | `E1-Bibliotheque-Vide` | MVP | M3 | V8 (vide) | Mention hors ligne en pied |
| E2 | `E2-Lecteur` | MVP | M2 | F02–F05 | Curseur de pages + bulle de défilement rapide ; lien interne souligné (F09) |
| E2 | `E2-Lecteur-Nuit` | MVP | M2 / M5 | F13, F14 | Plein écran + rendu inversé ; indicateur « Page 3 / 8 » discret |
| E3 | `E3-Parametres` | MVP | M5 | F13, F17, F19 | Thème et rendu en boutons segmentés |
| E4 | `E4-APropos` | MVP | M5 | §7 | Licence et dépôt à confirmer (ADR 0001) |
| V1 | `V1-Recherche` | MVP | M4 | F07 | Résultat courant contrasté, compteur, filtres accents / casse |
| V2 | `V2-TableMatieres` | MVP | M4 | F06 | Tiroir, arborescence repliable, section courante surlignée |
| V3 | `V3-AllerPage` | MVP | M4 | F05 | Plage valide affichée sous le champ |
| V4 | `V4-MotDePasse` | MVP | M3 | F10 | État d'erreur montré ; mention « jamais enregistré » |
| V5 | `V5-Proprietes` | MVP | M5 | F15, F16 | Feuille avec Partager / Imprimer |
| V6 | `V6-Options` | MVP | M5 | F03, F04, F13, F14, F17 | Choix Normal / Nuit / Sépia par aperçu |
| V7 | `V7-LienExterne` | MVP | M4 | F09 | Rappel : l'app n'accède pas au réseau |
| V8 | `V8-Erreur` | MVP | M2–M3 | §6 | « Retirer des récents » en action principale |
| E5 | `E5-Outils` | v1.1 | M6 | T01–T05 | Dossier de sortie ; rappel « jamais d'écrasement » |
| E6 | `E6-Fusion` | v1.1 | M6 | T01 | Glisser-déposer (élément soulevé), plages par fichier, nom sans collision |
| E7 | `E7-Extraction` | v1.1 | M6 | T02 | Sélection en grille + saisie de plage synchronisées |
| E8 | `E8-Conversion` | v1.1 | M6 | T03–T05 | Onglets Images / Texte / HTML ; options de page |
| E9 | `E9-Progression` | v1.1 | M6 | T06 | Annulation + notification (WorkManager) |
| E9 | `E9-Resultat` | v1.1 | M6 | T06 | Ouvrir / Partager ; ajouté aux récents |
| E10 | `E10-Assistants` | v1.1 | M7 | §2.4 | Lecture du contenu **désactivée par défaut** + avertissement ; historique sans contenu |
| V9 | `V9-Signets` | v1.x | M8 | F20 | |
| V10 | `V10-Miniatures` | v1.x | M8 | F24 | Réutilisée par E7 (voir points ouverts) |

## Composants partagés (`core:ui`)

- **Miniature de page** (E1, E7, E9, V10) : page blanche à bordure `outlineVariant`,
  état sélectionné / courant = bordure 3 dp `primary`.
- **Ligne de document** (E1, E9) : miniature 44 × 58 dp, titre, métadonnées, progression.
- **Interrupteur avec libellé et description** (E3, V6, E10).
- **Groupe segmenté** (E3, V6, E8).
- **Dialogue standard** (V3, V4, V7) et **feuille modale** (V5, V6, V9).
- **Écran d'état** icône + titre + texte + actions (E1 vide, V8, E9).
- **Bandeau d'information / d'avertissement** (E4, E5, E10).

## Points ouverts (incohérences relevées dans les documents)

> Suivis dans `docs/open-questions.md`.

1. **`readAloud` vs F25** : la fonction est exposée aux assistants en v1.1 (SPEC §2.4),
   mais la lecture à voix haute (F25) est prévue en v1.x. La règle de parité
   d'`AGENTS.md` §10 impose d'avancer F25 en v1.1 ou de retirer `readAloud` de la v1.1.
2. **V10 en v1.x, mais requise par E7 en v1.1** : le composant grille de miniatures
   doit exister dès M6.
3. **Dossier de sortie** : ROADMAP le place dans E10, SPEC dans les paramètres.
   Les maquettes le placent dans E5 (Outils) ; à trancher dans un ADR.
