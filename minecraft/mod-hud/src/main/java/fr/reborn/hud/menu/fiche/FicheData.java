package fr.reborn.hud.menu.fiche;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.reborn.hud.menu.stats.StatDef;
import fr.reborn.hud.menu.stats.StatsData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Blocs « v2 » de la fiche, lus dans le MÊME JSON {@code reborn:stats} que {@link StatsData} (PROTOCOLE §1-2) :
 * identité, lignée, apprentissage, compléments des techniques, état du corps. Toutes les clés sont optionnelles ; un
 * bloc illisible est ignoré seul (un try par bloc). Le snapshot de stats reste celui de {@link StatsData}.
 *
 * <p>En solo (aucun serveur), {@link #snapshot()} et les blocs viennent de la fiche de démonstration de Ren Hyūga
 * (celle des maquettes), pour que l'écran soit cohérent.
 */
public final class FicheData {

    private FicheData() {}

    /* ================================================================ modèle */

    record Identite(Integer age, String taille, String sexe, String naturesNote, List<String> traits, String citation) {}

    record Dojutsu(String nom, int stade, String txt) {}

    /** {@code rang} : lettre D…S d'une mission (clé {@code rang} du serveur) ; {@code kj} : ancien kanji imposé, lu mais plus affiché
     *  (18/10, aucun kanji décoratif). */
    record Service(String type, String kj, String rang, String txt, String date) {}

    record Lignee(Dojutsu dojutsu, Integer meritePalier, String meriteTxt, List<Service> service, int serviceTotal) {}

    record Apprentissage(String id, String name, String letter, String nature, String cat, int done, int total,
                         String etat, String txt) {}

    record TechPlus(String letter, String groupe, Integer palier, String palierTxt, String origin, boolean teach,
                    String desc, List<String> mudras) {}

    record Jauge(int lvl, String txt) {}

    record Blessure(String zone, String label, String type, String txt, boolean forte) {}

    record Etat(String titre, String titreTxt, String sousTxt, Jauge vie, Jauge chakra, Jauge endurance,
                Jauge volonte, Jauge soins, String ataNiveau, String ataTxt, String koPhase,
                List<Blessure> blessures, Map<String, List<FicheTextes.Cran>> echelles) {}

    /** {@code pid} : Player ID du personnage (« 00042 »), envoyé au seul joueur concerné ; null si absent. */
    record Donnees(int v, String rankId, Integer merit, Identite identite, Lignee lignee,
                   List<Apprentissage> apprentissage, Map<String, TechPlus> techs, Etat etat, String pid) {

        static final Donnees VIDE = new Donnees(0, null, null, null, null, List.of(), Map.of(), null, null);
    }

    private static volatile Donnees serveur = null;
    private static volatile Donnees demo = null;
    private static volatile StatsData.Snapshot demoSnap = null;
    private static volatile int version = 0;

    /** Les blocs à afficher : ceux du serveur, sinon la démonstration (solo). */
    public static Donnees get() {
        Donnees d = serveur;
        if (d != null) return d;
        if (StatsData.fromServer()) return Donnees.VIDE;            // serveur sans blocs v2 : replis
        return demoDonnees();
    }

    /** Snapshot de stats : celui du serveur, sinon Ren Hyūga (cohérent avec les blocs de démonstration). */
    public static StatsData.Snapshot snapshot() {
        if (StatsData.fromServer()) return StatsData.get();
        StatsData.Snapshot s = demoSnap;
        if (s == null) {
            demoDonnees();
            s = demoSnap;
        }
        return s != null ? s : StatsData.get();
    }

    public static int version() { return version; }

    public static void clear() {
        serveur = null;
        version++;
        Repartition.clearSession();
    }

    /** Lit les blocs v2 du JSON reborn:stats. Silencieux : un bloc cassé est ignoré seul. */
    public static void update(String json) {
        try {
            serveur = parse(JsonParser.parseString(json).getAsJsonObject());
            version++;
        } catch (Exception ignored) {
            // JSON illisible : on garde l'état précédent (StatsData fait de même).
        }
    }

    private static Donnees demoDonnees() {
        Donnees d = demo;
        if (d == null) {
            try {
                JsonObject r = JsonParser.parseString(DEMO_JSON).getAsJsonObject();
                d = parse(r);
                demoSnap = snapshotDe(r);
            } catch (Exception e) {
                d = Donnees.VIDE;
            }
            demo = d;
        }
        return d;
    }

    /* ================================================================ lecture */

    static Donnees parse(JsonObject r) {
        int v = intv(r, "v", 0);
        String rankId = str(r, "rankId", null);
        Integer merit = r.has("merit") && !r.get("merit").isJsonNull() ? intv(r, "merit", 0) : null;
        Identite id = null;
        try { id = identite(obj(r, "identite")); } catch (Exception ignored) { }
        Lignee li = null;
        try { li = lignee(obj(r, "lignee")); } catch (Exception ignored) { }
        List<Apprentissage> ap = List.of();
        try { ap = apprentissage(r); } catch (Exception ignored) { }
        Map<String, TechPlus> tp = Map.of();
        try { tp = techs(r); } catch (Exception ignored) { }
        Etat et = null;
        try { et = etat(obj(r, "etat")); } catch (Exception ignored) { }
        String pid = str(r, "pid", null);
        if (pid != null && (pid.isBlank() || !pid.chars().allMatch(Character::isDigit))) pid = null;
        return new Donnees(v, rankId, merit, id, li, ap, tp, et, pid);
    }

    private static Identite identite(JsonObject o) {
        if (o == null) return null;
        List<String> traits = List.of();
        String citation = null;
        JsonObject c = obj(o, "caractere");
        if (c != null) {
            List<String> t = new ArrayList<>();
            if (c.has("traits") && c.get("traits").isJsonArray()) for (JsonElement e : c.getAsJsonArray("traits")) {
                if (e.isJsonPrimitive() && !e.getAsString().isBlank()) t.add(e.getAsString().trim());
            }
            traits = Collections.unmodifiableList(t);
            citation = str(c, "citation", null);
        }
        Integer age = o.has("age") && !o.get("age").isJsonNull() ? intv(o, "age", 0) : null;
        return new Identite(age, str(o, "taille", null), str(o, "sexe", null), str(o, "naturesNote", null), traits, citation);
    }

    private static Lignee lignee(JsonObject o) {
        if (o == null) return null;
        Dojutsu dj = null;
        JsonObject d = obj(o, "dojutsu");
        if (d != null) dj = new Dojutsu(str(d, "nom", "?"), intv(d, "stade", 0), str(d, "txt", ""));
        Integer mp = null;
        String mt = null;
        JsonObject m = obj(o, "merite");
        if (m != null) {
            mp = intv(m, "palier", 0);
            mt = str(m, "txt", null);
        }
        List<Service> sv = new ArrayList<>();
        if (o.has("service") && o.get("service").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("service")) {
                if (!e.isJsonObject()) continue;
                JsonObject s = e.getAsJsonObject();
                sv.add(new Service(str(s, "type", "AUTRE"), str(s, "kj", null), str(s, "rang", null), str(s, "txt", ""),
                    str(s, "date", "")));
            }
        }
        return new Lignee(dj, mp, mt, Collections.unmodifiableList(sv), intv(o, "serviceTotal", sv.size()));
    }

    private static List<Apprentissage> apprentissage(JsonObject r) {
        if (!r.has("apprentissage") || !r.get("apprentissage").isJsonArray()) return List.of();
        List<Apprentissage> out = new ArrayList<>();
        for (JsonElement e : r.getAsJsonArray("apprentissage")) {
            if (!e.isJsonObject()) continue;
            JsonObject a = e.getAsJsonObject();
            out.add(new Apprentissage(str(a, "id", "?"), str(a, "name", "?"), str(a, "letter", ""),
                str(a, "nature", "NONE"), str(a, "cat", ""), intv(a, "done", 0), intv(a, "total", 1),
                str(a, "etat", "REPOS"), str(a, "txt", "")));
        }
        return Collections.unmodifiableList(out);
    }

    private static Map<String, TechPlus> techs(JsonObject r) {
        if (!r.has("techniques") || !r.get("techniques").isJsonArray()) return Map.of();
        Map<String, TechPlus> out = new HashMap<>();
        for (JsonElement e : r.getAsJsonArray("techniques")) {
            if (!e.isJsonObject()) continue;
            JsonObject t = e.getAsJsonObject();
            if (!t.has("letter") && !t.has("groupe") && !t.has("desc") && !t.has("origin") && !t.has("mudras")
                && !t.has("palier")) continue;
            List<String> mu = null;
            if (t.has("mudras") && t.get("mudras").isJsonArray()) {
                mu = new ArrayList<>();
                for (JsonElement m : t.getAsJsonArray("mudras")) mu.add(m.getAsString());
            }
            Integer pal = t.has("palier") && !t.get("palier").isJsonNull() ? intv(t, "palier", 0) : null;
            out.put(str(t, "id", "?"), new TechPlus(str(t, "letter", null), str(t, "groupe", null), pal,
                str(t, "palierTxt", null), str(t, "origin", null), bool(t, "teach", false), str(t, "desc", null),
                mu == null ? null : Collections.unmodifiableList(mu)));
        }
        return out;
    }

    private static Jauge jauge(JsonObject o, String k) {
        JsonObject j = obj(o, k);
        return j == null ? null : new Jauge(intv(j, "lvl", 0), str(j, "txt", ""));
    }

    private static Etat etat(JsonObject o) {
        if (o == null) return null;
        String ataN = null, ataT = null, ko = null;
        JsonObject a = obj(o, "ata");
        if (a != null) {
            ataN = str(a, "niveau", null);
            ataT = str(a, "txt", null);
        }
        JsonObject k = obj(o, "ko");
        if (k != null) ko = str(k, "phase", null);
        List<Blessure> bl = new ArrayList<>();
        if (o.has("blessures") && o.get("blessures").isJsonArray()) {
            for (JsonElement e : o.getAsJsonArray("blessures")) {
                if (!e.isJsonObject()) continue;
                JsonObject b = e.getAsJsonObject();
                bl.add(new Blessure(str(b, "zone", "TORSE"), str(b, "label", ""), str(b, "type", "HEMATOME"),
                    str(b, "txt", ""), bool(b, "forte", false)));
                if (bl.size() >= 8) break;
            }
        }
        Map<String, List<FicheTextes.Cran>> ech = new LinkedHashMap<>();
        JsonObject eo = obj(o, "echelles");
        if (eo != null) {
            for (String key : eo.keySet()) {
                try {
                    List<FicheTextes.Cran> l = new ArrayList<>();
                    for (JsonElement e : eo.getAsJsonArray(key)) {
                        JsonArray c = e.getAsJsonArray();
                        l.add(new FicheTextes.Cran(c.get(0).getAsDouble(), c.get(1).getAsString(),
                            c.size() > 2 ? c.get(2).getAsInt() : 0));
                    }
                    if (!l.isEmpty()) ech.put(key, Collections.unmodifiableList(l));
                } catch (Exception ignored) { }
            }
        }
        return new Etat(str(o, "titre", "INDEMNE"), str(o, "titreTxt", null), str(o, "sousTxt", null),
            jauge(o, "vie"), jauge(o, "chakra"), jauge(o, "endurance"), jauge(o, "volonte"), jauge(o, "soins"),
            ataN, ataT, ko, Collections.unmodifiableList(bl), ech);
    }

    /** Snapshot de stats lu comme StatsData (uniquement pour la démonstration solo). */
    private static StatsData.Snapshot snapshotDe(JsonObject r) {
        int[] stats = new int[StatDef.values().length];
        JsonObject so = obj(r, "stats");
        for (StatDef d : StatDef.values()) stats[d.ordinal()] = so == null ? 1 : intv(so, d.key(), 1);
        Map<String, Double> w = new LinkedHashMap<>();
        w.put("S", 0.12); w.put("A", 0.10); w.put("B", 0.07); w.put("C", 0.04); w.put("D", 0.02);
        JsonObject lo = obj(r, "levers");
        double soft = lo == null ? 6 : dbl(lo, "softCap", 6);
        StatsData.Levers lv = new StatsData.Levers(3, soft, 0.5, 200, 100, 4000, 12000, 100, 8, 4, 0.01, 0.0015,
            0.03, 0.08, true, 0.05, 0.005, w, new double[]{400, 800, 2000, 5000, 12000, 35000},
            new double[]{12, 18, 30, 45, 60, 75});
        List<String> natures = new ArrayList<>();
        if (r.has("natures")) for (JsonElement e : r.getAsJsonArray("natures")) natures.add(e.getAsString());
        List<StatsData.Tech> techs = new ArrayList<>();
        if (r.has("techniques")) {
            for (JsonElement e : r.getAsJsonArray("techniques")) {
                JsonObject t = e.getAsJsonObject();
                Map<StatDef, String> sc = new EnumMap<>(StatDef.class);
                JsonObject sco = obj(t, "scaling");
                if (sco != null) for (String k : sco.keySet()) {
                    StatDef d = StatDef.fromKey(k);
                    if (d != null) sc.put(d, sco.get(k).getAsString());
                }
                techs.add(new StatsData.Tech(str(t, "id", "?"), str(t, "name", "?"), str(t, "rank", "E"),
                    intv(t, "tier", 0), str(t, "cat", ""), str(t, "nature", "NONE"),
                    "STAMINA".equals(str(t, "kind", "CHAKRA")), dbl(t, "factor", 1.0), bool(t, "explicit", false),
                    bool(t, "castable", true), intv(t, "mastery", 0), sc));
            }
        }
        return new StatsData.Snapshot(str(r, "name", "?"), str(r, "clan", ""), str(r, "village", ""),
            str(r, "rank", "?"), intv(r, "rankTier", 0), str(r, "nextRank", ""), Collections.unmodifiableList(natures),
            intv(r, "min", 1), intv(r, "max", 10), intv(r, "earned", 0), intv(r, "bonus", 0),
            bool(r, "canRespec", false), bool(r, "respecFree", false), intv(r, "respecTokens", 0), stats, lv,
            Collections.unmodifiableList(techs));
    }

    /* ================================================================ json */

    static JsonObject obj(JsonObject o, String k) {
        return (o != null && o.has(k) && o.get(k).isJsonObject()) ? o.getAsJsonObject(k) : null;
    }

    static String str(JsonObject o, String k, String def) {
        try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : def; }
        catch (Exception e) { return def; }
    }

    static int intv(JsonObject o, String k, int def) {
        try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsInt() : def; }
        catch (Exception e) { return def; }
    }

    static double dbl(JsonObject o, String k, double def) {
        try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsDouble() : def; }
        catch (Exception e) { return def; }
    }

    static boolean bool(JsonObject o, String k, boolean def) {
        try { return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsBoolean() : def; }
        catch (Exception e) { return def; }
    }

    /* ================================================================ démonstration */

    /** Ren Hyūga des maquettes, au format v2 complet du PROTOCOLE (aussi le snapshot du banc). */
    static final String DEMO_JSON = """
        {"open":true,"v":2,"pid":"00042","name":"Ren Hyūga","clan":"Hyūga","village":"Konohagakure","rank":"Genin","rankId":"GENIN",
         "rankTier":1,"merit":3,"nextRank":"Genin confirmé","natures":["SUITON","FUTON"],"min":1,"max":10,"earned":19,
         "bonus":0,"canRespec":true,"respecFree":false,"respecTokens":1,
         "stats":{"taijutsu":4,"kenjutsu":2,"ninjutsu":4,"controle":5,"vigueur":3,"chakra":5},
         "levers":{"softCap":5},
         "identite":{"age":16,"taille":"1,68 m","sexe":"Homme","naturesNote":"Une autre nature sommeille encore.",
           "caractere":{"traits":["Calme","Obstiné","Loyal"],"citation":"Je ne recule pas, même quand le clan me le demande."}},
         "lignee":{"dojutsu":{"nom":"Byakugan","stade":0,"txt":"Les yeux de ton clan. Ils ne sont pas encore éveillés."},
           "merite":{"palier":1,"txt":"Reconnu par ton village"},
           "service":[{"type":"ENREGISTREMENT","txt":"Enregistré à Konohagakure","date":"4 septembre"},
                      {"type":"PROMOTION","txt":"Promu Genin, sur examen","date":"28 septembre"},
                      {"type":"AUTRE","txt":"Jūken transmis par le clan","date":"9 octobre"}],
           "serviceTotal":3},
         "apprentissage":[
           {"id":"hakkesho_kaiten","name":"Hakkeshō Kaiten","letter":"B","nature":"NONE","cat":"taijutsu/juken","done":2,"total":6,"etat":"REPOS","txt":"séance demain"},
           {"id":"futon_lame","name":"Fūton — Lame de Vent","letter":"C","nature":"FUTON","cat":"ninjutsu/futon","done":2,"total":3,"etat":"MAITRE","txt":"maître à portée"}],
         "techniques":[
           {"id":"suiton_teppodama","name":"Suiton — Balle Aqueuse","rank":"C","tier":2,"cat":"ninjutsu/suiton","nature":"SUITON","kind":"CHAKRA","factor":1,"explicit":true,"castable":true,"mastery":15,"scaling":{"ninjutsu":"S","controle":"C"},
            "letter":"C","groupe":"NINJUTSU","palier":0,"palierTxt":"Novice","origin":"PARCHEMIN","teach":false,
            "desc":"Une sphère d'eau dense crachée d'un seul souffle. Elle frappe comme un poing et trempe tout autour.","mudras":["Tigre","Serpent","Rat"]},
           {"id":"suiton_mizurappa","name":"Suiton — Jet Tranchant","rank":"D","tier":1,"cat":"ninjutsu/suiton","nature":"SUITON","kind":"CHAKRA","factor":1,"explicit":true,"castable":true,"mastery":55,"scaling":{"ninjutsu":"B","controle":"C"},
            "letter":"D","groupe":"NINJUTSU","palier":1,"palierTxt":"Avancé","origin":"PARCHEMIN","teach":false,
            "desc":"Un jet d'eau comprimé, assez fin pour entailler le bois. Il part des lèvres, d'un seul souffle.","mudras":["Tigre","Bélier","Rat"]},
           {"id":"futon_rafale","name":"Fūton — Rafale Vive","rank":"D","tier":1,"cat":"ninjutsu/futon","nature":"FUTON","kind":"CHAKRA","factor":1,"explicit":true,"castable":true,"mastery":10,"scaling":{"ninjutsu":"B","chakra":"C"},
            "letter":"D","groupe":"NINJUTSU","palier":0,"palierTxt":"Novice","origin":"PARCHEMIN","teach":false,
            "desc":"Une bourrasque soufflée entre les doigts, qui repousse et déséquilibre.","mudras":["Serpent","Bélier"]},
           {"id":"juken","name":"Jūken — Poing Souple","rank":"C","tier":2,"cat":"taijutsu/juken","nature":"NONE","kind":"STAMINA","factor":1,"explicit":true,"castable":true,"mastery":60,"scaling":{"taijutsu":"A","controle":"C"},
            "letter":"C","groupe":"TAIJUTSU","palier":1,"palierTxt":"Avancé","origin":"CLAN","teach":false,
            "desc":"Le poing souple de ton clan : chaque frappe vise les points de chakra plutôt que la chair."},
           {"id":"konoha_senpu","name":"Konoha Senpū","rank":"D","tier":1,"cat":"taijutsu/goken","nature":"NONE","kind":"STAMINA","factor":1,"explicit":true,"castable":true,"mastery":58,"scaling":{"taijutsu":"S","vigueur":"B"},
            "letter":"D","groupe":"TAIJUTSU","palier":1,"palierTxt":"Avancé","origin":"ACADEMIE","teach":false,
            "desc":"Un coup de pied tournoyant, bas puis haut, appris à l'Académie."},
           {"id":"shunshin","name":"Shunshin no Jutsu","rank":"E","tier":0,"cat":"fondamentaux","nature":"NONE","kind":"CHAKRA","factor":1,"explicit":false,"castable":true,"mastery":100,"scaling":{"controle":"B"},
            "groupe":"FONDAMENTAUX","palier":2,"palierTxt":"Maître","origin":"ACADEMIE","teach":true,
            "desc":"Un pas porté par le chakra, si vif qu'on croit te voir disparaître.","mudras":["Tigre"]},
           {"id":"kawarimi","name":"Kawarimi no Jutsu","rank":"E","tier":0,"cat":"fondamentaux","nature":"NONE","kind":"CHAKRA","factor":1,"explicit":false,"castable":true,"mastery":60,"scaling":{"controle":"C"},
            "groupe":"FONDAMENTAUX","palier":1,"palierTxt":"Avancé","origin":"ACADEMIE","teach":false,
            "desc":"Tu échanges ta place avec une bûche au dernier instant.","mudras":["Tigre","Sanglier","Buffle"]}],
         "etat":{"titre":"BLESSE","titreTxt":"BLESSE","sousTxt":"Rien de grave : tu tiens debout, mais ça tire.",
           "vie":{"lvl":1,"txt":"Quelques plaies"},"chakra":{"lvl":0,"txt":"Bien rempli"},
           "endurance":{"lvl":2,"txt":"Essoufflé"},"volonte":{"lvl":0,"txt":"Ta volonté tient encore."},
           "soins":{"lvl":0,"txt":"Aucun soin en attente."},"ata":null,"ko":null,
           "blessures":[{"zone":"TORSE","label":"TORSE","type":"HEMATOME","txt":"Hématome, fort","forte":true},
                        {"zone":"BRAS_G","label":"BRAS GAUCHE","type":"BRULURE","txt":"Brûlure, légère","forte":false}],
           "echelles":{
             "vie":[[0.95,"Indemne",0],[0.7,"Quelques égratignures",0],[0.45,"Quelques plaies",1],[0.2,"Mal en point",1],[0,"Au bord de l'évanouissement",2]],
             "chakra":[[0.8,"Bien rempli",0],[0.5,"Entamé",1],[0.25,"Bas",1],[0.01,"Presque vide",2],[0,"Épuisé",2]],
             "endurance":[[0.8,"Frais",0],[0.5,"En forme",1],[0.2,"Essoufflé",2],[0,"À bout de souffle",2]]}}}
        """;

    /** La démonstration sans aucune clé v2 (banc « ancien serveur »). */
    static String demoAncienServeur() {
        JsonObject r = JsonParser.parseString(DEMO_JSON).getAsJsonObject();
        for (String k : List.of("v", "pid", "rankId", "identite", "lignee", "apprentissage", "etat")) r.remove(k);
        JsonArray ts = r.getAsJsonArray("techniques");
        for (JsonElement e : ts) {
            JsonObject t = e.getAsJsonObject();
            for (String k : List.of("letter", "groupe", "palier", "palierTxt", "origin", "teach", "desc", "mudras")) t.remove(k);
        }
        return r.toString();
    }
}
