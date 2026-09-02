package com.reborn.shinobitail.data;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-CHARACTER jinchūriki state (a Minecraft account can own several
 * characters in ShinobiCore — the beast is sealed in a character, not
 * in an account).
 *
 * <p>Relationship values are GM-managed levers (0–100):
 * <ul>
 *   <li><b>trust</b> — the beast believes in its host</li>
 *   <li><b>anger</b> — resentment toward the host</li>
 *   <li><b>cooperation</b> — willingness to lend power on request</li>
 *   <li><b>influence</b> — how deep the beast's claws are in the host's mind</li>
 * </ul>
 * Mastery is tracked per stage (0–100%); rage persists between
 * sessions so logging out is never an escape hatch.
 */
public final class JinchurikiData implements com.reborn.shinobicore.data.CharacterData {

    private final UUID characterId;
    private String beastId;

    private double trust;
    private double anger;
    private double cooperation;
    private double influence = 50.0;

    /** stage number → mastery 0..100 */
    private final Map<Integer, Double> mastery = new LinkedHashMap<>();

    private double rage;

    /* ------------------------------------------------------ progression */
    /** Highest stage ever entered — gates sequential mastery growth. */
    private int highestStageReached;
    /** Point of no return crossed: the host is lost to the beast. */
    private boolean finalReleaseReached;
    /** Seal broken in harmony (trust+cooperation 100%): Union unlocked. */
    private boolean sealRemoved;

    /* lifetime stats (GM dashboards / RP flavour) */
    private int transformations;
    private int resistSuccesses;
    private int resistFailures;
    private long secondsTransformed;

    private transient boolean dirty;

    public JinchurikiData(UUID characterId) {
        this.characterId = characterId;
    }

    /* ---------------------------------------------------------- identity */

    public UUID characterId() { return characterId; }
    public String beastId()   { return beastId; }
    public void setBeastId(String id) { this.beastId = id; markDirty(); }

    /* ------------------------------------------------------ relationship */

    public double trust()        { return trust; }
    public double anger()        { return anger; }
    public double cooperation()  { return cooperation; }
    public double influence()    { return influence; }

    public void setTrust(double v)       { trust = clamp(v); markDirty(); }
    public void setAnger(double v)       { anger = clamp(v); markDirty(); }
    public void setCooperation(double v) { cooperation = clamp(v); markDirty(); }
    public void setInfluence(double v)   { influence = clamp(v); markDirty(); }

    /* ----------------------------------------------------------- mastery */

    public double mastery(int stage) {
        return mastery.getOrDefault(stage, 0.0);
    }

    public void setMastery(int stage, double v) {
        mastery.put(stage, clamp(v));
        markDirty();
    }

    public void addMastery(int stage, double delta) {
        setMastery(stage, mastery(stage) + delta);
    }

    public Map<Integer, Double> masteryView() { return mastery; }

    /**
     * The stage whose control can currently progress through
     * confrontations: the LOWEST stage under 100% that the host has
     * already reached. Returns 0 when nothing can progress — every
     * reached stage is mastered and the next one stays locked until
     * the host yields to the rage once more.
     */
    public int masteryTargetStage(int maxStage) {
        int reach = Math.min(maxStage, Math.max(0, highestStageReached));
        for (int s = 1; s <= reach; s++) {
            if (mastery(s) < 100.0) return s;
        }
        return 0;
    }

    public int highestStageReached() { return highestStageReached; }

    public void setHighestStageReached(int v) {
        highestStageReached = Math.max(0, v);
        markDirty();
    }

    public boolean finalReleaseReached() { return finalReleaseReached; }

    public void setFinalReleaseReached(boolean v) {
        finalReleaseReached = v;
        markDirty();
    }

    public boolean sealRemoved() { return sealRemoved; }

    public void setSealRemoved(boolean v) {
        sealRemoved = v;
        markDirty();
    }

    /* -------------------------------------------------------------- rage */

    public double rage() { return rage; }

    public void setRage(double v) {
        rage = Math.max(0.0, Math.min(100.0, v));
        markDirty();
    }

    public void addRage(double delta) { setRage(rage + delta); }

    /* -------------------------------------------------------------- stats */

    public int transformations()      { return transformations; }
    public int resistSuccesses()      { return resistSuccesses; }
    public int resistFailures()       { return resistFailures; }
    public long secondsTransformed()  { return secondsTransformed; }

    public void recordTransformation()    { transformations++; markDirty(); }
    public void recordResist(boolean ok)  {
        if (ok) resistSuccesses++; else resistFailures++;
        markDirty();
    }
    public void addSecondsTransformed(long s) { secondsTransformed += s; markDirty(); }

    /* -------------------------------------------------------------- reset */

    /**
     * Wipe this jinchūriki back to a pristine, freshly-sealed state: the
     * beast stays bound ({@link #beastId} is untouched), everything else
     * returns to creation defaults — relationship dials, mastery, rage,
     * the progression flags AND the lifetime stats. Used by the GM
     * {@code /tail reset} command to start a character's arc over from zero.
     */
    public void reset() {
        trust = 0;
        anger = 0;
        cooperation = 0;
        influence = 50.0;
        mastery.clear();
        rage = 0;
        highestStageReached = 0;
        finalReleaseReached = false;
        sealRemoved = false;
        transformations = 0;
        resistSuccesses = 0;
        resistFailures = 0;
        secondsTransformed = 0;
        markDirty();
    }

    /* ---------------------------------------------------------- plumbing */

    public boolean dirty()    { return dirty; }
    public void markDirty()   { dirty = true; }
    public void markClean()   { dirty = false; }

    /** Used by the store when loading from disk (no dirty flip). */
    void loadValues(String beastId, double trust, double anger,
                    double cooperation, double influence, double rage,
                    Map<Integer, Double> mastery,
                    int transformations, int resistOk, int resistKo,
                    long secondsTransformed,
                    int highestStageReached, boolean finalReleaseReached,
                    boolean sealRemoved) {
        this.highestStageReached = Math.max(0, highestStageReached);
        this.finalReleaseReached = finalReleaseReached;
        this.sealRemoved = sealRemoved;
        this.beastId = beastId;
        this.trust = clamp(trust);
        this.anger = clamp(anger);
        this.cooperation = clamp(cooperation);
        this.influence = clamp(influence);
        this.rage = Math.max(0.0, Math.min(100.0, rage));
        this.mastery.clear();
        this.mastery.putAll(mastery);
        this.transformations = transformations;
        this.resistSuccesses = resistOk;
        this.resistFailures = resistKo;
        this.secondsTransformed = secondsTransformed;
        this.dirty = false;
    }

    private static double clamp(double v) {
        return Math.max(0.0, Math.min(100.0, v));
    }
}
