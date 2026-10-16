package com.reborn.shinobicore.faction;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.faction.FactionData.Faction;
import com.reborn.shinobicore.faction.FactionData.Membre;
import com.reborn.shinobicore.faction.FactionData.Section;
import com.reborn.shinobicore.staff.panel.StaffGrades;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Canal {@value #CHANNEL} (les deux sens, JSON UTF-8 brut sans préfixe de longueur), miroir de {@code FactionPayload}
 * côté mod. Le client demande ({@code {"a": …}}), le serveur répond ({@code {"t": …}}) ; protocole complet :
 * docs/technique/PLUGINS_REFERENCE.md §4 septies. Rafraîchi à l'ouverture et sur événement (aucun sondage) : chaque
 * changement d'une faction renvoie la vue aux membres qui ont le volet ouvert, et l'état court (« peut inviter ») à
 * ceux en ligne pour le menu R.
 */
public final class FactionCanal implements PluginMessageListener, Listener {

    public static final String CHANNEL = "reborn:faction";

    private final ShinobiCore plugin;
    private final FactionService svc;
    private final FactionVue vue;
    private final Gson gson = new Gson();
    private final Set<UUID> ouverts = ConcurrentHashMap.newKeySet();
    private final Set<UUID> staffOuverts = ConcurrentHashMap.newKeySet();

    FactionCanal(ShinobiCore plugin, FactionService svc) {
        this.plugin = plugin;
        this.svc = svc;
        this.vue = new FactionVue(plugin, svc);
    }

    void start() {
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CHANNEL)) m.registerOutgoingPluginChannel(plugin, CHANNEL);
        m.registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    public boolean modde(Player p) { return p.getListeningPluginChannels().contains(CHANNEL); }

    void envoyer(Player p, JsonObject o) {
        if (p == null || !p.isOnline() || !modde(p)) return;
        p.sendPluginMessage(plugin, CHANNEL, gson.toJson(o).getBytes(StandardCharsets.UTF_8));
    }

    void toast(Player p, String m, boolean refus) {
        if (!modde(p)) { p.sendMessage((refus ? "§c" : "§6") + "[Faction] §f" + m); return; }
        JsonObject o = new JsonObject();
        o.addProperty("t", "toast");
        o.addProperty("m", m);
        if (refus) o.addProperty("refus", true);
        envoyer(p, o);
    }

    /** Ouvre le volet (touche J, /faction). */
    public void ouvrir(Player p) {
        if (!modde(p)) { p.sendMessage("§7Le volet Faction demande le mod Reborn à jour. En attendant : /faction info."); return; }
        ouverts.add(p.getUniqueId());
        envoyerVue(p, true);
    }

    void envoyerVue(Player p, boolean ouvrir) {
        if (!ouvrir && !ouverts.contains(p.getUniqueId())) { etat(p); return; }
        JsonObject o = vue.joueur(p);
        if (ouvrir) o.addProperty("ouvrir", true);
        envoyer(p, o);
        etat(p);
    }

    /** État court pour le menu R : peut-il inviter, dans quelle section. */
    void etat(Player p) {
        Membre m = svc.membre(p);
        Section s = m == null ? null : svc.section(m.section);
        JsonObject o = new JsonObject();
        o.addProperty("t", "etat");
        if (s != null) {
            o.addProperty("section", s.nom);
            o.addProperty("insigne", s.insigne);
            String r = svc.refusInviter(m);
            o.addProperty("inviter", r == null);
            if (r != null && svc.droit(m, Droits.INVITER)) o.addProperty("refus", r);
            Faction f = svc.faction(s.faction);
            if (f != null && !f.village()) o.addProperty("organisation", f.nom);
        }
        envoyer(p, o);
    }

    /** Une faction a changé : la vue aux volets ouverts de ses membres, l'état court à tous ses membres en ligne. */
    void rafraichir(Faction f) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Membre m = svc.membre(p);
            if (m == null || f == null || !f.tag.equals(m.faction)) continue;
            envoyerVue(p, false);
        }
        rafraichirStaff();
    }

    void rafraichirStaff() {
        for (UUID id : staffOuverts) {
            Player p = Bukkit.getPlayer(id);
            if (p == null || StaffGrades.of(p) < StaffGrades.HELPER) { staffOuverts.remove(id); continue; }
            envoyer(p, vue.staff(StaffGrades.of(p)));
        }
    }

    void invitation(Player cible, FactionService.Invit inv, String de, Section s) {
        if (!modde(cible)) {
            cible.sendMessage("§6[Faction] §f" + de + " t'invite dans la section §e" + s.nom + "§f : §a/faction accepter§f ou §c/faction refuser§f ("
                    + inv.secs() + " s).");
            return;
        }
        JsonObject o = new JsonObject();
        o.addProperty("t", "invit");
        o.addProperty("de", de);
        o.addProperty("section", s.nom);
        o.addProperty("insigne", s.insigne);
        Faction f = svc.faction(s.faction);
        if (f != null) o.addProperty("faction", f.village() ? f.embleme : f.nom);
        o.addProperty("s", inv.secs());
        o.addProperty("panneau", inv.panneau());
        envoyer(cible, o);
    }

    void invitationFin(Player p) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "invit_fin");
        envoyer(p, o);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent ev) {
        Player p = ev.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) etat(p); }, 60L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent ev) {
        ouverts.remove(ev.getPlayer().getUniqueId());
        staffOuverts.remove(ev.getPlayer().getUniqueId());
    }

    /* ================================================================ réception */

    @Override
    public void onPluginMessageReceived(String channel, Player p, byte[] bytes) {
        if (!CHANNEL.equals(channel)) return;
        JsonObject in;
        try { in = JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)).getAsJsonObject(); }
        catch (RuntimeException e) { return; }
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (p.isOnline()) traiter(p, in);
        });
    }

    private static String s(JsonObject o, String k) {
        try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : ""; } catch (RuntimeException e) { return ""; }
    }

    private static int n(JsonObject o, String k) {
        try { return o.has(k) ? o.get(k).getAsInt() : 0; } catch (RuntimeException e) { return 0; }
    }

    private static boolean b(JsonObject o, String k) {
        try { return o.has(k) && o.get(k).getAsBoolean(); } catch (RuntimeException e) { return false; }
    }

    private static UUID u(JsonObject o, String k) {
        try { return UUID.fromString(s(o, k)); } catch (IllegalArgumentException e) { return null; }
    }

    private static Set<String> liste(JsonObject o, String k) {
        Set<String> out = new LinkedHashSet<>();
        if (o.has(k) && o.get(k).isJsonArray()) for (JsonElement e : o.getAsJsonArray(k)) out.add(e.getAsString());
        return out;
    }

    private void traiter(Player p, JsonObject in) {
        String a = s(in, "a");
        if (a.startsWith("staff")) { staff(p, a, in); return; }
        Membre m = svc.membre(p);
        Faction f = m == null ? null : svc.faction(m.faction);
        Section ms = m == null ? null : svc.section(m.section);
        String r;
        String ok = null;
        switch (a) {
            case "open" -> { ouvrir(p); return; }
            case "close" -> { ouverts.remove(p.getUniqueId()); return; }
            case "refresh" -> { envoyerVue(p, false); return; }
            case "etat" -> { etat(p); return; }
            case "proches" -> { envoyer(p, vue.proches(p)); return; }
            case "inviter" -> {
                Membre c = svc.membre(u(in, "p"));
                Player q = svc.enLigne(c);
                r = q == null ? "Cette personne n'est pas en ligne." : svc.inviter(p, q, true);
                if (r == null) ok = "Invitation envoyée à " + c.nom + " · " + FactionService.SECS_PANNEAU + " s";
            }
            case "repondre" -> r = svc.repondre(p, b(in, "oui"));
            case "quitter" -> { r = svc.quitter(p); if (r == null) ok = "Tu as quitté ta section."; }
            case "places" -> r = svc.places(p, m, ms, n(in, "n"), false);
            case "tenue" -> r = svc.tenue(p, m, ms, s(in, "id"), s(in, "nom"), in.has("acc") ? s(in, "acc") : null, false);
            case "grade_creer" -> r = svc.gradeCreer(p, m, sectionGeree(m, in), s(in, "nom"), false);
            case "grade_renommer" -> r = svc.gradeRenommer(p, m, sectionGeree(m, in), s(in, "id"), s(in, "nom"), false);
            case "grade_suppr" -> r = svc.gradeSupprimer(p, m, sectionGeree(m, in), s(in, "id"), false);
            case "grade_ordre" -> r = svc.gradeOrdre(p, m, sectionGeree(m, in), s(in, "id"), n(in, "sens"), false);
            case "grade_droits" -> {
                Section sg = sectionGeree(m, in);
                // on ne donne que des droits qu'on a (le chef de section les a tous)
                Set<String> d = liste(in, "droits");
                if (m != null && sg != null && !m.perso.equals(sg.chef) && f != null && !m.perso.equals(f.chef)) d.removeIf(x -> !svc.droit(m, x));
                r = svc.gradeDroits(p, m, sg, s(in, "id"), d, false);
                if (r == null) ok = "Droits enregistrés.";
            }
            case "monter" -> r = svc.deplacer(p, m, svc.membre(u(in, "p")), -1, false);
            case "descendre" -> r = svc.deplacer(p, m, svc.membre(u(in, "p")), 1, false);
            case "exclure" -> r = svc.exclure(p, m, svc.membre(u(in, "p")), s(in, "motif"), false);
            case "section_creer" -> {
                r = svc.creerSection(p, m, f, s(in, "modele"), s(in, "nom"), s(in, "insigne"), u(in, "chef"), n(in, "places"), b(in, "cachee"), false);
                if (r == null) ok = "Section créée.";
            }
            case "section_cacher" -> r = svc.cacher(p, m, svc.section(s(in, "id")), b(in, "v"), false);
            case "section_chef" -> r = svc.nommerChef(p, m, svc.section(s(in, "id")), u(in, "p"), false);
            case "section_renommer" -> r = svc.renommer(p, m, svc.section(s(in, "id")), s(in, "nom"), s(in, "insigne"), false);
            case "section_dissoudre" -> r = svc.dissoudre(p, m, svc.section(s(in, "id")), s(in, "motif"), false);
            case "taux" -> { r = svc.taux(p, m, f, n(in, "n"), false); if (r == null) ok = "Taxe enregistrée."; }
            case "seuil" -> r = svc.seuil(p, m, f, s(in, "id"), Droits.rang(s(in, "rang")), false);
            case "charge_creer" -> r = svc.chargeCreer(p, m, f, s(in, "nom"), false);
            case "charge_droits" -> r = svc.chargeDroits(p, m, f, s(in, "id"), liste(in, "droits"), b(in, "publique"), false);
            case "charge_titulaire" -> r = svc.chargeTitulaire(p, m, f, s(in, "id"), u(in, "p"), b(in, "oui"), false);
            case "charge_suppr" -> r = svc.chargeSupprimer(p, m, f, s(in, "id"), false);
            case "annoncer" -> { r = svc.annoncer(p, b(in, "section"), s(in, "texte")); if (r == null) ok = "Annonce publiée."; }
            default -> { return; }
        }
        if (r != null) toast(p, r, true);
        else {
            if (ok != null) toast(p, ok, false);
            envoyerVue(p, false);
        }
    }

    /** La section visée par une gestion de grades : la sienne, ou une autre de sa faction pour le chef de faction. */
    private Section sectionGeree(Membre m, JsonObject in) {
        String id = s(in, "section");
        if (m == null) return null;
        if (id.isEmpty() || id.equals(m.section)) return svc.section(m.section);
        Section s = svc.section(id);
        Faction f = s == null ? null : svc.faction(s.faction);
        return f != null && m.perso.equals(f.chef) ? s : null;
    }

    /* ================================================================ staff (Poste de garde › Outils › Factions) */

    private void staff(Player p, String a, JsonObject in) {
        int g = StaffGrades.of(p);
        if (g < StaffGrades.HELPER) { toast(p, "Réservé au staff.", true); return; }
        if (a.equals("staff")) { staffOuverts.add(p.getUniqueId()); envoyer(p, vue.staff(g)); return; }
        if (a.equals("staff_close")) { staffOuverts.remove(p.getUniqueId()); return; }
        Faction f = svc.faction(s(in, "tag"));
        String motif = s(in, "motif");
        String r;
        String ok = "C'est fait, inscrit au journal.";
        switch (a) {
            case "staff_faction" -> {
                if (g < StaffGrades.ADMIN) { toast(p, "Créer une faction : Admin et plus.", true); return; }
                Membre ch = svc.resoudre(s(in, "chef"));
                if (!s(in, "chef").isBlank() && ch == null) { toast(p, "Chef introuvable (N°, nom exact).", true); return; }
                r = svc.creerFaction(p, s(in, "tag"), s(in, "nom"), "organisation".equals(s(in, "type")), s(in, "embleme"),
                        ch == null ? null : ch.perso, motif);
            }
            case "staff_chef" -> {
                if (g < StaffGrades.MODO) { toast(p, "Nommer un chef : Modérateur et plus.", true); return; }
                Membre ch = svc.resoudre(s(in, "chef"));
                if (ch == null && !s(in, "chef").isBlank()) { toast(p, "Personnage introuvable (N°, nom exact).", true); return; }
                r = svc.nommerChefFaction(p, f, ch == null ? null : ch.perso, motif);
            }
            case "staff_renommer" -> {
                if (g < StaffGrades.ADMIN) { toast(p, "Renommer une faction : Admin et plus.", true); return; }
                r = svc.renommerFaction(p, f, s(in, "nom"), s(in, "embleme"), motif);
            }
            case "staff_dissoudre_faction" -> {
                if (g < StaffGrades.ADMIN) { toast(p, "Dissoudre : Admin et plus.", true); return; }
                r = svc.dissoudreFaction(p, f, motif);
            }
            case "staff_taux" -> {
                if (g < StaffGrades.ADMIN) { toast(p, "La taxe : Admin et plus.", true); return; }
                r = motif.isBlank() ? "Donne un motif." : svc.taux(p, null, f, n(in, "n"), true);
            }
            case "staff_section" -> {
                if (g < StaffGrades.MODO) { toast(p, "Créer une section : Modérateur et plus.", true); return; }
                if (motif.isBlank()) { toast(p, "Donne un motif.", true); return; }
                Membre ch = svc.resoudre(s(in, "chef"));
                r = ch == null ? "Chef introuvable (N°, nom exact)." : svc.creerSection(p, null, f, s(in, "modele"), s(in, "nom"),
                        s(in, "insigne"), ch.perso, n(in, "places"), b(in, "cachee"), true);
            }
            case "staff_section_maj" -> {
                if (g < StaffGrades.MODO) { toast(p, "Corriger une section : Modérateur et plus.", true); return; }
                if (motif.isBlank()) { toast(p, "Donne un motif.", true); return; }
                Section sec = svc.section(s(in, "id"));
                r = sec == null ? "Section introuvable." : null;
                if (r == null && in.has("nom") && !s(in, "nom").equals(sec.nom)) r = svc.renommer(p, null, sec, s(in, "nom"), s(in, "insigne"), true);
                if (r == null && in.has("cachee")) r = svc.cacher(p, null, sec, b(in, "cachee"), true);
                if (r == null && in.has("places") && n(in, "places") != sec.places) r = svc.places(p, null, sec, n(in, "places"), true);
                if (r == null && !s(in, "chef").isBlank()) {
                    Membre ch = svc.resoudre(s(in, "chef"));
                    r = ch == null ? "Chef introuvable (N°, nom exact)." : svc.nommerChef(p, null, sec, ch.perso, true);
                }
                if (r == null) svc.journal(svc.faction(sec.faction), sec, p, null, "STAFF", "Correction staff de « " + sec.nom + " » · « " + motif + " »");
            }
            case "staff_section_dissoudre" -> {
                if (g < StaffGrades.ADMIN) { toast(p, "Dissoudre une section : Admin et plus.", true); return; }
                r = svc.dissoudre(p, null, svc.section(s(in, "id")), motif, true);
            }
            case "staff_caisse" -> {
                if (g < StaffGrades.ADMIN) { toast(p, "Corriger une caisse : Admin et plus.", true); return; }
                r = svc.caisseStaff(p, svc.section(s(in, "id")), s(in, "op"), n(in, "n"), motif);
            }
            default -> { return; }
        }
        if (r != null) toast(p, r, true);
        else toast(p, ok, false);
        envoyer(p, vue.staff(g));
    }

    /** Pour /faction info (sans le mod). */
    JsonObject vueJoueur(Player p) { return vue.joueur(p); }
}
