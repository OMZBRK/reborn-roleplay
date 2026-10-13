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

    /**
     * Player ID — N° court du personnage (0, 1, 2… affiché « 00042 »), -1 si non attribué. Réservé au joueur
     * lui-même et au staff : ne jamais l'envoyer aux autres joueurs. Default for older cores.
     */
    default int getPlayerId(ShinobiCharacter character) {
        return character == null ? -1 : character.playerId();
    }

    /**
     * Personnage par N°, même hors ligne (lu via l'index player-ids.yml puis le fichier du propriétaire),
     * ou null si le N° est inconnu ou le personnage supprimé. Default null for older cores.
     */
    default ResolvedCharacter findByPlayerId(int number) { return null; }

    /** Persist a character through the engine's storage backend. */
    void save(ShinobiCharacter character);
}
