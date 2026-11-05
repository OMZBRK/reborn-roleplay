package fr.reborn.hud.menu.settings;

import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Textures des Paramètres Reborn ({@code textures/gui/reglages/*}, générées par {@code tools/gen_reglages_textures.py} ;
 * tailles et ancres = contrat en tête du générateur). 1 texel = 1 GUI ; le texte est posé par le code ({@link DaKit}).
 * Partagé par l'écran ({@code menu/screens/ConfigShellScreen}), les onglets et leurs contrôles.
 */
public final class ReglagesArt {

    private ReglagesArt() {}

    static Identifier tex(String n) { return DaKit.tex("reglages", n); }

    public static final Identifier FOND = tex("fond"), ENTETE = tex("entete"), ONGLETS = tex("onglets"),
        FERMER = tex("fermer"), PLANCHE = tex("planche"), REGLETTE = tex("reglette"), INTERRUPTEUR = tex("interrupteur"),
        SEGMENTS = tex("segments"), BOUTONS = tex("boutons"), PIECES = tex("pieces"), VIGNETTE = tex("vignette"),
        ONGLETS_PICTOS = tex("onglets_pictos");

    public static final int FOND_W = 1280, FOND_H = 720;

    /** Pièce de laque et byōbu, centrée, calée en haut. */
    public static void fond(GuiGraphicsExtractor g, int w, int h) {
        g.fill(0, 0, w, h, 0xFF0A0608);
        // Écran logique plus grand que la pièce (1366 × 768 en « Petite », écrans larges) : pièce agrandie pour couvrir
        float k = Math.max(w / (float) FOND_W, h / (float) FOND_H);
        if (k <= 1f) {
            DaKit.blit(g, FOND, w / 2 - FOND_W / 2, 0, 0, 0, FOND_W, FOND_H, FOND_W, FOND_H);
            return;
        }
        g.pose().pushMatrix();
        g.pose().translate(w / 2f, 0);
        g.pose().scale(k, k);
        DaKit.blit(g, FOND, -FOND_W / 2, 0, 0, 0, FOND_W, FOND_H, FOND_W, FOND_H);
        g.pose().popMatrix();
    }

    /** En-tête suspendu (plaque 520 de large), centré en cx. */
    public static void entete(GuiGraphicsExtractor g, int cx) {
        DaKit.blit(g, ENTETE, cx - 270, 0, 0, 0, 540, 48, 540, 48);
    }

    /** Onglet pendu (cellule 26×36, attache en (tx, y)) : 0 repos, 1 survol, 2 actif. */
    public static void onglet(GuiGraphicsExtractor g, int tx, int y, int etat) {
        DaKit.blit(g, ONGLETS, tx - 13, y, etat * 26, 0, 26, 36, 78, 36);
    }

    /** Picto 16×16 de l'onglet {@code i} (0 vidéo … 7 Minecraft), état 0 repos, 1 survol, 2 actif ; centré en (tx, haut y). */
    public static void ongletPicto(GuiGraphicsExtractor g, int i, int tx, int y, int etat) {
        DaKit.blit(g, ONGLETS_PICTOS, tx - 8, y, (i * 3 + etat) * 16, 0, 16, 16, 384, 16);
    }

    /** Plaque de fermeture à croix (cellule 26×38, attache en (tx, 0)). */
    public static void fermer(GuiGraphicsExtractor g, int tx, boolean survol) {
        DaKit.blit(g, FERMER, tx - 13, 0, survol ? 26 : 0, 0, 26, 38, 52, 38);
    }

    /** Planche laquée (coupe) : boîte (x, y, w, h) ≤ 616×648. */
    public static void planche(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        DaKit.coupe(g, PLANCHE, x - 2, y - 2, w + 8, h + 8, 0, 0, 624, 656, 18, 18, 624, 656);
    }

    /** Réglette d'or : rainure (x, y, w×10), remplissage v ∈ [0, 1], curseur (survolé ou non). */
    public static void reglette(GuiGraphicsExtractor g, int x, int y, int w, float v, boolean hot) {
        DaKit.coupeH(g, REGLETTE, x, y, w, 0, 0, 304, 10, 6, 304, 30);
        v = Math.max(0, Math.min(1, v));
        int fw = Math.round((w - 4) * v);
        DaKit.blit(g, REGLETTE, x + 2, y + 3, 2, 12, Math.min(300, fw), 4, 304, 30);
        DaKit.blit(g, REGLETTE, x + 2 + fw - 3, y - 1, hot ? 8 : 0, 18, 7, 12, 304, 30);
    }

    /** Interrupteur laqué 40×14 en (x, y). */
    public static void interrupteur(GuiGraphicsExtractor g, int x, int y, boolean marche, boolean survol) {
        int r = (marche ? 1 : 0) + (survol ? 2 : 0);
        DaKit.blit(g, INTERRUPTEUR, x - 1, y - 1, 0, r * 16, 44, 16, 44, 64);
    }

    /** Plaque segmentée (16 de haut, largeur w) : 0 repos, 1 survol, 2 actif. */
    public static void segment(GuiGraphicsExtractor g, int x, int y, int w, int etat) {
        DaKit.coupeH(g, SEGMENTS, x - 1, y - 1, w + 3, 0, etat * 20, 160, 20, 6, 160, 60);
    }

    /** Bouton-plaque (20 de haut, largeur w ≤ 596) : 0 normal, 1 survol, 2 inactif, 3 rouge. */
    public static void bouton(GuiGraphicsExtractor g, int x, int y, int w, int etat) {
        DaKit.coupeH(g, BOUTONS, x - 1, y - 1, w + 3, 0, etat * 24, 600, 24, 6, 600, 96);
    }

    /** Flèche d'or 5×7 (bouton d'action). */
    public static void fleche(GuiGraphicsExtractor g, int x, int y, float a) {
        DaKit.blit(g, PIECES, x, y, 0, 20, 5, 7, 120, 40, DaKit.blanc(a));
    }

    /** Filet d'or à losanges de x0 à x1 (y = ligne du filet). */
    public static void filet(GuiGraphicsExtractor g, int x0, int x1, int y) {
        if (x1 - x0 < 8) return;
        g.fill(x0 + 3, y, x1 - 3, y + 1, 0xFF6A4E24);
        DaKit.blit(g, PIECES, x0, y - 1, 1, 14, 3, 3, 120, 40);
        DaKit.blit(g, PIECES, x1 - 3, y - 1, 1, 14, 3, 3, 120, 40);
    }

    public static void rail(GuiGraphicsExtractor g, int x, int y, int h) {
        for (int yy = y; yy < y + h; yy += 40) DaKit.blit(g, PIECES, x, yy, 100, 0, 4, Math.min(40, y + h - yy), 120, 40);
    }

    public static void curseur(GuiGraphicsExtractor g, int x, int y, int h) {
        DaKit.blit(g, PIECES, x, y, 108, 0, 6, 2, 120, 40);
        for (int yy = y + 2; yy < y + h - 2; yy += 12) DaKit.blit(g, PIECES, x, yy, 108, 2, 6, Math.min(12, y + h - 2 - yy), 120, 40);
        DaKit.blit(g, PIECES, x, y + h - 2, 108, 14, 6, 2, 120, 40);
    }

    /* ================================================================ lot 3 (gabarit o2 des Paramètres) */

    /** Ombre de l'ArcadePix ×1 ({@link DaKit#arcX1}). */
    private static final int OMBRE_ARC = 0xFF0A0406;

    /**
     * ArcadePix ×1 <b>avec ses accents</b> (« PARAMÈTRES », « GÉNÉRAL », « CAMÉRA ») : ArcadePix n'a que l'ASCII, la
     * lettre de base est écrite par {@link DaKit#arcX1} et l'accent est dessiné au pixel au-dessus (même dessin que les
     * maquettes o2 : aigu, grave, circonflexe, tréma sur deux rangées d'1 GUI, une rangée libre au-dessus de la
     * capitale ; cédille sous la ligne). Haut des capitales en y, ancre 0 gauche, 1 centre, 2 droite. Positions des
     * accents calculées une fois par texte (mémo) : rien d'alloué ensuite hors du composant de texte.
     */
    public static int titre(GuiGraphicsExtractor g, Font f, String texte, float x, float y, int col, int ancre,
                            boolean ombre) {
        int w = DaKit.arcX1(g, f, DaKit.arcade(texte), x, y, col, ancre, ombre);
        int[] acc = accents(f, texte);
        if (acc.length == 0) return w;
        int x0 = Math.round(ancre == 1 ? x - w / 2f : ancre == 2 ? x - w : x);
        int yy = Math.round(y);
        if (ombre) poserAccents(g, acc, x0 + 1, yy + 1, OMBRE_ARC);
        poserAccents(g, acc, x0, yy, col);
        return w;
    }

    private static final Map<String, int[]> ACCENTS = new ConcurrentHashMap<>();
    private static final int[] SANS = new int[0];
    private static final int AIGU = 0, GRAVE = 1, CIRC = 2, TREMA = 3;

    /** Paires (avance de la lettre en GUI, type d'accent) du texte mis en capitales. */
    private static int[] accents(Font f, String texte) {
        if (texte == null) return SANS;
        int[] r = ACCENTS.get(texte);
        if (r != null) return r;
        if (ACCENTS.size() > 512) ACCENTS.clear();
        String up = Normalizer.normalize(texte.toUpperCase(Locale.ROOT), Normalizer.Form.NFC);
        int[] tmp = new int[up.length() * 2];
        int n = 0;
        for (int i = 0; i < up.length(); i++) {
            String nfd = Normalizer.normalize(up.substring(i, i + 1), Normalizer.Form.NFD);
            int type = nfd.indexOf('́') >= 0 ? AIGU : nfd.indexOf('̀') >= 0 ? GRAVE
                : nfd.indexOf('̂') >= 0 ? CIRC : nfd.indexOf('̈') >= 0 ? TREMA
                : nfd.indexOf('̧') >= 0 ? 4 : -1;
            if (type < 0) continue;
            tmp[n++] = f.width(DaKit.arcade(up.substring(0, i)));
            tmp[n++] = type;
        }
        r = n == 0 ? SANS : Arrays.copyOf(tmp, n);
        ACCENTS.put(texte, r);
        return r;
    }

    /** Accents au pixel (rangées du masque des maquettes : 0-1 au-dessus de la capitale, 10-11 sous la ligne). */
    private static void poserAccents(GuiGraphicsExtractor g, int[] acc, int x0, int y, int col) {
        for (int i = 0; i < acc.length; i += 2) {
            int c = x0 + acc[i] + 3;          // milieu de l'encre d'une capitale de 7
            int h0 = y - 3, h1 = y - 2;
            switch (acc[i + 1]) {
                case AIGU -> {
                    g.fill(c, h0, c + 2, h0 + 1, col);
                    g.fill(c - 1, h1, c + 1, h1 + 1, col);
                }
                case GRAVE -> {
                    g.fill(c - 1, h0, c + 1, h0 + 1, col);
                    g.fill(c, h1, c + 2, h1 + 1, col);
                }
                case CIRC -> {
                    g.fill(c - 1, h0, c + 2, h0 + 1, col);
                    g.fill(c - 2, h1, c, h1 + 1, col);
                    g.fill(c + 1, h1, c + 3, h1 + 1, col);
                }
                case TREMA -> {
                    g.fill(c - 2, h1, c - 1, h1 + 1, col);
                    g.fill(c + 2, h1, c + 3, h1 + 1, col);
                }
                default -> {                  // cédille
                    g.fill(c, y + 7, c + 2, y + 8, col);
                    g.fill(c - 1, y + 8, c + 1, y + 9, col);
                }
            }
        }
    }

    /** Chevron d'or de 5×3 (liste déroulante), pointe en bas ; (x, y) = milieu du haut. */
    public static void chevron(GuiGraphicsExtractor g, int x, int y, int col) {
        for (int i = 0; i < 3; i++) g.fill(x - 2 + i, y + i, x + 3 - i, y + i + 1, col);
    }

    /** Segments (y, x0, x1, …, -1) de l'icône « ↺ » pour un côté en pixels écran, recalculés seulement à un changement
     *  d'échelle. */
    private static int[] reinitRuns = SANS;
    private static int reinitCote = -1;

    /**
     * Icône « ↺ » de « Réinitialiser la section » (maquette o2 : arc de 40° à 330°, pointe de flèche en haut à droite),
     * ~5,5 GUI de côté, dessinée en pixels écran par segments horizontaux (RebornFont n'a pas les flèches). (x, yc) =
     * bord gauche et milieu, en GUI.
     */
    public static void iconeReinit(GuiGraphicsExtractor g, float x, float yc, int col) {
        int s = DaKit.echelleGui();
        int n = Math.max(5, Math.round(5.5f * s));
        if (n != reinitCote) {
            reinitRuns = runsReinit(n, s);
            reinitCote = n;
        }
        int[] r = reinitRuns;
        DaKit.debutPx(g);
        int px = Math.round(x * s), py = Math.round(yc * s - n / 2f);
        for (int i = 0; i + 2 < r.length && r[i] >= 0; i += 3) {
            g.fill(px + r[i + 1], py + r[i], px + r[i + 2], py + r[i] + 1, col);
        }
        DaKit.finPx(g);
    }

    private static int[] runsReinit(int n, int s) {
        boolean[][] m = new boolean[n][n];
        double c = (n - 1) / 2.0, ro = n / 2.0 - 0.35, ep = Math.max(1.0, 0.9 * s);
        for (int y = 0; y < n; y++) {
            for (int xx = 0; xx < n; xx++) {
                double dx = xx - c, dy = y - c, d = Math.sqrt(dx * dx + dy * dy);
                double a = Math.toDegrees(Math.atan2(dy, dx));
                if (a < 0) a += 360;              // 0 = est, sens horaire (y vers le bas), comme Pillow
                if (d <= ro && d >= ro - ep && a >= 40 && a <= 330) m[y][xx] = true;
            }
        }
        // pointe de flèche : triangle (0,60 n ; 0) (n ; 0,08 n) (0,80 n ; 0,46 n)
        double[][] t = {{0.60 * n, 0}, {n, 0.08 * n}, {0.80 * n, 0.46 * n}};
        for (int y = 0; y < n; y++) {
            for (int xx = 0; xx < n; xx++) {
                if (dansTriangle(xx + 0.5, y + 0.5, t)) m[y][xx] = true;
            }
        }
        int[] out = new int[n * n * 3 + 1];
        int k = 0;
        for (int y = 0; y < n; y++) {
            int xx = 0;
            while (xx < n) {
                if (!m[y][xx]) {
                    xx++;
                    continue;
                }
                int a = xx;
                while (xx < n && m[y][xx]) xx++;
                out[k++] = y;
                out[k++] = a;
                out[k++] = xx;
            }
        }
        out[k] = -1;
        return Arrays.copyOf(out, k + 1);
    }

    private static boolean dansTriangle(double px, double py, double[][] t) {
        double d1 = signe(px, py, t[0], t[1]), d2 = signe(px, py, t[1], t[2]), d3 = signe(px, py, t[2], t[0]);
        boolean neg = d1 < 0 || d2 < 0 || d3 < 0, pos = d1 > 0 || d2 > 0 || d3 > 0;
        return !(neg && pos);
    }

    private static double signe(double px, double py, double[] a, double[] b) {
        return (px - b[0]) * (a[1] - b[1]) - (a[0] - b[0]) * (py - b[1]);
    }
}
