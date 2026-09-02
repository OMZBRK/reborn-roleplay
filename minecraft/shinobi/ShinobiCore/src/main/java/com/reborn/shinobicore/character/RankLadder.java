package com.reborn.shinobicore.character;

import com.reborn.shinobicore.api.Internal;
import com.reborn.shinobicore.api.ProgressionLadder;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@link ProgressionLadder} backed by the legacy {@link Rank} enum.
 * Interim implementation until the rung list moves into world config;
 * ids are the enum names, which is exactly what characters persist.
 */
@Internal
public final class RankLadder implements ProgressionLadder {

    private record RankRung(Rank rank) implements Rung {
        @Override public String id() { return rank.name(); }
        @Override public String displayName() { return rank.displayName(); }
        @Override public int ordinal() { return rank.ordinal(); }
    }

    private final List<RankRung> rungs;

    public RankLadder() {
        List<RankRung> out = new ArrayList<>();
        for (Rank r : Rank.values()) out.add(new RankRung(r));
        this.rungs = List.copyOf(out);
    }

    @Override
    public List<? extends Rung> rungs() { return rungs; }

    @Override
    public Rung byId(String id) {
        if (id == null) return null;
        String norm = id.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        for (RankRung r : rungs) {
            if (r.id().equals(norm)) return r;
        }
        return null;
    }
}
