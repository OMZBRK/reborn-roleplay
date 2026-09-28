package fr.reborn.hud.menu.stats;

import java.util.Map;

/**
 * Formules de la fiche, <b>miroir exact</b> de {@code StatFormulas} (ShinobiCore)
 * avec les leviers poussés par le serveur — sert uniquement à prévisualiser une
 * allocation en attente. Toute modification côté serveur doit se refléter ici.
 */
public final class StatsMath {

    private StatsMath() {}

    public static double eff(StatsData.Levers l, int raw) {
        return raw <= l.softCap() ? raw : l.softCap() + (raw - l.softCap()) * l.overFactor();
    }

    private static double e(StatsData.Levers l, int[] s, StatDef d) {
        return eff(l, s[d.ordinal()]);
    }

    public static double maxHp(StatsData.Levers l, int[] s) {
        return l.hpBase() + e(l, s, StatDef.VIGUEUR) * l.hpPerVig();
    }

    public static double maxChakra(StatsData.Levers l, int[] s) {
        return l.chakraBase() + e(l, s, StatDef.CHAKRA) * l.chakraPerPoint();
    }

    public static double maxStamina(StatsData.Levers l, int[] s) {
        return l.stBase() + e(l, s, StatDef.VIGUEUR) * l.stPerVig() + e(l, s, StatDef.TAIJUTSU) * l.stPerTai();
    }

    public static double regenPer10s(StatsData.Levers l, int[] s) {
        return maxChakra(l, s) * (l.regenBase() + l.regenPerCtrl() * e(l, s, StatDef.CONTROLE));
    }

    public static double costReduction(StatsData.Levers l, int[] s) {
        return Math.max(0.0, Math.min(0.9, l.costRedPerCtrl() * e(l, s, StatDef.CONTROLE)));
    }

    public static double crit(StatsData.Levers l, int[] s) {
        if (!l.critEnabled()) return 0.0;
        return Math.max(0.0, Math.min(1.0, l.critBase() + l.critPerCtrl() * e(l, s, StatDef.CONTROLE)));
    }

    public static double melee(StatsData.Levers l, int[] s, boolean armed) {
        return 1.0 + l.meleePerPoint() * e(l, s, armed ? StatDef.KENJUTSU : StatDef.TAIJUTSU);
    }

    /** {@code 1 + Σ poids(lettre) × eff(stat)}. */
    public static double power(StatsData.Levers l, int[] s, Map<StatDef, String> scaling) {
        double sum = 0.0;
        for (Map.Entry<StatDef, String> en : scaling.entrySet()) {
            sum += l.weights().getOrDefault(en.getValue(), 0.0) * e(l, s, en.getKey());
        }
        return 1.0 + sum;
    }

    /** Coût de la technique pour ces stats (hors maîtrise). */
    public static double cost(StatsData.Levers l, int[] s, StatsData.Tech t) {
        double[] table = t.stamina() ? l.staminaCost() : l.chakraCost();
        double base = table[Math.max(0, Math.min(table.length - 1, t.tier()))] * t.costFactor();
        return base * (1.0 - costReduction(l, s));
    }

    /** Lancers possibles à réserve pleine — la lecture « économie » de la spec §1.4. */
    public static int casts(StatsData.Levers l, int[] s, StatsData.Tech t) {
        double c = cost(l, s, t);
        double pool = t.stamina() ? maxStamina(l, s) : maxChakra(l, s);
        return c <= 0 ? 0 : (int) Math.floor(pool / c);
    }
}
