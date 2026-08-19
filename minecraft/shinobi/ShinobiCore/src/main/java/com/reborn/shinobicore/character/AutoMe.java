package com.reborn.shinobicore.character;

import com.reborn.shinobicore.ShinobiCore;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

/**
 * Reusable broadcaster for "system-emitted" {@code /me}-style RP
 * lines — used when a gameplay action implies an in-character
 * gesture nobody had to type for it (opening an État GUI, picking
 * an item out of a body, applying medicine).
 *
 * <p>Mirrors the chat half of {@link com.reborn.shinobicore.character.command.MeCommand}
 * (per-viewer name resolution, single tone per line, stranger →
 * gray ???? rendering) but skips the floating bubble — those
 * automatic narrations would be too dense visually if every osculter
 * spawned a bubble.
 *
 * <p>Convention: the {@code action} string starts with a verb and
 * fills in any target name as plain text. The actor's display name
 * is resolved per viewer the same way {@code /me} does, so a
 * stranger sees {@code * ???? examine attentivement Aiko Uchiha}
 * while a known acquaintance sees {@code * Hashirama Senju examine
 * attentivement Aiko Uchiha}.
 */
public final class AutoMe {

    private AutoMe() {}

    /** Broadcast {@code action} attributed to {@code actor}. Console
     *  receives the canonical real-name version for moderation logs.
     *  Safe to call when {@code actor} has no active character — the
     *  line falls back to the Mojang username. */
    public static void broadcast(ShinobiCore plugin, Player actor, String action) {
        if (action == null || action.isBlank()) return;
        ShinobiCharacter senderChar = plugin.characters().getActive(actor.getUniqueId());

        for (Player viewer : Bukkit.getOnlinePlayers()) {
            ShinobiCharacter viewerChar = plugin.characters().getActive(viewer.getUniqueId());
            viewer.sendMessage(line(senderChar, actor, action, viewerChar, false));
        }
        Bukkit.getConsoleSender().sendMessage(
                line(senderChar, actor, action, null, true));
    }

    /* --------------------------------------------------- internals */

    /** Build the {@code * Name action} component for one viewer. */
    private static Component line(ShinobiCharacter senderChar, Player sender,
                                  String action, ShinobiCharacter viewerChar,
                                  boolean forceRealName) {
        NamedTextColor tone = forceRealName
                ? Clan.colourFor(senderChar == null ? null : senderChar.clan())
                : toneFor(senderChar, viewerChar);

        Component prefix;
        if (senderChar == null) {
            prefix = Component.text(sender.getName(), tone)
                    .decoration(TextDecoration.BOLD, true)
                    .decoration(TextDecoration.ITALIC, false);
        } else if (forceRealName) {
            prefix = CharacterDisplay.styledName(senderChar);
        } else {
            prefix = CharacterDisplay.styledNameFor(senderChar, viewerChar);
        }

        return Component.empty()
                .append(Component.text("* ", tone)
                        .decoration(TextDecoration.BOLD, true))
                .append(prefix)
                .append(Component.text(" " + action, tone)
                        .decoration(TextDecoration.ITALIC, true));
    }

    /** Same colour-picking rule as {@code MeCommand} — clan colour
     *  when the viewer knows the real identity, gray for nicknames
     *  / strangers. */
    private static NamedTextColor toneFor(ShinobiCharacter senderChar,
                                          ShinobiCharacter viewerChar) {
        if (senderChar == null) return NamedTextColor.GRAY;
        if (viewerChar == null) return Clan.colourFor(senderChar.clan());
        if (viewerChar.id().equals(senderChar.id())) {
            return Clan.colourFor(senderChar.clan());
        }
        ShinobiCharacter.KnownName known = viewerChar.knownNameOf(senderChar.id());
        if (known == null) return NamedTextColor.GRAY;
        return known.isNickname() ? NamedTextColor.GRAY
                : Clan.colourFor(senderChar.clan());
    }
}
