package com.reborn.shinobicore.technique;

import com.reborn.shinobicore.api.StatsService.CostKind;
import com.reborn.shinobicore.api.StatsService.Grade;
import com.reborn.shinobicore.api.StatsService.Stat;
import com.reborn.shinobicore.character.ChakraAffinity;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * How a technique relates to the stats (SPEC_STATS_SERVICE §1.3): its scaling
 * letters, its nature on the wheel, what it is paid in, and whether ShinobiCore
 * scales its damage automatically.
 *
 * <p>Read from optional keys of an {@code abilities.yml} entry:
 * <pre>
 *   scaling: { ninjutsu: S, controle: C }   # letters S/A/B/C/D per stat
 *   nature: KATON                            # wheel; default = category leaf
 *   cost-kind: STAMINA                       # CHAKRA / STAMINA; default by category
 *   cost-factor: 1.5                         # × the rank's base cost
 *   scaling-mode: MANUAL                     # AUTO (default) / MANUAL
 *   attribution-millis: 30000                # hits scaled this long after the cast
 * </pre>
 * Every key has a category default, so the 218 existing entries get a
 * coherent profile without being edited — {@link #explicitScaling()} tells the
 * sheet which letters are designer-set and which are inferred.
 *
 * <p>{@code scaling-mode: MANUAL} turns the automatic damage attribution off
 * for that technique: the spell (MagicSpells / MythicMobs) scales itself with
 * the {@code %power%} placeholder instead. Never both — that would scale twice.
 *
 * @param tier          E=0, D=1, C=2, B=3, A=4, S/HIDEN=5
 * @param windowMillis  attribution window; 0 = the {@code stats.attribution} default
 *                      (raise it for a summon / a lingering zone that keeps hitting)
 */
public record TechniqueProfile(Map<Stat, Grade> scaling,
                               boolean explicitScaling,
                               ChakraAffinity nature,
                               CostKind costKind,
                               double costFactor,
                               boolean autoScale,
                               int tier,
                               long windowMillis) {

    public TechniqueProfile {
        scaling = Collections.unmodifiableMap(new EnumMap<>(scaling.isEmpty()
                ? Map.of(Stat.NINJUTSU, Grade.B) : scaling));
    }

    public static int tierOf(JutsuRank rank) {
        return switch (rank) {
            case E -> 0;
            case D -> 1;
            case C -> 2;
            case B -> 3;
            case A -> 4;
            case HIDEN -> 5;
        };
    }

    /** Build the profile of one entry; {@code warn} receives parse problems. */
    public static TechniqueProfile parse(String id, String category, JutsuRank rank,
                                         Map<?, ?> m, Consumer<String> warn) {
        String cat = category == null ? "" : category.toLowerCase(Locale.ROOT);

        Map<Stat, Grade> scaling = new LinkedHashMap<>();
        boolean explicit = false;
        if (m.get("scaling") instanceof Map<?, ?> raw) {
            for (Map.Entry<?, ?> e : raw.entrySet()) {
                Stat st = Stat.from(String.valueOf(e.getKey()));
                Grade g = Grade.from(String.valueOf(e.getValue()));
                if (st == null || g == null) {
                    warn.accept("Technique " + id + " : scaling « " + e.getKey() + ": "
                            + e.getValue() + " » illisible — ignoré.");
                    continue;
                }
                scaling.put(st, g);
            }
            explicit = !scaling.isEmpty();
        }
        if (!explicit) scaling = defaultScaling(cat);

        ChakraAffinity nature = m.get("nature") != null
                ? ChakraAffinity.from(String.valueOf(m.get("nature")))
                : natureOf(cat);

        CostKind kind = defaultCostKind(cat);
        if (m.get("cost-kind") != null) {
            String k = String.valueOf(m.get("cost-kind")).trim().toUpperCase(Locale.ROOT);
            if (k.startsWith("STAM") || k.startsWith("END")) kind = CostKind.STAMINA;
            else if (k.startsWith("CHAK")) kind = CostKind.CHAKRA;
            else warn.accept("Technique " + id + " : cost-kind « " + k + " » inconnu — " + kind + ".");
        }

        double factor = 1.0;
        Object rawFactor = m.get("cost-factor");
        if (rawFactor != null) {
            try { factor = Math.max(0.0, Double.parseDouble(String.valueOf(rawFactor))); }
            catch (NumberFormatException ex) {
                warn.accept("Technique " + id + " : cost-factor illisible — 1.0.");
            }
        }

        boolean auto = !"MANUAL".equalsIgnoreCase(String.valueOf(m.get("scaling-mode")).trim());

        long window = 0L;
        Object rawWindow = m.get("attribution-millis");
        if (rawWindow != null) {
            try { window = Math.max(0L, Long.parseLong(String.valueOf(rawWindow).trim())); }
            catch (NumberFormatException ex) {
                warn.accept("Technique " + id + " : attribution-millis illisible — défaut.");
            }
        }

        return new TechniqueProfile(scaling, explicit, nature, kind, factor, auto, tierOf(rank), window);
    }

    /**
     * Category defaults — the four branches of the tree map onto the four
     * combat stats; Contrôle rides along as the precision letter.
     */
    public static Map<Stat, Grade> defaultScaling(String cat) {
        Map<Stat, Grade> s = new LinkedHashMap<>();
        if (cat.startsWith("taijutsu")) {
            s.put(Stat.TAIJUTSU, Grade.S); s.put(Stat.VIGUEUR, Grade.B);
        } else if (cat.startsWith("bukijutsu/kenjutsu")) {
            s.put(Stat.KENJUTSU, Grade.S); s.put(Stat.TAIJUTSU, Grade.C);
        } else if (cat.startsWith("bukijutsu")) {
            s.put(Stat.KENJUTSU, Grade.A); s.put(Stat.CONTROLE, Grade.C);
        } else if (cat.startsWith("ninjutsu/iryo")) {
            s.put(Stat.CONTROLE, Grade.S); s.put(Stat.NINJUTSU, Grade.C);
        } else if (cat.startsWith("autres/genjutsu") || cat.startsWith("kekkei/dojutsu")) {
            s.put(Stat.CONTROLE, Grade.S); s.put(Stat.NINJUTSU, Grade.C);
        } else if (cat.startsWith("ninjutsu/fuinjutsu") || cat.startsWith("autres/juinjutsu")
                || cat.startsWith("autres/kugutsu")) {
            s.put(Stat.CONTROLE, Grade.A); s.put(Stat.NINJUTSU, Grade.B);
        } else if (cat.startsWith("ninjutsu/kuchiyose") || cat.startsWith("senjutsu")) {
            s.put(Stat.NINJUTSU, Grade.A); s.put(Stat.CHAKRA, Grade.B);
        } else if (cat.startsWith("autres/kinjutsu")) {
            s.put(Stat.NINJUTSU, Grade.A); s.put(Stat.CHAKRA, Grade.B); s.put(Stat.VIGUEUR, Grade.D);
        } else if (cat.startsWith("ninjutsu") || cat.startsWith("kekkei")) {
            s.put(Stat.NINJUTSU, Grade.S); s.put(Stat.CONTROLE, Grade.C);
        } else {
            s.put(Stat.NINJUTSU, Grade.B);
        }
        return s;
    }

    /** Body arts are paid in endurance, chakra arts in chakra. */
    public static CostKind defaultCostKind(String cat) {
        return cat.startsWith("taijutsu") || cat.startsWith("bukijutsu")
                ? CostKind.STAMINA : CostKind.CHAKRA;
    }

    /** Wheel nature from the five basic releases; kekkei-genkai combos have none. */
    public static ChakraAffinity natureOf(String cat) {
        if (!cat.startsWith("ninjutsu/")) return ChakraAffinity.NONE;
        String leaf = cat.substring(cat.lastIndexOf('/') + 1);
        return switch (leaf) {
            case "katon" -> ChakraAffinity.KATON;
            case "suiton" -> ChakraAffinity.SUITON;
            case "futon", "fuuton" -> ChakraAffinity.FUTON;
            case "doton" -> ChakraAffinity.DOTON;
            case "raiton" -> ChakraAffinity.RAITON;
            default -> ChakraAffinity.NONE;
        };
    }

    /** Compact label for chat / tooltips: {@code "Nin S · Ctr C"}. */
    public String scalingLabel() {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<Stat, Grade> e : scaling.entrySet()) {
            if (sb.length() > 0) sb.append(" · ");
            String n = e.getKey().displayName();
            sb.append(n, 0, Math.min(3, n.length())).append(' ').append(e.getValue().name());
        }
        return sb.toString();
    }
}
