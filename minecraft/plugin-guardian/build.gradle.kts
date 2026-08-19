// Reborn Guardian — plugin Paper qui valide les clients connectes au serveur
// Reborn Roleplay (cf PLAN_CONCEPTION_LAUNCHER.md §9.5).
//
// Stack :
//   Paper API 26.1.2 (dev-bundle 26.2.build.112-stable)
//   Java 25 toolchain
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
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
    id("xyz.jpenilla.run-paper") version "3.0.2"
}

group = "fr.reborn"
version = "0.1.0-dev"
description = "Plugin serveur Reborn Roleplay : valide les clients via la triple validation."

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(25))
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/") {
        name = "papermc"
    }
}

dependencies {
    // Aligne avec le serveur dev Reborn (Purpur 26.1.2) et le build (Paper
    // 26.1.2-74). Le dev-bundle Paper suit le nouveau schema 26.x.build.NN.
    paperweight.paperDevBundle("26.2.build.112-stable")

    // Tests unitaires (PlayTokenVerifier surtout : code crypto critique).
    testImplementation("org.junit.jupiter:junit-jupiter:5.10.2")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    compileJava {
        options.release.set(25)
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
        minecraftVersion("26.2")
        // Accepte l'EULA Mojang automatiquement pour le serveur de test local.
        systemProperty("com.mojang.eula.agree", "true")

        // Le plugin a besoin de REBORN_PLAY_TOKEN_SECRET pour valider les
        // attestations. En dev, on relaie depuis l'env du shell ou on tombe
        // sur un placeholder bien identifiable qui matche celui qu'il faudra
        // utiliser cote API (.env du monorepo) si tu veux tester le E2E.
        environment(
            "REBORN_PLAY_TOKEN_SECRET",
            System.getenv("REBORN_PLAY_TOKEN_SECRET")
                ?: "dev-placeholder-secret-please-override-32chars-min!!!"
        )

        // IMPORTANT — runDirectory hors du dossier Desktop (donc hors OneDrive).
        // Sinon OneDrive verrouille `world/session.lock` au demarrage et le
        // serveur crashe avec "Le processus ne peut pas acceder au fichier
        // car un autre processus en a verrouille une partie".
        runDirectory = file(System.getProperty("user.home") + "/.reborn-guardian-run")
    }

    test {
        useJUnitPlatform()
    }
}
