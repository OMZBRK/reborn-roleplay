package com.reborn.shinobitail.beast;

import java.util.List;

/**
 * Fully-resolved stats for one stage of one beast. Built by
 * {@link BeastRegistry} from the power curve in config.yml, with any
 * per-stage override from beasts.yml already applied.
 *
 * <p>All percentages are final values for THIS stage:
 * <ul>
 *   <li>{@code bonusSpeedPercent} — added on top of every other speed</li>
 *   <li>{@code resistancePercent} — damage reduction (hard-capped 80%)</li>
 *   <li>{@code healthRegenPercent} / {@code chakraRegenPercent} — % of max
 *       per second while the stage is active</li>
 *   <li>{@code instantHealPercent} / {@code instantChakraPercent} — % of max
 *       granted once on stage entry</li>
 *   <li>{@code auraRange} — blocks; how far sensory characters can feel
 *       the bijū chakra (the tracking arrow itself lives in the sensory
 *       system, ShinobiTail only exposes the range)</li>
 * </ul>
 * {@code extraEffects} entries follow the {@code "EFFECT_NAME:amplifier"}
 * config format, parsed lazily so a staff typo degrades to a console
 * warning instead of a load failure.
 */
public record BeastStage(int number,
                         String displayName,
                         double power,
                         double damageMultiplier,
                         double bonusSpeedPercent,
                         double resistancePercent,
                         double knockbackResistance,
                         double extraScale,
                         double healthRegenPercent,
                         double instantHealPercent,
                         double chakraRegenPercent,
                         double instantChakraPercent,
                         double auraRange,
                         List<String> extraEffects) {
}
