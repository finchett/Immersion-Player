import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.compose")
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":core"))
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation("net.java.dev.jna:jna:5.19.1")
    implementation("org.xerial:sqlite-jdbc:3.53.4.0")
    implementation("org.json:json:20250517")
}

compose.desktop {
    application {
        mainClass = "io.github.immersionplayer.desktop.MainKt"
        // dev: find a system libmpv (Homebrew on macOS). Packaged builds bundle their own.
        jvmArgs += listOf("-Djna.library.path=/opt/homebrew/lib:/usr/local/lib:/usr/lib")
        // ./gradlew :desktop:run -PappArgs="--bench video.mkv 1920" (split on '|' so paths may hold spaces)
        (project.findProperty("mpvOptions") as String?)?.let { jvmArgs += "-Dmpv.options=$it" }
        if (project.hasProperty("stats")) jvmArgs += "-Dimmersion.stats=1"
        (project.findProperty("dumpFrame") as String?)?.let { jvmArgs += "-Dimmersion.dumpFrame=$it" }
        (project.findProperty("appArgs") as String?)?.let { args += it.split('|') }
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Msi, TargetFormat.Deb)
            packageName = "Immersion Player"
            packageVersion = "0.1.0"
        }
    }
}
