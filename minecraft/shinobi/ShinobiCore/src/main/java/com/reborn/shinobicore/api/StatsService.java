package com.reborn.shinobicore.api;

import com.reborn.shinobicore.character.ChakraAffinity;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Character stats — six integers allocated by the player, and every value
 * derived from them. Source of truth for the HP / chakra / endurance pools,
 * technique costs and damage scaling (SPEC_STATS_SERVICE §1, option B).
 *
 * <p><b>Golden rule:</b> no caller re-implements a formula. ShinobiCombat,
 * ShinobiAbilities and the HUD all go through the derived methods below, so
 * balancing happens in one place (the {@code stats:} block of ShinobiCore's
 * config.yml, live-reloadable).
 *
 * <p>{@code character} is always a <em>character</em> id, never an account id.
 * Resolve via {@code Bukkit.getServicesManager().load(StatsService.class)}.
 */
@Stable
public interface StatsService {

    /** The six stats. Declaration order is the display order. */
    enum Stat {
        TAIJUTSU("Taïjutsu"),
        KENJUTSU("Kenjutsu"),
        NINJUTSU("Ninjutsu"),
        CONTROLE("Contrôle"),
        VIGUEUR("Vigueur"),
        CHAKRA("Chakra");

        private final String displayName;

        Stat(String displayName) { this.displayName = displayName; }

        public String displayName() { return displayName; }

        /** YAML / wire key ({@code taijutsu}, {@code controle}…). */
        public String key() { return name().toLowerCase(Locale.ROOT); }

        /** Tolerant parse by key, name, display name or 3-letter prefix; null when unknown. */
        public static Stat from(String s) {
            if (s == null) return null;
            String n = java.text.Normalizer.normalize(s.trim(), java.text.Normalizer.Form.NFD)
                    .replaceAll("\\p{M}", "").toUpperCase(Locale.ROOT);
            for (Stat st : values()) {
                if (st.name().equals(n)) return st;
            }
            if (n.length() >= 3) {
                for (Stat st : values()) {
                    if (st.name().startsWith(n)) return st;
                }
            }
            return null;
        }
    }

    /** Scaling letter of a technique on one stat. */
    enum Grade {
        S, A, B, C, D;

        public static Grade from(String s) {
            if (s == null) return null;
            try { return Grade.valueOf(s.trim().toUpperCase(Locale.ROOT)); }
            catch (IllegalArgumentException ignore) { return null; }
        }
    }

    enum CostKind { CHAKRA, STAMINA }

    int MIN = 1;
    /** May be raised without migration, never lowered. */
    int MAX = 10;

    /** Raw allocated value, always in [MIN, MAX]. */
    int get(UUID character, Stat stat);

    /** Value after the soft cap — the one that enters the formulas. */
    double effective(UUID character, Stat stat);

    /** Full view in display order — one read for the HUD and the sheet. */
    Map<Stat, Integer> all(UUID character);

    /** Points earned (rank passages + bonus) and not spent yet. */
    int unspentPoints(UUID character);

    /**
     * Spend points. Atomic: everything passes or nothing does. Fails when the
     * total exceeds the available points or a stat would leave [MIN, MAX].
     * Negative deltas are refused (lowering a stat is {@link #respec}).
     */
    AllocationResult allocate(UUID character, Map<Stat, Integer> deltas);

    /** Every stat back to MIN, points refunded. For the prestige reset. */
    void respec(UUID character);

    // --- Derived values: nobody recomputes the formulas on their own ---

    double maxHp(UUID character);

    double maxChakra(UUID character);

    double maxStamina(UUID character);

    double chakraRegenPer10s(UUID character);

    double critChance(UUID character);

    /** Technique cost for this character after the Contrôle reduction. {@code techniqueRank}: E=0, D=1 … S=5. */
    double techniqueCost(UUID character, int techniqueRank, CostKind kind);

    /** Cost of a technique rank before any character reduction — for catalog / lore display. */
    double baseTechniqueCost(int techniqueRank, CostKind kind);

    /** Damage or heal of a technique: {@code base × (1 + Σ weight(grade) × eff(stat))} — nature excluded. */
    double techniquePower(UUID character, double base, Map<Stat, Grade> scaling);

    /** Nature-wheel multiplier between two techniques. 0 on cancellation. */
    double natureMultiplier(ChakraAffinity attacker, int attackerRank,
                            ChakraAffinity defender, int defenderRank);

    /** M1 damage: Taïjutsu scaling bare-handed, Kenjutsu scaling armed. */
    double meleeDamage(UUID character, double base, boolean armed);

    /** Outcome of {@link #allocate}. */
    record AllocationResult(boolean ok, String reason, int unspentAfter) {
        public static AllocationResult success(int unspentAfter) {
            return new AllocationResult(true, null, unspentAfter);
        }

        public static AllocationResult failure(String reason, int unspent) {
            return new AllocationResult(false, reason, unspent);
        }
    }
}
