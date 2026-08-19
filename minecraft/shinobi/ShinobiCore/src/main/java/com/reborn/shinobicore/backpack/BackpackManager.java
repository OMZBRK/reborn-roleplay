package com.reborn.shinobicore.backpack;

import com.reborn.shinobicore.ShinobiCore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Owns every {@link Backpack} on the server, keyed by UUID. The
 * backpack ID lives on the physical {@link ItemStack} via
 * {@link BackpackItem}; the contents live here, so multiple stacks
 * referencing the same id all see the same inventory (impossible
 * under normal play, but cleanly handles the place-on-ground +
 * pick-up-from-ground round-trip without duplicating storage).
 *
 * <h2>Persistence — {@code backpacks.yml}</h2>
 * <pre>
 * backpacks:
 *   "&lt;uuid&gt;":
 *     size: SMALL | LARGE
 *     contents:
 *       '0': &lt;ItemStack YAML&gt;
 *       '5': &lt;ItemStack YAML&gt;
 *       ...
 * </pre>
 *
 * <p>Empty slots are simply absent from the contents map. The whole
 * section is null-set before each save so deleted slots propagate.
 */
public class BackpackManager {

    private final ShinobiCore plugin;
    private final File file;
    private final Map<UUID, Backpack> byId = new LinkedHashMap<>();

    public BackpackManager(ShinobiCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "backpacks.yml");
    }

    /* ---------------------------------------------------------- lifecycle */

    public void load() {
        byId.clear();
        if (!file.isFile()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("backpacks");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            UUID id;
            try { id = UUID.fromString(key); }
            catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipping malformed backpack id '" + key + "'.");
                continue;
            }
            BackpackSize size = BackpackSize.parse(s.getString("size", "SMALL"));
            if (size == null) size = BackpackSize.SMALL;
            Backpack pack = new Backpack(id, size);

            ConfigurationSection contents = s.getConfigurationSection("contents");
            if (contents != null) {
                for (String slotKey : contents.getKeys(false)) {
                    int slot;
                    try { slot = Integer.parseInt(slotKey); }
                    catch (NumberFormatException ex) { continue; }
                    Object raw = contents.get(slotKey);
                    if (raw instanceof ItemStack stack) pack.set(slot, stack);
                }
            }
            byId.put(id, pack);
        }
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        for (Backpack pack : byId.values()) {
            String key = "backpacks." + pack.id();
            cfg.set(key + ".size", pack.size().name());
            // Null the contents section first so removed items don't
            // linger from a previous save (deleted slots actually delete).
            cfg.set(key + ".contents", null);
            ItemStack[] contents = pack.contents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack s = contents[i];
                if (s != null && !s.getType().isAir() && s.getAmount() > 0) {
                    cfg.set(key + ".contents." + i, s);
                }
            }
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to save backpacks.yml", ex);
        }
    }

    /* ---------------------------------------------------------- CRUD */

    /** Mint a brand-new backpack with a random UUID, register it, and
     *  return the data record. The caller usually pairs this with
     *  {@link BackpackItem#create} to get the matching physical item. */
    public Backpack create(BackpackSize size) {
        UUID id = UUID.randomUUID();
        Backpack pack = new Backpack(id, size);
        byId.put(id, pack);
        save();
        return pack;
    }

    /** Lookup by id; returns {@code null} for unknown ids. */
    public Backpack get(UUID id) {
        return id == null ? null : byId.get(id);
    }

    /** Lookup the backpack referenced by an {@link ItemStack}; returns
     *  {@code null} if the item isn't a backpack or its id has no
     *  registered record (which can happen if backpacks.yml was
     *  manually edited or deleted). */
    public Backpack getFor(ItemStack item) {
        UUID id = BackpackItem.idOf(plugin, item);
        return id == null ? null : byId.get(id);
    }

    /** Convenience — get-or-recreate. If the id on the item isn't
     *  registered (e.g., orphaned backpack from a wiped data file),
     *  re-add a record with the item's known size so the contents
     *  flow continues to work. Used on equip / interact paths where
     *  showing an empty backpack is better than throwing an error. */
    public Backpack getOrAdopt(ItemStack item) {
        Backpack existing = getFor(item);
        if (existing != null) return existing;
        UUID id = BackpackItem.idOf(plugin, item);
        BackpackSize size = BackpackItem.sizeOf(plugin, item);
        if (id == null || size == null) return null;
        Backpack pack = new Backpack(id, size);
        byId.put(id, pack);
        save();
        return pack;
    }

    /** Drop a backpack record entirely — only used by admin tooling
     *  / cleanup, not normal play. */
    public boolean delete(UUID id) {
        boolean changed = byId.remove(id) != null;
        if (changed) save();
        return changed;
    }

    public Collection<Backpack> all() { return byId.values(); }
    public int count() { return byId.size(); }
}
