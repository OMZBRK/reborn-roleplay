package com.reborn.shinobicore.api.event;

import com.reborn.shinobicore.api.Stable;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired the moment a character's primary resource pool bottoms out from
 * overdraw and the exhaustion collapse triggers, just before the KO is
 * applied. World-agnostic: the Naruto pack fires it with resource id
 * {@code "chakra"} (via the deprecated {@code ChakraDepletedEvent}
 * subclass, which shares this handler list — listen to THIS type).
 *
 * <p>Not cancellable — the collapse is final (mirrors {@code KoEnterEvent}).
 */
@Stable
public class ResourceDepletedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final java.util.UUID characterId;
    private final String resourceId;
    private final double debt;

    public ResourceDepletedEvent(@NotNull Player player,
                                 @Nullable java.util.UUID characterId,
                                 @NotNull String resourceId,
                                 double debt) {
        this.player = player;
        this.characterId = characterId;
        this.resourceId = resourceId;
        this.debt = debt;
    }

    /** The player who just collapsed from resource exhaustion. */
    public @NotNull Player player() { return player; }

    /** Id of the character they were incarnating, when known. */
    public @Nullable java.util.UUID characterId() { return characterId; }

    /** Which resource ran out (Naruto pack: {@code "chakra"}). */
    public @NotNull String resourceId() { return resourceId; }

    /** How deep the debt was when they collapsed (&gt;= 0). */
    public double debt() { return debt; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
