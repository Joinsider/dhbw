import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kover)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
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
            api(projects.services)
            // Temporary: the ViewModels still hold state in Compose's `mutableStateOf`.
            // P4 replaces that with StateFlow, after which this dependency is removed and
            // the module becomes usable from Swift.
            implementation(compose.runtime)
            api(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.napier)
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
    namespace = "de.fampopprol.dhbwhorb.presentation"
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
