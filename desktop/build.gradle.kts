import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(17)
}

val appVersion = providers.gradleProperty("appVersion").get()

// APP_VERSION, from gradle.properties, so the app knows which release it is
val analyticsId = providers.gradleProperty("analyticsMeasurementId").getOrElse("")
val analyticsSecret = providers.gradleProperty("analyticsApiSecret").getOrElse("")

val writeBuildInfo by tasks.registering {
    val out = layout.buildDirectory.dir("generated/buildinfo")
    inputs.property("version", appVersion)
    inputs.property("analyticsId", analyticsId)
    inputs.property("analyticsSecret", analyticsSecret)
    outputs.dir(out)
    doLast {
        val file = out.get().file("io/github/immersionplayer/desktop/BuildInfo.kt").asFile
        file.parentFile.mkdirs()
        file.writeText(
            "package io.github.immersionplayer.desktop\n\n" +
                "const val APP_VERSION = \"$appVersion\"\n" +
                "const val ANALYTICS_MEASUREMENT_ID = \"$analyticsId\"\n" +
                "const val ANALYTICS_API_SECRET = \"$analyticsSecret\"\n",
        )
    }
}

sourceSets.main {
    kotlin.srcDir(writeBuildInfo)
    // Compose code shared with the Android app, compiled here against JetBrains Compose
    kotlin.srcDir("../shared-ui/src")
    // bundled dictionaries (JMdict), read as resources under dictionaries/
    resources.srcDir("../app/src/main/assets")
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("net.java.dev.jna:jna:5.19.1")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    implementation("org.json:json:20250517")

    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation("junit:junit:4.13.2")
}

tasks.test {
    // the UI test drives real libmpv; same lookup path as the dev run
    systemProperty("jna.library.path", "/opt/homebrew/lib:/usr/local/lib:/usr/lib")
    System.getenv("IMMERSION_TEST_VIDEO")?.let { environment("IMMERSION_TEST_VIDEO", it) }
    System.getenv("IMMERSION_TEST_SHOTS")?.let { environment("IMMERSION_TEST_SHOTS", it) }
    // an AnkiConnect to read note types and decks from (read only); the Anki pane is tested offline without it
    System.getenv("IMMERSION_TEST_ANKI")?.let { environment("IMMERSION_TEST_ANKI", it) }
}

compose.desktop {
    application {
        mainClass = "io.github.immersionplayer.desktop.MainKt"
        // dev: find a system libmpv (Homebrew on macOS). Packaged builds bundle their own.
        jvmArgs += listOf("-Djna.library.path=/opt/homebrew/lib:/usr/local/lib:/usr/lib")
        // so Main can name the window after its launcher, for the dock (setLinuxWindowClass)
        if (System.getProperty("os.name").lowercase().contains("linux")) jvmArgs += "--add-opens=java.desktop/sun.awt.X11=ALL-UNNAMED"
        // ./gradlew :desktop:run -PappArgs="--bench video.mkv 1920" (split on '|' so paths may hold spaces)
        (project.findProperty("mpvOptions") as String?)?.let { jvmArgs += "-Dmpv.options=$it" }
        if (project.hasProperty("stats")) jvmArgs += "-Dimmersion.stats=1"
        // -Prenderer=sw: mpv's software renderer instead of OpenGL, to compare the two
        (project.findProperty("renderer") as String?)?.let { jvmArgs += "-Dimmersion.renderer=$it" }
        if (project.hasProperty("debugLights")) jvmArgs += "-Dimmersion.debugLights=1"
        (project.findProperty("snapTitlebar") as String?)?.let { jvmArgs += "-Dimmersion.snapTitlebar=$it" }
        (project.findProperty("dumpFrame") as String?)?.let { jvmArgs += "-Dimmersion.dumpFrame=$it" }
        (project.findProperty("appArgs") as String?)?.let { args += it.split('|') }
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Immersion Player"
            packageVersion = appVersion
            description = "Japanese video player with a built-in dictionary"
            copyright = "MPL-2.0"
            modules("java.instrument", "java.prefs", "java.sql", "jdk.unsupported")
            // libmpv and its libraries, bundled per OS by bundleLibmpv
            appResourcesRootDir.set(layout.buildDirectory.dir("native"))
            // the Android launcher icon, rebuilt for each platform by scripts/make-icons.sh
            macOS {
                iconFile.set(project.file("icons/icon.icns"))
                // jpackage refuses a 0.x version on macOS; packageMacRelease writes the real one
                packageVersion = "1.0.0"
                bundleID = "io.github.immersionplayer.desktop"
                appCategory = "public.app-category.education"
                minimumSystemVersion = "12.0"
            }
            windows {
                iconFile.set(project.file("icons/icon.ico"))
                // into the user's own folder, so neither installing nor updating asks for an admin
                perUserInstall = true
                // the same for every release, so a new installer replaces the old version
                upgradeUuid = "17ce85a1-57a0-4e4e-9c09-549dfad03d7d"
                menuGroup = "Immersion Player"
                shortcut = true
            }
            linux {
                iconFile.set(project.file("src/main/resources/icon.png"))
                packageName = "immersion-player"
                appCategory = "Video"
                menuGroup = "AudioVideo;Video;Education"
            }
        }
    }
}

// Copies Homebrew's libmpv and everything it loads into the app, relinked to find each other there.
// Only macOS on Apple Silicon is packaged so far.
val bundleLibmpv by tasks.registering(Exec::class) {
    val out = layout.buildDirectory.dir("native/macos-arm64")
    onlyIf { System.getProperty("os.name").lowercase().contains("mac") }
    inputs.file("scripts/bundle-libmpv-macos.sh")
    outputs.dir(out)
    commandLine("scripts/bundle-libmpv-macos.sh", out.get().asFile.path)
}

tasks.matching { it.name == "prepareAppResources" }.configureEach { dependsOn(bundleLibmpv) }

// The release .dmg: the app jpackage builds, with the real (0.x) version written into Info.plist,
// ad-hoc signed again after that edit, next to an Applications link.
val packageMacRelease by tasks.registering(Exec::class) {
    dependsOn("createDistributable")
    val version = appVersion
    val app = layout.buildDirectory.dir("compose/binaries/main/app/Immersion Player.app")
    val out = layout.buildDirectory.file("release/Immersion-Player-$version-macos-arm64.dmg")
    inputs.dir(app)
    inputs.file("scripts/package-dmg-macos.sh")
    inputs.property("version", version)
    outputs.file(out)
    commandLine("scripts/package-dmg-macos.sh", app.get().asFile.path, version, out.get().asFile.path)
}
