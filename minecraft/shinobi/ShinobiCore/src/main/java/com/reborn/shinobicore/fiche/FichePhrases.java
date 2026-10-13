package com.reborn.shinobicore.fiche;

import org.bukkit.configuration.ConfigurationSection;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Toutes les phrases de la fiche shinobi v2 (palier → phrase). Les valeurs par
 * défaut sont dans le code ; {@link #load(ConfigurationSection)} permettra de
 * les surcharger depuis un futur bloc {@code fiche:} du config — <b>non branché
 * pour l'instant</b> (le config.yml est tenu par une autre session).
 *
 * <p>RÈGLE : aucun chiffre exact (niveau, XP, valeur de stat, pourcentage) dans
 * une phrase envoyée au client.
 */
public final class FichePhrases {

    private FichePhrases() {}

    /** Une marche d'échelle : à partir de {@code min} (ratio 0..1), phrase {@code txt}, mèche {@code lvl}. */
    public record Step(double min, String txt, int lvl) {}

    /* ------------------------------------------------------------- Mérite */

    private static List<String> merite = List.of(
            "Pas encore remarqué",
            "Reconnu par ton village",
            "Estimé de ton village",
            "Honoré par ton village",
            "Une figure de ton village",
            "Une légende de ton village");

    /** Palier de Mérite (0..5) depuis les points de Mérite ({@code 3 × grade}). */
    public static int meritePalier(int meritPoints) {
        return Math.max(0, Math.min(merite.size() - 1, meritPoints / 3));
    }

    public static String merite(int palier) {
        return merite.get(Math.max(0, Math.min(merite.size() - 1, palier)));
    }

    /* ------------------------------------------------------------ Maîtrise */

    private static int[] maitrise = {40, 80};
    private static List<String> maitriseTxt = List.of("Novice", "Avancé", "Maître");

    /** 0 Novice, 1 Avancé, 2 Maître. */
    public static int maitrisePalier(int mastery) {
        if (mastery < maitrise[0]) return 0;
        if (mastery < maitrise[1]) return 1;
        return 2;
    }

    public static String maitriseTxt(int palier) {
        return maitriseTxt.get(Math.max(0, Math.min(2, palier)));
    }

    /* ----------------------------------------------------- Échelles vitales */

    private static List<Step> vie = List.of(
            new Step(0.95, "Indemne", 0),
            new Step(0.7, "Quelques égratignures", 0),
            new Step(0.45, "Quelques plaies", 1),
            new Step(0.2, "Mal en point", 1),
            new Step(0, "Au bord de l'évanouissement", 2));

    private static List<Step> chakra = List.of(
            new Step(0.8, "Bien rempli", 0),
            new Step(0.5, "Entamé", 1),
            new Step(0.25, "Bas", 1),
            new Step(0.01, "Presque vide", 2),
            new Step(0, "Épuisé", 2));

    private static List<Step> endurance = List.of(
            new Step(0.8, "Frais", 0),
            new Step(0.5, "En forme", 1),
            new Step(0.2, "Essoufflé", 2),
            new Step(0, "À bout de souffle", 2));

    public static List<Step> vie() { return vie; }
    public static List<Step> chakra() { return chakra; }
    public static List<Step> endurance() { return endurance; }

    /** Première marche dont le seuil est atteint (la dernière sinon). */
    public static Step pick(List<Step> scale, double ratio) {
        double r = Double.isNaN(ratio) ? 0 : Math.max(0, Math.min(1, ratio));
        for (Step s : scale) if (r >= s.min()) return s;
        return scale.get(scale.size() - 1);
    }

    /* ------------------------------------------------------------ Volonté */

    /** Relèves restantes (2, 1, 0) → phrase, mèche. */
    public static Step volonte(int left) {
        if (left >= 2) return new Step(1, "Tient encore", 0);
        if (left == 1) return new Step(0.5, "Vacille", 1);
        return new Step(0, "À bout : il te faut un médic", 2);
    }

    /* -------------------------------------------------------------- Soins */

    public static final String SOINS_AUCUN = "Aucun soin en attente.";
    public static final String SOINS_ATTENTE = "Une blessure attend un médic.";
    public static final String SOINS_EN_COURS = "Soin en cours : le médic repassera plus tard.";

    /* ---------------------------------------------------------------- ATA */

    public static String ata(float progress) {
        if (progress < 0.34f) return "Encore un long repos";
        if (progress < 0.67f) return "À mi-chemin du repos";
        return "Presque remis";
    }

    /* ----------------------------------------------------- Titres d'état */

    /** Seuils de vie pour le titre global (sans blessure visible). */
    public static double titreBlesseSous = 0.7;
    public static double titreGraveSous = 0.3;

    private static final Map<String, String[]> TITRES = new LinkedHashMap<>();
    static {
        // Mêmes libellés et phrases que les replis du mod (FicheTextes.titreTxt / sousTitre).
        TITRES.put("A_TERRE", new String[]{"A TERRE", "Tu es à terre : quelqu'un doit venir te relever."});
        TITRES.put("INCONSCIENT", new String[]{"INCONSCIENT", "Tu as perdu connaissance."});
        TITRES.put("ATA", new String[]{"EN ATTENTE DE SOIN", "Pas de technique ni de course tant qu'on ne t'a pas soigné."});
        TITRES.put("GRAVE", new String[]{"GRAVEMENT BLESSE", "Il te faut un médic, vite."});
        TITRES.put("BLESSE", new String[]{"BLESSE", "Rien de grave : tu tiens debout, mais ça tire."});
        TITRES.put("INDEMNE", new String[]{"INDEMNE", "Rien à signaler : tu es en pleine forme."});
    }

    /** Texte ArcadePix du titre (majuscules sans accents). */
    public static String titreTxt(String titre) {
        String[] t = TITRES.get(titre);
        return t == null ? titre : t[0];
    }

    public static String sousTxt(String titre) {
        String[] t = TITRES.get(titre);
        return t == null ? "" : t[1];
    }

    /* ------------------------------------------------------------ Dōjutsu */

    private static final Map<String, String> DOJUTSU_CLANS = new LinkedHashMap<>();
    static {
        DOJUTSU_CLANS.put("hyuga", "Byakugan");
        DOJUTSU_CLANS.put("uchiha", "Sharingan");
    }

    private static final Map<String, List<String>> DOJUTSU_STADES = new LinkedHashMap<>();
    static {
        DOJUTSU_STADES.put("Byakugan", List.of(
                "Les yeux de ton clan. Ils ne sont pas encore éveillés.",
                "Tes yeux se sont éveillés : les veines saillent quand tu regardes.",
                "Ton regard perce presque tout ce qui t'entoure."));
        DOJUTSU_STADES.put("Sharingan", List.of(
                "Les yeux de ton clan. Ils ne sont pas encore éveillés.",
                "Un premier tomoe s'est éveillé dans ton regard.",
                "Deux tomoe tournent dans ton regard.",
                "Trois tomoe : ton regard est complet."));
    }

    /** Dōjutsu du clan ({@code Hyuga}, {@code Hyūga} → Byakugan…), ou {@code null}. */
    public static String dojutsuOf(String clan) {
        if (clan == null) return null;
        return DOJUTSU_CLANS.get(fold(clan));
    }

    /** Phrase du stade ; au-delà du dernier stade décrit, la dernière phrase. */
    public static String dojutsuTxt(String nom, int stade) {
        List<String> l = DOJUTSU_STADES.get(nom);
        if (l == null || l.isEmpty()) return stade <= 0 ? "Les yeux de ton clan. Ils ne sont pas encore éveillés." : "Tes yeux se sont éveillés.";
        return l.get(Math.max(0, Math.min(l.size() - 1, stade)));
    }

    /* ------------------------------------------------------- Fondamentaux */

    private static Set<String> fondamentaux = new LinkedHashSet<>(List.of("shunshin", "kawarimi", "henge", "bunshin"));

    public static boolean fondamental(String techId) {
        return techId != null && fondamentaux.contains(techId.toLowerCase(Locale.ROOT));
    }

    /* -------------------------------------------------------- Natures */

    public static final String NATURE_AUCUNE = "Le test de la feuille t'attend.";
    public static final String NATURE_RESTE = "Une autre nature sommeille encore.";

    /* ------------------------------------------------------ Apprentissage */

    public static final String APP_PRET = "Séance possible";
    public static final String APP_MAITRE = "Maître à portée : séance possible";
    public static final String APP_SANS_ROULEAU = "Il te faut le rouleau pour continuer.";
    public static final String APP_VALIDATION = "Entre les mains du staff.";

    /** Phrase du repos : « dans environ N h », « dans moins d'une heure », « demain » au-delà de 12 h. */
    public static String repos(long waitMillis) {
        long h = Math.round(waitMillis / 3_600_000.0);
        if (waitMillis > 12L * 3_600_000L) return "Prochaine séance : demain";
        if (h < 1) return "Prochaine séance : dans moins d'une heure";
        return "Prochaine séance : dans environ " + h + " h";
    }

    /* ---------------------------------------------------------- Surcharge */

    /**
     * Surcharge depuis un bloc {@code fiche:} (futur). Chaque clé absente garde
     * sa valeur par défaut. NON appelé tant que le bloc n'existe pas dans le config.
     */
    public static void load(ConfigurationSection sec) {
        if (sec == null) return;
        List<String> m = sec.getStringList("merite.paliers");
        if (m.size() >= 2) merite = List.copyOf(m);
        List<Integer> mt = sec.getIntegerList("maitrise.paliers");
        if (mt.size() == 2 && mt.get(0) < mt.get(1)) maitrise = new int[]{mt.get(0), mt.get(1)};
        vie = steps(sec.getMapList("echelles.vie"), vie);
        chakra = steps(sec.getMapList("echelles.chakra"), chakra);
        endurance = steps(sec.getMapList("echelles.endurance"), endurance);
        List<String> f = sec.getStringList("fondamentaux");
        if (!f.isEmpty()) {
            Set<String> s = new LinkedHashSet<>();
            for (String x : f) s.add(x.toLowerCase(Locale.ROOT));
            fondamentaux = s;
        }
        ConfigurationSection dc = sec.getConfigurationSection("dojutsu.clans");
        if (dc != null) {
            for (String k : dc.getKeys(false)) {
                String v = dc.getString(k);
                if (v != null && !v.isBlank()) DOJUTSU_CLANS.put(fold(k), v.trim());
            }
        }
        ConfigurationSection ds = sec.getConfigurationSection("dojutsu.stades");
        if (ds != null) {
            for (String k : ds.getKeys(false)) {
                List<String> l = ds.getStringList(k);
                if (!l.isEmpty()) DOJUTSU_STADES.put(k, List.copyOf(l));
            }
        }
    }

    /** Lit une échelle {@code - {min: 0.8, txt: "...", lvl: 0}} ; repli si illisible. */
    private static List<Step> steps(List<Map<?, ?>> raw, List<Step> fallback) {
        if (raw == null || raw.isEmpty()) return fallback;
        List<Step> out = new ArrayList<>();
        for (Map<?, ?> m : raw) {
            try {
                double min = ((Number) m.get("min")).doubleValue();
                String txt = String.valueOf(m.get("txt"));
                int lvl = m.get("lvl") instanceof Number n ? n.intValue() : 0;
                out.add(new Step(min, txt, Math.max(0, Math.min(2, lvl))));
            } catch (RuntimeException e) {
                return fallback;
            }
        }
        out.sort((a, b) -> Double.compare(b.min(), a.min()));
        return out.isEmpty() ? fallback : List.copyOf(out);
    }

    /** Minuscules sans accents ni espaces (comparaison de clan, de nom). */
    public static String fold(String s) {
        return Normalizer.normalize(s.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]", "");
    }
}
