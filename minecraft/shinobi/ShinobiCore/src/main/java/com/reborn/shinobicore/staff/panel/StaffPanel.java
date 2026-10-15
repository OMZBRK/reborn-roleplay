package com.reborn.shinobicore.staff.panel;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.ko.KoState;
import com.reborn.shinobicore.ko.ata.AtaManager;
import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.Statistic;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Date;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Poste de garde — panel staff en jeu (maquette validée le 2026-10-06, carte Trello « Poste de garde »).
 *
 * <p>Canal {@value #CHANNEL}, JSON UTF-8 brut dans les deux sens (contrat miroir de {@code StaffPanelPayload}
 * côté mod-hud). Le client demande ({@code open}, {@code refresh}, {@code profile}, {@code act},
 * {@code sanction}, {@code dismiss}, {@code chat}) ; le serveur répond ({@code snap}, {@code profile},
 * {@code toast}, {@code open}). <b>Tout est revérifié ici</b> : grade, cible, palier de sanction.
 *
 * <p>Contenu : alertes (combats hors terrain d'entraînement, appels à l'aide, signalements), joueurs et fiche
 * (personnage, état KO / ATA, casier, comptes liés, infractions avec escalade), chat staff (aussi avec {@code #}
 * en début de message), journal de toutes les actions staff, catalogue des commandes par grade.
 */
public final class StaffPanel implements Listener, PluginMessageListener {

    public static final String CHANNEL = "reborn:staff";

    private record Alert(String id, String kind, UUID target, String motif, List<String> ctx, int score, long at) { }
    private record ChatLine(long at, int grade, String name, String text, boolean sys) { }
    /** Alerte traitée (Poste de garde v3, onglets « Traitées » / « Les miennes ») : qui, quoi, quand. */
    private record Traitee(long at, String kind, UUID target, String par, UUID parId, String quoi) { }
    private final Deque<Traitee> traitees = new ArrayDeque<>();
    private record Said(long at, String text) { }

    private final ShinobiCore plugin;
    private final StaffStore store;
    private final Gson gson = new Gson();
    private final Map<String, Alert> alerts = new LinkedHashMap<>();
    private final Set<String> dismissed = new HashSet<>();
    private final Deque<ChatLine> chat = new ArrayDeque<>();
    private final Map<UUID, Deque<Said>> said = new ConcurrentHashMap<>();
    private final Map<UUID, Deque<Long>> aggressions = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> victims = new ConcurrentHashMap<>();
    private final Map<UUID, Set<UUID>> reporters = new ConcurrentHashMap<>();
    /** Gel persistant (outils staff, gels.yml) ; avant le 14/10 il était tenu en mémoire ici. */
    private boolean frozen(UUID id) {
        var o = plugin.outilsStaff();
        return o != null && o.gels().gele(id);
    }

    public StaffPanel(ShinobiCore plugin) {
        this.plugin = plugin;
        this.store = new StaffStore(plugin);
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CHANNEL)) m.registerOutgoingPluginChannel(plugin, CHANNEL);
        m.registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    public StaffStore store() { return store; }

    private InfirmeriePanel infirmerie;

    /** L'Infirmerie (gestion visuelle du KO) partage ce canal. */
    public InfirmeriePanel infirmerie() {
        if (infirmerie == null) infirmerie = new InfirmeriePanel(plugin, this);
        return infirmerie;
    }

    /* ================================================================ réseau */

    public boolean modded(Player p) {
        return p.getListeningPluginChannels().contains(CHANNEL);
    }

    void send(Player p, JsonObject o) {
        p.sendPluginMessage(plugin, CHANNEL, gson.toJson(o).getBytes(StandardCharsets.UTF_8));
    }

    void toast(Player p, String msg) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "toast");
        o.addProperty("m", msg);
        if (modded(p)) send(p, o); else p.sendMessage("§6[Garde] §f" + msg);
    }

    /** Demande au client d'ouvrir l'écran (commande /garde). */
    public void openFor(Player p) {
        if (StaffGrades.of(p) == StaffGrades.NONE) { p.sendMessage("§cRéservé au staff."); return; }
        if (!modded(p)) { p.sendMessage("§7Le Poste de garde demande le mod Reborn à jour. En attendant : /staff."); return; }
        JsonObject o = new JsonObject();
        o.addProperty("t", "open");
        send(p, o);
        sendSnapshot(p);
    }

    @Override
    public void onPluginMessageReceived(String channel, Player p, byte[] bytes) {
        if (!CHANNEL.equals(channel)) return;
        String raw = new String(bytes, StandardCharsets.UTF_8);
        Bukkit.getScheduler().runTask(plugin, () -> handle(p, raw));
    }

    private void handle(Player p, String raw) {
        int grade = StaffGrades.of(p);
        if (grade == StaffGrades.NONE) return;
        JsonObject in;
        try { in = JsonParser.parseString(raw).getAsJsonObject(); }
        catch (RuntimeException e) { return; }
        String a = str(in, "a");
        switch (a) {
            case "open" -> openFor(p);
            case "refresh" -> sendSnapshot(p);
            case "profile" -> {
                UUID t = cible(in);
                if (t != null) sendProfile(p, t);
            }
            case "act" -> {
                UUID t = cible(in);
                if (t != null) act(p, grade, str(in, "op"), t, in);
            }
            case "journal" -> journalQuery(p, in);
            case "notes" -> sendNotes(p, in);
            case "note_add" -> {
                int pid = pidDe(in);
                if (pid < 0 || plugin.outilsStaff() == null) { toast(p, "Personnage introuvable (ou sans N°)."); return; }
                if (plugin.outilsStaff().notes().ajouter(p, pid, str(in, "etiquette"), str(in, "texte")) == null) toast(p, "Note vide.");
                else toast(p, "Note ajoutée.");
                sendNotes(p, in);
            }
            case "note_del" -> {
                int pid = pidDe(in);
                if (pid < 0 || plugin.outilsStaff() == null) return;
                int r = plugin.outilsStaff().notes().supprimer(p, pid, str(in, "id"));
                toast(p, r > 0 ? "Note supprimée." : r < 0 ? "Seul l'auteur ou un Admin peut supprimer cette note." : "Note introuvable.");
                sendNotes(p, in);
            }
            case "annonce" -> {
                if (grade < com.reborn.shinobicore.staff.outils.OutilsStaff.G_ANNONCE) { toast(p, "Réservé aux modérateurs et plus."); return; }
                String m = str(in, "m").trim();
                if (m.isEmpty() || plugin.outilsStaff() == null) return;
                plugin.outilsStaff().effets().annonceGlobale(p, str(in, "titre"), m, intDe(in, "s", 8));
                toast(p, "Annonce envoyée à tous.");
            }
            case "seisme" -> {
                if (grade < com.reborn.shinobicore.staff.outils.OutilsStaff.G_SEISME) { toast(p, "Réservé aux admins et plus."); return; }
                if (plugin.outilsStaff() == null) return;
                int n = plugin.outilsStaff().effets().seisme(p, p.getLocation(), intDe(in, "r", 0), intDe(in, "f", 5),
                        intDe(in, "s", 4), str(in, "m"));
                toast(p, "Séisme : " + n + " joueur(s).");
            }
            case "modestaff" -> {
                if (plugin.outilsStaff() == null) return;
                boolean on = plugin.outilsStaff().mode().basculer(p);
                toast(p, on ? "Mode staff activé : invisible, aucune mécanique RP." : "Mode staff désactivé.");
                sendSnapshot(p);
            }
            case "modeanim" -> {
                if (plugin.outilsStaff() == null) return;
                if (!com.reborn.shinobicore.staff.outils.ModeAnimation.permis(p)) { toast(p, "Réservé aux animateurs et MJ."); return; }
                boolean on = plugin.outilsStaff().animation().basculer(p);
                toast(p, on ? "Mode animation : mécaniques RP permises, tout est journalisé." : "Mode animation désactivé.");
                sendSnapshot(p);
            }
            case "cherche" -> cherche(p, in);
            case "sanction" -> {
                UUID t = uuid(in, "t");
                if (t != null) sanction(p, grade, t, str(in, "o"), in.has("warnOnly") && in.get("warnOnly").getAsBoolean(),
                        str(in, "note"), in.has("ev") ? in.getAsJsonArray("ev") : new JsonArray());
            }
            case "kill" -> {
                long kid;
                try { kid = Long.parseLong(str(in, "id").replace("kill-", "")); } catch (NumberFormatException e) { return; }
                if (grade < StaffGrades.MODO) { toast(p, "Réservé aux modérateurs et plus."); return; }
                traiter(str(in, "id"), p, "ok".equals(str(in, "v")) ? "a validé la mise à mort" : "a refusé la mise à mort");
                if ("ok".equals(str(in, "v"))) plugin.killRequests().approveWithReason(p, kid, str(in, "note"));
                else plugin.killRequests().refuse(p, kid);
                sendSnapshot(p);
            }
            case "dismiss" -> {
                traiter(str(in, "id"), p, "l'a ignorée");
                dismissed.add(str(in, "id"));
                alerts.remove(str(in, "id"));
                log(p, "alerte", "Alerte ignorée", str(in, "id"));
                sendSnapshot(p);
            }
            case "chat" -> {
                String m = str(in, "m").trim();
                if (!m.isEmpty()) staffChat(p, m);
                sendSnapshot(p);
            }
            default -> infirmerie().handle(p, grade, a, in);
        }
    }

    static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    static UUID uuid(JsonObject o, String k) {
        try { return UUID.fromString(str(o, k)); } catch (IllegalArgumentException e) { return null; }
    }

    static int intDe(JsonObject o, String k, int def) {
        try { JsonElement e = o.get(k); return e == null || e.isJsonNull() ? def : e.getAsInt(); }
        catch (RuntimeException e) { return def; }
    }

    static long longDe(JsonObject o, String k) {
        try { JsonElement e = o.get(k); return e == null || e.isJsonNull() ? 0L : e.getAsLong(); }
        catch (RuntimeException e) { return 0L; }
    }

    /** Cible d'une requête : {@code t} (UUID du compte) ou {@code pid} (N°, avec ou sans #). */
    private UUID cible(JsonObject in) {
        UUID t = uuid(in, "t");
        if (t != null) return t;
        String pid = str(in, "pid").trim();
        if (pid.isEmpty()) return null;
        int n = com.reborn.shinobicore.character.PlayerIdRegistry.parse(pid.startsWith("#") ? pid : "#" + pid);
        if (n < 0 || plugin.playerIds() == null) return null;
        var e = plugin.playerIds().get(n);
        return e == null ? null : e.ownerId();
    }

    /** N° visé : {@code pid}, sinon personnage actif du compte {@code t}. -1 si aucun. */
    private int pidDe(JsonObject in) {
        String pid = str(in, "pid").trim();
        if (!pid.isEmpty()) return com.reborn.shinobicore.character.PlayerIdRegistry.parse(pid.startsWith("#") ? pid : "#" + pid);
        UUID t = uuid(in, "t");
        ShinobiCharacter c = t != null ? plugin.characters().getActive(t) : null;
        return c != null && c.hasPlayerId() ? c.playerId() : -1;
    }

    /* ================================================================ snapshot */

    private void sendSnapshot(Player viewer) {
        int grade = StaffGrades.of(viewer);
        JsonObject o = new JsonObject();
        o.addProperty("t", "snap");
        o.addProperty("grade", grade);
        o.addProperty("gradeName", StaffGrades.name(grade));

        JsonArray al = new JsonArray();
        long now = System.currentTimeMillis();
        alerts.values().removeIf(x -> now - x.at() > 45 * 60_000L);
        List<Alert> sorted = new ArrayList<>(alerts.values());
        sorted.sort((x, y) -> Long.compare(y.at(), x.at()));
        for (Alert x : sorted) {
            JsonObject j = person(x.target());
            j.addProperty("id", x.id());
            j.addProperty("kind", x.kind());
            j.addProperty("motif", x.motif());
            JsonArray ctx = new JsonArray();
            x.ctx().forEach(ctx::add);
            ctx.add(ago(x.at()));
            j.add("ctx", ctx);
            j.addProperty("score", x.score());
            al.add(j);
        }
        o.add("alerts", al);

        JsonArray pl = new JsonArray();
        for (Player p : Bukkit.getOnlinePlayers()) pl.add(person(p.getUniqueId()));
        o.add("players", pl);

        JsonArray ch = new JsonArray();
        for (ChatLine c : chat) {
            JsonObject j = new JsonObject();
            j.addProperty("time", hm(c.at()));
            j.addProperty("grade", c.grade());
            j.addProperty("name", c.name());
            j.addProperty("text", c.text());
            j.addProperty("sys", c.sys());
            ch.add(j);
        }
        o.add("chat", ch);
        int online = 0;
        for (Player p : Bukkit.getOnlinePlayers()) if (StaffGrades.of(p) > 0) online++;
        o.addProperty("staffOnline", online);

        JsonArray jr = new JsonArray();
        int n = 0;
        for (StaffStore.JournalEntry e : store.journal()) {
            if (n++ >= 120) break;
            JsonObject j = new JsonObject();
            j.addProperty("time", hm(e.at()));
            j.addProperty("name", e.staff());
            j.addProperty("grade", e.grade());
            j.addProperty("kind", e.kind());
            j.addProperty("type", e.type());
            j.addProperty("detail", e.detail());
            jr.add(j);
        }
        o.add("journal", jr);
        o.add("commands", commands());
        var outils = plugin.outilsStaff();
        o.addProperty("modeStaff", outils != null && outils.mode().actif(viewer.getUniqueId()));
        o.addProperty("modeAnim", outils != null && outils.animation().actif(viewer.getUniqueId()));
        o.addProperty("animPossible", com.reborn.shinobicore.staff.outils.ModeAnimation.permis(viewer));
        JsonArray tr = new JsonArray();
        for (Traitee x : traitees) {
            JsonObject j = new JsonObject();
            Player tp = Bukkit.getPlayer(x.target());
            ShinobiCharacter tc = tp != null ? plugin.characters().getActive(x.target()) : null;
            j.addProperty("kind", x.kind());
            j.addProperty("pid", tc != null && tc.hasPlayerId() ? com.reborn.shinobicore.character.PlayerIds.format(tc.playerId()) : "");
            j.addProperty("perso", tc != null ? tc.name() : store.nameOf(x.target()));
            j.addProperty("par", x.parId().equals(viewer.getUniqueId()) ? "Toi" : x.par());
            j.addProperty("quoi", x.quoi());
            j.addProperty("time", hm(x.at()));
            j.addProperty("moi", x.parId().equals(viewer.getUniqueId()));
            tr.add(j);
        }
        o.add("traitees", tr);
        JsonArray cats = new JsonArray();
        for (var c : com.reborn.shinobicore.api.JournalService.Categorie.values()) {
            JsonObject j = new JsonObject();
            j.addProperty("id", c.name());
            j.addProperty("label", c.label());
            cats.add(j);
        }
        o.add("journalCats", cats);
        send(viewer, o);
    }

    /** Résumé d'un joueur (en ligne ou non) pour les listes. */
    private JsonObject person(UUID id) {
        JsonObject j = new JsonObject();
        j.addProperty("uuid", id.toString());
        Player p = Bukkit.getPlayer(id);
        j.addProperty("name", p != null ? p.getName() : store.nameOf(id));
        j.addProperty("online", p != null);
        ShinobiCharacter c = p != null ? plugin.characters().getActive(id) : null;
        j.addProperty("perso", c != null ? c.name() : "");
        // Player ID (N° du personnage actif) : le Poste de garde est réservé au staff.
        j.addProperty("pid", c != null && c.hasPlayerId() ? com.reborn.shinobicore.character.PlayerIds.format(c.playerId()) : "");
        j.addProperty("village", c != null ? c.village() : "");
        j.addProperty("rank", c != null ? c.rank().displayName() : "");
        j.addProperty("state", stateOf(id, c));
        var outils = plugin.outilsStaff();
        int pidN = c != null && c.hasPlayerId() ? c.playerId() : -1;
        j.addProperty("notes", outils != null && pidN >= 0 ? outils.notes().nombre(pidN) : 0);
        JsonArray et = new JsonArray();
        if (outils != null && pidN >= 0) outils.notes().etiquettes(pidN).forEach(et::add);
        j.add("etiquettes", et);
        j.addProperty("gele", frozen(id));
        j.addProperty("noir", outils != null && outils.effets().enNoir(id));
        j.addProperty("modeStaff", outils != null && outils.mode().actif(id));
        j.addProperty("modeAnim", outils != null && outils.animation().actif(id));
        return j;
    }

    private String stateOf(UUID id, ShinobiCharacter c) {
        List<String> parts = new ArrayList<>();
        KoState ko = plugin.ko().getKo(id);
        if (ko != null) parts.add(ko.isDowned() ? "À terre" : "Inconscient");
        if (c != null && plugin.ata() != null) {
            AtaManager.View v = plugin.ata().view(c.id());
            if (v != null) parts.add("ATA " + (v.level() == AtaManager.Level.PLEINE ? "pleine" : "allégée")
                    + " · repos " + v.restMinutes() + "/" + v.requiredMinutes());
        }
        if (frozen(id)) parts.add("Gelé");
        if (store.mutedUntil(id) > System.currentTimeMillis()) parts.add("Muet");
        return String.join(" · ", parts);
    }

    /* ================================================================ fiche */

    private void sendProfile(Player viewer, UUID id) {
        int grade = StaffGrades.of(viewer);
        JsonObject o = person(id);
        o.addProperty("t", "profile");
        Player p = Bukkit.getPlayer(id);
        ShinobiCharacter c = p != null ? plugin.characters().getActive(id) : null;
        if (p != null) {
            o.addProperty("hp", Math.round(p.getHealth()));
            o.addProperty("hpMax", Math.round(p.getMaxHealth()));
            o.addProperty("ping", p.getPing());
            o.addProperty("playtime", p.getStatistic(Statistic.PLAY_ONE_MINUTE) / 20 / 60);
        }
        if (c != null) {
            o.addProperty("chakra", Math.round(c.chakra().current()));
            o.addProperty("chakraMax", Math.round(c.chakra().max()));
            // Monnaie et banque (15/10) : section « Économie » de la fiche (le ryo quitte la ligne ping / temps de jeu).
            if (plugin.monnaie() != null) o.add("eco", plugin.monnaie().banque().ficheStaff(c, p));
            // Nourriture (14/10) : entreprise(s) et satiété, une ligne de la fiche.
            var nour = plugin.nourriture();
            if (nour != null) {
                String ent = nour.entreprises().resume(c.id());
                if (!ent.isEmpty()) o.addProperty("entreprise", ent);
                o.addProperty("satiete", Math.round(nour.satiete().satiete(p)));
            }
        }
        o.addProperty("frozen", frozen(id));
        var outils = plugin.outilsStaff();
        var gel = outils != null ? outils.gels().gel(id) : null;
        o.addProperty("geleMotif", gel != null ? gel.motif() : "");
        o.addProperty("gelePar", gel != null ? gel.par() : "");
        o.addProperty("retour", outils != null && outils.peutRenvoyer(id));
        o.addProperty("back", outils != null && outils.peutRevenir(viewer.getUniqueId()));
        o.addProperty("watching", plugin.vanish() != null && plugin.vanish().isVanished(viewer.getUniqueId()));
        Alert ag = alerts.get("ag-" + id);
        o.addProperty("score", ag != null ? ag.score() : 0);

        JsonArray alts = new JsonArray();
        store.alts(id).forEach((alt, seen) -> {
            JsonObject j = new JsonObject();
            j.addProperty("uuid", alt.toString());
            j.addProperty("name", store.nameOf(alt));
            j.addProperty("seen", seen > 0 ? ago(seen) : "jamais");
            alts.add(j);
        });
        o.add("alts", alts);

        long now = System.currentTimeMillis();
        JsonArray rec = new JsonArray();
        int active = 0;
        List<StaffStore.Sanction> list = new ArrayList<>(store.of(id));
        list.sort((x, y) -> Long.compare(y.at, x.at));
        for (StaffStore.Sanction s : list) {
            JsonObject j = new JsonObject();
            j.addProperty("when", new SimpleDateFormat("dd/MM · HH:mm").format(new Date(s.at)));
            Offenses.Offense off = Offenses.ALL.get(s.offense);
            j.addProperty("text", Offenses.Step.parse(s.kind + ":" + s.minutes).label() + " · "
                    + (off != null ? off.name() : s.offense) + " · par " + s.staff);
            boolean act = s.active(now);
            if (act) active++;
            j.addProperty("status", s.revoked ? "levée" : act ? "actif" : ("WARN".equals(s.kind) ? "noté" : "expiré"));
            j.addProperty("active", act);
            rec.add(j);
        }
        o.add("record", rec);
        o.addProperty("recordTotal", list.size());
        o.addProperty("recordActive", active);

        JsonArray offs = new JsonArray();
        for (Offenses.Offense off : Offenses.ALL.values()) {
            int strikes = store.strikes(id, off.id());
            Offenses.Step next = Offenses.stepFor(off, strikes);
            JsonObject j = new JsonObject();
            j.addProperty("id", off.id());
            j.addProperty("name", off.name());
            j.addProperty("strikes", strikes);
            j.addProperty("next", next.label());
            j.addProperty("tone", next.tone());
            j.addProperty("locked", next.requiredGrade() > grade);
            j.addProperty("lockGrade", StaffGrades.name(next.requiredGrade()));
            offs.add(j);
        }
        o.add("offenses", offs);

        JsonArray msgs = new JsonArray();
        Deque<Said> d = said.get(id);
        if (d != null) synchronized (d) {
            for (Said s : d) {
                JsonObject j = new JsonObject();
                j.addProperty("time", new SimpleDateFormat("dd/MM 'à' HH:mm").format(new Date(s.at())));
                j.addProperty("text", s.text());
                msgs.add(j);
            }
        }
        o.add("msgs", msgs);
        send(viewer, o);
    }

    /* ================================================================ actions */

    private void act(Player staff, int grade, String op, UUID id, JsonObject in) {
        var outils = plugin.outilsStaff();
        Player t = Bukkit.getPlayer(id);
        String who = t != null ? persoName(t) : store.nameOf(id);
        int need = switch (op) {
            case "tp", "back", "announce" -> StaffGrades.HELPER;
            case "shake" -> StaffGrades.ADMIN;
            default -> StaffGrades.MODO;
        };
        if (grade < need) { toast(staff, "Ton grade ne permet pas cette action (" + StaffGrades.name(need) + " requis)."); return; }
        if (outils == null && !op.equals("watch") && !op.equals("revive") && !op.equals("lift_ata")) { toast(staff, "Outils staff indisponibles."); return; }
        if (t == null && !op.equals("unfreeze") && !op.equals("freeze") && !op.equals("back")) { toast(staff, who + " n'est pas en ligne."); return; }
        switch (op) {
            case "tp" -> {
                outils.aller(staff, t);
                log(staff, "staff", "TP vers", who);
                toast(staff, "Téléporté vers " + who + ".");
            }
            case "back" -> {
                if (outils.retour(staff)) toast(staff, "Retour effectué.");
                else toast(staff, "Aucune position de retour.");
            }
            case "bring" -> {
                outils.ramener(staff, t);
                log(staff, "staff", "Ramené", who + " → " + staff.getName());
                toast(staff, who + " a été ramené.");
            }
            case "return" -> {
                if (outils.renvoyer(staff, t)) toast(staff, who + " est renvoyé à sa position.");
                else toast(staff, who + " n'a pas été ramené.");
            }
            case "freeze", "unfreeze" -> {
                boolean on = op.equals("freeze") && !frozen(id);
                if (on) outils.gels().geler(staff, id, t != null ? t.getName() : store.nameOf(id), str(in, "m"));
                else outils.gels().degeler(staff, id);
                log(staff, "staff", on ? "Gelé" : "Dégelé", who);
                toast(staff, who + (on ? " est gelé (persistant)." : " est dégelé."));
            }
            case "watch" -> {
                var v = plugin.vanish();
                if (v == null) return;
                if (v.isVanished(staff.getUniqueId())) {
                    v.disable(staff);
                    toast(staff, "Observation terminée : tu es de nouveau visible.");
                } else {
                    if (outils != null) outils.memoriser(staff);
                    v.enable(staff, com.reborn.shinobicore.vanish.VanishManager.Mode.HIDE_ALL, Set.of());
                    Location l = t.getLocation().clone().add(t.getLocation().getDirection().multiply(-4)).add(0, 1.5, 0);
                    staff.teleport(l.setDirection(t.getLocation().toVector().subtract(l.toVector())));
                    log(staff, "tp", "Observation", who);
                    toast(staff, "Tu observes " + who + " en invisible.");
                }
            }
            case "revive" -> {
                if (!plugin.ko().isKo(id)) { toast(staff, who + " n'est pas KO."); return; }
                plugin.ko().forceClear(id);
                double hp = Math.max(1.0, t.getMaxHealth() * plugin.ko().floorPct());
                t.setHealth(hp);
                log(staff, "ko", "Réveil forcé", who);
                toast(staff, who + " est réveillé.");
            }
            case "lift_ata" -> {
                ShinobiCharacter c = plugin.characters().getActive(id);
                if (c == null || plugin.ata() == null || !plugin.ata().lift(c.id(), "Le staff a levé ton ATA.")) {
                    toast(staff, who + " n'a pas d'ATA.");
                    return;
                }
                log(staff, "ko", "ATA levée", who);
                toast(staff, "ATA levée pour " + who + ".");
            }
            case "invsee" -> outils.voirSac().ouvrir(staff, t);
            case "blind" -> {
                if (outils.effets().enNoir(id)) {
                    outils.effets().finNoir(staff, id);
                    toast(staff, "Écran noir levé pour " + who + ".");
                } else {
                    outils.effets().noir(staff, t, str(in, "m"), intDe(in, "s", 0));
                    toast(staff, "Écran noir sur " + who + ".");
                }
            }
            case "shake" -> {
                outils.effets().secouerUn(staff, t, intDe(in, "v", 5), intDe(in, "s", 4));
                toast(staff, "Séisme sur " + who + ".");
            }
            case "announce" -> {
                String m = str(in, "m").trim();
                if (m.isEmpty()) return;
                outils.effets().annoncePerso(staff, t, m, intDe(in, "s", 8));
                toast(staff, "Message affiché à " + who + ".");
            }
            case "emote" -> {
                String v = str(in, "v").trim();
                if (v.isEmpty() || plugin.emotes() == null) return;
                if (v.equalsIgnoreCase("stop")) plugin.emotes().stop(t);
                else {
                    var e = plugin.emotes().resolve(v);
                    if (e == null) { toast(staff, "Emote inconnue : " + v); return; }
                    plugin.emotes().play(t, e);
                }
                com.reborn.shinobicore.journal.Journal.entree(com.reborn.shinobicore.api.JournalService.Categorie.STAFF,
                        "EMOTE_FORCEE").acteur(staff).cible(t).detail(v).ecrire();
                toast(staff, "Emote « " + v + " » pour " + who + ".");
            }
            case "outfit" -> {
                String v = str(in, "v").trim();
                if (v.isEmpty()) return;
                if (v.equalsIgnoreCase("retirer")) {
                    toast(staff, outils.tenues().lever(staff, t) ? "Tenue rendue à " + who + "." : who + " n'a pas de tenue imposée.");
                } else {
                    var it = outils.tenues().fabriquer(staff, v);
                    if (it == null) { toast(staff, "Tenue introuvable : " + v); return; }
                    toast(staff, outils.tenues().imposer(staff, t, it, v) ? "Tenue imposée à " + who + "." : who + " n'a pas de personnage actif.");
                }
            }
            default -> { return; }
        }
        String alerte = str(in, "alerte");
        if (!alerte.isEmpty()) {
            String quoi = switch (op) { case "tp" -> "y est allé"; case "watch" -> "l'a observé"; case "announce" -> "a répondu"; default -> "a agi"; };
            if (traiter(alerte, staff, quoi)) alerts.remove(alerte);
        }
        sendProfile(staff, id);
    }

    /** Range une alerte dans les « traitées » (20 dernières). Renvoie false si elle n'existe plus. */
    private boolean traiter(String alertId, Player staff, String quoi) {
        Alert a = alerts.get(alertId);
        if (a == null) return false;
        traitees.addFirst(new Traitee(System.currentTimeMillis(), a.kind(), a.target(), staff.getName(), staff.getUniqueId(), quoi));
        while (traitees.size() > 20) traitees.pollLast();
        return true;
    }

    /** Mode staff / animation du joueur, pour l'indicateur discret du mod (message {@code mode}). */
    public void envoyerMode(Player p) {
        if (!modded(p)) return;
        var o = plugin.outilsStaff();
        JsonObject j = new JsonObject();
        j.addProperty("t", "mode");
        j.addProperty("staff", o != null && o.mode().actif(p.getUniqueId()));
        j.addProperty("anim", o != null && o.animation().actif(p.getUniqueId()));
        send(p, j);
    }

    /**
     * Recherche universelle du Poste de garde ({@code a: cherche}) : personnages de l'index des N° (hors ligne
     * compris) dont le N°, le nom RP ou le pseudo du compte correspond. 20 résultats au plus.
     */
    private void cherche(Player viewer, JsonObject in) {
        String q = str(in, "q").trim().toLowerCase(java.util.Locale.ROOT);
        JsonObject r = new JsonObject();
        r.addProperty("t", "cherche");
        r.addProperty("req", intDe(in, "req", 0));
        r.addProperty("q", q);
        JsonArray js = new JsonArray();
        if (q.length() >= 2 && plugin.playerIds() != null) {
            String[] mots = q.split(" +");
            for (var e : plugin.playerIds().entries()) {
                if (e.deleted()) continue;
                String pseudo = store.nameOf(e.ownerId());
                String pid = com.reborn.shinobicore.character.PlayerIds.format(e.number());
                boolean ok = true;
                for (String w : mots) {
                    String d = w.startsWith("#") ? w.substring(1) : w;
                    boolean num = !d.isEmpty() && d.chars().allMatch(Character::isDigit);
                    boolean m = num ? pid.contains(d) || String.valueOf(e.number()).equals(d.replaceFirst("^0+(?=.)", ""))
                            : e.name().toLowerCase(java.util.Locale.ROOT).contains(w) || pseudo.toLowerCase(java.util.Locale.ROOT).contains(w);
                    if (!m) { ok = false; break; }
                }
                if (!ok) continue;
                Player en = Bukkit.getPlayer(e.ownerId());
                ShinobiCharacter actif = en != null ? plugin.characters().getActive(e.ownerId()) : null;
                boolean online = actif != null && actif.hasPlayerId() && actif.playerId() == e.number();
                JsonObject j = new JsonObject();
                j.addProperty("uuid", e.ownerId().toString());
                j.addProperty("pid", pid);
                j.addProperty("perso", e.name());
                j.addProperty("name", pseudo);
                j.addProperty("online", online);
                long vu = store.vuLe(e.ownerId());
                j.addProperty("vu", en != null ? "en ligne (autre perso)" : vu > 0 ? ago(vu) : "");
                js.add(j);
                if (js.size() >= 20) break;
            }
        }
        r.add("joueurs", js);
        send(viewer, r);
    }

    /* ================================================================ journal universel, notes */

    private void journalQuery(Player viewer, JsonObject in) {
        var j = com.reborn.shinobicore.journal.Journal.impl();
        if (j == null) { toast(viewer, "Journal indisponible."); return; }
        java.util.Set<com.reborn.shinobicore.api.JournalService.Categorie> cats =
                java.util.EnumSet.noneOf(com.reborn.shinobicore.api.JournalService.Categorie.class);
        if (in.has("cat") && in.get("cat").isJsonArray()) {
            for (JsonElement e : in.getAsJsonArray("cat")) {
                var c = com.reborn.shinobicore.api.JournalService.Categorie.parse(e.getAsString());
                if (c != null) cats.add(c);
            }
        }
        String pidS = str(in, "pid").trim();
        int pid = pidS.isEmpty() ? -1 : com.reborn.shinobicore.character.PlayerIdRegistry.parse(pidS.startsWith("#") ? pidS : "#" + pidS);
        var req = new com.reborn.shinobicore.journal.JournalServiceImpl.Requete(cats, pid, str(in, "joueur"),
                longDe(in, "du"), longDe(in, "au"), str(in, "q"), intDe(in, "page", 0), intDe(in, "par", 50));
        long reqId = longDe(in, "req");
        j.chercher(req, res -> {
            if (!viewer.isOnline()) return;
            JsonObject o = new JsonObject();
            o.addProperty("t", "journal");
            o.addProperty("req", reqId);
            o.addProperty("total", res.total());
            o.addProperty("page", res.page());
            o.addProperty("pages", res.pages());
            o.addProperty("source", res.source());
            JsonArray cl = new JsonArray();
            for (var c : com.reborn.shinobicore.api.JournalService.Categorie.values()) {
                JsonObject x = new JsonObject();
                x.addProperty("id", c.name());
                x.addProperty("label", c.label());
                cl.add(x);
            }
            o.add("cats", cl);
            JsonArray arr = new JsonArray();
            SimpleDateFormat f = new SimpleDateFormat("dd/MM HH:mm:ss");
            for (var l : res.lignes()) {
                JsonObject e = new JsonObject();
                e.addProperty("id", l.id());
                e.addProperty("at", l.at());
                e.addProperty("date", f.format(new Date(l.at())));
                e.addProperty("cat", l.cat().name());
                e.addProperty("action", l.action());
                e.add("acteur", l.acteur() == null ? com.google.gson.JsonNull.INSTANCE : l.acteur().json());
                e.add("cible", l.cible() == null ? com.google.gson.JsonNull.INSTANCE : l.cible().json());
                e.addProperty("detail", l.detail());
                e.addProperty("lieu", l.lieu());
                arr.add(e);
            }
            o.add("entries", arr);
            send(viewer, o);
        });
    }

    private void sendNotes(Player viewer, JsonObject in) {
        var outils = plugin.outilsStaff();
        int pid = pidDe(in);
        JsonObject o = new JsonObject();
        o.addProperty("t", "notes");
        o.addProperty("pid", pid >= 0 ? com.reborn.shinobicore.character.PlayerIdRegistry.format(pid) : "");
        var e = pid >= 0 && plugin.playerIds() != null ? plugin.playerIds().get(pid) : null;
        o.addProperty("perso", e != null ? e.name() : "");
        o.addProperty("pseudo", e != null && e.ownerId() != null ? store.nameOf(e.ownerId()) : "");
        JsonArray arr = new JsonArray();
        if (outils != null && pid >= 0) {
            SimpleDateFormat f = new SimpleDateFormat("dd/MM HH:mm");
            for (var n : outils.notes().de(pid)) {
                JsonObject j = new JsonObject();
                j.addProperty("id", n.id());
                j.addProperty("at", n.at());
                j.addProperty("date", f.format(new Date(n.at())));
                j.addProperty("auteur", n.auteur());
                j.addProperty("auteurPid", n.auteurPid());
                j.addProperty("auteurGrade", n.auteurGrade());
                j.addProperty("etiquette", n.etiquette());
                j.addProperty("texte", n.texte());
                j.addProperty("suppr", com.reborn.shinobicore.staff.outils.NotesStaff.peutSupprimer(viewer, n));
                arr.add(j);
            }
        }
        o.add("entries", arr);
        send(viewer, o);
    }

    private void sanction(Player staff, int grade, UUID id, String offenseId, boolean warnOnly, String note, JsonArray ev) {
        Offenses.Offense off = Offenses.ALL.get(offenseId);
        if (off == null) return;
        int strikes = store.strikes(id, off.id());
        Offenses.Step step = warnOnly ? new Offenses.Step("WARN", 0) : Offenses.stepFor(off, strikes);
        if (step.requiredGrade() > grade) {
            toast(staff, step.label() + " : réservé au grade " + StaffGrades.name(step.requiredGrade()) + ".");
            return;
        }
        long now = System.currentTimeMillis();
        StaffStore.Sanction s = new StaffStore.Sanction();
        s.id = Long.toString(now, 36);
        s.offense = off.id();
        s.kind = step.kind();
        s.minutes = step.minutes();
        s.until = "BAN".equals(step.kind()) && step.minutes() < 0 ? -1
                : step.minutes() > 0 ? now + step.minutes() * 60_000L : 0;
        s.staff = staff.getName();
        s.note = note == null ? "" : note;
        s.at = now;
        for (JsonElement e : ev) s.evidence.add(e.getAsString());
        store.add(id, s);

        OfflinePlayer target = Bukkit.getOfflinePlayer(id);
        Player online = target.getPlayer();
        String who = online != null ? persoName(online) : store.nameOf(id);
        String reason = off.name() + (s.note.isEmpty() ? "" : " — " + s.note);
        switch (step.kind()) {
            case "WARN" -> {
                if (online != null) online.showTitle(Title.title(Component.text("Avertissement", NamedTextColor.GOLD),
                        Component.text(off.name(), NamedTextColor.GRAY)));
            }
            case "MUTE" -> {
                if (online != null) online.sendMessage("§cTu es muet pendant " + Offenses.duration(step.minutes())
                        + " (" + off.name() + ").");
            }
            case "KICK" -> {
                if (online != null) online.kick(Component.text("Expulsé : " + reason));
            }
            case "BAN" -> {
                Duration d = step.minutes() < 0 ? null : Duration.ofMinutes(step.minutes());
                target.ban(reason, d, staff.getName());
                if (online != null) online.kick(Component.text("Banni : " + reason
                        + (d == null ? " (définitif)" : " (" + Offenses.duration(step.minutes()) + ")")));
            }
            default -> { }
        }
        log(staff, "sanction", step.label(), who + " · " + off.name());
        system("Sanction : " + who + " · " + step.label() + " (" + off.name() + "), par " + staff.getName() + ".");
        alerts.remove("ag-" + id);
        alerts.remove("rep-" + id);
        toast(staff, "Sceau apposé : " + step.label() + " · inscrit au casier.");
        sendProfile(staff, id);
        sendSnapshot(staff);
    }

    /* ================================================================ alertes */

    /** Combat ouvert hors terrain d'entraînement (appelé par AggressionLog). */
    public void onAggression(Player attacker, Player victim, Location at) {
        UUID id = attacker.getUniqueId();
        long now = System.currentTimeMillis();
        Deque<Long> d = aggressions.computeIfAbsent(id, k -> new ArrayDeque<>());
        d.addLast(now);
        while (!d.isEmpty() && now - d.peekFirst() > 3600_000L) d.pollFirst();
        Set<UUID> v = victims.computeIfAbsent(id, k -> ConcurrentHashMap.newKeySet());
        v.add(victim.getUniqueId());
        if (d.size() < 2) return;   // un échange isolé n'est pas une alerte
        int score = d.size() * 40 + v.size() * 20;
        String key = "ag-" + id;
        boolean fresh = !alerts.containsKey(key);
        alerts.put(key, new Alert(key, "agression", id, "Combat hors terrain d'entraînement ×" + d.size(),
                List.of(v.size() + (v.size() > 1 ? " victimes différentes" : " victime"), place(at)), score, now));
        if (fresh) system("Alerte : " + persoName(attacker) + " · combat hors terrain ×" + d.size() + " (score " + score + ").");
    }

    /** Appel à l'aide d'un joueur à terre (appelé par /aide). */
    public void onAide(Player p) {
        String key = "aide-" + p.getUniqueId();
        alerts.put(key, new Alert(key, "aide", p.getUniqueId(), "Appel à l'aide · à terre",
                List.of(place(p.getLocation())), 0, System.currentTimeMillis()));
    }

    /**
     * Déconnexion en combat (moins de 15 s après un coup porté ou reçu, {@link com.reborn.shinobicore.etat.EnCombat}) :
     * alerte au Poste de garde + ligne au chat staff ; pas de sanction automatique (décision du 14/10). Le libellé
     * sert de type tant que le mod n'a pas de pictogramme dédié.
     */
    public void onDecoEnCombat(Player p, String contre, Location at) {
        long now = System.currentTimeMillis();
        String key = "deco-" + p.getUniqueId() + "-" + now;
        alerts.put(key, new Alert(key, "Déco en combat", p.getUniqueId(), "Déconnexion en plein combat · contre " + contre,
                List.of(place(at)), 20, now));
        system("Déconnexion en combat : " + persoName(p) + " (contre " + contre + ") · " + place(at) + ".");
    }

    /** Demande de mise à mort RP : attend la décision du staff. */
    public void onKillRequest(com.reborn.shinobicore.ko.action.KillRequest req) {
        String key = "kill-" + req.id();
        alerts.put(key, new Alert(key, "mort", req.targetPlayerId(),
                "Mise à mort RP demandée par " + req.requesterName(),
                List.of(place(req.scene()), "valider ou refuser"), 0, System.currentTimeMillis()));
        system("Mort RP à trancher : " + req.targetName() + " (demandée par " + req.requesterName() + ").");
    }

    /** La demande de mort RP est tranchée ou tombée. */
    public void onKillClosed(long requestId) { alerts.remove("kill-" + requestId); }

    /**
     * Chirurgie des yeux : un œil vient d'être prélevé (ou perdu pendant le prélèvement). Alerte {@code yeux}
     * sur la victime (le propriétaire de l'œil, même si c'est un corps) + ligne dans le chat staff.
     */
    public void onPrelevementOeil(Player operator, UUID victim, String victimName, String cote, String type,
                                  boolean ok, int score, String source, Location at) {
        long now = System.currentTimeMillis();
        String key = "yeux-" + victim + "-" + now;
        String issue = ok ? "œil prélevé (intégrité " + score + " %)" : "œil perdu pendant le prélèvement";
        alerts.put(key, new Alert(key, "yeux", victim,
                "Œil " + cote + " (" + type + ") · " + issue + " par " + persoName(operator),
                List.of(source, place(at)), 0, now));
        system("Prélèvement d'œil : " + persoName(operator) + " sur " + victimName + " (" + source + ") · œil "
                + cote + " " + type + " · " + issue + " · " + place(at) + ".");
    }

    /**
     * Vol d'un parchemin dans une bibliothèque (ShinobiAbilities, docs/gameplay/APPRENTISSAGE.md §3.7). Alerte
     * {@code vol} sur le voleur + ligne dans le chat staff.
     */
    public void onVolParchemin(Player thief, String library, String technique, char rank, Location at) {
        long now = System.currentTimeMillis();
        String key = "vol-" + thief.getUniqueId() + "-" + now;
        alerts.put(key, new Alert(key, "vol", thief.getUniqueId(),
                "Parchemin volé : " + technique + " (rang " + rank + ") · " + library,
                List.of(place(at)), rank == 'S' ? 60 : rank == 'A' ? 30 : 0, now));
        system("Vol de parchemin : " + persoName(thief) + " · " + technique + " (rang " + rank + ") · " + library
                + " · " + place(at) + ".");
    }

    /**
     * Monnaie et banque (15/10) : alerte {@code economie} (virement ou échange ≥ 10 000, vol ≥ 1 000, trois virements
     * vers le même compte en 24 h, comptes liés, bourse en double, grosse bourse ramassée) + ligne dans le chat staff.
     */
    public void onAlerteEconomie(UUID who, String texte, Location at, int score) {
        long now = System.currentTimeMillis();
        String key = "eco-" + who + "-" + now;
        alerts.put(key, new Alert(key, "economie", who, texte, at == null ? List.of() : List.of(place(at)), score, now));
        system("Économie : " + texte + (at == null ? "" : " · " + place(at)) + ".");
    }

    /** Deux comptes déjà vus avec la même IP (Poste de garde, « Comptes liés ») ? */
    public boolean comptesLies(UUID a, UUID b) {
        if (a == null || b == null || a.equals(b)) return false;
        try { return store.alts(a).containsKey(b); } catch (RuntimeException e) { return false; }
    }

    /** Signalement d'un joueur par un autre ({@code /signaler}). */
    public void onReport(Player reporter, Player target, String motif) {
        Set<UUID> r = reporters.computeIfAbsent(target.getUniqueId(), k -> ConcurrentHashMap.newKeySet());
        r.add(reporter.getUniqueId());
        String key = "rep-" + target.getUniqueId();
        alerts.put(key, new Alert(key, "signalement", target.getUniqueId(),
                "Signalé par " + r.size() + " joueur" + (r.size() > 1 ? "s" : "") + " · " + motif,
                List.of(place(target.getLocation())), 30 * r.size(), System.currentTimeMillis()));
        system("Signalement : " + persoName(target) + " · " + motif + " (par " + reporter.getName() + ").");
    }

    /* ================================================================ chat staff + journal */

    public void staffChat(Player from, String text) {
        int g = StaffGrades.of(from);
        chat.addLast(new ChatLine(System.currentTimeMillis(), g, from.getName(), text, false));
        com.reborn.shinobicore.journal.Journal.entree(com.reborn.shinobicore.api.JournalService.Categorie.CHAT,
                "CHAT_STAFF").acteur(from).detail(text).ecrire();
        while (chat.size() > 100) chat.pollFirst();
        Component line = Component.text("[Staff] ", NamedTextColor.GOLD)
                .append(Component.text(StaffGrades.name(g) + " " + from.getName() + " : ", NamedTextColor.YELLOW))
                .append(Component.text(text, NamedTextColor.WHITE));
        for (Player p : Bukkit.getOnlinePlayers()) if (StaffGrades.of(p) > 0) p.sendMessage(line);
    }

    public void system(String text) {
        chat.addLast(new ChatLine(System.currentTimeMillis(), 0, "Système", text, true));
        com.reborn.shinobicore.journal.Journal.entree(com.reborn.shinobicore.api.JournalService.Categorie.SYSTEME,
                "ALERTE_STAFF").detail(text).ecrire();
        while (chat.size() > 100) chat.pollFirst();
        Component line = Component.text("[Staff] ", NamedTextColor.GOLD).append(Component.text(text, NamedTextColor.RED));
        for (Player p : Bukkit.getOnlinePlayers()) if (StaffGrades.of(p) > 0) p.sendMessage(line);
    }

    /** Journalise une action faite par un membre du staff (ou un médic pour les soins). */
    /** Poste de garde seulement : l'appelant a déjà écrit au journal universel (ex. {@code KoJournal}). */
    public void logSansJournal(Player who, String kind, String type, String detail) {
        int g = StaffGrades.of(who);
        String tag = g == StaffGrades.NONE ? com.reborn.shinobicore.character.PlayerIds.tag(who) : "";
        store.log(tag.isEmpty() ? who.getName() : tag + " " + who.getName(), g, kind, type, detail);
    }

    public void log(Player who, String kind, String type, String detail) {
        // Acteur joueur (médic, bibliothèque…) : son N° devant le pseudo, pour retrouver le personnage.
        int g = StaffGrades.of(who);
        String tag = g == StaffGrades.NONE ? com.reborn.shinobicore.character.PlayerIds.tag(who) : "";
        store.log(tag.isEmpty() ? who.getName() : tag + " " + who.getName(), g, kind, type, detail);
        // Journal universel (14/10). « objet », « duel » et « staff » sont déjà journalisés par l'appelant.
        if (kind == null || kind.equals("objet") || kind.equals("duel") || kind.equals("staff")) return;
        var cat = switch (kind) {
            case "soin", "yeux" -> com.reborn.shinobicore.api.JournalService.Categorie.INTERACTION;
            case "parchemins" -> com.reborn.shinobicore.api.JournalService.Categorie.BIBLIOTHEQUE;
            case "ko" -> g > StaffGrades.NONE ? com.reborn.shinobicore.api.JournalService.Categorie.STAFF
                    : com.reborn.shinobicore.api.JournalService.Categorie.KO;
            default -> com.reborn.shinobicore.api.JournalService.Categorie.STAFF;
        };
        com.reborn.shinobicore.journal.Journal.entree(cat, (kind + "_" + type).toUpperCase(java.util.Locale.ROOT)
                .replace(' ', '_')).acteur(who).detail(detail).ecrire();
    }

    /* ================================================================ événements */

    @EventHandler(priority = EventPriority.LOWEST)
    public void onChat(AsyncChatEvent ev) {
        Player p = ev.getPlayer();
        String text = PlainTextComponentSerializer.plainText().serialize(ev.message());
        if (text.startsWith("#") && StaffGrades.of(p) > 0) {
            ev.setCancelled(true);
            String m = text.substring(1).trim();
            if (!m.isEmpty()) Bukkit.getScheduler().runTask(plugin, () -> staffChat(p, m));
            return;
        }
        long until = store.mutedUntil(p.getUniqueId());
        if (until > System.currentTimeMillis()) {
            ev.setCancelled(true);
            p.sendMessage("§cTu es muet encore " + Offenses.duration(Math.max(1, (until - System.currentTimeMillis()) / 60_000L)) + ".");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onChatSeen(AsyncChatEvent ev) {
        String text = PlainTextComponentSerializer.plainText().serialize(ev.message());
        Deque<Said> d = said.computeIfAbsent(ev.getPlayer().getUniqueId(), k -> new ArrayDeque<>());
        synchronized (d) {
            d.addFirst(new Said(System.currentTimeMillis(), text));
            while (d.size() > 8) d.pollLast();
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent ev) {
        Player p = ev.getPlayer();
        String ip = p.getAddress() != null ? p.getAddress().getAddress().getHostAddress() : null;
        store.seen(p.getUniqueId(), p.getName(), ip);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent ev) {
        store.seen(ev.getPlayer().getUniqueId(), ev.getPlayer().getName(), null);
    }

    /* ================================================================ utilitaires */

    private String persoName(Player p) {
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        // Staff seulement (toasts, chat staff, journal) : le N° préfixe le nom RP.
        return c != null ? com.reborn.shinobicore.character.PlayerIds.prefix(c) + c.name() + " (" + p.getName() + ")" : p.getName();
    }

    private String place(Location l) {
        if (l == null) return "";
        return l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ();
    }

    private static String hm(long at) { return new SimpleDateFormat("HH:mm").format(new Date(at)); }

    private static String ago(long at) {
        long m = (System.currentTimeMillis() - at) / 60_000L;
        if (m < 1) return "à l'instant";
        if (m < 60) return "il y a " + m + " min";
        return "il y a " + (m / 60) + " h";
    }

    private static JsonArray commands() {
        String[][] rows = {
                {"Joueurs et modération", "/garde", "Ouvrir le Poste de garde", "1"},
                {"Joueurs et modération", "#message", "Parler au staff depuis le chat", "1"},
                {"Joueurs et modération", "/staffchat <message>", "Parler au staff", "1"},
                {"Joueurs et modération", "/pid <joueur|#N°>", "N° de personnage, compte, état (hors ligne avec #N°)", "1"},
                {"Joueurs et modération", "/aller <joueur|#N°>", "Aller à un joueur (/retour pour revenir)", "1"},
                {"Joueurs et modération", "/journal [#N°] [catégorie] [h:24]", "Journal universel (tout est tracé)", "1"},
                {"Joueurs et modération", "/note <#N°> ajouter <texte>", "Notes staff sur un personnage", "1"},
                {"Joueurs et modération", "/annoncejoueur <joueur> <message>", "Message à un seul joueur", "1"},
                {"Joueurs et modération", "/modestaff", "Mode staff : invisible, aucune mécanique RP, plaques au-dessus des têtes", "1"},
                {"Joueurs et modération", "/modeanim", "Mode animation (animateur / MJ) : jouer pour un event, tout est journalisé", "1"},
                {"Joueurs et modération", "/ramener <joueur>", "Ramener un joueur (/retour <joueur> pour le renvoyer)", "2"},
                {"Joueurs et modération", "/geler <joueur> [motif]", "Gel persistant (/degeler)", "2"},
                {"Joueurs et modération", "/voirsac <joueur>", "Voir barre, sacoche et équipement", "2"},
                {"Joueurs et modération", "/noir <joueur> [secondes|off] [message]", "Écran noir forcé", "2"},
                {"Joueurs et modération", "/annonce <message>", "Annonce à tous", "2"},
                {"Joueurs et modération", "/tenue <joueur> <id|main|retirer>", "Imposer une tenue", "2"},
                {"Joueurs et modération", "/emoteforcee <joueur> <emote|stop>", "Forcer une emote", "2"},
                {"Joueurs et modération", "/persoreset <#N°>", "Remettre un personnage à zéro (confirmation)", "3"},
                {"Joueurs et modération", "/persosuppr <#N°>", "Supprimer un personnage (confirmation)", "3"},
                {"Monde et zones", "/seisme [rayon|tous] [force] [s] [message]", "Séisme (écran qui tremble)", "3"},
                {"Joueurs et modération", "/vanish", "Devenir invisible", "2"},
                {"Joueurs et modération", "/pardon <joueur>", "Lever un bannissement", "3"},
                {"KO, ATA et soins", "/ata voir <joueur>", "État ATA, repos et peur restante", "1"},
                {"KO, ATA et soins", "/soigner", "Soigner les blessures du joueur en face", "1"},
                {"KO, ATA et soins", "/ata lever <joueur>", "Lever l'ATA", "2"},
                {"KO, ATA et soins", "/ata appliquer <joueur> [pleine|allegee]", "Poser une ATA", "2"},
                {"KO, ATA et soins", "/hopital liste", "Voir les lits d'hôpital", "2"},
                {"KO, ATA et soins", "/hopital set <village|defaut>", "Placer le lit de réveil d'un village", "3"},
                {"KO, ATA et soins", "/sckillvote", "Valider ou refuser une mort RP", "3"},
                {"Monde et zones", "/zonerp liste", "Lister les zones RP", "2"},
                {"Monde et zones", "/carte tp <lieu>", "Se rendre à un lieu de la carte", "2"},
                {"Monde et zones", "/dummy", "Cibles d'entraînement", "2"},
                {"Monde et zones", "/zonerp creer <entrainement|repos> <id>", "Créer une zone RP", "3"},
                {"Monde et zones", "/meteo <type> [intensité] [durée] [tous|monde|ici|joueur]", "Météo : pluie, sable, brume, blizzard, tempête de sable", "3"},
                {"Monde et zones", "/cinematic play <nom>", "Lancer une cinématique", "3"},
                {"Serveur", "/sc reload", "Recharger ShinobiCore", "4"},
                {"Serveur", "/sa reload", "Recharger les techniques", "4"},
                {"Serveur", "/nexo reload", "Recharger les assets Nexo", "4"},
        };
        JsonArray out = new JsonArray();
        for (String[] r : rows) {
            JsonObject j = new JsonObject();
            j.addProperty("cat", r[0]);
            j.addProperty("cmd", r[1]);
            j.addProperty("desc", r[2]);
            j.addProperty("grade", Integer.parseInt(r[3]));
            out.add(j);
        }
        return out;
    }
}
