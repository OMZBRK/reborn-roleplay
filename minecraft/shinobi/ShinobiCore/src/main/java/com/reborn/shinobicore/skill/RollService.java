package com.reborn.shinobicore.skill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Pure resolver for the three-band roll model (see
 * {@code ShinobiCore_Rolls_Skills_DESIGN.md}):
 *
 * <ul>
 *   <li>{@code skill >= DC} → auto-success (no roll)</li>
 *   <li>{@code DC > skill + DIE_MAX} → auto-fail</li>
 *   <li>otherwise → roll {@code d20 + skill} vs DC, with degrees of success</li>
 * </ul>
 *
 * No Bukkit dependency — trivially testable.
 */
public final class RollService {

    /** Maximum value of the random component (a d20). */
    public static final int DIE_MAX = 20;

    /** At or above this skill, dice under 5 are treated as 5 (reliable talent). */
    public static final int COMPETENCE_FLOOR_SKILL = 15;

    private RollService() {}

    private static final Map<String, Integer> PRESETS = new LinkedHashMap<>();
    static {
        PRESETS.put("trivial", 5);
        PRESETS.put("facile", 10);
        PRESETS.put("moyen", 15);
        PRESETS.put("difficile", 20);
        PRESETS.put("tresdifficile", 25);
        PRESETS.put("heroique", 30);
        PRESETS.put("legendaire", 35);
    }

    /** Difficulty preset labels for tab-completion. */
    public static List<String> presetNames() {
        return List.of("trivial", "facile", "moyen", "difficile",
                "tres-difficile", "heroique", "legendaire");
    }

    /** Parse a DC token: an integer, or a named preset (accent / dash / 's'
     *  tolerant). Returns {@code null} when the token is neither. */
    public static Integer parseDc(String token) {
        if (token == null) return null;
        String t = token.trim();
        if (t.isEmpty()) return null;
        try {
            return Integer.parseInt(t);
        } catch (NumberFormatException ignore) {
            // fall through to preset lookup
        }
        String key = t.toLowerCase(Locale.ROOT)
                .replace("-", "").replace("_", "").replace(" ", "")
                .replace("é", "e").replace("è", "e").replace("ê", "e").replace("à", "a");
        return PRESETS.get(key);
    }

    /** Resolve a roll. {@code dc == null} runs OPEN mode (just total = skill + die). */
    public static RollResult resolve(int skill, Integer dc) {
        int die = ThreadLocalRandom.current().nextInt(1, DIE_MAX + 1);

        if (dc == null) {
            return new RollResult(RollResult.Outcome.OPEN, die, skill, skill + die, null);
        }
        if (skill >= dc) {
            return new RollResult(RollResult.Outcome.AUTO_SUCCESS, 0, skill, skill, dc);
        }
        if (dc > skill + DIE_MAX) {
            return new RollResult(RollResult.Outcome.AUTO_FAIL, 0, skill, skill, dc);
        }

        // Uncertain band — the only place luck matters.
        int effDie = (skill >= COMPETENCE_FLOOR_SKILL && die < 5) ? 5 : die;
        int total = effDie + skill;

        RollResult.Outcome outcome;
        if (die == DIE_MAX || total >= dc + 10) {
            outcome = RollResult.Outcome.CRITICAL;
        } else if (total >= dc) {
            outcome = RollResult.Outcome.SUCCESS;
        } else if (total >= dc - 5) {
            outcome = RollResult.Outcome.PARTIAL;
        } else {
            outcome = RollResult.Outcome.FAILURE;
        }

        // A natural 1 never crits and costs one degree — but only here, never
        // on an auto-success task. Experts can't fumble the trivial.
        if (die == 1) {
            outcome = switch (outcome) {
                case CRITICAL -> RollResult.Outcome.SUCCESS;
                case SUCCESS -> RollResult.Outcome.PARTIAL;
                case PARTIAL -> RollResult.Outcome.FAILURE;
                default -> outcome;
            };
        }
        return new RollResult(outcome, effDie, skill, total, dc);
    }
}
