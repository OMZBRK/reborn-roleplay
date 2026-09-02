package com.reborn.shinobicore.api.event;

import com.reborn.shinobicore.character.ShinobiCharacter;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Fired at the very start of {@code CharacterManager.setActive} for an
 * <em>online</em> player, BEFORE the previous character's inventory is
 * captured and before any stat / inventory / position swap happens.
 *
 * <p>This ordering is deliberate: addon plugins (ShinobiAbilities) that
 * temporarily replace hotbar slots (jutsu picker) MUST restore the real
 * hotbar in this hook, otherwise the outgoing character would persist
 * picker icons as its inventory snapshot.
 *
 * <p>Not cancellable — it is a notification, not a veto point.
 */
@com.reborn.shinobicore.api.Stable
public class CharacterSwitchEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final ShinobiCharacter previous;
    private final ShinobiCharacter next;

    public CharacterSwitchEvent(@NotNull Player player,
                                @Nullable ShinobiCharacter previous,
                                @NotNull ShinobiCharacter next) {
        this.player = player;
        this.previous = previous;
        this.next = next;
    }

    /** The online player performing the switch. */
    public @NotNull Player player() { return player; }

    /** Character being switched away from; {@code null} on first pick. */
    public @Nullable ShinobiCharacter previous() { return previous; }

    /** Character being switched to. Never null. */
    public @NotNull ShinobiCharacter next() { return next; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
