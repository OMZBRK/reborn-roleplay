package com.reborn.shinobicore.api.event;

import com.reborn.shinobicore.api.Stable;
import com.reborn.shinobicore.ko.injury.DamageOrigin;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.event.entity.EntityDamageEvent;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a player with an active character takes damage, carrying
 * the RP classification of the hit ({@link DamageOrigin}) alongside the
 * raw vanilla cause. World-agnostic event shape; today's
 * {@code DamageOrigin} VALUES are Naruto content and should become
 * data-driven with the technique catalog (see BOUNDARY.md).
 *
 * <p>The Naruto pack fires the deprecated {@code ShinobiDamageEvent}
 * subclass, which shares this handler list — listen to THIS type.
 *
 * <p>Cancellable: cancelling also cancels the underlying damage.
 */
@Stable
public class CharacterDamageEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player victim;
    private final DamageOrigin origin;
    private final EntityDamageEvent.DamageCause vanillaCause;
    private final double amount;
    private final boolean lethal;
    private boolean cancelled;

    public CharacterDamageEvent(@NotNull Player victim, @NotNull DamageOrigin origin,
                                @NotNull EntityDamageEvent.DamageCause vanillaCause,
                                double amount, boolean lethal) {
        this.victim = victim;
        this.origin = origin;
        this.vanillaCause = vanillaCause;
        this.amount = amount;
        this.lethal = lethal;
    }

    /** The character-player taking the hit. */
    public @NotNull Player victim() { return victim; }

    /** RP classification of the hit. */
    public @NotNull DamageOrigin origin() { return origin; }

    /** The underlying vanilla damage cause. */
    public @NotNull EntityDamageEvent.DamageCause vanillaCause() { return vanillaCause; }

    /** Final damage of this hit. */
    public double amount() { return amount; }

    /** True if this hit would drop the player into KO. */
    public boolean isLethal() { return lethal; }

    private com.reborn.shinobicore.api.etat.DegatContexte contexte;

    /**
     * Contexte de la porte des dégâts (étiquettes, technique, défense appliquée ou contournée — ETATS_JOUEUR §6.3), ou
     * null (dégât non évalué).
     */
    public com.reborn.shinobicore.api.etat.DegatContexte contexte() { return contexte; }

    public void contexte(com.reborn.shinobicore.api.etat.DegatContexte c) { this.contexte = c; }

    @Override
    public boolean isCancelled() { return cancelled; }

    @Override
    public void setCancelled(boolean cancel) { this.cancelled = cancel; }

    @Override
    public @NotNull HandlerList getHandlers() { return HANDLERS; }

    public static @NotNull HandlerList getHandlerList() { return HANDLERS; }
}
