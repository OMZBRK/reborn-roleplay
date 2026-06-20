// Reborn HUD — mod Fabric client-side.
//
// Role : toute la couche UI Reborn cote client. HUD in-game
// (chat, scoreboard, bossbar, action bar, hotbar, etc.) avec editeur
// drag/resize/hide + chat custom Reborn. Cote menu : TitleScreen,
// ConnectScreen, GameMenuScreen (ESC), sub-screens (Options / Lore /
// Regles) refondus aux couleurs Reborn, avec background dynamique
// MCEF (Chromium embedded) rendant une scene 3D du skin du joueur.
//
// Build :
//   ./gradlew build           → build/libs/reborn-hud-<ver>.jar
//   ./gradlew runClient       → lance un client MC 1.21.1 avec le mod
//                               (premiere execution telecharge ~300 Mo
//                               vanilla + ~150 Mo natives MCEF).

plugins {
    java
    id("fabric-loom") version "1.9-SNAPSHOT"
}

val mod_version: String by project
val maven_group: String by project
val archives_base_name: String by project
val minecraft_version: String by project
val yarn_mappings: String by project
val loader_version: String by project
val fabric_version: String by project

version = mod_version
group = maven_group
base.archivesName.set(archives_base_name)

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
    withSourcesJar()
}

repositories {
    mavenCentral()
    maven("https://maven.fabricmc.net/") { name = "Fabric" }
    // MCEF (CinemaMod) : Chromium embedded pour le main menu background
    // dynamic animated player. Pas sur Maven Central — repo dedie CinemaMod.
    maven("https://mcef-download.cinemamod.com/repositories/releases") { name = "CinemaMod" }
}

dependencies {
    minecraft("com.mojang:minecraft:${minecraft_version}")
    mappings("net.fabricmc:yarn:${yarn_mappings}:v2")
    modImplementation("net.fabricmc:fabric-loader:${loader_version}")
    // Fabric API : keybinds, HudRenderCallback, screen events, client networking.
    modImplementation("net.fabricmc.fabric-api:fabric-api:${fabric_version}")

    // MCEF (Minecraft Chromium Embedded Framework) — render le
    // bbmodel_viewer.html de Dynamic Animated Player en background du
    // main menu Reborn.
    // - mcef (commun) en compile-only : l'API publique (MCEF, MCEFBrowser, ...)
    // - mcef-fabric en runtime : la glue Fabric (loader, init mixin) + tire
    //   mcef transitivement, donc pas besoin de le redeclarer en runtime.
    // Au premier lancement, MCEF telecharge ~150 MB de natives Chromium
    // depuis mcef-download.cinemamod.com.
    modCompileOnly("com.cinemamod:mcef:2.1.6-1.21.1")
    modRuntimeOnly("com.cinemamod:mcef-fabric:2.1.6-1.21.1")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    processResources {
        val replacements = mapOf(
            "version" to project.version.toString(),
            "minecraft_version" to minecraft_version,
            "loader_version" to loader_version
        )
        inputs.properties(replacements)
        filesMatching("fabric.mod.json") { expand(replacements) }
    }

    compileJava {
        options.release.set(21)
        options.encoding = "UTF-8"
    }

    test {
        useJUnitPlatform()
    }
}
