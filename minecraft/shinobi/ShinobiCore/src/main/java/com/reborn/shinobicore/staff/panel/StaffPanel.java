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
    private final Set<UUID> frozen = ConcurrentHashMap.newKeySet();

    public StaffPanel(ShinobiCore plugin) {
        this.plugin = plugin;
        this.store = new StaffStore(plugin);
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CHANNEL)) m.registerOutgoingPluginChannel(plugin, CHANNEL);
        m.registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    public StaffStore store() { return store; }

    /* ================================================================ réseau */

    public boolean modded(Player p) {
        return p.getListeningPluginChannels().contains(CHANNEL);
    }

    private void send(Player p, JsonObject o) {
        p.sendPluginMessage(plugin, CHANNEL, gson.toJson(o).getBytes(StandardCharsets.UTF_8));
    }

    private void toast(Player p, String msg) {
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
                UUID t = uuid(in, "t");
                if (t != null) sendProfile(p, t);
            }
            case "act" -> {
                UUID t = uuid(in, "t");
                if (t != null) act(p, grade, str(in, "op"), t);
            }
            case "sanction" -> {
                UUID t = uuid(in, "t");
                if (t != null) sanction(p, grade, t, str(in, "o"), in.has("warnOnly") && in.get("warnOnly").getAsBoolean(),
                        str(in, "note"), in.has("ev") ? in.getAsJsonArray("ev") : new JsonArray());
            }
            case "dismiss" -> {
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
            default -> { }
        }
    }

    private static String str(JsonObject o, String k) {
        JsonElement e = o.get(k);
        return e == null || e.isJsonNull() ? "" : e.getAsString();
    }

    private static UUID uuid(JsonObject o, String k) {
        try { return UUID.fromString(str(o, k)); } catch (IllegalArgumentException e) { return null; }
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
        j.addProperty("village", c != null ? c.village() : "");
        j.addProperty("rank", c != null ? c.rank().displayName() : "");
        j.addProperty("state", stateOf(id, c));
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
        if (frozen.contains(id)) parts.add("Gelé");
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
            o.addProperty("ryo", c.ryo());
        }
        o.addProperty("frozen", frozen.contains(id));
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

    private void act(Player staff, int grade, String op, UUID id) {
        Player t = Bukkit.getPlayer(id);
        String who = t != null ? persoName(t) : store.nameOf(id);
        int need = switch (op) {
            case "tp" -> StaffGrades.HELPER;
            default -> StaffGrades.MODO;
        };
        if (grade < need) { toast(staff, "Ton grade ne permet pas cette action (" + StaffGrades.name(need) + " requis)."); return; }
        if (t == null && !op.equals("unfreeze")) { toast(staff, who + " n'est pas en ligne."); return; }
        switch (op) {
            case "tp" -> {
                staff.teleport(t.getLocation());
                log(staff, "tp", "TP vers", who);
                toast(staff, "Téléporté vers " + who + ".");
            }
            case "bring" -> {
                t.teleport(staff.getLocation());
                log(staff, "tp", "Ramené", who + " → " + staff.getName());
                toast(staff, who + " a été ramené.");
            }
            case "freeze" -> {
                boolean on = frozen.add(id);
                if (!on) frozen.remove(id);
                if (on) t.showTitle(Title.title(Component.text("Gelé", NamedTextColor.AQUA),
                        Component.text("Un membre du staff t'a immobilisé.", NamedTextColor.GRAY)));
                else t.sendMessage("§7Tu peux de nouveau bouger.");
                log(staff, "sanction", on ? "Gelé" : "Dégelé", who);
                toast(staff, who + (on ? " est gelé." : " est dégelé."));
            }
            case "watch" -> {
                var v = plugin.vanish();
                if (v == null) return;
                if (v.isVanished(staff.getUniqueId())) {
                    v.disable(staff);
                    toast(staff, "Observation terminée : tu es de nouveau visible.");
                } else {
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
            default -> { return; }
        }
        sendProfile(staff, id);
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
        while (chat.size() > 100) chat.pollFirst();
        Component line = Component.text("[Staff] ", NamedTextColor.GOLD)
                .append(Component.text(StaffGrades.name(g) + " " + from.getName() + " : ", NamedTextColor.YELLOW))
                .append(Component.text(text, NamedTextColor.WHITE));
        for (Player p : Bukkit.getOnlinePlayers()) if (StaffGrades.of(p) > 0) p.sendMessage(line);
    }

    public void system(String text) {
        chat.addLast(new ChatLine(System.currentTimeMillis(), 0, "Système", text, true));
        while (chat.size() > 100) chat.pollFirst();
        Component line = Component.text("[Staff] ", NamedTextColor.GOLD).append(Component.text(text, NamedTextColor.RED));
        for (Player p : Bukkit.getOnlinePlayers()) if (StaffGrades.of(p) > 0) p.sendMessage(line);
    }

    /** Journalise une action faite par un membre du staff (ou un médic pour les soins). */
    public void log(Player who, String kind, String type, String detail) {
        store.log(who.getName(), StaffGrades.of(who), kind, type, detail);
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

    @EventHandler(ignoreCancelled = true)
    public void onMove(PlayerMoveEvent ev) {
        if (!frozen.contains(ev.getPlayer().getUniqueId())) return;
        if (ev.getFrom().getX() != ev.getTo().getX() || ev.getFrom().getZ() != ev.getTo().getZ()
                || ev.getFrom().getY() < ev.getTo().getY()) {
            ev.setCancelled(true);
        }
    }

    /* ================================================================ utilitaires */

    private String persoName(Player p) {
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        return c != null ? c.name() + " (" + p.getName() + ")" : p.getName();
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
                {"Joueurs et modération", "/tp <joueur>", "Aller à un joueur", "1"},
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
                {"Monde et zones", "/meteo <type> [intensité] [joueur|tous]", "Météo visuelle", "3"},
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
