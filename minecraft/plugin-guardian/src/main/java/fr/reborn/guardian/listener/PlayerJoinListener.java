package fr.reborn.guardian.listener;

import fr.reborn.guardian.RebornGuardian;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Listener bidon pour valider que le plugin se charge bien sur Paper.
 * Sera remplace par {@code RebornTokenListener} qui validera le token
 * pousse par le mod Reborn Integrity au moment du AsyncPlayerPreLoginEvent.
 */
public final class PlayerJoinListener implements Listener {

    private final RebornGuardian plugin;

    public PlayerJoinListener(RebornGuardian plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        plugin.getLogger().info(
            "[guardian] " + event.getPlayer().getName() + " a rejoint (token : pas encore valide)."
        );
    }
}
