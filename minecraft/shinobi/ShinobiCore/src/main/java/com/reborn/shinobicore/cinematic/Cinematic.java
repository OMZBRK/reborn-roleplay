package com.reborn.shinobicore.cinematic;

import java.util.ArrayList;
import java.util.List;

/**
 * A named, ordered sequence of {@link CinematicAnchor}s. List order is the
 * playback order; the management GUI mutates it in place.
 */
public final class Cinematic {

    private final String name;
    private final List<CinematicAnchor> anchors = new ArrayList<>();

    public Cinematic(String name) {
        this.name = name;
    }

    public String name() { return name; }

    public List<CinematicAnchor> anchors() { return anchors; }

    public int size() { return anchors.size(); }

    public boolean isEmpty() { return anchors.isEmpty(); }

    public CinematicAnchor anchor(int index) {
        return (index >= 0 && index < anchors.size()) ? anchors.get(index) : null;
    }

    /** Append a fresh anchor and return it (for the editor). */
    public CinematicAnchor addAnchor() {
        CinematicAnchor a = new CinematicAnchor();
        anchors.add(a);
        return a;
    }

    public void addAnchor(CinematicAnchor a) {
        if (a != null) anchors.add(a);
    }

    public boolean removeAnchor(int index) {
        if (index < 0 || index >= anchors.size()) return false;
        anchors.remove(index);
        return true;
    }

    /** Move the anchor at {@code index} one step earlier (toward 0). */
    public boolean moveUp(int index) {
        if (index <= 0 || index >= anchors.size()) return false;
        anchors.add(index - 1, anchors.remove(index));
        return true;
    }

    /** Move the anchor at {@code index} one step later. */
    public boolean moveDown(int index) {
        if (index < 0 || index >= anchors.size() - 1) return false;
        anchors.add(index + 1, anchors.remove(index));
        return true;
    }
}
