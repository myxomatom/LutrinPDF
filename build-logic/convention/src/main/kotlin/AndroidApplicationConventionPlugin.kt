// SPDX-License-Identifier: GPL-3.0-or-later
import app.cash.licensee.LicenseeExtension
import com.android.build.api.artifact.SingleArtifact
import com.android.build.api.dsl.ApplicationExtension
import com.android.build.api.dsl.ManagedVirtualDevice
import com.android.build.api.variant.ApplicationAndroidComponentsExtension
import lutrin.build.configureAndroidCommon
import lutrin.build.configureQuality
import lutrin.build.libs
import lutrin.build.permissions.VerifyPermissionsTask
import lutrin.build.pluginId
import lutrin.build.version
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.create
import org.gradle.kotlin.dsl.register

/**
 * Convention for the `:app` module: Android application, quality tools, dependency license
 * allow-list (Licensee), forbidden-permission check and managed emulators for instrumented tests.
 */
class AndroidApplicationConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply(libs.pluginId("android-application"))
            pluginManager.apply(libs.pluginId("licensee"))
            extensions.configure<ApplicationExtension> {
                configureAndroidCommon(this)
                defaultConfig.targetSdk = libs.version("targetSdk").toInt()
                configureManagedDevices(this)
            }
            configureLicensee()
            registerPermissionChecks()
            configureQuality()
        }
    }

    /** Emulators run by `./gradlew ciGroupDebugAndroidTest`: `minSdk` and the latest API (`AGENTS.md` §7). */
    private fun Project.configureManagedDevices(extension: ApplicationExtension) {
        val devices = extension.testOptions.managedDevices
        val minApi =
            devices.allDevices.create<ManagedVirtualDevice>("api${libs.version("minSdk")}") {
                device = MANAGED_DEVICE_PROFILE
                apiLevel = libs.version("minSdk").toInt()
                systemImageSource = "aosp"
            }
        val latestApi =
            devices.allDevices.create<ManagedVirtualDevice>("api${libs.version("targetSdk")}") {
                device = MANAGED_DEVICE_PROFILE
                apiLevel = libs.version("targetSdk").toInt()
                systemImageSource = "aosp-atd"
            }
        devices.groups.create("ci") {
            targetDevices.add(minApi)
            targetDevices.add(latestApi)
        }
    }

    /** Only licenses compatible with GPL v3 and AGPL v3 are accepted (`SPEC.md` §7, ADR 0003). */
    private fun Project.configureLicensee() {
        extensions.configure<LicenseeExtension> {
            ALLOWED_LICENSES.forEach { allow(it) }
        }
    }

    private fun Project.registerPermissionChecks() {
        extensions.configure<ApplicationAndroidComponentsExtension> {
            onVariants(selector().withBuildType("release")) { variant ->
                val taskName = "verify${variant.name.replaceFirstChar(Char::titlecase)}Permissions"
                val verify =
                    tasks.register<VerifyPermissionsTask>(taskName) {
                        group = "verification"
                        description = "Fails if the ${variant.name} merged manifest requests a forbidden permission."
                        mergedManifest.set(variant.artifacts.get(SingleArtifact.MERGED_MANIFEST))
                        report.set(layout.buildDirectory.file("reports/permissions/${variant.name}.txt"))
                    }
                tasks.named("check").configure { dependsOn(verify) }
            }
        }
    }

    private companion object {
        const val MANAGED_DEVICE_PROFILE = "Pixel 6"
        val ALLOWED_LICENSES = listOf("Apache-2.0", "MIT", "BSD-2-Clause", "BSD-3-Clause")
    }
}
