package fr.reborn.hud.menu.esc;

import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.LotBlit;
import fr.reborn.hud.ui.LotGui;
import fr.reborn.hud.ui.TexteCache;
import fr.reborn.hud.ui.TexteRaster;
import fr.reborn.hud.ui.TexteRaster.Ancre;
import fr.reborn.hud.ui.TexteRaster.Police;
import fr.reborn.hud.ui.TexteRaster.Preset;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Kit de l'Échap e5 (Game Plan n° 17, lot 3, maquette validée le 05/11 : {@code e5_echap_final.png}, script
 * {@code _maquettes_e5_final.py}) : textures, couleurs, styles de texte et formes. Tout se dessine en <b>pixels
 * écran</b> ({@link DaKit#debutPx}) : la colonne de gauche suit la hauteur de la fenêtre (k = H / 1080, comme
 * MenuScale), la grille de droite est du pixel art à P pixels écran par pixel d'art (P entier, 3 en 1080p, 2 en 768p,
 * 4 en 1440p), son texte à kk = P / 3. L'Inter ne descend jamais sous {@value #PLANCHER} px de capitale (lisible en
 * 1366 × 768).
 *
 * <p>Textures ({@code textures/gui/echap/e5_*}, tirées des fonctions mêmes de la maquette par
 * {@code gen_echap_e5.py}, script du lot 3 hors dépôt) : la façade de la Boutique (332 × 156 pixels d'art, trois
 * états), les chōchin (repos, allumé, éteint), la bande laquée du survol, le pictogramme Stream, l'ombre, le halo et
 * la lueur en 9 tranches, les emblèmes des villages en masque, le logo réduit d'avance. Le reste (cadres, cartes,
 * jauge, interrupteurs, cases) est en aplats.
 */
final class EchapArt {

    private EchapArt() {}

    static Identifier tex(String n) { return DaKit.tex("echap", n); }

    static final Identifier VITRINE = tex("e5_vitrine"), CHOCHIN = tex("e5_chochin"), BANDE = tex("e5_bande"),
        PICTOS = tex("e5_pictos"), DOUCES = tex("e5_douces"), EMBLEMES = tex("e5_emblemes"), LOGO = tex("e5_logo"),
        OMAMORI = tex("omamori"), RESEAUX = tex("reseaux"), RBCOIN = DaKit.tex("esc", "rbcoin"),
        PARCHEMIN = DaKit.tex("shop", "scroll");

    /* ---- cotes de la façade (pixels d'art, gen_echap_e5.py) ---- */
    static final int VIT_W = 332, VIT_H = 156, VIT_OX0 = 30, VIT_OX1 = 172, ENS_X = 204, ENS_Y = 56, ENS_W = 120,
        ENS_H = 34;
    /* ---- grille de droite (pixels d'art) : 1 002 × 756 px en 1080p ---- */
    static final int GRILLE_W = 334, BOUTIQUE_H = 158, GOUTTIERE = 6, RANG_H = 88, RECOMP_W = 234, TUILE_H = 26,
        TUILE_PAS = 31;
    /* ---- logo réduit d'avance (2160p, 1440p, 1080p, 768p) : largeurs, hauteurs et v des rangées de l'atlas ---- */
    static final int[] LOGO_W = {448, 299, 224, 160}, LOGO_H = {157, 105, 78, 56}, LOGO_V = {0, 157, 262, 340};
    static final int LOGO_TW = 448, LOGO_TH = 396;

    /** Rangée de l'atlas du logo la plus proche d'une largeur voulue. */
    static int rangLogo(int w) {
        int best = 0;
        for (int i = 1; i < LOGO_W.length; i++) if (Math.abs(LOGO_W[i] - w) < Math.abs(LOGO_W[best] - w)) best = i;
        return best;
    }

    /* ---- couleurs (RGB) ---- */
    static final int CREME = 0xF5E9D0, REPOS = 0xCFC3AE, DOUX = 0xC2B59A, GRIS = 0x8A8070, HUD = 0xF3F0EA,
        OR = 0xD9A95E, OR_CLAIR = 0xF2D08A, OR_ETEINT = 0x8E6A30, BLANC = 0xFDFBF6, VERT = 0x63C97C,
        ROUGE_HOV = 0xC82A2E, LAQUE_C = 0x4A1418, LAQUE_HOV = 0x6A1C20, ENCRE_C = 0x070506, ENCRE = 0x0E0A0C,
        NUIT_H = 0x1C1519, NUIT_H_HOV = 0x261C22, NUIT_B = 0x100C0F, CASE_FOND = 0x2A2627, CASE_LISERE = 0x5A5254,
        DIRECT = 0xFF5A4E;

    /** Plancher de l'Inter (px de capitale). */
    static final double PLANCHER = 8.6;

    /** Préréglage des menus (maquettes du lot 3) : sans contour, ombre 40 %. */
    static final Preset MENU = new Preset("MENU", 0f, 0.40f, 0.85f);

    /* ---- styles de texte (constants : rien n'est alloué à chaque image) ---- */
    private static TexteCache.Style st(Police p, Preset pr, int rgb, Ancre a) {
        return new TexteCache.Style(p, pr, TexteRaster.cleRgb(rgb), a, true, false);
    }

    static final Police PHILO = Police.PHILOSOPHER, IB = Police.INTER_GRAS, IM = Police.INTER_MEDIUM;
    static final Ancre G = Ancre.GAUCHE, C = Ancre.CENTRE, D = Ancre.DROITE;

    static final TexteCache.Style
        P_CREME = st(PHILO, MENU, CREME, G), P_REPOS = st(PHILO, MENU, REPOS, G), P_OR = st(PHILO, MENU, OR_CLAIR, G),
        P_BLANC = st(PHILO, MENU, BLANC, G), P_OR_D = st(PHILO, MENU, OR, D), P_BLANC_D = st(PHILO, MENU, BLANC, D),
        I_DOUX = st(IM, Preset.HUD, DOUX, G), I_GRIS_D = st(IM, MENU, GRIS, D), I_CREME = st(IM, Preset.HUD, CREME, G),
        I_GRIS = st(IM, Preset.HUD, GRIS, G), I_HUD_C = st(IM, Preset.HUD, HUD, C),
        B_DOUX = st(IB, MENU, DOUX, G), B_OR = st(IB, Preset.HUD, OR_CLAIR, G), B_OR_D = st(IB, Preset.HUD, OR_CLAIR, D),
        B_CREME_C = st(IB, Preset.HUD, CREME, C), B_HUD_C = st(IB, Preset.HUD, HUD, C), B_DOUX_C = st(IB, Preset.HUD, DOUX, C),
        B_VERT = st(IB, Preset.HUD, VERT, G), B_GRIS = st(IB, Preset.HUD, GRIS, G), B_CREME_D = st(IB, Preset.HUD, CREME, D);

    /** Capitale Inter à l'échelle s, jamais sous le plancher. */
    static double capInter(double cap, double s) { return Math.max(cap * s, PLANCHER); }

    static int r(double v) { return TexteRaster.arrondi(v); }

    /* ================================================================ libellé : poignée de texte + largeur mesurée */

    /** Une ligne de texte affichée (poignée {@link TexteCache.Texte}) et sa largeur, mesurée quand elle change. */
    static final class Lib {
        final TexteCache.Texte t = new TexteCache.Texte();
        private String mT;
        private TexteCache.Style mS;
        private double mCap, mInter, mW = Double.NaN;

        /** Largeur d'avance (px) ; 0 tant que la police n'est pas chargée. */
        double largeur(String s, TexteCache.Style st, double cap, double inter) {
            if (s.equals(mT) && st == mS && cap == mCap && inter == mInter && !Double.isNaN(mW)) return mW;
            mT = s;
            mS = st;
            mCap = cap;
            mInter = inter;
            mW = TexteRaster.largeur(s, st.police(), cap, inter, st.typo());
            return Double.isNaN(mW) ? 0 : mW;
        }

        /** Pose la ligne (x selon l'ancre du style, yc = milieu des capitales), en pixels écran. */
        void ligne(GuiGraphicsExtractor ctx, String s, TexteCache.Style st, double cap, double f, double inter, double x,
                   double yc, float a) {
            t.ligne(s, st, cap, f, inter, x, yc).dessinerPx(ctx, a);
        }
    }

    /* ================================================================ aplats */

    static int argb(int rgb, float a) { return DaKit.argb(rgb, a); }

    static void rect(LotGui l, int x0, int y0, int x1, int y1, int rgb, float a) {
        if (a > 0.003f) l.fill(x0, y0, x1, y1, argb(rgb, a));
    }

    /**
     * Dégradé horizontal (gauche → droite) : l'aplat en dégradé vertical de Minecraft, tourné d'un quart de tour.
     */
    static void degradeH(GuiGraphicsExtractor ctx, int x0, int y0, int x1, int y1, int gauche, int droite) {
        if (x1 <= x0 || y1 <= y0) return;
        ctx.pose().pushMatrix();
        ctx.pose().translate(x0, y1);
        ctx.pose().rotate((float) (-Math.PI / 2));
        ctx.fillGradient(0, 0, y1 - y0, x1 - x0, gauche, droite);
        ctx.pose().popMatrix();
    }

    /** Filet effacé aux deux bouts (fondu de la boussole : plein sur la moitié du milieu). */
    static void filetFondu(GuiGraphicsExtractor ctx, int x, int y, int L, int h, int rgb, float a) {
        if (a <= 0.003f || L <= 4) return;
        int q = L / 4;
        degradeH(ctx, x, y, x + q, y + h, argb(rgb, 0), argb(rgb, a));
        ctx.fill(x + q, y, x + L - q, y + h, argb(rgb, a));
        degradeH(ctx, x + L - q, y, x + L, y + h, argb(rgb, a), argb(rgb, 0));
    }

    /** Pilule (bouts ronds) h de haut, rangée par rangée. */
    static void pilule(LotGui l, int x, int y, int w, int h, int rgb, float a) {
        if (a <= 0.003f) return;
        int col = argb(rgb, a);
        double rr = h / 2.0;
        for (int j = 0; j < h; j++) {
            double dy = j + 0.5 - rr;
            int in = (int) Math.round(rr - Math.sqrt(Math.max(0, rr * rr - dy * dy)));
            l.fill(x + in, y + j, x + w - in, y + j + 1, col);
        }
    }

    /** Disque plein de rayon r centré en (cx, cy). */
    static void disque(LotGui l, int cx, int cy, int rayon, int rgb, float a) {
        if (a <= 0.003f) return;
        int col = argb(rgb, a);
        for (int j = -rayon; j < rayon; j++) {
            double dy = j + 0.5;
            int hw = (int) Math.round(Math.sqrt(Math.max(0, rayon * rayon - dy * dy)));
            if (hw > 0) l.fill(cx - hw, cy + j, cx + hw, cy + j + 1, col);
        }
    }

    /**
     * Cadre de carte en pixel art (cadre_art de la piste A) à P px par pixel d'art : contour d'encre aux coins coupés,
     * anneau laqué, filet d'or intérieur, équerres d'or de 6 pixels aux quatre coins.
     */
    static void cadre(LotGui l, int x, int y, int w, int h, int P, boolean survol, float a) {
        int aw = w / P, ah = h / P;
        // contour d'encre (coins coupés)
        rect(l, x + P, y, x + (aw - 1) * P, y + P, ENCRE_C, a);
        rect(l, x + P, y + (ah - 1) * P, x + (aw - 1) * P, y + ah * P, ENCRE_C, a);
        rect(l, x, y + P, x + P, y + (ah - 1) * P, ENCRE_C, a);
        rect(l, x + (aw - 1) * P, y + P, x + aw * P, y + (ah - 1) * P, ENCRE_C, a);
        // anneau laqué (retrait 1) et filet d'or (retrait 2)
        anneau(l, x, y, aw, ah, P, 1, survol ? LAQUE_HOV : LAQUE_C, a);
        anneau(l, x, y, aw, ah, P, 2, survol ? OR : OR_ETEINT, a * (survol ? 0.85f : 0.40f));
        // équerres
        int eq = survol ? OR_CLAIR : OR;
        int L = 6;
        for (int c = 0; c < 4; c++) {
            boolean droite = (c & 1) != 0, bas = (c & 2) != 0;
            int cx = droite ? aw - 2 : 1, cy = bas ? ah - 2 : 1;
            int hx0 = droite ? cx - L + 1 : cx, vy0 = bas ? cy - L + 1 : cy;
            rect(l, x + hx0 * P, y + cy * P, x + (hx0 + L) * P, y + (cy + 1) * P, eq, a);
            rect(l, x + cx * P, y + vy0 * P, x + (cx + 1) * P, y + (vy0 + L) * P, eq, a);
            int px = cx + (droite ? -1 : 1), py = cy + (bas ? -1 : 1);
            rect(l, x + px * P, y + py * P, x + (px + 1) * P, y + (py + 1) * P, OR_CLAIR, a);
        }
    }

    private static void anneau(LotGui l, int x, int y, int aw, int ah, int P, int i, int rgb, float a) {
        int x0 = x + i * P, y0 = y + i * P, x1 = x + (aw - i) * P, y1 = y + (ah - i) * P;
        rect(l, x0, y0, x1, y0 + P, rgb, a);
        rect(l, x0, y1 - P, x1, y1, rgb, a);
        rect(l, x0, y0 + P, x0 + P, y1 - P, rgb, a);
        rect(l, x1 - P, y0 + P, x1, y1 - P, rgb, a);
    }

    /** Case de touche du lot 2 (coins rognés de 2 px, liseré en haut, ombre d'1 px) : fond à 92 %. */
    static void caseTouche(LotGui l, int x, int y, int w, int h, float a) {
        int fond = argb(CASE_FOND, 0.92f * a), ombre = argb(ENCRE, 0.6f * a), lis = argb(CASE_LISERE, 0.9f * a);
        l.fill(x + 3, y + h, x + w + 1, y + h + 1, ombre);
        l.fill(x + w, y + 3, x + w + 1, y + h, ombre);
        l.fill(x + 2, y, x + w - 2, y + 1, lis);
        l.fill(x + 1, y + 1, x + w - 1, y + 2, fond);
        l.fill(x + 1, y + 1, x + 2, y + 2, lis);
        l.fill(x + w - 2, y + 1, x + w - 1, y + 2, lis);
        l.fill(x, y + 2, x + w, y + h - 2, fond);
        l.fill(x + 1, y + h - 2, x + w - 1, y + h - 1, fond);
        l.fill(x + 2, y + h - 1, x + w - 2, y + h, fond);
    }

    /* ================================================================ textures */

    /** Région (u, v, uw, vh) d'une texture posée à la taille (w, h) (pixel art : multiples entiers). */
    static void blit(GuiGraphicsExtractor ctx, Identifier id, int x, int y, int w, int h, float u, float v, int uw, int vh,
                     int tw, int th, float a) {
        if (w <= 0 || h <= 0 || a <= 0.003f) return;
        ctx.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, uw, vh, tw, th, DaKit.blanc(a));
    }

    static void blitTeint(GuiGraphicsExtractor ctx, Identifier id, int x, int y, int w, int h, float u, float v, int uw,
                          int vh, int tw, int th, int argb) {
        if (w <= 0 || h <= 0 || (argb >>> 24) == 0) return;
        ctx.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, uw, vh, tw, th, argb);
    }

    /** Masques doux (9 tranches de {@code e5_douces}) : ombre portée, halo d'or, lueur. */
    static final int OMBRE = 0, HALO = 128, LUEUR = 256;

    /**
     * Ombre ou halo d'une carte (x, y, w, h) : le masque de 128 texels (bord de la carte à 32 texels) agrandi de s,
     * en 9 tranches (coins de 64 texels, bords étirés).
     */
    static void doux(GuiGraphicsExtractor ctx, int u0, int x, int y, int w, int h, double s, int argb) {
        if ((argb >>> 24) == 0) return;
        int pad = r(32 * s), c = r(64 * s);
        int X = x - pad, Y = y - pad, W = w + 2 * pad, H = h + 2 * pad;
        if (W < 2 * c || H < 2 * c) c = Math.min(W, H) / 2;
        int mw = W - 2 * c, mh = H - 2 * c;
        LotBlit l = LotBlit.ouvrir(ctx, DOUCES, 384, 128);
        l.etire(X, Y, c, c, u0, 0, 64, 64, argb);
        l.etire(X + W - c, Y, c, c, u0 + 64, 0, 64, 64, argb);
        l.etire(X, Y + H - c, c, c, u0, 64, 64, 64, argb);
        l.etire(X + W - c, Y + H - c, c, c, u0 + 64, 64, 64, 64, argb);
        if (mw > 0) {
            l.etire(X + c, Y, mw, c, u0 + 63, 0, 2, 64, argb);
            l.etire(X + c, Y + H - c, mw, c, u0 + 63, 64, 2, 64, argb);
        }
        if (mh > 0) {
            l.etire(X, Y + c, c, mh, u0, 63, 64, 2, argb);
            l.etire(X + W - c, Y + c, c, mh, u0 + 64, 63, 64, 2, argb);
        }
        if (mw > 0 && mh > 0) l.etire(X + c, Y + c, mw, mh, u0 + 63, 63, 2, 2, argb);
        l.fermer();
    }

    /** Lueur ronde de rayon rayon (px) centrée en (cx, cy). */
    static void lueur(GuiGraphicsExtractor ctx, int cx, int cy, int rayon, int argb) {
        blitTeint(ctx, DOUCES, cx - rayon, cy - rayon, 2 * rayon, 2 * rayon, LUEUR, 0, 128, 128, 384, 128, argb);
    }
}
