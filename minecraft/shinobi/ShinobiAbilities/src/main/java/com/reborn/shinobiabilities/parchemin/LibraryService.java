package com.reborn.shinobiabilities.parchemin;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reborn.shinobiabilities.CoreServices;
import com.reborn.shinobiabilities.techniques.ParcheminItems;
import com.reborn.shinobicore.character.Rank;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.technique.Ability;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
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
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/**
 * Bibliothèques v2 (docs/gameplay/APPRENTISSAGE.md §3).
 *
 * <ul>
 *   <li>Sept types (Village, Académie, Clan, ANBU, Palais du Kage, Hôpital, Personnelle) ; un propriétaire (village,
 *       clan ou personnage) ; un accès {@code libre | membres}. En {@code membres}, un non-membre voit les rouleaux
 *       sans pouvoir les prendre ; le staff peut ouvrir l'accès un temps ({@code /bibliotheque ouvrir}).</li>
 *   <li>Deux étagères : le <b>fonds</b> (rouleaux déposés par les joueurs, permanents, objet conservé tel quel) et les
 *       <b>arrivages</b> (tirage partagé renouvelé toutes les {@code delayHours}, modèle de pourcentages du type ;
 *       désactivés par défaut pour Clan et Personnelle).</li>
 *   <li>Chaque dépôt et chaque prise est journalisé (propriétaire et staff).</li>
 * </ul>
 *
 * <p>Canal {@code reborn:parchemin}, JSON UTF-8 brut. Le serveur envoie {@code {"t": "lib" | "lib_cfg" | "lib_log" |
 * "toast" | "steal"}}, le client demande {@code {"a": "take" | "deposit" | "log" | "read_onsite" | "cfg_save" | "reroll" |
 * "cfg_open" | "train" | "steal" | "steal_cancel"}}. Les champs ajoutés en v2 (et pour le vol et les secrets, le
 * 13/10) sont optionnels des deux côtés (anciens clients compatibles). Vol : {@link VolService} ; réserve de
 * secrets : {@link #reserveOf(Library)}, {@link ScrollCatalog#defaultReserve}.
 */
public final class LibraryService implements Listener, PluginMessageListener {

    public static final String CHANNEL = "reborn:parchemin";
    public static final String STAFF = "shinobiabilities.staff";
    public static final String PERM_ANBU = "shinobiabilities.biblio.anbu";
    public static final String PERM_PALAIS = "shinobiabilities.biblio.palais";
    public static final String PERM_MEDIC = "shinobicore.medic";
    public static final double REACH_SQ = 8 * 8;

    /** Un rouleau porté : référence de case (H barre d'action, I inventaire, B sac) et l'objet. */
    public record Carried(String ref, ItemStack item) {}

    private final JavaPlugin plugin;
    private final CoreServices core;
    private final ScrollCatalog catalog;
    private final SlotRegistry slots;
    private final File file;
    private final Map<String, Library> libs = new LinkedHashMap<>();
    private final Random rnd = new Random();
    private SeanceService seances;
    private VolService vol;

    public LibraryService(JavaPlugin plugin, CoreServices core, ScrollCatalog catalog, SlotRegistry slots) {
        this.plugin = plugin;
        this.core = core;
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

    public Library byId(String id) { return id == null ? null : libs.get(id); }

    public void wire(SeanceService seances) { this.seances = seances; }

    public void wire(VolService vol) { this.vol = vol; }

    public ScrollCatalog catalog() { return catalog; }

    // ─────────────────────────────────────────────────────────────── persistance

    public void load() {
        libs.clear();
        if (!file.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        int migrated = 0;
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
            // ─── v2 ───
            Library.Type t = Library.Type.parse(s.getString("type"));
            if (t == null) {                       // bibliothèque v1 : Publique → Village, Réservée → Académie, Privée → Palais
                t = Library.Type.fromModel(l.model);
                l.type = t;
                l.access = t.defaultAccess;
                l.draws = true;                    // elles tiraient déjà au sort : on garde leurs arrivages
                migrated++;
                plugin.getLogger().info("[parchemins] bibliothèque « " + l.name + " » migrée : type " + t.label + ".");
            } else {
                l.type = t;
                l.access = "membres".equalsIgnoreCase(s.getString("acces")) ? Library.Access.MEMBRES
                        : "libre".equalsIgnoreCase(s.getString("acces")) ? Library.Access.LIBRE : t.defaultAccess;
                l.draws = s.getBoolean("arrivages", t.drawsByDefault);
            }
            l.owner = s.getString("proprietaire", "");
            l.ownerName = s.getString("proprietaire-nom", "");
            l.openUntil = s.getLong("ouvert-jusqu-a", 0L);
            ConfigurationSection au = s.getConfigurationSection("autorises");
            if (au != null) for (String k : au.getKeys(false)) l.allowed.put(k, au.getString(k, "?"));
            ConfigurationSection f = s.getConfigurationSection("fonds");
            if (f != null) for (String k : f.getKeys(false)) {
                int i;
                try { i = Integer.parseInt(k); } catch (NumberFormatException e) { continue; }
                ConfigurationSection d = f.getConfigurationSection(k);
                if (d == null || i < 0 || i >= Library.FONDS) continue;
                l.fonds[i] = new Library.Deposit(d.getString("technique", ""), d.getString("rouleau", ""),
                        d.getString("objet", ""), d.getString("par", "?"), d.getLong("quand", 0L), d.getString("vole-a", ""));
            }
            // ─── vol, secrets (13/10) : absents = défauts ───
            l.theft = s.getBoolean("vol", true);
            l.theftSeconds = clamp(s.getInt("vol-secondes", 6), 2, 60);
            l.theftNamed = s.getBoolean("vol-nominatif", false);
            l.commons = s.getBoolean("tirage-commun", t.commonsByDefault());
            ConfigurationSection rs = s.getConfigurationSection("reserve");
            if (rs != null) {
                l.reserveCustom = true;
                for (String k : rs.getKeys(false)) l.reserve.put(k, Math.max(0, Math.min(100, rs.getDouble(k))));
            } else if (s.getBoolean("reserve-vide", false)) {
                l.reserveCustom = true;
            }
            for (Map<?, ?> m : s.getMapList("journal")) {
                try {
                    String rk = String.valueOf(m.get("rang"));
                    l.journal.add(new Library.LogEntry(Long.parseLong(String.valueOf(m.get("quand"))),
                            String.valueOf(m.get("qui")), String.valueOf(m.get("acte")), String.valueOf(m.get("etagere")),
                            String.valueOf(m.get("technique")), String.valueOf(m.get("nom")),
                            rk.isEmpty() ? 'D' : rk.charAt(0)));
                } catch (RuntimeException ignored) {
                    // ligne abîmée : ignorée
                }
            }
            libs.put(id, l);
        }
        if (migrated > 0) save();
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
            y.set(k + "type", l.type.name());
            y.set(k + "proprietaire", l.owner);
            if (!l.ownerName.isEmpty()) y.set(k + "proprietaire-nom", l.ownerName);
            y.set(k + "acces", l.access.name().toLowerCase(Locale.ROOT));
            y.set(k + "arrivages", l.draws);
            if (l.openUntil > System.currentTimeMillis()) y.set(k + "ouvert-jusqu-a", l.openUntil);
            l.allowed.forEach((id, n) -> y.set(k + "autorises." + id, n));
            for (int i = 0; i < Library.FONDS; i++) {
                Library.Deposit d = l.fonds[i];
                if (d == null) continue;
                String f = k + "fonds." + i + ".";
                y.set(f + "technique", d.techId());
                y.set(f + "rouleau", d.scrollId());
                y.set(f + "objet", d.item());
                y.set(f + "par", d.by());
                y.set(f + "quand", d.at());
                if (d.stolenFrom() != null && !d.stolenFrom().isEmpty()) y.set(f + "vole-a", d.stolenFrom());
            }
            y.set(k + "vol", l.theft);
            y.set(k + "vol-secondes", l.theftSeconds);
            y.set(k + "vol-nominatif", l.theftNamed);
            y.set(k + "tirage-commun", l.commons);
            if (l.reserveCustom) {
                if (l.reserve.isEmpty()) y.set(k + "reserve-vide", true);
                else l.reserve.forEach((id, p) -> y.set(k + "reserve." + id, p));
            }
            List<Map<String, Object>> jl = new ArrayList<>();
            for (Library.LogEntry e : l.journal) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("quand", e.at());
                m.put("qui", e.who());
                m.put("acte", e.act());
                m.put("etagere", e.shelf());
                m.put("technique", e.techId());
                m.put("nom", e.techName());
                m.put("rang", String.valueOf(e.rank()));
                jl.add(m);
            }
            y.set(k + "journal", jl);
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

    // ─────────────────────────────────────────────────────────────── création, suppression

    public Library create(String name, Location at, Library.Type type, String owner, String ownerName) {
        String base = norm(name).replaceAll("[^a-z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (base.isEmpty()) base = "bibliotheque";
        String id = base;
        for (int n = 2; libs.containsKey(id); n++) id = base + "-" + n;
        Library l = new Library(id);
        l.name = name;
        l.world = at.getWorld().getName();
        l.x = at.getBlockX();
        l.y = at.getBlockY();
        l.z = at.getBlockZ();
        l.applyTypeDefaults(type);
        l.owner = owner == null ? "" : owner;
        l.ownerName = ownerName == null ? "" : ownerName;
        libs.put(id, l);
        save();
        return l;
    }

    /**
     * Supprime une bibliothèque. Les rouleaux du fonds ne disparaissent pas (objets uniques) : ils sont rendus à
     * {@code to} (sacoche, sinon au sol), ou posés au sol devant le meuble. Renvoie le nombre de rouleaux rendus.
     */
    public int delete(Library l, Player to) {
        releaseUntaken(l);
        int n = 0;
        World w = Bukkit.getWorld(l.world);
        for (int i = 0; i < Library.FONDS; i++) {
            Library.Deposit d = l.fonds[i];
            if (d == null) continue;
            ItemStack it = itemOf(d);
            l.fonds[i] = null;
            if (it == null) continue;
            n++;
            if (to != null) give(to, it);
            else if (w != null) w.dropItemNaturally(new Location(w, l.x + 0.5, l.y + 1, l.z + 0.5), it);
        }
        libs.remove(l.id);
        save();
        return n;
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

    /** La bibliothèque personnelle d'un personnage (une par personnage), ou null. */
    public Library personalOf(UUID charId) {
        String id = charId.toString();
        for (Library l : libs.values()) if (l.type == Library.Type.PERSONNELLE && id.equals(l.owner)) return l;
        return null;
    }

    // ─────────────────────────────────────────────────────────────── appartenance

    public ShinobiCharacter character(Player p) {
        return core == null || core.characters() == null ? null : core.characters().getActive(p.getUniqueId());
    }

    /** Joueur en ligne jouant le personnage de ce nom, ou null. */
    public Player characterPlayer(String name) {
        try {
            return core == null || core.characters() == null ? null : core.characters().findOnlinePlayerByCharacter(name);
        } catch (RuntimeException e) {
            return null;
        }
    }

    static String norm(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s.trim(), Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private static boolean villageOk(ShinobiCharacter c, Library l) {
        return l.owner.isBlank() || (c != null && norm(c.village()).equals(norm(l.owner)));
    }

    /** Membre de la bibliothèque (règles du type). Le staff l'est toujours. */
    public boolean isMember(Player p, Library l) {
        return p.hasPermission(STAFF) || isMemberRp(p, l);
    }

    /** Membre selon les règles RP du type seulement (sans le passe-droit du staff) : destinataires des alertes de vol. */
    public boolean isMemberRp(Player p, Library l) {
        ShinobiCharacter c = character(p);
        return switch (l.type) {
            case VILLAGE, ACADEMIE -> c != null && villageOk(c, l);
            case CLAN -> c != null && !l.owner.isBlank() && norm(c.clan()).equals(norm(l.owner));
            case ANBU -> p.hasPermission(PERM_ANBU) && (l.owner.isBlank() || villageOk(c, l));
            case PALAIS -> (p.hasPermission(PERM_PALAIS) || (c != null && c.rank() == Rank.KAGE))
                    && (l.owner.isBlank() || villageOk(c, l));
            case HOPITAL -> p.hasPermission(PERM_MEDIC) && (l.owner.isBlank() || villageOk(c, l));
            case PERSONNELLE -> c != null && (c.id().toString().equals(l.owner) || l.allowed.containsKey(c.id().toString()));
        };
    }

    /** Peut prendre : accès libre, ouverture temporaire du staff, ou membre. */
    public boolean canTake(Player p, Library l) {
        return l.access == Library.Access.LIBRE || l.openNow() || isMember(p, l);
    }

    /** Peut déposer : tout le monde (Village), Chūnin et plus (Académie), le propriétaire (Personnelle), sinon membre. */
    public boolean canDeposit(Player p, Library l) {
        if (p.hasPermission(STAFF)) return true;
        ShinobiCharacter c = character(p);
        if (c == null) return false;
        return switch (l.type) {
            case VILLAGE -> true;
            case ACADEMIE -> isMember(p, l) && c.rank().ordinal() >= Rank.CHUNIN.ordinal();
            case PERSONNELLE -> c.id().toString().equals(l.owner);
            default -> isMember(p, l);
        };
    }

    /** Peut voler : vol permis sur cette bibliothèque, ne peut pas prendre normalement, personnage actif. */
    public boolean canSteal(Player p, Library l) {
        return l.theft && !canTake(p, l) && character(p) != null;
    }

    /** Pourquoi le joueur ne peut pas déposer ici (message clair de l'écran), ou "" s'il peut. */
    public String depositWhy(Player p, Library l) {
        if (canDeposit(p, l)) return "";
        if (character(p) == null) return "Il faut un personnage actif pour déposer.";
        String o = ownerLabel(l);
        return switch (l.type) {
            case ACADEMIE -> isMember(p, l) ? "Seuls les instructeurs (Chūnin et plus) déposent à l'Académie."
                    : "Seuls les instructeurs du village" + (o.isEmpty() ? "" : " de " + o) + " déposent ici.";
            case CLAN -> "Seuls les membres du clan" + (o.isEmpty() ? "" : " " + o) + " déposent ici.";
            case ANBU -> "Seuls les membres de l'ANBU déposent ici.";
            case PALAIS -> "Seuls le Kage et son conseil déposent ici.";
            case HOPITAL -> "Seuls les médecins de l'hôpital déposent ici.";
            case PERSONNELLE -> "Seul " + (o.isEmpty() ? "son propriétaire" : o) + " dépose ici. Ta propre bibliothèque : /bibliotheque perso.";
            default -> "Tu ne peux pas déposer ici.";
        };
    }

    public boolean isOwner(Player p, Library l) {
        if (l.type != Library.Type.PERSONNELLE) return false;
        ShinobiCharacter c = character(p);
        return c != null && c.id().toString().equals(l.owner);
    }

    public boolean canSeeJournal(Player p, Library l) {
        return p.hasPermission(STAFF) || isOwner(p, l);
    }

    /** Propriétaire lisible (nom du personnage, du clan ou du village). */
    public static String ownerLabel(Library l) {
        if (!l.ownerName.isBlank()) return l.ownerName;
        if (l.owner.isBlank()) return "";
        return l.type == Library.Type.PERSONNELLE ? "?" : l.owner;
    }

    /** Le fonds contient-il un rouleau de cette technique ? Renvoie sa case, ou -1. */
    public int fondsCellOf(Library l, String techId) {
        for (int i = 0; i < Library.FONDS; i++) if (l.fonds[i] != null && l.fonds[i].techId().equals(techId)) return i;
        return -1;
    }

    // ─────────────────────────────────────────────────────────────── tirage (arrivages)

    private void releaseUntaken(Library l) {
        for (int i = 0; i < 9; i++) {
            if (l.cells[i] == null || l.scrollIds[i] == null) continue;
            Ability a = catalog.byId(l.cells[i]);
            if (a != null && ScrollCatalog.rank(a) == 'S') slots.release(a.id(), "rouleau:" + l.scrollIds[i]);
        }
    }

    /**
     * Tirage commun : le catalogue <b>hors secrets</b> (les secrets ne sortent que des réserves), sauf l'Hôpital
     * (catégorie médicale, s'il y en a). Vide si le tirage commun est coupé.
     */
    private List<Ability> poolFor(Library l) {
        if (!l.commons) return List.of();
        List<Ability> pool = catalog.commonPool();
        if (l.type != Library.Type.HOPITAL) return pool;
        List<Ability> med = new ArrayList<>();
        for (Ability a : pool) if (ScrollCatalog.nature(a).equals("Iryō")) med.add(a);
        return med.isEmpty() ? pool : med;
    }

    /** Réserve effective : celle réglée par le staff, sinon la réserve par défaut du type. */
    public Map<String, Double> reserveOf(Library l) {
        return l.reserveCustom ? l.reserve : catalog.defaultReserve(l.type, l.owner);
    }

    /** Candidats d'un tirage → pourcentage : tirage commun (rang ou exception), puis la réserve (son % l'emporte). */
    private Map<Ability, Double> candidates(Library l) {
        Map<Ability, Double> out = new LinkedHashMap<>();
        for (Ability a : poolFor(l)) {
            Double o = l.overrides.get(a.id());
            out.put(a, o != null ? o : l.byRank[ScrollCatalog.bucket(ScrollCatalog.rank(a))]);
        }
        for (Map.Entry<String, Double> e : reserveOf(l).entrySet()) {
            Ability a = catalog.byId(e.getKey());
            if (a != null) out.put(a, e.getValue());
        }
        return out;
    }

    /** Nouveau tirage si le délai est passé (ou {@code force}). Arrivages désactivés : étagère vide. */
    public void ensureRoll(Library l, boolean force) {
        long now = System.currentTimeMillis();
        if (!l.draws) {
            boolean any = false;
            for (int i = 0; i < 9; i++) if (l.cells[i] != null) any = true;
            if (any) {
                releaseUntaken(l);
                for (int i = 0; i < 9; i++) { l.cells[i] = null; l.scrollIds[i] = null; }
                save();
            }
            return;
        }
        if (!force && now < l.nextRoll) return;
        releaseUntaken(l);
        List<Ability> hit = new ArrayList<>();
        for (Map.Entry<Ability, Double> e : candidates(l).entrySet()) {
            Ability a = e.getKey();
            if (ScrollCatalog.rank(a) == 'S' && !slots.free(a.id())) continue;
            if (rnd.nextDouble() * 100.0 < e.getValue()) hit.add(a);
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
        o.addProperty("count", l.draws ? l.count : 0);
        o.addProperty("refreshIn", duration(l.nextRoll - System.currentTimeMillis()));
        JsonArray cells = new JsonArray();
        for (int i = 0; i < 9; i++) {
            Ability a = l.cells[i] == null ? null : catalog.byId(l.cells[i]);
            cells.add(a == null ? JsonNull.INSTANCE : ScrollCatalog.json(a));
        }
        o.add("cells", cells);
        var inv = inventory();
        o.addProperty("weight", inv == null ? 0 : inv.weight(p));
        o.addProperty("maxWeight", inv == null ? 12 : inv.maxWeight(p));
        boolean staff = p.hasPermission(STAFF);
        o.addProperty("staff", staff);
        // ─── v2 (champs optionnels) ───
        boolean member = isMember(p, l);
        o.addProperty("type", l.type.name());
        o.addProperty("typeLabel", l.type.label);
        o.addProperty("owner", ownerLabel(l));
        o.addProperty("access", l.access.name().toLowerCase(Locale.ROOT));
        o.addProperty("member", member);
        o.addProperty("canTake", canTake(p, l));
        o.addProperty("canDeposit", canDeposit(p, l));
        o.addProperty("draws", l.draws);
        if (l.openNow()) o.addProperty("openFor", duration(l.openUntil - System.currentTimeMillis()));
        o.addProperty("onsite", l.type.onsiteReading() && member);
        o.addProperty("journal", canSeeJournal(p, l));
        JsonArray fonds = new JsonArray();
        for (int i = 0; i < Library.FONDS; i++) {
            Library.Deposit d = l.fonds[i];
            Ability a = d == null ? null : catalog.byId(d.techId());
            if (a == null) { fonds.add(JsonNull.INSTANCE); continue; }
            JsonObject t = ScrollCatalog.json(a);
            t.addProperty("by", d.by());
            if (d.stolenFrom() != null && !d.stolenFrom().isEmpty()) t.addProperty("stolen", d.stolenFrom());
            fonds.add(t);
        }
        o.add("fonds", fonds);
        // ─── vol, dépôt (13/10, optionnels) ───
        boolean steal = canSteal(p, l);
        o.addProperty("canSteal", steal);
        if (steal && vol != null) {
            JsonArray secs = new JsonArray();
            for (char r : "DCBAS".toCharArray()) secs.add(vol.durationMs(l, r) / 1000.0);
            o.add("stealSecs", secs);
        }
        String why = depositWhy(p, l);
        if (!why.isEmpty()) o.addProperty("depositWhy", why);
        JsonArray bag = new JsonArray();
        if (canDeposit(p, l)) for (Carried c : carried(p)) {
            Ability a = catalog.byId(ParcheminItems.abilityIdOf(c.item()));
            if (a == null) continue;
            JsonObject t = ScrollCatalog.json(a);
            t.addProperty("ref", c.ref());
            bag.add(t);
        }
        o.add("bag", bag);
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
        o.addProperty("type", l.type.name());
        o.addProperty("owner", ownerLabel(l));
        o.addProperty("access", l.access.name().toLowerCase(Locale.ROOT));
        o.addProperty("draws", l.draws);
        o.addProperty("fondsCount", l.fondsCount());
        JsonArray r = new JsonArray();
        for (double d : l.byRank) r.add(d);
        o.add("byRank", r);
        JsonObject ov = new JsonObject();
        l.overrides.forEach(ov::addProperty);
        o.add("overrides", ov);
        // « techs » = tirage commun (hors secrets) : l'onglet Techniques d'un ancien mod reste juste
        JsonArray techs = new JsonArray();
        for (Ability a : catalog.commonPool()) {
            JsonObject t = ScrollCatalog.json(a);
            if (ScrollCatalog.rank(a) == 'S') {
                t.addProperty("slots", slots.used(a.id()) + "/" + slots.max(a.id()));
            }
            techs.add(t);
        }
        o.add("techs", techs);
        // ─── vol, secrets (13/10, optionnels) ───
        o.addProperty("theft", l.theft);
        o.addProperty("theftSecs", l.theftSeconds);
        o.addProperty("theftNamed", l.theftNamed);
        if (vol != null) {
            JsonArray f = new JsonArray();
            for (double d : vol.factors()) f.add(d);
            o.add("theftFactors", f);
        }
        o.addProperty("commons", l.commons);
        o.addProperty("reserveCustom", l.reserveCustom);
        JsonObject res = new JsonObject();
        reserveOf(l).forEach(res::addProperty);
        o.add("reserve", res);
        JsonObject def = new JsonObject();
        catalog.defaultReserve(l.type, l.owner).forEach(def::addProperty);
        o.add("reserveDefault", def);
        JsonArray sp = new JsonArray();
        for (double d : catalog.secretPcts()) sp.add(d);
        o.add("secretPct", sp);
        JsonArray st = new JsonArray();
        for (Ability a : catalog.reservablePool()) {
            JsonObject t = catalog.lightJson(a);
            if (ScrollCatalog.rank(a) == 'S') t.addProperty("slots", slots.used(a.id()) + "/" + slots.max(a.id()));
            st.add(t);
        }
        o.add("secretTechs", st);
        send(p, o);
    }

    /** Renvoie l'état de la bibliothèque au joueur (après un vol, une prise…). */
    public void refresh(Player p, Library l) { send(p, libJson(p, l)); }

    /** Journal (le plus récent d'abord) : propriétaire et staff. */
    public void openJournal(Player p, Library l) {
        if (!canSeeJournal(p, l)) { toast(p, "Seuls le propriétaire et le staff consultent le journal."); return; }
        JsonObject o = new JsonObject();
        o.addProperty("t", "lib_log");
        o.addProperty("id", l.id);
        o.addProperty("name", l.name);
        JsonArray arr = new JsonArray();
        SimpleDateFormat f = new SimpleDateFormat("dd/MM HH:mm", Locale.FRANCE);
        for (int i = l.journal.size() - 1, n = 0; i >= 0 && n < 60; i--, n++) {
            Library.LogEntry e = l.journal.get(i);
            JsonObject j = new JsonObject();
            j.addProperty("when", f.format(new Date(e.at())));
            j.addProperty("who", e.who());
            j.addProperty("act", e.act());
            j.addProperty("shelf", e.shelf());
            j.addProperty("tech", e.techName());
            j.addProperty("rank", String.valueOf(e.rank()));
            arr.add(j);
        }
        o.add("entries", arr);
        send(p, o);
    }

    public static String duration(long ms) {
        if (ms <= 0) return "un instant";
        long min = (ms + 59_999) / 60_000;
        if (min < 60) return min + " min";
        long h = min / 60, m = min % 60;
        return m == 0 ? h + " h" : h + " h " + String.format("%02d", m);
    }

    // ─────────────────────────────────────────────────────────────── prise d'un rouleau

    /** Prise sur une étagère : {@code fonds} ou {@code arrivages} (défaut, compatible avec les anciens clients). */
    public void take(Player p, Library l, String shelf, int slot) {
        if (slot < 0 || slot >= 9 || l.distanceSq(p.getLocation()) > REACH_SQ) return;
        boolean fondsShelf = "fonds".equals(shelf);
        if (!canTake(p, l)) {
            toast(p, canSteal(p, l) ? "Réservé aux membres : il faudra le voler." : "Réservé aux membres : tu peux regarder, pas emporter.");
            send(p, libJson(p, l));
            return;
        }
        Ability a;
        if (fondsShelf) {
            Library.Deposit d = l.fonds[slot];
            if (d == null) { toast(p, "Ce rouleau a déjà été pris."); send(p, libJson(p, l)); return; }
            a = catalog.byId(d.techId());
        } else {
            if (l.cells[slot] == null) { toast(p, "Ce rouleau a déjà été pris."); send(p, libJson(p, l)); return; }
            a = catalog.byId(l.cells[slot]);
            if (a == null) { l.cells[slot] = null; save(); send(p, libJson(p, l)); return; }
        }
        var inv = inventory();
        double w = a == null ? 0.2 : ScrollCatalog.weight(ScrollCatalog.rank(a));
        if (inv != null && inv.weight(p) + w > inv.maxWeight(p) + 1e-6) {
            toast(p, "Ta sacoche est trop lourde.");
            return;
        }
        ItemStack it;
        if (fondsShelf) {
            Library.Deposit d = l.fonds[slot];
            it = itemOf(d);
            l.fonds[slot] = null;
            if (it == null) { save(); send(p, libJson(p, l)); return; }
        } else {
            it = ParcheminItems.create(a, l.scrollIds[slot]);
            l.cells[slot] = null;
            l.scrollIds[slot] = null;
        }
        String techName = a == null ? "?" : a.name();
        char r = a == null ? 'D' : ScrollCatalog.rank(a);
        l.log(who(p), "prise", fondsShelf ? "fonds" : "arrivages", a == null ? "" : a.id(), techName, r);
        save();
        give(p, it);
        toast(p, "Rouleau pris : " + techName);
        if (r == 'A' || r == 'S' || (fondsShelf && l.access == Library.Access.MEMBRES && !isMember(p, l))) {
            logStaff(p, "Parchemin rang " + r + " pris (" + (fondsShelf ? "fonds" : "arrivages") + ")", techName + " · " + l.name);
        }
        send(p, libJson(p, l));
    }

    // ─────────────────────────────────────────────────────────────── dépôt dans le fonds

    /** Dépose le rouleau de la case {@code ref} (H barre, I inventaire, B sac) dans la case {@code cell} du fonds. */
    public void deposit(Player p, Library l, String ref, int cell) {
        if (l.distanceSq(p.getLocation()) > REACH_SQ) return;
        if (!canDeposit(p, l)) { toast(p, "Tu ne peux pas déposer dans cette bibliothèque."); return; }
        if (cell < 0 || cell >= Library.FONDS || l.fonds[cell] != null) cell = l.freeFondsCell();
        if (cell < 0) { toast(p, "Le fonds est plein."); send(p, libJson(p, l)); return; }
        ItemStack it = removeCarried(p, ref);
        if (it == null) { toast(p, "Ce rouleau n'est plus dans ta sacoche."); send(p, libJson(p, l)); return; }
        String techId = ParcheminItems.abilityIdOf(it);
        Ability a = catalog.byId(techId);
        String sid = ParcheminItems.scrollIdOf(it);
        String b64 = Base64.getEncoder().encodeToString(it.serializeAsBytes());
        String stolen = ParcheminItems.stolenFrom(it);
        l.fonds[cell] = new Library.Deposit(techId, sid == null ? "" : sid, b64, who(p), System.currentTimeMillis(),
                stolen == null ? "" : stolen);
        char r = a == null ? 'D' : ScrollCatalog.rank(a);
        String techName = a == null ? techId : a.name();
        l.log(who(p), "depot", "fonds", techId, techName, r);
        save();
        toast(p, "Rouleau déposé : " + techName);
        if (r == 'A' || r == 'S') logStaff(p, "Parchemin rang " + r + " déposé", techName + " · " + l.name);
        send(p, libJson(p, l));
    }

    String who(Player p) {
        ShinobiCharacter c = character(p);
        return c == null ? p.getName() : c.name();
    }

    /** Objet du dépôt : l'objet d'origine sérialisé, sinon un rouleau recréé (technique + parchemin_id). */
    ItemStack itemOf(Library.Deposit d) {
        if (d == null) return null;
        if (d.item() != null && !d.item().isEmpty()) {
            try {
                return ItemStack.deserializeBytes(Base64.getDecoder().decode(d.item()));
            } catch (RuntimeException e) {
                plugin.getLogger().warning("[parchemins] Rouleau du fonds illisible, recréé : " + e.getMessage());
            }
        }
        Ability a = catalog.byId(d.techId());
        if (a == null) return null;
        return d.scrollId() == null || d.scrollId().isEmpty() ? ParcheminItems.create(a) : ParcheminItems.create(a, d.scrollId());
    }

    /** Rouleaux portés : barre d'action (H0..H8), inventaire (I9..I35), stockage du sac (B0..Bn). */
    public List<Carried> carried(Player p) {
        List<Carried> out = new ArrayList<>();
        PlayerInventory pi = p.getInventory();
        for (int i = 0; i < 36; i++) {
            ItemStack it = pi.getItem(i);
            if (it != null && ParcheminItems.isParchemin(it)) out.add(new Carried((i < 9 ? "H" : "I") + i, it));
        }
        var inv = inventory();
        if (inv != null) {
            try {
                List<ItemStack> bag = inv.bagStorage(p);
                for (int i = 0; i < bag.size(); i++) {
                    ItemStack it = bag.get(i);
                    if (it != null && ParcheminItems.isParchemin(it)) out.add(new Carried("B" + i, it));
                }
            } catch (LinkageError ignored) {
                // ShinobiCore sans l'API du sac : seuls la barre d'action et l'inventaire comptent
            }
        }
        return out;
    }

    private ItemStack removeCarried(Player p, String ref) {
        if (ref == null || ref.length() < 2) return null;
        int i;
        try { i = Integer.parseInt(ref.substring(1)); } catch (NumberFormatException e) { return null; }
        char k = ref.charAt(0);
        if (k == 'H' || k == 'I') {
            if (i < 0 || i >= 36) return null;
            ItemStack it = p.getInventory().getItem(i);
            if (it == null || !ParcheminItems.isParchemin(it)) return null;
            p.getInventory().setItem(i, null);
            return it;
        }
        if (k == 'B') {
            var inv = inventory();
            if (inv == null) return null;
            try {
                List<ItemStack> bag = inv.bagStorage(p);
                if (i < 0 || i >= bag.size() || bag.get(i) == null || !ParcheminItems.isParchemin(bag.get(i))) return null;
                return inv.takeFromBag(p, i);
            } catch (LinkageError e) {
                return null;
            }
        }
        return null;
    }

    void give(Player p, ItemStack it) {
        var inv = inventory();
        if (inv != null) inv.give(p, it);
        else p.getInventory().addItem(it).values().forEach(rest -> p.getWorld().dropItemNaturally(p.getLocation(), rest));
    }

    // ─────────────────────────────────────────────────────────────── réglages staff

    private void applyConfig(Player p, Library l, JsonObject in) {
        if (in.has("name")) l.name = in.get("name").getAsString().trim().isEmpty() ? l.name : in.get("name").getAsString().trim();
        if (in.has("type")) {
            Library.Type t = Library.Type.parse(in.get("type").getAsString());
            if (t != null && t != l.type) {
                l.type = t;
                l.model = t.model;
                if (!in.has("access")) l.access = t.defaultAccess;
                if (!in.has("draws")) l.draws = t.drawsByDefault;
                if (!in.has("commons")) l.commons = t.commonsByDefault();
            }
        }
        if (in.has("access")) l.access = "membres".equalsIgnoreCase(in.get("access").getAsString()) ? Library.Access.MEMBRES : Library.Access.LIBRE;
        if (in.has("draws")) l.draws = in.get("draws").getAsBoolean();
        // ─── vol, secrets (optionnels : un ancien mod ne les envoie pas, ils restent tels quels) ───
        if (in.has("theft")) l.theft = in.get("theft").getAsBoolean();
        if (in.has("theftSecs")) l.theftSeconds = clamp(in.get("theftSecs").getAsInt(), 2, 60);
        if (in.has("theftNamed")) l.theftNamed = in.get("theftNamed").getAsBoolean();
        if (in.has("commons")) l.commons = in.get("commons").getAsBoolean();
        if (in.has("reserveReset") && in.get("reserveReset").getAsBoolean()) {
            l.reserveCustom = false;
            l.reserve.clear();
        } else if (in.has("reserve") && in.get("reserve").isJsonObject()) {
            l.reserveCustom = true;
            l.reserve.clear();
            for (Map.Entry<String, JsonElement> e : in.getAsJsonObject("reserve").entrySet()) {
                if (catalog.reservable(catalog.byId(e.getKey()))) {
                    l.reserve.put(e.getKey(), Math.max(0, Math.min(100, e.getValue().getAsDouble())));
                }
            }
        }
        if (in.has("count")) l.count = clamp(in.get("count").getAsInt(), 1, 9);
        if (in.has("delayH")) l.delayHours = clamp(in.get("delayH").getAsInt(), 1, 168);
        if (in.has("model") && !in.has("type")) l.model = clamp(in.get("model").getAsInt(), 0, 3);
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
        if (!l.draws) ensureRoll(l, false);                       // arrivages coupés : étagère vidée
        save();
        toast(p, "Réglages enregistrés. Les pourcentages s'appliquent au prochain tirage.");
        logStaff(p, "Bibliothèque réglée", l.name + " · " + l.type.label + " · " + l.access.name().toLowerCase(Locale.ROOT));
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
        if (a.equals("train")) {                                       // séance d'apprentissage
            String tech = in.has("tech") ? in.get("tech").getAsString() : "";
            String lib = in.has("lib") && !in.get("lib").isJsonNull() ? in.get("lib").getAsString() : null;
            if (seances != null) Bukkit.getScheduler().runTask(plugin, () -> seances.train(p, tech, lib));
            return;
        }
        Library l = in.has("id") ? libs.get(in.get("id").getAsString()) : null;
        if (l == null) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            switch (a) {
                case "take" -> take(p, l, in.has("shelf") ? in.get("shelf").getAsString() : "arrivages",
                        in.has("slot") ? in.get("slot").getAsInt() : -1);
                case "deposit" -> deposit(p, l, in.has("ref") ? in.get("ref").getAsString() : "",
                        in.has("cell") ? in.get("cell").getAsInt() : -1);
                case "steal" -> { if (vol != null) vol.start(p, l, in.has("shelf") ? in.get("shelf").getAsString() : "arrivages",
                        in.has("slot") ? in.get("slot").getAsInt() : -1); }
                case "steal_cancel" -> { if (vol != null) vol.abort(p, "Tu renonces à la fouille."); }
                case "log" -> openJournal(p, l);
                case "read_onsite" -> {
                    int cell = in.has("cell") ? in.get("cell").getAsInt() : -1;
                    if (seances != null && cell >= 0 && cell < Library.FONDS && l.fonds[cell] != null) {
                        seances.readOnsite(p, l, l.fonds[cell].techId());
                    }
                }
                case "cfg_save" -> { if (p.hasPermission(STAFF)) { applyConfig(p, l, in); openConfig(p, l); } }
                case "reroll" -> {
                    if (p.hasPermission(STAFF)) {
                        if (!l.draws) { toast(p, "Les arrivages de cette bibliothèque sont désactivés."); return; }
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

    com.reborn.shinobicore.inventory.InventoryManager inventory() {
        if (Bukkit.getPluginManager().getPlugin("ShinobiCore") instanceof com.reborn.shinobicore.ShinobiCore sc) {
            return sc.rpInventory();
        }
        return null;
    }

    public void logStaff(Player p, String type, String detail) {
        if (Bukkit.getPluginManager().getPlugin("ShinobiCore") instanceof com.reborn.shinobicore.ShinobiCore sc
                && sc.staffPanel() != null) {
            sc.staffPanel().log(p, "parchemins", type, detail);
        }
    }
}
