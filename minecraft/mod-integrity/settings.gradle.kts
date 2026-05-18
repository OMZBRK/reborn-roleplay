// Reborn Integrity — mod Fabric client-side qui pousse le play-token
// signe par l'API au plugin Guardian via le custom payload reborn:auth.
//
// Le projet est isole du monorepo pnpm (comme plugin-guardian). Ouvrir
// ce dossier en standalone dans IntelliJ pour l'integration Gradle.

pluginManagement {
    repositories {
        maven("https://maven.fabricmc.net/") { name = "Fabric" }
        gradlePluginPortal()
    }
}

// Auto-download de la JDK 21 via Foojay (Adoptium/Temurin) si pas
// presente sur la machine. Sans ca, Loom plante au configure avec
// "Minecraft 1.21.1 requires Java 21 but Gradle is using 17".
// La JDK est cachee dans ~/.gradle/jdks/.
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}

rootProject.name = "reborn-integrity"
