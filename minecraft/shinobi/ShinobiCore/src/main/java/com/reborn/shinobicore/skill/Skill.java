package com.reborn.shinobicore.skill;

import com.reborn.shinobicore.character.Affinity;

import java.text.Normalizer;
import java.util.Locale;

/**
 * The skill axes a character invests points into. Each skill is the bonus
 * applied to a {@code /sc roll} of that type.
 *
 * <p>Separate from the body {@link Affinity} (a single archetype that buffs
 * HP/chakra/speed) — but the matching Affinity grants a small synergy bonus
 * on its skill at roll time (see {@link #synergyAffinity()}).
 */
public enum Skill {
    FORCE        ("Force",        Affinity.STRENGTH),
    AGILITE      ("Agilité",      Affinity.AGILITY),
    ENDURANCE    ("Endurance",    null),
    INTELLIGENCE ("Intelligence", Affinity.INTELLIGENCE),
    PERCEPTION   ("Perception",   null),
    CONTROLE     ("Contrôle",     null),
    PRESENCE     ("Présence",     null);

    private final String displayName;
    private final Affinity synergy;

    Skill(String displayName, Affinity synergy) {
        this.displayName = displayName;
        this.synergy = synergy;
    }

    public String displayName() { return displayName; }

    /** The body affinity that grants this skill a synergy bonus, or {@code null}. */
    public Affinity synergyAffinity() { return synergy; }

    /** Tolerant parse by enum name or French display name (accent-insensitive).
     *  Returns {@code null} when nothing matches. */
    public static Skill from(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        for (Skill sk : values()) {
            if (sk.name().equalsIgnoreCase(t) || sk.displayName.equalsIgnoreCase(t)) {
                return sk;
            }
        }
        String norm = stripAccents(t).toLowerCase(Locale.ROOT);
        for (Skill sk : values()) {
            if (stripAccents(sk.displayName).toLowerCase(Locale.ROOT).equals(norm)) return sk;
        }
        return null;
    }

    private static String stripAccents(String in) {
        return Normalizer.normalize(in, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
    }
}
