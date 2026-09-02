package com.reborn.shinobicore.character.repository;

import com.reborn.shinobicore.character.ShinobiCharacter;

import java.util.List;
import java.util.UUID;

/**
 * Abstract persistence for characters. Two implementations are planned:
 *
 * <ol>
 *   <li>{@link YamlCharacterRepository} — YAML file per owner UUID (current).</li>
 *   <li>SqlCharacterRepository — MySQL/MariaDB (future).</li>
 * </ol>
 *
 * Implementations should be safe to call from the server thread. If IO
 * becomes a bottleneck the call sites can be moved to the async scheduler
 * without touching this interface.
 */
public interface CharacterRepository {

    /** Called once in onEnable. Create tables, data directories, etc. */
    void init();

    /** Called in onDisable. Flush buffers, close connections. */
    void close();

    /** Load every character belonging to {@code owner}. Must not return null. */
    List<ShinobiCharacter> loadAll(UUID owner);

    /** Create-or-update the given character. */
    void save(ShinobiCharacter character);

    /** Remove a character by id. */
    void delete(UUID owner, UUID characterId);
}
