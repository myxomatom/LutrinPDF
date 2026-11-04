#!/bin/sh
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Fetches MuPDF (third-party/mupdf/libmupdf, pinned by the parent repository) and only the
# third-party libraries its Android build uses (platform/java/Android.mk). OCR (tesseract,
# leptonica), barcodes (zxing-cpp, zint), curl and freeglut are not built, so not fetched.
set -eu
cd "$(dirname "$0")/.."

git submodule update --init third-party/mupdf/libmupdf
git -C third-party/mupdf/libmupdf submodule update --init --depth 1 \
    thirdparty/brotli \
    thirdparty/cmark-gfm \
    thirdparty/extract \
    thirdparty/freetype \
    thirdparty/gumbo-parser \
    thirdparty/harfbuzz \
    thirdparty/jbig2dec \
    thirdparty/lcms2 \
    thirdparty/libjpeg \
    thirdparty/mujs \
    thirdparty/openjpeg
