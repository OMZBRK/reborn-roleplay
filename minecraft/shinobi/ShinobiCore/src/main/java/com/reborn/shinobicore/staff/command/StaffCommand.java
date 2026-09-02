package com.reborn.shinobicore.staff.command;

import com.reborn.shinobicore.ShinobiCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * {@code /staff} — opens the staff panel hub. {@code /staff build}
 * toggles builder creative mode. Gated on {@code shinobicore.staff}.
 */
public final class StaffCommand implements CommandExecutor, TabCompleter {

    private final ShinobiCore plugin;

    public StaffCommand(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text(
                    "Cette commande necessite un joueur.", NamedTextColor.RED));
            return true;
        }
        if (!p.hasPermission("shinobicore.staff")) {
            p.sendMessage(Component.text("Permission insuffisante.", NamedTextColor.RED));
            return true;
        }
        if (args.length >= 1 && "build".equalsIgnoreCase(args[0])) {
            if (plugin.staffBuild() != null) plugin.staffBuild().toggle(p);
            return true;
        }
        plugin.coreGui().openStaffPanel(p);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        if (args.length == 1 && sender.hasPermission("shinobicore.staff")) {
            String low = args[0].toLowerCase();
            if ("build".startsWith(low)) return List.of("build");
        }
        return List.of();
    }
}
