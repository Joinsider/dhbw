import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kover)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    iosArm64()
    iosSimulatorArm64()

    macosArm64()
    macosX64()

    jvm("desktop")

    applyDefaultHierarchyTemplate()

    sourceSets {
        commonMain.dependencies {
            api(projects.core.common)
            api(projects.domain)
            api(projects.data)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.napier)
        }

        commonTest.dependencies {
            implementation(projects.core.testing)
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
        }

        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.androidx.work.runtime.ktx)
            implementation(libs.androidx.core.ktx)
        }

        iosMain.dependencies {
        }

        // Desktop (JVM) and macOS (Native) share the exact same coroutine-based scheduler — no
        // platform API on either side — so it lives once here instead of twice, byte-for-byte,
        // per target (see the S1192 duplication SonarCloud flagged on the old copies).
        val desktopAndMacosMain by creating {
            dependsOn(commonMain.get())
        }

        macosMain {
            dependsOn(desktopAndMacosMain)
        }

        val desktopMain by getting {
            dependsOn(desktopAndMacosMain)
            dependencies {
            }
        }
    }
}

android {
    namespace = "de.fampopprol.dhbwhorb.services"
    compileSdk = libs.versions.android.compileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.android.minSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    // Napier logs through android.util.Log, which is a stub in a unit test — without this every
    // test that logs dies with "Method println in android.util.Log not mocked".
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
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
