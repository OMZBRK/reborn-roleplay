package com.reborn.shinobicore.ko;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/**
 * Per-player KO record.
 *
 * <p>Stored in {@link KoManager#active} while the player is down,
 * persisted to {@code ko-state.yml} so a server restart doesn't cure
 * an unconscious shinobi. The fields:
 *
 * <ul>
 *   <li>{@code playerId} — Mojang/account UUID, used as the map key.</li>
 *   <li>{@code characterId} — the {@code ShinobiCharacter} the player
 *       was incarnating when they went down. Carried so we can route
 *       the KO back to the right character even if the player picks a
 *       different one before reconnecting.</li>
 *   <li>{@code startMillis} — when the KO started; the 5-minute
 *       auto-wake threshold is computed from this.</li>
 *   <li>{@code blindUntil} — wall-clock millis at which the blindness
 *       effect can be dropped. Always {@code >= startMillis + 5min}.</li>
 *   <li>{@code lockLocation} — where the body is pinned. Movement is
 *       cancelled back to here every tick so the player can't crawl.
 *       Updated only by {@link com.reborn.shinobicore.ko.action.PorterManager}
 *       when carried.</li>
 *   <li>{@code carrierPlayerId} — UUID of the player carrying this body
 *       (null when no-one is). Used by {@code PorterManager} to release
 *       the mount on disconnect / death.</li>
 * </ul>
 */
public final class KoState {

    /** What knocked the character out. Drives both the auto-wake
     *  duration ({@code HP}=5min, {@code CHAKRA}=3min) and which
     *  stat is restored on wake ({@code HP}=HP only, {@code CHAKRA}=
     *  chakra only). The OTHER stat is left exactly where it was at
     *  KO entry — getting hit hard doesn't drain your chakra, and
     *  exhausting your chakra doesn't injure you. */
    public enum Cause { HP, CHAKRA }

    private final UUID    playerId;
    private final UUID    characterId;
    private final Cause   cause;
    private final long    startMillis;
    private       long    blindUntil;
    private       Location lockLocation;
    private       UUID    carrierPlayerId;

    public KoState(UUID playerId, UUID characterId, Cause cause,
                   long startMillis, long blindUntil, Location lockLocation) {
        this.playerId        = playerId;
        this.characterId     = characterId;
        this.cause           = cause;
        this.startMillis     = startMillis;
        this.blindUntil      = blindUntil;
        this.lockLocation    = lockLocation;
        this.carrierPlayerId = null;
    }

    public UUID    playerId()        { return playerId; }
    public UUID    characterId()     { return characterId; }
    public Cause   cause()           { return cause; }
    public long    startMillis()     { return startMillis; }
    public long    blindUntil()      { return blindUntil; }
    public Location lockLocation()   { return lockLocation; }
    public UUID    carrierPlayerId() { return carrierPlayerId; }

    public void setBlindUntil(long t)              { this.blindUntil = t; }
    public void setLockLocation(Location loc)      { this.lockLocation = loc; }
    public void setCarrierPlayerId(UUID carrier)   { this.carrierPlayerId = carrier; }

    /* ---------------------------------------------------------- helpers */

    public boolean blindExpired()      { return System.currentTimeMillis() >= blindUntil; }
    public boolean isBeingCarried()    { return carrierPlayerId != null; }

    /* --------------------------------------------------------------- I/O */

    public static Location parseLoc(String world, double x, double y, double z,
                                    float yaw, float pitch) {
        World w = Bukkit.getWorld(world);
        if (w == null) return null;
        return new Location(w, x, y, z, yaw, pitch);
    }
}
