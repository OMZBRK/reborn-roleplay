package fr.reborn.ost.plugin;

import fr.reborn.ost.plugin.broadcast.OstBroadcaster;
import fr.reborn.ost.plugin.commands.OstCommand;
import fr.reborn.ost.plugin.network.OstChannel;
import org.bukkit.command.PluginCommand;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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
