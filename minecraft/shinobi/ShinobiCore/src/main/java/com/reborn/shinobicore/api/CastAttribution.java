package com.reborn.shinobicore.api;

import com.reborn.shinobicore.character.ChakraAffinity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

/**
 * Attributes damage to the technique that caused it, whatever engine renders
 * the technique — MagicSpells ({@code cast forcecast}), MythicMobs skills,
 * ModelEngine models driven by MythicMobs, or an internal Java effect.
 *
 * <p>Those engines deal their own damage, out of ShinobiCore's reach. So the
 * caster opens a short <em>attribution window</em> when the technique fires;
 * ShinobiCore's damage listener multiplies every hit the caster (or a projectile
 * / MythicMobs summon they own) deals during that window by the stat scaling of
 * the technique. One place scales every engine the same way — no spell file has
 * to know about stats.
 *
 * <p>Taïjutsu M1 is scaled at the source by ShinobiCombat, which calls
 * {@link #markScaled} so the listener does not scale the same hit twice.
 */
@Stable
public interface CastAttribution {

    /** What a window multiplies. */
    enum Kind { TECHNIQUE, MELEE }

    /**
     * A technique just fired.
     *
     * @param multiplier   stat scaling (≥ 0), from {@link StatsService#techniquePower} with base 1
     * @param nature       elemental nature of the technique, {@link ChakraAffinity#NONE} if none
     * @param rankTier     D=1 … S=5, for the nature wheel
     * @param windowMillis how long hits stay attributed to this cast
     */
    void beginTechnique(Player caster, String techniqueId, double multiplier,
                        ChakraAffinity nature, int rankTier, long windowMillis);

    /** A weapon / fist M1 rendered by an external engine (e.g. the MagicSpells kenjutsu M1). */
    void beginMelee(Player caster, double multiplier, long windowMillis);

    /** Closes the caster's window early (cancelled cast, KO, character switch). */
    void end(Player caster);

    /** This damage event is already stat-scaled at the source — leave it alone. */
    void markScaled(EntityDamageEvent event);

    /**
     * True when this hit comes from a real swing of the damager (Paper's
     * {@code PrePlayerAttackEntityEvent}), false for a technique hit that only
     * looks like one ({@code ENTITY_ATTACK} dealt by a MythicMobs / MagicSpells
     * mechanic). ShinobiCombat's M1 engine must only handle genuine swings.
     */
    boolean isGenuineMelee(EntityDamageByEntityEvent event);
}
