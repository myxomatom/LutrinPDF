# core:testing — jeu de fichiers de test

Fichiers générés par [`scripts/generate_test_files.py`](scripts/generate_test_files.py)
(aucun fichier tiers : libres de droits). Ils sont dans `src/main/assets/` de ce module,
qui n'est utilisé **que** par les tests (`testImplementation` / `androidTestImplementation`)
et n'est donc jamais embarqué dans l'APK. Liste exposée en Kotlin par `TestFiles`.

Régénérer (Python 3.11+) :

```bash
python3 -m venv .venv && .venv/bin/pip install -r core/testing/scripts/requirements.txt
.venv/bin/python core/testing/scripts/generate_test_files.py
```

## PDF (`assets/pdfs/`, `SPEC.md` §9)

| Fichier | Contenu | Attendu |
|---------|---------|---------|
| `simple_1page.pdf` | 1 page, texte seul | Ouverture, texte extractible |
| `large_600pages.pdf` | 600 pages de texte ; table des matières à 2 niveaux (12 parties de 50 pages, 5 chapitres de 10 pages chacune) | Pas d'`OutOfMemoryError`, défilement fluide, long sommaire F06 ; document des benchmarks de `:benchmark`, copié dans les builds de mesure de l'application (ADR 0012) |
| `nested_outline.pdf` | 6 pages, table des matières à 3 niveaux (15 entrées) | Arborescence F06 |
| `links_internal_external.pdf` | 4 pages ; page 1 : lien vers la page 3 et vers `https://example.org/` ; page 2 : lien vers le milieu de la page 4 (y = 421 pt depuis le haut), lien vers la destination nommée `chapitre-3` (haut de la page 3, y = 100 pt), liens `mailto:lutrin@example.org`, `javascript:alert(1)`, `file:///sdcard/secret.pdf` et URI relative `annexe.html` ; sommaire : une entrée vers le milieu de la page 4 et une entrée externe `https://example.org/sommaire` | F06, F09 : position de la destination ; seuls `http`, `https` et `mailto` s'ouvrent, après confirmation (ADR 0020) |
| `text_selection.pdf` | 1 page : « biblio- » en fin de ligne et « thèque » au début de la suivante ; une ligne à sélectionner ; une phrase arabe « مرحبا بكم في مكتبة كبيرة. » | F07 (mot coupé trouvé, résultat sur deux lignes, recherche en arabe) et F08 (sélection d'un mot, d'une ligne, de texte arabe) |
| `search_many_hits.pdf` | 1 page, 800 fois « lutrin » (80 lignes de 10) | F07 : une page rend toutes ses occurrences (ADR 0020) |
| `rotated_destinations.pdf` | 3 pages, la 2ᵉ tournée de 90° ; page 1 : liens à destination explicite vers la page 2 (`/FitH 300`, `/FitV 200`, `/FitR 100 200 300 400`, `/XYZ 100 500`, `/XYZ null 500`) puis vers la page 3, droite (`/FitH 300`, `/FitR 100 200 300 400`) | F09 : position dans une page tournée ; attendu en coordonnées de page tournée : FitR → x 200, y 100, 200 × 200 ; XYZ → x 500, y 100 ; FitH, FitV et XYZ incomplet → page seule ; page droite : FitH → y 542, FitR → x 100, y 442, 200 × 200 (ADR 0020) |
| `encrypted_password_lutrin.pdf` | AES-256 (révision 6), mot de passe utilisateur **`lutrin`** | F10 ; un autre mot de passe est refusé |
| `encrypted_rc4_128.pdf` | RC4 128 bits (révision 3), mot de passe utilisateur **`lutrin-rc4`** | F10 (ADR 0017) |
| `encrypted_aes_128.pdf` | AES-128 (révision 4), mot de passe utilisateur **`lutrin-aes128`** | F10 (ADR 0017) |
| `encrypted_owner_password_only.pdf` | AES-128, mot de passe utilisateur vide, mot de passe propriétaire `lutrin-owner` (impression et copie restreintes) | S'ouvre **sans** demander de mot de passe ; n'est pas « protégé » dans les récents (ADR 0017) |
| `form_acroform.pdf` | Champ texte `nom`, case `accepte`, liste `pays` | Affichage ; F22 plus tard |
| `scanned_images_only.pdf` | 2 pages d'images, sans couche texte | Rendu correct, recherche sans résultat |
| `mixed_page_sizes.pdf` | A4, Lettre paysage, A5, carré, page tournée de 90° | Tailles et rotation respectées |
| `non_latin_text.pdf` | Français accentué, arabe (droite à gauche), chinois | Rendu, recherche, sélection F07–F08 |
| `corrupted_truncated.pdf` | Fichier valide coupé en deux | Réparé par le moteur ou message clair, jamais de crash |
| `corrupted_garbage.pdf` | En-tête `%PDF` suivi de données aléatoires | « Impossible d'ouvrir ce document » (`SPEC.md` §6) |
| `not_a_pdf.pdf` | Fichier texte renommé en `.pdf` | Message « type de fichier non PDF » |

Les mots de passe `lutrin`, `lutrin-rc4`, `lutrin-aes128` et `lutrin-owner` (et les mots de
passe propriétaires `<mot de passe>-owner`) ne sont pas des secrets : ce sont des données de test
documentées. Les PDF chiffrés ne sont pas reproductibles à l'octet près (identifiant de fichier et
vecteur d'initialisation aléatoires), mais leur contenu et leurs mots de passe le sont. Pour ne
régénérer qu'une partie du jeu, passer le nom des fonctions du script, par exemple
`generate_test_files.py encrypted_variants` (les trois derniers PDF chiffrés).

## Outils (`assets/tools/`, `SPEC.md` §2.3)

| Fichier | Contenu |
|---------|---------|
| `image_small.jpg` | JPEG 640 × 480 |
| `image_medium.png` | PNG 1600 × 1200 |
| `image.webp` | WebP 1200 × 900 |
| `photo_exif_orientation_6.jpg` | JPEG stocké en 800 × 600 avec EXIF `Orientation = 6` : doit apparaître en portrait |
| `image_huge_51mpx.png` | PNG 8256 × 6192 (51,1 Mpx) : à sous-échantillonner |
| `text_utf8.txt` | Texte UTF-8 : accents, emoji, arabe, chinois |
| `markdown_utf8.md` | Markdown UTF-8 : titres, liste, gras / italique, bloc de code |
| `local_with_remote_resource.html` | HTML local avec feuille de style, script et image **distants**, à bloquer (T05) |
