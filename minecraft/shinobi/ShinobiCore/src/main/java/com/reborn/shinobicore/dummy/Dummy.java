package com.reborn.shinobicore.dummy;

import com.reborn.shinobicore.ko.injury.Injury;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * A training dummy — a Villager-shaped NPC with virtual HP / chakra
 * mirroring a {@code ShinobiCharacter}.
 *
 * <p>Dummies are kept in {@link DummyManager}'s registry keyed by
 * {@link #name name} (case-insensitive lookup). The {@link #entityId}
 * tracks the live Villager UUID so damage events can route back to
 * the dummy data; if the Villager is unloaded / despawned, we restore
 * it on world load.
 *
 * <p>Stats:
 * <ul>
 *   <li>{@link #maxHp}, {@link #currentHp} — virtual HP. The Villager's
 *       Bukkit health is capped to a constant; we cancel real damage
 *       and bookkeep the virtual hit on this object.</li>
 *   <li>{@link #maxChakra}, {@link #currentChakra} — purely cosmetic
 *       for now (dummies don't use abilities), but exposed in /état
 *       and /osculter so the user can sanity-check the chakra-zero
 *       KO trigger.</li>
 *   <li>{@link #injuries} — same {@link Injury} type used by real
 *       characters, so the silhouette GUI renders identically.</li>
 *   <li>{@link #ko} — true once virtual HP hit 0 OR chakra hit 0.
 *       Cleared by /dummy heal.</li>
 * </ul>
 *
 * <p>Persisted in {@code dummies.yml}; the Villager itself is
 * tag-marked so we can re-bind on world load.
 */
public final class Dummy {

    private final String   name;
    private final UUID     id;          // stable id, separate from entity uuid
    private       UUID     entityId;
    private       String   worldName;
    private       double   x, y, z;
    private       double   maxHp;
    private       double   currentHp;
    private       double   maxChakra;
    private       double   currentChakra;
    private final List<Injury> injuries = new ArrayList<>();
    private       boolean  ko;

    public Dummy(String name, UUID id) {
        this.name          = name;
        this.id            = id;
        this.maxHp         = 100.0;
        this.currentHp     = 100.0;
        this.maxChakra     = 100.0;
        this.currentChakra = 100.0;
    }

    /* ---------------------------------------------------- accessors */

    public String name()              { return name; }
    public UUID   id()                { return id; }
    public UUID   entityId()          { return entityId; }
    public String worldName()         { return worldName; }
    public double x()                 { return x; }
    public double y()                 { return y; }
    public double z()                 { return z; }
    public double maxHp()             { return maxHp; }
    public double currentHp()         { return currentHp; }
    public double maxChakra()         { return maxChakra; }
    public double currentChakra()     { return currentChakra; }
    public List<Injury> injuries()    { return injuries; }
    public boolean ko()               { return ko; }

    public void setEntityId(UUID v)        { this.entityId = v; }
    public void setLocation(String world, double x, double y, double z) {
        this.worldName = world; this.x = x; this.y = y; this.z = z;
    }
    public void setMaxHp(double v)         { this.maxHp = Math.max(1, v); if (currentHp > maxHp) currentHp = maxHp; }
    public void setCurrentHp(double v)     { this.currentHp = Math.max(0, Math.min(v, maxHp)); if (currentHp > 0) ko = false; }
    public void setMaxChakra(double v)     { this.maxChakra = Math.max(0, v); if (currentChakra > maxChakra) currentChakra = maxChakra; }
    public void setCurrentChakra(double v) { this.currentChakra = Math.max(0, Math.min(v, maxChakra)); }
    public void setKo(boolean v)           { this.ko = v; }

    public void clearInjuries() { injuries.clear(); }
    public void addInjury(Injury i) { if (i != null) injuries.add(i); }
}
