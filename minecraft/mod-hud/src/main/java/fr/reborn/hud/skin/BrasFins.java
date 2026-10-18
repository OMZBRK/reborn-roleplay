package fr.reborn.hud.skin;

import com.mojang.blaze3d.platform.NativeImage;

/**
 * Bras fins (carrure Alex, 3 px) à la volée — 18/10. Tous les personnages composés sont en carrure fine ; un calque
 * peint pour des bras de 4 px (asset live ajouté par le staff, ancien gabarit) est converti au chargement, avec la règle
 * exacte du script {@code tools/bras_fins.py} : sur chaque bras et sa manche, une colonne du milieu (1 ou 2 vue de face,
 * celle qui fait le moins perdre, la même pour le bras et la manche, la colonne miroir à l'arrière) est retirée des faces
 * avant, dessus, dessous et arrière, et les faces sont recollées au gabarit fin. Un calque déjà fin n'est pas touché,
 * ni un dessin fin dont la zone inutilisée est bouchée d'une couleur unie ({@link #remplissage}).
 * Le masque RGBA d'un asset reçoit la même coupe ({@link #plan} puis {@link #convertir}).
 */
public final class BrasFins {

    private BrasFins() {}

    /** Bras droit, manche droite, bras gauche, manche gauche (coin du bloc 16×16 de chaque membre). */
    private static final int[][] BRAS = {{40, 16}, {40, 32}, {32, 48}, {48, 48}};
    /** Colonne retirée à égalité de perte (côté corps) : bras droit 2, bras gauche 1. */
    private static final int[] INTERIEUR = {2, 1};

    /**
     * Plan de coupe d'un calque peint en bras larges ({@code {droit, gauche}}, colonne 1 ou 2), ou {@code null} si le
     * calque est déjà fin (ou n'a pas de bras).
     */
    public static int[] plan(NativeImage im) {
        if (im == null || im.getWidth() != 64 || im.getHeight() != 64 || !large(im)) return null;
        int[] cols = new int[2];
        for (int p = 0; p < 2; p++) {
            long p1 = perte(im, p, 1), p2 = perte(im, p, 2);
            cols[p] = p1 == p2 ? INTERIEUR[p] : p1 < p2 ? 1 : 2;
        }
        return cols;
    }

    /** Convertit en place si le calque est peint en bras larges ; renvoie le plan appliqué ({@code null} = rien). */
    public static int[] versFin(NativeImage im) {
        int[] cols = plan(im);
        if (cols != null) convertir(im, cols);
        return cols;
    }

    private static boolean opaque(NativeImage im, int x, int y) { return (im.getPixel(x, y) >>> 24) != 0; }

    private static boolean large(NativeImage im) {
        for (int[] b : BRAS) {
            int a = 0, c = 0;
            for (int y = 4; y < 16; y++) {
                if (opaque(im, b[0] + 14, b[1] + y)) a++;
                if (opaque(im, b[0] + 15, b[1] + y)) a++;
                if (opaque(im, b[0] + 12, b[1] + y)) c++;
                if (opaque(im, b[0] + 13, b[1] + y)) c++;
            }
            if (a >= 8 && a * 5 >= c * 3 && !remplissage(im, b[0], b[1])) return true;
        }
        return false;
    }

    /**
     * Dessin fin dont la zone inutilisée (colonnes 14-15, et 10-11 en haut) est bouchée d'une seule couleur (souvent la
     * peau, habitude des skins Alex « élargis » : Complet_Ceremonie2) : c'est un dessin en 3 px, il ne faut pas le couper.
     */
    private static boolean remplissage(NativeImage im, int bx, int by) {
        int ref = im.getPixel(bx + 14, by + 4);
        if ((ref >>> 24) == 0) return false;
        for (int y = 4; y < 16; y++) {
            if (im.getPixel(bx + 14, by + y) != ref || im.getPixel(bx + 15, by + y) != ref) return false;
        }
        for (int y = 0; y < 4; y++) {
            if (im.getPixel(bx + 10, by + y) != ref || im.getPixel(bx + 11, by + y) != ref) return false;
        }
        return true;
    }

    private static int ecart(int p, int q) {
        int ap = p >>> 24, aq = q >>> 24;
        if (ap == 0 && aq == 0) return 0;
        if (ap == 0 || aq == 0) return 1000;
        int d = Math.abs(ap - aq);
        for (int s = 0; s <= 16; s += 8) d += Math.abs(((p >> s) & 0xFF) - ((q >> s) & 0xFF));
        return d;
    }

    private static long perte(NativeImage im, int paire, int c) {
        long tot = 0;
        for (int b = paire * 2; b < paire * 2 + 2; b++) {
            int bx = BRAS[b][0], by = BRAS[b][1];
            // dessus, dessous, avant, arrière (miroir)
            int[][] faces = {{bx + 4, by, 4, 0}, {bx + 8, by, 4, 0}, {bx + 4, by + 4, 12, 0}, {bx + 12, by + 4, 12, 1}};
            for (int[] f : faces) {
                int k = f[3] == 1 ? 3 - c : c;
                for (int y = f[1]; y < f[1] + f[2]; y++) {
                    int p = im.getPixel(f[0] + k, y);
                    tot += Math.min(ecart(p, im.getPixel(f[0] + k - 1, y)), ecart(p, im.getPixel(f[0] + k + 1, y)));
                }
            }
        }
        return tot;
    }

    /** Applique le plan {@code {droit, gauche}} (colonne retirée vue de face) à un calque ou à son masque. */
    public static void convertir(NativeImage im, int[] cols) {
        if (im == null || cols == null || im.getWidth() != 64 || im.getHeight() != 64) return;
        for (int b = 0; b < 4; b++) {
            int bx = BRAS[b][0], by = BRAS[b][1], c = cols[b / 2];
            int[] s = new int[256];
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) s[y * 16 + x] = im.getPixel(bx + x, by + y);
            int[] d = new int[256];
            int[] garde = new int[3], gardeDos = new int[3];
            for (int k = 0, j = 0, jd = 0; k < 4; k++) {
                if (k != c) garde[j++] = k;
                if (k != 3 - c) gardeDos[jd++] = k;
            }
            for (int y = 0; y < 4; y++) {
                for (int j = 0; j < 3; j++) {
                    d[y * 16 + 4 + j] = s[y * 16 + 4 + garde[j]];       // dessus
                    d[y * 16 + 7 + j] = s[y * 16 + 8 + garde[j]];       // dessous
                }
            }
            for (int y = 4; y < 16; y++) {
                for (int x = 0; x < 4; x++) {
                    d[y * 16 + x] = s[y * 16 + x];                      // côté extérieur
                    d[y * 16 + 7 + x] = s[y * 16 + 8 + x];              // côté intérieur
                }
                for (int j = 0; j < 3; j++) {
                    d[y * 16 + 4 + j] = s[y * 16 + 4 + garde[j]];       // avant
                    d[y * 16 + 11 + j] = s[y * 16 + 12 + gardeDos[j]];  // arrière
                }
            }
            for (int y = 0; y < 16; y++) for (int x = 0; x < 16; x++) im.setPixel(bx + x, by + y, d[y * 16 + x]);
        }
    }
}
