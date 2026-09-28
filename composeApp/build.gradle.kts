import org.jetbrains.compose.ExperimentalComposeLibrary
import org.jetbrains.compose.desktop.application.dsl.TargetFormat
import org.jetbrains.compose.desktop.application.tasks.AbstractJPackageTask
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.composeHotReload)
    alias(libs.plugins.kover)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    androidTarget {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_11)
        }
    }

    // No iOS targets since P7: the iOS app is SwiftUI and links `Shared.framework`. Compose
    // here would only be dead weight in a binary nothing imports — and it would quietly re-open
    // the door for a Compose dependency to reach `:presentation`.

    macosArm64()
    macosX64()

    jvm("desktop")

    // Configure source set hierarchy
    applyDefaultHierarchyTemplate()

    sourceSets {
        androidMain.dependencies {
            implementation(compose.preview)
            implementation(libs.androidx.activity.compose)
            implementation(libs.androidx.security.crypto)
            implementation(libs.androidx.work.runtime.ktx)
            implementation(libs.glance.appwidget)
            implementation(libs.glance.material3)
            implementation(libs.ktor.client.okhttp)
            // Ensure OkHttp core is present for DoH
            implementation(libs.okhttp)
            // Use OkHttp DNS-over-HTTPS for fallback
            implementation(libs.okhttp.dnsoverhttps)
            // Foldable device support via WindowInfoTracker and FoldingFeature
            implementation(libs.koin.android)
            implementation(libs.androidx.window)
        }
        commonMain.dependencies {
            api(projects.core.common)
            api(projects.domain)
            api(projects.data)
            api(projects.services)
            api(projects.presentation)
            api(projects.shared)
            implementation(libs.koin.compose)
            implementation(libs.koin.compose.viewmodel)

            implementation(compose.runtime)
            implementation(compose.foundation)
            // Pinned to an alpha on purpose, re-checked in P9.
            //
            // The UI is built on Material 3 Expressive: `MaterialExpressiveTheme` in Theme.kt, the
            // expressive type scale in Type.kt, `LoadingIndicator` on the login and documents
            // screens, `ButtonGroupDefaults` in the design settings. On the newest stable release
            // (1.9.0) `ExperimentalMaterial3ExpressiveApi` is internal and `LoadingIndicator` does
            // not exist — tried in P9, 45 compile errors.
            //
            // The old comment here warned against "upgrading to stable (1.10.0)". There is no such
            // release: the artefact goes 1.9.0 → 1.10.0-alphaNN → 1.11.0-alphaNN. alpha05 is the
            // last of the 1.10 line and matches the Compose Multiplatform runtime; the 1.11 and
            // 1.12 alphas run ahead of it, so moving there is a runtime bump, not a version bump.
            //
            // Since P7 this only ships to Android, Desktop and macOS — the iOS app is SwiftUI and
            // never sees Compose.
            implementation(libs.compose.material3)
            implementation(compose.ui)
            implementation(compose.components.resources)
            implementation(compose.components.uiToolingPreview)
            // androidx.compose.ui.tooling.preview.Preview — the non-deprecated replacement for
            // org.jetbrains.compose.ui.tooling.preview.Preview — ships to Android and Desktop as a
            // side effect of other dependencies, but macOS/iOS commonMain code needs it declared
            // explicitly to get the klib for those targets.
            implementation(compose.preview)
            implementation(libs.androidx.lifecycle.viewmodelCompose)
            implementation(libs.androidx.lifecycle.runtimeCompose)
            implementation(libs.androidx.navigation.compose)
            implementation(libs.kotlinx.datetime)
            implementation(libs.material.icons.extended)
            implementation(libs.napier)
            implementation(libs.kotlinx.datetime.v040)
            implementation(libs.kotlinx.serialization.json)
            implementation("com.materialkolor:material-kolor:4.0.5") {
                exclude(group = "org.jetbrains.compose.material3", module = "material3")
            }
        }

        commonTest.dependencies {
            // Only the tests that belong to this module are left: the Compose UI, navigation and
            // the Koin graph as the app assembles it. Everything below the UI moved into its own
            // module's commonTest in P9.
            implementation(projects.core.testing)
            implementation(libs.androidx.sqlite.bundled)
            implementation(libs.ktor.client.core)
            implementation(libs.kotlin.test)
            @OptIn(ExperimentalComposeLibrary::class)
            implementation(compose.uiTest)
            implementation(libs.ktor.client.mock)
            implementation(libs.kotlinx.coroutines.test)
        }

        androidUnitTest.dependencies {
            implementation(libs.robolectric)
        }

        // The instrumented suite had no dependencies at all, so it had not compiled since the
        // module split — which is how eight tests that assert nothing survived unnoticed. It runs
        // on a device (`:composeApp:connectedDebugAndroidTest`) and is not part of the gate.
        androidInstrumentedTest.dependencies {
            implementation(libs.kotlin.test)
            implementation(libs.junit)
            implementation(libs.androidx.testExt.junit)
            implementation(libs.androidx.test.core)
            implementation(libs.androidx.test.runner)
            implementation(libs.androidx.work.testing)
            implementation(libs.androidx.work.runtime.ktx)
            implementation(libs.glance.appwidget)
        }

        macosMain.dependencies {
            implementation(libs.ktor.client.darwin)
        }

        val desktopTest by getting {
            dependencies {
                implementation(libs.koin.test)
                // MigrationTestHelper reads the schema exports in data/schemas/ and opens real
                // database files — the migration guard cannot run against an in-memory database.
                implementation(libs.androidx.room.testing)
            }
        }

        val desktopMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.kotlinx.coroutinesSwing)
                implementation(libs.java.keyring)
                implementation(libs.ktor.client.cio)
                implementation(libs.ktor.client.okhttp)
            }
        }
    }
}

android {
    namespace = "de.fampopprol.dhbwhorb"
    compileSdk = libs.versions.android.compileSdk.get().toInt()
    buildToolsVersion = "36.0.0"

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    defaultConfig {
        applicationId = "de.fampopprol.dhbwhorb"
        minSdk = libs.versions.android.minSdk.get().toInt()
        targetSdk = libs.versions.android.targetSdk.get().toInt()
        versionCode = 28
        versionName = "v3.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        resValue("string", "app_name", "DHBW Horb Studenten App")
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    signingConfigs {
        create("release") {
            storeFile = System.getenv("SIGNING_KEYSTORE_PATH")?.let { file(it) }
            storePassword = System.getenv("SIGNING_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("SIGNING_KEY_ALIAS")
            keyPassword = System.getenv("SIGNING_KEY_PASSWORD")
        }
    }
    buildTypes {
        getByName("release") {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            isReturnDefaultValues = true
            all {
                // Exclude Compose UI tests from Android unit tests
                // These tests work on iOS and JVM but require instrumented tests on Android
                it.exclude(
                    "**/AppTest.class",
                    "**/AppRoutingTest.class",
                    "**/LoginFormTest.class",
                    "**/ui/**/*Test.class",
                    // Renders Compose UI, needs an Android runtime (Build.FINGERPRINT); covered by desktopTest.
                    "**/Phase8StabilityTest.class"
                )
            }
        }
    }
    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    debugImplementation(compose.uiTooling)

    // Aggregate the library modules into this project's coverage report — after the module split
    // the report would otherwise only cover the UI layer.
    kover(projects.core.common)
    kover(projects.domain)
    kover(projects.data)
    kover(projects.services)
    kover(projects.presentation)
}

val javaToolchains = extensions.getByType<JavaToolchainService>()

compose.desktop {
    application {
        mainClass = "de.fampopprol.dhbwhorb.MainKt"

        // jpackage ships only with a full JDK, and by default the packaging tasks inherit
        // whatever JVM the Gradle daemon happens to run on.  That is not dependable:
        // gradle/gradle-daemon-jvm.properties asks for "a JetBrains 21" without naming a
        // specific installation, and the JBR bundled inside Android Studio satisfies that
        // while shipping no jpackage — checkRuntime then fails with "'jpackage' is
        // missing".  Asking for a JetBrains toolchain here does not help, because Gradle
        // resolves that against the same set of installations and favours a matching JVM
        // it is already running on.
        //
        // Temurin is requested instead: Android Studio's JBR cannot satisfy an Adoptium
        // vendor constraint, every Temurin build carries jpackage, the CI runners already
        // install it via setup-java, and the foojay resolver in settings.gradle.kts
        // provisions it anywhere else.  That makes the packaging JDK the same everywhere,
        // whatever JVM the daemon ended up on.
        javaHome = javaToolchains.launcherFor {
            languageVersion.set(JavaLanguageVersion.of(21))
            vendor.set(JvmVendorSpec.AZUL)
        }.get().metadata.installationPath.asFile.absolutePath

        buildTypes.release.proguard {
            isEnabled.set(false)
        }

        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "dhbw-horb-student-app"
            packageVersion = "3.0.2"
            // Avoid SSL trust chain issues in packaged apps caused by missing JDK modules.
            includeAllModules = true
            modules(
                "java.base",
                "java.datatransfer",
                "java.desktop",
                "java.instrument",
                "java.logging",
                "java.management",
                "java.prefs",
                "java.xml",
                "jdk.crypto.ec",
                "jdk.security.auth",
                "jdk.unsupported"
            )

            windows {
                iconFile.set(project.file("icon.ico"))
            }
            macOS {
                iconFile.set(project.file("icon.icns"))
                bundleID = "de.fampopprol.dhbw"

                // Developer ID signing + notarization for distribution outside the App
                // Store.  Every value comes from the environment so that neither the
                // identity nor the app-specific password lands in the repo or shows up in
                // the process list, as it would with -P properties.  When
                // APPLE_SIGN_IDENTITY is unset (Linux/Windows runners, ordinary dev
                // builds) the build stays unsigned and behaves exactly as before.
                val signIdentity = project.providers.environmentVariable("APPLE_SIGN_IDENTITY")
                signing {
                    sign.set(signIdentity.map { it.isNotBlank() }.orElse(false))
                    identity.set(signIdentity)
                }
                notarization {
                    appleID.set(project.providers.environmentVariable("APPLE_ID"))
                    password.set(project.providers.environmentVariable("APPLE_APP_PASSWORD"))
                    teamID.set(project.providers.environmentVariable("APPLE_TEAM_ID"))
                }
                // entitlementsFile is deliberately left unset: the Compose plugin defaults
                // (allow-jit, allow-unsigned-executable-memory, disable-library-validation)
                // are exactly what the JVM needs under the hardened runtime.
                // macOS.entitlements is App Store / sandbox specific and would break a
                // Developer ID build.
                // appStore.set(true)  // only for the Mac App Store route (PKG instead of DMG)
            }
            linux {
                iconFile.set(project.file("icon.png"))
            }
        }
    }
}


// Three gaps the Compose plugin leaves open on the way to a notarized DMG:
//
//  * The app image contains one native library the plugin does not sign.  Its jar
//    signing processor matches only .dylib and .jnilib, while jkeychain ships
//    osxkeychain.so — Apple's notary service rejects the whole submission over it.
//    packaging/macos/sign-jar-natives.sh signs it and re-seals the bundle.
//  * notarizeDmg tickets the disk image only, so an app dragged out of it needs an
//    online Gatekeeper check on first launch.  packaging/macos/notarize-app.sh
//    notarizes and staples the bundle itself; jpackage copies the app image into the
//    DMG without re-signing, so that ticket survives into the disk image.
//  * jpackage signs the .app but never the .dmg container around it, a long-standing
//    JDK limitation, and `stapler` cannot attach a ticket to an unsigned disk image.
//
// Every hook runs after the task that produces its input and before notarizeDmg
// consumes it.  Without APPLE_SIGN_IDENTITY they are no-ops, matching the unsigned
// build path used on the Linux and Windows runners.
tasks.withType<AbstractJPackageTask>().configureEach {
    val format = targetFormat
    if (format != TargetFormat.AppImage && format != TargetFormat.Dmg) return@configureEach

    val signIdentity = project.providers.environmentVariable("APPLE_SIGN_IDENTITY")
    val appleId = project.providers.environmentVariable("APPLE_ID")
    val appPassword = project.providers.environmentVariable("APPLE_APP_PASSWORD")
    val teamId = project.providers.environmentVariable("APPLE_TEAM_ID")
    val outputDir = destinationDir
    val scripts = project.rootProject.layout.projectDirectory.dir("packaging/macos")
    val signJarNatives = scripts.file("sign-jar-natives.sh").asFile
    val notarizeApp = scripts.file("notarize-app.sh").asFile

    doLast {
        val identity = signIdentity.orNull?.takeIf { it.isNotBlank() } ?: return@doLast
        val outputs = outputDir.get().asFile.listFiles().orEmpty()

        // Each step pairs a command with what to feed it on stdin — the app-specific
        // password travels that way so it never lands in the argument list.
        val steps = mutableListOf<Pair<List<String>, String?>>()

        if (format == TargetFormat.AppImage) {
            val app = outputs.single { it.extension == "app" }
            steps += listOf(
                "/bin/bash", signJarNatives.absolutePath, app.absolutePath, identity
            ) to null

            val id = appleId.orNull
            val team = teamId.orNull
            val password = appPassword.orNull
            if (!id.isNullOrBlank() && !team.isNullOrBlank() && !password.isNullOrBlank()) {
                steps += listOf(
                    "/bin/bash", notarizeApp.absolutePath, app.absolutePath, id, team
                ) to password
            } else {
                logger.lifecycle(
                    "Skipping app notarization: APPLE_ID, APPLE_APP_PASSWORD or APPLE_TEAM_ID is unset."
                )
            }
        } else {
            val dmg = outputs.single { it.extension == "dmg" }
            steps += listOf(
                "/usr/bin/codesign", "--force", "--timestamp", "--sign", identity, dmg.absolutePath
            ) to null
            // codesign stays silent on success, so name the artifact explicitly.
            logger.lifecycle("Signing ${dmg.name} with $identity")
        }

        for ((command, stdin) in steps) {
            val process = ProcessBuilder(command).redirectErrorStream(true).start()
            process.outputStream.bufferedWriter().use { writer ->
                if (stdin != null) writer.appendLine(stdin)
            }
            val output = process.inputStream.bufferedReader().readText().trim()
            check(process.waitFor() == 0) { "${command.first()} failed:\n$output" }
            if (output.isNotEmpty()) logger.lifecycle(output)
        }
    }
}

// The Compose notarization task holds an `ascProvider` property deprecated at
// DeprecationLevel.ERROR whose provider always throws when read.  The configuration
// cache serializes the whole settings bean and trips over it, so `notarizeDmg` never
// starts while the cache is enabled (Compose 1.10.3).  The property cannot be
// neutralized from the build script because ERROR deprecation blocks any reference to
// it, so mark the tasks as cache-incompatible instead: Gradle then disables the
// configuration cache for that single run rather than failing the build.
tasks.matching { it.name.startsWith("notarize") }.configureEach {
    notCompatibleWithConfigurationCache(
        "Compose 1.10.3: MacOSNotarizationSettings.ascProvider is not serializable"
    )
}

compose.resources {
    packageOfResClass = "de.fampopprol.dhbwhorb.resources"
    publicResClass = true
    generateResClass = always
}

// ─── Kover — KMP-unified coverage ────────────────────────────────────────────
kover {
    currentProject {
        // Instrument both Android (debug) and Desktop (JVM) compilations.
        // iOS / macOS native targets are skipped because they require an Apple
        // runner and cannot produce JVM bytecode for instrumentation.
        createVariant("kmpCoverage") {
            addWithDependencies("debug")        // Android debug unit tests
            addWithDependencies("desktop")      // Desktop / JVM tests
        }
    }

    reports {
        variant("kmpCoverage") {
            // XML consumed by SonarCloud
            xml {
                onCheck = false
                xmlFile = layout.buildDirectory.file("reports/kover/report.xml")
            }
            // Human-readable HTML report uploaded as a CI artefact
            html {
                onCheck = false
                htmlDir = layout.buildDirectory.dir("reports/kover/html")
            }
        }

        filters {
            excludes {
                // Room-generated DAOs / databases
                packages(
                    "*.generated.*",
                    "*.BuildConfig",
                    "de.fampopprol.dhbwhorb.resources",           // Compose-generated resources
                    "de.fampopprol.dhbwhorb.*_Impl",              // Room _Impl classes
                )
                annotatedBy(
                    "androidx.room.Database",
                    "androidx.room.Dao",
                )
                // Build-config and generated source trees
                classes(
                    "*_Factory",
                    "*_MembersInjector",
                    "*.BuildConfig",
                    "Manifest*",
                    "*.ComposableSingletons*",
                )
            }
        }
    }
}

// Custom fat JAR task - simple and reliable
val packageFatJar by tasks.registering(Jar::class) {
    archiveBaseName.set("dhbw-horb-student-app")
    archiveVersion.set("3.0.2")
    archiveClassifier.set("all")

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    manifest {
        attributes["Main-Class"] = "de.fampopprol.dhbwhorb.MainKt"
    }

    // Get desktop compilation
    val desktopCompilation = kotlin.targets["desktop"].compilations["main"]

    // Include compiled classes and resources
    from(desktopCompilation.output.classesDirs)
    from(desktopCompilation.output.resourcesDir)

    // Include all runtime dependencies
    dependsOn(desktopCompilation.compileAllTaskName)
    from({
        desktopCompilation.runtimeDependencyFiles?.files?.map {
            if (it.isDirectory) it else zipTree(it)
        }
    })
}
