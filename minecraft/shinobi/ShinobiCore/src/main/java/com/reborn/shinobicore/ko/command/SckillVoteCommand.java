package com.reborn.shinobicore.ko.command;

import com.reborn.shinobicore.ShinobiCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * Internal click-handler command for the Tuer staff approval flow.
 *
 * <p>Invoked exclusively from the {@code [Valider]} / {@code [Refuser]}
 * clickable components emitted by {@link com.reborn.shinobicore.ko.action.KillRequestManager}.
 * Players cannot type this command directly — it requires the
 * {@code shinobicore.staff} permission AND the request id is only
 * valid until decided / timed out.
 *
 * <p>Usage (from clicks):
 * <pre>
 *   /sckillvote approve &lt;requestId&gt;
 *   /sckillvote refuse  &lt;requestId&gt;
 * </pre>
 */
public final class SckillVoteCommand implements CommandExecutor {

    private final ShinobiCore plugin;

    public SckillVoteCommand(ShinobiCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player staff)) {
            sender.sendMessage(Component.text(
                    "Cette commande nécessite un joueur.", NamedTextColor.RED));
            return true;
        }
        if (!staff.hasPermission("shinobicore.staff")) {
            staff.sendMessage(Component.text(
                    "Permission insuffisante.", NamedTextColor.RED));
            return true;
        }
        if (args.length < 2) {
            staff.sendMessage(Component.text(
                    "Usage : /sckillvote approve|refuse <id>", NamedTextColor.GRAY));
            return true;
        }
        long id;
        try { id = Long.parseLong(args[1]); }
        catch (NumberFormatException ex) {
            staff.sendMessage(Component.text(
                    "Identifiant invalide.", NamedTextColor.RED));
            return true;
        }
        switch (args[0].toLowerCase()) {
            case "approve" -> plugin.killRequests().approve(staff, id);
            case "refuse"  -> plugin.killRequests().refuse(staff, id);
            default -> staff.sendMessage(Component.text(
                    "Action inconnue. approve|refuse.", NamedTextColor.RED));
        }
        return true;
    }
}
