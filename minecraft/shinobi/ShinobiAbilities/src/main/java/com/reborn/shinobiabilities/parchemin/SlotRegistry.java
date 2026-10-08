package com.reborn.shinobiabilities.parchemin;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Slots du rang S : chaque technique S a un nombre maximum de détenteurs ({@code parchemins.slots-s}, réglable par
 * technique dans {@code parchemins.slots-par-technique}). Occupe un slot : chaque parchemin S existant
 * ({@code rouleau:<uuid>}) et chaque personnage qui connaît la technique ({@code perso:<uuid>}). Fichier
 * {@code parchemins-s.yml}.
 */
public final class SlotRegistry {

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, Set<String>> holders = new HashMap<>();
    private int defaultMax = 2;
    private final Map<String, Integer> maxById = new HashMap<>();

    public SlotRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "parchemins-s.yml");
    }

    public void loadConfig(ConfigurationSection sec) {
        maxById.clear();
        if (sec == null) return;
        defaultMax = Math.max(0, sec.getInt("slots-s", 2));
        ConfigurationSection per = sec.getConfigurationSection("slots-par-technique");
        if (per != null) for (String k : per.getKeys(false)) maxById.put(k, Math.max(0, per.getInt(k)));
    }

    public void load() {
        holders.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String id : y.getKeys(false)) holders.put(id, new LinkedHashSet<>(y.getStringList(id)));
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        holders.forEach((id, set) -> { if (!set.isEmpty()) y.set(id, set.stream().toList()); });
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[parchemins] Sauvegarde des slots S échouée : " + e.getMessage());
        }
    }

    public int max(String id) { return maxById.getOrDefault(id, defaultMax); }

    public int used(String id) { return holders.getOrDefault(id, Set.of()).size(); }

    public boolean free(String id) { return used(id) < max(id); }

    public Set<String> holders(String id) { return holders.getOrDefault(id, Set.of()); }

    public void reserve(String id, String token) {
        holders.computeIfAbsent(id, k -> new LinkedHashSet<>()).add(token);
        save();
    }

    public void release(String id, String token) {
        Set<String> s = holders.get(id);
        if (s != null && s.remove(token)) save();
    }

    /** Le parchemin {@code scrollId} est appris par {@code charId} : son slot passe du rouleau au personnage. */
    public void transfer(String id, String scrollId, String charId) {
        Set<String> s = holders.computeIfAbsent(id, k -> new LinkedHashSet<>());
        s.remove("rouleau:" + scrollId);
        s.add("perso:" + charId);
        save();
    }
}
