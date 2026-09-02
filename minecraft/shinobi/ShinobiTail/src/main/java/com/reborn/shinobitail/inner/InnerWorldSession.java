package com.reborn.shinobitail.inner;

import com.reborn.shinobitail.beast.BeastDefinition;
import com.reborn.shinobitail.data.JinchurikiData;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * One ongoing Inner World trip. Lives from the freeze flash until the
 * player is returned to their body.
 */
public final class InnerWorldSession {

    public enum Phase { FREEZING, CHOOSING, RESOLVING }

    private final UUID playerId;
    private final JinchurikiData data;
    private final BeastDefinition beast;
    private final int stageAtEntry;
    private final Location returnLocation;

    private Phase phase = Phase.FREEZING;
    private UUID fakeBodyId;
    private BukkitTask freezeTask;
    private BukkitTask auraTask;
    private BukkitTask timeoutTask;

    public InnerWorldSession(UUID playerId, JinchurikiData data,
                             BeastDefinition beast, int stageAtEntry,
                             Location returnLocation) {
        this.playerId = playerId;
        this.data = data;
        this.beast = beast;
        this.stageAtEntry = stageAtEntry;
        this.returnLocation = returnLocation;
    }

    public UUID playerId()           { return playerId; }
    public JinchurikiData data()     { return data; }
    public BeastDefinition beast()   { return beast; }
    public int stageAtEntry()        { return stageAtEntry; }
    public Location returnLocation() { return returnLocation.clone(); }

    public Phase phase()             { return phase; }
    public void setPhase(Phase p)    { this.phase = p; }

    public UUID fakeBodyId()             { return fakeBodyId; }
    public void setFakeBodyId(UUID id)   { this.fakeBodyId = id; }

    public BukkitTask freezeTask()             { return freezeTask; }
    public void setFreezeTask(BukkitTask t)    { this.freezeTask = t; }
    public BukkitTask auraTask()               { return auraTask; }
    public void setAuraTask(BukkitTask t)      { this.auraTask = t; }
    public BukkitTask timeoutTask()            { return timeoutTask; }
    public void setTimeoutTask(BukkitTask t)   { this.timeoutTask = t; }

    public void cancelTasks() {
        if (freezeTask != null)  { freezeTask.cancel();  freezeTask = null; }
        if (auraTask != null)    { auraTask.cancel();    auraTask = null; }
        if (timeoutTask != null) { timeoutTask.cancel(); timeoutTask = null; }
    }
}
