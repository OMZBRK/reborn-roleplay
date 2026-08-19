package com.reborn.shinobiabilities.command;

import com.reborn.shinobiabilities.gui.GuiRouter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** {@code /menu} — the Shinobi hub: techniques, catalogue, liaisons,
 *  mobilité, encyclopédie, et les panneaux staff/admin. */
public final class MenuCommand implements CommandExecutor {

    private final GuiRouter router;

    public MenuCommand(GuiRouter router) {
        this.router = router;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Commande joueur uniquement.", NamedTextColor.RED));
            return true;
        }
        router.openHub(p);
        return true;
    }
}
