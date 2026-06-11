// Reborn OST — mod Fabric client-side.
//
// Role : lecteur audio in-game pour la BGM Reborn (BGM ambiance + tracks
// broadcastees par le serveur). Les .ogg vivent dans
// ~/.minecraft/reborn/ost/<categorie>/<nom>.ogg (pas dans le jar) — voir
// OstLibrary pour le scan filesystem.
//
// Build :
//   ./gradlew build           → build/libs/reborn-ost-<ver>.jar
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

// Deuxième client de dev — pratique pour tester l'attenuation positionnelle
// du broadcast OST avec deux instances connectées au même serveur. Run dir
// séparée (`run-clientB`) pour ne pas se battre sur les locks de world / lock
// de config / Credentials. Username "DevB" pour que Paper accepte les deux
// connexions simultanées.
loom {
    runs {
        register("clientB") {
            client()
            ideConfigGenerated(true)
            runDir("run-clientB")
            programArgs("--username", "DevB")
        }
    }
}

dependencies {
    minecraft("com.mojang:minecraft:${minecraft_version}")
    mappings("net.fabricmc:yarn:${yarn_mappings}:v2")
    modImplementation("net.fabricmc:fabric-loader:${loader_version}")
    // Fabric API : keybinds, networking C2S/S2C, HudRenderCallback,
    // Screen events. Toujours pratique pour rester sur l'API publique.
    modImplementation("net.fabricmc.fabric-api:fabric-api:${fabric_version}")

    // Tests unitaires sur les codecs / serializers / scan FS.
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
