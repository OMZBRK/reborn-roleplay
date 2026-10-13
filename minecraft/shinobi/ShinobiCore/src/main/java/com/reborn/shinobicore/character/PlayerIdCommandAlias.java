package com.reborn.shinobicore.character;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;

import java.util.Locale;
import java.util.Set;

/**
 * {@code #N°} dans quelques commandes <b>vanilla</b> du staff ({@code /tp #00042}, {@code /kick #00042}…) : le N° est
 * remplacé par le pseudo du joueur qui joue ce personnage, avant que la commande ne soit lue. Staff seulement
 * ({@link PlayerIds#mayUseRef}) ; un N° hors ligne ou inconnu est laissé tel quel (la commande échoue normalement).
 * Les commandes Reborn lisent le {@code #N°} elles-mêmes ({@link PlayerIds}).
 */
public final class PlayerIdCommandAlias implements Listener {

    private static final Set<String> VANILLA = Set.of(
            "tp", "teleport", "spectate", "kick", "gamemode", "clear", "give", "effect", "kill");

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent ev) {
        String msg = ev.getMessage();
        if (msg.indexOf('#') < 0) return;
        Player p = ev.getPlayer();
        String[] parts = msg.split(" ", -1);
        String label = parts[0].startsWith("/") ? parts[0].substring(1) : parts[0];
        label = label.toLowerCase(Locale.ROOT);
        if (label.startsWith("minecraft:")) label = label.substring("minecraft:".length());
        if (!VANILLA.contains(label) || !PlayerIds.mayUseRef(p)) return;
        boolean changed = false;
        for (int i = 1; i < parts.length; i++) {
            if (!PlayerIds.isRef(parts[i])) continue;
            Player t = PlayerIds.onlineByRef(parts[i]);
            if (t == null) continue;
            parts[i] = t.getName();
            changed = true;
        }
        if (changed) ev.setMessage(String.join(" ", parts));
    }
}
