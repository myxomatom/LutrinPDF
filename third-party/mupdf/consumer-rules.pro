# SPDX-License-Identifier: GPL-3.0-or-later
# MuPDF's JNI code finds classes, fields and methods of com.artifex.mupdf.fitz by name:
# R8 must neither rename nor remove them.
-keep class com.artifex.mupdf.fitz.** { *; }
