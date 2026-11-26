package fr.reborn.hud.mur;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;

/**
 * <b>Géométrie des passages</b> de la marche sur les murs (lot 5, étape C1a, cahier §2) : coin rentrant, coin sortant,
 * sommet. Classe pure, sans monde ni entité : les blocs sont lus par un {@link LecteurBlocs}.
 *
 * <p>Le serveur juge les mêmes passages avec ShinobiAbilities {@code MurRegles} : les fonctions du coin sortant
 * ({@link #blocsDerriere}, {@link #facePleine}, {@link #areteSortante}, {@link #departSortant}, {@link #arriveeSortant},
 * {@link #coinSortant}) en sont la copie exacte, pour que le mod ne demande jamais un virage que le serveur refuserait
 * et que les deux côtés tombent sur la même arête et la même arrivée.
 *
 * <p>Notations du cahier : {@code P} bas-centre du cube du serveur, à {@value #DISTANCE_PAROI} de la surface ; {@code n}
 * normale de la paroi ({@link MurFace}) ; {@code d} le sens de marche le long de la paroi.
 */
public final class MurTransitions {

    /** Marche : la surface du mur est à cette distance au plus derrière {@code P}. */
    public static final double PORTEE_MARCHE = 1.5;
    /** Hauteur lue au-dessus de {@code P} : le milieu du cube du serveur. */
    public static final double HAUTEUR_CUBE = 0.3;
    /** Hauteur du torse d'un joueur debout. */
    public static final double HAUTEUR_TORSE = 0.9;
    /** Au mur, {@code P} se tient à cette distance de la surface. */
    public static final double DISTANCE_PAROI = 0.9;
    /** Demi-largeur du cube du serveur. */
    public static final double DEMI_CUBE = 0.3;
    /** Coin sortant : la fin du mur est cherchée à cette distance au plus devant {@code P}. */
    public static final double PORTEE_ARETE = 1.5;
    /** Rayon de l'arc du coin sortant : de {@code P} au centre de l'arc, à 0,3 dans le bloc d'angle. */
    public static final double RAYON = DISTANCE_PAROI + DEMI_CUBE;
    /** Longueur de l'arc du coin sortant (quart de cercle). */
    public static final double ARC = RAYON * Math.PI / 2.0;
    /** Durée du coin sortant (ticks) : l'arc à l'allure de la marche, borné. */
    public static final int SORTANT_MIN = 6, SORTANT_MAX = 10;
    /** Durée du coin rentrant (ticks). */
    public static final int RENTRANT = 4;
    /** Sommet : on monte de 0,6 en 2 ticks, puis on avance de 1,2 en 4 ticks. */
    public static final int SOMMET_MONTEE = 2, SOMMET_AVANCE = 4;
    public static final double SOMMET_HAUT = 0.6, SOMMET_RECUL = 0.3;

    private static final double EPS = 1.0e-6;

    private MurTransitions() {}

    /** Lecture du monde : les boîtes de collision d'un bloc, dans son repère (0 à 1), {@code {minX, minY, minZ, maxX, maxY, maxZ}}. */
    @FunctionalInterface
    public interface LecteurBlocs {
        Collection<double[]> boites(int x, int y, int z);
    }

    /** L'arête verticale où le mur de la face A s'arrête, et la colonne du bloc d'angle (comme {@code MurRegles.Arete}). */
    public record Arete(double x, double z, int blocX, int blocZ) {}

    /* ================================================================ contrôle de paroi (copie de MurRegles) */

    /** Un bloc à face pleine (tournée vers {@code n}) est-il à {@code portee} au plus derrière le point, le long de {@code −n} ? */
    public static boolean murDerriere(LecteurBlocs blocs, double px, double py, double pz, MurFace face, double portee) {
        int y = (int) Math.floor(py);
        for (int[] b : blocsDerriere(px, pz, face, portee)) {
            if (facePleine(blocs.boites(b[0], y, b[1]), face)) return true;
        }
        return false;
    }

    /**
     * Comme {@link #murDerriere}, mais rend la coordonnée de la surface trouvée sur l'axe de la normale (un entier), ou
     * {@code NaN} s'il n'y a pas de mur plein à portée.
     */
    public static double surfaceDerriere(LecteurBlocs blocs, double px, double py, double pz, MurFace face, double portee) {
        int y = (int) Math.floor(py);
        boolean surX = face.nx != 0;
        for (int[] b : blocsDerriere(px, pz, face, portee)) {
            if (!facePleine(blocs.boites(b[0], y, b[1]), face)) continue;
            int bloc = surX ? b[0] : b[1];
            return face.sens() > 0 ? bloc + 1 : bloc;
        }
        return Double.NaN;
    }

    /** Les colonnes {@code {x, z}} dont la face tournée vers {@code n} est entre 0 et {@code portee} derrière le point. */
    public static List<int[]> blocsDerriere(double px, double pz, MurFace face, double portee) {
        boolean surX = face.nx != 0;
        double a = surX ? px : pz;
        int autre = (int) Math.floor(surX ? pz : px);
        List<int[]> out = new ArrayList<>(2);
        if (face.nx + face.nz > 0) {
            for (int b = (int) Math.floor(a - 1 + EPS); a - (b + 1) <= portee + EPS; b--) out.add(colonne(surX, b, autre));
        } else {
            for (int b = (int) Math.ceil(a - EPS); b - a <= portee + EPS; b++) out.add(colonne(surX, b, autre));
        }
        return out;
    }

    private static int[] colonne(boolean surX, int b, int autre) {
        return surX ? new int[] {b, autre} : new int[] {autre, b};
    }

    /**
     * La face du bloc tournée vers {@code n} est-elle pleine ? Les boîtes qui touchent ce bord doivent couvrir tout le
     * carré (cube plein, deux dalles empilées, dos d'escalier : oui ; dalle seule, barrière, bloc en retrait : non).
     */
    public static boolean facePleine(Collection<double[]> boites, MurFace face) {
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

    /* ================================================================ coin sortant (copie de MurRegles) */

    /**
     * La fin du mur de la face {@code a} devant le point, dans le sens de marche {@code d} (une face voisine de
     * {@code a}) : null si {@code d} n'est pas voisine, s'il n'y a pas de mur plein derrière le point, ou si le mur
     * continue au-delà de {@link #PORTEE_ARETE}.
     */
    public static Arete areteSortante(LecteurBlocs blocs, double px, double py, double pz, MurFace a, MurFace d) {
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

    /** Départ du virage d'un coin sortant : {@code arête − 0,3 d + 0,9 nA}, {@code {x, z}}. */
    public static double[] departSortant(Arete arete, MurFace a, MurFace d) {
        return new double[] {
            arete.x() - DEMI_CUBE * d.nx + DISTANCE_PAROI * a.nx,
            arete.z() - DEMI_CUBE * d.nz + DISTANCE_PAROI * a.nz};
    }

    /** Arrivée du virage d'un coin sortant : {@code arête + 0,9 d − 0,3 nA}, {@code {x, z}}. */
    public static double[] arriveeSortant(Arete arete, MurFace a, MurFace d) {
        return new double[] {
            arete.x() + DISTANCE_PAROI * d.nx - DEMI_CUBE * a.nx,
            arete.z() + DISTANCE_PAROI * d.nz - DEMI_CUBE * a.nz};
    }

    /**
     * Le coin sortant de la face {@code a} vers la face {@code b} pour un {@code P} en {@code (px, py, pz)} : l'arête
     * (sens de marche = normale de {@code b}) et, depuis l'arrivée prédite, un mur plein de face {@code b} derrière le
     * joueur ; lus à la hauteur du cube, puis à celle du torse si elle tombe dans un autre rang. Null sinon.
     */
    public static Arete coinSortant(LecteurBlocs blocs, double px, double py, double pz, MurFace a, MurFace b) {
        if (a == null || !a.voisine(b)) return null;
        for (double hauteur : hauteurs(py)) {
            Arete arete = areteSortante(blocs, px, hauteur, pz, a, b);
            if (arete == null) continue;
            double[] arrivee = arriveeSortant(arete, a, b);
            if (murDerriere(blocs, arrivee[0], hauteur, arrivee[1], b, PORTEE_MARCHE)) return arete;
        }
        return null;
    }

    private static double[] hauteurs(double py) {
        double cube = py + HAUTEUR_CUBE, torse = py + HAUTEUR_TORSE;
        return Math.floor(cube) == Math.floor(torse) ? new double[] {cube} : new double[] {cube, torse};
    }

    /**
     * Un point de l'arc du coin sortant, {@code s} de 0 (départ) à 1 (arrivée) : quart de cercle de rayon
     * {@value #RAYON} centré à {@code arête − 0,3 d − 0,3 nA}, de {@code nA} vers {@code d}. {@code hors} reçoit
     * {@code {x, z}}.
     */
    public static void pointSortant(Arete arete, MurFace a, MurFace d, double s, double[] hors) {
        double cx = arete.x() - DEMI_CUBE * d.nx - DEMI_CUBE * a.nx, cz = arete.z() - DEMI_CUBE * d.nz - DEMI_CUBE * a.nz;
        double angle = s * Math.PI / 2.0, ca = Math.cos(angle), sa = Math.sin(angle);
        hors[0] = cx + RAYON * (ca * a.nx + sa * d.nx);
        hors[1] = cz + RAYON * (ca * a.nz + sa * d.nz);
    }

    /** Durée du coin sortant à cette allure (blocs par tick) : l'arc parcouru à l'allure de la marche, de 6 à 10 ticks. */
    public static int ticksSortant(double pasParTick) {
        int n = pasParTick > 0 ? (int) Math.ceil(ARC / pasParTick - EPS) : SORTANT_MAX;
        return Math.max(SORTANT_MIN, Math.min(SORTANT_MAX, n));
    }

    /* ================================================================ coin rentrant */

    /**
     * Le coin rentrant : l'arête est le point du plan horizontal où se coupent la surface de A ({@code planA}, sur l'axe
     * de {@code nA}) et celle de B ({@code planB}, sur l'axe de {@code nB}). Départ : {@code coin + 0,9 nA + 0,3 nB} ;
     * {@code hors} reçoit {@code {x, z}}.
     */
    public static void departRentrant(double planA, MurFace a, double planB, MurFace b, double[] hors) {
        point(planA, a, DISTANCE_PAROI, planB, b, DEMI_CUBE, hors);
    }

    /** Arrivée du coin rentrant : {@code coin + 0,3 nA + 0,9 nB}, même hauteur ; {@code hors} reçoit {@code {x, z}}. */
    public static void arriveeRentrant(double planA, MurFace a, double planB, MurFace b, double[] hors) {
        point(planA, a, DEMI_CUBE, planB, b, DISTANCE_PAROI, hors);
    }

    private static void point(double planA, MurFace a, double da, double planB, MurFace b, double db, double[] hors) {
        double surA = planA + a.sens() * da, surB = planB + b.sens() * db;
        if (a.nx != 0) {
            hors[0] = surA;
            hors[1] = surB;
        } else {
            hors[0] = surB;
            hors[1] = surA;
        }
    }

    /* ================================================================ sommet */

    /**
     * Le chemin de la sortie par le sommet, pas {@code i} (de 1 à {@value #SOMMET_MONTEE} + {@value #SOMMET_AVANCE}) :
     * d'abord monter de {@value #SOMMET_HAUT} le long de la paroi, puis avancer sur le toit jusqu'à
     * {@value #SOMMET_RECUL} derrière l'arête. {@code distance} : la distance de {@code P} à la surface au départ (0,9),
     * {@code sommet} : la hauteur du toit. {@code hors} reçoit la distance à la surface le long de {@code n} (négative
     * au-dessus du mur) et la hauteur.
     */
    public static void pasSommet(int i, double distance, double sommet, double[] hors) {
        if (i <= SOMMET_MONTEE) {
            hors[0] = distance;
            hors[1] = sommet - SOMMET_HAUT + SOMMET_HAUT * i / SOMMET_MONTEE;
        } else {
            hors[0] = distance - (distance + SOMMET_RECUL) * (i - SOMMET_MONTEE) / SOMMET_AVANCE;
            hors[1] = sommet;
        }
    }
}
