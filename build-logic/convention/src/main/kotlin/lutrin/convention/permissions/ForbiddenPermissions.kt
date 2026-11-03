// SPDX-License-Identifier: GPL-3.0-or-later
package lutrin.convention.permissions

import java.io.File
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

/**
 * Permissions that must never appear in the merged manifest of a release build
 * (`SPEC.md` §1, `AGENTS.md` §1 and §9): network access and broad storage access.
 */
val FORBIDDEN_PERMISSIONS: Set<String> =
    setOf(
        "android.permission.INTERNET",
        "android.permission.ACCESS_NETWORK_STATE",
        "android.permission.CHANGE_NETWORK_STATE",
        "android.permission.ACCESS_WIFI_STATE",
        "android.permission.CHANGE_WIFI_STATE",
        "android.permission.MANAGE_EXTERNAL_STORAGE",
        "android.permission.READ_EXTERNAL_STORAGE",
        "android.permission.WRITE_EXTERNAL_STORAGE",
    )

private const val ANDROID_NAMESPACE = "http://schemas.android.com/apk/res/android"
private val PERMISSION_TAGS = listOf("uses-permission", "uses-permission-sdk-23")

/**
 * Lists the forbidden permissions requested by an Android manifest.
 *
 * @param manifest an `AndroidManifest.xml` file (typically the merged manifest of a variant).
 * @param forbidden the permission names to look for.
 * @return the forbidden permissions found, sorted, without duplicates; empty when the manifest is clean.
 * @throws org.xml.sax.SAXException if the manifest is not well-formed XML.
 */
fun findForbiddenPermissions(manifest: File, forbidden: Set<String> = FORBIDDEN_PERMISSIONS): List<String> {
    val factory =
        DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            // The manifest is a build output, but never resolve external entities anyway.
            setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        }
    val document = factory.newDocumentBuilder().parse(manifest)
    return PERMISSION_TAGS
        .flatMap { tag -> document.getElementsByTagName(tag).elements() }
        .map { it.getAttributeNS(ANDROID_NAMESPACE, "name") }
        .filter { it in forbidden }
        .distinct()
        .sorted()
}

private fun org.w3c.dom.NodeList.elements(): List<Element> = (0 until length).map {
    item(it)
}.filterIsInstance<Element>()
