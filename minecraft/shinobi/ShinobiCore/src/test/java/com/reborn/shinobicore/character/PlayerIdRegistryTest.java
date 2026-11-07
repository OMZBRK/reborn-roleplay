package com.reborn.shinobicore.character;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Migration du démarrage face à un personnage supprimé retrouvé sur le disque (correctif du 07/11). */
class PlayerIdRegistryTest {

    @TempDir Path tmp;

    private static void ecrirePerso(File f, UUID id, String nom, int n) throws Exception {
        YamlConfiguration cfg = f.isFile() ? YamlConfiguration.loadConfiguration(f) : new YamlConfiguration();
        cfg.set(id + ".name", nom);
        cfg.set(id + ".playerId", n);
        cfg.save(f);
    }

    @Test
    void supprimeParLeStaff_purgeEtJamaisReactive() throws Exception {
        File index = tmp.resolve("player-ids.yml").toFile();
        File dossier = tmp.resolve("characters").toFile();
        assertTrue(dossier.mkdirs());
        UUID owner = UUID.randomUUID();
        UUID vivant = UUID.randomUUID(), fantome = UUID.randomUUID();

        PlayerIdRegistry ids = new PlayerIdRegistry(index, Logger.getAnonymousLogger());
        ids.load();
        int nV = ids.assign(vivant, owner, "Vivant");
        int nF = ids.assign(fantome, owner, "Fantome");
        ids.markDeleted(fantome);                          // suppression au Poste de garde
        File compte = new File(dossier, owner + ".yml");
        ecrirePerso(compte, vivant, "Vivant", nV);
        ecrirePerso(compte, fantome, "Fantome", nF);       // remis par une écriture périmée

        // Redémarrage.
        PlayerIdRegistry relu = new PlayerIdRegistry(index, Logger.getAnonymousLogger());
        relu.load();
        assertTrue(relu.estSupprimeDefinitif(fantome));
        relu.migrate(dossier);

        assertTrue(relu.get(nF).deleted(), "le N° du personnage supprimé ne doit pas être réactivé");
        assertTrue(relu.estSupprimeDefinitif(fantome));
        assertFalse(relu.get(nV).deleted());
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(compte);
        assertFalse(cfg.contains(fantome.toString()), "le fantôme doit être purgé du fichier");
        assertTrue(cfg.contains(vivant.toString()));

        // Persisté : un nouveau démarrage le sait toujours.
        PlayerIdRegistry encore = new PlayerIdRegistry(index, Logger.getAnonymousLogger());
        encore.load();
        assertTrue(encore.estSupprimeDefinitif(fantome));
    }

    @Test
    void fichierNeContenantQueLeFantome_supprime() throws Exception {
        File index = tmp.resolve("player-ids.yml").toFile();
        File dossier = tmp.resolve("characters").toFile();
        assertTrue(dossier.mkdirs());
        UUID owner = UUID.randomUUID(), fantome = UUID.randomUUID();
        PlayerIdRegistry ids = new PlayerIdRegistry(index, Logger.getAnonymousLogger());
        ids.load();
        int n = ids.assign(fantome, owner, "Fantome");
        ids.markDeleted(fantome);
        File compte = new File(dossier, owner + ".yml");
        ecrirePerso(compte, fantome, "Fantome", n);

        ids.migrate(dossier);
        assertFalse(compte.exists());
        assertTrue(ids.get(n).deleted());
    }

    @Test
    void absentAuDemarrageSansSuppression_reactiveCommeAvant() throws Exception {
        File index = tmp.resolve("player-ids.yml").toFile();
        File dossier = tmp.resolve("characters").toFile();
        assertTrue(dossier.mkdirs());
        UUID owner = UUID.randomUUID(), perso = UUID.randomUUID();
        PlayerIdRegistry ids = new PlayerIdRegistry(index, Logger.getAnonymousLogger());
        ids.load();
        int n = ids.assign(perso, owner, "Perso");

        ids.migrate(dossier);                              // fichier absent : marqué supprimé (orphelin)
        assertTrue(ids.get(n).deleted());
        assertFalse(ids.estSupprimeDefinitif(perso));

        File compte = new File(dossier, owner + ".yml");
        ecrirePerso(compte, perso, "Perso", n);           // le fichier revient (sauvegarde restaurée)
        ids.migrate(dossier);
        assertFalse(ids.get(n).deleted(), "un orphelin qui revient est réactivé (comportement inchangé)");
        assertEquals(n, ids.numberOf(perso));
    }
}
