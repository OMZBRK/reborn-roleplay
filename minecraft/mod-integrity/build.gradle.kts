// Reborn Integrity — mod Fabric client-side.
//
// Role : a la jonction launcher↔serveur. Le launcher ecrit le play-token
// signe par l'API dans un fichier (chemin passe via sysprop
// `reborn.playTokenPath`). Au JOIN serveur, ce mod lit ce fichier et envoie
// le contenu au plugin Guardian via custom payload `reborn:auth`.
//
// Build :
//   ./gradlew build           → build/libs/reborn-integrity-<ver>.jar
//   ./gradlew runClient       → lance un client MC 1.21.1 avec le mod
//                               (premiere execution telecharge ~300 Mo)

plugins {
    java
    id("fabric-loom") version "1.9-SNAPSHOT"
}

// Les properties Gradle viennent telles quelles de gradle.properties — Kotlin
// DSL ne convertit pas snake_case en camelCase, donc on garde le nom exact
// avec `by project`. Cf https://docs.gradle.org/current/userguide/kotlin_dsl.html#sec:project_extensions_and_conventions
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
    // Inclus le source jar dans la sortie distribuable — debug serveur facilite.
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
    // Fabric API n'est strictement necessaire que pour
    // ClientPlayConnectionEvents (sinon il faudrait poser un mixin sur
    // ClientPlayNetworkHandler). On reste sur Fabric API pour la lisibilite.
    modImplementation("net.fabricmc.fabric-api:fabric-api:${fabric_version}")
}

tasks {
    processResources {
        val replacements = mapOf(
            "version" to project.version.toString(),
            "minecraft_version" to minecraft_version,
            "loader_version" to loader_version,
        )
        inputs.properties(replacements)
        filesMatching("fabric.mod.json") {
            expand(replacements)
        }
    }

    compileJava {
        options.release.set(21)
        options.encoding = "UTF-8"
    }

    jar {
        from("LICENSE") {
            rename { "${it}_${base.archivesName.get()}" }
        }
    }
}
