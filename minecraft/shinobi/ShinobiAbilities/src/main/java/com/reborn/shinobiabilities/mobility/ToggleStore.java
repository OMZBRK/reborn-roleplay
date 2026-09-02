package com.reborn.shinobiabilities.mobility;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Per-character UNLOCKED {@link MobilityActionSlot}s, persisted in this
 * plugin's {@code mobility-unlocks.yml}.
 *
 * <p>Default: nothing unlocked — every mobility ability starts LOCKED and must
 * be earned (a training-ground parkour) or granted by staff via
 * {@code /mobility unlock}. The six ability classes gate on {@link #isEnabled},
 * which now means "is this slot unlocked for the character".
 *
 * <p>(Formerly the toggle store. The on/off toggle menu was removed and this
 * became the unlock ledger; the class + accessor names are kept so the ability
 * constructors don't churn.) Serialize-on-main / write-async pattern.
 */
public final class ToggleStore {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<UUID, EnumSet<MobilityActionSlot>> unlocked = new HashMap<>();
    private boolean dirty;
    private boolean savePending;

    public ToggleStore(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "mobility-unlocks.yml");
    }

    public void load() {
        unlocked.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String key : yml.getKeys(false)) {
            UUID id;
            try { id = UUID.fromString(key); }
            catch (IllegalArgumentException ex) { continue; }
            EnumSet<MobilityActionSlot> set = EnumSet.noneOf(MobilityActionSlot.class);
            for (String name : yml.getStringList(key)) {
                MobilityActionSlot slot = MobilityActionSlot.from(name);
                if (slot != null) set.add(slot);
            }
            if (!set.isEmpty()) unlocked.put(id, set);
        }
    }

    /** True when {@code slot} is UNLOCKED for the character (default false).
     *  The ability classes call this as their gate. */
    public boolean isEnabled(UUID characterId, MobilityActionSlot slot) {
        EnumSet<MobilityActionSlot> set = unlocked.get(characterId);
        return set != null && set.contains(slot);
    }

    /** Alias reading better at grant/inspect call sites. */
    public boolean isUnlocked(UUID characterId, MobilityActionSlot slot) {
        return isEnabled(characterId, slot);
    }

    /** Unlock one slot. Returns true if it was newly unlocked. */
    public boolean unlock(UUID characterId, MobilityActionSlot slot) {
        EnumSet<MobilityActionSlot> set = unlocked.computeIfAbsent(
                characterId, k -> EnumSet.noneOf(MobilityActionSlot.class));
        boolean added = set.add(slot);
        if (added) { dirty = true; scheduleSave(); }
        return added;
    }

    /** Unlock several slots at once (a training reward). Returns count newly added. */
    public int unlockAll(UUID characterId, Iterable<MobilityActionSlot> slots) {
        EnumSet<MobilityActionSlot> set = unlocked.computeIfAbsent(
                characterId, k -> EnumSet.noneOf(MobilityActionSlot.class));
        int added = 0;
        for (MobilityActionSlot s : slots) if (set.add(s)) added++;
        if (added > 0) { dirty = true; scheduleSave(); }
        return added;
    }

    /** Lock one slot (revoke). Returns true if it was unlocked before. */
    public boolean lock(UUID characterId, MobilityActionSlot slot) {
        EnumSet<MobilityActionSlot> set = unlocked.get(characterId);
        if (set == null || !set.remove(slot)) return false;
        if (set.isEmpty()) unlocked.remove(characterId);
        dirty = true; scheduleSave();
        return true;
    }

    /** Lock everything for a character. */
    public void lockAll(UUID characterId) {
        if (unlocked.remove(characterId) != null) { dirty = true; scheduleSave(); }
    }

    /** Snapshot of unlocked slots for the character. */
    public EnumSet<MobilityActionSlot> unlockedOf(UUID characterId) {
        EnumSet<MobilityActionSlot> set = unlocked.get(characterId);
        return set == null ? EnumSet.noneOf(MobilityActionSlot.class) : EnumSet.copyOf(set);
    }

    /* ----------------------------------------------------------- persistence */

    private void scheduleSave() {
        if (savePending) return;
        savePending = true;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            savePending = false;
            if (!dirty) return;
            dirty = false;
            final String payload = serialize();
            Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> write(payload));
        }, 40L);
    }

    public void saveSync() {
        if (!dirty && file.exists()) return;
        dirty = false;
        write(serialize());
    }

    private String serialize() {
        YamlConfiguration yml = new YamlConfiguration();
        for (var e : unlocked.entrySet()) {
            List<String> names = new ArrayList<>();
            for (MobilityActionSlot s : e.getValue()) names.add(s.name());
            yml.set(e.getKey().toString(), names);
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
            plugin.getLogger().severe("Écriture mobility-unlocks.yml impossible: " + ex.getMessage());
        }
    }
}
