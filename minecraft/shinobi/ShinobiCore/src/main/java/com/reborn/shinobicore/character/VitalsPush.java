package com.reborn.shinobicore.character;

import com.reborn.shinobicore.ShinobiCore;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Pousse en DIRECT (~5×/s) la vie/chakra RP du joueur local sur le canal
 * {@code reborn:vitals}, indépendamment du tablist (qui ne se rafraîchit que
 * toutes les 2 s). Le HUD de vitals (mod-hud {@code VitalsFeed}/{@code VitalsHud})
 * lit ce flux en priorité → la barre de vie/chakra bouge en temps réel (dégâts,
 * dépense/régén de chakra), sans attendre le prochain tick tablist.
 *
 * <p>Corps du paquet = 4 entiers bruts {@code hp, maxHp, chakra, maxChakra}
 * (DataOutputStream, big-endian) — contrat miroir de {@code VitalsPayload} côté mod.
 * Envoyé seulement aux joueurs ayant un personnage actif.
 */
public final class VitalsPush {

    public static final String CHANNEL = "reborn:vitals";
    /** ~5 envois/s : assez « live » à l'œil, négligeable en bande passante. */
    private static final long PERIOD_TICKS = 4L;

    private final ShinobiCore plugin;
    private BukkitTask task;

    public VitalsPush(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CHANNEL)) {
            m.registerOutgoingPluginChannel(plugin, CHANNEL);
        }
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, PERIOD_TICKS);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
            if (c == null || c.chakra() == null) continue;
            // VIE : on pousse la vie VANILLA en direct — c.currentHp() n'est écrit qu'aux
            // checkpoints (auto-save 60 s, switch, quit), donc il retarde ; p.getHealth()
            // reflète chaque coup instantanément et vaut la vie RP (maxHp est mappé sur
            // l'attribut MAX_HEALTH par CharacterManager.applyStats). Max = attribut live
            // (suit les buffs éventuels), fallback c.maxHp().
            double maxHp = c.maxHp();
            org.bukkit.attribute.AttributeInstance attr =
                    p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            if (attr != null) maxHp = attr.getValue();
            byte[] body;
            try {
                body = encode(
                        (int) Math.round(p.getHealth()), (int) Math.round(Math.max(1.0, maxHp)),
                        (int) Math.round(c.chakra().current()), (int) Math.round(c.chakra().max()));
            } catch (IOException e) {
                continue;
            }
            try { p.sendPluginMessage(plugin, CHANNEL, body); } catch (Exception ignored) { }
        }
    }

    private static byte[] encode(int hp, int mhp, int ck, int mck) throws IOException {
        ByteArrayOutputStream b = new ByteArrayOutputStream(16);
        try (DataOutputStream out = new DataOutputStream(b)) {
            out.writeInt(hp);
            out.writeInt(mhp);
            out.writeInt(ck);
            out.writeInt(mck);
        }
        return b.toByteArray();
    }
}
