package fr.reborn.guardian.listener;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.List;

/**
 * Mode fermé : actif quand Guardian ne peut pas vérifier les jetons (secret absent ou invalide,
 * ou {@code config.yml} illisible).
 *
 * <p>Sans secret, aucune attestation ne peut être vérifiée. Plutôt que de laisser entrer tout le
 * monde (ce que faisait l'ancienne désactivation), on refuse toute connexion qui ne vient pas du
 * staff : op, ou une des permissions listées dans {@code closed-mode-staff-permissions}. Le staff
 * entre pour réparer, et reçoit un rappel à chaque connexion.
 *
 * <p>Aucune marque {@code reborn.scope} n'est posée en mode fermé : rien n'a été vérifié.
 */
public final class ClosedModeListener implements Listener {

    /** Message d'expulsion d'un joueur pendant le mode fermé. */
    public static final String KICK_MESSAGE =
        "Reborn : le serveur est fermé pour le moment. Réessaie un peu plus tard.";

    private final Plugin plugin;
    private final List<String> staffPermissions;
    /** Cause du mode fermé, pour le journal et le staff (« secret du jeton de jeu absent ou invalide »). */
    private final String reason;
    /** Ce que le staff doit corriger, affiché à sa connexion. */
    private final String staffAdvice;

    public ClosedModeListener(Plugin plugin, List<String> staffPermissions, String reason, String staffAdvice) {
        this.plugin = plugin;
        this.staffPermissions = List.copyOf(staffPermissions);
        this.reason = reason;
        this.staffAdvice = staffAdvice;
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (isStaff(player)) {
            remind(player);
            return;
        }
        event.joinMessage(null);
        refuse(player);
    }

    /** Applique le mode fermé aux joueurs déjà connectés (rechargement du plugin). */
    public void applyToOnlinePlayers() {
        for (Player player : List.copyOf(plugin.getServer().getOnlinePlayers())) {
            if (isStaff(player)) {
                remind(player);
            } else {
                refuse(player);
            }
        }
    }

    /** Op, ou l'une des permissions staff configurées (groupes LuckPerms). */
    public boolean isStaff(Player player) {
        if (player.isOp()) return true;
        for (String permission : staffPermissions) {
            if (permission != null && !permission.isBlank() && player.hasPermission(permission)) return true;
        }
        return false;
    }

    private void refuse(Player player) {
        plugin.getLogger().warning(String.format(
            "[guardian] MODE FERMÉ : connexion refusée pour %s (%s)",
            player.getName(), reason
        ));
        player.kick(Component.text(KICK_MESSAGE));
    }

    private void remind(Player player) {
        plugin.getLogger().warning(String.format(
            "[guardian] MODE FERMÉ : %s admis (staff), sans vérification de jeton",
            player.getName()
        ));
        player.sendMessage(Component.text(
            "[Guardian] Mode fermé : " + reason + ". Seul le staff peut se connecter. " + staffAdvice,
            NamedTextColor.RED
        ));
    }
}
