package com.reborn.shinobiabilities.mobility;

import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;

/**
 * Relais de synchro d'animation de mouvement — canal {@code reborn:anim}.
 *
 * <p>Le mod client envoie (C2S) son état d'anim (state + style de marche) quand il
 * change. Ce relais le rediffuse (S2C) aux joueurs <b>proches</b> (≤ {@value #RANGE}
 * blocs, même monde) pour que tout le monde voie les démarches / la course / le
 * naruto-run des autres. On réécrit l'UUID avec celui de l'émetteur RÉEL (le champ
 * client est ignoré → pas d'usurpation).
 *
 * <p>Format (miroir de {@code AnimSyncPayload} côté mod) : UUID (2 longs) + state
 * (1 o) + walkStyle (1 o). Plugin-messages = main thread → Bukkit direct.
 *
 * <p>Enregistrement dans {@code ShinobiAbilities.onEnable} :
 * <pre>{@code
 * getServer().getMessenger().registerOutgoingPluginChannel(this, AnimRelayListener.CHANNEL);
 * getServer().getMessenger().registerIncomingPluginChannel(this, AnimRelayListener.CHANNEL,
 *     new AnimRelayListener(this));
 * }</pre>
 */
public final class AnimRelayListener implements PluginMessageListener {

    public static final String CHANNEL = "reborn:anim";
    private static final double RANGE = 48.0;

    private final Plugin plugin;

    public AnimRelayListener(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void onPluginMessageReceived(@NotNull String channel, @NotNull Player sender, byte[] message) {
        if (!CHANNEL.equals(channel)) return;

        byte state, walk;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            in.readLong();
            in.readLong();          // UUID client — ignoré (on prend l'émetteur réel)
            state = in.readByte();
            walk = in.readByte();
        } catch (Exception e) {
            return;
        }

        byte[] out;
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream(18);
             DataOutputStream dos = new DataOutputStream(bos)) {
            dos.writeLong(sender.getUniqueId().getMostSignificantBits());
            dos.writeLong(sender.getUniqueId().getLeastSignificantBits());
            dos.writeByte(state);
            dos.writeByte(walk);
            out = bos.toByteArray();
        } catch (Exception e) {
            return;
        }

        double r2 = RANGE * RANGE;
        for (Player p : sender.getWorld().getPlayers()) {
            if (p.equals(sender)) continue;
            if (p.getLocation().distanceSquared(sender.getLocation()) > r2) continue;
            try {
                p.sendPluginMessage(plugin, CHANNEL, out);
            } catch (Exception ignore) {
                // canal non enregistré côté récepteur / joueur parti : cosmétique.
            }
        }
    }
}
