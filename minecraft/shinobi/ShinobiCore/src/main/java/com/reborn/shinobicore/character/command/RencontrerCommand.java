package com.reborn.shinobicore.character.command;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.RencontrerManager;
import com.reborn.shinobicore.character.ShinobiCharacter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.UUID;

/**
 * {@code /rencontrer} — the meet-and-introduce flow.
 *
 * <p>Main invocation opens the rencontrer root screen. Two hidden
 * subcommands power the save-nickname clickable chat prompt:
 * <ul>
 *   <li>{@code /rencontrer save <token>} — persist the nickname tied to
 *       {@code token} onto the player's active character.</li>
 *   <li>{@code /rencontrer skip <token>} — discard the token with a
 *       short acknowledgement message.</li>
 * </ul>
 *
 * <p>The subcommands are deliberately un-documented in the usage string
 * because they're meant to be triggered by clicking chat components, not
 * typed by hand. The token TTL is enforced by {@link RencontrerManager}
 * so a click on a stale prompt fails gracefully.
 */
public class RencontrerCommand implements CommandExecutor {

    private final ShinobiCore plugin;

    public RencontrerCommand(ShinobiCore plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(Component.text("Réservé aux joueurs.", NamedTextColor.RED));
            return true;
        }
        if (args.length == 0) {
            ShinobiCharacter viewerChar = plugin.characters().getActive(p.getUniqueId());
            plugin.coreGui().openRencontrerRoot(p);
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "save" -> handleSave(p, args);
            case "skip" -> handleSkip(p, args);
            default -> p.sendMessage(Component.text(
                    "Usage : /rencontrer", NamedTextColor.RED));
        }
        return true;
    }

    /* ---------------------------------------------------- hidden subcommands */

    private void handleSave(Player p, String[] args) {
        UUID token = parseToken(p, args);
        if (token == null) return;
        RencontrerManager.SavePrompt prompt = plugin.rencontrer().consumeSavePrompt(token);
        if (prompt == null) {
            p.sendMessage(Component.text("Cette proposition de surnom a expiré.",
                    NamedTextColor.RED));
            return;
        }
        ShinobiCharacter c = plugin.characters()
                .findById(p.getUniqueId(), prompt.characterId())
                .orElse(null);
        if (c == null) {
            // Character was deleted between mint + click. Silent-ish recovery.
            p.sendMessage(Component.text(
                    "Ce personnage n'est plus disponible.", NamedTextColor.RED));
            return;
        }
        c.saveNickname(prompt.nickname());
        plugin.characters().save(c);
        p.sendMessage(Component.text(
                "\"" + prompt.nickname() + "\" ajouté à ta liste de surnoms.",
                NamedTextColor.GREEN));
    }

    private void handleSkip(Player p, String[] args) {
        UUID token = parseToken(p, args);
        if (token == null) return;
        // consumeSavePrompt returns null for expired tokens too — that's fine,
        // a skip click on a stale token should be a no-op either way.
        plugin.rencontrer().consumeSavePrompt(token);
        p.sendMessage(Component.text("Surnom non sauvegardé.", NamedTextColor.GRAY));
    }

    private UUID parseToken(Player p, String[] args) {
        if (args.length < 2) {
            p.sendMessage(Component.text("Jeton manquant.", NamedTextColor.RED));
            return null;
        }
        try {
            return UUID.fromString(args[1]);
        } catch (IllegalArgumentException ex) {
            p.sendMessage(Component.text("Jeton invalide.", NamedTextColor.RED));
            return null;
        }
    }
}
