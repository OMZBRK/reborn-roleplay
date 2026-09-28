package com.reborn.shinobicore.api.event;

import com.reborn.shinobicore.api.Stable;
import com.reborn.shinobicore.api.StatsService;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;

/**
 * Fired after {@link StatsService#allocate} / {@link StatsService#respec} (and
 * staff edits). The pools are already recomputed when it fires: the max moved,
 * the current value stayed the same in absolute terms (no free heal).
 */
@Stable
public class CharacterStatsChangedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final UUID ownerId;
    private final UUID characterId;
    private final Map<StatsService.Stat, Integer> before;
    private final Map<StatsService.Stat, Integer> after;

    public CharacterStatsChangedEvent(@NotNull UUID ownerId, @NotNull UUID characterId,
                                      @NotNull Map<StatsService.Stat, Integer> before,
                                      @NotNull Map<StatsService.Stat, Integer> after) {
        this.ownerId = ownerId;
        this.characterId = characterId;
        this.before = Map.copyOf(before);
        this.after = Map.copyOf(after);
    }

    public @NotNull UUID ownerId() { return ownerId; }

    public @NotNull UUID characterId() { return characterId; }

    public @NotNull Map<StatsService.Stat, Integer> before() { return before; }

    public @NotNull Map<StatsService.Stat, Integer> after() { return after; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static HandlerList getHandlerList() { return HANDLERS; }
}
