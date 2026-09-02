package com.reborn.shinobicore.data;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * File-backed per-character store — one YAML file per character under
 * {@code <dataFolder>/<subdir>/<characterUuid>.yml}. The default
 * persistence backend (contract §5).
 *
 * <p>ShinobiTail's jinchūriki store and ShinobiLearning's academy store
 * both build on this; a subclass supplies only how to
 * {@link #create create}, {@link #read read}, and {@link #write write}
 * its record (and, optionally, {@link #shouldPersist when} a pristine
 * record isn't worth a file). The cache/autosave/dirty-flag lifecycle
 * lives once in {@link CharacterDataStore}, shared with the SQL
 * backend ({@link SqlCharacterDataStore}).
 *
 * @param <T> the per-character record type
 */
public abstract class YamlCharacterDataStore<T extends CharacterData>
        extends CharacterDataStore<T> {

    private final String subdir;

    /**
     * @param plugin owning plugin (its data folder, scheduler, config and
     *               logger are used)
     * @param subdir folder under the data folder, e.g. {@code "data/jinchuriki"}
     */
    protected YamlCharacterDataStore(JavaPlugin plugin, String subdir) {
        super(plugin);
        this.subdir = subdir;
    }

    /** The backing file for a character id. */
    protected File fileFor(UUID characterId) {
        return new File(plugin().getDataFolder(), subdir + "/" + characterId + ".yml");
    }

    /* ------------------------------------------------------ backend hooks */

    @Override
    protected YamlConfiguration loadRaw(UUID characterId) {
        File f = fileFor(characterId);
        return f.exists() ? YamlConfiguration.loadConfiguration(f) : null;
    }

    @Override
    protected void writeRaw(UUID characterId, YamlConfiguration yml)
            throws IOException {
        File f = fileFor(characterId);
        File parent = f.getParentFile();
        if (parent != null) parent.mkdirs();
        yml.save(f);
    }

    @Override
    protected boolean deleteRaw(UUID characterId) {
        return fileFor(characterId).delete();
    }

    @Override
    protected boolean existsRaw(UUID characterId) {
        return fileFor(characterId).exists();
    }

    @Override
    protected List<UUID> listRawKeys() {
        List<UUID> out = new ArrayList<>();
        File dir = new File(plugin().getDataFolder(), subdir);
        File[] files = dir.listFiles((d, name) -> name.endsWith(".yml"));
        if (files == null) return out;
        for (File f : files) {
            String base = f.getName().substring(0, f.getName().length() - 4);
            try {
                out.add(UUID.fromString(base));
            } catch (IllegalArgumentException ignore) {
                // Foreign file in the data folder — not a record.
            }
        }
        return out;
    }
}
