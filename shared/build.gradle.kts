import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kover)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    // Apple targets share one Shared.framework per platform: the Swift-facing API is identical on
    // iOS and macOS (Apple Silicon only, Intel Macs are not supported).
    listOf(
        iosArm64(),
        iosSimulatorArm64(),
        macosArm64()
    ).forEach { appleTarget ->
        appleTarget.binaries.framework {
            baseName = "Shared"
            // macOS: dynamic, so the framework links its own bundled SQLite and Xcode does not
            // pull the static archive members into the app executable. iOS stays static.
            isStatic = appleTarget.name != "macosArm64"

            // Without export() the modules are linked but their APIs stay invisible to Swift.
            export(projects.core.common)
            export(projects.domain)
            export(projects.data)
            export(projects.services)
            export(projects.presentation)
        }
    }

    jvm("desktop")

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            implementation(libs.napier)
            api(projects.core.common)
            api(projects.domain)
            api(projects.data)
            api(projects.services)
            api(projects.presentation)
        }

        commonTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        androidMain.dependencies {
        }

        iosMain.dependencies {
        }

        macosMain.dependencies {
        }

        val desktopMain by getting {
            dependencies {
            }
        }
    }
}

android {
    namespace = "de.fampopprol.dhbwhorb.shared"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

// ─── Kover — same variant as :composeApp so the aggregated report covers this module ─────────
kover {
    currentProject {
        createVariant("kmpCoverage") {
            addWithDependencies("debug")
            addWithDependencies("desktop")
        }
    }
}
