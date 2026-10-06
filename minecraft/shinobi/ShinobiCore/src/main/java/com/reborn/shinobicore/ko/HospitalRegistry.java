package com.reborn.shinobicore.ko;

import com.reborn.shinobicore.ShinobiCore;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Level;

/**
 * Lits d'hôpital par village ({@code hospitals.yml}) : où se réveille un shinobi
 * rapatrié après un KO sans soin (lot KO-2). Clé = village du personnage en
 * minuscules ; {@code defaut} sert de repli, puis le spawn du monde.
 */
public final class HospitalRegistry {

    public static final String DEFAULT = "defaut";

    private final ShinobiCore plugin;
    private final File file;
    private final Map<String, Location> beds = new LinkedHashMap<>();

    public HospitalRegistry(ShinobiCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "hospitals.yml");
        load();
    }

    private void load() {
        beds.clear();
        if (!file.isFile()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("hopitaux");
        if (root == null) return;
        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            World w = Bukkit.getWorld(s.getString("world", ""));
            if (w == null) continue;
            beds.put(key, new Location(w, s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                    (float) s.getDouble("yaw"), (float) s.getDouble("pitch")));
        }
    }

    private void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        beds.forEach((k, l) -> {
            String p = "hopitaux." + k;
            cfg.set(p + ".world", l.getWorld().getName());
            cfg.set(p + ".x", l.getX()); cfg.set(p + ".y", l.getY()); cfg.set(p + ".z", l.getZ());
            cfg.set(p + ".yaw", l.getYaw()); cfg.set(p + ".pitch", l.getPitch());
        });
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Échec de la sauvegarde de hospitals.yml", ex);
        }
    }

    public static String key(String village) {
        return village == null || village.isBlank() ? DEFAULT : village.toLowerCase(Locale.ROOT).trim();
    }

    public void set(String village, Location loc) {
        beds.put(key(village), loc.clone());
        save();
    }

    public boolean remove(String village) {
        if (beds.remove(key(village)) == null) return false;
        save();
        return true;
    }

    /** Lit du village, sinon lit par défaut, sinon null (l'appelant prend le spawn). */
    public Location locationFor(String village) {
        Location l = beds.get(key(village));
        if (l == null) l = beds.get(DEFAULT);
        return l == null ? null : l.clone();
    }

    public Map<String, Location> all() { return Collections.unmodifiableMap(beds); }
}
