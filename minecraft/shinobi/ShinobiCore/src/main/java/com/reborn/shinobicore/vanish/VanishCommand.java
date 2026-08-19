package com.reborn.shinobicore.vanish;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /vanish} — open the vanish chooser for yourself; {@code /vanish
 * <personnage>} — configure another connected character's vanish. Perm
 * {@code shinobicore.vanish}.
 */
public final class VanishCommand implements CommandExecutor, TabCompleter {

    private static final String PERM = "shinobicore.vanish";

    private final ShinobiCore plugin;

    public VanishCommand(ShinobiCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(PERM)) {
            sender.sendMessage(Component.text("Permission insuffisante.", NamedTextColor.RED));
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player p)) {
                sender.sendMessage(Component.text("Usage : /vanish <personnage>", NamedTextColor.GRAY));
                return true;
            }
            new VanishGui(plugin, p.getUniqueId()).open(p);
            return true;
        }

        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Réservé aux joueurs.", NamedTextColor.RED));
            return true;
        }
        Player target = plugin.characters().findOnlinePlayerByCharacter(args[0]);
        if (target == null) {
            sender.sendMessage(Component.text("Personnage introuvable ou hors ligne : " + args[0],
                    NamedTextColor.RED));
            return true;
        }
        new VanishGui(plugin, target.getUniqueId()).open(p);
        return true;
    }

    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command cmd,
                                      @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission(PERM) || args.length != 1) return List.of();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
            if (c != null && c.name().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(c.name());
        }
        return out;
    }
}
