# 0009 — Écarts assumés entre les maquettes et l'application (jalon M2)

- **Statut** : accepté
- **Date** : 2026-11-04
- **Références** : `AGENTS.md` §11, `docs/design/README.md`, ADR 0008, `ROADMAP.md` M2 et M3

## Contexte

L'APK des jalons M0 et M1 n'affichait qu'un écran provisoire : les maquettes de
`docs/design/` décrivent des écrans des jalons M2 à M7, pas encore codés. Le jalon M2
implémente le lecteur (E2) et la vue d'erreur (V8), avec une bibliothèque (E1) réduite à son
état vide pour pouvoir ouvrir un fichier. Plusieurs écarts avec les maquettes sont voulus.

## Décision

1. **Couleurs dynamiques (Material You)** : conservées par défaut (`SPEC.md` §4.1). Sur
   Android 12+, la palette des jetons est remplacée par des teintes tirées du fond d'écran ;
   les maquettes ne correspondent donc à l'appareil que sous Android 11 ou moins.
   Un réglage « Couleurs dynamiques » est proposé pour E3 (point ouvert n° 9). Le cadre du
   lecteur (`ReaderColors`, jetons `color.reader`) ne suit jamais Material You.
2. **E1 en avance sur M3, état vide seulement** : sans récents (M3), la bibliothèque montre
   toujours l'état vide de `E1-Bibliotheque-Vide.png`. Les boutons « Outils PDF » et
   « Paramètres » de la barre n'apparaissent qu'avec leurs écrans (M5, M6) : un bouton sans
   destination serait trompeur.
3. **E2 sans les actions des jalons suivants** : « Rechercher », « Table des matières » et
   « Plus d'options » (M4, M5) ne sont pas affichées ; la bulle de défilement rapide et
   l'ouverture de V3 par l'indicateur « 3 / 8 » viennent avec M4.
4. **V8 sans « Retirer des récents »** : l'action arrive avec les récents (M3).
5. **Titres de barre** : les maquettes utilisent 18 px / 500 pour E2 et V8, une taille hors de
   l'échelle des jetons. L'application garde le style `titleLarge` du `TopAppBar` Material 3
   (22 sp / 400), comme le titre « Lutrin » de E1.
6. **Icônes** : les icônes standard viennent de `material-icons-core` (Apache-2.0) ; dossier,
   bouclier, ajustement à la largeur et l'illustration du lutrin sont des vecteurs repris des
   SVG des maquettes (`core:ui/res/drawable`).
7. **Police** : police système (Roboto sur AOSP et Pixel, autre police chez certains
   constructeurs) ; l'espacement des lettres de Material 3 est conservé. Point ouvert n° 10.

## Conséquences

- L'utilisateur voit désormais la bibliothèque et le lecteur des maquettes ; les écarts restants
  sont listés ici et suivis dans `ROADMAP.md`.
- À chaque jalon qui ajoute une action ci-dessus, cet ADR est complété par le suivant.
