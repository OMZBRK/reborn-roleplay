package com.reborn.shinobicore.event;

import com.reborn.shinobicore.ko.KoState;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired when a player enters the KO (unconscious) state, before the KO
 * effects / title are applied. Addon plugins use this to force-close
 * any UI that hijacks the hotbar (jutsu picker) and to cancel in-flight
 * incantations.
 *
 * <p>Not cancellable — the KO decision belongs to {@code KoManager}.
 */
public class KoEnterEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final java.util.UUID characterId;
    private final KoState.Cause cause;

    public KoEnterEvent(@NotNull Player player,
                        @Nullable java.util.UUID characterId,
                        @NotNull KoState.Cause cause) {
        this.player = player;
        this.characterId = characterId;
        this.cause = cause;
    }

    /** The player who just went unconscious. */
    public @NotNull Player player() { return player; }

    /** Id of the character they were incarnating, when known. */
    public @Nullable java.util.UUID characterId() { return characterId; }

    /** What dropped them (HP loss or chakra exhaustion). */
    public @NotNull KoState.Cause cause() { return cause; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
