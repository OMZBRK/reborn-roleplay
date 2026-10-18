package com.reborn.shinobicore.ko;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.api.StatsService.Stat;
import com.reborn.shinobicore.stats.StatFormulas;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * KO-5 — QTE « Volonté » : à terre, le joueur tente de se relever.
 *
 * <ul>
 *   <li>Le client demande ({@code {"a":"start"}}, touche Saut à terre). Le serveur tire la séquence : n sceaux
 *       (4 à 7), l'instant de chacun, la touche à frapper (Espace / clic gauche / clic droit) et la fenêtre de
 *       tolérance (~250 → ~120 ms), puis l'envoie ({@code {"t":"start",…}}) et prolonge la phase « à terre » le
 *       temps du QTE.</li>
 *   <li>Le client renvoie ses appuis horodatés ({@code {"a":"result","p":[[touche,ms],…]}}). Le serveur vérifie
 *       chaque appui (bonne touche, dans la fenêtre + marge réseau) et que la durée réelle est plausible.</li>
 *   <li>Réussite : <b>second souffle</b> — debout à 5 % des PV, une blessure aggravée d'un cran, ATA allégée.
 *       Échec : perte de connaissance. <b>2 relèves maximum</b> par cycle de blessure ; le compteur revient à zéro
 *       au soin d'un médic. Tant que le compteur n'est pas remis à zéro, retomber = KO direct (sans phase à
 *       terre).</li>
 *   <li>Difficulté = 4 + relèves déjà faites + blessures graves − Vigueur/4.</li>
 * </ul>
 */
public final class VolonteQte implements PluginMessageListener {

    public static final String CHANNEL = "reborn:volonte";
    public static final int MAX_RELEVES = 2;
    private static final long NET_SLACK_MS = 90;

    private record Run(int[] keys, long[] times, int window, long sentAt) {}

    private final ShinobiCore plugin;
    private final Map<UUID, Run> runs = new ConcurrentHashMap<>();
    /** Relèves faites par personnage depuis le dernier soin de médic. */
    private final Map<UUID, Integer> releves = new ConcurrentHashMap<>();
    /** Personnages en « second souffle » (retomber = KO direct). */
    private final Set<UUID> secondWind = ConcurrentHashMap.newKeySet();
    private final Random rnd = new Random();

    public VolonteQte(ShinobiCore plugin) {
        this.plugin = plugin;
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CHANNEL)) m.registerOutgoingPluginChannel(plugin, CHANNEL);
        m.registerIncomingPluginChannel(plugin, CHANNEL, this);
        charger();
    }

    /* ---------------------------------------------------------------- persistance (ETATS_JOUEUR §2.4 n° 16) */

    /** Relèves et second souffle survivent au redémarrage (ils étaient perdus) : {@code volonte.yml}, par personnage. */
    private java.io.File fichier() { return new java.io.File(plugin.getDataFolder(), "volonte.yml"); }

    private void charger() {
        java.io.File f = fichier();
        if (!f.exists()) return;
        var y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
        var r = y.getConfigurationSection("releves");
        if (r != null) for (String k : r.getKeys(false)) {
            try { releves.put(UUID.fromString(k), r.getInt(k)); } catch (IllegalArgumentException ignored) { }
        }
        for (String k : y.getStringList("second-souffle")) {
            try { secondWind.add(UUID.fromString(k)); } catch (IllegalArgumentException ignored) { }
        }
    }

    private void sauver() {
        var y = new org.bukkit.configuration.file.YamlConfiguration();
        releves.forEach((k, v) -> y.set("releves." + k, v));
        y.set("second-souffle", secondWind.stream().map(UUID::toString).toList());
        try {
            y.save(fichier());
        } catch (java.io.IOException e) {
            plugin.getLogger().warning("[volonte] sauvegarde impossible : " + e.getMessage());
        }
    }

    /** Vrai si ce personnage doit tomber directement en KO (relèves épuisées ou second souffle en cours). */
    public boolean skipDowned(UUID charId) {
        return charId != null && (secondWind.contains(charId) || releves.getOrDefault(charId, 0) >= MAX_RELEVES);
    }

    /**
     * La Volonté est <b>facultative</b> : seulement pour les personnages qui connaissent la technique
     * {@code ko.volonte.technique} (défaut {@code volonte}), ou pour tous si {@code ko.volonte.pour-tous}.
     */
    public boolean allowed(ShinobiCharacter c) {
        if (plugin.getConfig().getBoolean("ko.volonte.pour-tous", false)) return true;
        String tech = plugin.getConfig().getString("ko.volonte.technique", "volonte");
        return c != null && tech != null && !tech.isBlank() && c.knowsAbility(tech);
    }

    /** À terre : prévient le client que la relève est possible (sans ça, aucune invite à l'écran). */
    public void offer(Player p, UUID charId) {
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        if (!allowed(c) || left(charId) <= 0) return;
        JsonObject o = new JsonObject();
        o.addProperty("t", "avail");
        o.addProperty("left", left(charId));
        send(p, o);
    }

    public int left(UUID charId) { return charId == null ? 0 : Math.max(0, MAX_RELEVES - releves.getOrDefault(charId, 0)); }

    /** Soin d'un médic : relèves et second souffle remis à zéro. */
    public void reset(UUID charId) {
        if (charId == null) return;
        boolean ch = releves.remove(charId) != null;
        ch |= secondWind.remove(charId);
        if (ch) sauver();
    }

    @Override
    public void onPluginMessageReceived(String channel, Player p, byte[] bytes) {
        if (!CHANNEL.equals(channel)) return;
        String raw = new String(bytes, StandardCharsets.UTF_8);
        Bukkit.getScheduler().runTask(plugin, () -> handle(p, raw));
    }

    private void handle(Player p, String raw) {
        JsonObject in;
        try { in = JsonParser.parseString(raw).getAsJsonObject(); } catch (RuntimeException e) { return; }
        String a = in.has("a") ? in.get("a").getAsString() : "";
        if (a.equals("start")) start(p);
        else if (a.equals("result")) result(p, in.has("p") ? in.getAsJsonArray("p") : new JsonArray());
    }

    private void start(Player p) {
        KoState st = plugin.ko().getKo(p.getUniqueId());
        if (st == null || !st.isDowned() || st.isBeingCarried() || runs.containsKey(p.getUniqueId())) return;
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        UUID cid = c != null ? c.id() : null;
        if (!allowed(c)) return;
        if (left(cid) <= 0) { toast(p, "Ton corps ne répond plus : tu as déjà puisé dans ta volonté."); return; }
        int grave = 0;
        double vig = 0;
        if (c != null) {
            for (var inj : c.injuries()) if (!inj.hidden() && inj.severity().ordinal() >= 2) grave++;
            vig = StatFormulas.eff(c.stats(), Stat.VIGUEUR);
        }
        int diff = (int) Math.round(Math.max(0, Math.min(6, releves.getOrDefault(cid, 0) + grave - vig / 4.0)));
        int n = Math.min(7, 4 + (diff + 1) / 2);
        int window = Math.max(120, 250 - diff * 22);
        int[] keys = new int[n];
        long[] times = new long[n];
        long t = 1100;
        for (int i = 0; i < n; i++) {
            keys[i] = rnd.nextInt(3);
            times[i] = t;
            t += 620 + rnd.nextInt(260) - diff * 25L;
        }
        Run run = new Run(keys, times, window, System.currentTimeMillis());
        runs.put(p.getUniqueId(), run);
        // la phase « à terre » ne s'écoule pas pendant le QTE
        st.setPhase(KoState.Phase.DOWNED, st.phaseStartMillis() + times[n - 1] + 1800);
        JsonObject o = new JsonObject();
        o.addProperty("t", "start");
        o.addProperty("win", window);
        o.addProperty("left", left(cid));
        JsonArray ks = new JsonArray(), ts = new JsonArray();
        for (int i = 0; i < n; i++) { ks.add(keys[i]); ts.add(times[i]); }
        o.add("keys", ks);
        o.add("times", ts);
        send(p, o);
        // sécurité : sans réponse, la tentative est perdue
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (runs.get(p.getUniqueId()) == run) { runs.remove(p.getUniqueId()); fail(p); }
        }, (times[n - 1] + 4000) / 50);
    }

    private void result(Player p, JsonArray presses) {
        Run run = runs.remove(p.getUniqueId());
        if (run == null) return;
        boolean ok = presses.size() >= run.keys().length;
        long last = 0;
        for (int i = 0; ok && i < run.keys().length; i++) {
            JsonElement e = presses.get(i);
            if (!e.isJsonArray() || e.getAsJsonArray().size() < 2) { ok = false; break; }
            int key = e.getAsJsonArray().get(0).getAsInt();
            long ms = e.getAsJsonArray().get(1).getAsLong();
            if (key != run.keys()[i] || Math.abs(ms - run.times()[i]) > run.window() + NET_SLACK_MS || ms < last) ok = false;
            last = ms;
        }
        // durée réelle plausible : on ne peut pas finir avant le dernier sceau
        long real = System.currentTimeMillis() - run.sentAt();
        if (real + 250 < run.times()[run.times().length - 1]) ok = false;
        KoState st = plugin.ko().getKo(p.getUniqueId());
        if (st == null || !st.isDowned()) return;
        if (ok) rise(p); else fail(p);
    }

    private void rise(Player p) {
        ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
        UUID cid = c != null ? c.id() : null;
        if (cid != null) {
            releves.merge(cid, 1, Integer::sum);
            secondWind.add(cid);
            sauver();
            // l'obstination se paie : la blessure la plus grave s'aggrave d'un cran
            com.reborn.shinobicore.ko.injury.Injury worst = null;
            for (var inj : c.injuries()) {
                if (inj.hidden()) continue;
                if (worst == null || inj.severity().ordinal() > worst.severity().ordinal()) worst = inj;
            }
            if (worst != null) {
                var up = worst.severity().upgrade();
                if (up != worst.severity()) { worst.setSeverity(up); worst.setNextHealableMillis(0L); }
                plugin.characterRepository().save(c);
            }
        }
        double max = c != null ? c.maxHp() : p.getMaxHealth();
        plugin.ko().rise(p.getUniqueId(), Math.max(1.0, max * 0.05));
        JsonObject o = new JsonObject();
        o.addProperty("t", "ok");
        o.addProperty("left", left(cid));
        send(p, o);
        p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_ATTACK_STRONG, 0.9f, 0.7f);
    }

    private void fail(Player p) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "fail");
        send(p, o);
        plugin.ko().knockOut(p.getUniqueId());
    }

    private void toast(Player p, String msg) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "deny");
        o.addProperty("m", msg);
        send(p, o);
    }

    private void send(Player p, JsonObject o) {
        try { p.sendPluginMessage(plugin, CHANNEL, o.toString().getBytes(StandardCharsets.UTF_8)); }
        catch (RuntimeException ignored) { }
    }
}
