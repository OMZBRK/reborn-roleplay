package com.reborn.shinobicore.character.command;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.CharacterDisplay;
import com.reborn.shinobicore.character.CharacterEmote;
import com.reborn.shinobicore.character.Clan;
import com.reborn.shinobicore.character.ShinobiCharacter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * {@code /me <action>} — broadcast a roleplay action narrated with the
 * sender's character nickname rather than their Minecraft username. Example:
 *
 * <pre>
 *   /me opens the door
 *   → * Hashirama Senju opens the door          (viewer knows the sender)
 *   → * ???? opens the door                     (viewer doesn't — stranger)
 *   → * Spikey opens the door                   (viewer only knows a nickname)
 * </pre>
 *
 * <h2>Unified colour</h2>
 * The entire /me line is rendered in a single colour so the narration reads
 * as one cohesive thought instead of a ransom-note of mixed tones. The only
 * visual emphasis is that the name+clan portion is <b>bold</b>. Colour is
 * picked from the sender's side (clan colour when known, gray when the
 * viewer only has a nickname or is still a stranger).
 *
 * <h2>Per-viewer rendering</h2>
 * Because the server is an RP environment and characters default to
 * {@code ????} until they've been introduced via {@code /rencontrer}, the
 * narration is sent to each online player individually with the prefix
 * resolved through the viewer's active character's contact book. Console
 * receives the real name.
 *
 * <h2>Bubble</h2>
 * A floating {@link CharacterEmote} bubble appears above the actor showing
 * the same name + action. It groups viewers by the label they see so two
 * strangers get a single shared {@code ????} bubble while someone who knows
 * the sender sees their real name on a separate bubble entity.
 */
public class MeCommand implements CommandExecutor {

    private final ShinobiCore plugin;

    public MeCommand(ShinobiCore plugin) {
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
            p.sendMessage(Component.text("Usage : /me <action>", NamedTextColor.RED));
            return true;
        }

        String action = String.join(" ", args).trim();
        if (action.isEmpty()) {
            p.sendMessage(Component.text("Usage : /me <action>", NamedTextColor.RED));
            return true;
        }

        ShinobiCharacter senderChar = plugin.characters().getActive(p.getUniqueId());

        // ---------- per-viewer chat broadcast ----------
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            ShinobiCharacter viewerChar = plugin.characters().getActive(viewer.getUniqueId());
            Component line = meLineFor(senderChar, p, action, viewerChar, /*forceRealName=*/false);
            viewer.sendMessage(line);
        }
        // Console sees the real name so moderation / logs stay readable.
        Component consoleLine = meLineFor(senderChar, p, action, /*viewerChar=*/null, /*forceRealName=*/true);
        Bukkit.getConsoleSender().sendMessage(consoleLine);

        // ---------- per-viewer floating bubble ----------
        // Group viewers by the text they should see, so each unique label
        // becomes one TextDisplay visible only to that group (and invisible
        // to everyone else via Player.hideEntity).
        Map<String, CharacterEmote.ViewerGroup> groups = new HashMap<>();
        for (Player viewer : Bukkit.getOnlinePlayers()) {
            ShinobiCharacter viewerChar = plugin.characters().getActive(viewer.getUniqueId());
            Component prefix = (senderChar == null)
                    ? Component.text(p.getName(), NamedTextColor.GRAY)
                            .decoration(TextDecoration.BOLD, true)
                            .decoration(TextDecoration.ITALIC, false)
                    : CharacterDisplay.styledNameFor(senderChar, viewerChar);
            NamedTextColor tone = toneFor(senderChar, viewerChar);
            // Bubble body uses the unified tone, italics for narration flavour.
            Component bubble = Component.empty()
                    .append(prefix)
                    .append(Component.text(" " + action, tone)
                            .decoration(TextDecoration.ITALIC, true));

            // Key groups by the plain-text prefix label so two strangers
            // share a single ???? bubble. The action is identical for
            // everyone so it isn't part of the key.
            String labelKey = plainLabel(senderChar, viewerChar, p);
            groups.computeIfAbsent(labelKey, k -> new CharacterEmote.ViewerGroup(bubble))
                    .viewers.add(viewer);
        }
        CharacterEmote.showPerViewer(plugin, p, groups.values(), action.length());
        return true;
    }

    /** Build the full {@code * Name action} line for one viewer. */
    private Component meLineFor(ShinobiCharacter senderChar, Player sender, String action,
                                ShinobiCharacter viewerChar, boolean forceRealName) {
        NamedTextColor tone = forceRealName
                ? Clan.colourFor(senderChar == null ? null : senderChar.clan())
                : toneFor(senderChar, viewerChar);

        Component prefix;
        if (senderChar == null) {
            // No active character (shouldn't happen under lockdown; be
            // defensive). Fall back to the Minecraft username.
            prefix = Component.text(sender.getName(), tone)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false);
        } else if (forceRealName) {
            prefix = CharacterDisplay.styledName(senderChar);
        } else {
            prefix = CharacterDisplay.styledNameFor(senderChar, viewerChar);
        }

        // Leading "* " in the unified tone as well so the whole line reads
        // as one colour — only the name+clan is bold, the rest is italic.
        return Component.empty()
                .append(Component.text("* ", tone)
                        .decoration(TextDecoration.BOLD, true))
                .append(prefix)
                .append(Component.text(" " + action, tone)
                        .decoration(TextDecoration.ITALIC, true));
    }

    /** Pick the single colour for the /me line as seen by this viewer.
     *  <ul>
     *    <li>Viewer knows the real name → clan colour of the sender.</li>
     *    <li>Viewer only knows a nickname → neutral gray (no clan leak).</li>
     *    <li>Viewer is the sender → clan colour.</li>
     *    <li>Stranger viewer → gray (???? matches gray).</li>
     *  </ul>
     */
    private NamedTextColor toneFor(ShinobiCharacter senderChar, ShinobiCharacter viewerChar) {
        if (senderChar == null) return NamedTextColor.GRAY;
        if (viewerChar == null) return Clan.colourFor(senderChar.clan());
        if (viewerChar.id().equals(senderChar.id())) return Clan.colourFor(senderChar.clan());
        ShinobiCharacter.KnownName known = viewerChar.knownNameOf(senderChar.id());
        if (known == null) return NamedTextColor.GRAY;
        return known.isNickname() ? NamedTextColor.GRAY : Clan.colourFor(senderChar.clan());
    }

    /** Plain-text label used to group viewers who see the same bubble. */
    private String plainLabel(ShinobiCharacter senderChar, ShinobiCharacter viewerChar, Player sender) {
        if (senderChar == null) return "mc:" + sender.getName();
        if (viewerChar == null || viewerChar.id().equals(senderChar.id())) {
            return "real:" + CharacterDisplay.realNameString(senderChar);
        }
        ShinobiCharacter.KnownName known = viewerChar.knownNameOf(senderChar.id());
        if (known == null) return "unknown";
        return (known.isNickname() ? "nick:" : "real:") + known.display();
    }
}
