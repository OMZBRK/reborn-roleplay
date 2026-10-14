package com.reborn.shinobicore.banque;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.PlayerIdRegistry;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.inventory.InventoryManager;
import com.reborn.shinobicore.inventory.Notifs;
import com.reborn.shinobicore.monnaie.Bourse;
import com.reborn.shinobicore.monnaie.BourseService;
import com.reborn.shinobicore.monnaie.MonnaieCanal;
import com.reborn.shinobicore.monnaie.MonnaieConfig;
import com.reborn.shinobicore.monnaie.MonnaieModule;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * L'écran du guichet côté serveur, canal {@code reborn:banque} (MONNAIE_ET_BANQUE.md §4, maquettes 03 et 04).
 *
 * <p>S2C (JSON) :
 * <ul>
 *   <li>{@code {"t":"guichet","ouvrir":bool,"onglet":0..4,"banque","titulaire","numero","guichetier"?,"allie",
 *       "fraisAllie","compte","bourse","plafond","palier","paliers":[{n,nom,cout,cases,plafond}],"coffre":[item|null],
 *       "sacoche":[{"ref","item","refus"?}],"historique":[{"le","d","t"}],"solde"?:{"montant","semaine"},
 *       "contacts":[{"ref","nom"}],"virement":{"pourcent","min","autre","plafondJour","deja"},
 *       "proches":[{"uuid","nom"}],"echange"?:{…},"gele"?,"toast"?,"refus"?:{"ref","texte"}}} ;</li>
 *   <li>{@code {"t":"ferme"}} (guichet trop loin) ; {@code {"t":"guichetier","actif":bool}} (menu R : « Ouvrir son
 *       compte ») ; {@code {"t":"sceau","a","b","detail"}} (guichetier : un échange attend).</li>
 * </ul>
 * C2S (texte) : {@code ouvrir}, {@code fermer}, {@code onglet:<n>}, {@code deposer:<n>}, {@code retirer:<n>},
 * {@code virer:<#N°|c:uuid>\n<n>\n<motif>}, {@code palier:<k>}, {@code coffre:depot:<H0|B0>},
 * {@code coffre:retrait:<i>}, {@code echange:ouvrir:<uuid>}, {@code echange:ryo:<n>:<bourse|compte>},
 * {@code echange:objet:<ref>}, {@code echange:retirer:<i>}, {@code echange:recevoir:<bourse|compte>},
 * {@code echange:confirmer}, {@code echange:annuler}, {@code echange:sceller}, {@code echange:refuser}.
 */
public final class GuichetCanal implements Listener {

    private record Session(String guichet, int onglet) {}

    private final ShinobiCore plugin;
    private final MonnaieModule module;
    private final Map<UUID, Session> sessions = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> guichetierEnvoye = new ConcurrentHashMap<>();

    public GuichetCanal(ShinobiCore plugin, MonnaieModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    public void start() { Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 40L); }

    private BanqueService banque() { return module.banque(); }

    /** Clic droit sur un guichet (ou /banque client par le guichetier) : ouvre l'écran. */
    public void ouvrir(Player p, BanqueStore.Guichet g, int onglet) {
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        if (c == null) return;
        String v = module.bourses().verrou(p);
        if (v != null) { Notifs.msg(p, "refus", null, "Impossible", v); return; }
        Compte a = banque().compte(c);
        BanqueService.Acces ac = banque().acces(a, g.village());
        if (ac.refus() != null && !a.gele()) {
            Notifs.msg(p, "refus", null, ac.refus(), null);
            return;
        }
        if (!module.canal().modde(p)) {
            p.sendMessage("§6[Banque] §fCompte " + Bourse.fmt(a.solde) + " ryo · bourse " + Bourse.fmt(module.bourses().montant(p))
                    + " ryo. L'écran du guichet demande le mod Reborn à jour.");
            return;
        }
        sessions.put(p.getUniqueId(), new Session(g.id(), onglet));
        envoyer(p, g, onglet, true, null, null);
        p.playSound(p.getLocation(), Sound.BLOCK_CHAIN_PLACE, SoundCategory.PLAYERS, 0.4f, 1.6f);
    }

    /** Ouvre l'écran du joueur au guichet le plus proche, sur un onglet (échange proposé). */
    public void ouvrirSurOnglet(Player p, int onglet) {
        BanqueStore.Guichet g = module.guichets().proche(p.getLocation(), MonnaieConfig.get().echangeDistance + 2);
        if (g != null) ouvrir(p, g, onglet);
    }

    /** Rafraîchit l'écran ouvert (sans le rouvrir s'il est fermé). */
    public void envoyerEtat(Player p, String toast) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null) return;
        BanqueStore.Guichet g = banque().store.guichets.get(s.guichet());
        if (g == null) return;
        envoyer(p, g, -1, false, toast, null);
    }

    private BanqueStore.Guichet guichet(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null) return null;
        BanqueStore.Guichet g = banque().store.guichets.get(s.guichet());
        if (g == null) return null;
        var c = Guichets.centre(g);
        if (c == null || !c.getWorld().equals(p.getWorld()) || c.distance(p.getLocation()) > MonnaieConfig.get().porteeGuichet + 1.5) {
            sessions.remove(p.getUniqueId());
            module.canal().envoyerBanque(p, new MonnaieCanal.J().s("t", "ferme"));
            return null;
        }
        return g;
    }

    /* ================================================================ réception */

    public void recevoir(Player p, String msg) {
        if (msg.equals("fermer")) { sessions.remove(p.getUniqueId()); return; }
        BanqueStore.Guichet g = guichet(p);
        if (g == null) return;
        Guichets.Ouverture o = module.guichets().ouverture(g, p);
        String res = null;
        String refusRef = null;
        String[] a = msg.split(":", 4);
        switch (a[0]) {
            case "ouvrir" -> { }
            case "onglet" -> {
                int n = (int) Math.max(0, MonnaieCanal.lire(a.length > 1 ? a[1] : "0"));
                sessions.put(p.getUniqueId(), new Session(g.id(), n));
            }
            case "deposer" -> res = banque().deposer(p, MonnaieCanal.lire(a.length > 1 ? a[1] : ""), o);
            case "retirer" -> res = banque().retirer(p, MonnaieCanal.lire(a.length > 1 ? a[1] : ""), o);
            case "virer" -> {
                String[] l = msg.substring("virer:".length()).split("\n", 3);
                res = l.length < 3 ? "Formulaire incomplet." : banque().virer(p, l[0], MonnaieCanal.lire(l[1]), l[2].trim(), o);
                if (res == null) res = "+Virement envoyé.";
            }
            case "palier" -> res = banque().passerPalier(p, (int) MonnaieCanal.lire(a.length > 1 ? a[1] : ""), o);
            case "coffre" -> {
                if (a.length >= 3 && a[1].equals("depot")) {
                    res = banque().coffreDeposer(p, a[2], o);
                    if (res != null && res.startsWith("!")) { refusRef = a[2]; res = res.substring(1); }
                } else if (a.length >= 3 && a[1].equals("retrait")) res = banque().coffreRetirer(p, (int) MonnaieCanal.lire(a[2]), o);
            }
            case "echange" -> res = echange(p, a, o);
            default -> { }
        }
        if (res != null && !res.startsWith("+")) p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, SoundCategory.PLAYERS, 0.5f, 0.6f);
        envoyer(p, g, -1, false, res == null ? null : res.startsWith("+") ? res.substring(1) : res, refusRef);
    }

    private String echange(Player p, String[] a, Guichets.Ouverture o) {
        EchangeGaranti ex = module.echanges();
        if (a.length < 2) return null;
        return switch (a[1]) {
            case "ouvrir" -> {
                try { yield ex.ouvrir(p, UUID.fromString(a.length > 2 ? a[2] : ""), o); }
                catch (IllegalArgumentException e) { yield "Personne ici."; }
            }
            case "ryo" -> ex.deposerRyo(p, MonnaieCanal.lire(a.length > 2 ? a[2] : "0"), a.length > 3 ? a[3] : "bourse");
            case "objet" -> ex.deposerObjet(p, a.length > 2 ? a[2] : "");
            case "retirer" -> ex.retirerObjet(p, (int) MonnaieCanal.lire(a.length > 2 ? a[2] : "-1"));
            case "recevoir" -> { ex.recevoir(p, a.length > 2 ? a[2] : "compte"); yield null; }
            case "confirmer" -> ex.confirmer(p);
            case "annuler" -> { ex.annuler(p); yield null; }
            case "sceller" -> ex.sceller(p, true);
            case "refuser" -> ex.sceller(p, false);
            default -> null;
        };
    }

    /* ================================================================ état */

    private void envoyer(Player p, BanqueStore.Guichet g, int onglet, boolean ouvrir, String toast, String refusRef) {
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        if (c == null) return;
        Compte a = banque().compte(c);
        Guichets.Ouverture o = module.guichets().ouverture(g, p);
        MonnaieConfig k = MonnaieConfig.get();
        MonnaieConfig.Palier pa = k.palier(a.palier);
        Session s = sessions.get(p.getUniqueId());
        MonnaieCanal.J j = new MonnaieCanal.J().s("t", "guichet").b("ouvrir", ouvrir)
                .n("onglet", onglet >= 0 ? onglet : s != null ? s.onglet() : 0)
                .s("banque", o.banque()).s("titulaire", c.name())
                .s("numero", c.hasPlayerId() ? PlayerIdRegistry.format(c.playerId()) : "")
                .b("allie", banque().acces(a, g.village()).allie()).n("fraisAllie", k.fraisAlliePourcent)
                .n("compte", a.solde).n("bourse", module.bourses().montant(p)).n("plafond", pa.plafond())
                .n("palier", a.palier);
        if (o.guichetier() != null) j.s("guichetier", o.guichetier());
        if (a.gele()) j.s("gele", a.gelMotif);
        JsonArray ps = new JsonArray();
        for (MonnaieConfig.Palier x : k.paliers) {
            JsonObject q = new JsonObject();
            q.addProperty("n", x.numero());
            q.addProperty("nom", x.nom());
            q.addProperty("cout", x.cout());
            q.addProperty("cases", x.cases());
            q.addProperty("plafond", x.plafond());
            ps.add(q);
        }
        j.a("paliers", ps);
        InventoryManager im = plugin.rpInventory();
        JsonArray co = new JsonArray();
        for (ItemStack it : a.coffre) co.add(JsonParser.parseString(im == null ? "null" : im.jsonObjet(it)));
        j.a("coffre", co);
        JsonArray sac = new JsonArray();
        BourseService.Emplacements e = module.bourses().sur(p);
        for (String r : e.refs()) {
            ItemStack it = e.lire(r);
            if (it == null || it.getType().isAir()) continue;
            JsonObject q = new JsonObject();
            q.addProperty("ref", r);
            q.add("item", JsonParser.parseString(im == null ? "null" : im.jsonObjet(it)));
            if (!Bourse.est(it)) {
                String rf = banque().refus(it);
                if (rf != null) q.addProperty("refus", rf);
            }
            sac.add(q);
        }
        j.a("sacoche", sac);
        JsonArray h = new JsonArray();
        for (Compte.Ligne l : a.historique()) {
            JsonObject q = new JsonObject();
            q.addProperty("le", l.le());
            q.addProperty("d", l.delta());
            q.addProperty("t", l.texte());
            h.add(q);
        }
        j.a("historique", h);
        if (a.soldeMontant > 0 && !a.soldeSemaine.isEmpty())
            j.e("solde", obj("montant", a.soldeMontant, "semaine", a.soldeSemaine));
        JsonArray ct = new JsonArray();
        c.contacts().forEach((id, kn) -> {
            Compte b = banque().compte(id);
            JsonObject q = new JsonObject();
            q.addProperty("ref", "c:" + id);
            q.addProperty("nom", kn.display() + (b != null && b.numero >= 0 ? " · N° " + PlayerIdRegistry.format(b.numero) : ""));
            ct.add(q);
        });
        j.a("contacts", ct);
        String jour = java.time.LocalDate.now(java.time.ZoneId.of(k.fuseau)).toString();
        JsonObject vir = new JsonObject();
        vir.addProperty("pourcent", k.virementPourcent);
        vir.addProperty("min", k.virementMin);
        vir.addProperty("autre", k.virementAutreVillagePourcent);
        vir.addProperty("plafondJour", k.virementPlafondJour);
        vir.addProperty("deja", jour.equals(a.virementJour) ? a.virementTotal : 0);
        j.e("virement", vir);
        JsonArray pr = new JsonArray();
        var centre = Guichets.centre(g);
        if (centre != null) {
            for (Player q : centre.getWorld().getPlayers()) {
                if (q.equals(p) || q.getLocation().distance(centre) > k.echangeDistance + 1) continue;
                ShinobiCharacter qc = plugin.characters().getActive(q.getUniqueId());
                if (qc == null || com.reborn.shinobicore.staff.outils.ModeStaff.horsJeu(q)) continue;
                var kn = c.knownNameOf(qc.id());
                JsonObject x = new JsonObject();
                x.addProperty("uuid", q.getUniqueId().toString());
                x.addProperty("nom", kn != null ? kn.display() : "Inconnu (" + q.getName() + ")");
                pr.add(x);
            }
        }
        j.a("proches", pr);
        JsonObject ex = module.echanges().etat(p);
        if (ex != null) j.e("echange", ex);
        if (toast != null) j.s("toast", toast);
        if (refusRef != null) j.e("refus", obj("ref", refusRef, "texte", toast == null ? "" : toast));
        module.canal().envoyerBanque(p, j);
    }

    private static JsonObject obj(Object... kv) {
        JsonObject o = new JsonObject();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            Object v = kv[i + 1];
            if (v instanceof Number n) o.addProperty(String.valueOf(kv[i]), n);
            else o.addProperty(String.valueOf(kv[i]), String.valueOf(v));
        }
        return o;
    }

    /** Au guichetier : un échange attend son sceau. */
    void envoyerSceau(Player gu, EchangeGaranti.Echange e) {
        module.canal().envoyerBanque(gu, new MonnaieCanal.J().s("t", "sceau").s("a", e.a.nom).s("b", e.b.nom)
                .n("id", e.id));
    }

    /* ================================================================ guichetier (menu R), écrans éloignés */

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (sessions.containsKey(p.getUniqueId())) guichet(p);
            boolean actif = module.guichets().enService(p) != null;
            Boolean d = guichetierEnvoye.put(p.getUniqueId(), actif);
            if (d == null || d != actif) module.canal().envoyerBanque(p, new MonnaieCanal.J().s("t", "guichetier").b("actif", actif));
        }
    }

    /** {@code /banque client <joueur>} : le guichetier en service ouvre le compte d'un client à son guichet. */
    public String ouvrirPourClient(Player gu, Player client) {
        BanqueStore.Guichet g = module.guichets().enService(gu);
        if (g == null) return "Tu n'es pas guichetier en service ici.";
        var c = Guichets.centre(g);
        if (c == null || !client.getWorld().equals(c.getWorld()) || client.getLocation().distance(c) > MonnaieConfig.get().porteeGuichetier)
            return "Ton client doit être au guichet.";
        ouvrir(client, g, 0);
        return null;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent ev) {
        sessions.remove(ev.getPlayer().getUniqueId());
        guichetierEnvoye.remove(ev.getPlayer().getUniqueId());
    }
}
