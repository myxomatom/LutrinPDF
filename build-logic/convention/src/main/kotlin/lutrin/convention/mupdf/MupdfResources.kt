// SPDX-License-Identifier: GPL-3.0-or-later
package lutrin.convention.mupdf

/**
 * Binary resources that MuPDF's `make generate` turns into C sources with `hexdump.sh`,
 * as paths relative to the MuPDF source tree (patterns in Ant / glob syntax).
 *
 * Mirrors `FONT_BIN` and `HYPH_BIN` of `libmupdf/Makefile` (MuPDF 1.28.6) when no `TOFU*` or
 * `FZ_ENABLE_HYPHEN*` flag is set, which is how the build used to call `make generate`. Check
 * this list again when MuPDF is updated.
 *
 * The other `generate` targets of the Makefile (ICC profiles, CMaps, `util.js.h`,
 * `css-properties.h`) write headers that MuPDF commits under `source/`: they are used as
 * checked out and never regenerated (ADR 0013).
 */
internal val MUPDF_EMBEDDED_RESOURCES: List<String> =
    listOf(
        "resources/fonts/urw/*.cff",
        "resources/fonts/han/*.ttc",
        "resources/fonts/droid/DroidSansFallbackFull.ttf",
        "resources/fonts/droid/DroidSansFallback.ttf",
        "resources/fonts/noto/*.otf",
        "resources/fonts/noto/*.ttf",
        "resources/fonts/sil/*.cff",
        "resources/hyphen/hyph-all.zip",
    )

/** Directory of the MuPDF source tree where the generated C sources go, as in the Makefile. */
internal const val MUPDF_GENERATED_DIR = "generated"

/**
 * Path of the C source generated for the resource at [resourcePath], both relative to the
 * MuPDF source tree, with `/` separators: `resources/hyphen/hyph-all.zip` gives
 * `resources/hyphen/hyph-all.zip.c`, relative to [MUPDF_GENERATED_DIR] (Makefile rule
 * `generated/%.zip.c : %.zip`).
 *
 * @throws IllegalArgumentException if [resourcePath] is blank or absolute.
 */
internal fun generatedSourcePath(resourcePath: String): String {
    require(resourcePath.isNotBlank()) { "Resource path is blank" }
    require(!resourcePath.startsWith("/")) { "Resource path must be relative: $resourcePath" }
    return "$resourcePath.c"
}
