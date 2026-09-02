package com.reborn.shinobicore.ko.command;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

/**
 * First-person look at your own body.
 *
 * <p>Opens the silhouette GUI in {@link EtatGui.Mode#COARSE}, which
 * collapses every wound to one of three felt sensations
 * (<em>Douleur</em>, <em>Brûlure</em>, <em>Hématome</em>) and a
 * binary intensity (<em>légère</em>, <em>forte</em>). Origins,
 * timestamps, and the precise medical category (Plaie / Os cassé /
 * Infection) stay hidden — those need an Iryō auscultation
 * ({@code /osculter}) to surface.
 *
 * <p>Open to every player (no permission gate). Requires an active
 * character — characters are where injuries live.
 */
public final class EtatCommand implements CommandExecutor {

    private final ShinobiCore plugin;

    public EtatCommand(ShinobiCore plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(Component.text(
                    "Cette commande nécessite un joueur.", NamedTextColor.RED));
            return true;
        }
        ShinobiCharacter own = plugin.characters().getActive(player.getUniqueId());
        if (own == null) {
            player.sendMessage(Component.text(
                    "Sélectionne un personnage avec /character pour consulter ton état.",
                    NamedTextColor.RED));
            return true;
        }
        plugin.coreGui().openEtatSelf(player, own);
        return true;
    }
}
