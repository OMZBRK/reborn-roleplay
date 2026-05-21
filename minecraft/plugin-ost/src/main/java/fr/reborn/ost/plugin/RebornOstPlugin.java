package fr.reborn.ost.plugin;

import fr.reborn.ost.plugin.broadcast.OstBroadcaster;
import fr.reborn.ost.plugin.commands.OstCommand;
import fr.reborn.ost.plugin.network.OstChannel;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Reborn OST Plugin — broadcaste les pistes OST aux clients via le
 * canal custom {@code reborn:ost}.
 *
 * <p>Boot :
 * <ol>
 *   <li>Enregistre le canal en outgoing (le client est seul listener).</li>
 *   <li>Enregistre {@link OstCommand} sur la commande {@code /ost}.</li>
 * </ol>
 *
 * <p>Compatible avec un serveur Paper VANILLA — pas de dépendance à
 * d'autres plugins ni mods serveur. Les clients sans le mod
 * {@code reborn-ost} reçoivent les plugin messages mais les ignorent
 * (Minecraft handle silencieusement les canaux non-souscrits).
 */
public final class RebornOstPlugin extends JavaPlugin {

    @Override
    public void onEnable() {
        getLogger().info("Reborn OST plugin " + getPluginMeta().getVersion() + " demarre.");

        getServer().getMessenger().registerOutgoingPluginChannel(this, OstChannel.NAME);
        getLogger().info("Canal " + OstChannel.NAME + " enregistre (outgoing).");

        OstBroadcaster broadcaster = new OstBroadcaster(this);
        PluginCommand cmd = getCommand("ost");
        if (cmd != null) {
            OstCommand executor = new OstCommand(broadcaster);
            cmd.setExecutor(executor);
            cmd.setTabCompleter(executor);
        } else {
            getLogger().severe("Commande /ost introuvable — verifier paper-plugin.yml.");
        }
    }

    @Override
    public void onDisable() {
        getLogger().info("Reborn OST plugin arrete.");
    }
}
