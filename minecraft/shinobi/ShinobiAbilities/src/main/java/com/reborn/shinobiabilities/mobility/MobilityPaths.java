package com.reborn.shinobiabilities.mobility;

import com.reborn.shinobiabilities.CoreServices;
import com.reborn.shinobicore.character.ShinobiCharacter;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-character mobility "path". A fresh character knows NONE — no mobility at
 * all. A character learns exactly one path (later: by completing a parkour of
 * that path's training category; for now via {@code /mobility path}). The two
 * paths are mutually exclusive.
 *
 * <ul>
 *   <li>{@link Path#ONE} — "Voie du Chakra": the chakra-powered ninja kit
 *       (dash, naruto-run, double/wall-jump, climb, shockwave, trails).</li>
 *   <li>{@link Path#TWO} — "Voie du Flux": the momentum / free-running model.</li>
 * </ul>
 *
 * <p>Stored in ShinobiAbilities (keyed by character UUID) so the path system
 * stays decoupled from ShinobiCore's character data.
 */
public final class MobilityPaths {

    public enum Path {
        NONE("Aucune"),
        ONE("Voie du Chakra"),
        TWO("Voie du Flux");

        private final String display;
        Path(String display) { this.display = display; }
        public String display() { return display; }
    }

    private final JavaPlugin plugin;
    private final CoreServices core;
    private final File file;
    private YamlConfiguration yml;
    private final Map<UUID, Path> cache = new HashMap<>();

    public MobilityPaths(JavaPlugin plugin, CoreServices core) {
        this.plugin = plugin;
        this.core = core;
        this.file = new File(plugin.getDataFolder(), "mobility-paths.yml");
        load();
    }

    public void load() {
        cache.clear();
        yml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
        for (String key : yml.getKeys(false)) {
            try {
                cache.put(UUID.fromString(key), Path.valueOf(yml.getString(key, "NONE")));
            } catch (IllegalArgumentException ignore) { }
        }
    }

    /** The path a character knows (NONE by default). */
    public Path get(UUID characterId) {
        return characterId == null ? Path.NONE : cache.getOrDefault(characterId, Path.NONE);
    }

    /** The path of a player's ACTIVE character (NONE if none active). */
    public Path pathOf(Player p) {
        if (p == null) return Path.NONE;
        ShinobiCharacter c = core.characters().getActive(p.getUniqueId());
        return c == null ? Path.NONE : get(c.id());
    }

    /** Set (or clear, with NONE) a character's path and persist. */
    public void set(UUID characterId, Path path) {
        if (characterId == null) return;
        if (path == Path.NONE) {
            cache.remove(characterId);
            yml.set(characterId.toString(), null);
        } else {
            cache.put(characterId, path);
            yml.set(characterId.toString(), path.name());
        }
        save();
    }

    private void save() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            yml.save(file);
        } catch (Exception ex) {
            plugin.getLogger().warning("Sauvegarde mobility-paths.yml impossible: " + ex.getMessage());
        }
    }
}
