import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidKotlinMultiplatformLibrary)
    alias(libs.plugins.kover)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    android {
        namespace = "de.fampopprol.dhbwhorb.services"
        compileSdk = libs.versions.android.compileSdk.get().toInt()
        minSdk = libs.versions.android.minSdk.get().toInt()

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }

        // The notification icon (R.drawable.ic_school) is an Android resource of this module; the
        // KMP library plugin compiles resources only when asked to.
        androidResources {
            enable = true
        }

        // Napier logs through android.util.Log, which is a stub in a host test — without this every
        // test that logs dies with "Method println in android.util.Log not mocked".
        withHostTest {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
        }
    }

    iosArm64()
    iosSimulatorArm64()

    macosArm64()

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

        getByName("androidHostTest").dependencies {
            // BroadcastReceiver.goAsync()'s PendingResult only behaves like the real thing under
            // Robolectric's shadow — a plain JVM test sees the unimplemented android.jar stub.
            implementation(libs.robolectric)
            implementation(libs.androidx.test.core)
        }

        iosMain.dependencies {
        }

        // Desktop (JVM) keeps the coroutine loop (desktopMain); macOS schedules its checks with
        // NSBackgroundActivityScheduler (macosMain). Nothing is shared between the two anymore.
    }
}

// ─── Kover — same variant as :composeApp so the aggregated report covers this module ─────────
kover {
    currentProject {
        createVariant("kmpCoverage") {
            addWithDependencies("android")
            addWithDependencies("desktop")
        }
    }
}

dependencies {
    constraints {
        add("androidHostTestImplementation", "org.bouncycastle:bcprov-jdk18on:1.87")
    }
}
