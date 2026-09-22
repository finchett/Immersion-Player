import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(17)
}

sourceSets.main {
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
}

compose.desktop {
    application {
        mainClass = "io.github.immersionplayer.desktop.MainKt"
        // dev: find a system libmpv (Homebrew on macOS). Packaged builds bundle their own.
        jvmArgs += listOf("-Djna.library.path=/opt/homebrew/lib:/usr/local/lib:/usr/lib")
        // ./gradlew :desktop:run -PappArgs="--bench video.mkv 1920" (split on '|' so paths may hold spaces)
        (project.findProperty("mpvOptions") as String?)?.let { jvmArgs += "-Dmpv.options=$it" }
        if (project.hasProperty("stats")) jvmArgs += "-Dimmersion.stats=1"
        if (project.hasProperty("debugLights")) jvmArgs += "-Dimmersion.debugLights=1"
        (project.findProperty("dumpFrame") as String?)?.let { jvmArgs += "-Dimmersion.dumpFrame=$it" }
        (project.findProperty("appArgs") as String?)?.let { args += it.split('|') }
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Immersion Player"
            packageVersion = "0.1.0"
        }
    }
}
