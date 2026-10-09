package com.reborn.shinobiabilities.parchemin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reborn.shinobiabilities.techniques.ParcheminItems;
import com.reborn.shinobicore.technique.Ability;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Bibliothèques à tirage (docs/PROPOSITION_PARCHEMINS.md §3.1).
 *
 * <ul>
 *   <li>Le staff pose une bibliothèque sur un bloc ou un meuble ({@code /bibliotheque creer}) et la règle depuis
 *       l'écran du mod ({@code /bibliotheque regler}).</li>
 *   <li>Le tirage est <b>partagé</b> et renouvelé toutes les {@code delayHours} : chaque technique sort avec son
 *       pourcentage, on garde les plus rares si plus de rouleaux sortent que de cases. Un rouleau pris disparaît pour
 *       tout le monde jusqu'au tirage suivant. Un rang S ne sort que s'il reste un slot ; il le réserve.</li>
 *   <li>On peut tout emporter : seul le poids de la sacoche limite.</li>
 * </ul>
 *
 * <p>Canal {@code reborn:parchemin}, JSON UTF-8 brut. Le serveur envoie {@code {"t": "lib" | "lib_cfg" | "toast"}},
 * le client demande {@code {"a": "take" | "cfg_save" | "reroll"}}.
 */
public final class LibraryService implements Listener, PluginMessageListener {

    public static final String CHANNEL = "reborn:parchemin";
    private static final String STAFF = "shinobiabilities.staff";
    private static final double REACH_SQ = 8 * 8;

    private final JavaPlugin plugin;
    private final ScrollCatalog catalog;
    private final SlotRegistry slots;
    private final File file;
    private final Map<String, Library> libs = new LinkedHashMap<>();
    private final Random rnd = new Random();
    private SeanceService seances;

    public LibraryService(JavaPlugin plugin, ScrollCatalog catalog, SlotRegistry slots) {
        this.plugin = plugin;
        this.catalog = catalog;
        this.slots = slots;
        this.file = new File(plugin.getDataFolder(), "bibliotheques.yml");
    }

    public void register() {
        plugin.getServer().getMessenger().registerOutgoingPluginChannel(plugin, CHANNEL);
        plugin.getServer().getMessenger().registerIncomingPluginChannel(plugin, CHANNEL, this);
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public Map<String, Library> all() { return libs; }

    public void wire(SeanceService seances) { this.seances = seances; }

    // ─────────────────────────────────────────────────────────────── persistance

    public void load() {
        libs.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        for (String id : y.getKeys(false)) {
            ConfigurationSection s = y.getConfigurationSection(id);
            if (s == null) continue;
            Library l = new Library(id);
            l.name = s.getString("nom", id);
            l.world = s.getString("monde", "world");
            l.x = s.getInt("x");
            l.y = s.getInt("y");
            l.z = s.getInt("z");
            l.count = clamp(s.getInt("rouleaux", 6), 1, 9);
            l.delayHours = clamp(s.getInt("delai-heures", 3), 1, 168);
            l.model = clamp(s.getInt("modele", 0), 0, 3);
            List<Double> r = s.getDoubleList("par-rang");
            if (r.size() == 5) for (int i = 0; i < 5; i++) l.byRank[i] = r.get(i);
            ConfigurationSection o = s.getConfigurationSection("exceptions");
            if (o != null) for (String k : o.getKeys(false)) l.overrides.put(k, o.getDouble(k));
            l.nextRoll = s.getLong("prochain-tirage", 0L);
            List<String> cells = s.getStringList("tirage");
            List<String> ids = s.getStringList("rouleaux-ids");
            for (int i = 0; i < 9; i++) {
                l.cells[i] = i < cells.size() && !cells.get(i).isEmpty() ? cells.get(i) : null;
                l.scrollIds[i] = i < ids.size() && !ids.get(i).isEmpty() ? ids.get(i) : null;
            }
            libs.put(id, l);
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Library l : libs.values()) {
            String k = l.id + ".";
            y.set(k + "nom", l.name);
            y.set(k + "monde", l.world);
            y.set(k + "x", l.x);
            y.set(k + "y", l.y);
            y.set(k + "z", l.z);
            y.set(k + "rouleaux", l.count);
            y.set(k + "delai-heures", l.delayHours);
            y.set(k + "modele", l.model);
            List<Double> r = new ArrayList<>();
            for (double d : l.byRank) r.add(d);
            y.set(k + "par-rang", r);
            l.overrides.forEach((id, p) -> y.set(k + "exceptions." + id, p));
            y.set(k + "prochain-tirage", l.nextRoll);
            List<String> cells = new ArrayList<>(), ids = new ArrayList<>();
            for (int i = 0; i < 9; i++) {
                cells.add(l.cells[i] == null ? "" : l.cells[i]);
                ids.add(l.scrollIds[i] == null ? "" : l.scrollIds[i]);
            }
            y.set(k + "tirage", cells);
            y.set(k + "rouleaux-ids", ids);
        }
        try {
            y.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[parchemins] Sauvegarde des bibliothèques échouée : " + e.getMessage());
        }
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    // ─────────────────────────────────────────────────────────────── gestion staff

    public Library create(String name, Location at) {
        String base = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.isEmpty()) base = "bibliotheque";
        String id = base;
        for (int n = 2; libs.containsKey(id); n++) id = base + "-" + n;
        Library l = new Library(id);
        l.name = name;
        l.world = at.getWorld().getName();
        l.x = at.getBlockX();
        l.y = at.getBlockY();
        l.z = at.getBlockZ();
        libs.put(id, l);
        save();
        return l;
    }

    public void delete(Library l) {
        releaseUntaken(l);
        libs.remove(l.id);
        save();
    }

    public Library at(Location l) {
        for (Library lib : libs.values()) if (lib.at(l)) return lib;
        return null;
    }

    public Library nearest(Location l, double maxSq) {
        Library best = null;
        double d = maxSq;
        for (Library lib : libs.values()) {
            double ds = lib.distanceSq(l);
            if (ds <= d) { d = ds; best = lib; }
        }
        return best;
    }

    // ─────────────────────────────────────────────────────────────── tirage

    private void releaseUntaken(Library l) {
        for (int i = 0; i < 9; i++) {
            if (l.cells[i] == null || l.scrollIds[i] == null) continue;
            Ability a = catalog.byId(l.cells[i]);
            if (a != null && ScrollCatalog.rank(a) == 'S') slots.release(a.id(), "rouleau:" + l.scrollIds[i]);
        }
    }

    /** Nouveau tirage si le délai est passé (ou {@code force}). */
    public void ensureRoll(Library l, boolean force) {
        long now = System.currentTimeMillis();
        if (!force && now < l.nextRoll) return;
        releaseUntaken(l);
        List<Ability> hit = new ArrayList<>();
        for (Ability a : catalog.pool()) {
            char r = ScrollCatalog.rank(a);
            if (r == 'S' && !slots.free(a.id())) continue;
            Double o = l.overrides.get(a.id());
            double p = o != null ? o : l.byRank[ScrollCatalog.bucket(r)];
            if (rnd.nextDouble() * 100.0 < p) hit.add(a);
        }
        Collections.shuffle(hit, rnd);
        hit.sort(Comparator.comparingInt((Ability a) -> ScrollCatalog.bucket(ScrollCatalog.rank(a))).reversed());
        List<Ability> kept = new ArrayList<>(hit.subList(0, Math.min(l.count, hit.size())));
        List<Integer> cells = new ArrayList<>();
        for (int i = 0; i < l.count; i++) cells.add(i);
        Collections.shuffle(cells, rnd);
        for (int i = 0; i < 9; i++) { l.cells[i] = null; l.scrollIds[i] = null; }
        for (int k = 0; k < kept.size(); k++) {
            int c = cells.get(k);
            Ability a = kept.get(k);
            l.cells[c] = a.id();
            l.scrollIds[c] = UUID.randomUUID().toString();
            if (ScrollCatalog.rank(a) == 'S') slots.reserve(a.id(), "rouleau:" + l.scrollIds[c]);
        }
        l.nextRoll = now + l.delayHours * 3_600_000L;
        save();
    }

    // ─────────────────────────────────────────────────────────────── ouverture

    public void open(Player p, Library l) {
        ensureRoll(l, false);
        send(p, libJson(p, l));
    }

    private JsonObject libJson(Player p, Library l) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "lib");
        o.addProperty("id", l.id);
        o.addProperty("name", l.name);
        o.addProperty("count", l.count);
        o.addProperty("refreshIn", duration(l.nextRoll - System.currentTimeMillis()));
        JsonArray cells = new JsonArray();
        for (int i = 0; i < 9; i++) {
            Ability a = l.cells[i] == null ? null : catalog.byId(l.cells[i]);
            cells.add(a == null ? com.google.gson.JsonNull.INSTANCE : ScrollCatalog.json(a));
        }
        o.add("cells", cells);
        var inv = inventory();
        o.addProperty("weight", inv == null ? 0 : inv.weight(p));
        o.addProperty("maxWeight", inv == null ? 12 : inv.maxWeight(p));
        o.addProperty("staff", p.hasPermission(STAFF));
        return o;
    }

    public void openConfig(Player p, Library l) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "lib_cfg");
        o.addProperty("id", l.id);
        o.addProperty("name", l.name);
        o.addProperty("count", l.count);
        o.addProperty("delayH", l.delayHours);
        o.addProperty("model", l.model);
        JsonArray r = new JsonArray();
        for (double d : l.byRank) r.add(d);
        o.add("byRank", r);
        JsonObject ov = new JsonObject();
        l.overrides.forEach(ov::addProperty);
        o.add("overrides", ov);
        JsonArray techs = new JsonArray();
        for (Ability a : catalog.pool()) {
            JsonObject t = ScrollCatalog.json(a);
            if (ScrollCatalog.rank(a) == 'S') {
                t.addProperty("slots", slots.used(a.id()) + "/" + slots.max(a.id()));
            }
            techs.add(t);
        }
        o.add("techs", techs);
        send(p, o);
    }

    private static String duration(long ms) {
        if (ms <= 0) return "un instant";
        long min = (ms + 59_999) / 60_000;
        if (min < 60) return min + " min";
        long h = min / 60, m = min % 60;
        return m == 0 ? h + " h" : h + " h " + String.format("%02d", m);
    }

    // ─────────────────────────────────────────────────────────────── prise d'un rouleau

    public void take(Player p, Library l, int slot) {
        if (slot < 0 || slot >= 9 || l.distanceSq(p.getLocation()) > REACH_SQ) return;
        if (l.cells[slot] == null) { toast(p, "Ce rouleau a déjà été pris."); send(p, libJson(p, l)); return; }
        Ability a = catalog.byId(l.cells[slot]);
        if (a == null) { l.cells[slot] = null; save(); send(p, libJson(p, l)); return; }
        var inv = inventory();
        double w = ScrollCatalog.weight(ScrollCatalog.rank(a));
        if (inv != null && inv.weight(p) + w > inv.maxWeight(p) + 1e-6) {
            toast(p, "Ta sacoche est trop lourde.");
            return;
        }
        ItemStack it = ParcheminItems.create(a, l.scrollIds[slot]);
        l.cells[slot] = null;
        l.scrollIds[slot] = null;
        save();
        if (inv != null) inv.give(p, it);
        else p.getInventory().addItem(it).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
        toast(p, "Rouleau pris : " + a.name());
        char r = ScrollCatalog.rank(a);
        if (r == 'A' || r == 'S') logStaff(p, "Parchemin rang " + r + " pris", a.name() + " · " + l.name);
        send(p, libJson(p, l));
    }

    // ─────────────────────────────────────────────────────────────── réglages staff

    private void applyConfig(Player p, Library l, JsonObject in) {
        if (in.has("name")) l.name = in.get("name").getAsString().trim().isEmpty() ? l.name : in.get("name").getAsString().trim();
        if (in.has("count")) l.count = clamp(in.get("count").getAsInt(), 1, 9);
        if (in.has("delayH")) l.delayHours = clamp(in.get("delayH").getAsInt(), 1, 168);
        if (in.has("model")) l.model = clamp(in.get("model").getAsInt(), 0, 3);
        if (in.has("byRank")) {
            JsonArray r = in.getAsJsonArray("byRank");
            for (int i = 0; i < 5 && i < r.size(); i++) l.byRank[i] = Math.max(0, Math.min(100, r.get(i).getAsDouble()));
        }
        if (in.has("overrides")) {
            l.overrides.clear();
            for (Map.Entry<String, JsonElement> e : in.getAsJsonObject("overrides").entrySet()) {
                if (catalog.byId(e.getKey()) != null) {
                    l.overrides.put(e.getKey(), Math.max(0, Math.min(100, e.getValue().getAsDouble())));
                }
            }
        }
        for (int i = l.count; i < 9; i++) {                      // cases retirées : leur rouleau disparaît
            if (l.cells[i] != null) {
                Ability a = catalog.byId(l.cells[i]);
                if (a != null && ScrollCatalog.rank(a) == 'S') slots.release(a.id(), "rouleau:" + l.scrollIds[i]);
                l.cells[i] = null;
                l.scrollIds[i] = null;
            }
        }
        save();
        toast(p, "Réglages enregistrés. Ils s'appliquent au prochain tirage.");
        logStaff(p, "Bibliothèque réglée", l.name);
    }

    // ─────────────────────────────────────────────────────────────── réseau

    @Override
    public void onPluginMessageReceived(String channel, Player p, byte[] message) {
        if (!CHANNEL.equals(channel)) return;
        JsonObject in;
        try {
            in = JsonParser.parseString(new String(message, StandardCharsets.UTF_8)).getAsJsonObject();
        } catch (RuntimeException e) {
            return;
        }
        String a = in.has("a") ? in.get("a").getAsString() : "";
        if (a.equals("train")) {                                       // séance d'apprentissage (rouleau en main)
            String tech = in.has("tech") ? in.get("tech").getAsString() : "";
            if (seances != null) Bukkit.getScheduler().runTask(plugin, () -> seances.train(p, tech));
            return;
        }
        Library l = in.has("id") ? libs.get(in.get("id").getAsString()) : null;
        if (l == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            switch (a) {
                case "take" -> take(p, l, in.has("slot") ? in.get("slot").getAsInt() : -1);
                case "cfg_save" -> { if (p.hasPermission(STAFF)) { applyConfig(p, l, in); openConfig(p, l); } }
                case "reroll" -> {
                    if (p.hasPermission(STAFF)) {
                        ensureRoll(l, true);
                        toast(p, "Nouveau tirage effectué.");
                        logStaff(p, "Nouveau tirage forcé", l.name);
                    }
                }
                case "cfg_open" -> { if (p.hasPermission(STAFF)) openConfig(p, l); }
                default -> { }
            }
        });
    }

    public void send(Player p, JsonObject o) {
        try {
            p.sendPluginMessage(plugin, CHANNEL, o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (RuntimeException ignored) {
            // client sans le mod : rien à afficher
        }
    }

    public void toast(Player p, String msg) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "toast");
        o.addProperty("msg", msg);
        send(p, o);
    }

    // ─────────────────────────────────────────────────────────────── interaction en jeu

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = false)
    public void onClickBlock(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) return;
        Library l = at(e.getClickedBlock().getLocation());
        if (l == null) return;
        e.setCancelled(true);
        open(e.getPlayer(), l);
    }

    /** Bibliothèque posée sur un meuble (Nexo) : l'entité du meuble est sur le bloc enregistré. */
    @EventHandler(priority = EventPriority.HIGH)
    public void onClickEntity(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        Entity en = e.getRightClicked();
        Library l = at(en.getLocation());
        if (l == null) l = at(en.getLocation().clone().add(0, -0.5, 0));
        if (l == null) return;
        e.setCancelled(true);
        open(e.getPlayer(), l);
    }

    // ─────────────────────────────────────────────────────────────── liens avec ShinobiCore

    private com.reborn.shinobicore.inventory.InventoryManager inventory() {
        if (Bukkit.getPluginManager().getPlugin("ShinobiCore") instanceof com.reborn.shinobicore.ShinobiCore sc) {
            return sc.rpInventory();
        }
        return null;
    }

    private void logStaff(Player p, String type, String detail) {
        if (Bukkit.getPluginManager().getPlugin("ShinobiCore") instanceof com.reborn.shinobicore.ShinobiCore sc
                && sc.staffPanel() != null) {
            sc.staffPanel().log(p, "parchemins", type, detail);
        }
    }
}
