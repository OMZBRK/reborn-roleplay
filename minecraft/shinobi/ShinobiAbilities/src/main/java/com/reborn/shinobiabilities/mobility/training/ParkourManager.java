package com.reborn.shinobiabilities.mobility.training;

import com.reborn.shinobiabilities.mobility.MobilityActionSlot;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Registry + persistence for training-ground parkours ({@code parkours.yml}).
 *
 * <p>Each parkour is an ordered chain of {@link ParkourAnchor}s in one world
 * plus a reward set of {@link MobilityActionSlot}s granted on completion.
 * Editing (a temporary-hotbar editor) and running (the reaction mini-game) are
 * owned by sibling classes; this class is the data spine they share.
 */
public final class ParkourManager {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, Parkour> parkours = new LinkedHashMap<>();
    private final Map<UUID, ParkourEditorSession> editors = new HashMap<>();
    private BukkitTask editorTask;

    public ParkourManager(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "parkours.yml");
    }

    /* ------------------------------------------------------------ storage */

    public void load() {
        parkours.clear();
        if (!file.exists()) return;
        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        for (String name : yml.getKeys(false)) {
            ConfigurationSection s = yml.getConfigurationSection(name);
            if (s == null) continue;
            String world = s.getString("world");
            if (world == null) continue;
            Parkour pk = new Parkour(name, world);
            for (String r : s.getStringList("rewards")) {
                MobilityActionSlot slot = MobilityActionSlot.from(r);
                if (slot != null) pk.rewards().add(slot);
            }
            for (String raw : s.getStringList("anchors")) {
                ParkourAnchor a = parseAnchor(raw);
                if (a != null) pk.anchors().add(a);
            }
            if (!pk.anchors().isEmpty()) {
                parkours.put(name.toLowerCase(Locale.ROOT), pk);
            }
        }
        plugin.getLogger().info(parkours.size() + " parcours d'entraînement chargé(s).");
    }

    private static ParkourAnchor parseAnchor(String raw) {
        String[] p = raw.split(",");
        if (p.length < 3) return null;
        try {
            double x = Double.parseDouble(p[0]);
            double y = Double.parseDouble(p[1]);
            double z = Double.parseDouble(p[2]);
            if (p.length >= 7) {
                return new ParkourAnchor(x, y, z,
                        ParkourAnchor.Key.from(p[3]),
                        ParkourAnchor.Zone.from(p[4]),
                        Integer.parseInt(p[5].trim()),
                        Integer.parseInt(p[6].trim()));
            }
            return new ParkourAnchor(x, y, z);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public void saveSync() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Parkour pk : parkours.values()) {
            yml.set(pk.name() + ".world", pk.world());
            List<String> rewards = new ArrayList<>();
            for (MobilityActionSlot s : pk.rewards()) rewards.add(s.name());
            yml.set(pk.name() + ".rewards", rewards);
            List<String> anchors = new ArrayList<>(pk.anchors().size());
            for (ParkourAnchor a : pk.anchors()) {
                anchors.add(a.x() + "," + a.y() + "," + a.z() + ","
                        + a.key().name() + "," + a.zone().name() + ","
                        + a.loops() + "," + a.loopTicks());
            }
            yml.set(pk.name() + ".anchors", anchors);
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            Files.writeString(file.toPath(), yml.saveToString(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            plugin.getLogger().severe("Écriture parkours.yml impossible: " + ex.getMessage());
        }
    }

    /* --------------------------------------------------------------- CRUD */

    /** Create + register an empty parkour; null if the name is taken. */
    public Parkour create(String name, String world) {
        if (parkours.containsKey(name.toLowerCase(Locale.ROOT))) return null;
        Parkour pk = new Parkour(name, world);
        parkours.put(name.toLowerCase(Locale.ROOT), pk);
        return pk;
    }

    /** Persist current state (called by the editor on finish). */
    public void save() { saveSync(); }

    /** Register (or replace) a parkour and persist — the editor's commit. */
    public void commit(Parkour pk) {
        parkours.put(pk.name().toLowerCase(Locale.ROOT), pk);
        saveSync();
    }

    /* --------------------------------------------------- editor sessions */

    public boolean isEditing(UUID id) { return editors.containsKey(id); }
    public ParkourEditorSession editor(UUID id) { return editors.get(id); }
    public void putEditor(UUID id, ParkourEditorSession s) {
        editors.put(id, s);
        ensureEditorTask();
    }
    public void removeEditor(UUID id) { editors.remove(id); }

    /** Editor-only carpet + glow preview refresh (~½s) while anyone is editing. */
    private void ensureEditorTask() {
        if (editorTask != null) return;
        editorTask = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (editors.isEmpty()) { editorTask.cancel(); editorTask = null; return; }
            for (UUID id : editors.keySet().toArray(new UUID[0])) {
                ParkourEditorSession s = editors.get(id);
                if (s == null) continue;
                Player pl = plugin.getServer().getPlayer(id);
                if (pl != null && pl.isOnline()) s.previewTick(pl);
            }
        }, 10L, 10L);
    }

    /** Abort every open editor (restore inventories + previews) — on disable. */
    public void cancelEditors() {
        for (UUID id : editors.keySet().toArray(new UUID[0])) {
            ParkourEditorSession s = editors.get(id);
            if (s != null) s.abort();
        }
        editors.clear();
        if (editorTask != null) { editorTask.cancel(); editorTask = null; }
    }

    public boolean delete(String name) {
        boolean removed = parkours.remove(name.toLowerCase(Locale.ROOT)) != null;
        if (removed) saveSync();
        return removed;
    }

    public Parkour byName(String name) {
        return name == null ? null : parkours.get(name.toLowerCase(Locale.ROOT));
    }

    public List<String> names() {
        List<String> out = new ArrayList<>();
        for (Parkour pk : parkours.values()) out.add(pk.name());
        return out;
    }

    public Collection<Parkour> all() { return parkours.values(); }
}
