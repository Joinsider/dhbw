import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

/**
 * Test helpers shared by every module's tests: repository fakes, in-memory DAOs, a Koin graph
 * built from them.
 *
 * A module of its own rather than `commonTest` somewhere, because a test source set is not visible
 * to another module — which is why all the tests used to live in `:composeApp` in the first place.
 * Nothing in production depends on this; it is only ever pulled in from a `commonTest`.
 *
 * No Kover variant: coverage of the fakes is not a number anybody wants.
 */
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
            // api, not implementation: a test that pulls in a fake also needs the interface it
            // fakes, the entities it builds and the stores it drives.
            api(projects.core.common)
            api(projects.domain)
            api(projects.data)
            api(projects.services)
            api(projects.presentation)
            api(libs.kotlin.test)
            api(libs.kotlinx.coroutines.test)
            api(libs.kotlinx.datetime)
            api(libs.ktor.client.core)
            api(libs.ktor.client.mock)
            implementation(libs.napier)
        }
    }
}

android {
    namespace = "de.fampopprol.dhbwhorb.core.testing"
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
