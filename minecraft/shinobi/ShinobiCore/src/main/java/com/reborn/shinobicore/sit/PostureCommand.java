package com.reborn.shinobicore.sit;

import com.reborn.shinobicore.ShinobiCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Routes the three RP posture commands:
 * <ul>
 *   <li>{@code /assoir}   — sit in place (toggle)</li>
 *   <li>{@code /allonger} — lie down (toggle)</li>
 *   <li>{@code /ramper}   — crawl (toggle)</li>
 * </ul>
 */
public final class PostureCommand implements CommandExecutor {

    private final ShinobiCore plugin;

    public PostureCommand(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Commande réservée aux joueurs.", NamedTextColor.RED));
            return true;
        }
        switch (cmd.getName().toLowerCase()) {
            case "assoir" -> {
                if (plugin.sit().isSeated(p)) {
                    p.leaveVehicle();                       // stand up
                } else if (!plugin.sit().sitInPlace(p)) {
                    p.sendMessage(Component.text("Tu ne peux pas t'asseoir maintenant.", NamedTextColor.GRAY));
                }
            }
            case "allonger" -> plugin.posture().toggleLay(p);
            case "ramper" -> plugin.posture().toggleCrawl(p);
            default -> {
                return false;
            }
        }
        return true;
    }
}
