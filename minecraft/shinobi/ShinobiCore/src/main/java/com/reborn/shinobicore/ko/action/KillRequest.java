package com.reborn.shinobicore.ko.action;

import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;

/**
 * One pending Tuer approval request.
 *
 * <p>Carries everything {@link KillRequestManager} needs to:
 * <ul>
 *   <li>render the staff prompt without re-resolving names,</li>
 *   <li>finalise the kill (target player UUID, target character UUID),</li>
 *   <li>journal the verdict (location, requester names),</li>
 *   <li>cancel the timeout if a verdict lands first.</li>
 * </ul>
 *
 * <p>Mutable only through the {@link #setTimeoutTask} setter so the
 * scheduled timeout can be cancelled on early decision; everything
 * else is final.
 */
public final class KillRequest {

    private final long      id;
    private final UUID      requesterId;
    private final String    requesterName;
    private final UUID      targetPlayerId;
    private final UUID      targetCharacterId;
    private final String    targetName;
    private final Location  scene;
    private final long      requestedAt;
    private       BukkitTask timeoutTask;

    public KillRequest(long id, UUID requesterId, String requesterName,
                       UUID targetPlayerId, UUID targetCharacterId,
                       String targetName, Location scene, long requestedAt) {
        this.id                = id;
        this.requesterId       = requesterId;
        this.requesterName     = requesterName;
        this.targetPlayerId    = targetPlayerId;
        this.targetCharacterId = targetCharacterId;
        this.targetName        = targetName;
        this.scene             = scene;
        this.requestedAt       = requestedAt;
    }

    public long       id()                { return id; }
    public UUID       requesterId()       { return requesterId; }
    public String     requesterName()     { return requesterName; }
    public UUID       targetPlayerId()    { return targetPlayerId; }
    public UUID       targetCharacterId() { return targetCharacterId; }
    public String     targetName()        { return targetName; }
    public Location   scene()             { return scene; }
    public long       requestedAt()       { return requestedAt; }
    public BukkitTask timeoutTask()       { return timeoutTask; }

    public void setTimeoutTask(BukkitTask t) { this.timeoutTask = t; }
}
