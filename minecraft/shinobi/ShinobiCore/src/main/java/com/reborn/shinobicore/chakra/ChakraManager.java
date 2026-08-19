package com.reborn.shinobicore.chakra;

import com.reborn.shinobicore.ShinobiCore;
import com.reborn.shinobicore.api.ResourcePool;
import com.reborn.shinobicore.api.ResourceService;
import com.reborn.shinobicore.character.ShinobiCharacter;
import org.bukkit.entity.Player;

/**
 * Provides a {@link #get(Player)} accessor that returns the <em>active
 * character's</em> {@link ChakraPool}, or {@link ChakraPool#EMPTY} when the
 * player hasn't selected a character yet.
 *
 * <p>Chakra does not regenerate automatically any more — the only way to gain
 * chakra back is via the {@code /meditation} command. This manager exists
 * solely as a thin lookup layer so other modules can resolve a player's
 * active pool without reaching into {@code CharacterManager} directly.
 */
public class ChakraManager implements ResourceService {

    private final ShinobiCore plugin;

    public ChakraManager(ShinobiCore plugin) {
        this.plugin = plugin;
        reloadConfig();
    }

    /** Kept for API parity with modules that call it; no-op now. */
    public void reloadConfig() { /* nothing to read — regen removed */ }

    /** Kept for API parity with {@link ShinobiCore}'s boot order. No-op. */
    public void start() { /* no regen task any more */ }

    /** Kept for API parity with {@link ShinobiCore}'s shutdown. No-op. */
    public void stop() { /* no regen task any more */ }

    /** Returns the active character's chakra pool, or {@link ChakraPool#EMPTY}. */
    public ChakraPool get(Player p) {
        var characters = plugin.characters();
        if (characters == null) return ChakraPool.EMPTY;
        ShinobiCharacter c = characters.getActive(p.getUniqueId());
        return c == null ? ChakraPool.EMPTY : c.chakra();
    }

    /* ----------------------------------------------- ResourceService api */

    @Override
    public String resourceId() { return "chakra"; }

    @Override
    public ResourcePool pool(Player player) { return get(player); }
}
