package com.reborn.shinobiabilities.parchemin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.reborn.shinobicore.technique.Ability;
import com.reborn.shinobicore.technique.AbilityRegistry;
import com.reborn.shinobicore.technique.JutsuRank;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Les techniques qu'un parchemin peut porter (docs/PROPOSITION_PARCHEMINS.md) : Taïjutsu, Kenjutsu et Ninjutsu,
 * d'après la catégorie de chaque technique. Les techniques de clan (kekkei, jūken…), ANBU, senjutsu et « autres »
 * restent hors des bibliothèques. Réglable dans {@code parchemins.categories} / {@code parchemins.exclues}.
 */
public final class ScrollCatalog {

    private final AbilityRegistry registry;
    private List<String> include = List.of("taijutsu/goken", "bukijutsu/kenjutsu", "ninjutsu/");
    private List<String> exclude = List.of();

    public ScrollCatalog(AbilityRegistry registry) {
        this.registry = registry;
    }

    public void load(ConfigurationSection sec) {
        if (sec == null) return;
        if (sec.isList("categories")) include = lower(sec.getStringList("categories"));
        if (sec.isList("exclues")) exclude = lower(sec.getStringList("exclues"));
    }

    private static List<String> lower(List<String> in) {
        List<String> out = new ArrayList<>();
        for (String s : in) out.add(s.toLowerCase(Locale.ROOT));
        return out;
    }

    public boolean eligible(Ability a) {
        if (a == null) return false;
        String cat = a.category().toLowerCase(Locale.ROOT);
        for (String ex : exclude) if (cat.startsWith(ex)) return false;
        for (String in : include) if (cat.startsWith(in)) return true;
        return false;
    }

    /** Toutes les techniques éligibles, par rang puis par nom. */
    public List<Ability> pool() {
        List<Ability> out = new ArrayList<>();
        for (Ability a : registry.all().values()) if (eligible(a)) out.add(a);
        out.sort(Comparator.comparingInt((Ability a) -> bucket(rank(a))).thenComparing(Ability::name));
        return out;
    }

    public Ability byId(String id) { return registry.byId(id); }

    // ─────────────────────────────────────────────── lecture d'une technique

    public static String branch(Ability a) {
        String cat = a.category().toLowerCase(Locale.ROOT);
        if (cat.startsWith("taijutsu")) return "Taïjutsu";
        if (cat.contains("kenjutsu")) return "Kenjutsu";
        return "Ninjutsu";
    }

    public static String nature(Ability a) {
        return switch (a.categoryLeaf().toLowerCase(Locale.ROOT)) {
            case "katon" -> "Katon";
            case "suiton" -> "Suiton";
            case "doton" -> "Doton";
            case "futon" -> "Fūton";
            case "raiton" -> "Raiton";
            case "iryo" -> "Iryō";
            case "fuinjutsu" -> "Fūinjutsu";
            case "kuchiyose" -> "Kuchiyose";
            default -> "";
        };
    }

    /** Lettre du rang : E, D, C, B, A, S (le rang HIDEN est le S). */
    public static char rank(Ability a) {
        return a.rank() == JutsuRank.HIDEN ? 'S' : a.rank().name().charAt(0);
    }

    /** Case de pourcentage : D (avec E), C, B, A, S. */
    public static int bucket(char r) {
        return switch (r) {
            case 'C' -> 1;
            case 'B' -> 2;
            case 'A' -> 3;
            case 'S' -> 4;
            default -> 0;
        };
    }

    public static int seances(char r) {
        return switch (r) {
            case 'C' -> 3;
            case 'B' -> 6;
            case 'A' -> 12;
            case 'S' -> 20;
            default -> 1;
        };
    }

    public static double weight(char r) {
        return switch (r) {
            case 'S' -> 0.8;
            case 'A' -> 0.5;
            default -> 0.2;
        };
    }

    public static JsonObject json(Ability a) {
        JsonObject o = new JsonObject();
        char r = rank(a);
        o.addProperty("id", a.id());
        o.addProperty("name", a.name());
        o.addProperty("branch", branch(a));
        o.addProperty("nature", nature(a));
        o.addProperty("rank", String.valueOf(r));
        o.addProperty("desc", a.description() == null ? "" : a.description());
        JsonArray signs = new JsonArray();
        List<String> labels = a.incantationLabels();
        if (labels != null) for (String s : labels) signs.add(s);
        o.add("signs", signs);
        o.addProperty("seances", seances(r));
        o.addProperty("weight", weight(r));
        return o;
    }
}
