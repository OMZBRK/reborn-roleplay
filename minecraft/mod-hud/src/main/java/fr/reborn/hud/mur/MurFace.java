package fr.reborn.hud.mur;

/**
 * Face d'une paroi verticale (marche sur les murs, lot 5, essai C0) : la direction de sa <b>normale</b> {@code n}, cardinale
 * et horizontale, qui pointe du mur vers l'air. {@code E} : le mur est à l'ouest du joueur, son corps part vers l'est.
 * Lettres du canal {@code reborn:mur} ({@code N}, {@code S}, {@code E}, {@code W}).
 *
 * <p>{@code t} est la tangente horizontale de la paroi, {@code haut × n} : la droite de qui regarde le mur. {@code lacet}
 * est le lacet (degrés du jeu) de qui regarde le mur de face, c'est-à-dire vers {@code −n}.
 */
public enum MurFace {

    N("N", 0, -1, 0f),
    S("S", 0, 1, 180f),
    E("E", 1, 0, 90f),
    W("W", -1, 0, -90f);

    /** Lettre du protocole. */
    public final String code;
    /** Normale (du mur vers l'air). */
    public final int nx, nz;
    /** Tangente horizontale : la droite de qui regarde le mur. */
    public final int tx, tz;
    /** Lacet de qui regarde le mur de face. */
    public final float lacet;

    MurFace(String code, int nx, int nz, float lacet) {
        this.code = code;
        this.nx = nx;
        this.nz = nz;
        this.tx = nz;
        this.tz = -nx;
        this.lacet = lacet;
    }

    /** La face d'une lettre du protocole ; {@code null} pour « - » (plus au mur) ou une lettre inconnue. */
    public static MurFace de(String code) {
        if (code == null) return null;
        return switch (code) {
            case "N" -> N;
            case "S" -> S;
            case "E" -> E;
            case "W" -> W;
            default -> null;
        };
    }

    /** Coordonnée d'un point sur l'axe de la normale. */
    public double surNormale(double x, double z) { return nx != 0 ? x : z; }

    /** Coordonnée d'un point sur l'axe horizontal de la paroi. */
    public double surParoi(double x, double z) { return nx != 0 ? z : x; }

    /** Sens de la normale sur son axe : +1 ou −1. */
    public int sens() { return nx + nz; }

    /** Sens de la tangente sur l'axe horizontal de la paroi : +1 ou −1. */
    public int sensTangente() { return tx + tz; }
}
