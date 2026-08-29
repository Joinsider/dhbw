import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    alias(libs.plugins.kover)
    alias(libs.plugins.ksp)
    alias(libs.plugins.androidx.room)
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
            api(libs.androidx.room.runtime)
            implementation(libs.androidx.sqlite.bundled)
            api(libs.ktor.client.core)
            implementation(libs.kotlinx.serialization.json)
            implementation(libs.napier)
            api(libs.kotlinx.datetime)
        }

        commonTest.dependencies {
            implementation(projects.core.testing)
            implementation(libs.kotlin.test)
            implementation(libs.kotlinx.coroutines.test)
            implementation(libs.ktor.client.mock)
        }

        androidMain.dependencies {
            implementation(libs.koin.android)
            implementation(libs.androidx.security.crypto)
            implementation(libs.androidx.core.ktx)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.ktor.client.okhttp)
            implementation(libs.okhttp)
            implementation(libs.okhttp.dnsoverhttps)
        }

        iosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        macosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        val desktopMain by getting {
            dependencies {
            implementation(libs.java.keyring)
            implementation(libs.ktor.client.cio)
            implementation(libs.ktor.client.okhttp)
            }
        }

        val desktopTest by getting {
            dependencies {
                // MigrationTestHelper reads the schema exports in data/schemas/ and opens real
                // database files — the migration guard cannot run against an in-memory database.
                implementation(libs.androidx.room.testing)
                implementation(libs.androidx.sqlite.bundled)
            }
        }
    }
}

android {
    namespace = "de.fampopprol.dhbwhorb.data"
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

room {
    // Plain interpolation on purpose: the escaped form used to make the plugin write into a
    // directory literally named `$projectDir`, so `data/schemas/` silently stopped being
    // updated — and a migration gate reading a frozen export is worth nothing.
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    add("kspAndroid", libs.androidx.room.compiler)
    add("kspIosSimulatorArm64", libs.androidx.room.compiler)
    add("kspIosArm64", libs.androidx.room.compiler)
    add("kspMacosArm64", libs.androidx.room.compiler)
    add("kspMacosX64", libs.androidx.room.compiler)
    add("kspDesktop", libs.androidx.room.compiler)
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
