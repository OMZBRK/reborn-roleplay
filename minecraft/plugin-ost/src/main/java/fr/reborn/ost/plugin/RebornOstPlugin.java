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

    private OstBroadcaster broadcaster;

    @Override
    public void onEnable() {
        getLogger().info("Reborn OST plugin " + getPluginMeta().getVersion() + " demarre.");

        getServer().getMessenger().registerOutgoingPluginChannel(this, OstChannel.NAME);
        getLogger().info("Canal " + OstChannel.NAME + " enregistre (outgoing).");

        this.broadcaster = new OstBroadcaster(this);
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
            + " — scan zones planifie dans " + JOIN_SCAN_DELAY + " ticks");
        getServer().getScheduler().runTaskLater(this, () -> {
            // Pas de bug si le joueur a redéco entre temps — scanPlayer
            // itère les zones et envoie aux joueurs online (le joueur retire
            // de la liste s'il est offline). Mais on garde la simplicité.
            if (player.isOnline()) {
                int added = broadcaster.scanPlayer(player);
                if (added == 0) {
                    getLogger().info("Scan delayed " + player.getName()
                        + " : aucune zone active dans le rayon.");
                }
            }
        }, JOIN_SCAN_DELAY);
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
