package com.reborn.shinobicore.api;

import org.bukkit.entity.Player;

/**
 * Combat endurance (stamina) — owned by ShinobiCombat's {@code StaminaManager},
 * published through the ServicesManager so a Taïjutsu / Kenjutsu technique can
 * be paid in endurance rather than chakra (SPEC_STATS_SERVICE §1.3, cost
 * {@code STAMINA}). The <em>maximum</em> is not decided here: it comes from
 * {@link StatsService#maxStamina}.
 *
 * <p>Optional service — absent when ShinobiCombat is not installed; callers
 * fall back to chakra.
 */
@Stable
public interface EnduranceService {

    double current(Player player);

    double max(Player player);

    /** Spend if affordable; false (nothing spent) otherwise. */
    boolean tryConsume(Player player, double amount);
}
