package fr.reborn.hud.corde;

/**
 * Qui est ligoté, côté client (prototype du 26/11, aucun réseau) : identifiants d'entités et variante. <b>Vide en
 * production</b> : seul le banc {@code SteleCordeDebug} la remplit. Plus tard, un canal du serveur la remplira (état
 * « ligoté » diffusé aux joueurs proches).
 *
 * <p>Tableaux fixes de {@value #CAPACITE} places, lus à chaque image par {@link CordeLayer} : une recherche linéaire,
 * aucune allocation ; une liste vide ne coûte qu'un entier lu.
 */
public final class CordeLigotes {

    public static final int CAPACITE = 16;
    /** Aucune corde. */
    public static final byte AUCUNE = 0;
    /** Bras et torse liés, jambes libres (le ligoté peut marcher). */
    public static final byte HAUT = 1;
    /** Bras, torse, genoux et chevilles liés. */
    public static final byte COMPLET = 2;

    static final int[] ID = new int[CAPACITE];
    static final byte[] VARIANTE = new byte[CAPACITE];
    static int n;

    private CordeLigotes() {}

    /** Variante portée par l'entité {@code id} ({@link #AUCUNE} si elle n'est pas ligotée). */
    public static byte variante(int id) {
        for (int i = 0; i < n; i++) if (ID[i] == id) return VARIANTE[i];
        return AUCUNE;
    }

    public static boolean vide() { return n == 0; }

    /** Ligote (ou change la variante de) l'entité {@code id} ; {@link #AUCUNE} la libère. */
    public static void poser(int id, byte variante) {
        if (variante == AUCUNE) { retirer(id); return; }
        for (int i = 0; i < n; i++) {
            if (ID[i] == id) { VARIANTE[i] = variante; return; }
        }
        if (n >= CAPACITE) return;
        ID[n] = id;
        VARIANTE[n] = variante;
        n++;
    }

    public static void retirer(int id) {
        for (int i = 0; i < n; i++) {
            if (ID[i] != id) continue;
            n--;
            ID[i] = ID[n];
            VARIANTE[i] = VARIANTE[n];
            return;
        }
    }

    public static void vider() { n = 0; }
}
