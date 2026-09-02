package com.reborn.shinobitail.beast;

import org.bukkit.Color;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 * One configured Tailed Beast (beasts.yml → {@code beasts.<id>}).
 * Stage count equals tail count; stage stats are pre-resolved at load.
 */
public final class BeastDefinition {

    private final String id;
    private final String name;
    private final int tails;
    private final Personality personality;
    private final Color auraColor;
    private final double rageMultiplier;
    private final double entryDifficultyDivisor;
    private final List<String> whispers;
    private final List<String> confrontLines;
    private final Map<Integer, BeastStage> stages;

    public BeastDefinition(String id, String name, int tails,
                           Personality personality, Color auraColor,
                           double rageMultiplier, double entryDifficultyDivisor,
                           List<String> whispers, List<String> confrontLines,
                           Map<Integer, BeastStage> stages) {
        this.id = id;
        this.name = name;
        this.tails = tails;
        this.personality = personality;
        this.auraColor = auraColor;
        this.rageMultiplier = rageMultiplier;
        this.entryDifficultyDivisor = entryDifficultyDivisor;
        this.whispers = whispers;
        this.confrontLines = confrontLines;
        this.stages = stages;
    }

    public String id()                      { return id; }
    public String beastName()               { return name; }
    public int tails()                      { return tails; }
    public Personality personality()        { return personality; }
    public Color auraColor()                { return auraColor; }
    /** Combined personality × per-beast rage growth multiplier. */
    public double rageMultiplier()          { return rageMultiplier * personality.rageMultiplier(); }
    /** Stage-1 trigger chances are divided by this (≥ 1 for low-tail beasts). */
    public double entryDifficultyDivisor()  { return entryDifficultyDivisor; }
    public List<String> whispers()          { return whispers; }
    public List<String> confrontLines()     { return confrontLines; }

    /** Resolved stage stats; {@code stage} is clamped into [1, tails]. */
    public BeastStage stage(int stage) {
        int s = Math.max(1, Math.min(tails, stage));
        return stages.get(s);
    }

    public String randomWhisper() {
        if (whispers.isEmpty()) return null;
        return whispers.get(ThreadLocalRandom.current().nextInt(whispers.size()));
    }

    public String randomConfrontLine() {
        if (confrontLines.isEmpty()) return null;
        return confrontLines.get(ThreadLocalRandom.current().nextInt(confrontLines.size()));
    }
}
