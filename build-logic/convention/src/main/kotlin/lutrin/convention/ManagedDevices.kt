// SPDX-License-Identifier: GPL-3.0-or-later
package lutrin.convention

import com.android.build.api.dsl.CommonExtension
import com.android.build.api.dsl.ManagedVirtualDevice
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create

private const val MANAGED_DEVICE_PROFILE = "Pixel 6"

/**
 * Emulators for instrumented tests: `minSdk` and the latest API (`AGENTS.md` §7). Run a
 * module's tests on both with `./gradlew :module:ciGroupDebugAndroidTest`.
 */
internal fun Project.configureManagedDevices(extension: CommonExtension) {
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
