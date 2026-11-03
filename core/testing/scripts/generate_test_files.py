#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Generates the test file set of SPEC.md §9 into core/testing/src/main/assets.

Every file is produced by this script, so the set is free of third-party rights and can be
rebuilt at any time:

    python3 -m venv .venv && .venv/bin/pip install -r core/testing/scripts/requirements.txt
    .venv/bin/python core/testing/scripts/generate_test_files.py

Output is deterministic except for the encrypted PDF (AES uses a random initialisation vector).
The files are described in core/testing/README.md; keep both in sync.
"""

from __future__ import annotations

import io
from pathlib import Path

import pymupdf
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1] / "src" / "main" / "assets"
PDFS = ROOT / "pdfs"
TOOLS = ROOT / "tools"

# Known password of the encrypted fixture. Not a secret: it is documented in core/testing/README.md.
TEST_PASSWORD = "lutrin"
FIXED_DATE = "D:20260101000000Z"
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
    save(doc, "links_internal_external.pdf")


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


def main() -> None:
    PDFS.mkdir(parents=True, exist_ok=True)
    TOOLS.mkdir(parents=True, exist_ok=True)
    for generate in (simple, large, nested_outline, links, encrypted, form, scanned, mixed_sizes, non_latin,
                     corrupted, garbage, not_a_pdf, images, texts):
        generate()


if __name__ == "__main__":
    main()
