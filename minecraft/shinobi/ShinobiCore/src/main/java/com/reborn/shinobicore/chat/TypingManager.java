package com.reborn.shinobicore.chat;

import com.reborn.shinobicore.ShinobiCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Indicateur de frappe : le mod client (mod-hud) envoie {@code "1"}/{@code "0"}
 * sur {@code reborn:typing} quand un joueur ouvre/ferme le chat. On tient à jour
 * l'ensemble des joueurs en train d'écrire et on le rediffuse à tous
 * ({@code {"typing":["uuid",...]}}) — le mod dessine alors 3 points au-dessus de
 * la tête des joueurs proches concernés (la portée est gérée côté client par le
 * tracking d'entités : un joueur distant non chargé n'a pas de bulle).
 */
public class TypingManager implements Listener, PluginMessageListener {

    public static final String CHANNEL = "reborn:typing";

    private final ShinobiCore plugin;
    private final Set<UUID> typing = ConcurrentHashMap.newKeySet();

    public TypingManager(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CHANNEL)) {
            m.registerOutgoingPluginChannel(plugin, CHANNEL);
        }
        if (!m.isIncomingChannelRegistered(plugin, CHANNEL)) {
            m.registerIncomingPluginChannel(plugin, CHANNEL, this);
        }
    }

    @Override
    public void onPluginMessageReceived(String channel, Player p, byte[] message) {
        if (!CHANNEL.equals(channel)) return;
        String msg = new String(message, StandardCharsets.UTF_8).trim();
        boolean changed;
        if (msg.equals("1")) {
            changed = typing.add(p.getUniqueId());
        } else {
            changed = typing.remove(p.getUniqueId());
        }
        if (changed) broadcast();
    }

    /** Diffuse l'ensemble des joueurs en train d'écrire à tous les joueurs en ligne. */
    private void broadcast() {
        StringBuilder sb = new StringBuilder("{\"typing\":[");
        boolean first = true;
        for (UUID id : typing) {
            // Ne garde que les joueurs encore en ligne.
            if (Bukkit.getPlayer(id) == null) continue;
            if (!first) sb.append(',');
            sb.append('"').append(id).append('"');
            first = false;
        }
        sb.append("]}");
        byte[] bytes = sb.toString().getBytes(StandardCharsets.UTF_8);
        for (Player p : Bukkit.getOnlinePlayers()) {
            try {
                p.sendPluginMessage(plugin, CHANNEL, bytes);
            } catch (Exception ignored) {
                // canal non enregistré côté client (pas de mod) → ignoré.
            }
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        if (typing.remove(e.getPlayer().getUniqueId())) {
            // Rediffuse après le départ (au tick suivant, le joueur est hors ligne).
            Bukkit.getScheduler().runTask(plugin, this::broadcast);
        }
    }
}
