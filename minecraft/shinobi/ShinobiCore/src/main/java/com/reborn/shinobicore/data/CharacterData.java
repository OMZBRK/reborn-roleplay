package com.reborn.shinobicore.data;

import java.util.UUID;

/**
 * Minimal contract a per-character persisted record must satisfy to be
 * managed by {@link YamlCharacterDataStore}: an identity (the ShinobiCore
 * character UUID) and a dirty flag so the store only writes what changed.
 */
public interface CharacterData {

    /** The ShinobiCore character this record belongs to. */
    UUID characterId();

    /** True when the record has unsaved changes. */
    boolean dirty();

    /** Clear the dirty flag (called by the store after a successful save). */
    void markClean();
}
