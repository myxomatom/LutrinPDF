# 0001 — Moteur PDF : MuPDF

- **Statut** : proposé (à relire et accepter au début du jalon M1, `ROADMAP.md`)
- **Date** : 2026-11-03
- **Références** : `SPEC.md` §4.2 et §7, `AGENTS.md` §1 et §3, `ROADMAP.md` §4

> Brouillon reconstitué à partir de `SPEC.md`. Les points marqués **[à vérifier]**
> doivent être confirmés sur les sources officielles (site et dépôt d'Artifex) avant
> de passer le statut à « accepté ».

## Contexte

Lutrin doit afficher, rechercher, sélectionner du texte, suivre des liens et lire la
table des matières (v1.0), puis fusionner, extraire et créer des PDF (outils T01–T05,
v1.1), et à terme annoter et remplir des formulaires (v1.x). Contraintes :

- licence libre compatible avec une publication sur F-Droid ;
- compilation depuis les sources, build reproductible ;
- aucune dépendance propriétaire ni permission réseau ;
- `minSdk` 26 ; performances (première page < 1 s, 60 fps) et mémoire bornée.

## Options envisagées

### Option A — MuPDF (Artifex)
- Avantages : une seule bibliothèque pour la lecture **et** l'écriture (rendu, texte
  structuré, recherche, liens, outline, annotations, formulaires, greffe de pages,
  création de documents) ; mode reflow ; autres formats (EPUB, XPS, CBZ, FB2) ;
  bibliothèque Android maintenue par l'éditeur.
- Inconvénients : licence **AGPL v3** (contamine la distribution de l'application) ;
  compilation native lourde (NDK) ; objets non thread-safe ; une licence commerciale
  existe mais n'est pas compatible avec notre modèle libre (non pertinente ici).

### Option B — PDFium (Google / Chromium)
- Avantages : licence permissive (BSD / Apache 2.0 **[à vérifier]**) ; rendu éprouvé.
- Inconvénients : écriture limitée (fusion / création de documents peu outillées) ;
  pas de binding Android officiel (bindings communautaires de maintenance variable) ;
  compilation depuis les sources complexe (outillage Chromium) pour F-Droid.

### Option C — `android.graphics.pdf.PdfRenderer` (plateforme)
- Avantages : aucune dépendance, aucune taille ajoutée.
- Inconvénients : fonctionnalités de texte, recherche et liens absentes sur les anciennes
  versions d'Android (au mieux disponibles sur les versions récentes **[à vérifier]**),
  incompatible avec `minSdk` 26 pour le périmètre MVP ; aucune écriture.

## Décision

**MuPDF**, compilé depuis les sources (sous-module Git, version épinglée, NDK), accessible
**uniquement** via les interfaces `PdfEngine` et `PdfWriter` du module `core:pdf`.
PDFium reste le plan B documenté.

## Conséquences

- **Licence** : le code du projet est en GPL-3.0-or-later ; l'application distribuée est
  soumise à l'AGPL v3 (GPL v3 §13). README et écran « À propos » le mentionnent, avec un
  lien vers le code source de chaque version, y compris la version exacte de MuPDF.
  *Ceci n'est pas un avis juridique.*
- **Dépendances** : toute dépendance doit être compatible GPL v3 et AGPL v3.
- **Build** : temps de compilation natif à mettre en cache en CI ; version de MuPDF
  épinglée par commit / tag du sous-module ; vérifier la reproductibilité dès M1.
- **Concurrence** : un dispatcher mono-thread par document ; libération explicite des
  ressources natives (`destroy()` / `use {}`), y compris en cas d'annulation.
- **Isolation** : aucun type MuPDF hors de `core:pdf`, pour pouvoir changer de moteur.
- **Vérifications de M1** : capacités d'écriture (fusion de 2 PDF, image → PDF) validées
  tôt ; JavaScript PDF jamais exécuté ; identifiant SPDX exact de la licence de MuPDF
  **[à vérifier]** reporté dans `docs/dependencies.md`.
