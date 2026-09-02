package com.reborn.shinobiabilities.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Per-player, per-ability cooldown store with <b>enumeration</b> support
 * (core's {@code CooldownManager} can't list active entries, which the
 * HUD sections need). Wall-clock based so short cooldowns survive tick
 * drift. Expired entries are pruned lazily on enumeration.
 */
public final class CooldownTracker {

    /** One ticking cooldown for HUD rendering. */
    public record Entry(String abilityId, long remainingMillis) {}

    private final Map<UUID, Map<String, Long>> expiries = new ConcurrentHashMap<>();

    public boolean isOnCooldown(UUID id, String abilityId) {
        return remainingMillis(id, abilityId) > 0L;
    }

    public long remainingMillis(UUID id, String abilityId) {
        Map<String, Long> m = expiries.get(id);
        if (m == null) return 0L;
        Long until = m.get(abilityId);
        if (until == null) return 0L;
        return Math.max(0L, until - System.currentTimeMillis());
    }

    public void set(UUID id, String abilityId, long durationMillis) {
        if (durationMillis <= 0) return;
        expiries.computeIfAbsent(id, k -> new ConcurrentHashMap<>())
                .put(abilityId, System.currentTimeMillis() + durationMillis);
    }

    public void clear(UUID id, String abilityId) {
        Map<String, Long> m = expiries.get(id);
        if (m != null) m.remove(abilityId);
    }

    public void clearAll(UUID id) {
        expiries.remove(id);
    }

    /** Active (remaining &gt; 0) cooldowns for {@code id}, pruning the
     *  expired ones as a side effect. Unordered. */
    public List<Entry> active(UUID id) {
        Map<String, Long> m = expiries.get(id);
        if (m == null || m.isEmpty()) return List.of();
        long now = System.currentTimeMillis();
        List<Entry> out = new ArrayList<>(m.size());
        for (var it = m.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            long rem = e.getValue() - now;
            if (rem <= 0) { it.remove(); continue; }
            out.add(new Entry(e.getKey(), rem));
        }
        return out;
    }
}
