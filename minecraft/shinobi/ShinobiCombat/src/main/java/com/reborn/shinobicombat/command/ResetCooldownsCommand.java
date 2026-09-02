package com.reborn.shinobicombat.command;

import com.reborn.shinobicombat.combat.CombatListener;
import com.reborn.shinobicombat.net.CombatChannel;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Commande staff {@code /resetcd [player]} — remet à zéro les cooldowns
 * d'aptitudes (dash, saut chakra) d'un joueur pour le test dev.
 *
 * <p>Sans argument, la cible est l'émetteur (doit être un joueur). Avec un nom,
 * la cible est ce joueur (doit être en ligne). L'effet est double :
 * <ol>
 *   <li>vide les maps de cooldown côté serveur via
 *       {@link CombatListener#resetCooldowns};</li>
 *   <li>notifie le client de la cible d'effacer ses propres cooldowns/gates HUD
 *       via l'octet S2C {@link CombatChannel#TYPE_COOLDOWN_RESET}.</li>
 * </ol>
 * Réservée au staff (permission {@code shinobicombat.resetcd}, op par défaut).
 */
public final class ResetCooldownsCommand implements CommandExecutor {

    private final Plugin plugin;
    private final CombatListener combat;

    public ResetCooldownsCommand(Plugin plugin, CombatListener combat) {
        this.plugin = plugin;
        this.combat = combat;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        Player target;
        if (args.length == 0) {
            if (!(sender instanceof Player self)) {
                sender.sendMessage(Component.text(
                        "Console : précise un joueur — /resetcd <joueur>", NamedTextColor.RED));
                return true;
            }
            target = self;
        } else {
            target = Bukkit.getPlayerExact(args[0]);
            if (target == null) {
                sender.sendMessage(Component.text(
                        "Joueur introuvable ou hors ligne : " + args[0], NamedTextColor.RED));
                return true;
            }
        }

        // Serveur : vide les cooldowns d'aptitudes de la cible.
        combat.resetCooldowns(target.getUniqueId());
        // Client de la cible : efface ses cooldowns/gates HUD.
        CombatChannel.sendCooldownReset(plugin, target);

        sender.sendMessage(Component.text(
                "Cooldowns réinitialisés pour " + target.getName() + ".", NamedTextColor.GREEN));
        return true;
    }
}
