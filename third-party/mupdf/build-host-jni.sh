#!/bin/sh
# SPDX-License-Identifier: GPL-3.0-or-later
#
# Builds MuPDF's JNI library for the build machine (Linux), for JVM unit tests only.
# Never packaged in the app. Requires a C compiler, make, pkg-config, and the development
# files of freetype2, libcrypto (OpenSSL) and zlib.
#
# Usage: build-host-jni.sh <output directory> [jobs]
set -eu
OUT=$1
JOBS=${2:-4}
MUPDF=$(cd "$(dirname "$0")/libmupdf" && pwd)
: "${JAVA_HOME:?JAVA_HOME must point to a JDK}"

mkdir -p "$OUT"
make -s -C "$MUPDF" -j"$JOBS" OUT="$OUT" build=release XCFLAGS=-fPIC \
    USE_SYSTEM_FREETYPE=yes USE_SYSTEM_ZLIB=yes libs
cc -c "$MUPDF/platform/java/mupdf_native.c" -o "$OUT/mupdf_native.o" -fPIC -O2 -DNDEBUG -w \
    -I "$MUPDF/include" -I "$JAVA_HOME/include" -I "$JAVA_HOME/include/linux" \
    $(pkg-config --cflags freetype2 libcrypto)
cc -shared -z noexecstack -o "$OUT/libmupdf_java64.so" "$OUT/mupdf_native.o" \
    "$OUT/libmupdf.a" "$OUT/libmupdf-third.a" $(pkg-config --libs freetype2 libcrypto) -lz
