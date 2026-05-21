// Reborn OST Plugin — broadcast audio Paper.
//
// Stack :
//   Paper API 1.21.1
//   Java 21 toolchain
//   run-paper (jpenilla) pour lancer un serveur de test local.
//
// Build :
//   ./gradlew build              → build/libs/reborn-ost-plugin-<ver>.jar
//   ./gradlew runServer          → lance un Paper 1.21.1 avec le plugin.

plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.14"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "fr.reborn"
version = "0.1.0-dev"
description = "Plugin Reborn Roleplay : broadcast OST aux clients via canal reborn:ost."

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

dependencies {
    paperweight.paperDevBundle("1.21.1-R0.1-SNAPSHOT")

    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    compileJava {
        options.release.set(21)
        options.encoding = "UTF-8"
    }

    processResources {
        val tokens = mapOf("version" to project.version.toString())
        inputs.properties(tokens)
        filesMatching("paper-plugin.yml") { expand(tokens) }
    }

    runServer {
        minecraftVersion("1.21.1")
        systemProperty("com.mojang.eula.agree", "true")
        // runDirectory hors OneDrive — meme rationale que plugin-guardian.
        runDirectory = file(System.getProperty("user.home") + "/.reborn-ost-run")
    }

    test {
        useJUnitPlatform()
    }
}
