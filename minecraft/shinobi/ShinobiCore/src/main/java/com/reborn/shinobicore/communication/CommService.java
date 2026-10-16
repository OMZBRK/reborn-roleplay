package com.reborn.shinobicore.communication;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.api.event.CharacterSwitchEvent;
import com.reborn.shinobicore.character.Rank;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.communication.CommData.Cloche;
import com.reborn.shinobicore.communication.CommData.Dest;
import com.reborn.shinobicore.communication.CommData.Etat;
import com.reborn.shinobicore.communication.CommData.Missive;
import com.reborn.shinobicore.communication.CommData.Point;
import com.reborn.shinobicore.communication.CommData.TypeVoliere;
import com.reborn.shinobicore.communication.CommData.Voliere;
import com.reborn.shinobicore.faction.Droits;
import com.reborn.shinobicore.faction.FactionData.Annonce;
import com.reborn.shinobicore.faction.FactionData.Charge;
import com.reborn.shinobicore.faction.FactionData.Faction;
import com.reborn.shinobicore.faction.FactionData.GradeSection;
import com.reborn.shinobicore.faction.FactionData.Membre;
import com.reborn.shinobicore.faction.FactionData.Section;
import com.reborn.shinobicore.faction.FactionService;
import com.reborn.shinobicore.faction.Regles;
import com.reborn.shinobicore.inventory.InventoryManager;
import com.reborn.shinobicore.journal.Journal;
import com.reborn.shinobicore.monnaie.Bourse;
import com.reborn.shinobicore.staff.outils.ModeStaff;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * La cloche du village et les volières côté serveur (docs/gameplay/CLOCHE_ET_MISSIVES.md, lots C1, V1 à V4). Le staff
 * pose les points (cloche, perchoir) sur un bloc ou un meuble Nexo ; les joueurs y accèdent par le menu R ou un clic
 * droit. <b>Tout est revérifié ici</b> (droits par seuils et sections de la Faction, distance, coût, anti-spam) ; le
 * mod ({@link CommCanal}) ne fait qu'afficher et demander. Chaque geste va au journal universel : {@code FACTION}
 * (action {@code CLOCHE}) et {@code MISSIVE}, en ajout seul.
 */
public final class CommService implements Listener {

    static final double PORTEE = 6.5, PROCHES = 48;
    private static final String ALPHA = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    private final ShinobiCore plugin;
    final CommStore store;
    private CommCanal canal;
    CommRegles.Reglages k = CommRegles.Reglages.defaut();
    /** Le staff en mode animation sonne sans délai (events) ; {@code communication.cloche.sans-delai-animation}. */
    boolean sansDelaiAnimation = true;
    private final Map<UUID, Long> dernierePersonne = new ConcurrentHashMap<>();
    private final Map<String, List<Long>> dangers = new ConcurrentHashMap<>();
    private final Map<UUID, String> pointsEnvoyes = new ConcurrentHashMap<>();
    private final SecureRandom rnd = new SecureRandom();

    public CommService(ShinobiCore plugin) {
        this.plugin = plugin;
        this.store = new CommStore(plugin);
    }

    public void start() {
        recharger();
        store.start();
        canal = new CommCanal(plugin, this);
        canal.start();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        CommCommand cmd = new CommCommand(plugin, this);
        for (String c : new String[]{"cloche", "voliere", "missive"}) {
            var pc = plugin.getCommand(c);
            if (pc != null) { pc.setExecutor(cmd); pc.setTabCompleter(cmd); }
            else plugin.getLogger().warning("[cloche] Commande '" + c + "' absente du plugin.yml.");
        }
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::pousserPoints, 40L, 40L);
        plugin.getLogger().info("[cloche] Cloche et volières prêtes (canal " + CommCanal.CHANNEL + ").");
    }

    public void stop() { store.sauver(); }

    public void recharger() {
        k = CommRegles.Reglages.lire(plugin.getConfig().getConfigurationSection("communication"));
        String des = plugin.getConfig().getString("communication.cloche.sans-delai-des", "COMMANDANT_JONIN");
        Rank r = Droits.rang(des);
        if (r == null) plugin.getLogger().warning("[cloche] sans-delai-des inconnu (« " + des + " ») : Commandant Jōnin.");
        Droits.sansDelaiDefaut(r == null ? Rank.COMMANDANT_JONIN : r);
        sansDelaiAnimation = plugin.getConfig().getBoolean("communication.cloche.sans-delai-animation", true);
    }

    public CommCanal canal() { return canal; }

    private FactionService fs() { return plugin.factions(); }

    /* ================================================================ outils */

    String nouvelId(String prefixe) {
        for (int n = 0; n < 50; n++) {
            StringBuilder b = new StringBuilder(prefixe);
            for (int i = 0; i < 5; i++) b.append(ALPHA.charAt(rnd.nextInt(ALPHA.length())));
            String id = b.toString();
            if (!store.missives.containsKey(id) && !store.cloches.containsKey(id) && !store.volieres.containsKey(id)) return id;
        }
        return prefixe + System.currentTimeMillis();
    }

    static Location centre(Point p) {
        World w = Bukkit.getWorld(p.monde);
        return w == null ? null : new Location(w, p.x + 0.5, p.y + 0.5, p.z + 0.5);
    }

    static double distance(Point a, Point b) {
        if (!a.monde.equals(b.monde)) return -1;
        double dx = a.x - b.x, dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private boolean pres(Player p, Point pt) {
        Location c = centre(pt);
        return c != null && c.getWorld().equals(p.getWorld()) && c.distance(p.getLocation()) <= PORTEE;
    }

    private ShinobiCharacter perso(Player p) { return plugin.characters().getActive(p.getUniqueId()); }

    private Membre membre(Player p) { return fs() == null ? null : fs().membre(p); }

    Faction faction(String tag) { return fs() == null ? null : fs().faction(tag); }

    /** « Konoha » pour KON (nom sans « gakure »). */
    String village(String tag) {
        Faction f = faction(tag);
        String n = f == null ? tag : f.nom;
        return n.endsWith("gakure") ? n.substring(0, n.length() - 6) : n;
    }

    /** Faction-village par tag, nom ou valeur du champ village. */
    public Faction resoudreVillage(String s) {
        if (fs() == null || s == null) return null;
        Faction f = fs().faction(s);
        return f != null ? f : fs().factionDuVillage(s);
    }

    /** « de la Police », « du Corps médical », « de l'Académie ». */
    static String de(String nom) {
        if (nom == null || nom.isEmpty()) return "";
        String n = nom.toLowerCase(Locale.ROOT);
        if (n.equals("anbu") || "aeiouyéèêàâîïôûh".indexOf(n.charAt(0)) >= 0) return "de l'" + nom;
        if (n.startsWith("corps") || n.startsWith("bureau") || n.startsWith("conseil")) return "du " + nom;
        return "de la " + nom;
    }

    static String minuscule(String s) {
        return s == null || s.isEmpty() ? "" : Character.toLowerCase(s.charAt(0)) + s.substring(1);
    }

    static String heure(long ms) {
        return DateTimeFormatter.ofPattern("H 'h' mm").format(Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()));
    }

    /* ================================================================ réception */

    void recevoir(Player p, JsonObject in) {
        String a = CommCanal.s(in, "a");
        switch (a) {
            case "ouvrir" -> {
                String id = CommCanal.s(in, "id");
                Cloche c = store.cloches.get(id);
                Voliere v = store.volieres.get(id);
                if (c != null) ouvrirCloche(p, c);
                else if (v != null) ouvrirVoliere(p, v, CommCanal.b(in, "missives") ? 1 : 0, true);
            }
            case "compter" -> compter(p, in);
            case "sonner" -> sonner(p, in);
            case "envoyer" -> envoyer(p, in, false);
            case "transferer" -> envoyer(p, in, true);
            case "prendre" -> prendre(p, CommCanal.s(in, "id"), CommCanal.b(in, "lire"));
            case "bruler" -> bruler(p, CommCanal.s(in, "id"));
            default -> { }
        }
    }

    /* ================================================================ clic droit */

    public Cloche clocheA(String monde, int x, int y, int z) {
        for (Cloche c : store.cloches.values()) if (c.est(monde, x, y, z)) return c;
        return null;
    }

    public Voliere voliereA(String monde, int x, int y, int z) {
        for (Voliere v : store.volieres.values()) if (v.est(monde, x, y, z)) return v;
        return null;
    }

    private Point pointDe(Entity e) {
        for (Cloche c : store.cloches.values()) if (e.getUniqueId().equals(c.entite)) return c;
        for (Voliere v : store.volieres.values()) if (e.getUniqueId().equals(v.entite)) return v;
        Location l = e.getLocation();
        Cloche c = clocheA(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
        return c != null ? c : voliereA(l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    private void ouvrirPoint(Player p, Point pt) {
        if (pt instanceof Cloche c) {
            if (droitCloche(p, c) == null) { canal.toast(p, "Seuls les gradés du village (et les chefs de section) sonnent la cloche.", true); return; }
            ouvrirCloche(p, c);
        } else if (pt instanceof Voliere v) ouvrirVoliere(p, v, 0, true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBloc(PlayerInteractEvent ev) {
        if (ev.getHand() != EquipmentSlot.HAND) return;
        Player p = ev.getPlayer();
        if (ev.getAction() == Action.RIGHT_CLICK_BLOCK && ev.getClickedBlock() != null) {
            Block b = ev.getClickedBlock();
            Point pt = clocheA(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
            if (pt == null) pt = voliereA(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
            if (pt != null) {
                ev.setCancelled(true);
                ouvrirPoint(p, pt);
                return;
            }
        }
        if (ev.getAction() != Action.RIGHT_CLICK_AIR && ev.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack main = p.getInventory().getItemInMainHand();
        String id = MissiveObjet.id(main);
        if (id == null) return;
        ev.setCancelled(true);
        lireObjet(p, id);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntite(PlayerInteractEntityEvent ev) {
        if (ev.getHand() != EquipmentSlot.HAND) return;
        Point pt = pointDe(ev.getRightClicked());
        if (pt == null) return;
        ev.setCancelled(true);
        ouvrirPoint(ev.getPlayer(), pt);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent ev) {
        Player p = ev.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) prevenirEnAttente(p); }, 80L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onSwitch(CharacterSwitchEvent ev) {
        Player p = ev.player();
        pointsEnvoyes.remove(p.getUniqueId());
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) prevenirEnAttente(p); }, 40L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent ev) { pointsEnvoyes.remove(ev.getPlayer().getUniqueId()); }

    /* ================================================================ points proches (menu R) */

    /** Toutes les 2 s : les cloches que ce joueur peut sonner et les volières proches, pour le menu R du mod. */
    private void pousserPoints() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!canal.modde(p)) continue;
            JsonArray l = new JsonArray();
            Location me = p.getLocation();
            for (Cloche c : store.cloches.values()) {
                Location lc = centre(c);
                if (lc == null || !lc.getWorld().equals(me.getWorld()) || lc.distance(me) > PROCHES) continue;
                if (droitCloche(p, c) == null) continue;
                l.add(point("cloche", c, "Sonner la cloche"));
            }
            for (Voliere v : store.volieres.values()) {
                Location lv = centre(v);
                if (lv == null || !lv.getWorld().equals(me.getWorld()) || lv.distance(me) > PROCHES) continue;
                l.add(point("voliere", v, v.nom));
            }
            String sig = l.toString();
            if (sig.equals(pointsEnvoyes.get(p.getUniqueId()))) continue;
            pointsEnvoyes.put(p.getUniqueId(), sig);
            JsonObject o = new JsonObject();
            o.addProperty("t", "points");
            o.add("l", l);
            canal.envoyer(p, o);
        }
    }

    private static JsonObject point(String kind, Point pt, String nom) {
        JsonObject o = new JsonObject();
        o.addProperty("k", kind);
        o.addProperty("id", pt.id);
        o.addProperty("x", pt.x);
        o.addProperty("y", pt.y);
        o.addProperty("z", pt.z);
        o.addProperty("nom", nom);
        return o;
    }

    /* ================================================================ cloche : droits */

    /** Ce que le sonneur peut viser : tout le village (et des sections), ou sa section seulement. */
    record DroitCloche(boolean village, Membre m, Faction f, Section section, String titre) {}

    DroitCloche droitCloche(Player p, Cloche c) {
        if (fs() == null || ModeStaff.horsJeu(p)) return null;
        Membre m = membre(p);
        Faction f = faction(c.faction);
        if (m == null || f == null || !f.tag.equals(m.faction)) return null;
        Section s = fs().section(m.section);
        if (Regles.seuil(fs().lecteur(m), f, "cloche_village") || fs().charge(f, m.perso, Droits.C_ANNONCES))
            return new DroitCloche(true, m, f, s, titre(m, f, s, false));
        if (s != null && !s.cachee && fs().droit(m, Droits.CLOCHE_SECTION))
            return new DroitCloche(false, m, f, s, titre(m, f, s, true));
        return null;
    }

    /**
     * Pourquoi ce sonneur n'a aucun délai : {@code "grade"} (seuil « Sonner sans délai » ou chef de faction, le Kage),
     * {@code "animation"} (staff en mode animation), sinon {@code null} (anti-spam normal).
     */
    String sansDelai(Player p, DroitCloche d) {
        if (d == null) return null;
        if (Regles.sansDelai(fs().lecteur(d.m()), d.f())) return "grade";
        var o = plugin.outilsStaff();
        if (sansDelaiAnimation && o != null && o.animation() != null && o.animation().actif(p.getUniqueId())) return "animation";
        return null;
    }

    /** « Hokage », « Conseillère », « Commissaire de la Police », sinon le grade du village. */
    String titre(Membre m, Faction f, Section s, boolean section) {
        if (!section && m.perso.equals(f.chef)) return f.village() ? FactionService.kage(f) : "Chef de " + f.nom;
        if (!section) {
            Charge ch = fs().chargeDe(f, m.perso);
            if (ch != null) return ch.nom;
        }
        if (s != null && !s.cachee) {
            GradeSection g = s.grade(m.grade);
            String n = g != null ? g.nom : m.perso.equals(s.chef) ? "Chef" : "Membre";
            return n + " " + de(s.nom);
        }
        return m.rang.displayName();
    }

    /** Sections ciblables : jamais une section cachée ; un chef de section n'a que la sienne. */
    List<Section> ciblables(DroitCloche d) {
        List<Section> l = new ArrayList<>();
        if (!d.village()) { if (d.section() != null) l.add(d.section()); return l; }
        for (Section s : fs().sections(d.f())) if (!s.cachee && !s.id.equals(d.f().tag.toLowerCase(Locale.ROOT))) l.add(s);
        l.sort(Comparator.comparing(s -> s.nom));
        return l;
    }

    List<Player> zone(Cloche c) {
        List<Player> l = new ArrayList<>();
        Location lc = centre(c);
        if (lc == null) return l;
        double r2 = (double) c.rayon * c.rayon;
        for (Player q : lc.getWorld().getPlayers()) if (q.getLocation().distanceSquared(lc) <= r2) l.add(q);
        return l;
    }

    private boolean concerne(Player q, Faction f, boolean tout, Set<String> sections, Set<Rank> grades) {
        ShinobiCharacter qc = perso(q);
        if (qc == null || !grades.contains(qc.rank())) return false;
        if (tout) return true;
        Membre qm = membre(q);
        return qm != null && f.tag.equals(qm.faction) && sections.contains(qm.section);
    }

    /** Lit la cible demandée et la borne aux droits du sonneur. */
    private record Cible(boolean tout, Set<String> sections, Set<Rank> grades) {}

    private Cible cible(DroitCloche d, JsonObject in) {
        Set<String> ok = new HashSet<>();
        for (Section s : ciblables(d)) ok.add(s.id);
        Set<String> secs = new LinkedHashSet<>();
        if (in.has("sections") && in.get("sections").isJsonArray())
            for (JsonElement e : in.getAsJsonArray("sections")) { String s = e.getAsString(); if (ok.contains(s)) secs.add(s); }
        Set<Rank> gr = new LinkedHashSet<>();
        if (in.has("grades") && in.get("grades").isJsonArray())
            for (JsonElement e : in.getAsJsonArray("grades")) { Rank r = Droits.rang(e.getAsString()); if (r != null) gr.add(r); }
        else gr.addAll(List.of(Rank.values()));
        boolean tout = d.village() && CommCanal.b(in, "tout");
        if (!d.village() && d.section() != null) secs = Set.of(d.section().id);
        return new Cible(tout, secs, gr);
    }

    private int[] compte(Cloche c, Faction f, Cible ci) {
        List<Player> z = zone(c);
        int n = 0;
        for (Player q : z) if (concerne(q, f, ci.tout(), ci.sections(), ci.grades())) n++;
        return new int[]{z.size(), n};
    }

    private int dangersHeure(String tag, long now) {
        List<Long> l = dangers.computeIfAbsent(tag, x -> new ArrayList<>());
        l.removeIf(t -> now - t > 3_600_000L);
        return l.size();
    }

    /* ================================================================ cloche : écran */

    public void ouvrirCloche(Player p, Cloche c) {
        DroitCloche d = droitCloche(p, c);
        if (d == null) { canal.toast(p, "Tu ne peux pas sonner cette cloche.", true); return; }
        if (!pres(p, c)) { canal.toast(p, "Approche-toi de la cloche.", true); return; }
        if (!canal.modde(p)) { p.sendMessage("§7L'écran de la cloche demande le mod Reborn à jour."); return; }
        long now = System.currentTimeMillis();
        JsonObject o = new JsonObject();
        o.addProperty("t", "cloche");
        o.addProperty("ouvrir", true);
        o.addProperty("id", c.id);
        o.addProperty("lieu", c.lieu.isBlank() ? "Cloche de " + village(c.faction) : c.lieu);
        o.addProperty("nom", d.m().nom);
        o.addProperty("titre", d.titre());
        o.addProperty("village", d.village());
        o.addProperty("max", k.texteCloche());
        JsonArray secs = new JsonArray();
        for (Section s : ciblables(d)) {
            JsonObject x = new JsonObject();
            x.addProperty("id", s.id);
            x.addProperty("nom", s.nom);
            x.addProperty("insigne", s.insigne);
            if (s.cree > 0 && now - s.cree < 3L * 86_400_000L) x.addProperty("nouvelle", true);
            secs.add(x);
        }
        o.add("sections", secs);
        if (!d.village() && d.section() != null) o.addProperty("fixe", d.section().id);
        JsonArray gr = new JsonArray();
        for (Rank r : Rank.values()) gr.add(r.displayName());
        o.add("grades", gr);
        String sd = sansDelai(p, d);
        o.addProperty("libreDepuis", c.derniere <= 0 ? -1 : (now - c.derniere) / 1000);
        o.addProperty("attenteCloche", sd != null ? 0 : Math.max(0, (c.derniere + k.delaiCloche() * 1000L - now) / 1000));
        Long dp = dernierePersonne.get(d.m().perso);
        o.addProperty("attenteMoi", sd != null || dp == null ? 0 : Math.max(0, (dp + k.delaiPersonne() * 1000L - now) / 1000));
        o.addProperty("delaiMoi", k.delaiPersonne());
        if (sd != null) o.addProperty("sansDelai", sd);
        if (c.taireJusqua > now) o.addProperty("tue", (c.taireJusqua - now) / 1000);
        o.addProperty("dangers", sd != null ? k.dangerParHeure() : Math.max(0, k.dangerParHeure() - dangersHeure(d.f().tag, now)));
        int[] n = compte(c, d.f(), new Cible(d.village(), d.section() == null ? Set.of() : Set.of(d.section().id), Set.of(Rank.values())));
        o.addProperty("zone", n[0]);
        o.addProperty("concernes", n[1]);
        canal.envoyer(p, o);
    }

    private void compter(Player p, JsonObject in) {
        Cloche c = store.cloches.get(CommCanal.s(in, "id"));
        if (c == null) return;
        DroitCloche d = droitCloche(p, c);
        if (d == null) return;
        int[] n = compte(c, d.f(), cible(d, in));
        JsonObject o = new JsonObject();
        o.addProperty("t", "compte");
        o.addProperty("zone", n[0]);
        o.addProperty("concernes", n[1]);
        canal.envoyer(p, o);
    }

    /* ================================================================ cloche : sonner */

    private void sonner(Player p, JsonObject in) {
        Cloche c = store.cloches.get(CommCanal.s(in, "id"));
        if (c == null) { canal.toast(p, "Cette cloche n'existe plus.", true); return; }
        DroitCloche d = droitCloche(p, c);
        if (d == null) { canal.toast(p, "Tu ne peux pas sonner cette cloche.", true); return; }
        if (!pres(p, c)) { canal.toast(p, "Approche-toi de la cloche.", true); return; }
        String type = CommRegles.type(CommCanal.s(in, "type"));
        if (type == null) type = "normale";
        List<Riche.Seg> texte = Riche.lire(in.get("texte"), k.texteCloche());
        if (texte.isEmpty()) { canal.toast(p, "Écris l'annonce.", true); return; }
        Cible ci = cible(d, in);
        if (ci.grades().isEmpty()) { canal.toast(p, "Coche au moins un grade.", true); return; }
        if (!ci.tout() && ci.sections().isEmpty()) { canal.toast(p, "Coche au moins une section.", true); return; }
        long now = System.currentTimeMillis();
        String sd = sansDelai(p, d);
        String r = CommRegles.refusCloche(now, c.derniere, dernierePersonne.getOrDefault(d.m().perso, 0L),
                dangersHeure(d.f().tag, now), type, c.taireJusqua, sd != null, k);
        if (r != null) { canal.toast(p, r, true); return; }
        c.derniere = now;
        store.marquerPoints();
        dernierePersonne.put(d.m().perso, now);
        if ("danger".equals(type)) dangers.computeIfAbsent(d.f().tag, x -> new ArrayList<>()).add(now);

        String signe = d.m().nom + ", " + d.titre();
        String titre = d.village() ? "ANNONCE DU VILLAGE" : "ANNONCE · " + d.section().nom.toUpperCase(Locale.ROOT);
        JsonObject an = new JsonObject();
        an.addProperty("t", "annonce");
        an.addProperty("type", type);
        an.addProperty("titre", titre);
        an.add("texte", Riche.json(texte));
        an.addProperty("signe", signe);
        an.addProperty("s", k.duree(type));
        an.addProperty("village", d.f().tag);
        String plat = Riche.plat(texte).replace('\n', ' ');
        List<Player> z = zone(c);
        int n = 0;
        for (Player q : z) {
            if (!concerne(q, d.f(), ci.tout(), ci.sections(), ci.grades())) continue;
            n++;
            if (canal.modde(q)) canal.envoyer(q, an);
            else q.sendMessage("§6[" + (d.village() ? "Annonce du village" : "Annonce · " + d.section().nom) + "] §f" + plat + " §7— Signé : " + signe);
        }
        sonnerCloche(c, z, type);
        // le volet J garde l'annonce (onglet Annonces) : tout le village, ou une ligne par section visée
        List<String> cibles = ci.tout() ? List.of("") : new ArrayList<>(ci.sections());
        for (String sid : cibles) {
            Annonce a = new Annonce();
            a.le = now;
            a.auteur = d.m().perso;
            a.titre = d.titre();
            a.section = sid;
            a.texte = plat;
            fs().noterAnnonce(d.f(), a);
        }
        List<String> noms = new ArrayList<>();
        for (String sid : ci.sections()) { Section s = fs().section(sid); if (s != null) noms.add(s.nom); }
        List<String> grs = new ArrayList<>();
        for (Rank g : ci.grades()) grs.add(g.displayName());
        Journal.entree(Categorie.FACTION, "CLOCHE").acteur(p).ciblePersonnage(d.m().owner, d.m().perso)
                .detail("[" + d.f().tag + "] cloche " + c.id + " · " + type + " · " + (ci.tout() ? "tout le village" : String.join(", ", noms))
                        + " · grades " + (grs.size() == Rank.values().length ? "tous" : String.join(", ", grs)) + " · " + n
                        + " destinataire(s) sur " + z.size() + " dans la zone"
                        + (sd == null ? "" : " · sans délai (" + ("animation".equals(sd) ? "mode animation" : d.m().perso.equals(d.f().chef)
                                ? "chef de faction" : "grade " + d.m().rang.displayName()) + ")") + " · « " + plat + " »")
                .lieu(centre(c)).ecrire();
        JsonObject f = new JsonObject();
        f.addProperty("t", "ferme");
        canal.envoyer(p, f);
        canal.toast(p, "La cloche sonne : " + n + (n > 1 ? " personnes concernées" : " personne concernée") + " sur " + z.size() + " dans la zone.", false);
    }

    /** Un coup, deux coups, le tocsin : entendu de toute la zone, depuis la direction de la cloche. */
    private void sonnerCloche(Cloche c, List<Player> z, String type) {
        Location lc = centre(c);
        if (lc == null) return;
        int coups = switch (type) { case "importante" -> 2; case "danger" -> 7; default -> 1; };
        long pas = "danger".equals(type) ? 5L : 16L;
        for (int i = 0; i < coups; i++) {
            final int j = i;
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                for (Player q : z) {
                    if (!q.isOnline() || !q.getWorld().equals(lc.getWorld())) continue;
                    Location ql = q.getLocation();
                    double d = ql.distance(lc);
                    Location src = d <= 12 ? lc : ql.clone().add(lc.toVector().subtract(ql.toVector()).normalize().multiply(10));
                    float vol = d <= 12 ? 2f : (float) Math.max(0.35, 1.6 - d / c.rayon);
                    q.playSound(src, Sound.BLOCK_BELL_USE, SoundCategory.AMBIENT, vol, "danger".equals(type) ? 1.15f : 0.95f);
                    if (j == coups - 1 && !"normale".equals(type))
                        q.playSound(src, Sound.BLOCK_BELL_RESONATE, SoundCategory.AMBIENT, vol * 0.5f, 1f);
                }
            }, i * pas);
        }
    }

    /** Staff : faire taire les cloches d'un village (et retirer la ligne en cours chez tout le monde). */
    public int taire(String tag, int minutes) {
        long jusqua = minutes <= 0 ? 0 : System.currentTimeMillis() + minutes * 60_000L;
        int n = 0;
        for (Cloche c : store.cloches.values()) if (c.faction.equals(tag)) { c.taireJusqua = jusqua; n++; }
        store.marquerPoints();
        JsonObject o = new JsonObject();
        o.addProperty("t", "annonce_fin");
        o.addProperty("village", tag);
        for (Player p : Bukkit.getOnlinePlayers()) canal.envoyer(p, o);
        return n;
    }

    /* ================================================================ volière : écran */

    private boolean peutPrendre(Player p, Voliere v) {
        ShinobiCharacter c = perso(p);
        if (c == null) return false;
        Faction f = faction(v.faction);
        Rank seuil = f == null ? Rank.GENIN : f.seuils.getOrDefault("prendre_voliere", Rank.GENIN);
        return c.rank().ordinal() >= seuil.ordinal();
    }

    private String refusPalais(Player p, Voliere b) {
        if (b.type != TypeVoliere.PALAIS) return null;
        Membre m = membre(p);
        Faction f = faction(b.faction);
        if (m == null || f == null || !Regles.seuil(fs().lecteur(m), f, "voliere_palais"))
            return f == null ? "Volière du palais fermée." : "Dès " + f.seuils.getOrDefault("voliere_palais", Rank.CHUNIN).displayName();
        return null;
    }

    List<Missive> enAttente(Voliere v) {
        List<Missive> l = new ArrayList<>();
        for (Missive m : store.missives.values()) if (m.etat == Etat.ATTENTE && v.id.equals(m.arrivee)) l.add(m);
        l.sort(Comparator.comparingLong((Missive m) -> m.arrive).reversed());
        return l;
    }

    public void ouvrirVoliere(Player p, Voliere v, int onglet, boolean ouvrir) {
        if (ModeStaff.horsJeu(p)) { canal.toast(p, "En mode staff : aucune mécanique RP.", true); return; }
        ShinobiCharacter c = perso(p);
        if (c == null) return;
        if (!pres(p, v)) { canal.toast(p, "Approche-toi de la volière.", true); return; }
        if (!canal.modde(p)) { p.sendMessage("§7L'écran de la volière demande le mod Reborn à jour."); return; }
        Membre moi = membre(p);
        long now = System.currentTimeMillis();
        JsonObject o = new JsonObject();
        o.addProperty("t", "voliere");
        if (ouvrir) o.addProperty("ouvrir", true);
        o.addProperty("onglet", onglet);
        o.addProperty("id", v.id);
        o.addProperty("nom", v.nom);
        o.addProperty("type", v.type.libelle);
        o.addProperty("moi", moi != null ? moi.nom : c.name());
        o.addProperty("now", now);
        var mo = plugin.monnaie();
        o.addProperty("bourse", mo == null ? 0 : mo.bourses().montant(p));
        o.addProperty("max", k.texteMissive());
        o.addProperty("adresseMax", k.adresseMax());
        o.addProperty("destMax", k.destMax());
        o.addProperty("garde", k.gardeJours());
        o.addProperty("capacite", v.type.capacite);
        // contacts : les personnes connues, sous le nom appris
        JsonArray ct = new JsonArray();
        c.contacts().forEach((id, kn) -> {
            JsonObject x = new JsonObject();
            x.addProperty("ref", "c:" + id);
            x.addProperty("nom", kn.display());
            Membre mm = fs() == null ? null : fs().membre(id);
            if (mm != null && mm.mort) return;
            x.addProperty("sous", (mm == null ? "" : mm.rang.displayName() + " · ") + (mm != null && mm.feminin() ? "connue" : "connu"));
            if (mm != null && mm.owner != null) x.addProperty("mc", mm.owner.toString());
            ct.add(x);
        });
        o.add("contacts", ct);
        // volières d'arrivée : ici, le pays (par distance), le palais, puis les tours des autres villages
        List<Voliere> ds = new ArrayList<>();
        for (Voliere b : store.volieres.values()) if (CommRegles.route(v, b) == null) ds.add(b);
        ds.sort(Comparator.comparingInt((Voliere b) -> b.id.equals(v.id) ? 0 : !b.faction.equals(v.faction) ? 3 : b.type == TypeVoliere.PALAIS ? 2 : 1)
                .thenComparingDouble(b -> { double d = distance(v, b); return d < 0 ? 1e9 : d; }));
        JsonArray dj = new JsonArray();
        for (Voliere b : ds) {
            JsonObject x = new JsonObject();
            x.addProperty("id", b.id);
            x.addProperty("nom", b.id.equals(v.id) ? "Ici, " + minuscule(b.nom) : b.nom);
            x.addProperty("lieu", b.nom);
            x.addProperty("delai", CommRegles.delai(v, b, distance(v, b), k));
            x.addProperty("cout", CommRegles.cout(v, b, k));
            String rp = refusPalais(p, b);
            if (b.type == TypeVoliere.PALAIS) {
                Faction fb = faction(b.faction);
                x.addProperty("cond", (fb == null ? Rank.CHUNIN : fb.seuils.getOrDefault("voliere_palais", Rank.CHUNIN)).displayName() + " et plus");
            }
            if (rp != null) x.addProperty("refus", rp);
            dj.add(x);
        }
        o.add("destinations", dj);
        // missives présentes : les siennes en tête
        JsonArray ms = new JsonArray();
        List<Missive> att = enAttente(v);
        att.sort(Comparator.comparing((Missive m) -> !c.id().equals(m.pour().perso())));
        for (Missive m : att) ms.add(ligne(m, c.id()));
        o.add("missives", ms);
        o.addProperty("peutPrendre", peutPrendre(p, v));
        Faction f = faction(v.faction);
        o.addProperty("seuilPrendre", (f == null ? Rank.GENIN : f.seuils.getOrDefault("prendre_voliere", Rank.GENIN)).displayName());
        // la missive en main (transfert)
        ItemStack main = p.getInventory().getItemInMainHand();
        Missive mm = store.missives.get(String.valueOf(MissiveObjet.id(main)));
        if (mm != null && mm.etat == Etat.PRISE) {
            JsonObject x = new JsonObject();
            x.addProperty("id", mm.id);
            x.addProperty("pour", MissiveObjet.pour(mm));
            x.addProperty("scellee", mm.scellee);
            o.add("enMain", x);
        }
        canal.envoyer(p, o);
    }

    private JsonObject ligne(Missive m, UUID moi) {
        JsonObject x = new JsonObject();
        x.addProperty("id", m.id);
        x.addProperty("pour", m.pour().nom());
        JsonArray autres = new JsonArray();
        for (int i = 0; i < m.dests.size(); i++) if (i != m.index) autres.add(m.dests.get(i).nom());
        x.add("autres", autres);
        if (moi != null && moi.equals(m.pour().perso())) x.addProperty("toi", true);
        if (m.signee) x.addProperty("signe", m.auteurNom);
        Voliere dep = store.volieres.get(m.depart);
        x.addProperty("de", dep == null ? "une volière" : minuscule(dep.nom));
        x.addProperty("le", m.arrive);
        x.addProperty("scellee", m.scellee);
        return x;
    }

    /* ================================================================ volière : envoyer, transférer */

    private void envoyer(Player p, JsonObject in, boolean transfert) {
        Voliere v = store.volieres.get(CommCanal.s(in, "voliere"));
        Voliere b = store.volieres.get(CommCanal.s(in, "vers"));
        ShinobiCharacter c = perso(p);
        if (v == null || c == null) { canal.toast(p, "Volière introuvable.", true); return; }
        if (ModeStaff.horsJeu(p)) { canal.toast(p, "En mode staff : aucune mécanique RP.", true); return; }
        if (!pres(p, v)) { canal.toast(p, "Approche-toi de la volière.", true); return; }
        if (b == null) { canal.toast(p, "Choisis la volière d'arrivée.", true); return; }
        String r = CommRegles.route(v, b);
        if (r == null) r = refusPalais(p, b);
        if (r != null) { canal.toast(p, r.startsWith("Dès") ? "Volière du palais : " + minuscule(r) + "." : r, true); return; }
        // destinataires : personnes connues (sous le nom appris) ou adresses écrites à la main
        List<Dest> dests = new ArrayList<>();
        Set<String> vus = new HashSet<>();
        if (in.has("dests") && in.get("dests").isJsonArray()) {
            for (JsonElement e : in.getAsJsonArray("dests")) {
                if (!e.isJsonObject()) continue;
                JsonObject d = e.getAsJsonObject();
                String ref = CommCanal.s(d, "ref");
                if (ref.startsWith("c:")) {
                    UUID id;
                    try { id = UUID.fromString(ref.substring(2)); } catch (IllegalArgumentException ex) { continue; }
                    ShinobiCharacter.KnownName kn = c.knownNameOf(id);
                    if (kn == null || !vus.add("c:" + id)) continue;
                    dests.add(new Dest(kn.display(), id));
                } else {
                    String a = Riche.nettoyer(CommCanal.s(d, "adresse")).replace('\n', ' ').trim();
                    if (a.length() > k.adresseMax()) a = a.substring(0, k.adresseMax()).trim();
                    if (a.isEmpty() || !vus.add("a:" + a.toLowerCase(Locale.ROOT))) continue;
                    dests.add(new Dest(a, null));
                }
            }
        }
        if (dests.isEmpty()) { canal.toast(p, "À qui ? ajoute un destinataire.", true); return; }
        if (dests.size() > (transfert ? 1 : k.destMax())) {
            canal.toast(p, transfert ? "Une missive transférée ne part qu'à une personne." : k.destMax() + " destinataires au plus.", true);
            return;
        }
        Missive orig = null;
        ItemStack main = p.getInventory().getItemInMainHand();
        List<Riche.Seg> texte = List.of();
        if (transfert) {
            orig = store.missives.get(String.valueOf(MissiveObjet.id(main)));
            if (orig == null || orig.etat != Etat.PRISE) { canal.toast(p, "Prends en main la missive à transférer.", true); return; }
        } else {
            texte = Riche.lire(in.get("texte"), k.texteMissive());
            if (texte.isEmpty()) { canal.toast(p, "La missive est vide.", true); return; }
        }
        long unite = CommRegles.cout(v, b, k), total = unite * dests.size();
        var mo = plugin.monnaie();
        if (total > 0) {
            if (mo == null) { canal.toast(p, "La bourse n'est pas disponible.", true); return; }
            if (mo.bourses().montant(p) < total) { canal.toast(p, "Pas assez dans ta bourse : " + Bourse.fmt(total) + " ryo.", true); return; }
        }
        int delai = CommRegles.delai(v, b, distance(v, b), k);
        long now = System.currentTimeMillis();
        if (total > 0 && !mo.bourses().debiter(p, total, "MISSIVE", (transfert ? "transfert" : "missive") + " vers « " + b.nom + " » ("
                + dests.size() + " × " + unite + ")", p)) {
            canal.toast(p, "Paiement impossible.", true);
            return;
        }
        List<String> ids = new ArrayList<>();
        if (transfert) {
            p.getInventory().setItemInMainHand(null);
            orig.dests.clear();
            orig.dests.add(dests.get(0));
            orig.index = 0;
            orig.groupe = nouvelId("G");
            orig.depart = v.id;
            orig.arrivee = b.id;
            orig.envoye = now;
            orig.arrive = now + delai * 1000L;
            orig.etat = Etat.VOL;
            orig.preneur = null;
            orig.prevenu = false;
            orig.transferee = true;
            ids.add(orig.id);
        } else {
            Membre moi = membre(p);
            String groupe = nouvelId("G");
            String bal = Riche.balises(texte);
            for (int i = 0; i < dests.size(); i++) {
                Missive m = new Missive();
                m.id = nouvelId("M");
                m.groupe = groupe;
                m.auteur = c.id();
                m.auteurCompte = p.getUniqueId();
                m.auteurNom = moi != null ? moi.nom : c.name();
                m.signee = CommCanal.b(in, "signee");
                m.dests.addAll(dests);
                m.index = i;
                m.texte = bal;
                m.depart = v.id;
                m.arrivee = b.id;
                m.envoye = now;
                m.arrive = now + delai * 1000L;
                store.missives.put(m.id, m);
                ids.add(m.id);
            }
        }
        store.marquerMissives();
        List<String> noms = new ArrayList<>();
        for (Dest d : dests) noms.add(d.nom() + (d.perso() == null ? " (adresse)" : ""));
        Journal.entree(Categorie.MISSIVE, transfert ? "TRANSFERT" : "ENVOI").acteur(p).ciblePersonnage(p.getUniqueId(), c.id())
                .detail(String.join(", ", ids) + " · " + v.nom + " → " + b.nom + " · pour " + String.join(", ", noms)
                        + (transfert ? "" : " · " + (CommCanal.b(in, "signee") ? "signée" : "anonyme")) + " · " + total + " ryo · arrivée dans "
                        + CommRegles.minutes(delai * 1000L))
                .lieu(p.getLocation()).ecrire();
        Location lv = centre(v);
        if (lv != null) {
            lv.getWorld().playSound(lv, Sound.ENTITY_PARROT_FLY, SoundCategory.NEUTRAL, 1f, 0.7f);
            lv.getWorld().playSound(lv, Sound.ENTITY_BAT_TAKEOFF, SoundCategory.NEUTRAL, 0.6f, 0.6f);
            lv.getWorld().spawnParticle(Particle.CLOUD, lv.clone().add(0, 1.2, 0), 6, 0.2, 0.2, 0.2, 0.02);
        }
        JsonObject e = new JsonObject();
        e.addProperty("t", "envoye");
        e.addProperty("transfert", transfert);
        canal.envoyer(p, e);
        canal.toast(p, "Le corbeau s'envole : arrivée vers " + heure(now + delai * 1000L) + ", " + minuscule(b.nom) + ".", false);
        ouvrirVoliere(p, v, 0, false);
    }

    /* ================================================================ arrivées, garde, alertes */

    private void tick() {
        long now = System.currentTimeMillis();
        boolean change = false;
        List<Missive> arrivees = new ArrayList<>();
        for (Missive m : new ArrayList<>(store.missives.values())) {
            if (m.etat == Etat.VOL && m.arrive <= now) arrivees.add(m);
            else if (m.etat == Etat.ATTENTE && now - m.arrive > k.gardeJours() * 86_400_000L) {
                bruler(m, "non prise en " + k.gardeJours() + " jours : le gardien la brûle");
                change = true;
            } else if (m.etat == Etat.BRULEE && now - Math.max(m.arrive, m.priseLe) > 30L * 86_400_000L) {
                store.missives.remove(m.id);
                change = true;
            }
        }
        arrivees.sort(Comparator.comparingLong(m -> m.arrive));
        Set<String> groupesPalais = new HashSet<>();
        for (Missive m : arrivees) {
            arriver(m, groupesPalais);
            change = true;
        }
        if (change) store.marquerMissives();
    }

    private void bruler(Missive m, String raison) {
        m.etat = Etat.BRULEE;
        Journal.entree(Categorie.MISSIVE, "BRULEE").cibleTexte(m.id).detail(m.id + " pour " + m.pour().nom() + " · " + raison).ecrire();
    }

    private void arriver(Missive m, Set<String> groupesPalais) {
        Voliere b = store.volieres.get(m.arrivee);
        if (b == null) { bruler(m, "volière d'arrivée retirée"); return; }
        List<Missive> att = enAttente(b);
        while (att.size() >= b.type.capacite) {
            Missive vieille = att.remove(att.size() - 1);
            bruler(vieille, b.nom + " pleine : la plus ancienne brûle");
        }
        m.etat = Etat.ATTENTE;
        Journal.entree(Categorie.MISSIVE, "ARRIVEE").cibleTexte(m.id).detail(m.id + " à " + b.nom + " · pour " + m.pour().nom()).ecrire();
        // le destinataire est prévenu (un clan transmet par l'esprit) ; une adresse écrite à la main ne prévient personne
        UUID dest = m.pour().perso();
        if (dest != null) {
            Player q = joueurDe(dest);
            if (q != null) { alerteMissive(q, b); m.prevenu = true; }
        } else m.prevenu = true;
        if (b.type == TypeVoliere.PALAIS && groupesPalais.add(m.groupe)) alertePalais(b);
    }

    private Player joueurDe(UUID perso) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            ShinobiCharacter c = perso(p);
            if (c != null && c.id().equals(perso)) return p;
        }
        return null;
    }

    private void alerteMissive(Player q, Voliere b) {
        String lieu = minuscule(b.nom);
        if (!canal.modde(q)) { q.sendMessage("§6[Missive] §fUn corbeau a déposé une missive pour toi à la §e" + lieu + "§f."); return; }
        JsonObject o = new JsonObject();
        o.addProperty("t", "alerte");
        o.addProperty("k", "missive");
        o.addProperty("lieu", lieu);
        o.addProperty("s", k.dureeAlerte());
        canal.envoyer(q, o);
    }

    /** Volière du palais : une ligne sans auteur, sans contenu, sans destinataire, chez les gradés du palais en ligne. */
    private void alertePalais(Voliere b) {
        Faction f = faction(b.faction);
        if (f == null || fs() == null) return;
        for (Player q : Bukkit.getOnlinePlayers()) {
            Membre m = membre(q);
            if (m == null || !f.tag.equals(m.faction) || ModeStaff.horsJeu(q)) continue;
            if (!Regles.seuil(fs().lecteur(m), f, "alertes_palais") && !fs().charge(f, m.perso, Droits.C_ALERTES)) continue;
            if (!canal.modde(q)) { q.sendMessage("§6[Volière du palais] §fUne missive importante est arrivée à la volière du palais."); continue; }
            JsonObject o = new JsonObject();
            o.addProperty("t", "alerte");
            o.addProperty("k", "palais");
            o.addProperty("s", k.dureeAlerte());
            canal.envoyer(q, o);
        }
    }

    /** À la connexion : les missives arrivées pour ce personnage pendant son absence. */
    private void prevenirEnAttente(Player p) {
        ShinobiCharacter c = perso(p);
        if (c == null) return;
        boolean change = false;
        for (Missive m : store.missives.values()) {
            if (m.etat != Etat.ATTENTE || m.prevenu || !c.id().equals(m.pour().perso())) continue;
            Voliere b = store.volieres.get(m.arrivee);
            if (b == null) continue;
            alerteMissive(p, b);
            m.prevenu = true;
            change = true;
        }
        if (change) store.marquerMissives();
    }

    /* ================================================================ prendre, lire, brûler */

    private void prendre(Player p, String id, boolean lire) {
        Missive m = store.missives.get(id);
        ShinobiCharacter c = perso(p);
        if (m == null || c == null || m.etat != Etat.ATTENTE) { canal.toast(p, "Cette missive n'est plus là.", true); return; }
        Voliere v = store.volieres.get(m.arrivee);
        if (v == null || !pres(p, v)) { canal.toast(p, "Approche-toi de la volière.", true); return; }
        if (ModeStaff.horsJeu(p)) { canal.toast(p, "En mode staff : aucune mécanique RP.", true); return; }
        if (!peutPrendre(p, v)) { canal.toast(p, "Prendre à une volière : à partir de Genin.", true); return; }
        InventoryManager im = plugin.rpInventory();
        ItemStack probe = MissiveObjet.creer(m);
        String refus = im == null ? null : im.refusReception(p, probe);
        if (refus != null) { canal.toast(p, "Impossible de la prendre : " + refus + ".", true); return; }
        boolean briseAvant = !m.scellee;
        m.etat = Etat.PRISE;
        m.preneur = c.id();
        m.priseLe = System.currentTimeMillis();
        if (lire && m.scellee) briser(m, c.id());
        store.marquerMissives();
        InventoryManager.donner(p, MissiveObjet.creer(m));
        boolean pourLui = c.id().equals(m.pour().perso());
        Journal.entree(Categorie.MISSIVE, "PRISE").acteur(p).ciblePersonnage(p.getUniqueId(), c.id())
                .detail(m.id + " à " + v.nom + " · pour " + m.pour().nom() + (pourLui ? "" : " · prise par quelqu'un d'autre que le destinataire")
                        + (lire ? " · lue aussitôt" : ""))
                .lieu(p.getLocation()).ecrire();
        if (lire) envoyerLettre(p, m, briseAvant, m.briseePar != null && !briseAvant);
        else canal.toast(p, "Missive prise : dans ta sacoche.", false);
        ouvrirVoliere(p, v, 1, false);
    }

    private void briser(Missive m, UUID par) {
        m.scellee = false;
        m.briseePar = par;
        m.briseeLe = System.currentTimeMillis();
        Journal.entree(Categorie.MISSIVE, "DESCELLEE").cibleTexte(m.id).detail(m.id + " : cachet brisé").ecrire();
    }

    /** Clic droit sur la missive en main : la première lecture brise le cachet pour toujours. */
    private void lireObjet(Player p, String id) {
        if (ModeStaff.horsJeu(p)) { canal.toast(p, "En mode staff : aucune mécanique RP.", true); return; }
        Missive m = store.missives.get(id);
        ShinobiCharacter c = perso(p);
        if (m == null || c == null) { canal.toast(p, "Cette missive est illisible.", true); return; }
        boolean briseAvant = !m.scellee;
        if (m.scellee) {
            briser(m, c.id());
            store.marquerMissives();
            p.getInventory().setItemInMainHand(MissiveObjet.maj(p.getInventory().getItemInMainHand(), m));
            p.playSound(p.getLocation(), Sound.ITEM_AXE_WAX_OFF, SoundCategory.PLAYERS, 0.9f, 0.8f);
        }
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, SoundCategory.PLAYERS, 0.8f, 1f);
        Journal.entree(Categorie.MISSIVE, "LECTURE").acteur(p).ciblePersonnage(p.getUniqueId(), c.id())
                .detail(m.id + " · pour " + m.pour().nom() + (briseAvant ? "" : " · cachet brisé à cette lecture")).ecrire();
        envoyerLettre(p, m, briseAvant, !briseAvant);
    }

    private void envoyerLettre(Player p, Missive m, boolean dejaBrise, boolean vientDeBriser) {
        if (!canal.modde(p)) {
            p.sendMessage("§6[Missive] §7À " + String.join(", ", m.dests.stream().map(Dest::nom).toList()) + " :");
            p.sendMessage("§f" + Riche.plat(Riche.deBalises(m.texte)));
            p.sendMessage("§7— " + (m.signee ? m.auteurNom : "sans signature"));
            return;
        }
        ShinobiCharacter c = perso(p);
        JsonObject o = new JsonObject();
        o.addProperty("t", "lettre");
        o.addProperty("id", m.id);
        JsonArray a = new JsonArray();
        for (Dest d : m.dests) a.add(d.nom());
        o.add("a", a);
        Voliere b = store.volieres.get(m.arrivee);
        o.addProperty("lieu", b == null ? "" : minuscule(b.nom));
        o.addProperty("heure", heure(m.arrive));
        o.add("texte", Riche.json(Riche.deBalises(m.texte)));
        if (m.signee) o.addProperty("signe", m.auteurNom);
        o.addProperty("dejaBrise", dejaBrise && c != null && m.briseePar != null && !m.briseePar.equals(c.id()));
        o.addProperty("vientDeBriser", vientDeBriser);
        canal.envoyer(p, o);
    }

    private void bruler(Player p, String id) {
        if (ModeStaff.horsJeu(p)) return;
        Missive m = store.missives.get(id);
        if (m == null) return;
        ItemStack[] inv = p.getInventory().getContents();
        int slot = -1;
        if (id.equals(MissiveObjet.id(p.getInventory().getItemInMainHand()))) slot = p.getInventory().getHeldItemSlot();
        else for (int i = 0; i < inv.length; i++) if (id.equals(MissiveObjet.id(inv[i]))) { slot = i; break; }
        if (slot < 0) { canal.toast(p, "Garde la missive en main pour la brûler.", true); return; }
        p.getInventory().setItem(slot, null);
        bruler(m, "brûlée par " + p.getName());
        store.marquerMissives();
        InventoryManager im = plugin.rpInventory();
        if (im != null) im.push(p, false);
        p.playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, SoundCategory.PLAYERS, 0.6f, 1.2f);
        p.getWorld().spawnParticle(Particle.SMOKE, p.getLocation().add(0, 1.2, 0), 8, 0.15, 0.1, 0.15, 0.01);
        JsonObject f = new JsonObject();
        f.addProperty("t", "ferme");
        canal.envoyer(p, f);
        canal.toast(p, "La missive part en fumée.", false);
    }

    /* ================================================================ staff */

    public Cloche poserCloche(Player acteur, Block b, Entity e, Faction f, int rayon, String lieu) {
        Location l = e != null ? e.getLocation() : b.getLocation();
        Cloche c = new Cloche();
        c.id = f.tag.toLowerCase(Locale.ROOT) + "_cloche_" + (store.cloches.size() + 1);
        while (store.cloches.containsKey(c.id)) c.id += "b";
        c.faction = f.tag;
        c.monde = l.getWorld().getName();
        c.x = l.getBlockX();
        c.y = l.getBlockY();
        c.z = l.getBlockZ();
        c.entite = e == null ? null : e.getUniqueId();
        c.rayon = rayon > 0 ? rayon : k.rayonDefaut();
        c.lieu = lieu == null ? "" : lieu.trim();
        c.pose = System.currentTimeMillis();
        c.posePar = acteur.getUniqueId();
        store.cloches.put(c.id, c);
        store.marquerPoints();
        pointsEnvoyes.clear();
        Journal.entree(Categorie.STAFF, "CLOCHE_POSEE").acteur(acteur).cibleTexte(c.id)
                .detail("[" + f.tag + "] " + c.id + " · rayon " + c.rayon + (c.lieu.isEmpty() ? "" : " · « " + c.lieu + " »")).lieu(l).ecrire();
        return c;
    }

    public Voliere poserVoliere(Player acteur, Block b, Entity e, Faction f, TypeVoliere t, String nom) {
        Location l = e != null ? e.getLocation() : b.getLocation();
        Voliere v = new Voliere();
        v.id = f.tag.toLowerCase(Locale.ROOT) + "_" + t.name().toLowerCase(Locale.ROOT) + "_" + (store.volieres.size() + 1);
        while (store.volieres.containsKey(v.id)) v.id += "b";
        v.faction = f.tag;
        v.type = t;
        v.nom = nom == null || nom.isBlank() ? t.libelle + " de " + village(f.tag) : nom.trim();
        v.monde = l.getWorld().getName();
        v.x = l.getBlockX();
        v.y = l.getBlockY();
        v.z = l.getBlockZ();
        v.entite = e == null ? null : e.getUniqueId();
        v.pose = System.currentTimeMillis();
        v.posePar = acteur.getUniqueId();
        store.volieres.put(v.id, v);
        store.marquerPoints();
        pointsEnvoyes.clear();
        Journal.entree(Categorie.STAFF, "VOLIERE_POSEE").acteur(acteur).cibleTexte(v.id)
                .detail("[" + f.tag + "] " + v.id + " · " + t.libelle + " · « " + v.nom + " »").lieu(l).ecrire();
        return v;
    }

    public boolean retirerCloche(org.bukkit.command.CommandSender s, String id) {
        Cloche c = store.cloches.remove(id);
        if (c == null) return false;
        store.marquerPoints();
        pointsEnvoyes.clear();
        Journal.entree(Categorie.STAFF, "CLOCHE_RETIREE").acteur(s).cibleTexte(id).detail("[" + c.faction + "] " + id).ecrire();
        return true;
    }

    public int viderVoliere(org.bukkit.command.CommandSender s, Voliere v, String motif) {
        int n = 0;
        for (Missive m : enAttente(v)) { bruler(m, "volière vidée par le staff · « " + motif + " »"); n++; }
        store.marquerMissives();
        Journal.entree(Categorie.STAFF, "VOLIERE_VIDEE").acteur(s).cibleTexte(v.id).detail(v.nom + " · " + n + " missive(s) · « " + motif + " »").ecrire();
        return n;
    }

    public boolean retirerVoliere(org.bukkit.command.CommandSender s, String id, String motif) {
        Voliere v = store.volieres.get(id);
        if (v == null) return false;
        viderVoliere(s, v, motif);
        for (Missive m : store.missives.values()) if (m.etat == Etat.VOL && id.equals(m.arrivee)) bruler(m, "volière d'arrivée retirée");
        store.volieres.remove(id);
        store.marquerPoints();
        store.marquerMissives();
        pointsEnvoyes.clear();
        Journal.entree(Categorie.STAFF, "VOLIERE_RETIREE").acteur(s).cibleTexte(id).detail(v.nom + " · « " + motif + " »").ecrire();
        return true;
    }

    public Missive missive(String id) { return id == null ? null : store.missives.get(id.toUpperCase(Locale.ROOT)); }

    public Map<String, Integer> attenteParVoliere() {
        Map<String, Integer> n = new HashMap<>();
        for (Missive m : store.missives.values()) if (m.etat == Etat.ATTENTE) n.merge(m.arrivee, 1, Integer::sum);
        return n;
    }
}
