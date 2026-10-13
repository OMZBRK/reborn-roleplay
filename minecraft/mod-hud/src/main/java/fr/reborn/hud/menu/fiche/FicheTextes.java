package fr.reborn.hud.menu.fiche;

import fr.reborn.hud.menu.stats.StatDef;

import java.util.List;
import java.util.Locale;

/**
 * Mots et phrases de la fiche — jamais de chiffre exact (ni niveau, ni XP, ni valeur de stat, ni pourcentage).
 * Ce sont aussi les replis quand le serveur n'envoie pas encore les blocs v2 (PROTOCOLE §4).
 */
final class FicheTextes {

    private FicheTextes() {}

    /* ---------------------------------------------------------------- paliers de stat */

    static final String[] PALIERS = {"Novice", "Initié", "Aguerri", "Expert", "Maître", "Légendaire"};

    static int palierIdx(int v, int max) {
        if (v >= max) return 5;
        return v >= 8 ? 4 : v >= 6 ? 3 : v >= 4 ? 2 : v >= 2 ? 1 : 0;
    }

    static String palier(int v, int max) { return PALIERS[palierIdx(v, max)]; }

    /** Ligne de palier de l'infobulle : [début, mot doré (ou null), fin]. */
    static String[] lignePalier(int v, int vise, int max) {
        String a = palier(v, max);
        if (vise >= max && v >= max) return new String[]{a + " : le sommet de cette voie.", null, ""};
        if (vise == v) return new String[]{a + ".", null, ""};
        String b = palier(vise, max);
        if (b.equals(a)) return new String[]{a + ", un cran plus haut une fois validé.", null, ""};
        return new String[]{a + ", puis ", b, " une fois validé."};
    }

    /** Phrase du seuil (t = valeur visée, s = plafond doux). */
    static String phraseSeuil(int v, int t, double s, int max) {
        if (t >= max) return "Tu as atteint le sommet de cette voie.";
        if (t > s + 1e-6) return "Au-dessus du fil du seuil, chaque point pèse plus lourd.";
        if (Math.abs(t - s) < 0.51 || Math.abs(v - s) < 0.51) {
            return "Tu touches le fil du seuil : au-delà, chaque point pèsera plus lourd.";
        }
        return "Sous le fil du seuil, chaque point porte pleinement ses fruits.";
    }

    /* ---------------------------------------------------------------- grades, mérite */

    /** Kanji du grade : par rankId, sinon par le nom affiché, sinon rien. */
    static String kanjiGrade(String rankId, String rank) {
        String k = rankId != null ? rankId.toUpperCase(Locale.ROOT) : "";
        String r = FicheArt.cle(rank).replace(' ', '_').replace('-', '_').toUpperCase(Locale.ROOT);
        String id = !k.isEmpty() ? k : r;
        return switch (id) {
            case "ACADEMICIEN", "ACADEMICIENNE", "ETUDIANT" -> "学";
            case "GENIN", "GENIN_CONFIRME" -> "下忍";
            case "CHUNIN" -> "中忍";
            case "KONIN" -> "高忍";
            case "TOKUBETSU_JONIN" -> "特上";
            case "JONIN", "COMMANDANT_JONIN" -> "上忍";
            case "KAGE", "HOKAGE", "KAZEKAGE", "MIZUKAGE", "RAIKAGE", "TSUCHIKAGE" -> "影";
            default -> "";
        };
    }

    static final String[] MERITE = {"Pas encore remarqué", "Reconnu par ton village", "Estimé de ton village",
        "Honoré par ton village", "Une figure de ton village", "Une légende de ton village"};

    static String merite(int palier) { return MERITE[Math.max(0, Math.min(5, palier))]; }

    /* ---------------------------------------------------------------- clans, villages, natures */

    private static final String[] CLANS = {"hyuga", "uchiha", "senju", "nara", "sarutobi", "uzumaki", "aburame",
        "akimichi", "hatake", "yamanaka", "kurama"};

    /** Colonne du clan dans la rangée 0 de hanko (11 = clan inconnu). */
    static int colClan(String clan) {
        String c = FicheArt.cle(clan);
        for (int i = 0; i < CLANS.length; i++) if (c.equals(CLANS[i]) || c.startsWith(CLANS[i])) return i;
        return 11;
    }

    private static final String[][] VILLAGES = {
        {"konoha", "konohagakure"}, {"suna", "sunagakure"}, {"kiri", "kirigakure"},
        {"kumo", "kumogakure"}, {"iwa", "iwagakure"}, {"ame", "amegakure"}};

    /** Index d'emblème (konoha 0 … ame 5), −1 sinon. */
    static int village(String v) {
        String c = FicheArt.cle(v);
        for (int i = 0; i < VILLAGES.length; i++) {
            if (c.equals(VILLAGES[i][0]) || c.equals(VILLAGES[i][1]) || c.startsWith(VILLAGES[i][0])) return i;
        }
        return -1;
    }

    /** Couleur de lueur et de braises du village (table de la sélection de personnage). */
    static int couleurVillage(String v) {
        return switch (village(v)) {
            case 0 -> 0xF2A548;
            case 1 -> 0xE8C88A;
            case 2 -> 0x9ED8EA;
            case 3 -> 0xF4EC8C;
            case 4 -> 0xD39B6A;
            case 5 -> 0xA9B8DA;
            default -> 0xD9A95E;
        };
    }

    record Nature(String id, String kj, String nom, int couleur) {}

    static final List<Nature> NATURES = List.of(
        new Nature("SUITON", "水", "Suiton", 0x6A9CC8),
        new Nature("FUTON", "風", "Fūton", 0x86B49C),
        new Nature("KATON", "火", "Katon", 0xD0704C),
        new Nature("RAITON", "雷", "Raiton", 0xD8C860),
        new Nature("DOTON", "土", "Doton", 0xA88458));

    /** Index de nature (colonne hanko rangée 1), −1 si aucune. */
    static int nature(String id) {
        String c = id == null ? "" : id.toUpperCase(Locale.ROOT);
        for (int i = 0; i < NATURES.size(); i++) if (NATURES.get(i).id().equals(c)) return i;
        return -1;
    }

    /* ---------------------------------------------------------------- techniques */

    static final String[] MUDRAS = {"Rat", "Buffle", "Tigre", "Lièvre", "Dragon", "Serpent", "Cheval", "Bélier",
        "Singe", "Coq", "Chien", "Sanglier"};
    static final String[] MUDRAS_KJ = {"子", "丑", "寅", "卯", "辰", "巳", "午", "未", "申", "酉", "戌", "亥"};

    static String kanjiMudra(String nom) {
        String c = FicheArt.cle(nom);
        for (int i = 0; i < MUDRAS.length; i++) if (FicheArt.cle(MUDRAS[i]).equals(c)) return MUDRAS_KJ[i];
        return "印";
    }

    /** Lettre de rang affichée : E → base ; Hiden → S. */
    static String lettre(String rank) {
        if (rank == null) return "";
        String r = rank.trim().toUpperCase(Locale.ROOT);
        if (r.startsWith("HIDEN") || r.startsWith("KINJUTSU")) return "S";
        if (r.length() == 1 && "DCBAS".contains(r)) return r;
        return "";
    }

    /** Groupe d'une technique depuis la racine de sa catégorie. */
    static String groupe(String cat, String rank) {
        String root = cat == null ? "" : cat.split("/")[0].toLowerCase(Locale.ROOT);
        if ("E".equalsIgnoreCase(rank) || root.startsWith("fondament") || root.equals("base")) return "FONDAMENTAUX";
        return switch (root) {
            case "ninjutsu" -> "NINJUTSU";
            case "taijutsu" -> "TAIJUTSU";
            case "bukijutsu", "kenjutsu" -> "KENJUTSU";
            default -> "AUTRES";
        };
    }

    static final String[] GROUPES = {"NINJUTSU", "TAIJUTSU", "KENJUTSU", "FONDAMENTAUX", "AUTRES"};
    static final String[] GROUPES_KJ = {"忍", "体", "剣", "基", "術"};

    static String groupeNom(String g) {
        return switch (g) {
            case "NINJUTSU" -> "Ninjutsu";
            case "TAIJUTSU" -> "Taijutsu";
            case "KENJUTSU" -> "Kenjutsu";
            case "FONDAMENTAUX" -> "Fondamental";
            default -> "Autre";
        };
    }

    static final String[] PALIERS_MAITRISE = {"Novice", "Avancé", "Maître"};

    static int palierMaitrise(int mastery) { return mastery < 40 ? 0 : mastery < 80 ? 1 : 2; }

    static String origine(String o) {
        if (o == null) return null;
        return switch (o.toUpperCase(Locale.ROOT)) {
            case "PARCHEMIN" -> "Apprise dans un rouleau";
            case "ACADEMIE" -> "Enseignée à l'Académie";
            case "CLAN" -> "Transmise par ton clan";
            case "ENSEIGNEMENT" -> "Apprise auprès d'un maître";
            case "STAFF" -> "Accordée par les anciens";
            default -> null;
        };
    }

    /** Lettre de scaling → intensité, en mots. */
    static String gradeWords(String g) {
        return switch (g == null ? "" : g.trim().toUpperCase(Locale.ROOT)) {
            case "S" -> "énormément";
            case "A" -> "fortement";
            case "B" -> "nettement";
            case "C" -> "un peu";
            case "D" -> "à peine";
            default -> "légèrement";
        };
    }

    static int poidsGrade(String g) {
        return switch (g == null ? "" : g.trim().toUpperCase(Locale.ROOT)) {
            case "S" -> 5;
            case "A" -> 4;
            case "B" -> 3;
            case "C" -> 2;
            case "D" -> 1;
            default -> 0;
        };
    }

    static String castWords(int casts) {
        if (casts <= 0) return "ta réserve ne suffit pas encore";
        if (casts == 1) return "un seul lancer";
        if (casts <= 4) return "quelques lancers";
        if (casts <= 9) return "de nombreux lancers";
        return "presque à volonté";
    }

    /** « Nourrie nettement par ton Ninjutsu, un peu par ton Contrôle. » */
    static String nourrie(List<java.util.Map.Entry<StatDef, String>> tri) {
        if (tri.isEmpty()) return null;
        StringBuilder sb = new StringBuilder("Nourrie ");
        for (int i = 0; i < Math.min(2, tri.size()); i++) {
            if (i > 0) sb.append(", ");
            sb.append(gradeWords(tri.get(i).getValue())).append(" par ton ").append(tri.get(i).getKey().displayName);
        }
        return sb.append('.').toString();
    }

    static String avancement(int done, int total) {
        if (total <= 0 || done <= 0) return "Tu as fait le premier pas.";
        float r = done / (float) total;
        if (r < 0.45f) return "Tu as fait le premier pas.";
        if (r < 0.8f) return "La moitié du chemin est faite.";
        return "Il ne te reste presque plus rien à apprendre.";
    }

    /* ---------------------------------------------------------------- état */

    /** Échelle par défaut : seuils décroissants {ratio_min, lvl} + phrase. */
    record Cran(double min, String txt, int lvl) {}

    static final List<Cran> VIE = List.of(new Cran(0.95, "Indemne", 0), new Cran(0.7, "Quelques égratignures", 0),
        new Cran(0.45, "Quelques plaies", 1), new Cran(0.2, "Mal en point", 1), new Cran(0, "Au bord de l'évanouissement", 2));
    static final List<Cran> CHAKRA = List.of(new Cran(0.8, "Bien rempli", 0), new Cran(0.5, "Entamé", 1),
        new Cran(0.25, "Bas", 1), new Cran(0.01, "Presque vide", 2), new Cran(0, "Épuisé", 2));
    static final List<Cran> ENDURANCE = List.of(new Cran(0.8, "Frais", 0), new Cran(0.5, "En forme", 1),
        new Cran(0.2, "Essoufflé", 2), new Cran(0, "À bout de souffle", 2));

    static Cran cran(List<Cran> echelle, double ratio) {
        for (Cran c : echelle) if (ratio >= c.min()) return c;
        return echelle.isEmpty() ? new Cran(0, "", 0) : echelle.get(echelle.size() - 1);
    }

    static final String[] TITRES = {"INDEMNE", "BLESSE", "GRAVE", "ATA", "A_TERRE", "INCONSCIENT"};

    /** Colonne hanko (rangée 1) du titre d'état : 5 健 … 10 眠. */
    static int colTitre(String titre) {
        return switch (titre == null ? "" : titre) {
            case "BLESSE" -> 6;
            case "GRAVE" -> 7;
            case "ATA" -> 8;
            case "A_TERRE" -> 9;
            case "INCONSCIENT" -> 10;
            default -> 5;
        };
    }

    static String titreTxt(String titre) {
        return switch (titre == null ? "" : titre) {
            case "BLESSE" -> "BLESSE";
            case "GRAVE" -> "GRAVEMENT BLESSE";
            case "ATA" -> "EN ATTENTE DE SOIN";
            case "A_TERRE" -> "A TERRE";
            case "INCONSCIENT" -> "INCONSCIENT";
            default -> "INDEMNE";
        };
    }

    static String sousTitre(String titre) {
        return switch (titre == null ? "" : titre) {
            case "BLESSE" -> "Rien de grave : tu tiens debout, mais ça tire.";
            case "GRAVE" -> "Il te faut un médic, vite.";
            case "ATA" -> "Pas de technique ni de course tant qu'on ne t'a pas soigné.";
            case "A_TERRE" -> "Tu es à terre : quelqu'un doit venir te relever.";
            case "INCONSCIENT" -> "Tu as perdu connaissance.";
            default -> "Rien à signaler : tu es en pleine forme.";
        };
    }

    /** Colonne hanko (rangée 3) d'un type de blessure. */
    static int colBlessure(String type) {
        return switch (type == null ? "" : type) {
            case "BRULURE" -> 1;
            case "OS_CASSE" -> 2;
            case "PLAIE" -> 3;
            case "INFECTION" -> 4;
            default -> 0;
        };
    }

    static final String CHAPEAU_RESSENS = "Seule ta sensation : un ninja médecin peut t'ausculter pour en savoir plus.";
    static final String SANS_SENSATION = "Aucune sensation transmise.";
    static final String SANS_DOULEUR = "Aucune douleur : ton corps ne se plaint de rien.";
    static final String AUTRES_DOULEURS = "Et d'autres douleurs…";
    static final String HISTOIRE = "Ton histoire reste à écrire.";

    /* ---------------------------------------------------------------- service */

    /** Colonne hanko (rangée 2) d'un état de service. */
    static int colService(String type) {
        return switch (type == null ? "" : type) {
            case "ENREGISTREMENT" -> 0;
            case "PROMOTION" -> 1;
            case "RETROGRADATION" -> 2;
            case "MISSION" -> 3;
            case "DISTINCTION" -> 4;
            default -> 5;
        };
    }
}
