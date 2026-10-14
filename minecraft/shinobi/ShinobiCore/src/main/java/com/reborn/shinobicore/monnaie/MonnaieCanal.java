package com.reborn.shinobicore.monnaie;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.reborn.shinobicore.ShinobiCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;

/**
 * Canaux du mod : {@code reborn:monnaie} (bourse, « Combien ? », fouille) et {@code reborn:banque} (guichet, coffre,
 * échange garanti). JSON UTF-8 brut sans préfixe (la taille est portée par le paquet). Un client sans le mod n'a pas
 * le canal : rien n'est envoyé. Protocole : docs/technique/PLUGINS_REFERENCE.md (Monnaie et banque).
 *
 * <p>C2S {@code reborn:monnaie} (texte) : {@code poser:<n>}, {@code ranger:<n>}, {@code vol:<n>},
 * {@code fouille:prendre:<H0..|B0..>}, {@code fouille:fermer}.
 * <br>C2S {@code reborn:banque} (texte) : voir {@link com.reborn.shinobicore.banque.GuichetCanal}.
 */
public final class MonnaieCanal implements PluginMessageListener {

    public static final String MONNAIE = "reborn:monnaie";
    public static final String BANQUE = "reborn:banque";

    /** Petit constructeur de JSON. */
    public static final class J {
        public final JsonObject o = new JsonObject();
        public J s(String k, String v) { if (v != null) o.addProperty(k, v); return this; }
        public J n(String k, long v) { o.addProperty(k, v); return this; }
        public J b(String k, boolean v) { o.addProperty(k, v); return this; }
        public J a(String k, JsonArray a) { o.add(k, a); return this; }
        public J e(String k, JsonElement e) { o.add(k, e); return this; }
    }

    private final ShinobiCore plugin;
    private final MonnaieModule module;

    MonnaieCanal(ShinobiCore plugin, MonnaieModule module) {
        this.plugin = plugin;
        this.module = module;
    }

    void start() {
        var m = Bukkit.getMessenger();
        for (String c : new String[] {MONNAIE, BANQUE}) {
            if (!m.isOutgoingChannelRegistered(plugin, c)) m.registerOutgoingPluginChannel(plugin, c);
            if (!m.isIncomingChannelRegistered(plugin, c)) m.registerIncomingPluginChannel(plugin, c, this);
        }
    }

    public void envoyer(Player p, J j) { envoyer(p, MONNAIE, j.o); }

    public void envoyerBanque(Player p, J j) { envoyer(p, BANQUE, j.o); }

    void envoyer(Player p, String canal, JsonObject o) {
        if (p == null || !p.isOnline()) return;
        try {
            p.sendPluginMessage(plugin, canal, o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // canal non déclaré par le client (pas de mod) : ignoré
        }
    }

    public boolean modde(Player p) { return p.getListeningPluginChannels().contains(MONNAIE); }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player p, byte @NotNull [] message) {
        String msg = new String(message, StandardCharsets.UTF_8).trim();
        if (MONNAIE.equals(channel)) Bukkit.getScheduler().runTask(plugin, () -> monnaie(p, msg));
        else if (BANQUE.equals(channel)) Bukkit.getScheduler().runTask(plugin, () -> module.guichetCanal().recevoir(p, msg));
    }

    private void monnaie(Player p, String msg) {
        String[] a = msg.split(":", 3);
        switch (a[0]) {
            case "poser" -> { long n = nombre(a); if (n > 0) module.bourses().poser(p, n); }
            case "ranger" -> { long n = nombre(a); if (n > 0) module.bourses().ranger(p, n); }
            case "vol" -> { long n = nombre(a); if (n > 0) module.fouille().voler(p, n); }
            case "fouille" -> {
                if (a.length >= 3 && a[1].equals("prendre")) module.fouille().prendre(p, a[2]);
                else if (a.length >= 2 && a[1].equals("fermer")) module.fouille().fermer(p, null);
            }
            default -> { }
        }
    }

    static long nombre(String[] a) {
        if (a.length < 2) return -1;
        return lire(a[1]);
    }

    /** « 1 240 », « 1240 », « 1,2k » → montant ; −1 si illisible. */
    public static long lire(String s) {
        if (s == null) return -1;
        String t = s.trim().toLowerCase(java.util.Locale.ROOT).replace(" ", "").replace(" ", "").replace(" ", "");
        try {
            if (t.endsWith("k")) return Math.round(Double.parseDouble(t.substring(0, t.length() - 1).replace(',', '.')) * 1000);
            if (t.endsWith("m")) return Math.round(Double.parseDouble(t.substring(0, t.length() - 1).replace(',', '.')) * 1_000_000);
            return Long.parseLong(t);
        } catch (NumberFormatException e) {
            return -1;
        }
    }
}
