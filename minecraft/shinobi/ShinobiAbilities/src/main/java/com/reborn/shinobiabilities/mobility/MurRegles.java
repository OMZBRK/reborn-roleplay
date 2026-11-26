package com.reborn.shinobiabilities.mobility;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;

/**
 * Règles pures de l'essai de la marche sur les murs (lot 5, étapes C0 et C1a, {@link MurTest}) : aucune classe du
 * serveur, tout se teste sans lui ({@code MurReglesTest}).
 *
 * <ul>
 *   <li><b>Protocole</b> {@value #CANAL} (JSON UTF-8 brut, champ {@code v} = {@value #VERSION}, champs inconnus
 *       ignorés) : lecture des demandes du mod ({@link #lire}), écriture des trois messages du serveur
 *       ({@link #permis}, {@link #etat}, {@link #refus}). Une demande peut porter un numéro {@code n}, recopié dans
 *       la réponse faite au joueur ; un refus nomme l'action refusée (champ {@code a}) ;</li>
 *   <li><b>Faces</b> ({@link Face}) : la normale {@code n} de la paroi, cardinale, qui pointe du mur vers l'air ;</li>
 *   <li><b>Contrôle de paroi</b> ({@link #murDerriere}) : un bloc dont la face tournée vers le joueur est pleine se
 *       trouve à portée derrière lui, dans la direction {@code −n} ;</li>
 *   <li><b>Coin sortant</b> ({@link #areteSortante}, {@link #arriveeSortant}, {@link #coinSortant}) : le mur A
 *       s'arrête, le joueur tourne l'angle et passe sur la face B du même bloc d'angle. Le coin rentrant, lui, est
 *       jugé par le contrôle de paroi (le mur B est déjà derrière le joueur) ;</li>
 *   <li><b>Changement de face</b> ({@link #jugerFace}) : la réponse du serveur à une demande {@code face}, coin
 *       rentrant, coin sortant ou refus ;</li>
 *   <li><b>Fenêtre de virage</b> ({@link #murEnVirage}, {@link Tenue}) : pendant un virage, le mur peut être celui de
 *       la face quittée, celui de la nouvelle, ou le bloc d'angle ;</li>
 *   <li><b>Paquets refusés</b> ({@link CompteurRefus}) : un paquet de déplacement refusé compte une fois, même quand
 *       le serveur lui donne deux raisons ;</li>
 *   <li><b>Bilan</b> ({@link Compteurs}) : les chiffres de l'essai, écrits pour quelqu'un qui n'est pas développeur.</li>
 * </ul>
 */
public final class MurRegles {

    public static final String CANAL = "reborn:mur";
    public static final int VERSION = 1;
    /** Face envoyée quand l'entité n'est plus au mur. */
    public static final String SANS_FACE = "-";

    /** Accroche : la surface du mur est à cette distance au plus derrière {@code P} (debout, {@code P} en est à 0,3). */
    public static final double PORTEE_ACCROCHE = 1.3;
    /** Marche : la surface du mur est à cette distance au plus derrière {@code P} (couché, {@code P} en est à 0,9). */
    public static final double PORTEE_MARCHE = 1.5;
    /** Hauteur lue au-dessus de {@code P} : le milieu du cube du serveur (0,6 de haut), donc les pieds à l'accroche. */
    public static final double HAUTEUR_CUBE = 0.3;
    /** Hauteur du torse d'un joueur debout : là où le cube se trouve une fois la bascule finie. */
    public static final double HAUTEUR_TORSE = 0.9;
    /** Au mur, {@code P} (bas-centre du cube du serveur) se tient à cette distance de la surface. */
    public static final double DISTANCE_PAROI = 0.9;
    /** Demi-largeur du cube du serveur (0,6 de côté). */
    public static final double DEMI_CUBE = 0.3;
    /** Coin sortant : la fin du mur est cherchée à cette distance au plus devant {@code P}, dans le sens de marche. */
    public static final double PORTEE_ARETE = 1.5;
    /** Fenêtre de virage : le bloc d'angle compte comme mur tant qu'il est à cette distance au plus de {@code P}. */
    public static final double PORTEE_ANGLE = 1.5;
    /**
     * Durée de la fenêtre de virage après un changement de face accepté (ticks) : l'accord met un trajet à arriver au
     * mod, qui joue alors son virage (4 à 10 ticks) ; le serveur ne le voit qu'un trajet plus tard.
     */
    public static final int TICKS_VIRAGE = 30;
    /** Le serveur relit la paroi et l'état du joueur à cette période (ticks). */
    public static final int PERIODE_CONTROLE = 5;
    /** Un paquet refusé juste après le lâcher (le cube redevient debout) est encore compté au mur (ticks). */
    public static final int SORTIE_TICKS = 40;
    /** Un message du mod plus long que cela n'est pas lu (octets). */
    public static final int TAILLE_MAX = 256;
    /** Plafond du nombre de corrections qu'un mod peut annoncer pour une marche. */
    public static final int CORRECTIONS_MAX = 100_000;

    private static final double EPS = 1.0e-6;

    private MurRegles() {}

    /* ================================================================ faces */

    /**
     * Face de la paroi : la direction de sa normale {@code n}, du mur vers l'air. {@code E} : le mur est à l'ouest du
     * joueur, son corps part vers l'est.
     */
    public enum Face {
        N(0, -1), S(0, 1), E(1, 0), W(-1, 0);

        /** Composantes de la normale (une seule n'est pas nulle). */
        public final int nx, nz;

        Face(int nx, int nz) {
            this.nx = nx;
            this.nz = nz;
        }

        /** La face de ce code ({@code N}, {@code S}, {@code E}, {@code W}), ou null. */
        public static Face de(String code) {
            if (code == null) return null;
            return switch (code) {
                case "N" -> N;
                case "S" -> S;
                case "E" -> E;
                case "W" -> W;
                default -> null;
            };
        }

        /**
         * Les deux faces voisines, celles vers lesquelles on tourne à un coin (normales perpendiculaires). Ni cette
         * face, ni la face opposée.
         */
        public List<Face> voisines() {
            return nx != 0 ? List.of(N, S) : List.of(E, W);
        }

        /** Cette face est-elle une voisine de l'autre (perpendiculaire) ? */
        public boolean voisine(Face autre) {
            return autre != null && (nx != 0) != (autre.nx != 0);
        }
    }

    /* ================================================================ protocole, C → S */

    public enum Action {
        ACCROCHE("accroche"), FACE("face"), LACHE("lache");

        /** Le mot du protocole : champ {@code a} d'une demande, et d'un refus. */
        public final String code;

        Action(String code) { this.code = code; }
    }

    /**
     * Une demande du mod. {@code face} : pour {@link Action#ACCROCHE} et {@link Action#FACE} ; {@code corrections} :
     * pour {@link Action#LACHE}, le nombre de corrections de position que le mod a reçues pendant la marche ;
     * {@code n} : le numéro que le mod a donné à sa demande (champ facultatif, null s'il manque ou n'est pas un
     * entier), à recopier tel quel dans la réponse.
     */
    public record Demande(Action action, Face face, int corrections, Integer n) {}

    /** Lit un message du mod ; null s'il est trop long, mal formé, d'une autre version ou sans face valable. */
    public static Demande lire(byte[] octets) {
        if (octets == null || octets.length == 0 || octets.length > TAILLE_MAX) return null;
        return lire(new String(octets, StandardCharsets.UTF_8));
    }

    /** Lit un message du mod (texte JSON) ; null s'il n'est pas valable. */
    public static Demande lire(String brut) {
        if (brut == null) return null;
        try {
            JsonElement racine = JsonParser.parseString(brut);
            if (!racine.isJsonObject()) return null;
            JsonObject o = racine.getAsJsonObject();
            if (!entier(o, "v") || o.get("v").getAsInt() != VERSION) return null;
            String a = texte(o, "a");
            if (a == null) return null;
            Integer n = entier(o, "n") ? o.get("n").getAsInt() : null;
            return switch (a) {
                case "accroche" -> avecFace(Action.ACCROCHE, o, n);
                case "face" -> avecFace(Action.FACE, o, n);
                case "lache" -> new Demande(Action.LACHE, null,
                        entier(o, "c") ? Math.max(0, Math.min(CORRECTIONS_MAX, o.get("c").getAsInt())) : 0, n);
                default -> null;
            };
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static Demande avecFace(Action action, JsonObject o, Integer n) {
        Face f = Face.de(texte(o, "f"));
        return f == null ? null : new Demande(action, f, 0, n);
    }

    private static boolean entier(JsonObject o, String champ) {
        JsonElement e = o.get(champ);
        if (e == null || !e.isJsonPrimitive() || !((JsonPrimitive) e).isNumber()) return false;
        double d = e.getAsDouble();
        return d == Math.rint(d) && Math.abs(d) <= Integer.MAX_VALUE;
    }

    private static String texte(JsonObject o, String champ) {
        JsonElement e = o.get(champ);
        return e != null && e.isJsonPrimitive() && ((JsonPrimitive) e).isString() ? e.getAsString() : null;
    }

    /* ================================================================ protocole, S → C */

    /** {@code {"v":1,"t":"permis","on":true}} : le joueur peut s'accrocher ; {@code false} : interdit, il lâche. */
    public static byte[] permis(boolean on) {
        JsonObject o = base("permis");
        o.addProperty("on", on);
        return octets(o);
    }

    /** {@code {"v":1,"t":"etat","e":4182,"f":"E"}} : l'entité marche sur un mur de cette face ; null : elle n'y est plus. */
    public static byte[] etat(int entite, Face face) {
        return etat(entite, face, null);
    }

    /**
     * {@code {"v":1,"t":"etat","e":4182,"f":"N","n":13}} : l'exemplaire du joueur lui-même, quand cet état répond à
     * sa demande numéro {@code n}. {@code n} null : même message que {@link #etat(int, Face)} (le serveur parle de
     * lui-même, ou à un voisin).
     */
    public static byte[] etat(int entite, Face face, Integer n) {
        JsonObject o = base("etat");
        o.addProperty("e", entite);
        o.addProperty("f", face == null ? SANS_FACE : face.name());
        if (n != null) o.addProperty("n", n);
        return octets(o);
    }

    /** {@code {"v":1,"t":"refus","m":"…"}} : refus sans action nommée, la forme de l'étape C0. */
    public static byte[] refus(String message) {
        return refus(message, null, null);
    }

    /**
     * {@code {"v":1,"t":"refus","m":"…","a":"face","n":13}} : {@code a} est l'action refusée ({@code accroche} : le
     * joueur n'est pas ou n'est plus au mur ; {@code face} : il reste sur sa face), {@code n} le numéro de la demande.
     * Les deux sont omis s'ils sont null.
     */
    public static byte[] refus(String message, Action action, Integer n) {
        JsonObject o = base("refus");
        o.addProperty("m", message == null ? "" : message);
        if (action != null) o.addProperty("a", action.code);
        if (n != null) o.addProperty("n", n);
        return octets(o);
    }

    private static JsonObject base(String type) {
        JsonObject o = new JsonObject();
        o.addProperty("v", VERSION);
        o.addProperty("t", type);
        return o;
    }

    private static byte[] octets(JsonObject o) {
        return o.toString().getBytes(StandardCharsets.UTF_8);
    }

    /* ================================================================ contrôle de paroi */

    /** Lecture du monde par le contrôle de paroi : rien d'autre n'est demandé au serveur. */
    @FunctionalInterface
    public interface LecteurBlocs {
        /**
         * Les boîtes de collision du bloc, dans son repère (0 à 1 sur chaque axe), chacune sous la forme
         * {@code {minX, minY, minZ, maxX, maxY, maxZ}} ; vide ou null : pas de collision.
         */
        Collection<double[]> boites(int x, int y, int z);
    }

    /**
     * Un bloc plein est-il derrière le joueur ? On part de {@code (px, py, pz)} (la hauteur {@code py} est celle qu'on
     * veut lire : {@code P.y} + {@link #HAUTEUR_CUBE} ou {@link #HAUTEUR_TORSE}) et on recule le long de {@code −n} :
     * vrai dès qu'un bloc dont la face tournée vers {@code n} est pleine ({@link #facePleine}) a cette face à
     * {@code portee} au plus.
     */
    public static boolean murDerriere(LecteurBlocs blocs, double px, double py, double pz, Face face, double portee) {
        int y = (int) Math.floor(py);
        for (int[] b : blocsDerriere(px, pz, face, portee)) {
            if (facePleine(blocs.boites(b[0], y, b[1]), face)) return true;
        }
        return false;
    }

    /**
     * Le contrôle de paroi d'un joueur déjà au mur, {@code P} en {@code (px, py, pz)} ({@code py} : la hauteur de
     * {@code P} elle-même) : un mur plein de cette face à {@link #PORTEE_MARCHE} au plus derrière lui, lu à la hauteur
     * du cube. {@code bascule} : la bascule du mod peut ne pas être encore arrivée au serveur, la hauteur du torse est
     * acceptée aussi.
     */
    public static boolean murDeMarche(LecteurBlocs blocs, double px, double py, double pz, Face face, boolean bascule) {
        return murDerriere(blocs, px, py + HAUTEUR_CUBE, pz, face, PORTEE_MARCHE)
                || (bascule && murDerriere(blocs, px, py + HAUTEUR_TORSE, pz, face, PORTEE_MARCHE));
    }

    /**
     * Les colonnes {@code {x, z}} dont la face tournée vers {@code n} est entre 0 et {@code portee} derrière le point,
     * de la plus proche à la plus lointaine. La colonne où se tient le point n'en fait pas partie : sa face est devant lui.
     */
    public static List<int[]> blocsDerriere(double px, double pz, Face face, double portee) {
        boolean surX = face.nx != 0;
        double a = surX ? px : pz;
        int autre = (int) Math.floor(surX ? pz : px);
        List<int[]> out = new ArrayList<>(2);
        if (face.nx + face.nz > 0) {
            // le mur est du côté des coordonnées qui baissent : la face du bloc b est à b + 1, à (a − b − 1) du point
            for (int b = (int) Math.floor(a - 1 + EPS); a - (b + 1) <= portee + EPS; b--) out.add(colonne(surX, b, autre));
        } else {
            // le mur est du côté des coordonnées qui montent : la face du bloc b est à b, à (b − a) du point
            for (int b = (int) Math.ceil(a - EPS); b - a <= portee + EPS; b++) out.add(colonne(surX, b, autre));
        }
        return out;
    }

    private static int[] colonne(boolean surX, int b, int autre) {
        return surX ? new int[] {b, autre} : new int[] {autre, b};
    }

    /**
     * La face du bloc tournée vers {@code n} est-elle pleine ? Les boîtes de collision qui touchent ce bord du bloc
     * doivent, ensemble, couvrir tout le carré (un cube plein, deux dalles empilées, le dos d'un escalier : oui ; une
     * dalle, une barrière, un bloc en retrait : non).
     */
    public static boolean facePleine(Collection<double[]> boites, Face face) {
        if (boites == null || boites.isEmpty()) return false;
        boolean surX = face.nx != 0;
        boolean coteMax = face.nx + face.nz > 0;
        int axe = surX ? 0 : 2, travers = surX ? 2 : 0;
        List<double[]> rects = new ArrayList<>(boites.size());
        TreeSet<Double> us = new TreeSet<>(List.of(0.0, 1.0)), vs = new TreeSet<>(List.of(0.0, 1.0));
        for (double[] b : boites) {
            if (b == null || b.length < 6) continue;
            boolean touche = coteMax ? b[axe + 3] >= 1 - EPS : b[axe] <= EPS;
            if (!touche) continue;
            double u0 = borne(b[travers]), u1 = borne(b[travers + 3]), v0 = borne(b[1]), v1 = borne(b[4]);
            if (u1 - u0 <= EPS || v1 - v0 <= EPS) continue;
            rects.add(new double[] {u0, v0, u1, v1});
            us.add(u0);
            us.add(u1);
            vs.add(v0);
            vs.add(v1);
        }
        if (rects.isEmpty()) return false;
        Double[] u = us.toArray(new Double[0]), v = vs.toArray(new Double[0]);
        for (int i = 0; i + 1 < u.length; i++) {
            if (u[i + 1] - u[i] <= EPS) continue;
            double um = (u[i] + u[i + 1]) / 2;
            for (int j = 0; j + 1 < v.length; j++) {
                if (v[j + 1] - v[j] <= EPS) continue;
                double vm = (v[j] + v[j + 1]) / 2;
                boolean couvert = false;
                for (double[] r : rects) {
                    if (um > r[0] && um < r[2] && vm > r[1] && vm < r[3]) {
                        couvert = true;
                        break;
                    }
                }
                if (!couvert) return false;
            }
        }
        return true;
    }

    private static double borne(double d) { return d < 0 ? 0 : d > 1 ? 1 : d; }

    /* ================================================================ coin sortant */

    /**
     * L'arête verticale où le mur de la face A s'arrête : {@code (x, z)} est son point dans le plan horizontal (sur
     * la surface de A, à la fin du mur dans le sens de marche) ; {@code (blocX, blocZ)} la colonne du <b>bloc
     * d'angle</b>, le dernier bloc du mur, dont la face B fait le coin.
     */
    public record Arete(double x, double z, int blocX, int blocZ) {}

    /**
     * Cherche la fin du mur de la face {@code a} devant le joueur. On part de {@code (px, py, pz)} ({@code py} : la
     * hauteur lue, comme pour {@link #murDerriere}) : le mur est la première colonne à face pleine derrière le point,
     * à {@link #PORTEE_MARCHE} au plus ; on avance ensuite colonne par colonne dans le sens de marche {@code d} (une
     * face voisine de {@code a} : sa normale est la direction) tant que le bloc suivant, à la même profondeur, a sa
     * face {@code a} pleine. L'arête est le bord du dernier bloc plein.
     *
     * @return l'arête, ou null : {@code d} n'est pas voisine de {@code a}, pas de mur plein derrière le point, ou le
     *         mur continue au-delà de {@link #PORTEE_ARETE} devant lui
     */
    public static Arete areteSortante(LecteurBlocs blocs, double px, double py, double pz, Face a, Face d) {
        if (a == null || !a.voisine(d)) return null;
        int y = (int) Math.floor(py);
        int[] mur = null;
        for (int[] b : blocsDerriere(px, pz, a, PORTEE_MARCHE)) {
            if (facePleine(blocs.boites(b[0], y, b[1]), a)) {
                mur = b;
                break;
            }
        }
        if (mur == null) return null;
        boolean aSurX = a.nx != 0;
        // le long de la normale de A : la profondeur du mur ; le long de d : la colonne où se tient le point
        int profondeur = aSurX ? mur[0] : mur[1];
        int c = aSurX ? mur[1] : mur[0];
        double t = aSurX ? pz : px;
        int sens = d.nx + d.nz;
        while (true) {
            double bord = sens > 0 ? c + 1 : c;
            if ((bord - t) * sens > PORTEE_ARETE + EPS) return null;
            int suivant = c + sens;
            Collection<double[]> boites = aSurX ? blocs.boites(profondeur, y, suivant) : blocs.boites(suivant, y, profondeur);
            if (!facePleine(boites, a)) {
                double surface = a.nx + a.nz > 0 ? profondeur + 1 : profondeur;
                return aSurX ? new Arete(surface, bord, profondeur, c) : new Arete(bord, surface, c, profondeur);
            }
            c = suivant;
        }
    }

    /**
     * Le point d'où part le virage d'un coin sortant : {@code arête − 0,3 d + 0,9 nA}. Le bord meneur du cube est à
     * l'arête, {@code P} à {@link #DISTANCE_PAROI} de la face A. Rend {@code {x, z}} ; la hauteur ne change pas.
     */
    public static double[] departSortant(Arete arete, Face a, Face d) {
        return new double[] {
                arete.x() - DEMI_CUBE * d.nx + DISTANCE_PAROI * a.nx,
                arete.z() - DEMI_CUBE * d.nz + DISTANCE_PAROI * a.nz};
    }

    /**
     * Le point où finit le virage d'un coin sortant : {@code arête + 0,9 d − 0,3 nA}. {@code P} est à
     * {@link #DISTANCE_PAROI} de la face B (dont la normale est {@code d}), le bord arrière du cube à l'arête. Rend
     * {@code {x, z}} ; la hauteur ne change pas.
     */
    public static double[] arriveeSortant(Arete arete, Face a, Face d) {
        return new double[] {
                arete.x() + DISTANCE_PAROI * d.nx - DEMI_CUBE * a.nx,
                arete.z() + DISTANCE_PAROI * d.nz - DEMI_CUBE * a.nz};
    }

    /**
     * Le coin sortant de la face {@code a} vers la face {@code b}, pour un joueur dont {@code P} est en
     * {@code (px, py, pz)} ({@code py} : la hauteur de {@code P} elle-même). Il faut l'arête ({@link #areteSortante},
     * sens de marche = normale de {@code b}) et, depuis l'arrivée prédite ({@link #arriveeSortant}), un mur plein de
     * face {@code b} derrière le joueur : la face B du bloc d'angle. Les deux sont lus à la hauteur du cube, puis à
     * celle du torse si elle tombe dans un autre rang de blocs.
     *
     * @return l'arête du coin, ou null s'il n'y a pas de coin sortant valable
     */
    public static Arete coinSortant(LecteurBlocs blocs, double px, double py, double pz, Face a, Face b) {
        if (a == null || !a.voisine(b)) return null;
        for (double hauteur : hauteurs(py)) {
            Arete arete = areteSortante(blocs, px, hauteur, pz, a, b);
            if (arete == null) continue;
            double[] arrivee = arriveeSortant(arete, a, b);
            if (murDerriere(blocs, arrivee[0], hauteur, arrivee[1], b, PORTEE_MARCHE)) return arete;
        }
        return null;
    }

    /** Un coin sortant valable existe-t-il de la face {@code a} vers la face {@code b} ? Voir {@link #coinSortant}. */
    public static boolean coinSortantValide(LecteurBlocs blocs, double px, double py, double pz, Face a, Face b) {
        return coinSortant(blocs, px, py, pz, a, b) != null;
    }

    /** Les hauteurs à lire pour un {@code P} à cette hauteur : le cube, puis le torse s'il est dans un autre rang. */
    private static double[] hauteurs(double py) {
        double cube = py + HAUTEUR_CUBE, torse = py + HAUTEUR_TORSE;
        return Math.floor(cube) == Math.floor(torse) ? new double[] {cube} : new double[] {cube, torse};
    }

    /* ================================================================ changement de face */

    /** Ce que le serveur décide d'une demande de changement de face ({@link #jugerFace}). */
    public enum Jugement {
        /** La face demandée est déjà celle du joueur : simple accusé de réception (il annule un virage refusé). */
        MEME_FACE(null),
        /** Le mur s'arrête devant le joueur et la face voisine du bloc d'angle est pleine. */
        COIN_SORTANT(null),
        /** Le mur de la nouvelle face est déjà derrière le joueur. */
        COIN_RENTRANT(null),
        /** La face demandée est la face opposée : on ne passe d'un mur à l'autre que par un angle. */
        PAS_VOISINE("Cette face n'est pas voisine."),
        /** Ni mur de la nouvelle face derrière le joueur, ni angle à tourner devant lui. */
        PAS_DE_MUR("Pas de mur plein derrière cet angle.");

        /** Le texte du refus envoyé au joueur ; null quand la demande est acceptée. */
        public final String refus;

        Jugement(String refus) { this.refus = refus; }

        public boolean accepte() { return refus == null; }
    }

    /** Le jugement d'une demande de changement de face et, pour un coin sortant, son arête (null sinon). */
    public record Virage(Jugement jugement, Arete arete) {}

    /**
     * Juge la demande {@code face} d'un joueur au mur sur la face {@code courante}, {@code P} en {@code (px, py, pz)}
     * ({@code py} : la hauteur de {@code P} elle-même). Dans l'ordre : la face qu'il tient déjà n'est qu'un accusé ; la
     * face demandée doit être voisine ; <b>coin sortant</b> si {@link #coinSortant} trouve l'angle ; <b>coin
     * rentrant</b> si le mur de la face demandée est déjà derrière lui ({@link #murDeMarche}, {@code bascule} compris) ;
     * sinon refus. Un coin qui est les deux à la fois (ruelle étroite) est rendu sortant : son bloc d'angle est gardé
     * pour la fenêtre de virage.
     */
    public static Virage jugerFace(LecteurBlocs blocs, double px, double py, double pz,
                                   Face courante, Face demandee, boolean bascule) {
        if (courante != null && courante == demandee) return new Virage(Jugement.MEME_FACE, null);
        if (courante == null || !courante.voisine(demandee)) return new Virage(Jugement.PAS_VOISINE, null);
        Arete arete = coinSortant(blocs, px, py, pz, courante, demandee);
        if (arete != null) return new Virage(Jugement.COIN_SORTANT, arete);
        if (murDeMarche(blocs, px, py, pz, demandee, bascule)) return new Virage(Jugement.COIN_RENTRANT, null);
        return new Virage(Jugement.PAS_DE_MUR, null);
    }

    /* ================================================================ fenêtre de virage */

    /**
     * Le contrôle de paroi pendant la fenêtre de virage ({@link #TICKS_VIRAGE}) : le joueur vient de changer de face
     * et le serveur ne sait pas où en est son virage. Vrai si un mur plein est derrière lui selon la nouvelle face ou
     * l'ancienne ({@link #murDerriere}, portée {@link #PORTEE_MARCHE}), ou si le bloc d'angle du coin sortant
     * ({@code angle} : sa colonne {@code {x, z}}, null s'il n'y en a pas) est à {@link #PORTEE_ANGLE} au plus de
     * {@code P} dans le plan horizontal et garde l'une des deux faces pleine. Tout est lu à la hauteur du cube, puis à
     * celle du torse. {@code py} : la hauteur de {@code P}.
     */
    public static boolean murEnVirage(LecteurBlocs blocs, double px, double py, double pz,
                                      Face ancienne, Face nouvelle, int[] angle) {
        double[] hauteurs = hauteurs(py);
        for (double hauteur : hauteurs) {
            if (murDerriere(blocs, px, hauteur, pz, nouvelle, PORTEE_MARCHE)) return true;
        }
        if (ancienne != null) {
            for (double hauteur : hauteurs) {
                if (murDerriere(blocs, px, hauteur, pz, ancienne, PORTEE_MARCHE)) return true;
            }
        }
        if (angle == null || angle.length < 2 || distanceColonne(px, pz, angle[0], angle[1]) > PORTEE_ANGLE + EPS) return false;
        for (double hauteur : hauteurs) {
            Collection<double[]> boites = blocs.boites(angle[0], (int) Math.floor(hauteur), angle[1]);
            if (facePleine(boites, nouvelle) || (ancienne != null && facePleine(boites, ancienne))) return true;
        }
        return false;
    }

    /** Distance horizontale d'un point à la colonne de blocs {@code (bx, bz)} ; 0 s'il est dedans. */
    public static double distanceColonne(double px, double pz, int bx, int bz) {
        double dx = Math.max(0, Math.max(bx - px, px - (bx + 1)));
        double dz = Math.max(0, Math.max(bz - pz, pz - (bz + 1)));
        return Math.hypot(dx, dz);
    }

    /**
     * La face que tient un marcheur et sa fenêtre de virage : ce que le contrôle de paroi doit trouver derrière lui à
     * un tick donné de sa marche. Hors de la fenêtre, le mur de sa face ({@link #murDeMarche}) ; pendant les
     * {@link #TICKS_VIRAGE} ticks qui suivent un changement de face accepté, {@link #murEnVirage}.
     */
    public static final class Tenue {
        private Face face;
        /** La face quittée au dernier changement de face ; null tant qu'il n'y en a pas eu. */
        private Face faceAvant;
        /** Dernier tick de la marche couvert par la fenêtre de virage ; −1 : aucune fenêtre ouverte. */
        private int finVirage = -1;
        /** Colonne {@code {x, z}} du bloc d'angle du dernier coin sortant ; null pour un coin rentrant. */
        private int[] angle;

        public Tenue(Face face) { this.face = face; }

        public Face face() { return face; }

        /** La face quittée au dernier changement de face ; null s'il n'y en a pas eu. */
        public Face faceAvant() { return faceAvant; }

        /** Une accroche redemandée au mur : cette face seule, plus de fenêtre de virage. */
        public void poser(Face face) {
            this.face = face;
            faceAvant = null;
            finVirage = -1;
            angle = null;
        }

        /**
         * Un changement de face accepté au tick {@code tick} de la marche : la fenêtre de virage s'ouvre.
         * {@code arete} : celle du coin sortant, null pour un coin rentrant.
         */
        public void tourner(Face nouvelle, Arete arete, int tick) {
            faceAvant = face;
            face = nouvelle;
            finVirage = tick + TICKS_VIRAGE;
            angle = arete == null ? null : new int[] {arete.blocX(), arete.blocZ()};
        }

        /** La fenêtre de virage est-elle ouverte à ce tick de la marche ? */
        public boolean enVirage(int tick) { return tick <= finVirage; }

        /** Le contrôle de paroi à ce tick : {@code P} en {@code (px, py, pz)}, {@code bascule} comme {@link #murDeMarche}. */
        public boolean tient(LecteurBlocs blocs, double px, double py, double pz, int tick, boolean bascule) {
            if (!enVirage(tick)) return murDeMarche(blocs, px, py, pz, face, bascule);
            return murEnVirage(blocs, px, py, pz, faceAvant, face, angle);
        }
    }

    /* ================================================================ paquets refusés */

    /** Les quatre raisons d'un refus de déplacement ({@code PlayerFailMoveEvent.FailReason}). */
    public enum Raison {
        ECART("écart de position"),
        BLOC("traversée de bloc"),
        VITESSE("trop rapide"),
        CHUNK("zone non chargée");

        private final String libelle;

        Raison(String libelle) { this.libelle = libelle; }

        public String libelle() { return libelle; }

        /** La raison de ce nom du serveur ; null pour un nom inconnu (une cinquième raison ajoutée plus tard). */
        public static Raison de(String nomServeur) {
            if (nomServeur == null) return null;
            return switch (nomServeur) {
                case "MOVED_WRONGLY" -> ECART;
                case "CLIPPED_INTO_BLOCK" -> BLOC;
                case "MOVED_TOO_QUICKLY" -> VITESSE;
                case "MOVED_INTO_UNLOADED_CHUNK" -> CHUNK;
                default -> null;
            };
        }
    }

    /**
     * Compte les <b>paquets</b> de déplacement refusés, pas les raisons. Sur Paper 26.2, un paquet refusé pour
     * {@link Raison#ECART} est aussitôt refusé une seconde fois pour {@link Raison#BLOC} : deux événements, un seul
     * paquet. Deux refus appartiennent au même paquet quand ils tombent au même tick du serveur pour la même position
     * visée (après un refus, le serveur n'écoute plus de déplacement avant d'avoir replacé le joueur).
     */
    public static final class CompteurRefus {
        private int paquets;
        private final Map<Raison, Integer> parRaison = new EnumMap<>(Raison.class);
        /** Traversées de bloc tombées seules : le vrai juge, sans écart de position refusé dans le même paquet. */
        private int blocSeul;
        private boolean vu;
        private long tick;
        private double x, y, z;
        private boolean ecartDansLePaquet;

        /**
         * Note un refus de déplacement que rien n'a accepté. {@code tick} : le tick du serveur ; {@code (x, y, z)} : la
         * position visée par le paquet.
         */
        public void refuse(long tick, double x, double y, double z, Raison raison) {
            if (raison == null) return;
            boolean memePaquet = vu && this.tick == tick && this.x == x && this.y == y && this.z == z;
            if (!memePaquet) {
                paquets++;
                ecartDansLePaquet = false;
            }
            parRaison.merge(raison, 1, Integer::sum);
            if (raison == Raison.ECART) ecartDansLePaquet = true;
            if (raison == Raison.BLOC && !ecartDansLePaquet) blocSeul++;
            vu = true;
            this.tick = tick;
            this.x = x;
            this.y = y;
            this.z = z;
        }

        public int paquets() { return paquets; }

        public int raison(Raison r) { return parRaison.getOrDefault(r, 0); }

        public int blocSeul() { return blocSeul; }
    }

    /* ================================================================ cadence des messages */

    /**
     * Au plus {@code max} messages par fenêtre glissante : au-delà, le message est sans effet. Seuls y passent ceux que
     * {@link #soumisACadence} désigne.
     */
    public static final class Cadence {
        private final int max;
        private final long fenetreMs;
        private final ArrayDeque<Long> heures = new ArrayDeque<>();

        public Cadence(int max, long fenetreMs) {
            this.max = max;
            this.fenetreMs = fenetreMs;
        }

        public boolean passe(long maintenantMs) {
            while (!heures.isEmpty() && maintenantMs - heures.peekFirst() >= fenetreMs) heures.pollFirst();
            if (heures.size() >= max) return false;
            heures.addLast(maintenantMs);
            return true;
        }
    }

    /**
     * Cette demande passe-t-elle par la {@link Cadence} ? Seules y passent celles qui peuvent créer un état : une
     * accroche, ou une face nouvelle. Un {@code lache} ne fait que rendre le corps, et un retour à la face courante ou à
     * la face quittée ({@code avant}) annule un virage : les jeter laisserait le serveur et le mod en désaccord, rien ne
     * les recale ensuite. {@code courante} : la face tenue par le serveur, null si le joueur n'est pas au mur.
     */
    public static boolean soumisACadence(Demande d, Face courante, Face avant) {
        if (d.action() == Action.LACHE) return false;
        if (d.action() == Action.FACE && courante != null && (d.face() == courante || d.face() == avant)) return false;
        return true;
    }

    /* ================================================================ bilan */

    /**
     * Les chiffres de l'essai d'un membre du staff, de {@code /murtest raz} (ou de sa première commande) jusqu'au bilan.
     * Ils survivent à sa déconnexion : une expulsion se lit après s'être reconnecté.
     */
    public static final class Compteurs {
        public int accroches, accrochesRefusees;
        /** Dernière raison d'un refus d'accroche. */
        public String dernierRefus;
        /** Changements de face (coins) acceptés et refusés ; un refus laisse le joueur sur sa face. */
        public int faces, facesRefusees;
        /** Dernière raison d'un refus de changement de face. */
        public String dernierRefusFace;
        /** Lâchers décidés par le serveur (plus de mur, état refusé, KO, téléportation…), et la dernière raison. */
        public int lachersServeur;
        public String dernierLacher;
        /** Expulsions du serveur (« kick ») reçues au mur, et la dernière cause. */
        public int expulsions;
        public String derniereExpulsion;
        /** Paquets refusés au mur (et pendant {@link #SORTIE_TICKS} après), puis hors du mur : le témoin. */
        public final CompteurRefus refusMur = new CompteurRefus(), refusHorsMur = new CompteurRefus();
        /** Écarts de position acceptés par le filet. */
        public int filet;
        /** Corrections de position annoncées par le mod dans ses {@code lache}. */
        public int corrections;
        /** Lâchers du serveur dont le mod n'a pas renvoyé le compte de corrections. */
        public int sansCompte;
        /** Le serveur a lâché : le {@code lache} du mod qui suit ne vaut que pour son compte de corrections. */
        public boolean attendCompte;
        public int coupsMelee, projectiles, degats;
        /** Clics d'attaque reçus par le testeur hors du mur (tombé en pleine série) : à part, hors de l'essai 5. */
        public int coupsHorsMur;
        /** Pose de nage reposée par le serveur (quelque chose l'avait enlevée). */
        public int poseReposee;
        /** Ticks où le vol était autorisé au mur : à zéro, sinon l'essai 1 ne prouve rien. */
        public int volAutorise;

        private long tempsMurMs;
        private boolean enMarche;
        private long debutMarcheMs;
        private long finMarcheTick = Long.MIN_VALUE / 2;

        public void debutMarche(long maintenantMs) {
            accroches++;
            reprendreMarche(maintenantMs);
        }

        public void finMarche(long maintenantMs, long tick) {
            tempsMurMs = tempsMurMs(maintenantMs);
            enMarche = false;
            finMarcheTick = tick;
        }

        /** Une remise à zéro en pleine marche : le temps repart de maintenant, la marche continue. */
        public void reprendreMarche(long maintenantMs) {
            enMarche = true;
            debutMarcheMs = maintenantMs;
        }

        /** Temps passé au mur, marche en cours comprise. */
        public long tempsMurMs(long maintenantMs) {
            return tempsMurMs + (enMarche ? Math.max(0, maintenantMs - debutMarcheMs) : 0);
        }

        /** Le compteur d'un refus tombé à ce tick : au mur (et juste après le lâcher), ou hors du mur. */
        public CompteurRefus refus(boolean auMur, long tick) {
            return auMur || tick - finMarcheTick <= SORTIE_TICKS ? refusMur : refusHorsMur;
        }

        /**
         * Le bilan, une phrase courte et un chiffre par ligne. {@code face} : la face du mur où le joueur se tient en ce
         * moment, null s'il n'y est pas.
         */
        public List<String> lignes(boolean permis, boolean filetAllume, Face face, long maintenantMs) {
            long temps = tempsMurMs(maintenantMs);
            List<String> l = new ArrayList<>(32);
            l.add("Permis : " + (permis ? "allumé" : "éteint"));
            l.add("Filet : " + (filetAllume ? "allumé" : "éteint"));
            l.add("Au mur en ce moment : " + (face == null ? "non" : "oui, face " + face.name()));
            l.add("Accroches acceptées : " + accroches);
            l.add("Accroches refusées : " + accrochesRefusees);
            if (dernierRefus != null) l.add("Dernier refus : " + dernierRefus);
            l.add("Changements de face acceptés : " + faces);
            l.add("Changements de face refusés : " + facesRefusees);
            if (dernierRefusFace != null) l.add("Dernier refus de face : " + dernierRefusFace);
            l.add("Temps passé au mur : " + duree(temps));
            l.add("Expulsions du serveur pendant le mur : " + expulsions);
            if (derniereExpulsion != null) l.add("Dernière expulsion : " + derniereExpulsion);
            l.add("Lâchers décidés par le serveur : " + lachersServeur);
            if (dernierLacher != null) l.add("Dernier lâcher du serveur : " + dernierLacher);
            l.add("Paquets de déplacement refusés au mur : " + refusMur.paquets());
            for (Raison r : Raison.values()) l.add("  raison « " + r.libelle() + " » : " + refusMur.raison(r));
            l.add("  " + Raison.BLOC.libelle() + " seule, sans écart : " + refusMur.blocSeul());
            l.add("  (un même paquet peut porter deux raisons)");
            l.add("  (le mur compte jusqu'à 2 s après le lâcher)");
            l.add("Paquets acceptés par le filet : " + filet);
            l.add("Paquets refusés hors du mur, pour comparer : " + refusHorsMur.paquets());
            l.add("Corrections de position vues par le mod : " + corrections);
            if (enMarche) l.add("  (marche en cours non comptée, lâche le mur pour l'avoir)");
            l.add("Corrections par minute de mur : " + parMinute(corrections, enMarche ? tempsMurMs : temps));
            l.add("Lâchers du serveur sans compte du mod : " + sansCompte);
            l.add("Coups au corps à corps comptés par le serveur : " + coupsMelee);
            l.add("Essai 5, part sur 30 clics : " + Math.round(coupsMelee * 100.0 / 30.0) + " %");
            l.add("Clics reçus hors du mur, non comptés à l'essai 5 : " + coupsHorsMur);
            l.add("Projectiles qui ont touché : " + projectiles);
            l.add("Dégâts subis au mur, techniques comprises : " + degats);
            l.add("Pose de nage reposée par le serveur : " + poseReposee);
            l.add("Ticks avec le vol autorisé au mur : " + volAutorise);
            l.add("  (ces deux derniers chiffres doivent rester à zéro)");
            return l;
        }
    }

    /** {@code 0 s}, {@code 47 s}, {@code 2 min 04 s}. */
    public static String duree(long ms) {
        long s = Math.max(0, ms) / 1000;
        return s < 60 ? s + " s" : String.format(Locale.ROOT, "%d min %02d s", s / 60, s % 60);
    }

    /** Un nombre d'événements ramené à la minute de mur, une décimale, virgule française ; pas de taux avant 10 s de mur. */
    public static String parMinute(int nombre, long tempsMs) {
        if (tempsMs < 10_000) return "trop tôt pour le dire";
        return String.format(Locale.FRENCH, "%.1f", nombre * 60_000.0 / tempsMs);
    }
}
