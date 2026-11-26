// SPDX-License-Identifier: GPL-3.0-or-later
package io.github.myxomatom.lutrin.feature.about

import java.io.IOException

/** What `aboutlibraries.json` looks like, with three components given out of order. */
internal const val SAMPLE_JSON =
    """
{
  "libraries": [
    {
      "uniqueId": "org.example:zeta",
      "name": "Zeta",
      "artifactVersion": "2.0",
      "description": "The last library.",
      "website": "https://example.org/zeta",
      "developers": [{"name": "Ada"}, {"name": "Ada"}, {"name": "Grace"}],
      "licenses": ["Apache-2.0"]
    },
    {
      "uniqueId": "com.artifex.mupdf:mupdf",
      "name": "MuPDF",
      "artifactVersion": "1.28.6",
      "licenses": ["AGPL-3.0-or-later"]
    },
    {
      "uniqueId": "org.example:alpha",
      "name": "alpha",
      "licenses": ["Apache-2.0", "MIT-alpha"]
    },
    {
      "uniqueId": "org.example:zeta",
      "name": "Zeta again",
      "licenses": ["Apache-2.0"]
    }
  ],
  "licenses": {
    "Apache-2.0": {"hash": "Apache-2.0", "name": "Apache-2.0", "content": "Apache licence text"},
    "AGPL-3.0-or-later": {"hash": "AGPL-3.0-or-later", "name": "AGPL-3.0-or-later", "content": "GNU AFFERO text"},
    "MIT-alpha": {"hash": "MIT-alpha", "name": "MIT", "content": ""}
  }
}
"""

/** A [LicensesSource] returning [json], or failing like an unreadable resource. */
internal class FakeLicensesSource(private val json: String? = SAMPLE_JSON) : LicensesSource {
    /** How many times the source was read. */
    var reads = 0
        private set

    override fun readJson(): String {
        reads++
        return json ?: throw IOException("resource missing")
    }
}

/** A component with sensible defaults, for the screen tests. */
internal fun component(
    name: String,
    licenses: List<LicenseText> = listOf(LicenseText("Apache-2.0", "Apache licence text")),
    id: String = "org.example:${name.lowercase()}",
    version: String? = null,
    authors: List<String> = emptyList(),
) = LicensedComponent(
    id = id,
    name = name,
    version = version,
    description = null,
    website = null,
    authors = authors,
    licenses = licenses,
)
