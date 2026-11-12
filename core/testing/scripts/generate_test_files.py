#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Generates the test file set of SPEC.md §9 into core/testing/src/main/assets.

Every file is produced by this script, so the set is free of third-party rights and can be
rebuilt at any time:

    python3 -m venv .venv && .venv/bin/pip install -r core/testing/scripts/requirements.txt
    .venv/bin/python core/testing/scripts/generate_test_files.py

Output is deterministic except for the encrypted PDFs (random file identifier and AES initialisation
vector): their content and passwords are stable, not their bytes.
The files are described in core/testing/README.md; keep both in sync.

Names of generators can be given to rebuild only some files, for example
``generate_test_files.py encrypted_variants``, so that the other encrypted files keep their bytes.
"""

from __future__ import annotations

import io
import sys
from pathlib import Path

import pymupdf
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / "src" / "main" / "assets"
PDFS = ROOT / "pdfs"
TOOLS = ROOT / "tools"

# Known passwords of the encrypted fixtures. Not secrets: they are documented in core/testing/README.md.
TEST_PASSWORD = "lutrin"
RC4_PASSWORD = "lutrin-rc4"
AES_128_PASSWORD = "lutrin-aes128"
OWNER_ONLY_PASSWORD = "lutrin-owner"
FIXED_DATE = "D:20260101000000Z"
# Positions of the link targets of links_internal_external.pdf, in points from the top of the page.
MIDDLE_Y = 421
NAMED_Y = 100
A4 = pymupdf.paper_rect("a4")
LOREM = (
    "Le lutrin est un pupitre sur lequel on pose un livre ouvert pour le lire. "
    "Ce document est généré automatiquement pour les tests de Lutrin. "
)


def save(doc: pymupdf.Document, name: str, **kwargs) -> None:
    doc.subset_fonts()
    doc.set_metadata({"title": name, "author": "Lutrin test suite", "creationDate": FIXED_DATE, "modDate": FIXED_DATE})
    doc.save(PDFS / name, garbage=4, deflate=True, no_new_id=True, **kwargs)
    doc.close()


def text_page(doc: pymupdf.Document, title: str, body: str = LOREM, rect: pymupdf.Rect = A4) -> pymupdf.Page:
    page = doc.new_page(width=rect.width, height=rect.height)
    page.insert_text((72, 90), title, fontsize=20)
    page.insert_textbox(pymupdf.Rect(72, 110, rect.width - 72, rect.height - 72), body * 3, fontsize=11)
    return page


def simple() -> None:
    doc = pymupdf.open()
    text_page(doc, "Document simple")
    save(doc, "simple_1page.pdf")


def large() -> None:
    doc = pymupdf.open()
    for i in range(1, 601):
        text_page(doc, f"Page {i}", f"Contenu de la page {i}. " + LOREM)
    # Outline of a long document (F06): 12 parts of 50 pages, each with 5 chapters of 10 pages.
    toc = []
    for part in range(12):
        toc.append([1, f"Partie {part + 1}", part * 50 + 1])
        for chapter in range(5):
            toc.append([2, f"Chapitre {part * 5 + chapter + 1}", part * 50 + chapter * 10 + 1])
    doc.set_toc(toc)
    save(doc, "large_600pages.pdf")


def nested_outline() -> None:
    doc = pymupdf.open()
    toc = []
    page = 1
    for chapter in range(1, 4):
        toc.append([1, f"Chapitre {chapter}", page])
        for section in range(1, 3):
            toc.append([2, f"Section {chapter}.{section}", page])
            toc.append([3, f"Sous-section {chapter}.{section}.1", page])
            text_page(doc, f"Chapitre {chapter} - section {chapter}.{section}")
            page += 1
    doc.set_toc(toc)
    save(doc, "nested_outline.pdf")


def links() -> None:
    doc = pymupdf.open()
    text_page(doc, "Liens")
    for i in range(2, 5):
        text_page(doc, f"Page {i}")
    first = doc[0]
    internal = pymupdf.Rect(72, 300, 300, 320)
    first.insert_text(internal.bl - (0, 4), "Aller à la page 3", fontsize=12, color=(0, 0, 1))
    first.insert_link({"kind": pymupdf.LINK_GOTO, "from": internal, "page": 2})
    external = pymupdf.Rect(72, 340, 300, 360)
    first.insert_text(external.bl - (0, 4), "https://example.org/", fontsize=12, color=(0, 0, 1))
    first.insert_link({"kind": pymupdf.LINK_URI, "from": external, "uri": "https://example.org/"})
    link_targets(doc)
    # Outline (F06): an entry pointing to the middle of page 4 and an external one.
    doc.set_toc([
        [1, "Milieu de la page 4", 4, {"kind": pymupdf.LINK_GOTO, "page": 3, "to": pymupdf.Point(0, MIDDLE_Y), "zoom": 0}],
        [1, "Site externe", -1, {"kind": pymupdf.LINK_URI, "uri": "https://example.org/sommaire"}],
    ])
    save(doc, "links_internal_external.pdf")


def link_targets(doc: pymupdf.Document) -> None:
    """Page 2: a link to a position, a named destination and links the reader must refuse (F09, ADR 0020)."""
    page = doc[1]
    targets = [
        ("Milieu de la page 4", {"kind": pymupdf.LINK_GOTO, "page": 3, "to": pymupdf.Point(0, MIDDLE_Y), "zoom": 0}),
        ("Destination nommée", None),
        ("mailto:lutrin@example.org", {"kind": pymupdf.LINK_URI, "uri": "mailto:lutrin@example.org"}),
        ("javascript:", {"kind": pymupdf.LINK_URI, "uri": "javascript:alert(1)"}),
        ("file:", {"kind": pymupdf.LINK_URI, "uri": "file:///sdcard/secret.pdf"}),
        ("URI relative", {"kind": pymupdf.LINK_URI, "uri": "annexe.html"}),
    ]
    for row, (label, link) in enumerate(targets):
        area = pymupdf.Rect(72, 300 + row * 40, 300, 320 + row * 40)
        page.insert_text(area.bl - (0, 4), label, fontsize=12, color=(0, 0, 1))
        if link is not None:
            page.insert_link(dict(link, **{"from": area}))
        else:
            named_destination(doc, page, area)


def named_destination(doc: pymupdf.Document, page: pymupdf.Page, area: pymupdf.Rect) -> None:
    """Link to the named destination "chapitre-3", the top of page 3 (/XYZ in PDF coordinates)."""
    target = doc[2]
    pdf_y = target.rect.height - NAMED_Y
    names = doc.get_new_xref()
    doc.update_object(names, f"<< /Names [(chapitre-3) [{target.xref} 0 R /XYZ 0 {pdf_y:g} 0]] >>")
    doc.xref_set_key(doc.pdf_catalog(), "Names", f"<< /Dests {names} 0 R >>")
    annotation = doc.get_new_xref()
    r = area * page.transformation_matrix
    doc.update_object(
        annotation,
        f"<< /Type /Annot /Subtype /Link /Rect [{r.x0:g} {r.y0:g} {r.x1:g} {r.y1:g}] /Border [0 0 0] "
        f"/A << /S /GoTo /D (chapitre-3) >> >>",
    )
    annots = doc.xref_get_key(page.xref, "Annots")
    existing = annots[1][1:-1] if annots[0] == "array" else ""
    doc.xref_set_key(page.xref, "Annots", f"[{existing} {annotation} 0 R]")


def encrypted() -> None:
    doc = pymupdf.open()
    text_page(doc, "Document chiffré", f"Mot de passe : {TEST_PASSWORD}. " + LOREM)
    save(
        doc,
        "encrypted_password_lutrin.pdf",
        encryption=pymupdf.PDF_ENCRYPT_AES_256,
        user_pw=TEST_PASSWORD,
        owner_pw=TEST_PASSWORD + "-owner",
    )


def encrypted_variants() -> None:
    """The other encryption methods of F10 (ADR 0017), and a document restricted by its owner only."""
    for name, method, password in (
        ("encrypted_rc4_128.pdf", pymupdf.PDF_ENCRYPT_RC4_128, RC4_PASSWORD),
        ("encrypted_aes_128.pdf", pymupdf.PDF_ENCRYPT_AES_128, AES_128_PASSWORD),
    ):
        doc = pymupdf.open()
        text_page(doc, "Document chiffré", f"Mot de passe : {password}. " + LOREM)
        save(doc, name, encryption=method, user_pw=password, owner_pw=password + "-owner")
    # An empty user password: readers open it without asking; only printing and copying are restricted.
    doc = pymupdf.open()
    text_page(doc, "Document restreint", "Protégé par son auteur, sans mot de passe d'ouverture. " + LOREM)
    save(
        doc,
        "encrypted_owner_password_only.pdf",
        encryption=pymupdf.PDF_ENCRYPT_AES_128,
        user_pw="",
        owner_pw=OWNER_ONLY_PASSWORD,
        permissions=pymupdf.PDF_PERM_ACCESSIBILITY,
    )


def form() -> None:
    doc = pymupdf.open()
    text_page(doc, "Formulaire", "Remplissez les champs ci-dessous.")
    page = doc[0]
    fields = [
        (pymupdf.PDF_WIDGET_TYPE_TEXT, "nom", pymupdf.Rect(72, 200, 350, 222), "", None),
        (pymupdf.PDF_WIDGET_TYPE_CHECKBOX, "accepte", pymupdf.Rect(72, 240, 90, 258), False, None),
        (pymupdf.PDF_WIDGET_TYPE_COMBOBOX, "pays", pymupdf.Rect(72, 280, 250, 302), "France", ["France", "Belgique", "Suisse"]),
    ]
    for field_type, name, rect, value, choices in fields:
        widget = pymupdf.Widget()
        widget.field_type = field_type
        widget.field_name = name
        widget.rect = rect
        widget.field_value = value
        if choices:
            widget.choice_values = choices
        page.add_widget(widget)
    save(doc, "form_acroform.pdf")


def scanned() -> None:
    source = pymupdf.open()
    for i in (1, 2):
        text_page(source, f"Page numérisée {i}")
    doc = pymupdf.open()
    for page in source:
        # Rasterise: the result has images only, no text layer, like a scanned document.
        pixmap = page.get_pixmap(dpi=100, colorspace=pymupdf.csGRAY)
        image_page = doc.new_page(width=page.rect.width, height=page.rect.height)
        image_page.insert_image(image_page.rect, stream=pixmap.tobytes("png"))
    save(doc, "scanned_images_only.pdf")


def mixed_sizes() -> None:
    doc = pymupdf.open()
    sizes = [("A4 portrait", A4), ("Lettre paysage", pymupdf.paper_rect("letter-l")),
             ("A5 portrait", pymupdf.paper_rect("a5")), ("Carré", pymupdf.Rect(0, 0, 500, 500))]
    for title, rect in sizes:
        text_page(doc, title, rect=rect)
    text_page(doc, "Page tournée de 90°")
    doc[-1].set_rotation(90)
    save(doc, "mixed_page_sizes.pdf")


def non_latin() -> None:
    doc = pymupdf.open()
    page = doc.new_page(width=A4.width, height=A4.height)
    html = """
    <h1>Texte accentué et non latin</h1>
    <p>Français : « Où est passé l'été ? » — çà et là, œuvre, Noël, ÉÈÊË.</p>
    <p dir="rtl" lang="ar">العربية: هذا مستند اختبار لقارئ ملفات PDF.</p>
    <p lang="zh">中文：这是一个用于测试的PDF文档。</p>
    """
    page.insert_htmlbox(pymupdf.Rect(72, 72, A4.width - 72, A4.height - 72), html)
    save(doc, "non_latin_text.pdf")


def text_selection() -> None:
    """Search and selection (F07, F08, ADR 0020): a word cut at the end of a line and an Arabic sentence."""
    doc = pymupdf.open()
    page = doc.new_page(width=A4.width, height=A4.height)
    page.insert_text((72, 90), "Recherche et sélection", fontsize=20)
    # Explicit lines, so that "bibliothèque" is cut by a hyphen at the end of the first one.
    lines = [
        "Le mot suivant est coupé en fin de ligne : la biblio-",
        "thèque municipale ouvre à neuf heures.",
        "Une ligne entière à sélectionner.",
    ]
    for row, line in enumerate(lines):
        page.insert_text((72, 140 + row * 20), line, fontsize=12)
    html = '<p dir="rtl" lang="ar" style="font-size: 14pt">مرحبا بكم في مكتبة كبيرة.</p>'
    page.insert_htmlbox(pymupdf.Rect(72, 220, A4.width - 72, 300), html)
    save(doc, "text_selection.pdf")


def many_hits() -> None:
    """One page with 800 occurrences of "lutrin": a page search must return them all (ADR 0020)."""
    doc = pymupdf.open()
    page = doc.new_page(width=A4.width, height=A4.height)
    for row in range(80):
        page.insert_text((36, 40 + row * 9.5), " ".join(["lutrin"] * 10), fontsize=8)
    save(doc, "search_many_hits.pdf")


# Explicit destinations of rotated_destinations.pdf, in PDF coordinates (origin at the bottom left
# of the unrotated page): (label, target page, destination arguments).
EXPLICIT_DESTINATIONS = [
    ("FitH 300, page tournée", 1, "/FitH 300"),
    ("FitV 200, page tournée", 1, "/FitV 200"),
    ("FitR 100 200 300 400, page tournée", 1, "/FitR 100 200 300 400"),
    ("XYZ 100 500, page tournée", 1, "/XYZ 100 500 0"),
    ("XYZ null 500, page tournée", 1, "/XYZ null 500 0"),
    ("FitH 300, page droite", 2, "/FitH 300"),
    ("FitR 100 200 300 400, page droite", 2, "/FitR 100 200 300 400"),
]


def rotated_destinations() -> None:
    """Links with explicit destinations to a page rotated by 90° and to an upright one (F09, ADR 0020)."""
    doc = pymupdf.open()
    text_page(doc, "Destinations explicites")
    text_page(doc, "Page tournée de 90°")
    text_page(doc, "Page droite")
    doc[1].set_rotation(90)
    page = doc[0]
    for row, (label, target, arguments) in enumerate(EXPLICIT_DESTINATIONS):
        area = pymupdf.Rect(72, 300 + row * 30, 400, 318 + row * 30)
        page.insert_text(area.bl - (0, 4), label, fontsize=11, color=(0, 0, 1))
        annotation = doc.get_new_xref()
        r = area * page.transformation_matrix
        doc.update_object(
            annotation,
            f"<< /Type /Annot /Subtype /Link /Rect [{r.x0:g} {r.y0:g} {r.x1:g} {r.y1:g}] /Border [0 0 0] "
            f"/Dest [{doc[target].xref} 0 R {arguments}] >>",
        )
        annots = doc.xref_get_key(page.xref, "Annots")
        existing = annots[1][1:-1] if annots[0] == "array" else ""
        doc.xref_set_key(page.xref, "Annots", f"[{existing} {annotation} 0 R]")
    save(doc, "rotated_destinations.pdf")


def corrupted() -> None:
    # Cut in the middle: MuPDF can usually repair it, the app must at least never crash.
    buffer = io.BytesIO()
    doc = pymupdf.open()
    text_page(doc, "Document tronqué")
    doc.save(buffer, garbage=4, deflate=True, no_new_id=True)
    data = buffer.getvalue()
    (PDFS / "corrupted_truncated.pdf").write_bytes(data[: len(data) // 2])


def garbage() -> None:
    # Valid header followed by noise: no page can be recovered, opening must fail cleanly.
    noise = bytes((i * 7919 + 13) % 256 for i in range(4096))
    (PDFS / "corrupted_garbage.pdf").write_bytes(b"%PDF-1.7\n" + noise)


def not_a_pdf() -> None:
    (PDFS / "not_a_pdf.pdf").write_text("Ceci est un fichier texte renommé en .pdf.\n", encoding="utf-8")


def gradient(width: int, height: int) -> Image.Image:
    image = Image.new("RGB", (width, height))
    draw = ImageDraw.Draw(image)
    for x in range(0, width, max(1, width // 256)):
        shade = int(255 * x / width)
        draw.line([(x, 0), (x, height)], fill=(shade, 90, 255 - shade), width=max(1, width // 256))
    draw.rectangle([width // 10, height // 10, width // 3, height // 3], fill=(255, 255, 255))
    return image


def images() -> None:
    gradient(640, 480).save(TOOLS / "image_small.jpg", quality=85)
    gradient(1600, 1200).save(TOOLS / "image_medium.png", optimize=True)
    gradient(1200, 900).save(TOOLS / "image.webp", quality=80)
    # Stored landscape with EXIF Orientation = 6: must be displayed rotated 90° clockwise (portrait).
    exif = Image.Exif()
    exif[0x0112] = 6
    gradient(800, 600).save(TOOLS / "photo_exif_orientation_6.jpg", quality=85, exif=exif)
    # 8256 x 6192 = 51.1 Mpx (> 50 Mpx): the tools must downsample it instead of loading it whole.
    gradient(8256, 6192).save(TOOLS / "image_huge_51mpx.png", optimize=True)


def texts() -> None:
    sample = "Lutrin — fichier de test UTF-8 : é è à ù ç œ « » … ✓ 📄 العربية 中文\n"
    (TOOLS / "text_utf8.txt").write_text(sample * 3, encoding="utf-8")
    (TOOLS / "markdown_utf8.md").write_text(
        "# Titre\n\n## Sous-titre accentué : été\n\n- élément 1 📄\n- **gras** et *italique*\n\n"
        "```kotlin\nval lutrin = \"PDF\"\n```\n\n" + sample,
        encoding="utf-8",
    )
    (TOOLS / "local_with_remote_resource.html").write_text(
        """<!DOCTYPE html>
<html lang="fr">
<head>
<meta charset="utf-8">
<title>HTML local avec ressource distante</title>
<!-- Every remote resource below must be blocked by the converter (SPEC.md T05). -->
<link rel="stylesheet" href="https://example.org/remote.css">
<script src="https://example.org/remote.js"></script>
<style>body { font-family: sans-serif; } .local { color: #2A5B8C; }</style>
</head>
<body>
<h1 class="local">Contenu local</h1>
<p>Ce paragraphe doit apparaître dans le PDF.</p>
<img src="https://example.org/tracker.png" alt="Image distante bloquée" width="200" height="100">
</body>
</html>
""",
        encoding="utf-8",
    )


GENERATORS = (simple, large, nested_outline, links, encrypted, encrypted_variants, form, scanned, mixed_sizes,
              non_latin, text_selection, many_hits, rotated_destinations, corrupted, garbage, not_a_pdf, images, texts)


def main(names: list[str]) -> None:
    PDFS.mkdir(parents=True, exist_ok=True)
    TOOLS.mkdir(parents=True, exist_ok=True)
    unknown = set(names) - {generate.__name__ for generate in GENERATORS}
    if unknown:
        raise SystemExit(f"Unknown generators: {', '.join(sorted(unknown))}")
    for generate in GENERATORS:
        if not names or generate.__name__ in names:
            generate()


if __name__ == "__main__":
    main(sys.argv[1:])
