package com.reborn.shinobiabilities.command;

import com.reborn.shinobiabilities.gui.GuiRouter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/** {@code /techniques} — direct shortcut to « Mes Techniques » (also
 *  reachable through /menu). */
public final class TechniquesCommand implements CommandExecutor {

    private final GuiRouter router;

    public TechniquesCommand(GuiRouter router) {
        this.router = router;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Commande joueur uniquement.", NamedTextColor.RED));
            return true;
        }
        router.openKnown(p);
        return true;
    }
}
