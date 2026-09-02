package com.reborn.shinobicore.event;

import com.reborn.shinobicore.api.event.CharacterDamageEvent;
import com.reborn.shinobicore.ko.injury.DamageOrigin;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Naruto-named alias of {@link CharacterDamageEvent}. Kept so existing
 * listeners keep compiling and receiving; it declares NO handler list
 * of its own, so both old and new-style listeners share the parent's.
 *
 * @deprecated listen to {@link CharacterDamageEvent} instead; this
 *             subclass remains only as the fired type during the
 *             engine/content migration.
 */
@Deprecated
public class ShinobiDamageEvent extends CharacterDamageEvent {

    public ShinobiDamageEvent(@NotNull Player victim, @NotNull DamageOrigin origin,
                              @NotNull EntityDamageEvent.DamageCause vanillaCause,
                              double amount, boolean lethal) {
        super(victim, origin, vanillaCause, amount, lethal);
    }
}
