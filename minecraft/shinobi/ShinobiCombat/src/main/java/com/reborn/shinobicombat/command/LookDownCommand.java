package com.reborn.shinobicombat.command;

import com.reborn.shinobicombat.net.CombatChannel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Commande {@code /lookdown} — le joueur regarde le plus bas possible (plein sol).
 *
 * <p>Double effet, car le pitch du regard est géré différemment selon la vue :
 * <ol>
 *   <li><b>Serveur (téléport)</b> : on téléporte le joueur sur place en ne
 *       changeant que le pitch à {@code +90°} (le pitch va de {@code -90°} plein
 *       ciel à {@code +90°} plein sol), x/y/z + yaw conservés. Ça oriente
 *       l'entité — donc la <b>1ère personne</b>, la <b>vue vanilla</b> et ce que
 *       voient les <b>autres joueurs</b> plongent vers le sol. Seul moyen fiable
 *       côté Paper ({@code setRotation} se fait écraser au packet suivant).</li>
 *   <li><b>Client (canal {@code reborn:combat})</b> : en <b>vue épaule Reborn</b>,
 *       le pitch de la tête est asservi à l'orbite caméra du mod (client-local),
 *       qui ignore le pitch de l'entité. On envoie donc {@link
 *       CombatChannel#TYPE_LOOK_DOWN} pour que le mod incline sa caméra plein bas.
 *       Sans effet si le joueur n'a pas le mod / n'est pas en vue épaule.</li>
 * </ol>
 * L'effet est un snap unique — le joueur relève en bougeant la souris.
 */
public final class LookDownCommand implements CommandExecutor {

    private final Plugin plugin;

    public LookDownCommand(Plugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(
                    "Commande réservée aux joueurs.", NamedTextColor.RED));
            return true;
        }

        // Serveur : oriente l'entité (1ère personne, vue vanilla, rendu chez les autres).
        Location loc = player.getLocation();
        loc.setPitch(90f); // plein sol
        player.teleport(loc);

        // Client : incline la caméra épaule Reborn (asservie à son orbite, pas à l'entité).
        CombatChannel.sendLookDown(plugin, player);
        return true;
    }
}
