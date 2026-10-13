package com.reborn.shinobiabilities.parchemin;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.reborn.shinobicore.technique.Ability;
import com.reborn.shinobicore.technique.AbilityRegistry;
import com.reborn.shinobicore.technique.JutsuRank;
import com.reborn.shinobicore.technique.Requirement;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Les techniques qu'un parchemin peut porter (docs/gameplay/APPRENTISSAGE.md) : Taïjutsu, Kenjutsu et Ninjutsu,
 * d'après la catégorie de chaque technique. Les techniques de clan (kekkei, jūken…), ANBU, senjutsu et « autres »
 * restent hors des bibliothèques. Réglable dans {@code parchemins.categories} / {@code parchemins.exclues}.
 *
 * <p><b>Secrets</b> ({@code parchemins.secrets}, docs/gameplay/APPRENTISSAGE.md §3.8) : le rang S (Hiden), les
 * catégories secrètes (kekkei, jūken, kinjutsu, juinjutsu…), les techniques de clan et toute technique marquée
 * {@code secret: true} dans {@code abilities.yml} ne sortent <b>jamais</b> du tirage commun ; elles ne sortent que
 * des bibliothèques dont la <b>réserve</b> les contient. Clés facultatives d'une entrée d'{@code abilities.yml} :
 * {@code secret: true|false} (force), {@code clan: "Uchiha"}, {@code anbu: true}.
 */
public final class ScrollCatalog {

    private final AbilityRegistry registry;
    private List<String> include = List.of("taijutsu/goken", "bukijutsu/kenjutsu", "ninjutsu/");
    private List<String> exclude = List.of();

    // ─── secrets ───
    private boolean secretRankS = true;
    private List<String> secretCategories = List.of("kekkei/", "taijutsu/juken", "autres/kinjutsu", "autres/juinjutsu");
    private List<String> anbuCategories = List.of("autres/kinjutsu", "autres/juinjutsu");
    private List<String> secretIds = List.of();
    /** Clan (normalisé) → débuts de catégorie de ses techniques. */
    private final Map<String, List<String>> clanCategories = new LinkedHashMap<>();
    private final double[] secretPct = Library.SECRETS.clone();
    /** Marques lues dans les fichiers de techniques : {@code secret}, {@code clan}, {@code anbu}. */
    private final Map<String, Boolean> tagSecret = new HashMap<>();
    private final Map<String, String> tagClan = new HashMap<>();
    private final Map<String, Boolean> tagAnbu = new HashMap<>();

    public ScrollCatalog(AbilityRegistry registry) {
        this.registry = registry;
        defaultClans();
    }

    private void defaultClans() {
        clanCategories.clear();
        clanCategories.put("uchiha", List.of("kekkei/dojutsu/sharingan"));
        clanCategories.put("hyuga", List.of("kekkei/dojutsu/byakugan", "taijutsu/juken"));
        clanCategories.put("senju", List.of("kekkei/mokuton"));
    }

    public void load(ConfigurationSection sec) {
        if (sec == null) return;
        if (sec.isList("categories")) include = lower(sec.getStringList("categories"));
        if (sec.isList("exclues")) exclude = lower(sec.getStringList("exclues"));
        ConfigurationSection s = sec.getConfigurationSection("secrets");
        if (s == null) return;
        secretRankS = s.getBoolean("rang-s", true);
        if (s.isList("categories")) secretCategories = lower(s.getStringList("categories"));
        if (s.isList("anbu")) anbuCategories = lower(s.getStringList("anbu"));
        if (s.isList("techniques")) secretIds = lower(s.getStringList("techniques"));
        ConfigurationSection c = s.getConfigurationSection("clans");
        if (c != null) {
            clanCategories.clear();
            for (String k : c.getKeys(false)) clanCategories.put(LibraryService.norm(k), lower(c.getStringList(k)));
        }
        List<Double> p = s.getDoubleList("pourcentages");
        if (p.size() == 5) for (int i = 0; i < 5; i++) secretPct[i] = Math.max(0, Math.min(100, p.get(i)));
    }

    /**
     * Relit les marques {@code secret}, {@code clan}, {@code anbu} des fichiers de techniques (le registre de
     * ShinobiCore ne les garde pas) : {@code abilities.yml}, {@code abilities.generated.yml}, et
     * {@code abilities-debug.yml} si {@code debug}.
     */
    public void loadTags(File dir, boolean debug) {
        tagSecret.clear();
        tagClan.clear();
        tagAnbu.clear();
        readTags(new File(dir, "abilities.yml"));
        readTags(new File(dir, "abilities.generated.yml"));
        if (debug) readTags(new File(dir, "abilities-debug.yml"));
    }

    private void readTags(File f) {
        if (f == null || !f.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        List<Map<?, ?>> raw = y.getMapList("abilities");
        if (raw.isEmpty()) {
            ConfigurationSection sec = y.getConfigurationSection("abilities");
            if (sec != null) for (String k : sec.getKeys(false)) {
                ConfigurationSection a = sec.getConfigurationSection(k);
                if (a != null) tag(k, a.get("secret"), a.get("clan"), a.get("anbu"));
            }
            return;
        }
        for (Map<?, ?> m : raw) {
            Object id = m.get("id");
            if (id != null) tag(id.toString(), m.get("secret"), m.get("clan"), m.get("anbu"));
        }
    }

    private void tag(String id, Object secret, Object clan, Object anbu) {
        if (secret != null) tagSecret.put(id, Boolean.parseBoolean(secret.toString()));
        if (clan != null && !clan.toString().isBlank()) tagClan.put(id, clan.toString().trim());
        if (anbu != null) tagAnbu.put(id, Boolean.parseBoolean(anbu.toString()));
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

    // ─────────────────────────────────────────────── secrets

    private static boolean starts(String cat, List<String> prefixes) {
        for (String p : prefixes) if (cat.startsWith(p)) return true;
        return false;
    }

    /** Clan d'une technique (marque {@code clan:}, prérequis de clan, sinon catégorie du clan), ou null. */
    public String clanOf(Ability a) {
        String t = tagClan.get(a.id());
        if (t != null) return t;
        for (Requirement r : a.requires()) if (r.type() == Requirement.Type.CLAN && r.id() != null) return r.id();
        String cat = a.category().toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> e : clanCategories.entrySet()) {
            if (!e.getKey().isEmpty() && starts(cat, e.getValue())) {
                return e.getKey().substring(0, 1).toUpperCase(Locale.ROOT) + e.getKey().substring(1);
            }
        }
        return null;
    }

    /** Technique ANBU (marque {@code anbu: true} ou catégorie ANBU, kinjutsu par défaut). */
    public boolean isAnbu(Ability a) {
        Boolean t = tagAnbu.get(a.id());
        if (t != null) return t;
        return starts(a.category().toLowerCase(Locale.ROOT), anbuCategories);
    }

    /**
     * Technique secrète : jamais dans le tirage commun, seulement dans les réserves. Marque {@code secret:} d'abord,
     * puis la liste {@code secrets.techniques}, le rang S (Hiden), les catégories secrètes, les techniques de clan.
     */
    public boolean isSecret(Ability a) {
        Boolean t = tagSecret.get(a.id());
        if (t != null) return t;
        if (secretIds.contains(a.id().toLowerCase(Locale.ROOT))) return true;
        if (secretRankS && rank(a) == 'S') return true;
        if (starts(a.category().toLowerCase(Locale.ROOT), secretCategories)) return true;
        return clanOf(a) != null || isAnbu(a);
    }

    /** Peut entrer dans une réserve : éligible au parchemin, ou secrète (catégorie secrète, clan, ANBU, marque). */
    public boolean reservable(Ability a) {
        if (a == null) return false;
        if (eligible(a)) return true;
        if (Boolean.TRUE.equals(tagSecret.get(a.id())) || secretIds.contains(a.id().toLowerCase(Locale.ROOT))) return true;
        return starts(a.category().toLowerCase(Locale.ROOT), secretCategories) || clanOf(a) != null || isAnbu(a);
    }

    /** Tirage commun : les techniques éligibles qui ne sont pas secrètes. */
    public List<Ability> commonPool() {
        List<Ability> out = new ArrayList<>();
        for (Ability a : pool()) if (!isSecret(a)) out.add(a);
        return out;
    }

    /** Toutes les techniques qu'une réserve peut contenir, par rang puis par nom. */
    public List<Ability> reservablePool() {
        List<Ability> out = new ArrayList<>();
        for (Ability a : registry.all().values()) if (reservable(a)) out.add(a);
        out.sort(Comparator.comparingInt((Ability a) -> bucket(rank(a))).thenComparing(Ability::name));
        return out;
    }

    /** Pourcentage par défaut d'une technique de réserve, selon son rang. */
    public double secretPct(char r) { return secretPct[bucket(r)]; }

    public double[] secretPcts() { return secretPct.clone(); }

    /**
     * Réserve par défaut d'un type de bibliothèque : Clan → techniques du clan propriétaire ; ANBU → techniques ANBU
     * (kinjutsu…) ; Palais → rangs A et S du catalogue (Hiden compris) hors techniques de clan et ANBU ; autres
     * types → aucune. Pourcentage = {@link #secretPct(char)}.
     */
    public Map<String, Double> defaultReserve(Library.Type type, String owner) {
        Map<String, Double> out = new LinkedHashMap<>();
        String clan = LibraryService.norm(owner);
        for (Ability a : reservablePool()) {
            char r = rank(a);
            boolean in = switch (type) {
                case CLAN -> !clan.isEmpty() && clanOf(a) != null && LibraryService.norm(clanOf(a)).equals(clan);
                case ANBU -> isAnbu(a);
                case PALAIS -> (r == 'A' || r == 'S') && eligible(a) && clanOf(a) == null && !isAnbu(a);
                default -> false;
            };
            if (in) out.put(a.id(), secretPct(r));
        }
        return out;
    }

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

    /** Entrée légère d'une liste de réglages (sans description ni signes) : secret, clan, ANBU. */
    public JsonObject lightJson(Ability a) {
        JsonObject o = new JsonObject();
        char r = rank(a);
        o.addProperty("id", a.id());
        o.addProperty("name", a.name());
        o.addProperty("branch", branchLabel(a));
        o.addProperty("nature", nature(a));
        o.addProperty("rank", String.valueOf(r));
        o.addProperty("secret", isSecret(a));
        String clan = clanOf(a);
        if (clan != null) o.addProperty("clan", clan);
        if (isAnbu(a)) o.addProperty("anbu", true);
        return o;
    }

    /** Branche lisible, y compris hors des trois branches du parchemin (Kekkei genkai, Kinjutsu…). */
    public static String branchLabel(Ability a) {
        String cat = a.category().toLowerCase(Locale.ROOT);
        if (cat.startsWith("kekkei/dojutsu")) return "Dōjutsu";
        if (cat.startsWith("kekkei")) return "Kekkei genkai";
        if (cat.startsWith("taijutsu/juken")) return "Jūken";
        if (cat.startsWith("autres/kinjutsu")) return "Kinjutsu";
        if (cat.startsWith("autres/juinjutsu")) return "Juinjutsu";
        if (cat.startsWith("autres/genjutsu")) return "Genjutsu";
        if (cat.startsWith("bukijutsu") && !cat.contains("kenjutsu")) return "Bukijutsu";
        return branch(a);
    }
}
