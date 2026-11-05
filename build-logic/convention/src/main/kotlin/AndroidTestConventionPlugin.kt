// SPDX-License-Identifier: GPL-3.0-or-later
import com.android.build.api.dsl.TestExtension
import lutrin.convention.configureAndroidCommon
import lutrin.convention.configureStaticAnalysis
import lutrin.convention.libs
import lutrin.convention.pluginId
import lutrin.convention.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Convention for Android test modules (`com.android.test`), such as `:benchmark`: same SDK
 * levels and compiler settings as the app, with ktlint and detekt. No coverage nor Dokka:
 * such a module only holds tests run against another module's APK (and AGP runs no Android
 * Lint on it).
 */
class AndroidTestConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("android-test"))
            extensions.configure<TestExtension> {
                configureAndroidCommon(this)
                defaultConfig.targetSdk = libs.version("targetSdk").toInt()
            }
            configureStaticAnalysis()
        }
    }
}
