package com.reborn.shinobicore.cinematic;

import com.reborn.shinobicore.character.ShinobiCharacter;
import org.bukkit.Location;

import java.util.UUID;

/**
 * Transient per-player playback state for a running {@link Cinematic}:
 * the current anchor index, the per-anchor countdown, the locked viewpoint
 * to re-assert each tick, and the bookkeeping needed to restore the player
 * when the sequence ends. Lives only in memory; never persisted.
 */
final class PlaybackSession {

    final UUID playerId;
    final Cinematic cinematic;
    final boolean isIntro;
    /** Non-null only on the intro path — marked introSeen on completion. */
    final ShinobiCharacter introChar;

    int index;                 // current anchor
    int elapsed;               // ticks since this anchor was entered
    boolean titleShown;        // has the (delayed) title been shown yet
    int textsShown;            // chat lines revealed so far (0..3)
    CinematicAnchor current;   // the anchor currently displayed
    Location lockLocation;     // the viewpoint re-asserted every tick
    org.bukkit.entity.ArmorStand standIn;  // cinematic body left where the player was

    // Snapshot to restore on release.
    Location returnLocation;
    float returnWalkSpeed;
    float returnFlySpeed;
    boolean returnInvulnerable;
    boolean returnInvisible;

    PlaybackSession(UUID playerId, Cinematic cinematic,
                    boolean isIntro, ShinobiCharacter introChar) {
        this.playerId = playerId;
        this.cinematic = cinematic;
        this.isIntro = isIntro;
        this.introChar = introChar;
    }
}
