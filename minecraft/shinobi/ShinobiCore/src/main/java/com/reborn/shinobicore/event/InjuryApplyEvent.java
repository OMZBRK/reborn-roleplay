package com.reborn.shinobicore.event;

import com.reborn.shinobicore.ko.injury.Injury;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired just before an {@link Injury} is recorded on a character. Carries the
 * diagnosed body part, injury type, origin and severity.
 *
 * <p>Cancellable: cancelling skips recording the injury (e.g. armour / a
 * resistance jutsu negating a specific burn or fracture).
 */
public class InjuryApplyEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Injury injury;
    private boolean cancelled;

    public InjuryApplyEvent(@NotNull Player player, @NotNull Injury injury) {
        this.player = player;
        this.injury = injury;
    }

    /** The injured player. */
    public @NotNull Player player() { return player; }

    /** The injury about to be applied (body part, type, origin, severity). */
    public @NotNull Injury injury() { return injury; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
