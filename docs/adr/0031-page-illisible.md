# 0031 — Page illisible dans un document qui s'ouvre

- **Statut** : accepté
- **Date** : 2026-11-26
- **Décideurs** : message sur la page décidé par le mainteneur (point D1 du plan de correction de
  l'audit, d'abord « page vierge », puis changé pour un message) ; détails techniques par l'agent
  de codage
- **Références** : `SPEC.md` §3 (robustesse), §6 (messages), F02, F05, F13 ; `AGENTS.md` §3, §11 ;
  audit du 2026-11-26, constat A1 ; plan de correction, lot 1 et décision D1 ; ADR 0008 (rendu du
  lecteur), 0012 (mesure du démarrage), 0018 (aperçu du curseur)

## Contexte

Un PDF peut s'ouvrir alors que certaines de ses pages ne se chargent pas. Le cas type : un arbre
de pages dont `/Count` annonce plus de pages qu'il n'en contient. MuPDF lit `/Count` sans le
vérifier (sauf s'il est négatif ou dépasse la table xref) et ne découvre l'absence de la page qu'en
la chargeant (« cannot find page N in page tree »). `core:pdf` traduit l'erreur en
`PdfError.Unreadable`. Une page endommagée, ou la mémoire native épuisée, donne le même résultat.

Aucun appelant du lecteur ne l'interceptait : la lecture des tailles dans
`ReaderViewModel.show()`, le rendu de base et de détail de `PdfPage`, l'aperçu du curseur. L'application
plantait dès l'ouverture, contrairement à `SPEC.md` §3. Aucune maquette ne prévoit cet état.

## Options envisagées

### Ce que montre la page
- **Page vierge** : le fond de la page seul. Simple, mais l'utilisateur peut croire à un défaut
  de Lutrin ou attendre un rendu qui ne viendra jamais.
- **Message centré sur la page** (retenu par le mainteneur) : « Page endommagée », puis « Ce
  fichier PDF contient une erreur qui empêche d'afficher cette page. Les autres pages restent
  lisibles. » Le message dit que le fichier est en cause, pas l'application.
- Écran d'erreur pour tout le document : on perd les pages lisibles.

### Ce que renvoie `PageRenderer`
- `Bitmap?`, `null` couvrant aussi l'échec : l'interface ne distingue pas une page illisible, à
  expliquer, d'un manque de mémoire, qu'un rendu suivant peut réussir.
- **Un type scellé `RenderResult`** (retenu) : `Rendered(bitmap)`, `Unreadable`, `OutOfMemory`.
  Il servira aussi aux corrections de mémoire du lot 2 de l'audit (A2).

## Décision

1. **Contrat du moteur.** La KDoc de `PdfDocument` dit que toute fonction qui prend un index de
   page (`pageSize`, `renderPage`, `selectText`, `isOnText`, `links`) peut lever
   `PdfError.Unreadable`, et que le document reste utilisable : les autres pages se lisent, la
   fermeture libère tout normalement.
2. **Tailles des pages.** `ReaderViewModel` lit chaque taille avec un repli limité à
   `PdfError.Unreadable` : la taille de la page précédente, ou A4 (595 × 842 pt) pour la première.
   `PdfError.Closed` et l'annulation passent. Si **aucune** page ne se lit, le document est fermé et
   le lecteur affiche l'erreur « Impossible d'ouvrir ce document » (V8), sans entrer dans les récents.
3. **Rendu.** `PageRenderer.renderPage`, `renderPart` et `renderPreview` renvoient un
   `RenderResult`. Une page illisible donne `Unreadable` : le bitmap alloué est rendu aussitôt
   (`recycle`), rien n'est mis en cache. Seul `PdfError.Unreadable` est intercepté :
   `PdfError.Closed` (lecteur qui se ferme) et `PdfError.PageOutOfRange` (erreur de programmation)
   restent des exceptions.
4. **Affichage.** `PdfPage` dessine `DamagedPageMessage` sur le fond de la page, en défilement
   continu comme en page par page. Le rendu de détail d'une telle page n'est pas affiché.
   - Le message est mis en page sur 280 dp de large, à la taille de police choisie par
     l'utilisateur, puis mis à l'échelle d'un bloc pour couvrir au plus 80 % de la largeur et de la
     hauteur de la page affichée. Il suit donc la page : petite page, zoom arrière ou zoom avant. Il
     ne déborde jamais. À 200 %, sur une page courte, le texte est réduit pour tenir.
   - Couleurs : celles du texte de l'interface (`onSurface`, `onSurfaceVariant`) du schéma clair
     sur le papier blanc, du schéma sombre sur le papier du mode nuit (F13). Elles ne suivent pas le
     thème de l'application, comme le papier.
   - TalkBack lit le titre et l'explication d'un seul tenant.
5. **Aperçu du curseur.** La carte d'aperçu (ADR 0018) montre une page vierge : le message serait
   illisible à 128 dp. La page elle-même l'affiche une fois atteinte.
6. **Mesure du démarrage.** `ReportDrawnWhen` se déclenche aussi pour une page illisible
   (ADR 0012) : sinon la mesure de `:benchmark` ne finirait jamais sur un tel document.

Le texte des chaînes (`reader_page_damaged_title`, `reader_page_damaged_text`) est en français et
en anglais. Captures Roborazzi : clair, sombre, police à 200 %, défilement continu et page par
page, page courte à 200 %, mode nuit.

## Conséquences

- Un PDF malformé ne fait plus planter le lecteur : les pages lisibles s'affichent, les autres
  portent le message.
- Le jeu de test gagne `page_tree_count_mismatch.pdf` (`/Count 3`, 2 pages), écrit octet par
  octet par `generate_test_files.py`, et `FakePdfDocument.unreadablePages` pour les tests sans
  moteur natif.
- Une page illisible n'est jamais mise en cache : elle est redemandée au moteur quand elle
  revient à l'écran. MuPDF échoue vite, et une page qui ne se chargeait pas faute de mémoire native
  peut ainsi se charger plus tard.
- Écart avec les maquettes : un état sans maquette, documenté ici.
- Limites : la sélection, la recherche et les liens d'une page illisible gardent leur propre
  traitement des erreurs (déjà interceptées) ; l'impression d'un document protégé échoue
  proprement (`onWriteFailed`) si une page ne se rend pas.
