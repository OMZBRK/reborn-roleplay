package com.reborn.shinobicore.character.command;

import com.reborn.shinobicore.ShinobiCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * {@code /meditation} — toggle meditation for the player. Implementation
 * lives in {@link com.reborn.shinobicore.character.MeditationManager};
 * this class is just the Bukkit command shim.
 */
public class MeditationCommand implements CommandExecutor {

    private final ShinobiCore plugin;

    public MeditationCommand(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Réservé aux joueurs.", NamedTextColor.RED));
            return true;
        }
        plugin.meditation().start(p);
        return true;
    }
}
