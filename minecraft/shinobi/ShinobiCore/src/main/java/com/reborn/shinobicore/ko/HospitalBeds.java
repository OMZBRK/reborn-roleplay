package com.reborn.shinobicore.ko;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.ko.zone.TrainingZone;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Lits des hôpitaux (Infirmerie) : chaque zone {@link TrainingZone.Kind#HOPITAL} a autant de lits qu'on veut,
 * enregistrés en jeu en visant le meuble ({@code hospital-beds.yml}). Un lit est un meuble Nexo dont l'id contient
 * l'un des mots de {@code ko.lits-nexo} (par défaut « lit », « bed », « futon »), ou un lit vanilla.
 *
 * <p>Au rapatriement, le blessé prend le premier lit libre de l'hôpital de son village. Un lit est occupé s'il a
 * été attribué il y a moins de {@code ko.lit-occupe-minutes} et que son occupant est encore tout près.
 */
public final class HospitalBeds {

    /** Un lit : position du meuble (centre) et id du meuble. */
    public record Bed(String world, double x, double y, double z, String furniture) {
        public Location location() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z);
        }

        String key() { return world + ":" + Math.round(x * 2) + ":" + Math.round(y * 2) + ":" + Math.round(z * 2); }
    }

    private record Occupancy(UUID player, long since) { }

    private static final NamespacedKey NEXO_FURNITURE = NamespacedKey.fromString("nexo:furniture");

    private final ShinobiCore plugin;
    private final File file;
    private final Map<String, List<Bed>> beds = new LinkedHashMap<>();   // id de zone → lits
    private final Map<String, Occupancy> occupied = new HashMap<>();     // clé de lit →

    public HospitalBeds(ShinobiCore plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "hospital-beds.yml");
        load();
    }

    /* ============================================================ registre */

    public synchronized List<Bed> of(String zoneId) {
        return Collections.unmodifiableList(beds.getOrDefault(zoneId, List.of()));
    }

    public synchronized boolean add(String zoneId, Bed bed) {
        List<Bed> list = beds.computeIfAbsent(zoneId, k -> new ArrayList<>());
        for (Bed b : list) if (b.key().equals(bed.key())) return false;
        list.add(bed);
        save();
        return true;
    }

    /** Retire le lit le plus proche de {@code at} (à moins d'1,5 bloc). */
    public synchronized Bed removeNear(String zoneId, Location at) {
        List<Bed> list = beds.get(zoneId);
        if (list == null) return null;
        for (Bed b : list) {
            Location l = b.location();
            if (l != null && l.getWorld().equals(at.getWorld()) && l.distanceSquared(at) < 2.25) {
                list.remove(b);
                save();
                return b;
            }
        }
        return null;
    }

    public synchronized boolean remove(String zoneId, int index) {
        List<Bed> list = beds.get(zoneId);
        if (list == null || index < 0 || index >= list.size()) return false;
        list.remove(index);
        save();
        return true;
    }

    public synchronized void dropZone(String zoneId) {
        if (beds.remove(zoneId) != null) save();
    }

    /** Occupant actuel du lit, ou null. */
    public synchronized UUID occupant(Bed bed) {
        Occupancy o = occupied.get(bed.key());
        if (o == null) return null;
        long ttl = Math.max(1, plugin.getConfig().getLong("ko.lit-occupe-minutes", 15)) * 60_000L;
        Player p = Bukkit.getPlayer(o.player());
        Location l = bed.location();
        boolean near = p != null && l != null && p.getWorld().equals(l.getWorld()) && p.getLocation().distanceSquared(l) < 9;
        if (System.currentTimeMillis() - o.since() > ttl || !near) {
            occupied.remove(bed.key());
            return null;
        }
        return o.player();
    }

    /**
     * Lit libre pour un blessé du {@code village} : d'abord les hôpitaux de ce village, puis ceux sans village.
     * Marque le lit comme occupé. Null s'il n'y a ni hôpital ni lit libre.
     */
    public synchronized Bed claim(String village, UUID player) {
        String v = village == null ? "" : village.trim().toLowerCase(Locale.ROOT);
        for (int pass = 0; pass < 2; pass++) {
            for (TrainingZone z : plugin.trainingZones().all()) {
                if (z.kind() != TrainingZone.Kind.HOPITAL) continue;
                String zv = z.village().trim().toLowerCase(Locale.ROOT);
                if (pass == 0 ? !zv.equals(v) || v.isEmpty() : !zv.isEmpty()) continue;
                for (Bed b : beds.getOrDefault(z.id(), List.of())) {
                    if (occupant(b) != null) continue;
                    occupied.put(b.key(), new Occupancy(player, System.currentTimeMillis()));
                    return b;
                }
            }
        }
        return null;
    }

    /** Hôpital du village (ou sans village), pour un réveil à l'entrée faute de lit libre. */
    public TrainingZone hospitalFor(String village) {
        String v = village == null ? "" : village.trim().toLowerCase(Locale.ROOT);
        TrainingZone fallback = null;
        for (TrainingZone z : plugin.trainingZones().all()) {
            if (z.kind() != TrainingZone.Kind.HOPITAL) continue;
            String zv = z.village().trim().toLowerCase(Locale.ROOT);
            if (!v.isEmpty() && zv.equals(v)) return z;
            if (zv.isEmpty() && fallback == null) fallback = z;
        }
        return fallback;
    }

    /* ============================================================ meubles */

    /**
     * Le lit visé près de {@code at} : un meuble Nexo de lit (entité d'affichage marquée {@code nexo:furniture})
     * ou un lit vanilla. Null si rien ne correspond.
     */
    public Bed probe(Location at) {
        if (at == null || at.getWorld() == null) return null;
        List<String> words = plugin.getConfig().getStringList("ko.lits-nexo");
        if (words.isEmpty()) words = List.of("lit", "bed", "futon");
        Entity best = null;
        String bestId = null;
        double bestD = Double.MAX_VALUE;
        for (Entity e : at.getWorld().getNearbyEntities(at, 1.6, 1.6, 1.6)) {
            String id = NEXO_FURNITURE == null ? null
                    : e.getPersistentDataContainer().get(NEXO_FURNITURE, PersistentDataType.STRING);
            if (id == null) continue;
            String low = id.toLowerCase(Locale.ROOT);
            boolean bed = false;
            for (String w : words) if (low.contains(w.toLowerCase(Locale.ROOT))) { bed = true; break; }
            if (!bed) continue;
            double d = e.getLocation().distanceSquared(at);
            if (d < bestD) { bestD = d; best = e; bestId = id; }
        }
        if (best != null) {
            Location l = best.getLocation();
            return new Bed(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), bestId);
        }
        Block b = at.getBlock();
        if (Tag.BEDS.isTagged(b.getType())) {
            return new Bed(b.getWorld().getName(), b.getX() + 0.5, b.getY() + 0.56, b.getZ() + 0.5,
                    "minecraft:" + b.getType().name().toLowerCase(Locale.ROOT));
        }
        return null;
    }

    /* ============================================================ disque */

    private void load() {
        if (!file.isFile()) return;
        YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = cfg.getConfigurationSection("hopitaux");
        if (root == null) return;
        for (String zone : root.getKeys(false)) {
            List<Bed> list = new ArrayList<>();
            for (Map<?, ?> m : root.getMapList(zone)) {
                try {
                    list.add(new Bed(String.valueOf(m.get("world")), ((Number) m.get("x")).doubleValue(),
                            ((Number) m.get("y")).doubleValue(), ((Number) m.get("z")).doubleValue(),
                            String.valueOf(m.get("meuble"))));
                } catch (RuntimeException ignored) { }
            }
            beds.put(zone, list);
        }
    }

    private void save() {
        YamlConfiguration cfg = new YamlConfiguration();
        beds.forEach((zone, list) -> {
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Bed b : list) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("world", b.world()); m.put("x", b.x()); m.put("y", b.y()); m.put("z", b.z());
                m.put("meuble", b.furniture());
                rows.add(m);
            }
            cfg.set("hopitaux." + zone, rows);
        });
        try {
            if (!plugin.getDataFolder().exists()) plugin.getDataFolder().mkdirs();
            cfg.save(file);
        } catch (IOException ex) {
            plugin.getLogger().log(Level.WARNING, "Échec de la sauvegarde de hospital-beds.yml", ex);
        }
    }
}
