package com.reborn.shinobicore.map;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Lieux affichés sur la carte du monde du mod client ({@code places.yml}).
 *
 * <p>Deux sections :
 * <ul>
 *   <li>{@code maps.<id>} — une carte = une texture côté client
 *       ({@code assets/reborn/textures/gui/map/<id>.png}) liée à un monde Bukkit ;</li>
 *   <li>{@code places.<id>} — un lieu : carte, nom, type ({@link Type}), position.
 *       {@code y} absent = surface (bloc le plus haut) au moment de la téléportation.</li>
 * </ul>
 *
 * <p>Édité en jeu par {@code /carte set|del} ; relu par {@code /carte reload}.
 */
public final class PlaceRegistry {

    /** Rendu côté client : zone = grand libellé, les autres = marqueur losange. */
    public enum Type {
        ZONE, LIEU, PNJ, PORTE;

        public static Type parse(String s) {
            try {
                return valueOf(s.trim().toUpperCase(Locale.ROOT));
            } catch (Exception e) {
                return null;
            }
        }

        public String key() { return name().toLowerCase(Locale.ROOT); }
    }

    public record MapDef(String id, String title, String world) {}

    public record Place(String id, String map, String name, Type type, String world,
                        double x, Double y, double z, float yaw, float pitch) {

        /** Destination de téléportation ; null si le monde n'est pas chargé. */
        public Location location(org.bukkit.Server server) {
            World w = server.getWorld(world);
            if (w == null) return null;
            double ty = y != null ? y : w.getHighestBlockYAt((int) Math.floor(x), (int) Math.floor(z)) + 1;
            return new Location(w, x, ty, z, yaw, pitch);
        }
    }

    private final JavaPlugin plugin;
    private final File file;
    private final Map<String, MapDef> maps = new LinkedHashMap<>();
    private final Map<String, Place> places = new LinkedHashMap<>();

    public PlaceRegistry(JavaPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "places.yml");
    }

    public void load() {
        if (!file.exists()) plugin.saveResource("places.yml", false);
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        maps.clear();
        places.clear();
        ConfigurationSection ms = y.getConfigurationSection("maps");
        if (ms != null) {
            for (String id : ms.getKeys(false)) {
                ConfigurationSection s = ms.getConfigurationSection(id);
                if (s == null) continue;
                maps.put(id, new MapDef(id, s.getString("title", id), s.getString("world", "world")));
            }
        }
        ConfigurationSection ps = y.getConfigurationSection("places");
        if (ps != null) {
            for (String id : ps.getKeys(false)) {
                ConfigurationSection s = ps.getConfigurationSection(id);
                if (s == null) continue;
                Type t = Type.parse(s.getString("type", "lieu"));
                String map = s.getString("map", maps.isEmpty() ? "" : maps.keySet().iterator().next());
                MapDef def = maps.get(map);
                if (t == null || def == null) {
                    plugin.getLogger().warning("places.yml : lieu '" + id + "' ignoré (type ou carte inconnu).");
                    continue;
                }
                places.put(id, new Place(id, map, s.getString("name", id), t,
                        s.getString("world", def.world()),
                        s.getDouble("x"), s.contains("y") ? s.getDouble("y") : null, s.getDouble("z"),
                        (float) s.getDouble("yaw"), (float) s.getDouble("pitch")));
            }
        }
        plugin.getLogger().info("Carte : " + maps.size() + " carte(s), " + places.size() + " lieu(x).");
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (MapDef m : maps.values()) {
            y.set("maps." + m.id() + ".title", m.title());
            y.set("maps." + m.id() + ".world", m.world());
        }
        for (Place p : places.values()) {
            String k = "places." + p.id() + ".";
            y.set(k + "map", p.map());
            y.set(k + "name", p.name());
            y.set(k + "type", p.type().key());
            y.set(k + "world", p.world());
            y.set(k + "x", round(p.x()));
            if (p.y() != null) y.set(k + "y", round(p.y()));
            y.set(k + "z", round(p.z()));
            if (p.yaw() != 0f) y.set(k + "yaw", (double) Math.round(p.yaw()));
            if (p.pitch() != 0f) y.set(k + "pitch", (double) Math.round(p.pitch()));
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("places.yml : sauvegarde impossible — " + e.getMessage());
        }
    }

    private static double round(double v) { return Math.round(v * 10.0) / 10.0; }

    /* ------------------------------------------------------------ requêtes */

    public Collection<MapDef> maps() { return maps.values(); }

    public MapDef map(String id) { return maps.get(id); }

    /** Carte du monde où se trouve le joueur, sinon la première déclarée. */
    public MapDef mapFor(World w) {
        for (MapDef m : maps.values()) if (m.world().equalsIgnoreCase(w.getName())) return m;
        return maps.isEmpty() ? null : maps.values().iterator().next();
    }

    public Place place(String id) { return places.get(id); }

    public Collection<Place> places() { return places.values(); }

    public List<Place> placesOf(String map) {
        List<Place> out = new ArrayList<>();
        for (Place p : places.values()) if (p.map().equals(map)) out.add(p);
        return out;
    }

    /** Crée ou remplace un lieu à la position donnée (la carte = celle du monde courant). */
    public Place put(String id, Type type, String name, Location at) {
        MapDef def = mapFor(at.getWorld());
        if (def == null) return null;
        Place p = new Place(id, def.id(), name, type, at.getWorld().getName(),
                at.getX(), at.getY(), at.getZ(), at.getYaw(), at.getPitch());
        places.put(id, p);
        save();
        return p;
    }

    public boolean remove(String id) {
        boolean ok = places.remove(id) != null;
        if (ok) save();
        return ok;
    }
}
