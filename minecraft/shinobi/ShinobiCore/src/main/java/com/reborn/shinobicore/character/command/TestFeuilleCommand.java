package com.reborn.shinobicore.character.command;

import com.reborn.shinobicore.ShinobiCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /testfeuille} — ouvre le popup de confirmation du test de la feuille
 * (même flux que le clic droit sur l'item « Test de la Feuille »).
 * {@code /testfeuille give [joueur]} — donne l'item physique.
 *
 * <p>Toute la logique vit dans {@link com.reborn.shinobicore.character.LeafTestManager} ;
 * cette classe n'est que le shim Bukkit.
 */
public class TestFeuilleCommand implements CommandExecutor {

    private final ShinobiCore plugin;

    public TestFeuilleCommand(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (args.length >= 1 && args[0].equalsIgnoreCase("give")) {
            Player target;
            if (args.length >= 2) {
                target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage(Component.text("Joueur introuvable : " + args[1], NamedTextColor.RED));
                    return true;
                }
            } else if (sender instanceof Player p) {
                target = p;
            } else {
                sender.sendMessage(Component.text("Usage : /testfeuille give <joueur>", NamedTextColor.RED));
                return true;
            }
            plugin.leafTest().giveItem(target);
            return true;
        }

        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Réservé aux joueurs.", NamedTextColor.RED));
            return true;
        }
        plugin.leafTest().beginConfirm(p);
        return true;
    }
}
