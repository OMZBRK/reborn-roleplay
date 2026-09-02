package com.reborn.shinobicore.character;

import com.reborn.shinobicore.skill.Skill;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * One source of truth for everything a character has learned: ability
 * mastery (an ability is <em>known</em> iff it has a mastery entry —
 * the legacy separate known-set is gone, so the two can never drift),
 * plus the invested skill ratings and unspent skill points.
 *
 * <p><b>Unified semantic:</b> setting or bumping mastery on an ability
 * establishes it as known. Under {@code require-learned: false} (the
 * shipped testing config) this means casting an unlearned jutsu now
 * also learns it at its first mastery tick — the one deliberate
 * semantic consequence of the collapse, unreachable once
 * require-learned is on.
 *
 * <p>Insertion order of learning is preserved (LinkedHashMap), matching
 * the old LinkedHashSet behaviour of the known list.
 */
public final class LearnedState {

    /** ability id → mastery 0..100; key present == known. */
    private final Map<String, Integer> abilities = new LinkedHashMap<>();
    /** Per-skill invested rating (0+). */
    private final Map<Skill, Integer> skills = new EnumMap<>(Skill.class);
    /** Unspent skill points earned from levels / missions / training. */
    private int skillPoints;
    /** Bumped on every mutation; the owning character compares it against
     *  the value recorded at its last save to derive its dirty flag. */
    private transient long mutations;

    /** Monotonic mutation counter for dirty tracking. */
    public long mutationCount() { return mutations; }

    /* ------------------------------------------------------------ abilities */

    /** True if the ability is known (has a mastery entry). */
    public boolean knows(String abilityId) {
        return abilityId != null && abilities.containsKey(abilityId);
    }

    /** Learn an ability at mastery 0. Returns true if it wasn't known. */
    public boolean learn(String abilityId) {
        if (abilityId == null || abilityId.isBlank()) return false;
        boolean added = abilities.putIfAbsent(abilityId, 0) == null;
        if (added) mutations++;
        return added;
    }

    /** Forget an ability (drops its mastery). Returns true if it was known. */
    public boolean forget(String abilityId) {
        if (abilityId == null) return false;
        boolean removed = abilities.remove(abilityId) != null;
        if (removed) mutations++;
        return removed;
    }

    /** Unmodifiable view of the known ability ids, learn order. */
    public Set<String> knownView() {
        return Collections.unmodifiableSet(abilities.keySet());
    }

    /** Mastery 0..100 in an ability (0 when unknown or untrained). */
    public int mastery(String abilityId) {
        return abilityId == null ? 0 : abilities.getOrDefault(abilityId, 0);
    }

    /** Set mastery (clamped 0..100); establishes the ability as known. */
    public void setMastery(String abilityId, int value) {
        if (abilityId == null || abilityId.isBlank()) return;
        abilities.put(abilityId, Math.max(0, Math.min(100, value)));
        mutations++;
    }

    /** Bump mastery by {@code delta}; establishes the ability as known. */
    public void addMastery(String abilityId, int delta) {
        setMastery(abilityId, mastery(abilityId) + delta);
    }

    /** Unmodifiable view of ability id → mastery, learn order. */
    public Map<String, Integer> masteryView() {
        return Collections.unmodifiableMap(abilities);
    }

    /* --------------------------------------------------------------- skills */

    /** Invested rating in a skill (0 when never trained). */
    public int skill(Skill s) {
        return s == null ? 0 : skills.getOrDefault(s, 0);
    }

    /** Set the invested rating of a skill (clamped to >= 0). */
    public void setSkill(Skill s, int value) {
        if (s != null) { skills.put(s, Math.max(0, value)); mutations++; }
    }

    /** Unmodifiable view of invested skill ratings. */
    public Map<Skill, Integer> skillsView() {
        return Collections.unmodifiableMap(skills);
    }

    public int skillPoints() { return skillPoints; }

    public void setSkillPoints(int points) { this.skillPoints = Math.max(0, points); mutations++; }

    public void addSkillPoints(int delta) { setSkillPoints(skillPoints + delta); }
}
