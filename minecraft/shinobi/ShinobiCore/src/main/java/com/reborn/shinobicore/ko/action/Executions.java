package com.reborn.shinobicore.ko.action;

import com.reborn.shinobicore.ShinobiCore;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Exécutions : mises à mort animées, jouées sur un corps après une RPK validée (cosmétiques, <b>vendables</b>).
 *
 * <p>Catalogue {@code executions.yml} (dossier du plugin, rechargeable) : nom, emote du tueur et de la victime
 * (fichiers EmoteCraft déposés dans {@code emotes/}, voir EmoteManager), durée, et l'instant où la mort tombe.
 * Une exécution dont l'emote n'existe pas encore se joue sans animation (pose seule).
 *
 * <p>Possession : par joueur (compte, pas personnage), dans {@code executions-joueurs.yml} — donnée par le staff
 * ({@code /execution donner}) ou plus tard par la boutique ; ou par la permission
 * {@code shinobicore.execution.<id>}. Une entrée {@code gratuite: true} appartient à tout le monde.
 */
public final class Executions {

    public record Def(String id, String name, String killerEmote, String victimEmote, int durationTicks,
                      int deathTick, boolean free) {}

    private final ShinobiCore plugin;
    private final File catalogFile;
    private final File ownedFile;
    private final Map<String, Def> defs = new LinkedHashMap<>();
    private final Map<UUID, Set<String>> owned = new LinkedHashMap<>();

    public Executions(ShinobiCore plugin) {
        this.plugin = plugin;
        this.catalogFile = new File(plugin.getDataFolder(), "executions.yml");
        this.ownedFile = new File(plugin.getDataFolder(), "executions-joueurs.yml");
        reload();
    }

    public void reload() {
        if (!catalogFile.exists()) writeDefaults();
        defs.clear();
        ConfigurationSection root = YamlConfiguration.loadConfiguration(catalogFile).getConfigurationSection("executions");
        if (root != null) {
            for (String id : root.getKeys(false)) {
                ConfigurationSection s = root.getConfigurationSection(id);
                if (s == null) continue;
                int dur = Math.max(10, s.getInt("duree-ticks", 60));
                defs.put(id, new Def(id, s.getString("nom", id), s.getString("emote-tueur", ""),
                        s.getString("emote-victime", ""), dur,
                        Math.max(1, Math.min(dur, s.getInt("mort-au-tick", dur * 3 / 4))), s.getBoolean("gratuite", false)));
            }
        }
        owned.clear();
        YamlConfiguration o = YamlConfiguration.loadConfiguration(ownedFile);
        for (String k : o.getKeys(false)) {
            try { owned.put(UUID.fromString(k), new LinkedHashSet<>(o.getStringList(k))); }
            catch (IllegalArgumentException ignored) { }
        }
    }

    private void writeDefaults() {
        YamlConfiguration y = new YamlConfiguration();
        y.options().setHeader(List.of(
                "Exécutions (mises à mort animées après une RPK validée). Cosmétiques, vendables.",
                "emote-tueur / emote-victime : clés d'emotes EmoteCraft (plugins/ShinobiCore/emotes/).",
                "duree-ticks : durée de la scène (20 = 1 s) · mort-au-tick : instant où le personnage meurt.",
                "gratuite: true = disponible pour tous. Sinon : /execution donner <joueur> <id>, ou la",
                "permission shinobicore.execution.<id>."));
        put(y, "coup_de_grace", "Coup de grâce", "execution_coup_de_grace", "execution_coup_de_grace_victime", 60, 42, false);
        put(y, "lame_dans_le_dos", "Lame dans le dos", "execution_lame_dos", "execution_lame_dos_victime", 70, 50, false);
        put(y, "etranglement", "Étranglement", "execution_etranglement", "execution_etranglement_victime", 100, 85, false);
        try {
            plugin.getDataFolder().mkdirs();
            y.save(catalogFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "executions.yml : écriture impossible", e);
        }
    }

    private static void put(YamlConfiguration y, String id, String name, String ke, String ve, int dur, int death, boolean free) {
        String k = "executions." + id + ".";
        y.set(k + "nom", name);
        y.set(k + "emote-tueur", ke);
        y.set(k + "emote-victime", ve);
        y.set(k + "duree-ticks", dur);
        y.set(k + "mort-au-tick", death);
        y.set(k + "gratuite", free);
    }

    public Def get(String id) { return defs.get(id); }

    public List<Def> all() { return new ArrayList<>(defs.values()); }

    public boolean owns(Player p, Def d) {
        return d.free() || p.hasPermission("shinobicore.execution." + d.id())
                || owned.getOrDefault(p.getUniqueId(), Set.of()).contains(d.id());
    }

    public List<Def> ownedBy(Player p) {
        List<Def> out = new ArrayList<>();
        for (Def d : defs.values()) if (owns(p, d)) out.add(d);
        return out;
    }

    public boolean give(UUID player, String id, boolean add) {
        if (!defs.containsKey(id)) return false;
        Set<String> s = owned.computeIfAbsent(player, k -> new LinkedHashSet<>());
        boolean changed = add ? s.add(id) : s.remove(id);
        if (changed) save();
        return true;
    }

    private void save() {
        YamlConfiguration o = new YamlConfiguration();
        owned.forEach((k, v) -> { if (!v.isEmpty()) o.set(k.toString(), new ArrayList<>(v)); });
        try { o.save(ownedFile); } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "executions-joueurs.yml : écriture impossible", e);
        }
    }
}
