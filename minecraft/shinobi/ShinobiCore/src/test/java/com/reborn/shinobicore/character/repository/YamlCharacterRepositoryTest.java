package com.reborn.shinobicore.character.repository;

import com.reborn.shinobicore.character.ShinobiCharacter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Personnage supprimé qui revenait (07/11) : scénario complet sur le dépôt YAML, sans serveur. */
class YamlCharacterRepositoryTest {

    @TempDir Path tmp;

    private static Set<String> clesSurDisque(File f) {
        if (!f.isFile()) return Set.of();
        return new HashSet<>(YamlConfiguration.loadConfiguration(f).getKeys(false));
    }

    @Test
    void sauvegardePuisSuppressionDuMemeTick_lePersonnageNeRevientPas() {
        FichiersPersonnagesTest.FileManuelle file = new FichiersPersonnagesTest.FileManuelle();
        YamlCharacterRepository repo = new YamlCharacterRepository(tmp.toFile(), Logger.getAnonymousLogger(), file, null);
        UUID owner = UUID.randomUUID();
        ShinobiCharacter garde = new ShinobiCharacter(owner, "Kaito");
        ShinobiCharacter supprime = new ShinobiCharacter(owner, "Fantome");
        File f = tmp.resolve(owner + ".yml").toFile();

        // GestionPerso.sauvegarder → saveAll : deux écritures en file, pas encore sur le disque.
        repo.save(garde);
        repo.save(supprime);
        // Puis la suppression, au même tick (écriture immédiate).
        repo.delete(owner, supprime.id());
        assertEquals(Set.of(garde.id().toString()), clesSurDisque(f));

        // Tick suivant : les écritures en file arrivent, dans n'importe quel ordre.
        file.toutLancerALEnvers();
        assertEquals(Set.of(garde.id().toString()), clesSurDisque(f), "le personnage supprimé ne doit pas revenir");

        // Une sauvegarde ultérieure (autosave, déconnexion) ne le remet pas non plus.
        repo.save(supprime);
        repo.save(garde);
        file.toutLancerALEnvers();
        assertEquals(Set.of(garde.id().toString()), clesSurDisque(f));

        List<ShinobiCharacter> roster = repo.loadAll(owner);
        assertEquals(1, roster.size());
        assertEquals(garde.id(), roster.get(0).id());
        assertTrue(repo.estSupprime(supprime.id()));
    }

    @Test
    void deuxSauvegardesDuMemeTick_neSEcrasentPas() {
        FichiersPersonnagesTest.FileManuelle file = new FichiersPersonnagesTest.FileManuelle();
        YamlCharacterRepository repo = new YamlCharacterRepository(tmp.toFile(), Logger.getAnonymousLogger(), file, null);
        UUID owner = UUID.randomUUID();
        ShinobiCharacter a = new ShinobiCharacter(owner, "A");
        ShinobiCharacter b = new ShinobiCharacter(owner, "B");
        repo.save(a);
        repo.save(b);
        file.toutLancerALEnvers();
        assertEquals(Set.of(a.id().toString(), b.id().toString()), clesSurDisque(tmp.resolve(owner + ".yml").toFile()));
    }

    @Test
    void personnageSupprimeDansLIndex_nonChargeEtPurgeDuFichier() {
        FichiersPersonnagesTest.FileManuelle file = new FichiersPersonnagesTest.FileManuelle();
        UUID owner = UUID.randomUUID();
        ShinobiCharacter vivant = new ShinobiCharacter(owner, "Vivant");
        ShinobiCharacter fantome = new ShinobiCharacter(owner, "Fantome");
        File f = tmp.resolve(owner + ".yml").toFile();

        // Fichier laissé par l'ancien bug : le personnage supprimé y est encore.
        YamlCharacterRepository avant = new YamlCharacterRepository(tmp.toFile(), Logger.getAnonymousLogger(), file, null);
        avant.save(vivant);
        avant.save(fantome);
        file.toutLancerALEnvers();
        assertEquals(2, clesSurDisque(f).size());

        // Redémarrage : l'index des N° le donne supprimé pour de bon.
        Set<UUID> supprimesIndex = Set.of(fantome.id());
        YamlCharacterRepository apres = new YamlCharacterRepository(tmp.toFile(), Logger.getAnonymousLogger(), file,
                supprimesIndex::contains);
        List<ShinobiCharacter> roster = apres.loadAll(owner);
        assertEquals(1, roster.size());
        assertEquals(vivant.id(), roster.get(0).id());
        assertEquals(Set.of(vivant.id().toString()), clesSurDisque(f), "le fantôme doit être purgé du fichier");
        file.toutLancerALEnvers();
        assertFalse(clesSurDisque(f).contains(fantome.id().toString()));
    }

    @Test
    void contenuCompte_rendLesSauvegardesEncoreEnFile() throws Exception {
        FichiersPersonnagesTest.FileManuelle file = new FichiersPersonnagesTest.FileManuelle();
        YamlCharacterRepository repo = new YamlCharacterRepository(tmp.toFile(), Logger.getAnonymousLogger(), file, null);
        UUID owner = UUID.randomUUID();
        ShinobiCharacter c = new ShinobiCharacter(owner, "Kaito");
        assertTrue(repo.enregistrer(c));
        // Rien sur le disque encore, mais la copie de sauvegarde du staff doit déjà contenir le personnage.
        assertFalse(tmp.resolve(owner + ".yml").toFile().exists());
        String contenu = repo.contenuCompte(owner);
        assertTrue(contenu != null && contenu.contains(c.id().toString()));
    }
}
