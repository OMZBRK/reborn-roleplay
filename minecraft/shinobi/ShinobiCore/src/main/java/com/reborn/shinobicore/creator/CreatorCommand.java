package com.reborn.shinobicore.creator;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * {@code /creator reload} — recharge les assets du character creator déposés dans
 * {@code plugins/ShinobiCore/creator-assets/} et les re-pousse aux joueurs connectés
 * (sans redémarrage). Réservé au staff ({@code shinobicore.creator.admin}) — mêmes
 * grades que le panel Fichiers (Modélisateur/Développeur).
 */
public final class CreatorCommand implements CommandExecutor, TabCompleter {

    private final CreatorAssetManager manager;

    public CreatorCommand(CreatorAssetManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("shinobicore.creator.admin")) {
            sender.sendMessage(Component.text("Permission refusée.", NamedTextColor.RED));
            return true;
        }
        if (args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            manager.reload();
            sender.sendMessage(Component.text("Assets du creator rechargés + repoussés aux joueurs.",
                    NamedTextColor.GREEN));
            return true;
        }
        sender.sendMessage(Component.text("Usage : /creator reload", NamedTextColor.YELLOW));
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String label, @NotNull String[] args) {
        return args.length == 1 ? List.of("reload") : List.of();
    }
}
