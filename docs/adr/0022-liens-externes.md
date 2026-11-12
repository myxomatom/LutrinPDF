# 0022 — Liens externes

- **Statut** : accepté
- **Date** : 2026-11-12
- **Décideurs** : choix fonctionnels par le mainteneur (schémas acceptés, confirmation V7,
  ouverture par `ACTION_VIEW`, retour à la page, actions TalkBack) ; détails techniques par
  l'agent de codage
- **Références** : `SPEC.md` F09, F18 ; `AGENTS.md` §1 (aucun accès réseau), §3 (MVVM, effets
  ponctuels, dispatchers injectés), §9 (sécurité), §11 (accessibilité) ; `ROADMAP.md` M4
  (lot 4) ; ADR 0008 (rendu du lecteur), 0011 (page par page), 0020 (API des liens du moteur) ;
  maquette `docs/design/screens/V7-LienExterne.html`

## Contexte

Depuis l'ADR 0020, le moteur donne pour chaque page ses liens : `PdfLink.Internal` avec une
`PdfDestination(pageIndex, x, y)`, et `PdfLink.External` avec l'URI **telle qu'écrite** dans le
document, quel que soit son schéma ; MuPDF transforme une URI relative en `file://…`. Un PDF
peut donc contenir des liens `javascript:`, `file:`, `content:` ou `intent:`, qui ne doivent
jamais être suivis. F09 demande de suivre les liens internes et d'ouvrir les liens externes
« via intent système après confirmation », l'application elle-même n'accédant pas au réseau.

Il fallait aussi décider comment un toucher trouve le lien visé, sans casser les gestes
existants (un toucher bascule le plein écran, un double toucher zoome), dans les deux modes
de défilement, au zoom ; et comment rendre les liens accessibles avec TalkBack.

## Options envisagées

### Schémas acceptés
- **Liste blanche `http`, `https`, `mailto`** (retenue).
- Liste noire des schémas dangereux : tout schéma inconnu, ou ajouté plus tard à Android,
  passerait.
- Laisser le système choisir pour tout schéma : un `intent:` ou un `content:` pourrait viser
  une autre application ou un fichier local sans que l'utilisateur le comprenne.

### Où filtrer
- Dans le moteur (refusé par l'ADR 0020 : il rend l'URI brute).
- **Dans `core:domain`**, par un cas d'usage pur et testé (`CheckExternalLinkUseCase`),
  partagé par les liens et les entrées externes du sommaire (retenu).

### Trouver le lien touché
- Un détecteur de gestes par lien : il entrerait en concurrence avec le double toucher et le
  pincement.
- **Le détecteur existant transmet la position du toucher** ; des fonctions pures
  (`PageHitTest.kt`) la convertissent en page et en point de page à partir de la géométrie que
  la vue utilise déjà pour dessiner (retenu).

## Décision

1. **Filtrage** (`CheckExternalLinkUseCase`, `core:domain`) : l'URI est débarrassée des espaces
   qui l'entourent ; elle est acceptée seulement si son schéma (RFC 3986 §3.1, insensible à la
   casse) est `http`, `https` ou `mailto`, si `http(s)` est suivi de `//` et d'un hôte non vide,
   si `mailto:` est suivi de quelque chose. Sont refusés : tout autre schéma (`javascript:`,
   `file:`, `content:`, `intent:`, `data:`, `tel:`…), les URI relatives, les URI vides ou mal
   formées, celles qui contiennent une espace, un caractère de contrôle ou un caractère de
   mise en forme invisible (par exemple un renversement bidirectionnel `U+202E`, qui ferait
   afficher une autre adresse que la vraie), et celles de plus de 8 192 caractères. Le schéma
   accepté est réécrit en minuscules : Android compare les schémas des intents en tenant compte
   de la casse. Un lien refusé donne un message bref (« Ce lien ne peut pas être ouvert. »),
   sans dialogue.
2. **Confirmation (V7)** : `ExternalLinkDialog` affiche l'URI **complète** (elle défile dans sa
   boîte si elle est très longue), rappelle que Lutrin n'accède pas à Internet et propose
   « Copier le lien », « Annuler » et « Ouvrir ». Le libellé « Copier le lien » (au lieu de
   « Copier » dans la maquette) suit la décision du mainteneur ; avec une grande police, les
   boutons qui ne tiennent plus passent à la ligne. L'URI en attente est un `StateFlow` du
   ViewModel (`externalLink`) : elle survit à la rotation, pas à la mort du processus. Après
   « Copier le lien », « Lien copié » n'est affiché qu'avant Android 13, qui confirme la copie
   lui-même.
3. **Ouverture** : « Ouvrir » envoie l'effet ponctuel `LinkEffect.OpenExternalLink` (canal du
   ViewModel, qui ne voit jamais de `Context`) ; la route lance `ACTION_VIEW` avec
   `CATEGORY_BROWSABLE` (`openExternalLink`), comme le ferait un lien d'une page web : le système
   propose l'application de l'utilisateur. `ActivityNotFoundException` donne « Aucune
   application ne peut ouvrir ce lien. ». **Aucune permission n'est ajoutée** : Lutrin ne charge
   jamais l'URI lui-même.
4. **Lien interne** : l'effet `LinkEffect.GoTo` montre la destination (`PageNavigator.goTo`,
   avec `y` en défilement continu ; page entière en page par page, ADR 0011). Si la page change,
   un message « Vous étiez à la page N. » propose « Revenir », qui ramène au haut de la page
   quittée. Pas d'historique de navigation complet. Une destination hors du document est
   ignorée.
5. **Toucher** : `readerGestures` transmet la position du toucher simple. `PageHitTest.kt`
   donne la page et le point touchés dans les deux modes, au zoom et avec défilement
   horizontal ou vertical (les marges et l'espace entre les pages ne touchent aucune page) ;
   `linkAt` choisit le premier lien qui contient le point, sinon le plus proche à moins de
   8 dp (un doigt couvre plus qu'un petit lien). Un toucher sur un lien l'active ; ailleurs, il
   bascule le plein écran comme avant ; le double toucher zoome toujours.
6. **Chargement** : `PageLinks` lit les liens d'une page sur le dispatcher injecté et garde ceux
   des 64 dernières pages lues (LRU) ; un échec du moteur vaut « aucun lien » et n'est pas mis
   en cache.
7. **TalkBack** : la zone du document porte une action personnalisée par lien de la page
   courante (« Lien : page 3 », « Lien : https://… »), qui suit le lien comme un toucher.
8. **Sommaire** : une entrée externe du sommaire (lot 3, ADR 0021) passe par le même chemin
   (`ReaderViewModel.onExternalOutlineEntry`, puis `LinkController.onExternalLink`), donc par le
   même filtre et la même confirmation ; le tiroir reste ouvert sous V7. Cela complète le point
   laissé ouvert par l'ADR 0021.

## Conséquences

- Positives : aucun lien n'est suivi sans geste explicite, et aucun schéma dangereux ne
  l'est jamais ; la logique de filtrage et de géométrie est testée en unitaire ; les liens
  fonctionnent au zoom, dans les deux modes, et avec TalkBack.
- Négatives / dette acceptée :
  - « Revenir » ramène au haut de la page quittée, pas à la position exacte ni au zoom ;
  - `x` des destinations n'est pas utilisé (la page est ajustée à la largeur) ;
  - les actions TalkBack ne couvrent que la page courante ; elles ne disent pas où est le lien
    dans la page ;
  - les liens ne sont pas signalés visuellement (le PDF les dessine lui-même, ou pas) ;
  - pas de vérification préalable qu'une application répond (`queryIntentActivities`
    demanderait une déclaration `<queries>`) : l'échec est signalé après coup.
- Actions de suivi : essais sur appareil (navigateur, messagerie pour `mailto`, absence
  d'application) ; `links_internal_external.pdf` couvre les cas du jeu de test.
