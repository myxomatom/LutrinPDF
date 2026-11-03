// SPDX-License-Identifier: GPL-3.0-or-later
import com.android.build.api.dsl.LibraryExtension
import lutrin.convention.configureAndroidCommon
import lutrin.convention.configureQuality
import lutrin.convention.libs
import lutrin.convention.pluginId
import lutrin.convention.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/** Convention for Android library modules (`core:*` Android modules and, through the feature convention, `feature:*`). */
class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("android-library"))
            extensions.configure<LibraryExtension> {
                configureAndroidCommon(this)
                testOptions.targetSdk = libs.version("targetSdk").toInt()
                lint.targetSdk = libs.version("targetSdk").toInt()
            }
            configureQuality()
        }
    }
}
