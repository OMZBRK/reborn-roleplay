package fr.reborn.ost.plugin.network;

import fr.reborn.ost.plugin.broadcast.OstBroadcaster;
import fr.reborn.ost.plugin.broadcast.OstZoneRegistry;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Écoute le canal {@code reborn:ost_request} (client → serveur) : un joueur
 * demande de diffuser / stopper / mettre en pause un son de zone via le menu
 * OST (solo OFF).
 *
 * <p>Anti-abus :
 * <ul>
 *   <li><b>cooldown</b> par joueur sur les demandes de PLAY (spam de sons) ;</li>
 *   <li><b>cap de rayon</b> ({@link #MAX_RADIUS}) — un client ne peut pas
 *       forcer un rayon géant ;</li>
 *   <li><b>owner-only</b> : stop/pause n'agissent que sur les zones dont le
 *       joueur est propriétaire (les auditeurs, eux, coupent juste chez eux).</li>
 * </ul>
 *
 * <p>Les plugin messages sont délivrés sur le thread principal (Paper), donc
 * les appels Bukkit du broadcaster sont sûrs ici.
 */
public final class OstRequestListener implements PluginMessageListener {

    /** Rayon max qu'un joueur peut imposer (blocs). */
    private static final float MAX_RADIUS = 64f;
    /** Délai mini entre deux PLAY d'un même joueur (anti-spam). */
    private static final long PLAY_COOLDOWN_MS = 2000L;

    private final OstBroadcaster broadcaster;
    private final Logger log;
    private final Map<UUID, Long> lastPlay = new ConcurrentHashMap<>();

    public OstRequestListener(OstBroadcaster broadcaster, Logger log) {
        this.broadcaster = broadcaster;
        this.log = log;
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!OstChannel.REQUEST_NAME.equals(channel)) return;
        OstRequest req;
        try {
            req = OstRequest.parse(message);
        } catch (Exception e) {
            log.warning("ost-request illisible de " + player.getName() + " : " + e.getMessage());
            return;
        }

        switch (req) {
            case OstRequest.Play p -> handlePlay(player, p);
            case OstRequest.Stop ignored -> {
                int n = 0;
                for (OstZoneRegistry.ZoneRecord z : broadcaster.registry().zonesOwnedBy(player.getUniqueId())) {
                    n += broadcaster.stopZone(z);
                }
                log.info("ost stop de " + player.getName() + " (" + n + " auditeur(s))");
            }
            case OstRequest.Pause p -> {
                for (OstZoneRegistry.ZoneRecord z : broadcaster.registry().zonesOwnedBy(player.getUniqueId())) {
                    broadcaster.pauseZone(z, p.paused());
                }
                log.info("ost pause=" + p.paused() + " de " + player.getName());
            }
        }
    }

    private void handlePlay(Player player, OstRequest.Play p) {
        long now = System.currentTimeMillis();
        Long last = lastPlay.get(player.getUniqueId());
        if (last != null && now - last < PLAY_COOLDOWN_MS) return; // anti-spam
        lastPlay.put(player.getUniqueId(), now);

        float radius = Math.max(1f, Math.min(MAX_RADIUS, p.radius()));
        float volume = Math.max(0f, Math.min(1f, p.volume()));

        // Une seule zone active par owner : on remplace le broadcast précédent.
        // On exclut le joueur du StopBroadcast (il joue déjà la nouvelle piste
        // en local ; le stopper le couperait / le ferait sauter à la suivante).
        for (OstZoneRegistry.ZoneRecord z : broadcaster.registry().zonesOwnedBy(player.getUniqueId())) {
            broadcaster.stopZone(z, player.getUniqueId());
        }
        int n = broadcaster.playAtPosition(player.getLocation(), radius, p.trackId(), volume, player.getUniqueId());
        log.info("ost broadcast de " + player.getName() + " track=" + p.trackId()
            + " r=" + radius + " → " + n + " auditeur(s)");
    }
}
