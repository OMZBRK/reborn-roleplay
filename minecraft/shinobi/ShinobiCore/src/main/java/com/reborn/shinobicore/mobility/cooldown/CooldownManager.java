package com.reborn.shinobicore.mobility.cooldown;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Very small per-player, per-ability cooldown store. Uses wall-clock
 * timestamps so short cooldowns survive server ticks without drift.
 */
public class CooldownManager {

    /** key: {@code playerId + ":" + abilityId}  →  expiry epoch millis */
    private final Map<String, Long> expiries = new HashMap<>();

    private static String key(UUID id, String abilityId) {
        return id + ":" + abilityId;
    }

    public boolean isOnCooldown(UUID id, String abilityId) {
        Long until = expiries.get(key(id, abilityId));
        return until != null && System.currentTimeMillis() < until;
    }

    public long remainingMillis(UUID id, String abilityId) {
        Long until = expiries.get(key(id, abilityId));
        if (until == null) return 0L;
        return Math.max(0L, until - System.currentTimeMillis());
    }

    public void set(UUID id, String abilityId, long durationMillis) {
        expiries.put(key(id, abilityId), System.currentTimeMillis() + durationMillis);
    }

    public void clear(UUID id, String abilityId) {
        expiries.remove(key(id, abilityId));
    }

    public void clear(UUID id) {
        String prefix = id + ":";
        expiries.keySet().removeIf(k -> k.startsWith(prefix));
    }
}
