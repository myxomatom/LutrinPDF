#!/usr/bin/env python3
# SPDX-License-Identifier: GPL-3.0-or-later
"""Writes the hand-made part of the third-party license list (E4, ADR 0030).

AboutLibraries lists what Gradle resolves (Maven artifacts). It cannot see MuPDF, the native
libraries compiled into libmupdf_java.so and the fonts (the hyphenation patterns are compiled
out, ADR 0030). This script writes their description, with the real license texts copied from the MuPDF sources (submodule
third-party/mupdf/libmupdf, see scripts/init-mupdf.sh) and from LICENSES/, into
app/aboutlibraries/libraries/ and app/aboutlibraries/licenses/. Run it again after each update
of MuPDF or of a font, and commit the result. The output is deterministic (sorted keys, no date).

    python3 scripts/generate-licenses-config.py
"""
import json
import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MUPDF = ROOT / "third-party" / "mupdf" / "libmupdf"
THIRD = MUPDF / "thirdparty"
OUT = ROOT / "app" / "aboutlibraries"
MUPDF_VERSION = "1.28.6"
ARTIFEX = {"name": "Artifex Software, Inc.", "organisationUrl": "https://artifex.com"}

BSD_3_CLAUSE = (
    "Copyright 2008 Google Inc. All rights reserved.\n\n"
    "Redistribution and use in source and binary forms, with or without modification, are "
    "permitted provided that the following conditions are met:\n\n"
    "* Redistributions of source code must retain the above copyright notice, this list of "
    "conditions and the following disclaimer.\n"
    "* Redistributions in binary form must reproduce the above copyright notice, this list of "
    "conditions and the following disclaimer in the documentation and/or other materials "
    "provided with the distribution.\n"
    "* Neither the name of Google Inc. nor the names of its contributors may be used to endorse "
    "or promote products derived from this software without specific prior written permission.\n\n"
    'THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS '
    "OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF "
    "MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE "
    "COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, "
    "EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF "
    "SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) "
    "HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR "
    "TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS "
    "SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.\n"
)

def text(path: Path) -> str:
    return path.read_text(encoding="utf-8").replace("\r\n", "\n").strip() + "\n"


def write(directory: str, name: str, data: dict) -> None:
    target = OUT / directory
    target.mkdir(parents=True, exist_ok=True)
    (target / f"{name}.json").write_text(
        json.dumps(data, indent=2, ensure_ascii=False, sort_keys=True) + "\n",
        encoding="utf-8",
        newline="\n",
    )


def license_file(license_id: str, name: str, content: str, url: str = "") -> None:
    write("licenses", license_id, {"hash": license_id, "name": name, "url": url, "content": content})


def library(unique_id, name, version, description, website, developers, licenses) -> None:
    write(
        "libraries",
        re.sub(r"[^a-z0-9]+", "-", unique_id.lower()).strip("-"),
        {
            "uniqueId": unique_id,
            "name": name,
            "artifactVersion": version,
            "description": description,
            "website": website,
            "developers": developers,
            "licenses": licenses,
        },
    )


def ijg_terms() -> str:
    readme = text(THIRD / "libjpeg" / "README")
    match = re.search(r"^LEGAL ISSUES\n=+\n(.*?)\n\nREFERENCES\n", readme, re.S | re.M)
    return match.group(1).strip() + "\n"


def shared_licenses() -> None:
    """Texts of the licenses that Maven artifacts name in their POM without a text."""
    license_file(
        "Apache-2.0", "Apache-2.0", text(THIRD / "gumbo-parser" / "COPYING"), "https://www.apache.org/licenses/LICENSE-2.0"
    )
    license_file("BSD-3-Clause", "BSD-3-Clause", BSD_3_CLAUSE, "https://opensource.org/license/bsd-3-clause")
    license_file(
        "AGPL-3.0-or-later",
        "AGPL-3.0-or-later",
        text(ROOT / "LICENSES" / "AGPL-3.0-or-later.txt"),
        "https://www.gnu.org/licenses/agpl-3.0.html",
    )


def native_licenses() -> None:
    """The file each library ships, with its own copyright notice."""
    license_file("MIT-brotli", "MIT", text(THIRD / "brotli" / "LICENSE"))
    license_file("BSD-cmark-gfm", "BSD-2-Clause and others", text(THIRD / "cmark-gfm" / "COPYING"))
    freetype = text(THIRD / "freetype" / "LICENSE.TXT") + "\n" + text(THIRD / "freetype" / "docs" / "FTL.TXT")
    license_file("FTL-freetype", "FreeType License (FTL) or GPL-2.0-or-later", freetype)
    license_file("MIT-harfbuzz", "MIT (Old MIT)", text(THIRD / "harfbuzz" / "COPYING"))
    license_file("MIT-lcms2", "MIT", text(THIRD / "lcms2" / "LICENSE"))
    license_file("IJG", "IJG License", ijg_terms())
    license_file("ISC-mujs", "ISC", text(THIRD / "mujs" / "COPYING"))
    license_file("BSD-openjpeg", "BSD-2-Clause", text(THIRD / "openjpeg" / "LICENSE"))


def font_licenses() -> None:
    ofl_url = "https://openfontlicense.org"
    license_file("OFL-1.1-URW", "OFL-1.1", text(MUPDF / "resources" / "fonts" / "urw" / "OFL.txt"), ofl_url)
    license_file("OFL-1.1-Fraunces", "OFL-1.1", text(ROOT / "LICENSES" / "OFL-1.1-Fraunces.txt"), ofl_url)


def native_libraries() -> None:
    from_mupdf = "compiled into MuPDF."
    library("com.artifex.mupdf:mupdf", "MuPDF", MUPDF_VERSION,
            "PDF rendering and writing engine, compiled from source into the app.",
            "https://mupdf.com", [ARTIFEX], ["AGPL-3.0-or-later"])
    library("org.brotli:brotli", "Brotli", "", f"Brotli decompression, {from_mupdf}",
            "https://github.com/google/brotli", [{"name": "The Brotli Authors"}], ["MIT-brotli"])
    library("com.github.github:cmark-gfm", "cmark-gfm", "", f"Markdown parser, {from_mupdf}",
            "https://github.com/github/cmark-gfm", [{"name": "John MacFarlane and contributors"}],
            ["BSD-cmark-gfm"])
    library("com.artifex.mupdf:extract", "extract", "", f"Document structure extraction, {from_mupdf}",
            "https://ghostscript.com", [ARTIFEX], ["AGPL-3.0-or-later"])
    library("org.freetype:freetype", "FreeType", "",
            f"Font rendering, {from_mupdf} Used under the GPL-2.0-or-later option.",
            "https://freetype.org", [{"name": "The FreeType Project"}], ["FTL-freetype"])
    library("com.google.gumbo:gumbo-parser", "Gumbo", "", f"HTML parser, {from_mupdf}",
            "https://github.com/google/gumbo-parser", [{"name": "Google Inc."}], ["Apache-2.0"])
    library("org.harfbuzz:harfbuzz", "HarfBuzz", "", f"Complex text shaping, {from_mupdf}",
            "https://harfbuzz.github.io", [{"name": "The HarfBuzz authors"}], ["MIT-harfbuzz"])
    library("com.artifex.mupdf:jbig2dec", "jbig2dec", "", f"JBIG2 image decoder, {from_mupdf}",
            "https://jbig2dec.com", [ARTIFEX], ["AGPL-3.0-or-later"])
    library("com.littlecms:lcms2", "Little CMS 2", "", f"Colour management, {from_mupdf}",
            "https://www.littlecms.com", [{"name": "Marti Maria Saguer"}], ["MIT-lcms2"])
    library("org.ijg:libjpeg", "libjpeg (IJG)", "",
            f"JPEG decoder, {from_mupdf} This software is based in part on the work of the "
            "Independent JPEG Group.",
            "https://www.ijg.org", [{"name": "Independent JPEG Group"}], ["IJG"])
    library("com.artifex.mupdf:mujs", "MuJS", "",
            "JavaScript interpreter required to build MuPDF. Lutrin never runs the JavaScript "
            "of a document.",
            "https://mujs.com", [ARTIFEX], ["ISC-mujs"])
    library("org.openjpeg:openjpeg", "OpenJPEG", "", f"JPEG 2000 decoder, {from_mupdf}",
            "https://www.openjpeg.org",
            [{"name": "Universite catholique de Louvain and contributors"}], ["BSD-openjpeg"])

def font_libraries() -> None:
    library("com.urwpp:urw-base35", "URW++ Base 35 fonts (the 14 standard PDF fonts)", "",
            "Nimbus Roman, Sans and Mono, Standard Symbols, Dingbats and Nimbus Boxes, embedded in MuPDF.",
            "https://github.com/ArtifexSoftware/urw-base35-fonts",
            [{"name": "(URW)++ Design & Development"}], ["OFL-1.1-URW"])
    library("com.undercasetype:fraunces", "Fraunces", "1.000",
            "Font of the titles of Lutrin (SemiBold).", "https://github.com/undercasetype/Fraunces",
            [{"name": "The Fraunces Project Authors"}], ["OFL-1.1-Fraunces"])


def main() -> None:
    shared_licenses()
    native_licenses()
    font_licenses()
    native_libraries()
    font_libraries()


if __name__ == "__main__":
    main()
