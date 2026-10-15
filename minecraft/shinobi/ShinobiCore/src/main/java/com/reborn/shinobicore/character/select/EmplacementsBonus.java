package com.reborn.shinobicore.character.select;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Emplacements de personnage débloqués (ou retirés) par le staff, par compte (Poste de garde › fiche › Personnages,
 * 15/10). S'ajoutent à la base du compte (2, premium 4, staff 7 : {@link CharacterSelectManager#slotBase}). Fichier
 * {@code plugins/ShinobiCore/emplacements.yml} : {@code <uuid du compte>: <bonus>} (négatif = retiré).
 */
public final class EmplacementsBonus {

    private final Plugin plugin;
    private final File file;
    private final Map<UUID, Integer> bonus = new HashMap<>();

    public EmplacementsBonus(Plugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "emplacements.yml");
        load();
    }

    public synchronized int of(UUID owner) { return owner == null ? 0 : bonus.getOrDefault(owner, 0); }

    /** Fixe le bonus du compte et écrit le fichier. */
    public synchronized void set(UUID owner, int value) {
        if (owner == null) return;
        if (value == 0) bonus.remove(owner); else bonus.put(owner, value);
        save();
    }

    private void load() {
        if (!file.isFile()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String k : y.getKeys(false)) {
            try { bonus.put(UUID.fromString(k), y.getInt(k)); } catch (IllegalArgumentException ignored) { }
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        bonus.forEach((k, v) -> y.set(k.toString(), v));
        try { y.save(file); }
        catch (IOException e) { plugin.getLogger().log(Level.WARNING, "emplacements.yml : écriture impossible", e); }
    }
}
