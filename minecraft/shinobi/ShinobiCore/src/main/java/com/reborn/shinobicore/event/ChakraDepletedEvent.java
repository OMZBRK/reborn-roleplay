package com.reborn.shinobicore.event;

import com.reborn.shinobicore.api.event.ResourceDepletedEvent;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Naruto-named alias of {@link ResourceDepletedEvent} (resource id
 * {@code "chakra"}). Kept so existing listeners keep compiling and
 * receiving; it declares NO handler list of its own, so both old and
 * new-style listeners share the parent's.
 *
 * @deprecated listen to {@link ResourceDepletedEvent} instead; this
 *             subclass remains only as the fired type during the
 *             engine/content migration.
 */
@Deprecated
public class ChakraDepletedEvent extends ResourceDepletedEvent {

    public ChakraDepletedEvent(@NotNull Player player,
                               @Nullable java.util.UUID characterId,
                               double debt) {
        super(player, characterId, "chakra", debt);
    }
}
