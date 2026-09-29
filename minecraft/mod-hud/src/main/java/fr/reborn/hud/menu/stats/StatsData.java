package fr.reborn.hud.menu.stats;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Snapshot de la fiche shinobi du personnage <b>actif</b>, poussé par
 * ShinobiCore sur {@code reborn:stats}. Porte aussi les <b>leviers</b> des
 * formules : le client prévisualise une allocation avec exactement les mêmes
 * coefficients que le serveur ({@link StatsMath}). Le serveur reste seul juge.
 *
 * <p>Aucun serveur (test solo / runClient) → {@link #mock()}.
 */
public final class StatsData {

    private StatsData() {}

    /** Coefficients des formules (SPEC_STATS_SERVICE §1.3) — miroir de {@code StatFormulas.Levers}. */
    public record Levers(int pointsPerRank, double softCap, double overFactor,
                         double hpBase, double hpPerVig,
                         double chakraBase, double chakraPerPoint,
                         double stBase, double stPerVig, double stPerTai,
                         double regenBase, double regenPerCtrl,
                         double costRedPerCtrl, double meleePerPoint,
                         boolean critEnabled, double critBase, double critPerCtrl,
                         Map<String, Double> weights,
                         double[] chakraCost, double[] staminaCost) {}

    /** Une technique connue et son profil de scaling. {@code tier} : E0 … S5. */
    public record Tech(String id, String name, String rank, int tier, String category,
                       String nature, boolean stamina, double costFactor, boolean explicit,
                       boolean castable, int mastery, Map<StatDef, String> scaling) {}

    public record Snapshot(String name, String clan, String village, String rank, int rankTier,
                           String nextRank, List<String> natures, int min, int max,
                           int earned, int bonus, boolean canRespec,
                           boolean respecFree, int respecTokens,
                           int[] stats, Levers levers, List<Tech> techniques) {

        public int get(StatDef d) { return stats[d.ordinal()]; }

        public int spent() {
            int s = 0;
            for (int v : stats) s += v - min;
            return s;
        }

        public int unspent() { return earned - spent(); }
    }

    private static volatile Snapshot snapshot = null;
    /** Incrémenté à chaque push serveur — l'écran s'y resynchronise. */
    private static volatile int version = 0;

    public static Snapshot get() {
        Snapshot s = snapshot;
        return s != null ? s : mock();
    }

    public static boolean fromServer() { return snapshot != null; }

    public static int version() { return version; }

    public static void clear() { snapshot = null; }

    /** Parse le JSON serveur. Retourne {@code open} ; silencieux en cas d'erreur (garde l'ancien). */
    public static boolean update(String json) {
        try {
            JsonObject r = JsonParser.parseString(json).getAsJsonObject();
            int[] stats = new int[StatDef.values().length];
            JsonObject so = obj(r, "stats");
            for (StatDef d : StatDef.values()) stats[d.ordinal()] = so == null ? 1 : intv(so, d.key(), 1);

            JsonObject lo = obj(r, "levers");
            Levers lv = lo == null ? defaultLevers() : parseLevers(lo);

            List<String> natures = new ArrayList<>();
            if (r.has("natures") && r.get("natures").isJsonArray()) {
                for (JsonElement e : r.getAsJsonArray("natures")) natures.add(e.getAsString());
            }

            List<Tech> techs = new ArrayList<>();
            if (r.has("techniques") && r.get("techniques").isJsonArray()) {
                for (JsonElement e : r.getAsJsonArray("techniques")) {
                    if (!e.isJsonObject()) continue;
                    JsonObject t = e.getAsJsonObject();
                    Map<StatDef, String> sc = new EnumMap<>(StatDef.class);
                    JsonObject sco = obj(t, "scaling");
                    if (sco != null) {
                        for (String k : sco.keySet()) {
                            StatDef d = StatDef.fromKey(k);
                            if (d != null) sc.put(d, sco.get(k).getAsString());
                        }
                    }
                    techs.add(new Tech(str(t, "id", "?"), str(t, "name", "?"), str(t, "rank", "E"),
                        intv(t, "tier", 0), str(t, "cat", ""), str(t, "nature", "NONE"),
                        "STAMINA".equals(str(t, "kind", "CHAKRA")), dbl(t, "factor", 1.0),
                        bool(t, "explicit", false), bool(t, "castable", true),
                        intv(t, "mastery", 0), sc));
                }
            }

            snapshot = new Snapshot(str(r, "name", "?"), str(r, "clan", ""), str(r, "village", ""),
                str(r, "rank", "?"), intv(r, "rankTier", 0), str(r, "nextRank", ""),
                Collections.unmodifiableList(natures), intv(r, "min", 1), intv(r, "max", 10),
                intv(r, "earned", 0), intv(r, "bonus", 0), bool(r, "canRespec", false),
                bool(r, "respecFree", false), intv(r, "respecTokens", 0),
                stats, lv, Collections.unmodifiableList(techs));
            version++;
            return bool(r, "open", false);
        } catch (Exception ignored) {
            return false;
        }
    }

    private static Levers parseLevers(JsonObject o) {
        Levers d = defaultLevers();
        Map<String, Double> w = new java.util.LinkedHashMap<>(d.weights());
        JsonObject wo = obj(o, "weights");
        if (wo != null) for (String k : wo.keySet()) w.put(k, wo.get(k).getAsDouble());
        return new Levers(intv(o, "pointsPerRank", d.pointsPerRank()),
            dbl(o, "softCap", d.softCap()), dbl(o, "overFactor", d.overFactor()),
            dbl(o, "hpBase", d.hpBase()), dbl(o, "hpPerVig", d.hpPerVig()),
            dbl(o, "chakraBase", d.chakraBase()), dbl(o, "chakraPerPoint", d.chakraPerPoint()),
            dbl(o, "stBase", d.stBase()), dbl(o, "stPerVig", d.stPerVig()), dbl(o, "stPerTai", d.stPerTai()),
            dbl(o, "regenBase", d.regenBase()), dbl(o, "regenPerCtrl", d.regenPerCtrl()),
            dbl(o, "costRedPerCtrl", d.costRedPerCtrl()), dbl(o, "meleePerPoint", d.meleePerPoint()),
            bool(o, "critEnabled", d.critEnabled()), dbl(o, "critBase", d.critBase()),
            dbl(o, "critPerCtrl", d.critPerCtrl()), w,
            arr(o, "chakraCost", d.chakraCost()), arr(o, "staminaCost", d.staminaCost()));
    }

    /** Valeurs de départ de la spec — mock solo et repli si le serveur omet un levier. */
    static Levers defaultLevers() {
        Map<String, Double> w = new java.util.LinkedHashMap<>();
        w.put("S", 0.12); w.put("A", 0.10); w.put("B", 0.07); w.put("C", 0.04); w.put("D", 0.02);
        return new Levers(3, 6, 0.5, 200, 100, 4000, 12000, 100, 8, 4, 0.01, 0.0015, 0.03, 0.08,
            true, 0.05, 0.005, w,
            new double[]{400, 800, 2000, 5000, 12000, 35000},
            new double[]{12, 18, 30, 45, 60, 75});
    }

    /** Fiche de démonstration (runClient sans serveur) — un Chunin Katon/Iryō. */
    static Snapshot mock() {
        int[] st = {2, 1, 4, 3, 2, 3};
        List<Tech> t = new ArrayList<>();
        t.add(tech("katon_gokakyu", "Katon — Boule de Feu Suprême", "C", 2, "ninjutsu/katon", "KATON", false,
            true, 42, Map.of(StatDef.NINJUTSU, "S", StatDef.CONTROLE, "C")));
        t.add(tech("iryo_shosen", "Shōsen — Paume Mystique", "C", 2, "ninjutsu/iryo", "NONE", false,
            true, 12, Map.of(StatDef.CONTROLE, "S", StatDef.NINJUTSU, "C")));
        t.add(tech("taijutsu_konoha_senpu", "Konoha Senpū", "D", 1, "taijutsu/goken", "NONE", true,
            true, 67, Map.of(StatDef.TAIJUTSU, "S", StatDef.VIGUEUR, "B")));
        t.add(tech("kenjutsu_iai", "Iai — Dégainage Éclair", "C", 2, "bukijutsu/kenjutsu", "NONE", true,
            true, 0, Map.of(StatDef.KENJUTSU, "S", StatDef.TAIJUTSU, "C")));
        t.add(tech("raiton_dummy_b", "Raiton — Essai B", "B", 3, "ninjutsu/raiton", "RAITON", false,
            false, 0, Map.of(StatDef.NINJUTSU, "S", StatDef.CONTROLE, "C")));
        t.add(tech("kinjutsu_dummy_hiden", "Kinjutsu — Essai Hiden", "Hiden", 5, "autres/kinjutsu", "NONE", false,
            false, 0, Map.of(StatDef.NINJUTSU, "A", StatDef.CHAKRA, "B", StatDef.VIGUEUR, "D")));
        return new Snapshot("Itachi", "Uchiha", "Konoha", "Chunin", 2, "Jonin",
            List.of("KATON", "RAITON"), 1, 10, 9, 0, true, false, 1, st, defaultLevers(), t);
    }

    private static Tech tech(String id, String name, String rank, int tier, String cat, String nature,
                             boolean stamina, boolean explicit, int mastery, Map<StatDef, String> sc) {
        Map<StatDef, String> m = new EnumMap<>(StatDef.class);
        m.putAll(sc);
        return new Tech(id, name, rank, tier, cat, nature, stamina, 1.0, explicit, true, mastery, m);
    }

    /* ------------------------------------------------------------- json */

    private static JsonObject obj(JsonObject o, String k) {
        return (o != null && o.has(k) && o.get(k).isJsonObject()) ? o.getAsJsonObject(k) : null;
    }

    private static String str(JsonObject o, String k, String def) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def;
    }

    private static int intv(JsonObject o, String k, int def) {
        try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : def; }
        catch (Exception e) { return def; }
    }

    private static double dbl(JsonObject o, String k, double def) {
        try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : def; }
        catch (Exception e) { return def; }
    }

    private static boolean bool(JsonObject o, String k, boolean def) {
        try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsBoolean() : def; }
        catch (Exception e) { return def; }
    }

    private static double[] arr(JsonObject o, String k, double[] def) {
        try {
            if (!o.has(k) || !o.get(k).isJsonArray()) return def;
            JsonArray a = o.getAsJsonArray(k);
            double[] out = def.clone();
            for (int i = 0; i < a.size() && i < out.length; i++) out[i] = a.get(i).getAsDouble();
            return out;
        } catch (Exception e) {
            return def;
        }
    }
}
