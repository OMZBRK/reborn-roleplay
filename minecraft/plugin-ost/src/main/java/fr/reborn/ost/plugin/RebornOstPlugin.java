package fr.reborn.ost.plugin;

import fr.reborn.ost.plugin.broadcast.OstBroadcaster;
import fr.reborn.ost.plugin.commands.OstCommand;
import fr.reborn.ost.plugin.network.OstChannel;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Reborn OST Plugin — broadcaste les pistes OST aux clients via le
 * canal custom {@code reborn:ost}.
 *
 * <p>Boot :
 * <ol>
 *   <li>Enregistre le canal en outgoing (le client est seul listener).</li>
 *   <li>Enregistre {@link OstCommand} sur la commande {@code /ost}.</li>
 *   <li>Démarre le scheduler Phase 2 (1 Hz) qui scanne les nouvelles
 *       subscriptions aux zones broadcast actives.</li>
 *   <li>Listener PlayerQuitEvent pour purger les subscriptions du joueur.</li>
 * </ol>
 *
 * <p>Compatible avec un serveur Paper VANILLA — pas de dépendance à
 * d'autres plugins ni mods serveur. Les clients sans le mod
 * {@code reborn-ost} reçoivent les plugin messages mais les ignorent
 * (Minecraft handle silencieusement les canaux non-souscrits).
 */
public final class RebornOstPlugin extends JavaPlugin implements Listener {

    /** Période en ticks du scheduler de subscription scan. 20 = 1 Hz. */
    private static final long ZONE_TICK_PERIOD = 20L;

    /** Délai (ticks) après PlayerJoinEvent avant le scan ciblé. À la
     *  reconnexion, le canal réseau client met quelques secondes à être
     *  prêt à recevoir des plugin messages — si on envoie trop tôt, le
     *  packet part dans le vide silencieusement. 60 ticks = 3 s, marge
     *  empirique qui fait passer les late-joins fiables. */
    private static final long JOIN_SCAN_DELAY = 60L;

    /** Tentative rapide à T+1s pour les reconnexions sur bonne connexion.
     *  scanPlayer est idempotent (skip si déjà subscribed) donc inoffensif
     *  si le canal n'est pas encore prêt — le scan à 60 ticks rattrapera. */
    private static final long JOIN_SCAN_FAST_DELAY = 20L;

    private OstBroadcaster broadcaster;

    @Override
    public void onEnable() {
        getLogger().info("Reborn OST plugin " + getPluginMeta().getVersion() + " demarre.");

        getServer().getMessenger().registerOutgoingPluginChannel(this, OstChannel.NAME);
        getLogger().info("Canal " + OstChannel.NAME + " enregistre (outgoing).");

        this.broadcaster = new OstBroadcaster(this);

        // Canal entrant : broadcasts de zone demandés par les joueurs (menu OST
        // solo OFF) + stop/pause owner-only. Anti-abus dans le listener.
        getServer().getMessenger().registerIncomingPluginChannel(this, OstChannel.REQUEST_NAME,
            new fr.reborn.ost.plugin.network.OstRequestListener(broadcaster, getLogger()));
        getLogger().info("Canal " + OstChannel.REQUEST_NAME + " enregistre (incoming).");

        PluginCommand cmd = getCommand("ost");
        if (cmd != null) {
            OstCommand executor = new OstCommand(broadcaster);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        } else {
            getLogger().severe("Commande /ost introuvable — verifier plugin.yml.");
        }

        // Scheduler Phase 2 : scanne les nouveaux entrants dans les zones actives.
        getServer().getScheduler().runTaskTimer(this, broadcaster::tick,
            ZONE_TICK_PERIOD, ZONE_TICK_PERIOD);
        getLogger().info("Scheduler zone-tick demarre (periode " + ZONE_TICK_PERIOD + " ticks).");

        getServer().getPluginManager().registerEvents(this, this);
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (broadcaster == null) return;
        var player = event.getPlayer();
        getLogger().info("Join detecte pour " + player.getName()
            + " — scans planifies dans " + JOIN_SCAN_FAST_DELAY + " et "
            + JOIN_SCAN_DELAY + " ticks");
        // Scan rapide à 1s : si le canal est déjà prêt (bonne connexion,
        // reco rapide), la musique reprend immédiatement.
        getServer().getScheduler().runTaskLater(this,
            () -> tryScan(player, "fast"), JOIN_SCAN_FAST_DELAY);
        // Scan principal à 3s : safety net pour les connexions lentes ou les
        // premiers joins (canal pas ready à 1s).
        getServer().getScheduler().runTaskLater(this,
            () -> tryScan(player, "delayed"), JOIN_SCAN_DELAY);
    }

    private void tryScan(org.bukkit.entity.Player player, String label) {
        if (!player.isOnline()) return;
        int added = broadcaster.scanPlayer(player);
        if (added > 0) {
            getLogger().info("Scan " + label + " " + player.getName()
                + " : " + added + " zone(s) souscrite(s).");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        if (broadcaster != null) {
            broadcaster.registry().onPlayerQuit(event.getPlayer().getUniqueId());
            getLogger().info("Subscriptions purgees pour " + event.getPlayer().getName());
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Reborn OST plugin arrete.");
    }
}
