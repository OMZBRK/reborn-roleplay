package com.reborn.shinobicore.stats;

import com.reborn.shinobicore.api.StatsService.CostKind;
import com.reborn.shinobicore.api.StatsService.Grade;
import com.reborn.shinobicore.api.StatsService.Stat;
import com.reborn.shinobicore.character.ChakraAffinity;
import com.reborn.shinobicore.character.Rank;
import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;

/**
 * Every stat formula of SPEC_STATS_SERVICE §1.3, in one place. Pure functions
 * over a {@link CharacterStats}; the coefficients are <b>levers</b> read from the
 * {@code stats:} block of config.yml and swapped atomically on {@code /sc reload}
 * — nerfing never needs a redeploy (§6).
 *
 * <p>Technique tiers: E=0, D=1, C=2, B=3, A=4, S=5 (HIDEN is the S tier).
 */
public final class StatFormulas {

    /** All balancing coefficients. Immutable; replaced as a whole on reload. */
    public record Levers(
            int pointsPerRank,
            double softCap, double overCapFactor,
            double hpBase, double hpPerVigueur,
            double chakraBase, double chakraPerPoint,
            double staminaBase, double staminaPerVigueur, double staminaPerTaijutsu,
            double regenBase, double regenPerControle,
            double costReductionPerControle,
            double[] chakraCostByTier, double[] staminaCostByTier,
            Map<Grade, Double> gradeWeights,
            double meleePerPoint,
            boolean critEnabled, double critBase, double critPerControle, double critMultiplier,
            boolean natureWheelEnabled, double natureAdvantage, double natureDisadvantage,
            long techniqueWindowMillis, long meleeWindowMillis) {

        /** Starting values of the spec — also the fallback for every missing key. */
        public static Levers defaults() {
            Map<Grade, Double> w = new EnumMap<>(Grade.class);
            w.put(Grade.S, 0.12);
            w.put(Grade.A, 0.10);
            w.put(Grade.B, 0.07);
            w.put(Grade.C, 0.04);
            w.put(Grade.D, 0.02);
            return new Levers(3, 6, 0.5,
                    200, 100,
                    4000, 12000,
                    100, 8, 4,
                    0.01, 0.0015,
                    0.03,
                    new double[]{400, 800, 2000, 5000, 12000, 35000},
                    new double[]{12, 18, 30, 45, 60, 75},
                    w,
                    0.08,
                    true, 0.05, 0.005, 1.5,
                    true, 1.25, 0.8,
                    4000L, 700L);
        }
    }

    private static final String[] TIER_KEYS = {"E", "D", "C", "B", "A", "S"};

    private static volatile Levers levers = Levers.defaults();

    private StatFormulas() {}

    public static Levers levers() { return levers; }

    /** Re-read the {@code stats:} block. Missing keys keep the spec defaults. */
    public static void load(ConfigurationSection sec) {
        Levers d = Levers.defaults();
        if (sec == null) { levers = d; return; }
        Map<Grade, Double> w = new EnumMap<>(Grade.class);
        for (Grade g : Grade.values()) {
            w.put(g, sec.getDouble("grade-weights." + g.name(), d.gradeWeights().get(g)));
        }
        levers = new Levers(
                Math.max(0, sec.getInt("points-per-rank", d.pointsPerRank())),
                sec.getDouble("soft-cap.threshold", d.softCap()),
                sec.getDouble("soft-cap.over-factor", d.overCapFactor()),
                sec.getDouble("hp.base", d.hpBase()),
                sec.getDouble("hp.per-vigueur", d.hpPerVigueur()),
                sec.getDouble("chakra.base", d.chakraBase()),
                sec.getDouble("chakra.per-point", d.chakraPerPoint()),
                sec.getDouble("stamina.base", d.staminaBase()),
                sec.getDouble("stamina.per-vigueur", d.staminaPerVigueur()),
                sec.getDouble("stamina.per-taijutsu", d.staminaPerTaijutsu()),
                sec.getDouble("regen.base-fraction", d.regenBase()),
                sec.getDouble("regen.per-controle", d.regenPerControle()),
                sec.getDouble("cost.reduction-per-controle", d.costReductionPerControle()),
                tiers(sec, "cost.chakra", d.chakraCostByTier()),
                tiers(sec, "cost.stamina", d.staminaCostByTier()),
                w,
                sec.getDouble("melee.per-point", d.meleePerPoint()),
                sec.getBoolean("crit.enabled", d.critEnabled()),
                sec.getDouble("crit.base", d.critBase()),
                sec.getDouble("crit.per-controle", d.critPerControle()),
                sec.getDouble("crit.multiplier", d.critMultiplier()),
                sec.getBoolean("nature-wheel.enabled", d.natureWheelEnabled()),
                sec.getDouble("nature-wheel.advantage", d.natureAdvantage()),
                sec.getDouble("nature-wheel.disadvantage", d.natureDisadvantage()),
                Math.max(250L, sec.getLong("attribution.technique-window-millis", d.techniqueWindowMillis())),
                Math.max(100L, sec.getLong("attribution.melee-window-millis", d.meleeWindowMillis())));
    }

    private static double[] tiers(ConfigurationSection sec, String path, double[] def) {
        double[] out = def.clone();
        for (int i = 0; i < TIER_KEYS.length; i++) {
            out[i] = sec.getDouble(path + "." + TIER_KEYS[i], def[i]);
        }
        return out;
    }

    /* ------------------------------------------------------------- curve */

    /** Soft cap: full return up to the threshold, reduced beyond. */
    public static double eff(int raw) {
        Levers l = levers;
        return raw <= l.softCap() ? raw : l.softCap() + (raw - l.softCap()) * l.overCapFactor();
    }

    public static double eff(CharacterStats s, Stat stat) { return eff(s.get(stat)); }

    /* ------------------------------------------------------------ points */

    public static int earnedPoints(Rank rank, CharacterStats s) {
        return levers.pointsPerRank() * rank.statTier() + s.bonusPoints();
    }

    /** May be negative after a staff rank downgrade — allocation is then refused. */
    public static int unspent(Rank rank, CharacterStats s) {
        return earnedPoints(rank, s) - s.spent();
    }

    /* ------------------------------------------------------------- pools */

    public static double maxHp(CharacterStats s) {
        Levers l = levers;
        return l.hpBase() + eff(s, Stat.VIGUEUR) * l.hpPerVigueur();
    }

    public static double maxChakra(CharacterStats s) {
        Levers l = levers;
        return l.chakraBase() + eff(s, Stat.CHAKRA) * l.chakraPerPoint();
    }

    public static double maxStamina(CharacterStats s) {
        Levers l = levers;
        return l.staminaBase() + eff(s, Stat.VIGUEUR) * l.staminaPerVigueur()
                + eff(s, Stat.TAIJUTSU) * l.staminaPerTaijutsu();
    }

    public static double chakraRegenPer10s(CharacterStats s) {
        Levers l = levers;
        return maxChakra(s) * (l.regenBase() + l.regenPerControle() * eff(s, Stat.CONTROLE));
    }

    public static double critChance(CharacterStats s) {
        Levers l = levers;
        if (!l.critEnabled()) return 0.0;
        return Math.max(0.0, Math.min(1.0, l.critBase() + l.critPerControle() * eff(s, Stat.CONTROLE)));
    }

    /* -------------------------------------------------------- techniques */

    /** Fraction removed from every technique cost by Contrôle (0..0.9). */
    public static double costReduction(CharacterStats s) {
        return Math.max(0.0, Math.min(0.9, levers.costReductionPerControle() * eff(s, Stat.CONTROLE)));
    }

    /** Base cost of a tier before any reduction. */
    public static double baseCost(int tier, CostKind kind) {
        Levers l = levers;
        double[] t = kind == CostKind.STAMINA ? l.staminaCostByTier() : l.chakraCostByTier();
        return t[Math.max(0, Math.min(t.length - 1, tier))];
    }

    public static double techniqueCost(CharacterStats s, int tier, CostKind kind) {
        return baseCost(tier, kind) * (1.0 - costReduction(s));
    }

    /** {@code 1 + Σ weight(grade) × eff(stat)} — the technique damage / heal multiplier. */
    public static double powerMultiplier(CharacterStats s, Map<Stat, Grade> scaling) {
        if (scaling == null || scaling.isEmpty()) return 1.0;
        Levers l = levers;
        double sum = 0.0;
        for (Map.Entry<Stat, Grade> e : scaling.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) continue;
            sum += l.gradeWeights().getOrDefault(e.getValue(), 0.0) * eff(s, e.getKey());
        }
        return 1.0 + sum;
    }

    public static double meleeMultiplier(CharacterStats s, boolean armed) {
        return 1.0 + levers.meleePerPoint() * eff(s, armed ? Stat.KENJUTSU : Stat.TAIJUTSU);
    }

    /* ------------------------------------------------------ nature wheel */

    /** Katon › Fūton › Raiton › Doton › Suiton › Katon. */
    public static boolean beats(ChakraAffinity a, ChakraAffinity b) {
        return switch (a) {
            case KATON -> b == ChakraAffinity.FUTON;
            case FUTON -> b == ChakraAffinity.RAITON;
            case RAITON -> b == ChakraAffinity.DOTON;
            case DOTON -> b == ChakraAffinity.SUITON;
            case SUITON -> b == ChakraAffinity.KATON;
            default -> false;
        };
    }

    /**
     * ×advantage with the wheel, ×disadvantage against it (erased when the
     * attacker is two tiers above), 0 on equal nature and tier (the two
     * techniques cancel out), 1 otherwise.
     */
    public static double natureMultiplier(ChakraAffinity att, int attTier, ChakraAffinity def, int defTier) {
        Levers l = levers;
        if (!l.natureWheelEnabled() || att == null || def == null
                || att == ChakraAffinity.NONE || def == ChakraAffinity.NONE) return 1.0;
        if (att == def) return attTier == defTier ? 0.0 : 1.0;
        if (beats(att, def)) return l.natureAdvantage();
        if (beats(def, att)) return attTier - defTier >= 2 ? 1.0 : l.natureDisadvantage();
        return 1.0;
    }

    public static String tierLabel(int tier) {
        return TIER_KEYS[Math.max(0, Math.min(TIER_KEYS.length - 1, tier))].toUpperCase(Locale.ROOT);
    }
}
