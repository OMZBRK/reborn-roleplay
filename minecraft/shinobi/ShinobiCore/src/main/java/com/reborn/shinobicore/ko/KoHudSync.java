package com.reborn.shinobicore.ko;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.ko.ata.AtaManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Pont serveur → mod client pour l'habillage du KO / de l'ATA (lot KO-6).
 *
 * <p>Deux canaux S2C (big-endian, {@code DataOutputStream}) :
 * <ul>
 *   <li>{@value #CHANNEL} — état, envoyé chaque seconde tant qu'il y a quelque chose
 *       à montrer (puis une fois « rien ») : {@code byte phase} (0 aucune, 1 à terre,
 *       2 inconscient), {@code byte cause} (0 PV, 1 chakra), {@code short restant},
 *       {@code short total} (secondes), {@code boolean hôpital proposé},
 *       {@code byte ata} (0, 1 allégée, 2 pleine), {@code float repos} (0..1),
 *       {@code short repos min}, {@code short repos requis min}, {@code boolean au repos},
 *       {@code boolean peur} ;</li>
 *   <li>{@value #EVENT_CHANNEL} — moments forts : {@code byte} (voir constantes).</li>
 * </ul>
 * Contrat miroir de {@code KoPayload} / {@code KoEventPayload} côté mod-hud.
 *
 * <p>Quand le client a le mod ({@link #modded}), le serveur n'envoie plus les titres,
 * barres d'action et barres de boss vanilla : le mod les remplace.
 */
public final class KoHudSync {

    public static final String CHANNEL = "reborn:ko";
    public static final String EVENT_CHANNEL = "reborn:ko_event";

    public static final byte EV_A_TERRE = 1, EV_KO = 2, EV_REVEIL = 3, EV_HOPITAL = 4,
            EV_RETABLI = 5, EV_DEFAITE = 6;

    private final ShinobiCore plugin;
    private final Set<UUID> shown = ConcurrentHashMap.newKeySet();
    private BukkitTask task;

    public KoHudSync(ShinobiCore plugin) {
        this.plugin = plugin;
        var m = Bukkit.getMessenger();
        if (!m.isOutgoingChannelRegistered(plugin, CHANNEL)) m.registerOutgoingPluginChannel(plugin, CHANNEL);
        if (!m.isOutgoingChannelRegistered(plugin, EVENT_CHANNEL)) m.registerOutgoingPluginChannel(plugin, EVENT_CHANNEL);
    }

    public void start() {
        if (task != null) task.cancel();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 10L);
    }

    public void stop() {
        if (task != null) { task.cancel(); task = null; }
    }

    /** Le client affiche-t-il le KO lui-même (mod-hud à jour) ? */
    public boolean modded(Player p) {
        return p != null && p.getListeningPluginChannels().contains(CHANNEL);
    }

    /** Moment fort (titre stylisé côté mod). Renvoie false si le client n'a pas le mod :
     *  l'appelant affiche alors son titre vanilla. */
    public boolean event(Player p, byte kind) {
        if (!modded(p)) return false;
        p.sendPluginMessage(plugin, EVENT_CHANNEL, new byte[] { kind });
        push(p);
        return true;
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) push(p);
    }

    /** Envoie l'état courant (ou « rien » une fois quand il disparaît). */
    public void push(Player p) {
        if (!modded(p)) return;
        UUID id = p.getUniqueId();
        KoState st = plugin.ko().getKo(id);
        ShinobiCharacter c = plugin.characters().getActive(id);
        AtaManager.View ata = c != null && plugin.ata() != null ? plugin.ata().view(c.id()) : null;
        boolean fear = c != null && plugin.ata() != null && plugin.ata().isAfraid(c.id());

        if (st == null && ata == null && !fear) {
            if (shown.remove(id)) send(p, (byte) 0, (byte) 0, 0, 0, false, null, false);
            return;
        }
        shown.add(id);
        byte phase = 0, cause = 0;
        int left = 0, total = 0;
        boolean hospital = false;
        if (st != null) {
            long now = System.currentTimeMillis();
            phase = (byte) (st.isDowned() ? 1 : 2);
            cause = (byte) (st.cause() == KoState.Cause.CHAKRA ? 1 : 0);
            long totalMs = plugin.ko().phaseMillis(st);
            total = (int) (totalMs / 1000L);
            left = (int) Math.max(0, (st.phaseStartMillis() + totalMs - now) / 1000L);
            hospital = st.hospitalOffered();
        }
        send(p, phase, cause, left, total, hospital, ata, fear);
    }

    private void send(Player p, byte phase, byte cause, int left, int total, boolean hospital,
                      AtaManager.View ata, boolean fear) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream(32);
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeByte(phase);
            out.writeByte(cause);
            out.writeShort(Math.min(Short.MAX_VALUE, left));
            out.writeShort(Math.min(Short.MAX_VALUE, total));
            out.writeBoolean(hospital);
            out.writeByte(ata == null ? 0 : ata.level() == AtaManager.Level.PLEINE ? 2 : 1);
            out.writeFloat(ata == null ? 0f : ata.progress());
            out.writeShort(ata == null ? 0 : ata.restMinutes());
            out.writeShort(ata == null ? 0 : ata.requiredMinutes());
            out.writeBoolean(ata != null && ata.resting());
            out.writeBoolean(fear);
            p.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (IOException ignored) {
            // ByteArrayOutputStream ne lève pas.
        }
    }
}
