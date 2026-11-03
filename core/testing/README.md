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
| `large_600pages.pdf` | 600 pages de texte | Pas d'`OutOfMemoryError`, défilement fluide |
| `nested_outline.pdf` | 6 pages, table des matières à 3 niveaux (15 entrées) | Arborescence F06 |
| `links_internal_external.pdf` | 4 pages ; page 1 : lien vers la page 3 et vers `https://example.org/` | F09 (confirmation avant le lien externe) |
| `encrypted_password_lutrin.pdf` | AES-256, mot de passe utilisateur **`lutrin`** | F10 ; un autre mot de passe est refusé |
| `form_acroform.pdf` | Champ texte `nom`, case `accepte`, liste `pays` | Affichage ; F22 plus tard |
| `scanned_images_only.pdf` | 2 pages d'images, sans couche texte | Rendu correct, recherche sans résultat |
| `mixed_page_sizes.pdf` | A4, Lettre paysage, A5, carré, page tournée de 90° | Tailles et rotation respectées |
| `non_latin_text.pdf` | Français accentué, arabe (droite à gauche), chinois | Rendu, recherche, sélection F07–F08 |
| `corrupted_truncated.pdf` | Fichier valide coupé en deux | Réparé par le moteur ou message clair, jamais de crash |
| `corrupted_garbage.pdf` | En-tête `%PDF` suivi de données aléatoires | « Impossible d'ouvrir ce document » (`SPEC.md` §6) |
| `not_a_pdf.pdf` | Fichier texte renommé en `.pdf` | Message « type de fichier non PDF » |

Le mot de passe `lutrin` n'est pas un secret : c'est une donnée de test documentée.
Le PDF chiffré n'est pas reproductible à l'octet près (vecteur d'initialisation aléatoire).

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
