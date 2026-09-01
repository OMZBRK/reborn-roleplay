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
            CreatorAssetManager.ReloadResult res = manager.reload();
            sender.sendMessage(Component.text(
                    res.loaded() + " asset(s) chargé(s), " + res.skipped() + " ignoré(s) — repoussés aux joueurs.",
                    res.loaded() > 0 || res.skipped() == 0 ? NamedTextColor.GREEN : NamedTextColor.YELLOW));
            // Remonte les premières erreurs (id/PNG manquant…) → plus de « 0 chargé »
            // silencieux qui fait croire à un besoin de redémarrage.
            int shown = 0;
            for (String err : res.errors()) {
                if (shown++ >= 5) {
                    sender.sendMessage(Component.text("  … (+" + (res.errors().size() - 5) + " autre(s))",
                            NamedTextColor.GRAY));
                    break;
                }
                sender.sendMessage(Component.text("  ⚠ " + err, NamedTextColor.RED));
            }
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
