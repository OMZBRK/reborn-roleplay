package fr.reborn.guardian.listener;

import fr.reborn.guardian.auth.AuthSessionState;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * Au JOIN, planifie un kick differe ; le mod Reborn Integrity a une fenetre
 * pour envoyer son attestation et annuler ce kick.
 *
 * <p>Le delai {@value #KICK_DELAY_TICKS} ticks (= 8s) couvre :
 * <ul>
 *   <li>Le round-trip d'envoi du custom payload (~50ms en LAN, jusqu'a
 *       quelques secondes sur connexion residentielle).</li>
 *   <li>Le temps que le mod ait le ClientPlayConnectionEvents.JOIN fire
 *       (a peu pres immediat apres l'arrivee sur le serveur).</li>
 * </ul>
 *
 * <p>Si le delai expire sans attestation valide, on kick avec un message
 * actionnable pour le joueur.
 */
public final class PendingAuthListener implements Listener {

    /** 20 ticks/sec * 8 = 8s avant kick. */
    private static final long KICK_DELAY_TICKS = 20L * 8L;

    private final Plugin plugin;
    private final AuthSessionState state;

    public PendingAuthListener(Plugin plugin, AuthSessionState state) {
        this.plugin = plugin;
        this.state = state;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        plugin.getLogger().info(String.format(
            "[guardian] %s connecte — attestation attendue dans %ds",
            player.getName(),
            KICK_DELAY_TICKS / 20
        ));
        BukkitTask kickTask = plugin.getServer().getScheduler().runTaskLater(
            plugin,
            () -> {
                if (player.isOnline()) {
                    plugin.getLogger().warning(String.format(
                        "[guardian] kick %s : pas d'attestation recue",
                        player.getName()
                    ));
                    player.kick(Component.text(
                        "Reborn : ton client n'a pas pu s'attester. " +
                            "Utilise le launcher officiel pour rejoindre."
                    ));
                }
                state.forget(player.getUniqueId());
            },
            KICK_DELAY_TICKS
        );
        state.trackPendingKick(player.getUniqueId(), kickTask);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        // Cleanup au quit volontaire : si le joueur se deco avant l'attestation,
        // on libere le task pour eviter qu'il tente de kick un absent.
        state.forget(event.getPlayer().getUniqueId());
    }
}
