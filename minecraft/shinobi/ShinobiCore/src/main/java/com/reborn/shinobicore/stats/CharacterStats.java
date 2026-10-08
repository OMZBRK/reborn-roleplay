package com.reborn.shinobicore.stats;

import com.reborn.shinobicore.api.StatsService;
import com.reborn.shinobicore.api.StatsService.Stat;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;

/**
 * The six allocated stats of one character, plus the staff / migration bonus
 * points. Values are always in [{@link StatsService#MIN}, {@link StatsService#MAX}].
 *
 * <p>Points available = {@code POINTS_PER_RANK × rank.statTier() + bonus}; the
 * unspent count is <em>derived</em> ({@code available − spent}), never stored,
 * so a staff rank change or a respec can't desync it.
 *
 * <p>Tracks its own mutation counter, like {@code LearnedState} and
 * {@code ChakraPool}, so {@code ShinobiCharacter#dirty()} sees stat edits.
 */
public final class CharacterStats {

    private final EnumMap<Stat, Integer> values = new EnumMap<>(Stat.class);
    private int bonusPoints;
    private long mutations;
    /** Grade du personnage (bonus passifs) — tenu à jour par {@code ShinobiCharacter}, jamais persisté ici. */
    private com.reborn.shinobicore.character.Rank grade;

    public CharacterStats() {
        for (Stat s : Stat.values()) values.put(s, StatsService.MIN);
    }

    public long mutationCount() { return mutations; }

    public int get(Stat s) { return values.getOrDefault(s, StatsService.MIN); }

    /** Clamped write. Returns the stored value. */
    public int set(Stat s, int value) {
        int v = Math.max(StatsService.MIN, Math.min(StatsService.MAX, value));
        Integer prev = values.put(s, v);
        if (prev == null || prev != v) mutations++;
        return v;
    }

    /** Points invested above the free minimum. */
    public int spent() {
        int sum = 0;
        for (int v : values.values()) sum += v - StatsService.MIN;
        return sum;
    }

    public int bonusPoints() { return bonusPoints; }

    public com.reborn.shinobicore.character.Rank grade() { return grade; }

    public void setGrade(com.reborn.shinobicore.character.Rank grade) { this.grade = grade; }

    public void setBonusPoints(int points) {
        int v = Math.max(0, points);
        if (v != bonusPoints) { bonusPoints = v; mutations++; }
    }

    /** Every stat back to MIN. Bonus points are kept. */
    public void reset() {
        for (Stat s : Stat.values()) set(s, StatsService.MIN);
    }

    /** Forces a save of an otherwise unchanged block (legacy migration). */
    public void touch() { mutations++; }

    /** Immutable snapshot, display order. */
    public Map<Stat, Integer> snapshot() {
        return Collections.unmodifiableMap(new EnumMap<>(values));
    }
}
