package com.reborn.shinobicore.communication;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.reborn.shinobicore.ShinobiCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.StandardCharsets;

/**
 * Canal {@value #CHANNEL} (les deux sens, JSON UTF-8 brut sans préfixe de longueur), miroir de {@code CommPayload}
 * côté mod : la cloche du village et les volières. Le client demande ({@code {"a": …}}), le serveur répond
 * ({@code {"t": …}}) ; protocole complet : docs/technique/PLUGINS_REFERENCE.md (Cloche et volières).
 *
 * <p>S2C : {@code points} (cloches et volières proches pour le menu R), {@code cloche} (écran de la cloche),
 * {@code compte} (zone · concernés), {@code annonce} (la bande en haut de l'écran), {@code annonce_fin} (le staff a tu
 * la cloche), {@code voliere} (écran de la volière), {@code envoye}, {@code lettre} (la missive ouverte),
 * {@code alerte} (« un corbeau a déposé une missive pour toi », alerte du palais), {@code toast}, {@code ferme}.
 * <br>C2S : {@code ouvrir}, {@code compter}, {@code sonner}, {@code envoyer}, {@code prendre}, {@code transferer},
 * {@code bruler}.
 */
public final class CommCanal implements PluginMessageListener {

    public static final String CHANNEL = "reborn:communication";

    private final ShinobiCore plugin;
    private final CommService svc;

    CommCanal(ShinobiCore plugin, CommService svc) {
        this.plugin = plugin;
        this.svc = svc;
    }

    void start() {
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CHANNEL)) m.registerOutgoingPluginChannel(plugin, CHANNEL);
        if (!m.isIncomingChannelRegistered(plugin, CHANNEL)) m.registerIncomingPluginChannel(plugin, CHANNEL, this);
    }

    public boolean modde(Player p) { return p != null && p.getListeningPluginChannels().contains(CHANNEL); }

    void envoyer(Player p, JsonObject o) {
        if (p == null || !p.isOnline() || !modde(p)) return;
        try {
            p.sendPluginMessage(plugin, CHANNEL, o.toString().getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
            // canal non déclaré par le client : ignoré
        }
    }

    void toast(Player p, String m, boolean refus) {
        if (m == null) return;
        if (!modde(p)) { p.sendMessage((refus ? "§c" : "§6") + "[Corbeau] §f" + m); return; }
        JsonObject o = new JsonObject();
        o.addProperty("t", "toast");
        o.addProperty("m", m);
        if (refus) o.addProperty("refus", true);
        envoyer(p, o);
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player p, byte @NotNull [] message) {
        if (!CHANNEL.equals(channel)) return;
        String raw = new String(message, StandardCharsets.UTF_8);
        if (raw.length() > 16_000) return;
        JsonObject in;
        try {
            JsonElement e = JsonParser.parseString(raw);
            if (!e.isJsonObject()) return;
            in = e.getAsJsonObject();
        } catch (RuntimeException ex) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> svc.recevoir(p, in));
    }

    /* ================================================================ lecture tolérante */

    static String s(JsonObject o, String k) {
        try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : ""; } catch (RuntimeException e) { return ""; }
    }

    static boolean b(JsonObject o, String k) {
        try { return o.has(k) && o.get(k).getAsBoolean(); } catch (RuntimeException e) { return false; }
    }
}
