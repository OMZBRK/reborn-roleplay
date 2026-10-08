package com.reborn.shinobicore.stats;

import com.reborn.shinobicore.character.Rank;
import org.bukkit.configuration.ConfigurationSection;

import java.util.EnumMap;
import java.util.Map;

/**
 * Ce que donne chaque grade et qui peut le donner — bloc {@code grades:} de config.yml, relu au {@code /sc reload}.
 *
 * <ul>
 *   <li><b>Bonus passifs</b> : PV, chakra et endurance en plus, et un bonus ajouté aux six stats (il peut dépasser le
 *       plafond d'allocation ; il passe par la même courbe de rendement).</li>
 *   <li><b>Promotion</b> : pour chaque grade cible, le grade minimum de celui qui le donne, au mérite et sur examen
 *       réussi. Le staff ({@code shinobicore.grade.staff}) n'a pas de limite.</li>
 * </ul>
 */
public final class GradeRules {

    public record Bonus(double hp, double chakra, double stamina, int stats) {
        static final Bonus NONE = new Bonus(0, 0, 0, 0);
    }

    private record Rules(Map<Rank, Bonus> bonus, Map<Rank, Rank> merit, Map<Rank, Rank> exam, Rank demote) {}

    private static volatile Rules rules = defaults();

    private GradeRules() {}

    private static Rules defaults() {
        Map<Rank, Bonus> b = new EnumMap<>(Rank.class);
        b.put(Rank.ACADEMICIEN, Bonus.NONE);
        b.put(Rank.GENIN, new Bonus(40, 2000, 5, 0));
        b.put(Rank.GENIN_CONFIRME, new Bonus(80, 4000, 10, 0));
        b.put(Rank.CHUNIN, new Bonus(130, 7000, 15, 1));
        b.put(Rank.KONIN, new Bonus(180, 10000, 20, 1));
        b.put(Rank.TOKUBETSU_JONIN, new Bonus(240, 13000, 25, 1));
        b.put(Rank.JONIN, new Bonus(300, 17000, 30, 2));
        b.put(Rank.COMMANDANT_JONIN, new Bonus(350, 20000, 35, 2));
        b.put(Rank.KAGE, new Bonus(400, 25000, 45, 3));

        Map<Rank, Rank> merit = new EnumMap<>(Rank.class);
        merit.put(Rank.GENIN, Rank.TOKUBETSU_JONIN);
        merit.put(Rank.GENIN_CONFIRME, Rank.TOKUBETSU_JONIN);
        merit.put(Rank.CHUNIN, Rank.JONIN);
        merit.put(Rank.KONIN, Rank.JONIN);
        merit.put(Rank.TOKUBETSU_JONIN, Rank.COMMANDANT_JONIN);
        merit.put(Rank.JONIN, Rank.COMMANDANT_JONIN);
        merit.put(Rank.COMMANDANT_JONIN, Rank.KAGE);
        Map<Rank, Rank> exam = new EnumMap<>(Rank.class);
        exam.put(Rank.CHUNIN, Rank.TOKUBETSU_JONIN);
        return new Rules(b, merit, exam, Rank.KAGE);
    }

    /** Relit le bloc {@code grades:} ; les clés absentes gardent les valeurs par défaut. */
    public static void load(ConfigurationSection sec) {
        Rules d = defaults();
        if (sec == null) { rules = d; return; }
        Map<Rank, Bonus> b = new EnumMap<>(Rank.class);
        Map<Rank, Rank> merit = new EnumMap<>(Rank.class);
        Map<Rank, Rank> exam = new EnumMap<>(Rank.class);
        for (Rank r : Rank.values()) {
            Bonus def = d.bonus().getOrDefault(r, Bonus.NONE);
            String k = "bonus." + r.name();
            b.put(r, new Bonus(sec.getDouble(k + ".pv", def.hp()), sec.getDouble(k + ".chakra", def.chakra()),
                    sec.getDouble(k + ".endurance", def.stamina()), sec.getInt(k + ".stats", def.stats())));
            Rank m = rank(sec.getString("promotion." + r.name() + ".merite"), d.merit().get(r));
            if (m != null) merit.put(r, m);
            Rank e = rank(sec.getString("promotion." + r.name() + ".examen"), d.exam().get(r));
            if (e != null) exam.put(r, e);
        }
        Rank demote = rank(sec.getString("retrogradation"), d.demote());
        rules = new Rules(b, merit, exam, demote == null ? Rank.KAGE : demote);
    }

    private static Rank rank(String s, Rank def) {
        if (s == null) return def;
        if (s.isBlank() || s.equalsIgnoreCase("staff")) return null;
        return Rank.from(s);
    }

    public static Bonus bonus(Rank r) {
        return r == null ? Bonus.NONE : rules.bonus().getOrDefault(r, Bonus.NONE);
    }

    /** Grade minimum pour donner {@code target} au mérite ; {@code null} = staff uniquement. */
    public static Rank meritMin(Rank target) { return rules.merit().get(target); }

    /** Grade minimum pour valider l'examen menant à {@code target} ; {@code null} = pas d'examen. */
    public static Rank examMin(Rank target) { return rules.exam().get(target); }

    /** Grade minimum pour rétrograder quelqu'un. */
    public static Rank demoteMin() { return rules.demote(); }
}
