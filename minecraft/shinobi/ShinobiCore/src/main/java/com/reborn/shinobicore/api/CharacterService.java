package com.reborn.shinobicore.api;

import com.reborn.shinobicore.character.ShinobiCharacter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Engine character/account service — the roster of characters per
 * account and the active-character selection.
 *
 * <p>Resolve via {@code Bukkit.getServicesManager().load(CharacterService.class)}.
 * The surface is limited to what dependents actually consume; ask
 * before widening it.
 */
@Stable
public interface CharacterService {

    /**
     * Owner + character pair, for staff flows that target a character
     * by name or id across every loaded roster (characters are named;
     * players are not the addressing unit).
     */
    record ResolvedCharacter(UUID ownerId, ShinobiCharacter character) {}

    /** The account's currently active character, or null. */
    ShinobiCharacter getActive(UUID owner);

    /** Live view of every loaded roster, keyed by owner account id. */
    Map<UUID, List<ShinobiCharacter>> rosterView();

    /** Find a character by name across all loaded rosters (case-insensitive), or null. */
    ResolvedCharacter resolveOwned(String name);

    /** Find a character by its id across all loaded rosters, or null. */
    ResolvedCharacter resolveOwnedById(UUID characterId);

    /** Names of every loaded character, alphabetical, deduplicated. */
    List<String> allCharacterNames();

    /** The online player whose ACTIVE character has this name, or null. */
    Player findOnlinePlayerByCharacter(String name);

    /** Persist a character through the engine's storage backend. */
    void save(ShinobiCharacter character);
}
