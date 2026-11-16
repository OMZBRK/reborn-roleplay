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

    /**
     * Catégories dont les techniques se lancent par des signes des mains (15/11) : ninjutsu, kekkei genkai de nature,
     * genjutsu, kinjutsu, juinjutsu, senjutsu. Pas les arts du corps ni des armes (taijutsu, bukijutsu, kugutsu), ni les
     * dōjutsu (les yeux ne demandent aucun signe), ni « autres/divers » (suites déclarées au cas par cas).
     */
    public static boolean aSignes(String categorie) {
        if (categorie == null) return false;
        String c = categorie.trim().toLowerCase(Locale.ROOT);
        if (c.startsWith("kekkei/dojutsu")) return false;
        return c.startsWith("ninjutsu/") || c.startsWith("kekkei/") || c.startsWith("senjutsu/")
                || c.equals("autres/genjutsu") || c.equals("autres/kinjutsu") || c.equals("autres/juinjutsu");
    }

    /**
     * Suite <b>fixe</b> de {@code n} signes tirée d'une graine (l'identifiant de la technique) : la même à chaque
     * démarrage et sur toute machine, sans deux signes identiques à la suite. Sert aux techniques à signes dont
     * {@code abilities.yml} ne déclare pas les mudras : la fiche, le parchemin, l'apprentissage et le lancer montrent
     * alors la même suite. Déclarer {@code mudras:} sur la technique la remplace.
     */
    public static List<Mudra> suiteStable(String graine, int n) {
        long h = 0xcbf29ce484222325L;                                   // FNV-1a 64 bits de l'identifiant
        for (byte b : (graine == null ? "" : graine).getBytes(java.nio.charset.StandardCharsets.UTF_8)) {
            h ^= (b & 0xff);
            h *= 0x100000001b3L;
        }
        Mudra[] v = values();
        List<Mudra> out = new ArrayList<>(Math.max(0, n));
        Mudra last = null;
        for (int i = 0; i < n; i++) {
            h += 0x9E3779B97F4A7C15L;                                   // SplitMix64
            long z = h;
            z = (z ^ (z >>> 30)) * 0xBF58476D1CE4E5B9L;
            z = (z ^ (z >>> 27)) * 0x94D049BB133111EBL;
            z ^= (z >>> 31);
            int k = (int) Long.remainderUnsigned(z, v.length);
            if (v[k] == last) k = (k + 1 + (int) Long.remainderUnsigned(z >>> 32, v.length - 1)) % v.length;
            out.add(v[k]);
            last = v[k];
        }
        return out;
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
