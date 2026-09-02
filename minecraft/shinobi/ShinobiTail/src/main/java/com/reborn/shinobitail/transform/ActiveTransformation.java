package com.reborn.shinobitail.transform;

import com.reborn.shinobitail.beast.BeastDefinition;
import com.reborn.shinobitail.data.JinchurikiData;
import org.bukkit.boss.BossBar;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Mutable runtime state of one ongoing transformation. Created by
 * {@link TransformationManager#begin} and destroyed by
 * {@link TransformationManager#stop}; never persisted — a restart always
 * comes back untransformed (rage itself IS persisted on the data object).
 */
public final class ActiveTransformation {

    public enum Cause { HELP, TAKEOVER, KO_SAVE, GM, ESCALATION }

    private final UUID playerId;
    private final JinchurikiData data;
    private final BeastDefinition beast;

    private int stage;
    private long startedAtMillis;
    private long stageEnteredAtMillis;
    private long nextWindowAtMillis;
    private long lastWhisperAtMillis;
    /** Highest corruption tier threshold already fired (-1 = none). */
    private double firedCorruptionTier = -1;

    private boolean paused;
    private boolean finalRelease;
    private boolean union;
    private BossBar bossBar;
    /** Potion effects WE applied — removed verbatim on stop. */
    private final List<PotionEffectType> appliedEffects = new ArrayList<>();

    ActiveTransformation(UUID playerId, JinchurikiData data,
                         BeastDefinition beast, int stage) {
        this.playerId = playerId;
        this.data = data;
        this.beast = beast;
        this.stage = stage;
        this.startedAtMillis = System.currentTimeMillis();
        this.stageEnteredAtMillis = startedAtMillis;
        this.lastWhisperAtMillis = startedAtMillis;
    }

    public UUID playerId()             { return playerId; }
    public JinchurikiData data()       { return data; }
    public BeastDefinition beast()     { return beast; }
    public int stage()                 { return stage; }
    public long startedAtMillis()      { return startedAtMillis; }
    public long stageEnteredAtMillis() { return stageEnteredAtMillis; }
    public long nextWindowAtMillis()   { return nextWindowAtMillis; }
    public long lastWhisperAtMillis()  { return lastWhisperAtMillis; }
    public double firedCorruptionTier(){ return firedCorruptionTier; }
    public BossBar bossBar()           { return bossBar; }
    public List<PotionEffectType> appliedEffects() { return appliedEffects; }

    void setStage(int stage) {
        this.stage = stage;
        this.stageEnteredAtMillis = System.currentTimeMillis();
    }

    void setNextWindowAt(long millis)    { this.nextWindowAtMillis = millis; }
    void setLastWhisperAt(long millis)   { this.lastWhisperAtMillis = millis; }
    void setFiredCorruptionTier(double t){ this.firedCorruptionTier = t; }
    void setBossBar(BossBar bar)         { this.bossBar = bar; }

    /** GM narration freeze: rage engine + windows on hold. */
    public boolean paused()              { return paused; }
    public void setPaused(boolean v)     { this.paused = v; }

    /** Point of no return — no more confrontations, no way back. */
    public boolean finalRelease()        { return finalRelease; }
    void setFinalRelease(boolean v)      { this.finalRelease = v; }

    /** Mode Union — host and bijū as one: full power, zero rage. */
    public boolean union()               { return union; }
    void setUnion(boolean v)             { this.union = v; }

    /** Seconds spent in the CURRENT stage. */
    public long secondsInStage() {
        return (System.currentTimeMillis() - stageEnteredAtMillis) / 1000L;
    }
}
