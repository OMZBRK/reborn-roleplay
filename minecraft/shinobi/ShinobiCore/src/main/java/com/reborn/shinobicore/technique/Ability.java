package com.reborn.shinobicore.technique;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * One entry of {@code abilities.yml}. Immutable after load.
 * Implements the api's read view; the Naruto-flavored accessors
 * (mudras, jutsu meta) are content-interim per BOUNDARY.md.
 */
public final class Ability implements com.reborn.shinobicore.api.TechniqueRegistry.Technique {

    private final String id;
    private final String name;
    private final String category;       // e.g. "ninjutsu/katon"
    private final JutsuRank rank;
    private final ExecutionType execution;
    private final MinigameType minigame;
    private final Difficulty difficulty;
    private final List<Mudra> mudras;    // may be empty
    /** Free-form incantation steps for non-mudra arts (taijutsu
     *  movements, kenjutsu stances…). Used only when {@link #mudras}
     *  is empty. */
    private final List<String> steps;
    private final String description;
    private final JutsuMeta jutsu;       // nullable

    public Ability(String id, String name, String category, JutsuRank rank,
                   ExecutionType execution, MinigameType minigame,
                   Difficulty difficulty, List<Mudra> mudras,
                   List<String> steps,
                   String description, JutsuMeta jutsu) {
        this.id = id;
        this.name = name;
        this.category = category == null ? "" : category.trim().toLowerCase(Locale.ROOT);
        this.rank = rank;
        this.execution = execution;
        this.minigame = minigame;
        this.difficulty = difficulty;
        this.mudras = mudras == null ? List.of() : List.copyOf(mudras);
        this.steps = steps == null ? List.of() : List.copyOf(steps);
        this.description = description == null ? "" : description;
        this.jutsu = jutsu;
    }

    public String id()               { return id; }
    public String name()             { return name; }
    public String category()         { return category; }
    public JutsuRank rank()          { return rank; }
    public ExecutionType execution() { return execution; }
    public MinigameType minigame()   { return minigame; }
    public Difficulty difficulty()   { return difficulty; }
    public String description()      { return description; }

    /** Unmodifiable; empty when none declared. */
    public List<Mudra> mudras() { return Collections.unmodifiableList(mudras); }

    /** Unmodifiable free-form steps; empty when none declared. */
    public List<String> steps() { return Collections.unmodifiableList(steps); }

    /** Nullable — present only for castable jutsu. */
    public JutsuMeta jutsu() { return jutsu; }

    public boolean isCastable() { return jutsu != null; }

    /**
     * Display labels driving the incantation (and the MUDRA minigame
     * prompts): mudra names first, else the free-form steps, else
     * {@code null} — callers fall back to a plain-name display with the
     * rank-based step count.
     */
    public List<String> incantationLabels() {
        if (!mudras.isEmpty()) {
            List<String> out = new java.util.ArrayList<>(mudras.size());
            for (Mudra m : mudras) out.add(m.display());
            return out;
        }
        if (!steps.isEmpty()) return Collections.unmodifiableList(steps);
        return null;
    }

    /**
     * Incantation step count. An explicit mudra/steps list is the
     * source of truth; rank is only the fallback when neither exists.
     */
    public int mudraCount() {
        if (!mudras.isEmpty()) return mudras.size();
        if (!steps.isEmpty()) return steps.size();
        return rank.defaultMudraCount();
    }

    /** Last segment of the category path ("ninjutsu/katon" → "katon"). */
    public String categoryLeaf() {
        int i = category.lastIndexOf('/');
        return i < 0 ? category : category.substring(i + 1);
    }
}
