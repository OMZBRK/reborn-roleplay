package com.reborn.shinobicore.data;

import com.reborn.shinobicore.api.DataStore;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/**
 * Backend-agnostic skeleton for per-character stores: the in-memory
 * cache, dirty-flag lifecycle and periodic autosave live here once;
 * a backend subclass supplies only the raw storage primitives
 * ({@link #loadRaw}, {@link #writeRaw}, …) and a concrete store
 * supplies the record hooks ({@link #create}, {@link #read},
 * {@link #write}) exactly as before.
 *
 * <p>Records serialize through {@link YamlConfiguration} on every
 * backend — the YAML backend writes it to a file, the SQL backend to a
 * TEXT column — so switching backends never changes a store's
 * (de)serialization code.
 *
 * @param <T> the per-character record type
 */
public abstract class CharacterDataStore<T extends CharacterData>
        implements DataStore<UUID, T> {

    private final JavaPlugin plugin;
    private final Map<UUID, T> cache = new ConcurrentHashMap<>();
    private BukkitTask autosave;

    protected CharacterDataStore(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /* ----------------------------------------------------------- lifecycle */

    /** Start the periodic autosave ({@code storage.autosave-seconds}, min 30). */
    @Override
    public void start() {
        long ticks = 20L * Math.max(30,
                plugin.getConfig().getInt("storage.autosave-seconds", 120));
        autosave = plugin.getServer().getScheduler()
                .runTaskTimer(plugin, this::saveDirty, ticks, ticks);
    }

    /** Cancel the autosave and flush everything dirty. */
    @Override
    public void stop() {
        if (autosave != null) autosave.cancel();
        saveDirty();
    }

    /* -------------------------------------------------------------- access */

    /** Record for a character id; loads from the backend on first
     *  touch, never null. */
    public T of(UUID characterId) {
        return cache.computeIfAbsent(characterId, this::loadOne);
    }

    @Override
    public T get(UUID characterId) {
        return of(characterId);
    }

    /** Drop a character's record from the in-memory cache. */
    public void evict(UUID characterId) {
        cache.remove(characterId);
    }

    /** The owning plugin, for subclass logging / config reads. */
    protected JavaPlugin plugin() {
        return plugin;
    }

    /* --------------------------------------------------------- persistence */

    private T loadOne(UUID characterId) {
        T data = create(characterId);
        YamlConfiguration yml = loadRaw(characterId);
        if (yml != null) read(data, yml);
        return data;
    }

    /** Write one record (skipping pristine shells), then mark it clean. */
    @Override
    public void save(T data) {
        if (!shouldPersist(data, existsRaw(data.characterId()))) {
            data.markClean();
            return;
        }
        YamlConfiguration yml = new YamlConfiguration();
        write(data, yml);
        try {
            writeRaw(data.characterId(), yml);
            data.markClean();
        } catch (IOException ex) {
            plugin.getLogger().warning("Could not save record "
                    + data.characterId() + ": " + ex.getMessage());
        }
    }

    /** Flush every cached record whose dirty flag is set. */
    public void saveDirty() {
        for (T d : cache.values()) {
            if (d.dirty()) save(d);
        }
    }

    @Override
    public void flush() {
        saveDirty();
    }

    @Override
    public boolean delete(UUID characterId) {
        cache.remove(characterId);
        return deleteRaw(characterId);
    }

    @Override
    public List<UUID> keys() {
        // Backend keys ∪ cached-but-unsaved records.
        List<UUID> out = new ArrayList<>(listRawKeys());
        for (UUID cached : cache.keySet()) {
            if (!out.contains(cached)) out.add(cached);
        }
        return out;
    }

    @Override
    public List<T> query(Predicate<T> filter) {
        List<T> out = new ArrayList<>();
        for (UUID key : keys()) {
            T record = of(key);
            if (filter.test(record)) out.add(record);
        }
        return out;
    }

    /* ------------------------------------------------------ backend hooks */

    /** Raw record for a key, or null when the backend has none. */
    protected abstract YamlConfiguration loadRaw(UUID characterId);

    /** Persist the serialized record for a key. */
    protected abstract void writeRaw(UUID characterId, YamlConfiguration yml)
            throws IOException;

    /** Remove the raw record; true if one existed. */
    protected abstract boolean deleteRaw(UUID characterId);

    /** True when the backend holds a record for the key. */
    protected abstract boolean existsRaw(UUID characterId);

    /** Every key the backend holds. */
    protected abstract List<UUID> listRawKeys();

    /* ------------------------------------------------------- record hooks */

    /** A fresh, empty record for a character id (used on a cache miss). */
    protected abstract T create(UUID characterId);

    /** Populate {@code data} from its loaded serialized form. */
    protected abstract void read(T data, YamlConfiguration yml);

    /** Serialize {@code data} into {@code yml} for saving. */
    protected abstract void write(T data, YamlConfiguration yml);

    /**
     * Whether a record is worth persisting. Default {@code true};
     * override to skip writing a pristine, never-touched shell.
     * {@code exists} is true when the backend already holds a record
     * (you usually still want to persist then, to overwrite).
     */
    protected boolean shouldPersist(T data, boolean exists) {
        return true;
    }
}
