package com.reborn.shinobiabilities.jutsu;

import com.reborn.shinobicore.technique.JutsuItemType;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-<b>character</b> jutsu bindings: each JutsuItemType holds 5 slots
 * of ability ids. Stored in this plugin's own {@code bindings.yml}
 * (ShinobiCore deliberately knows nothing about jutsu).
 *
 * <p>Saves follow the project rule: serialize to a String on the main
 * thread, write the bytes async — never block a tick on disk I/O.
 */
public final class JutsuBindingStore {

    public static final int SLOTS = 5;

    private final JavaPlugin plugin;
    private final File file;
    /** characterId → type → 5-slot array (null = empty slot). */
    private final Map<UUID, EnumMap<JutsuItemType, String[]>> bindings = new HashMap<>();
    private boolean dirty;
    private boolean savePending;

    public JutsuBindingStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "bindings.yml");
    }

    /* ------------------------------------------------------------- load */

    public void load() {
        bindings.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String charKey : yml.getKeys(false)) {
            UUID charId;
            try { charId = UUID.fromString(charKey); }
            catch (IllegalArgumentException ex) { continue; }
            ConfigurationSection perType = yml.getConfigurationSection(charKey);
            if (perType == null) continue;
            EnumMap<JutsuItemType, String[]> map = new EnumMap<>(JutsuItemType.class);
            for (String typeKey : perType.getKeys(false)) {
                JutsuItemType type = JutsuItemType.from(typeKey);
                if (type == null) continue;
                String[] slots = new String[SLOTS];
                var list = perType.getStringList(typeKey);
                for (int i = 0; i < SLOTS && i < list.size(); i++) {
                    String v = list.get(i);
                    slots[i] = (v == null || v.isBlank() || v.equals("-")) ? null : v;
                }
                map.put(type, slots);
            }
            bindings.put(charId, map);
        }
        plugin.getLogger().info("Bindings chargés pour " + bindings.size() + " personnage(s).");
    }

    /* ------------------------------------------------------------ access */

    /** The 5 slots bound on {@code characterId} for {@code type}.
     *  Always returns a fresh defensive copy; entries may be null. */
    public String[] get(UUID characterId, JutsuItemType type) {
        EnumMap<JutsuItemType, String[]> map = bindings.get(characterId);
        if (map == null) return new String[SLOTS];
        String[] slots = map.get(type);
        return slots == null ? new String[SLOTS] : slots.clone();
    }

    /** Ability id bound at {@code slot} (0-4), or null. */
    public String get(UUID characterId, JutsuItemType type, int slot) {
        if (slot < 0 || slot >= SLOTS) return null;
        EnumMap<JutsuItemType, String[]> map = bindings.get(characterId);
        if (map == null) return null;
        String[] slots = map.get(type);
        return slots == null ? null : slots[slot];
    }

    /** Bind {@code abilityId} (or null to clear) at {@code slot} (0-4). */
    public void set(UUID characterId, JutsuItemType type, int slot, String abilityId) {
        if (slot < 0 || slot >= SLOTS || characterId == null || type == null) return;
        EnumMap<JutsuItemType, String[]> map =
                bindings.computeIfAbsent(characterId, k -> new EnumMap<>(JutsuItemType.class));
        String[] slots = map.computeIfAbsent(type, k -> new String[SLOTS]);
        slots[slot] = (abilityId == null || abilityId.isBlank()) ? null : abilityId;
        dirty = true;
        scheduleSave();
    }

    /* -------------------------------------------------------------- save */

    /** Debounced async save — serialize on main, write off-thread. */
    private void scheduleSave() {
        if (savePending) return;
        savePending = true;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            savePending = false;
            if (!dirty) return;
            dirty = false;
            final String payload = serialize();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(payload));
        }, 60L); // batch edits within 3 s
    }

    /** Synchronous flush for onDisable. */
    public void saveSync() {
        if (!dirty && file.exists()) return;
        dirty = false;
        write(serialize());
    }

    private String serialize() {
        YamlConfiguration yml = new YamlConfiguration();
        for (var e : bindings.entrySet()) {
            for (var t : e.getValue().entrySet()) {
                String[] slots = t.getValue();
                boolean any = false;
                String[] out = new String[SLOTS];
                for (int i = 0; i < SLOTS; i++) {
                    out[i] = slots[i] == null ? "-" : slots[i];
                    if (slots[i] != null) any = true;
                }
                if (any) {
                    yml.set(e.getKey() + "." + t.getKey().name(),
                            java.util.Arrays.asList(out));
                }
            }
        }
        return yml.saveToString();
    }

    private void write(String payload) {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            File tmp = new File(file.getParentFile(), file.getName() + ".tmp");
            Files.writeString(tmp.toPath(), payload, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), file.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            plugin.getLogger().severe("Écriture bindings.yml impossible: " + ex.getMessage());
        }
    }
}
