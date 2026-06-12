// Reborn HUD — mod Fabric client-side.
//
// Role : permet aux joueurs de repositionner librement les elements du HUD
// vanilla (chat, scoreboard, bossbar, action bar, etc.) via un mode edit
// drag-and-drop. Positions persistees dans config/reborn-hud.json.
//
// Build :
//   ./gradlew build           → build/libs/reborn-hud-<ver>.jar
//   ./gradlew runClient       → lance un client MC 1.21.1 avec le mod.

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
}

dependencies {
    minecraft("com.mojang:minecraft:${minecraft_version}")
    mappings("net.fabricmc:yarn:${yarn_mappings}:v2")
    modImplementation("net.fabricmc:fabric-loader:${loader_version}")
    // Fabric API : keybinds, HudRenderCallback, screen events.
    modImplementation("net.fabricmc.fabric-api:fabric-api:${fabric_version}")

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
