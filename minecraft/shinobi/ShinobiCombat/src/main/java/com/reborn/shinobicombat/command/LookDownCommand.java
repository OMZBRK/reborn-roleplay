package com.reborn.shinobicombat.command;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Commande {@code /lookdown} — force la caméra du joueur à regarder le plus bas
 * possible (plein sol).
 *
 * <p>Snap ponctuel : on téléporte le joueur sur place en ne changeant que le
 * pitch à {@code +90°} (le pitch va de {@code -90°} plein ciel à {@code +90°}
 * plein sol). On garde x/y/z + yaw actuels, donc le joueur ne bouge pas et
 * conserve son cap horizontal — seule la tête plonge vers le sol.
 *
 * <p>C'est le seul moyen fiable de forcer l'orientation d'un joueur côté Paper :
 * {@code Player#setRotation} se fait écraser par le prochain packet de mouvement
 * du client, alors qu'un téléport envoie un packet que le client applique.
 * L'effet est un snap unique — le joueur peut relever la souris juste après.
 */
public final class LookDownCommand implements CommandExecutor {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(
                    "Commande réservée aux joueurs.", NamedTextColor.RED));
            return true;
        }

        Location loc = player.getLocation();
        loc.setPitch(90f); // plein sol
        player.teleport(loc);
        return true;
    }
}
