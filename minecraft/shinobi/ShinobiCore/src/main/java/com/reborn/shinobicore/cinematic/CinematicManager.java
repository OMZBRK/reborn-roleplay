package com.reborn.shinobicore.cinematic;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Registry + persistence for cinematics. Owns {@code cinematics.yml}, the
 * {@code introCinematicName} pointer (the designated first-play intro), the
 * playback engine and the live anchor-editor sessions. Exposed via
 * {@code ShinobiCore#cinematics()}.
 *
 * <p>Mirrors the {@code DummyManager} lifecycle: {@link #load()} in
 * {@code onEnable}, {@link #save()} in {@code onDisable} (plus on every
 * mutation), and {@link #shutdown()} to release frozen players / restore
 * editor inventories before the server stops.
 */
public final class CinematicManager {

    private final ShinobiCore plugin;
    private final File file;

    /** lower-case name → cinematic. */
    private final Map<String, Cinematic> byName = new LinkedHashMap<>();
    /** the designated first-play intro (nullable). */
    private String introCinematicName;

    private final CinematicPlayback playback;
    /** live anchor-editor sessions, per player. */
    private final Map<UUID, CinematicEditorSession> editors = new HashMap<>();

    public CinematicManager(ShinobiCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "cinematics.yml");
        this.playback = new CinematicPlayback(plugin, this);
    }

    public ShinobiCore plugin() { return plugin; }

    /* ----------------------------------------------------------- registry */

    public Cinematic get(String name) {
        return name == null ? null : byName.get(name.toLowerCase(Locale.ROOT));
    }

    public boolean exists(String name) {
        return name != null && byName.containsKey(name.toLowerCase(Locale.ROOT));
    }

    public Collection<Cinematic> all() { return byName.values(); }

    public List<String> names() {
        List<String> out = new ArrayList<>(byName.size());
        for (Cinematic c : byName.values()) out.add(c.name());
        return out;
    }

    /** Get the cinematic by name, creating + persisting an empty one if new. */
    public Cinematic getOrCreate(String name) {
        Cinematic c = get(name);
        if (c == null) {
            c = new Cinematic(name);
            byName.put(name.toLowerCase(Locale.ROOT), c);
            save();
        }
        return c;
    }

    public boolean delete(String name) {
        if (name == null) return false;
        Cinematic removed = byName.remove(name.toLowerCase(Locale.ROOT));
        if (removed == null) return false;
        if (name.equalsIgnoreCase(introCinematicName)) introCinematicName = null;
        save();
        return true;
    }

    public String introCinematicName() { return introCinematicName; }

    public void setIntroCinematicName(String name) {
        this.introCinematicName = blankToNull(name);
        save();
    }

    /** The intro cinematic, or {@code null} when none is designated/loaded. */
    public Cinematic introCinematic() { return get(introCinematicName); }

    /* ----------------------------------------------------------- playback */

    public CinematicPlayback playback() { return playback; }

    public void play(Player p, Cinematic c, boolean intro, ShinobiCharacter ch) {
        playback.play(p, c, intro, ch);
    }

    public void play(Player p, Cinematic c) {
        playback.play(p, c, false, null);
    }

    public void stop(Player p) { playback.stop(p, true); }

    public boolean isPlaying(Player p) { return playback.isPlaying(p); }

    /* ------------------------------------------------------------- editors */

    public CinematicEditorSession editor(UUID id) { return editors.get(id); }

    public boolean isEditing(UUID id) { return editors.containsKey(id); }

    public void putEditor(UUID id, CinematicEditorSession session) {
        editors.put(id, session);
    }

    public void removeEditor(UUID id) { editors.remove(id); }

    /* ----------------------------------------------------------- lifecycle */

    /** Release every frozen player + restore every editor inventory, then
     *  persist. Called from {@code ShinobiCore#onDisable}. */
    public void shutdown() {
        playback.stopAll();
        for (CinematicEditorSession s : new ArrayList<>(editors.values())) {
            s.abort();
        }
        editors.clear();
        save();
    }

    /* --------------------------------------------------------- persistence */

    public void load() {
        byName.clear();
        introCinematicName = null;
        if (!file.isFile()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        introCinematicName = blankToNull(cfg.getString("intro-cinematic"));

        ConfigurationSection root = cfg.getConfigurationSection("cinematics");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            Cinematic cine = new Cinematic(key);
            for (Map<?, ?> m : s.getMapList("anchors")) {
                cine.addAnchor(readAnchor(m));
            }
            byName.put(key.toLowerCase(Locale.ROOT), cine);
        }
        plugin.getLogger().info(byName.size() + " cinématique(s) chargée(s).");
    }

    public void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        cfg.set("intro-cinematic", introCinematicName);
        for (Cinematic cine : byName.values()) {
            List<Map<String, Object>> anchorMaps = new ArrayList<>(cine.size());
            for (CinematicAnchor a : cine.anchors()) anchorMaps.add(writeAnchor(a));
            cfg.set("cinematics." + cine.name() + ".anchors", anchorMaps);
        }
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Failed to save cinematics.yml", ex);
        }
    }

    private Map<String, Object> writeAnchor(CinematicAnchor a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("world", a.worldName());
        m.put("x", a.x());
        m.put("y", a.y());
        m.put("z", a.z());
        m.put("yaw", (double) a.yaw());
        m.put("pitch", (double) a.pitch());
        if (a.title() != null)      m.put("title", a.title());
        if (a.titleColor() != null) m.put("title-color", a.titleColor().toString());
        if (a.text1() != null)      m.put("text1", a.text1());
        if (a.color1() != null)     m.put("color1", a.color1().toString());
        if (a.text2() != null)      m.put("text2", a.text2());
        if (a.color2() != null)     m.put("color2", a.color2().toString());
        if (a.text3() != null)      m.put("text3", a.text3());
        if (a.color3() != null)     m.put("color3", a.color3().toString());
        m.put("duration-ticks", a.durationTicks());
        if (a.sound() != null)      m.put("sound", a.sound());
        return m;
    }

    private CinematicAnchor readAnchor(Map<?, ?> m) {
        CinematicAnchor a = new CinematicAnchor();
        a.setWorldName(str(m, "world"));
        a.setX(dbl(m, "x"));
        a.setY(dbl(m, "y"));
        a.setZ(dbl(m, "z"));
        a.setYaw(flt(m, "yaw"));
        a.setPitch(flt(m, "pitch"));
        a.setTitle(str(m, "title"));
        a.setTitleColor(color(m, "title-color"));
        a.setText1(str(m, "text1"));
        a.setColor1(color(m, "color1"));
        a.setText2(str(m, "text2"));
        a.setColor2(color(m, "color2"));
        a.setText3(str(m, "text3"));
        a.setColor3(color(m, "color3"));
        a.setDurationTicks(intt(m, "duration-ticks", 100));
        a.setSound(str(m, "sound"));
        return a;
    }

    /* ------------------------------------------------------ map coercion */

    private static String str(Map<?, ?> m, String k) {
        Object o = m.get(k);
        return o == null ? null : o.toString();
    }

    private static double dbl(Map<?, ?> m, String k) {
        Object o = m.get(k);
        return o instanceof Number n ? n.doubleValue() : 0.0;
    }

    private static float flt(Map<?, ?> m, String k) {
        Object o = m.get(k);
        return o instanceof Number n ? n.floatValue() : 0f;
    }

    private static int intt(Map<?, ?> m, String k, int def) {
        Object o = m.get(k);
        return o instanceof Number n ? n.intValue() : def;
    }

    private static NamedTextColor color(Map<?, ?> m, String k) {
        Object o = m.get(k);
        if (o == null) return null;
        return NamedTextColor.NAMES.value(o.toString().toLowerCase(Locale.ROOT));
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
