// SPDX-License-Identifier: GPL-3.0-or-later
//
// MuPDF (Artifex, AGPL-3.0-or-later), built from the pinned Git submodule libmupdf/ (ADR 0001,
// ADR 0007). Third-party code: our quality rules (ktlint, detekt, Lint, Kover, Dokka) do not
// apply here. Only core:pdf may depend on this module.
plugins {
    alias(libs.plugins.android.library)
}

val mupdfDir = layout.projectDirectory.dir("libmupdf")
val buildJobs = Runtime.getRuntime().availableProcessors()
val isWindowsHost = providers.systemProperty("os.name").get().startsWith("Windows")

android {
    namespace = "io.github.myxomatom.lutrin.thirdparty.mupdf"
    compileSdk = libs.versions.compileSdk.get().toInt()
    ndkVersion = libs.versions.ndk.get()

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        consumerProguardFiles("consumer-rules.pro")
        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a", "x86_64")
        }
        externalNativeBuild {
            ndkBuild {
                arguments += "-j$buildJobs"
                // Windows limits a command line to 32 767 characters; the archive command of
                // libmupdf_core.a lists hundreds of objects by absolute path and fails with
                // "make (e=87)". Short commands pass the object lists through response files.
                if (isWindowsHost) {
                    arguments += "APP_SHORT_COMMANDS=true"
                }
            }
        }
    }

    sourceSets {
        named("main") {
            // Java bindings ("fitz") shipped with MuPDF; the JNI side is in platform/java/mupdf_native.c.
            java.directories.add(mupdfDir.dir("platform/java/src").asFile.path)
        }
    }

    externalNativeBuild {
        ndkBuild {
            path = mupdfDir.file("platform/java/Android.mk").asFile
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

// The submodule must be checked out at the version declared in libs.versions.toml: the app
// publishes the exact MuPDF version it ships (AGPL, SPEC.md §7).
val verifyMupdfVersion =
    tasks.register("verifyMupdfVersion") {
        description = "Fails if the MuPDF submodule is not at the version of libs.versions.toml."
        val versionHeader = mupdfDir.file("include/mupdf/fitz/version.h")
        val expected = libs.versions.mupdf.get()
        inputs.file(versionHeader)
        inputs.property("expected", expected)
        doLast {
            val found = Regex("#define FZ_VERSION \"([^\"]+)\"").find(versionHeader.asFile.readText())?.groupValues?.get(1)
            check(found == expected) {
                "MuPDF submodule is at version $found, expected $expected. Run ./scripts/init-mupdf.sh, or update both."
            }
        }
    }

// MuPDF embeds fonts, CMaps and ICC profiles as generated C sources (generated/, ignored by its Git).
val generateMupdfResources =
    tasks.register<Exec>("generateMupdfResources") {
        description = "Generates the C sources of the resources embedded in MuPDF."
        workingDir = mupdfDir.asFile
        commandLine("make", "-j$buildJobs", "generate")
        outputs.dir(mupdfDir.dir("generated"))
    }
tasks.named("preBuild") { dependsOn(generateMupdfResources, verifyMupdfVersion) }

// Desktop (Linux x86_64) build of the same JNI library, so that core:pdf can test the engine
// on the JVM without an emulator.
val hostJniDir = layout.buildDirectory.dir("host-jni")
val buildMupdfHostJni =
    tasks.register<Exec>("buildMupdfHostJni") {
        description = "Builds libmupdf_java64.so for the build machine, used by JVM unit tests."
        dependsOn(generateMupdfResources, verifyMupdfVersion)
        inputs.file("build-host-jni.sh")
        inputs.dir(mupdfDir.dir("source"))
        inputs.dir(mupdfDir.dir("include"))
        inputs.file(mupdfDir.file("platform/java/mupdf_native.c"))
        outputs.dir(hostJniDir)
        environment("JAVA_HOME", providers.systemProperty("java.home").get())
        commandLine("sh", "build-host-jni.sh", hostJniDir.get().asFile.absolutePath, buildJobs.toString())
    }

/** Directory containing the host JNI library, for `java.library.path` in tests. */
extra["hostJniDir"] = hostJniDir
extra["buildMupdfHostJni"] = buildMupdfHostJni

// Third-party sources: audited upstream, not by our Lint configuration.
tasks.matching { it.name.startsWith("lint") }.configureEach { enabled = false }
