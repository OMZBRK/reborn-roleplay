package com.reborn.shinobicore.technique;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The twelve hand seals. YAML accepts either the English keys used by
 * the legacy ability files ({@code tiger, hare, monkey, dog, …}) or
 * the French display names; rendering is always French.
 */
public enum Mudra {
    RAT("Rat"),
    OX("Buffle"),
    TIGER("Tigre"),
    HARE("Lièvre"),
    DRAGON("Dragon"),
    SNAKE("Serpent"),
    HORSE("Cheval"),
    RAM("Bélier"),
    MONKEY("Singe"),
    BIRD("Coq"),
    DOG("Chien"),
    BOAR("Sanglier");

    private final String display;

    Mudra(String display) { this.display = display; }

    /** French display name (Tigre, Lièvre, …). */
    public String display() { return display; }

    /** Tolerant parse over enum name, English alias and French display
     *  (accent-insensitive). Returns {@code null} on unknown input so
     *  the registry can warn about typos instead of silently mapping. */
    public static Mudra from(String s) {
        if (s == null || s.isBlank()) return null;
        String n = s.trim().toUpperCase(Locale.ROOT);
        try { return Mudra.valueOf(n); } catch (IllegalArgumentException ignore) { }
        String plain = strip(n);
        for (Mudra m : values()) {
            if (strip(m.display.toUpperCase(Locale.ROOT)).equals(plain)) return m;
        }
        // Common alternates seen in older files.
        return switch (plain) {
            case "OISEAU" -> BIRD;
            case "BOEUF" -> OX;
            case "LIEVRE", "LAPIN", "RABBIT" -> HARE;
            case "BELIER" -> RAM;
            default -> null;
        };
    }

    private static String strip(String s) {
        return s.replace("É", "E").replace("È", "E").replace("Ê", "E")
                .replace("À", "A").replace("Î", "I").replace("Ô", "O");
    }

    /** Random non-repeating-adjacent sequence of {@code n} seals, used
     *  when an ability has no explicit mudra list. */
    public static List<Mudra> randomSequence(int n) {
        List<Mudra> out = new ArrayList<>(Math.max(0, n));
        Mudra last = null;
        for (int i = 0; i < n; i++) {
            Mudra pick;
            do {
                pick = values()[ThreadLocalRandom.current().nextInt(values().length)];
            } while (pick == last && values().length > 1);
            out.add(pick);
            last = pick;
        }
        return out;
    }
}
