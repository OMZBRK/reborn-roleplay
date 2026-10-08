package fr.reborn.hud.parchemin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Tirage d'une bibliothèque (docs/PROPOSITION_PARCHEMINS.md §3.1) : chaque technique sort indépendamment avec son
 * pourcentage (celui de son rang, ou la valeur réglée pour elle) ; au-delà du nombre de rouleaux de la
 * bibliothèque, on garde les plus rares — un rouleau rare tiré n'est jamais coupé. Le rang S ne sort que s'il reste
 * un slot. Code pur : il passera tel quel côté serveur.
 */
public final class Tirage {

    /** Pourcentages de départ par rang, indices D C B A S. */
    public static final double[] PUBLIQUE = {35, 12, 3, 0.3, 0.02};
    public static final double[] RESERVEE = {30, 18, 7, 1.0, 0.08};
    public static final double[] PRIVEE = {20, 22, 12, 3.0, 0.3};

    private Tirage() {}

    public static double chance(Technique t, double[] byRank, Map<String, Double> overrides) {
        Double o = overrides == null ? null : overrides.get(t.id());
        return o != null ? o : byRank[t.rankIndex()];
    }

    /**
     * @param sSlotFree vrai si la technique S a encore un slot libre (sinon elle ne peut pas sortir)
     */
    public static List<Technique> draw(List<Technique> pool, double[] byRank, Map<String, Double> overrides, int count,
                                       java.util.function.Predicate<Technique> sSlotFree, Random rnd) {
        List<Technique> hit = new ArrayList<>();
        for (Technique t : pool) {
            if (t.rank() == 'S' && !sSlotFree.test(t)) continue;
            if (rnd.nextDouble() * 100.0 < chance(t, byRank, overrides)) hit.add(t);
        }
        Collections.shuffle(hit, rnd);
        hit.sort(Comparator.comparingInt(Technique::rankIndex).reversed());
        List<Technique> out = new ArrayList<>(hit.subList(0, Math.min(count, hit.size())));
        Collections.shuffle(out, rnd);
        return out;
    }

    /** Statistiques sur {@code n} tirages : rouleaux moyens par rang, et part des tirages avec au moins un A / un S. */
    public record Stats(double[] perDraw, double atLeastA, double atLeastS, double emptySlots) {}

    public static Stats simulate(List<Technique> pool, double[] byRank, Map<String, Double> overrides, int count, int n,
                                 long seed) {
        Random rnd = new Random(seed);
        double[] per = new double[5];
        int withA = 0, withS = 0;
        double empty = 0;
        for (int i = 0; i < n; i++) {
            List<Technique> d = draw(pool, byRank, overrides, count, t -> true, rnd);
            boolean a = false, s = false;
            for (Technique t : d) {
                per[t.rankIndex()]++;
                a |= t.rank() == 'A';
                s |= t.rank() == 'S';
            }
            if (a) withA++;
            if (s) withS++;
            empty += count - d.size();
        }
        for (int k = 0; k < 5; k++) per[k] /= n;
        return new Stats(per, withA * 100.0 / n, withS * 100.0 / n, empty / n);
    }
}
