package com.reborn.shinobiabilities.jutsu;

import com.reborn.shinobicore.api.EnduranceService;
import com.reborn.shinobicore.api.StatsService;
import com.reborn.shinobicore.api.StatsService.CostKind;
import com.reborn.shinobicore.character.ShinobiCharacter;
import com.reborn.shinobicore.technique.Ability;
import com.reborn.shinobicore.technique.TechniqueProfile;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

/**
 * What a technique costs, now that the stats own the economy
 * (SPEC_STATS_SERVICE §1.3–1.4): the <b>rank</b> sets a base cost (chakra for
 * the chakra arts, endurance for Taïjutsu / Bukijutsu), {@code cost-factor}
 * tunes it per technique, Contrôle and mastery bring it down.
 *
 * <p>{@code jutsu.stats-costs: false} restores the legacy flat
 * {@code chakra-cost} of abilities.yml (6-50, pre-stats scale).
 */
public final class TechniqueCosts {

    private TechniqueCosts() {}

    public static StatsService stats() {
        return Bukkit.getServicesManager().load(StatsService.class);
    }

    /** Optional — registered by ShinobiCombat. */
    public static EnduranceService endurance() {
        return Bukkit.getServicesManager().load(EnduranceService.class);
    }

    public static boolean enabled(JavaPlugin plugin) {
        return plugin.getConfig().getBoolean("jutsu.stats-costs", true) && stats() != null;
    }

    /** Resource actually charged: endurance only when ShinobiCombat can take it. */
    public static CostKind kind(JavaPlugin plugin, Ability a) {
        if (!enabled(plugin)) return CostKind.CHAKRA;
        CostKind k = a.profile().costKind();
        return k == CostKind.STAMINA && endurance() == null ? CostKind.CHAKRA : k;
    }

    /** Cost for this character, before the mastery discount. */
    public static double characterCost(JavaPlugin plugin, ShinobiCharacter c, Ability a) {
        StatsService s = stats();
        if (!enabled(plugin) || s == null) return a.jutsu().chakraCost();
        TechniqueProfile tp = a.profile();
        return s.techniqueCost(c.id(), tp.tier(), kind(plugin, a)) * tp.costFactor();
    }

    /** Base cost of the technique (no character) — catalogue and item lore. */
    public static double baseCost(JavaPlugin plugin, Ability a) {
        if (a.jutsu() == null) return 0;
        StatsService s = stats();
        if (!enabled(plugin) || s == null) return a.jutsu().chakraCost();
        return s.baseTechniqueCost(a.profile().tier(), kind(plugin, a)) * a.profile().costFactor();
    }

    /** Same, for static display code without a plugin handle. */
    public static String label(Ability a) {
        return label(JavaPlugin.getPlugin(com.reborn.shinobiabilities.ShinobiAbilities.class), a);
    }

    /** {@code "2 000 chakra"} / {@code "30 endurance"}. */
    public static String label(JavaPlugin plugin, Ability a) {
        return String.format(Locale.FRANCE, "%,d", Math.round(baseCost(plugin, a)))
                + (kind(plugin, a) == CostKind.STAMINA ? " endurance" : " chakra");
    }
}
