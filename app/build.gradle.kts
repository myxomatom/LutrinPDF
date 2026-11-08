// SPDX-License-Identifier: GPL-3.0-or-later
plugins {
    alias(libs.plugins.lutrin.android.application)
    alias(libs.plugins.lutrin.android.compose)
    alias(libs.plugins.lutrin.hilt)
    alias(libs.plugins.lutrin.android.robolectric)
    alias(libs.plugins.roborazzi)
    alias(libs.plugins.baselineprofile)
}

/** Version code of the release; each per-ABI APK derives its own from it (see below). */
val appVersionCode = 1

android {
    namespace = "io.github.myxomatom.lutrin"

    defaultConfig {
        applicationId = "io.github.myxomatom.lutrin"
        versionCode = appVersionCode
        versionName = "0.1.0"
    }

    // One APK per ABI, each under the 15 MB budget of SPEC.md §3 (the MuPDF native library
    // is most of the size). App bundles for Google Play are split by the store itself.
    splits {
        abi {
            isEnable = true
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64")
            isUniversalApk = false
        }
    }

    // No Google-encrypted dependency metadata in the APK or bundle: F-Droid flags it, and it
    // would make the build harder to reproduce.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    buildTypes {
        debug {
            // Lets a debug build live next to the published app on the same device.
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}

// Distinct version codes per ABI, as F-Droid and Google Play require for split APKs:
// versionCode * 10 + ABI rank (1 = armeabi-v7a, 2 = arm64-v8a, 3 = x86_64).
val abiVersionCodes = mapOf("armeabi-v7a" to 1, "arm64-v8a" to 2, "x86_64" to 3)
androidComponents {
    onVariants { variant ->
        variant.outputs.forEach { output ->
            val abi = output.filters.find {
                it.filterType ==
                    com.android.build.api.variant.FilterConfiguration.FilterType.ABI
            }
            val rank = abi?.let { abiVersionCodes[it.identifier] }
            if (rank != null) output.versionCode.set(appVersionCode * 10 + rank)
        }
    }
}

roborazzi {
    outputDir.set(file("src/test/screenshots"))
}

// Baseline Profile (ADR 0012): generated on a device by the maintainer with
// ./gradlew :app:generateBaselineProfile, then committed under src/release/generated/.
// The build never generates it by itself and works without it.
baselineProfile {
    automaticGenerationDuringBuild = false
    saveInSrc = true
}

/**
 * Build types added by the Baseline Profile plugin for :benchmark: benchmarkRelease
 * (measured, minified like release) and nonMinifiedRelease (profile generation), both
 * profileable, not debuggable and signed with the debug key by the plugin.
 * Only they get the benchmark entry point; the release build never contains it.
 */
val benchmarkBuildTypes = setOf("benchmarkRelease", "nonMinifiedRelease")

/** Copies the large test document of core:testing into the assets of the benchmark builds. */
abstract class CopyBenchmarkDocument : DefaultTask() {
    /** The PDF to bundle. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val document: RegularFileProperty

    /** Asset directory added to the variant; the file lands in its pdfs/ folder. */
    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    /** Replaces the directory content with the document. */
    @TaskAction
    fun copy() {
        val target = outputDir.get().dir("pdfs").asFile
        target.deleteRecursively()
        target.mkdirs()
        document.get().asFile.copyTo(target.resolve(document.get().asFile.name))
    }
}

val benchmarkDocument =
    tasks.register<CopyBenchmarkDocument>("copyBenchmarkDocument") {
        document.set(rootProject.layout.projectDirectory.file("core/testing/src/main/assets/pdfs/large_600pages.pdf"))
    }

/** Fails if the benchmark entry point reached a manifest it must never be in (ADR 0012). */
@CacheableTask
abstract class VerifyNoBenchmarkEntryPoint : DefaultTask() {
    /** Merged manifest of the release variant. */
    @get:InputFile
    @get:PathSensitive(PathSensitivity.NONE)
    abstract val mergedManifest: RegularFileProperty

    /** Written on success, so that the task can be up to date. */
    @get:OutputFile
    abstract val report: RegularFileProperty

    /** Looks for the activity name in the merged manifest. */
    @TaskAction
    fun verify() {
        if ("BenchmarkReaderActivity" in mergedManifest.get().asFile.readText()) {
            throw GradleException("The benchmark entry point must never be part of a release build (ADR 0012).")
        }
        report.get().asFile.writeText("No benchmark entry point.\n")
    }
}

androidComponents {
    onVariants(selector().withBuildType("release")) { variant ->
        val taskName = "verify${variant.name.replaceFirstChar(Char::titlecase)}NoBenchmarkEntryPoint"
        val verify =
            tasks.register<VerifyNoBenchmarkEntryPoint>(taskName) {
                group = "verification"
                description = "Fails if the ${variant.name} merged manifest declares the benchmark entry point."
                mergedManifest.set(variant.artifacts.get(com.android.build.api.artifact.SingleArtifact.MERGED_MANIFEST))
                report.set(layout.buildDirectory.file("reports/benchmark-entry-point/${variant.name}.txt"))
            }
        tasks.named("check").configure { dependsOn(verify) }
    }
    onVariants { variant ->
        if (variant.buildType in benchmarkBuildTypes) {
            variant.sources.kotlin?.addStaticSourceDirectory("src/benchmark/kotlin")
            variant.sources.manifests.addStaticManifestFile("src/benchmark/AndroidManifest.xml")
            variant.sources.assets?.addGeneratedSourceDirectory(benchmarkDocument, CopyBenchmarkDocument::outputDir)
        }
    }
}

dependencies {
    implementation(projects.core.ui)
    implementation(projects.core.domain)
    implementation(projects.core.data)
    implementation(projects.core.pdf)
    implementation(projects.feature.library)
    implementation(projects.feature.reader)
    implementation(projects.feature.settings)
    implementation(projects.feature.tools)
    implementation(projects.feature.assistant)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    // Installs the Baseline Profile on devices without Google Play (F-Droid, sideloading).
    implementation(libs.androidx.profileinstaller)
    baselineProfile(projects.benchmark)

    // Leak detection in debug builds only (AGENTS.md §8); never in release.
    debugImplementation(libs.leakcanary.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.roborazzi.junit.rule)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4.accessibility)
}
