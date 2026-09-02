package com.reborn.shinobilearning.academy;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

/**
 * Per-character Academy progress. Owned by ShinobiLearning's own store
 * (keyed by the ShinobiCore character UUID), so Core needs no new fields.
 */
public final class AcademyData implements com.reborn.shinobicore.data.CharacterData {

    private final UUID characterId;
    private boolean enrolled;
    private boolean graduated;
    private final EnumSet<Lesson> completed = EnumSet.noneOf(Lesson.class);

    private transient boolean dirty;

    public AcademyData(UUID characterId) { this.characterId = characterId; }

    public UUID characterId()       { return characterId; }
    public boolean enrolled()       { return enrolled; }
    public boolean graduated()      { return graduated; }
    public Set<Lesson> completed()  { return completed; }
    public boolean hasCompleted(Lesson l) { return l != null && completed.contains(l); }

    public void setEnrolled(boolean v)  { enrolled = v; dirty = true; }
    public void setGraduated(boolean v) { graduated = v; dirty = true; }

    public boolean complete(Lesson l) {
        if (l == null) return false;
        boolean added = completed.add(l);
        if (added) dirty = true;
        return added;
    }

    public boolean uncomplete(Lesson l) {
        if (l == null) return false;
        boolean removed = completed.remove(l);
        if (removed) dirty = true;
        return removed;
    }

    public boolean dirty()  { return dirty; }
    public void markClean() { dirty = false; }

    /** Used by the store on load (no dirty flip). */
    void load(boolean enrolled, boolean graduated, Set<Lesson> done) {
        this.enrolled = enrolled;
        this.graduated = graduated;
        this.completed.clear();
        if (done != null) this.completed.addAll(done);
        this.dirty = false;
    }
}
