package com.reborn.shinobicore.medic;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.UUID;

/**
 * Placement record for a Medic Armoir — the iron door block players
 * place via {@code /medic armoir}.
 *
 * <p>Stored in {@link MedicArmoirManager} keyed by {@link #id}; the
 * {@link #location} is hashable to a deterministic chunk for the
 * right-click handler to find. Each armoir tracks {@link #lastRefillMillis}
 * — when the auto-refill ticker last topped this armoir up to its
 * default loadout. The 15-minute interval is enforced by the manager.
 */
public final class MedicArmoir {

    private final UUID    id;
    private       String  worldName;
    private       int     x, y, z;
    private       long    lastRefillMillis;

    public MedicArmoir(UUID id) {
        this.id = id;
    }

    public UUID    id()                 { return id; }
    public String  worldName()          { return worldName; }
    public int     x()                  { return x; }
    public int     y()                  { return y; }
    public int     z()                  { return z; }
    public long    lastRefillMillis()   { return lastRefillMillis; }

    public void setLocation(Location loc) {
        this.worldName = loc.getWorld().getName();
        this.x = loc.getBlockX();
        this.y = loc.getBlockY();
        this.z = loc.getBlockZ();
    }
    public void setLastRefillMillis(long t) { this.lastRefillMillis = t; }

    public Location location() {
        World w = Bukkit.getWorld(worldName);
        if (w == null) return null;
        return new Location(w, x, y, z);
    }
}
