// SPDX-License-Identifier: GPL-3.0-or-later
package lutrin.convention

import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.provider.Provider
import org.gradle.kotlin.dsl.getByType

/** The `libs` version catalog (`gradle/libs.versions.toml`), seen from a convention plugin. */
internal val Project.libs: VersionCatalog
    get() = extensions.getByType<VersionCatalogsExtension>().named("libs")

/** Returns the catalog version named [alias]; fails the build if it does not exist. */
internal fun VersionCatalog.version(alias: String): String = findVersion(alias).get().requiredVersion

/** Returns the catalog library named [alias]; fails the build if it does not exist. */
internal fun VersionCatalog.library(alias: String): Provider<MinimalExternalModuleDependency> = findLibrary(alias).get()

/** Returns the catalog plugin id named [alias]; fails the build if it does not exist. */
internal fun VersionCatalog.pluginId(alias: String): String = findPlugin(alias).get().get().pluginId
