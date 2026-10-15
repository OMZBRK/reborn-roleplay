package com.reborn.shinobicore.boutique;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.JournalService.Categorie;
import com.reborn.shinobicore.character.PlayerIds;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.entreprise.Entreprise;
import com.reborn.shinobicore.inventory.InventoryManager;
import com.reborn.shinobicore.inventory.Notifs;
import com.reborn.shinobicore.journal.Journal;
import com.reborn.shinobicore.monnaie.Bourse;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Les boutiques-lieux côté serveur (BOUTIQUES.md, décisions du 15/10) : le <b>tailleur</b> (essayage sur l'estrade,
 * caméra en face du personnage, côté mod) et le <b>bazar</b> (vivres, cartes). Clic droit sur un comptoir marqué par
 * le staff → le client monte sur une estrade libre de la boutique et l'écran s'ouvre par-dessus le jeu ; le comptoir
 * ne montre que <b>ses</b> catégories (et sa liste précise d'articles s'il en a une).
 *
 * <p>Canal {@code reborn:boutique} (JSON UTF-8 brut, protocole : PLUGINS_REFERENCE §4 « Boutiques ») :
 * <ul>
 *   <li>S2C {@code {"t":"ouvrir"|"etat","boutique":{id,nom,genre,sous},"comptoir","bourse","appearance",
 *       "cam"?:{x,y,z,yaw,dist,haut},"categories":[{id,nom}],"articles":[{id,nom,cat,genre,prix,possede?,verrou?,raison?,
 *       item?}],"toast"?,"achat"?}} ; {@code {"t":"ferme"}} (coup reçu, trop loin, fermée).</li>
 *   <li>C2S (texte) {@code fermer}, {@code etat}, {@code acheter:<id>}, {@code acheter_porter:<id>\n<apparence>},
 *       {@code porter:<id>\n<apparence>}, {@code ranger\n<apparence>}.</li>
 * </ul>
 * Tout est revérifié ici (bourse, condition, place, état) ; achats payés avec la <b>bourse</b>, ryo détruits (V1),
 * journal {@code ECONOMIE ACHAT}. Les essais restent côté client et ne sont pas journalisés.
 */
public final class BoutiqueService implements Listener, PluginMessageListener {

    public static final String CANAL = "reborn:boutique";
    /** Délai sans coup avant d'ouvrir (combat), et distance au-delà de laquelle l'écran se ferme. */
    private static final long COMBAT_MS = 5_000L;
    private static final double PORTEE = 8.0, PORTEE_ESTRADE = 12.0;

    /** Écran ouvert : boutique, comptoir, estrade occupée (−1 : aucune), point d'ancrage (null : test staff). */
    record Session(String boutique, int comptoir, int estrade, Location ancre) {}

    private final ShinobiCore plugin;
    private final BoutiqueStore store;
    private final Catalogue catalogue;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Long> dernierCoup = new ConcurrentHashMap<>();
    private final Map<String, JsonObject> objets = new HashMap<>();

    public BoutiqueService(ShinobiCore plugin) {
        this.plugin = plugin;
        this.store = new BoutiqueStore(plugin);
        this.catalogue = new Catalogue(plugin);
    }

    public void start() {
        catalogue.charger();
        store.charger();
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CANAL)) m.registerOutgoingPluginChannel(plugin, CANAL);
        if (!m.isIncomingChannelRegistered(plugin, CANAL)) m.registerIncomingPluginChannel(plugin, CANAL, this);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 20L);
        BoutiqueCommand cmd = new BoutiqueCommand(plugin, this);
        PluginCommand pc = plugin.getCommand("boutique");
        if (pc != null) { pc.setExecutor(cmd); pc.setTabCompleter(cmd); }
        else plugin.getLogger().warning("Command 'boutique' is not declared in plugin.yml.");
        plugin.getLogger().info("[boutique] " + store.toutes().size() + " boutique(s) ; canal " + CANAL + ".");
    }

    public void stop() {
        for (UUID id : sessions.keySet()) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) envoyer(p, ferme());
        }
        sessions.clear();
    }

    public void recharger() {
        catalogue.charger();
        objets.clear();
    }

    public BoutiqueStore store() { return store; }

    public Catalogue catalogue() { return catalogue; }

    /* ================================================================ ouvrir */

    /**
     * Ouvre l'écran d'un comptoir. {@code test} (= {@code /boutique voir}) : à distance, sans estrade ni contrôle d'état.
     */
    public void ouvrir(Player p, Boutique b, Boutique.Comptoir comptoir, boolean test) {
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        if (c == null) return;
        if (!test) {
            if (!b.ouverte) { Notifs.msg(p, "refus", null, "Fermé", "revenez plus tard"); return; }
            String v = verrou(p);
            if (v != null) { Notifs.msg(p, "refus", null, "Impossible", v); return; }
        }
        if (!p.getListeningPluginChannels().contains(CANAL)) {
            p.sendMessage("§6[" + b.nom + "] §fL'écran de la boutique demande le mod Reborn à jour.");
            return;
        }
        int ci = b.comptoirs.indexOf(comptoir);
        if (ci < 0) ci = 0;
        int ei = -1;
        Location ancre = test ? null : comptoir.centre();
        Boutique.Estrade estrade = null;
        if (!test && b.genre == Boutique.Genre.TAILLEUR && !b.estrades.isEmpty()) {
            boolean occupee = false;
            double best = Double.MAX_VALUE;
            Location cc = comptoir.centre();
            for (int i = 0; i < b.estrades.size(); i++) {
                Location l = b.estrades.get(i).lieu();
                if (l == null || cc == null || !l.getWorld().equals(cc.getWorld()) || l.distance(cc) > PORTEE_ESTRADE) continue;
                if (occupee(b, i, p.getUniqueId())) { occupee = true; continue; }
                double d = l.distanceSquared(cc);
                if (d < best) { best = d; ei = i; }
            }
            if (ei < 0 && occupee) { Notifs.msg(p, "refus", null, "Quelqu'un essaie déjà", "patiente un instant"); return; }
            if (ei >= 0) {
                estrade = b.estrades.get(ei);
                Location l = estrade.lieu();
                p.teleport(l);
                ancre = l;
            }
        }
        sessions.put(p.getUniqueId(), new Session(b.id, ci, ei, ancre));
        JsonObject o = etat(p, c, b, comptoir, "ouvrir", null);
        if (estrade != null) {
            JsonObject cam = new JsonObject();
            cam.addProperty("x", estrade.x());
            cam.addProperty("y", estrade.y());
            cam.addProperty("z", estrade.z());
            cam.addProperty("yaw", estrade.yaw());
            cam.addProperty("dist", b.camDistance);
            cam.addProperty("haut", b.camHauteur);
            o.add("cam", cam);
        }
        envoyer(p, o);
        p.playSound(p.getLocation(), Sound.BLOCK_WOOL_PLACE, SoundCategory.PLAYERS, 0.5f, 1.2f);
    }

    private boolean occupee(Boutique b, int estrade, UUID sauf) {
        for (Map.Entry<UUID, Session> e : sessions.entrySet()) {
            if (e.getKey().equals(sauf)) continue;
            Session s = e.getValue();
            if (s.boutique().equals(b.id) && s.estrade() == estrade && Bukkit.getPlayer(e.getKey()) != null) return true;
        }
        return false;
    }

    /** Pourquoi ne peut-il pas entrer : KO, porté, étourdi, mode staff, combat ; null sinon. */
    private String verrou(Player p) {
        if (com.reborn.shinobicore.staff.outils.ModeStaff.horsJeu(p)) return "mode staff";
        InventoryManager im = plugin.rpInventory();
        String v = im == null ? null : im.lockReason(p);
        if (v != null) return v;
        Long t = dernierCoup.get(p.getUniqueId());
        if (t != null && System.currentTimeMillis() - t < COMBAT_MS) return "pas en plein combat";
        return null;
    }

    /* ================================================================ état */

    private JsonObject etat(Player p, ShinobiCharacter c, Boutique b, Boutique.Comptoir comptoir, String t, String toast) {
        JsonObject o = new JsonObject();
        o.addProperty("t", t);
        JsonObject bo = new JsonObject();
        bo.addProperty("id", b.id);
        bo.addProperty("nom", b.nom);
        bo.addProperty("genre", b.genre.name().toLowerCase(java.util.Locale.ROOT));
        bo.addProperty("sous", sousTitre(b, p));
        o.add("boutique", bo);
        o.addProperty("comptoir", comptoir.libelle == null ? "" : comptoir.libelle);
        o.addProperty("bourse", portee(p, c));
        o.addProperty("appearance", c.appearance());
        JsonArray cats = new JsonArray();
        for (Catalogue.Categorie cat : comptoir.categories) {
            JsonObject co = new JsonObject();
            co.addProperty("id", cat.id);
            co.addProperty("nom", cat.libelle);
            cats.add(co);
        }
        o.add("categories", cats);
        JsonArray arts = new JsonArray();
        for (Offre of : offres(p, c, b, comptoir)) arts.add(of.json());
        o.add("articles", arts);
        if (toast != null) o.addProperty("toast", toast);
        return o;
    }

    /** « Konoha · Au comptoir : Hōsen » (membre de l'entreprise de la boutique à 6 blocs) ou « Konoha · libre-service ». */
    private String sousTitre(Boutique b, Player client) {
        String v = b.village == null || b.village.isBlank() ? "" : Character.toUpperCase(b.village.charAt(0)) + b.village.substring(1);
        String qui = null;
        var es = plugin.nourriture() == null ? null : plugin.nourriture().entreprises();
        Entreprise e = es == null || b.entreprise.isBlank() ? null : es.trouver(b.entreprise);
        if (e != null) {
            for (Boutique.Comptoir co : b.comptoirs) {
                Location cc = co.centre();
                if (cc == null) continue;
                for (Player o : cc.getWorld().getPlayers()) {
                    if (o.equals(client) || o.getLocation().distanceSquared(cc) > 36) continue;
                    ShinobiCharacter oc = plugin.characters().getActive(o.getUniqueId());
                    if (oc != null && e.membre(oc.id()) != null) { qui = oc.name(); break; }
                }
                if (qui != null) break;
            }
        }
        String fin = qui != null ? "Au comptoir : " + qui : "Libre-service";
        return v.isEmpty() ? fin : v + " · " + fin;
    }

    /** Un article proposé à ce client à ce comptoir (vivres et cartes du bazar compris). */
    record Offre(String id, String nom, Catalogue.Categorie cat, String genre, long prix, boolean possede, String verrou,
                 String raison, JsonObject item, Catalogue.Article article) {
        JsonObject json() {
            JsonObject o = new JsonObject();
            o.addProperty("id", id);
            o.addProperty("nom", nom);
            o.addProperty("cat", cat.id);
            o.addProperty("genre", genre);
            o.addProperty("prix", prix);
            if (possede) o.addProperty("possede", true);
            if (verrou != null) { o.addProperty("verrou", verrou); o.addProperty("raison", raison); }
            if (item != null) o.add("item", item);
            return o;
        }
    }

    List<Offre> offres(Player p, ShinobiCharacter c, Boutique b, Boutique.Comptoir comptoir) {
        List<Offre> l = new ArrayList<>();
        for (Catalogue.Categorie cat : comptoir.categories) {
            if (cat == Catalogue.Categorie.VIVRES) {
                var n = plugin.nourriture();
                if (n == null) continue;
                try {
                    for (JsonElement e : JsonParser.parseString(n.vivresJson(p, c)).getAsJsonArray()) {
                        JsonObject v = e.getAsJsonObject();
                        String id = "vivre:" + v.get("id").getAsString();
                        if (!comptoir.articles.isEmpty() && !comptoir.articles.contains(id)) continue;
                        l.add(new Offre(id, v.get("name").getAsString(), cat, "vivre", v.get("price").getAsLong(), false,
                                null, null, null, null));
                    }
                } catch (RuntimeException ignored) {
                    // vivres illisibles
                }
            } else if (cat == Catalogue.Categorie.CARTES) {
                var places = plugin.places();
                if (places == null) continue;
                for (var m : places.maps()) {
                    if (m.price() <= 0) continue;
                    String id = "carte:" + m.id();
                    if (!comptoir.articles.isEmpty() && !comptoir.articles.contains(id)) continue;
                    l.add(new Offre(id, "Carte — " + m.title(), cat, "carte", m.price(), false, null, null, null, null));
                }
            } else {
                for (Catalogue.Article a : catalogue.de(cat)) {
                    if (b.exclus.contains(a.id())) continue;
                    if (!comptoir.articles.isEmpty() && !comptoir.articles.contains(a.id())) continue;
                    Conditions.Verdict v = Conditions.evaluer(plugin, p, c, a.conditions());
                    if (v.cache()) continue;
                    l.add(new Offre(a.id(), a.nom(), cat, a.tenue() ? "tenue" : "objet", b.prixDe(a),
                            a.tenue() && c.ownsOutfit(a.id()), v.verrou(), v.raison(), a.tenue() ? null : objetJson(a), a));
                }
            }
        }
        return l;
    }

    /** Apparence de l'objet pour le mod ({@code mat}, {@code model}…), calculée une fois. */
    private JsonObject objetJson(Catalogue.Article a) {
        JsonObject o = objets.get(a.id());
        if (o != null) return o;
        ItemStack s = fabriquer(a);
        InventoryManager im = plugin.rpInventory();
        if (s == null || im == null) return null;
        try {
            o = JsonParser.parseString(im.jsonObjet(s)).getAsJsonObject();
            if (a.emplacement() != null) o.addProperty("slot", a.emplacement().toUpperCase(java.util.Locale.ROOT));
            objets.put(a.id(), o);
        } catch (RuntimeException ignored) {
            return null;
        }
        return o;
    }

    /** L'objet Nexo d'un article (API de Nexo par réflexion), ou null. */
    static ItemStack fabriquer(Catalogue.Article a) {
        if (a.nexo() == null) return null;
        try {
            Class<?> k = Class.forName("com.nexomc.nexo.api.NexoItems");
            Object builder = k.getMethod("itemFromId", String.class).invoke(null, a.nexo());
            if (builder == null) return null;
            Object item = builder.getClass().getMethod("build").invoke(builder);
            return item instanceof ItemStack s ? s : null;
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    /* ================================================================ réception */

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player p, byte @NotNull [] message) {
        if (!CANAL.equals(channel)) return;
        String msg = new String(message, StandardCharsets.UTF_8);
        Bukkit.getScheduler().runTask(plugin, () -> recevoir(p, msg));
    }

    private void recevoir(Player p, String msg) {
        int nl = msg.indexOf('\n');
        String tete = (nl < 0 ? msg : msg.substring(0, nl)).trim();
        String blob = nl < 0 ? "" : msg.substring(nl + 1);
        if (tete.equals("fermer")) { sessions.remove(p.getUniqueId()); return; }
        Session s = sessions.get(p.getUniqueId());
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        Boutique b = s == null ? null : store.trouver(s.boutique());
        if (s == null || c == null || b == null || s.comptoir() >= b.comptoirs.size()) {
            envoyer(p, ferme());
            return;
        }
        Boutique.Comptoir comptoir = b.comptoirs.get(s.comptoir());
        String toast = null;
        if (tete.equals("etat")) {
            toast = null;
        } else if (tete.startsWith("acheter_porter:")) {
            toast = acheter(p, c, b, comptoir, tete.substring("acheter_porter:".length()), true, blob);
        } else if (tete.startsWith("acheter:")) {
            toast = acheter(p, c, b, comptoir, tete.substring("acheter:".length()), false, null);
        } else if (tete.startsWith("porter:")) {
            toast = porter(p, c, tete.substring("porter:".length()), blob);
        } else if (tete.equals("ranger")) {
            toast = porter(p, c, "", blob);
        } else {
            return;
        }
        envoyer(p, etat(p, c, b, comptoir, "etat", toast));
    }

    private String acheter(Player p, ShinobiCharacter c, Boutique b, Boutique.Comptoir comptoir, String id, boolean porter,
                           String blob) {
        Offre of = null;
        for (Offre o : offres(p, c, b, comptoir)) if (o.id().equals(id.trim())) { of = o; break; }
        if (of == null) return "§cCet article n'est pas vendu à ce comptoir.";
        if (of.verrou() != null) return "§c" + of.verrou() + " — " + of.raison().toLowerCase(java.util.Locale.ROOT) + ".";
        String v = verrou(p);
        if (v != null) return "§cImpossible : " + v + ".";
        String motif = b.nom + " · " + of.nom() + (b.entreprise.isBlank() ? "" : " · entreprise : " + b.entreprise);
        switch (of.genre()) {
            case "vivre" -> {
                var n = plugin.nourriture();
                return n == null ? "§cPas de vivres ici." : n.acheterVivre(p, c, id.substring("vivre:".length()));
            }
            case "carte" -> {
                var places = plugin.places();
                var def = places == null ? null : places.map(id.substring("carte:".length()));
                if (def == null) return "§cCette carte n'est pas en vente.";
                String refus = payer(p, c, of.prix(), motif);
                if (refus != null) return "§c" + refus;
                com.reborn.shinobicore.map.MapItem.give(plugin, p, def);
                return "§a" + of.nom() + " achetée.";
            }
            case "tenue" -> {
                if (c.ownsOutfit(id)) return "§eTu possèdes déjà cette tenue.";
                String refus = payer(p, c, of.prix(), motif);
                if (refus != null) return "§c" + refus;
                c.addOwnedOutfit(id);
                if (porter && blob != null && !blob.isBlank()) appliquer(p, c, blob);
                plugin.characters().save(c);
                Notifs.msg(p, "info", null, "−" + Bourse.fmt(of.prix()) + " ryo", of.nom());
                p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, SoundCategory.PLAYERS, 0.6f, 1.1f);
                return "§a" + of.nom() + (porter ? " achetée et portée." : " achetée : dans ta garde-robe.");
            }
            default -> {
                ItemStack it = fabriquer(of.article());
                if (it == null) return "§cCet objet n'existe pas (encore) dans le pack.";
                InventoryManager im = plugin.rpInventory();
                String slot = of.article().emplacement();
                boolean poser = porter && im != null && slot != null && im.cosmetique(p, slot) == null;
                if (!poser && im != null) {
                    String r = im.refusReception(p, it);
                    if (r != null) return "§cLibère une case (" + r + ").";
                }
                String refus = payer(p, c, of.prix(), motif);
                if (refus != null) return "§c" + refus;
                if (poser) im.poserCosmetique(p, slot, it);
                else if (im != null) im.recevoir(p, it);
                else p.getInventory().addItem(it);
                Notifs.objet(p, true, it, 1, "−" + Bourse.fmt(of.prix()) + " ryo");
                p.playSound(p.getLocation(), Sound.ITEM_ARMOR_EQUIP_LEATHER, SoundCategory.PLAYERS, 0.6f, 1.1f);
                return "§a" + of.nom() + (poser ? " achetée et portée." : " achetée : dans ta sacoche.");
            }
        }
    }

    /** Porter une tenue de sa garde-robe ({@code id} vide = la ranger) : l'apparence complète vient du mod. */
    private String porter(Player p, ShinobiCharacter c, String id, String blob) {
        id = id.trim();
        if (!id.isEmpty() && !c.ownsOutfit(id)) return "§cTu ne possèdes pas cette tenue.";
        if (blob == null || blob.isBlank()) return "§cApparence illisible.";
        appliquer(p, c, blob);
        plugin.characters().save(c);
        Journal.entree(Categorie.OBJET, "TENUE").acteur(p).ciblePersonnage(p.getUniqueId(), c.id())
                .detail(id.isEmpty() ? "tenue rangée (boutique)" : "porte « " + id + " » (boutique)").ecrire();
        return id.isEmpty() ? "§aTenue rangée." : "§aTenue portée.";
    }

    private void appliquer(Player p, ShinobiCharacter c, String blob) {
        c.setAppearance(blob);
        if (plugin.characterSelect() != null) plugin.characterSelect().broadcastActive(p);
    }

    /** Paie avec la bourse (journal ECONOMIE ACHAT, ryo détruits) ; null si payé, sinon la raison. */
    private String payer(Player p, ShinobiCharacter c, long prix, String motif) {
        if (prix <= 0) return null;
        var m = plugin.monnaie();
        if (m != null) {
            long sur = m.bourses().montant(p);
            if (sur < prix) return "Il te manque " + Bourse.fmt(prix - sur) + " ryo — passe au guichet.";
            return m.bourses().debiter(p, prix, "ACHAT", motif, p) ? null : "Paiement impossible.";
        }
        com.reborn.shinobicore.journal.RyoJournal.contexte(motif, p);
        boolean paye = c.trySpendRyo(prix);
        com.reborn.shinobicore.journal.RyoJournal.fin();
        return paye ? null : "Pas assez de ryo (" + prix + ").";
    }

    private long portee(Player p, ShinobiCharacter c) {
        var m = plugin.monnaie();
        return m != null ? m.bourses().montant(p) : c.ryo();
    }

    /* ================================================================ fermeture */

    private static JsonObject ferme() {
        JsonObject o = new JsonObject();
        o.addProperty("t", "ferme");
        return o;
    }

    public void fermer(Player p, String pourquoi) {
        if (sessions.remove(p.getUniqueId()) == null) return;
        envoyer(p, ferme());
        if (pourquoi != null) Notifs.msg(p, "info", null, pourquoi, null);
    }

    /** Ferme les écrans ouverts sur cette boutique (supprimée, fermée). */
    void fermerTous(Boutique b, String pourquoi) {
        for (Map.Entry<UUID, Session> e : new ArrayList<>(sessions.entrySet())) {
            if (!e.getValue().boutique().equals(b.id)) continue;
            Player p = Bukkit.getPlayer(e.getKey());
            if (p != null) fermer(p, pourquoi);
            else sessions.remove(e.getKey());
        }
    }

    private void tick() {
        for (Map.Entry<UUID, Session> e : new ArrayList<>(sessions.entrySet())) {
            Player p = Bukkit.getPlayer(e.getKey());
            if (p == null) { sessions.remove(e.getKey()); continue; }
            Location a = e.getValue().ancre();
            if (a == null) continue;
            if (!a.getWorld().equals(p.getWorld()) || a.distance(p.getLocation()) > PORTEE) fermer(p, null);
        }
    }

    void envoyer(Player p, JsonObject o) {
        if (p == null || !p.isOnline()) return;
        try {
            p.sendPluginMessage(plugin, CANAL, o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // client sans le canal
        }
    }

    /* ================================================================ événements */

    @EventHandler(priority = EventPriority.HIGH)
    public void onBloc(PlayerInteractEvent ev) {
        if (ev.getAction() != Action.RIGHT_CLICK_BLOCK || ev.getHand() != EquipmentSlot.HAND || ev.getClickedBlock() == null) return;
        var t = store.comptoirA(ev.getClickedBlock().getLocation(), 0);
        if (t == null) return;
        ev.setCancelled(true);
        ouvrir(ev.getPlayer(), t.getKey(), t.getValue(), false);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onEntite(PlayerInteractEntityEvent ev) {
        if (ev.getHand() != EquipmentSlot.HAND || ev.getRightClicked() instanceof Player) return;
        var t = store.comptoirDe(ev.getRightClicked());
        if (t == null) return;
        ev.setCancelled(true);
        ouvrir(ev.getPlayer(), t.getKey(), t.getValue(), false);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCoup(EntityDamageByEntityEvent ev) {
        long now = System.currentTimeMillis();
        Entity d = ev.getDamager();
        if (d instanceof Projectile pr && pr.getShooter() instanceof Entity sh) d = sh;
        if (d instanceof Player a) dernierCoup.put(a.getUniqueId(), now);
        if (ev.getEntity() instanceof Player v) {
            dernierCoup.put(v.getUniqueId(), now);
            if (sessions.containsKey(v.getUniqueId())) fermer(v, "Un coup t'a sorti de la boutique");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent ev) {
        sessions.remove(ev.getPlayer().getUniqueId());
        dernierCoup.remove(ev.getPlayer().getUniqueId());
    }

    /** Pour /boutique info : combien d'écrans ouverts sur cette boutique. */
    int ouverts(Boutique b) {
        int n = 0;
        for (Session s : sessions.values()) if (s.boutique().equals(b.id)) n++;
        return n;
    }

    /** Résumé d'un client pour le journal (« #00042 Kenta Arai »). */
    static String qui(ShinobiCharacter c) { return PlayerIds.prefix(c) + c.name(); }
}
