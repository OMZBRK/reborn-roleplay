package com.reborn.shinobicore.util;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.character.ShinobiCharacter;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.entity.Player;

/**
 * Active-character resolution helpers. Replace this boilerplate
 * (~60 occurrences across the codebase before extraction):
 * <pre>{@code
 *   ShinobiCharacter c = plugin.characters().getActive(p.getUniqueId());
 *   if (c == null) {
 *       p.sendMessage(Component.text("Aucun personnage actif.",
 *               NamedTextColor.RED));
 *       return;
 *   }
 * }</pre>
 *
 * <p>...with one of:
 * <pre>{@code
 *   ShinobiCharacter c = Players.activeOrWarn(plugin, p);
 *   if (c == null) return;
 * }</pre>
 * or, when a custom warning is needed:
 * <pre>{@code
 *   ShinobiCharacter c = Players.activeOrWarn(plugin, p,
 *           "Sélectionne un personnage avec /character.");
 *   if (c == null) return;
 * }</pre>
 *
 * <p>Both variants return {@code null} when no character is active
 * so callers can early-return cleanly. The plain {@link #active}
 * variant is for paths that handle {@code null} themselves.
 */
public final class Players {

    /** Default French-language warning shown by {@link #activeOrWarn}. */
    public static final String DEFAULT_NO_CHARACTER_MSG =
            "Aucun personnage actif.";

    private Players() {}

    /** Look up the active character without sending any message. */
    public static ShinobiCharacter active(ShinobiCore plugin, Player p) {
        if (p == null || plugin.characters() == null) return null;
        return plugin.characters().getActive(p.getUniqueId());
    }

    /** Look up the active character; if none, send the default
     *  "Aucun personnage actif." message in red and return null. */
    public static ShinobiCharacter activeOrWarn(ShinobiCore plugin, Player p) {
        return activeOrWarn(plugin, p, DEFAULT_NO_CHARACTER_MSG);
    }

    /** Look up the active character; if none, send {@code warning}
     *  in red and return null. */
    public static ShinobiCharacter activeOrWarn(ShinobiCore plugin,
                                                Player p, String warning) {
        ShinobiCharacter c = active(plugin, p);
        if (c == null && p != null) {
            p.sendMessage(Component.text(warning, NamedTextColor.RED));
        }
        return c;
    }

    /** True when the player has any active character. */
    public static boolean hasActive(ShinobiCore plugin, Player p) {
        return active(plugin, p) != null;
    }

    /* ---------------------------------------------- api-seam overloads
     * Same helpers over the CharacterService interface, for dependents
     * that no longer hold the concrete plugin (contract §5). */

    /** Look up the active character without sending any message. */
    public static ShinobiCharacter active(
            com.reborn.shinobicore.api.CharacterService characters, Player p) {
        if (p == null || characters == null) return null;
        return characters.getActive(p.getUniqueId());
    }

    /** Look up the active character; if none, send the default
     *  "Aucun personnage actif." message in red and return null. */
    public static ShinobiCharacter activeOrWarn(
            com.reborn.shinobicore.api.CharacterService characters, Player p) {
        return activeOrWarn(characters, p, DEFAULT_NO_CHARACTER_MSG);
    }

    /** Look up the active character; if none, send {@code warning}
     *  in red and return null. */
    public static ShinobiCharacter activeOrWarn(
            com.reborn.shinobicore.api.CharacterService characters,
            Player p, String warning) {
        ShinobiCharacter c = active(characters, p);
        if (c == null && p != null) {
            p.sendMessage(Component.text(warning, NamedTextColor.RED));
        }
        return c;
    }

    /** True when the player has any active character. */
    public static boolean hasActive(
            com.reborn.shinobicore.api.CharacterService characters, Player p) {
        return active(characters, p) != null;
    }
}
