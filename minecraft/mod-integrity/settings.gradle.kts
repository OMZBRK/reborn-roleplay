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

rootProject.name = "reborn-integrity"
