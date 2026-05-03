// Reborn Guardian — plugin Paper qui valide les clients connectes au serveur
// Reborn Roleplay (cf PLAN_CONCEPTION_LAUNCHER.md §9.5).
//
// Stack :
//   Paper API 1.21.4
//   Java 21 toolchain
//   run-paper (jpenilla) pour lancer un serveur de test local via Gradle.
//
// Build :
//   ./gradlew build              → produit build/libs/reborn-guardian-<ver>.jar
//   ./gradlew runServer          → lance un Paper 1.21.4 avec le plugin charge
//                                  (premiere execution telecharge ~50 Mo).
//
// Le projet est volontairement isole du monorepo pnpm. Ouvre-le dans IntelliJ
// via "Open" sur ce dossier (pas le repo entier) pour avoir l'integration
// Gradle complete.

plugins {
    java
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.14"
    id("xyz.jpenilla.run-paper") version "2.3.1"
}

group = "fr.reborn"
version = "0.1.0-dev"
description = "Plugin serveur Reborn Roleplay : valide les clients via la triple validation."

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
    // Aligne avec le serveur dev Reborn (qui tourne 1.21.1). Quand on bumpera
    // le serveur, on bumpera ces deux versions ensemble.
    paperweight.paperDevBundle("1.21.1-R0.1-SNAPSHOT")
}

tasks {
    compileJava {
        options.release.set(21)
        options.encoding = "UTF-8"
    }

    processResources {
        val tokens = mapOf("version" to project.version.toString())
        inputs.properties(tokens)
        filesMatching("paper-plugin.yml") {
            expand(tokens)
        }
    }

    runServer {
        minecraftVersion("1.21.1")
        // Accepte l'EULA Mojang automatiquement pour le serveur de test local.
        systemProperty("com.mojang.eula.agree", "true")

        // IMPORTANT — runDirectory hors du dossier Desktop (donc hors OneDrive).
        // Sinon OneDrive verrouille `world/session.lock` au demarrage et le
        // serveur crashe avec "Le processus ne peut pas acceder au fichier
        // car un autre processus en a verrouille une partie".
        runDirectory = file(System.getProperty("user.home") + "/.reborn-guardian-run")
    }
}
