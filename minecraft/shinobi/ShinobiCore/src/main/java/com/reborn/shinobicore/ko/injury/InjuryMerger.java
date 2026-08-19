package com.reborn.shinobicore.ko.injury;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Consolidates same-type same-severity injuries into a single higher
 * tier when enough accumulate on a body part.
 *
 * <h2>Thresholds</h2>
 * <table>
 *   <tr><th>Severity</th><th>Threshold</th><th>Result</th></tr>
 *   <tr><td>FAIBLE</td>   <td>7</td><td>1 MOYEN</td></tr>
 *   <tr><td>MOYEN</td>    <td>5</td><td>1 IMPORTANT</td></tr>
 *   <tr><td>IMPORTANT</td><td>3</td><td>1 URGENT</td></tr>
 *   <tr><td>URGENT</td>   <td>—</td><td>cap</td></tr>
 * </table>
 *
 * <p>The merge applies to <em>every</em> {@link InjuryType}, not just
 * Hématome — burns stack, cuts stack, infections stack. Hidden
 * injuries (see below) are ignored when counting and never merge.
 *
 * <h2>Hidden Os cassé</h2>
 * When a Hématome bucket merges into IMPORTANT or URGENT there's a
 * roll for a covert {@link InjuryType#OS_CASSE} on the same body
 * part, marked {@link Injury#hidden hidden}. Hidden injuries are
 * filtered out of every silhouette / list / count, simulating a
 * fracture masked by the visible bruise. They become visible once
 * the parent Hématome is treated by a successful {@code /soigner}
 * (handled in {@code TreatmentApplier.succeed}).
 */
public final class InjuryMerger {

    public static final int FAIBLE_TO_MOYEN     = 7;
    public static final int MOYEN_TO_IMPORTANT  = 5;
    public static final int IMPORTANT_TO_URGENT = 3;

    /** Probability that a Hématome merge into IMPORTANT also spawns
     *  a hidden Os cassé. */
    public static final double HIDDEN_OS_CHANCE_IMPORTANT = 0.30;
    /** Probability that a Hématome merge into URGENT also spawns
     *  a hidden Os cassé. */
    public static final double HIDDEN_OS_CHANCE_URGENT    = 0.50;

    private InjuryMerger() {}

    /** Walk the injury list, repeatedly merging until no group hits
     *  its threshold. Returns true if anything changed. The list is
     *  mutated in place. */
    public static boolean mergeAll(List<Injury> injuries) {
        boolean anyChange = false;
        boolean iterChange;
        do {
            iterChange = false;
            // Group visible injuries by (part, type, severity).
            Map<Key, List<Injury>> groups = new HashMap<>();
            for (Injury inj : injuries) {
                if (inj.hidden()) continue;
                Key k = new Key(inj.bodyPart(), inj.type(), inj.severity());
                groups.computeIfAbsent(k, x -> new ArrayList<>()).add(inj);
            }
            for (Map.Entry<Key, List<Injury>> e : groups.entrySet()) {
                Key k = e.getKey();
                int threshold = thresholdFor(k.severity());
                if (threshold <= 0) continue;             // URGENT — no further merge
                if (e.getValue().size() < threshold) continue;

                Severity next = k.severity().upgrade();
                if (next == k.severity()) continue;       // safety guard

                // Remove `threshold` matching visible injuries from
                // the master list.
                int removed = 0;
                Iterator<Injury> it = injuries.iterator();
                while (it.hasNext() && removed < threshold) {
                    Injury i = it.next();
                    if (i.hidden()) continue;
                    if (i.bodyPart() == k.part()
                            && i.type() == k.type()
                            && i.severity() == k.severity()) {
                        it.remove();
                        removed++;
                    }
                }

                // Add the upgraded injury — fresh timestamps, no
                // cooldown (it's a brand-new wound at its new tier).
                injuries.add(Injury.create(k.part(), k.type(),
                        DamageOrigin.AUTRE, next));

                // Hématome → IMPORTANT/URGENT spawns a hidden bone
                // fracture on the same body part with some chance.
                // Severity is FAIBLE for IMPORTANT escalations, MOYEN
                // for URGENT (a worse bruise hides a worse break).
                if (k.type() == InjuryType.HEMATOME
                        && (next == Severity.IMPORTANT || next == Severity.URGENT)) {
                    double chance = next == Severity.URGENT
                            ? HIDDEN_OS_CHANCE_URGENT
                            : HIDDEN_OS_CHANCE_IMPORTANT;
                    if (Math.random() < chance) {
                        Severity hiddenSev = next == Severity.URGENT
                                ? Severity.MOYEN : Severity.FAIBLE;
                        Injury secret = Injury.create(k.part(),
                                InjuryType.OS_CASSE, DamageOrigin.AUTRE,
                                hiddenSev);
                        secret.setHidden(true);
                        injuries.add(secret);
                    }
                }

                iterChange = true;
                anyChange = true;
                break; // re-group from scratch — list shape changed
            }
        } while (iterChange);
        return anyChange;
    }

    /** Reveal every hidden injury sitting on {@code bodyPart}. Used
     *  by {@code TreatmentApplier.succeed} when a parent injury is
     *  healed — the medic discovers what was lurking underneath. */
    public static List<Injury> revealHiddenOn(List<Injury> injuries,
                                              BodyPart bodyPart) {
        List<Injury> revealed = new ArrayList<>();
        for (Injury inj : injuries) {
            if (inj.bodyPart() == bodyPart && inj.hidden()) {
                inj.setHidden(false);
                inj.setNextHealableMillis(0L); // immediately treatable
                revealed.add(inj);
            }
        }
        return revealed;
    }

    /* ---------------------------------------------------- internal */

    private static int thresholdFor(Severity s) {
        return switch (s) {
            case FAIBLE    -> FAIBLE_TO_MOYEN;
            case MOYEN     -> MOYEN_TO_IMPORTANT;
            case IMPORTANT -> IMPORTANT_TO_URGENT;
            case URGENT    -> 0;
        };
    }

    private record Key(BodyPart part, InjuryType type, Severity severity) {}
}
