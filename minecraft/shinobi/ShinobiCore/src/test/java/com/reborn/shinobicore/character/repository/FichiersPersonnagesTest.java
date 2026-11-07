package com.reborn.shinobicore.character.repository;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Ordre des écritures des fichiers personnages (correctif du 07/11). */
class FichiersPersonnagesTest {

    /** File manuelle : les tâches « asynchrones » attendent qu'on les lance, dans l'ordre choisi par le test. */
    static final class FileManuelle implements FichiersPersonnages.Planificateur {
        final List<Runnable> taches = new ArrayList<>();
        @Override public boolean asyncDisponible() { return true; }
        @Override public void async(Runnable tache) { taches.add(tache); }
        void toutLancerALEnvers() {
            List<Runnable> copie = new ArrayList<>(taches);
            taches.clear();
            Collections.reverse(copie);
            copie.forEach(Runnable::run);
        }
    }

    @TempDir Path tmp;

    private static String lireDisque(File f) throws Exception {
        return Files.readString(f.toPath(), StandardCharsets.UTF_8);
    }

    @Test
    void ecrituresDansLeDesordre_laPlusRecenteGagne() throws Exception {
        FileManuelle file = new FileManuelle();
        FichiersPersonnages fp = new FichiersPersonnages(Logger.getAnonymousLogger(), file);
        File f = tmp.resolve("compte.yml").toFile();

        fp.ecrire(f, "v1");
        fp.ecrire(f, "v2");
        fp.ecrire(f, "v3");
        // Avant toute écriture, la lecture rend déjà le contenu le plus récent.
        assertEquals("v3", fp.lire(f));
        assertFalse(f.exists());

        file.toutLancerALEnvers();                 // v3 puis v2 puis v1
        assertEquals("v3", lireDisque(f));
        assertEquals("v3", fp.lire(f));
        assertFalse(tmp.resolve("compte.yml.tmp").toFile().exists(), "le fichier temporaire ne doit pas rester");
    }

    @Test
    void ecritureImmediate_rendLesEcrituresEnFilePerimees() throws Exception {
        FileManuelle file = new FileManuelle();
        FichiersPersonnages fp = new FichiersPersonnages(Logger.getAnonymousLogger(), file);
        File f = tmp.resolve("compte.yml").toFile();

        fp.ecrire(f, "avec-le-perso");
        fp.ecrireMaintenant(f, "sans-le-perso");
        assertEquals("sans-le-perso", lireDisque(f));

        file.toutLancerALEnvers();                 // l'ancienne écriture arrive après : ignorée
        assertEquals("sans-le-perso", lireDisque(f));
    }

    @Test
    void suppressionDuFichier_nonRestaureeParUneEcriturePerimee() throws Exception {
        FileManuelle file = new FileManuelle();
        FichiersPersonnages fp = new FichiersPersonnages(Logger.getAnonymousLogger(), file);
        File f = tmp.resolve("compte.yml").toFile();
        Files.writeString(f.toPath(), "ancien", StandardCharsets.UTF_8);

        fp.ecrire(f, "perime");
        fp.ecrireMaintenant(f, null);              // fichier devenu vide : supprimé
        assertFalse(f.exists());
        assertNull(fp.lire(f));

        file.toutLancerALEnvers();
        assertFalse(f.exists(), "une écriture en file ne doit pas recréer le fichier");
    }

    @Test
    void pierresTombales() {
        FichiersPersonnages fp = new FichiersPersonnages(Logger.getAnonymousLogger(), new FileManuelle());
        UUID id = UUID.randomUUID();
        assertFalse(fp.estEnterre(id));
        fp.enterrer(id);
        assertTrue(fp.estEnterre(id));
        assertTrue(fp.tombes().contains(id));
    }
    @Test
    void ecritureRecenteRatee_rendQuandMemeLesAnciennesPerimees() throws Exception {
        FileManuelle file = new FileManuelle();
        Logger muet = Logger.getAnonymousLogger();
        muet.setUseParentHandlers(false);
        FichiersPersonnages fp = new FichiersPersonnages(muet, file);
        File f = tmp.resolve("compte.yml").toFile();

        fp.ecrire(f, "avec-le-perso");             // génération 1, en file
        // La cible est un dossier non vide : l'écriture immédiate (génération 2) échoue.
        assertTrue(f.mkdirs());
        Files.writeString(new File(f, "verrou").toPath(), "x", StandardCharsets.UTF_8);
        fp.ecrireMaintenant(f, "sans-le-perso");
        assertEquals("sans-le-perso", fp.lire(f), "le contenu le plus récent reste en mémoire");

        // L'obstacle disparaît ; la vieille écriture en file ne doit pas passer pour autant.
        Files.delete(new File(f, "verrou").toPath());
        Files.delete(f.toPath());
        file.toutLancerALEnvers();
        assertFalse(f.exists(), "une écriture plus ancienne que l'échec ne doit pas arriver sur le disque");

        // Nouvel essai (arrêt du plugin) : le contenu en attente arrive enfin.
        fp.reessayerEchecs();
        assertEquals("sans-le-perso", lireDisque(f));
        fp.reessayerEchecs();                      // plus rien à refaire
        assertEquals("sans-le-perso", lireDisque(f));
    }

    @Test
    void lecture_tolereUnFichierEnAnsi() throws Exception {
        FichiersPersonnages fp = new FichiersPersonnages(Logger.getAnonymousLogger(), new FileManuelle());
        File f = tmp.resolve("compte.yml").toFile();
        // « é » encodé en ISO-8859-1 (éditeur en ANSI sous Windows) : invalide en UTF-8.
        Files.write(f.toPath(), "name: Kakashi Hataké".getBytes(StandardCharsets.ISO_8859_1));
        String texte = fp.lire(f);
        assertTrue(texte.startsWith("name: Kakashi Hatak"), "lecture refusée au lieu d'être tolérante");
    }
}
