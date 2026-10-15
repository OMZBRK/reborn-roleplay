package fr.reborn.hud.ui;

import fr.reborn.hud.menu.RebornFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Kit de rendu commun des écrans « au niveau de la fiche shinobi » (DA §4.1, §6) hors du paquet de la fiche :
 * blits à échelle entière (1 texel = 1 GUI) de textures générées par les scripts {@code tools/gen_*_textures.py},
 * coupes (une planche plus petite que sa texture : le haut et la gauche, puis le bord droit et le bas recollés), et
 * texte au pixel (ArcadePix ×1 et ×0,5, Inter 9 / 8 / 7, étiquettes en petites capitales, kanji 16 GUI).
 *
 * <p>Même typographie que {@code menu/fiche/FicheArt} (dont il reprend les règles), sans en dépendre.
 */
public final class DaKit {

    private DaKit() {}

    /* ================================================================ palette (fiche) */

    public static final int OR = 0xFFD9A95E, OR_CLAIR = 0xFFF2D08A, OR_MOYEN = 0xFFC8A05A, OR_ETEINT = 0xFF8E6A30,
        OR_ETIQ = 0xFFA8844A;
    public static final int CREME = 0xFFF5E9D0, CREME_VAL = 0xFFE8DCC0, CREME_KJ = 0xFFF2E6CC, DOUX = 0xFFC2B59A,
        ROUGE = 0xFFA0182B, ROUGE_CLAIR = 0xFFE26474;
    public static final int SEP = 0xFF2D181C, INACTIF = 0xFF6A5C52, NEUTRE_TXT = 0xFFB8A47E;
    public static final int ENCRE = 0xFF2A1A10, ENCRE_DOUCE = 0xFF6B5434, ENCRE_ROUGE = 0xFF8E1628;
    public static final int[] R_OR = {0xFF2E2210, 0xFF4A3518, 0xFF6A4E24, 0xFF8E6A30, 0xFFB08442, 0xFFD9A95E, 0xFFE8C27A,
        0xFFF2D59A, 0xFFFFF2C8};

    public static Identifier tex(String dossier, String nom) {
        return Identifier.fromNamespaceAndPath("reborn", "textures/gui/" + dossier + "/" + nom + ".png");
    }

    /* ================================================================ blits */

    public static void blit(GuiGraphicsExtractor ctx, Identifier id, int x, int y, float u, float v, int w, int h,
                            int tw, int th) {
        if (w <= 0 || h <= 0) return;
        ctx.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, tw, th);
    }

    public static void blit(GuiGraphicsExtractor ctx, Identifier id, int x, int y, float u, float v, int w, int h,
                            int tw, int th, int argb) {
        if (w <= 0 || h <= 0 || (argb >>> 24) == 0) return;
        ctx.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, tw, th, argb);
    }

    /** Étirement (réservé aux textures lisses : vignette, voile). */
    public static void etire(GuiGraphicsExtractor ctx, Identifier id, int x, int y, int w, int h, int tw, int th, int argb) {
        if (w <= 0 || h <= 0 || (argb >>> 24) == 0) return;
        ctx.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, w, h, tw, th, tw, th, argb);
    }

    /**
     * Pièce de la région (u0, v0, W, H) d'un atlas posée en (x, y) à la taille (w, h) ≤ (W, H), par coupe : le coin
     * haut gauche (w − capD) × (h − capB), puis la bande droite (capD), la bande basse (capB) et le coin bas droit pris
     * aux bords de la région. Les bandes de coupe du générateur sont sans ornement : la jointure ne se voit pas.
     */
    public static void coupe(GuiGraphicsExtractor ctx, Identifier id, int x, int y, int w, int h, int u0, int v0,
                             int W, int H, int capD, int capB, int tw, int th) {
        w = Math.min(w, W);
        h = Math.min(h, H);
        int lw = w - capD, lh = h - capB;
        LotBlit l = LotBlit.ouvrir(ctx, id, tw, th);   // les 4 morceaux = un seul élément GUI
        l.blit(x, y, u0, v0, lw, lh);
        l.blit(x + lw, y, u0 + W - capD, v0, capD, lh);
        l.blit(x, y + lh, u0, v0 + H - capB, lw, capB);
        l.blit(x + lw, y + lh, u0 + W - capD, v0 + H - capB, capD, capB);
        l.fermer();
    }

    /** Coupe horizontale seule (bouton, réglette, bandeau) : hauteur pleine. */
    public static void coupeH(GuiGraphicsExtractor ctx, Identifier id, int x, int y, int w, int u0, int v0, int W, int h,
                              int capD, int tw, int th) {
        w = Math.min(w, W);
        LotBlit l = LotBlit.ouvrir(ctx, id, tw, th);
        l.blit(x, y, u0, v0, w - capD, h);
        l.blit(x + w - capD, y, u0 + W - capD, v0, capD, h);
        l.fermer();
    }

    /* ================================================================ couleurs */

    public static int argb(int rgb, float a) {
        int al = Math.round(255 * Math.max(0f, Math.min(1f, a)));
        return al << 24 | (rgb & 0xFFFFFF);
    }

    /** Multiplie l'alpha d'une couleur ARGB. */
    public static int alpha(int argb, float f) {
        int al = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, f)));
        return al << 24 | (argb & 0xFFFFFF);
    }

    /** Blanc teinté par l'alpha seul (pour moduler un blit). */
    public static int blanc(float a) { return argb(0xFFFFFF, a); }

    /* ================================================================ texte */

    public static final Style INTER_GRAS = Style.EMPTY.withFont(new FontDescription.Resource(RebornFont.BOLD));

    public static MutableComponent arcade(String s) { return RebornFont.arcade(sansAccents(s)); }

    public static MutableComponent inter(String s) { return RebornFont.body(s == null ? "" : s); }

    public static MutableComponent interGras(String s) { return Component.literal(s == null ? "" : s).setStyle(INTER_GRAS); }

    /** Majuscules sans accents (ArcadePix n'a ni accents ni minuscules). */
    public static String sansAccents(String s) {
        if (s == null) return "";
        String r = SANS_ACCENTS.get(s);
        if (r == null) {
            if (SANS_ACCENTS.size() > 4096) SANS_ACCENTS.clear();
            r = sansAccentsCalcul(s);
            SANS_ACCENTS.put(s, r);
        }
        return r;
    }

    /** Mémo de {@link #sansAccents} (Normalizer + regex appelés à chaque image coûtaient). */
    private static final java.util.Map<String, String> SANS_ACCENTS = new java.util.concurrent.ConcurrentHashMap<>();

    private static String sansAccentsCalcul(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
            .replace('—', '-').replace('’', '\'').replace('…', '.').toUpperCase(Locale.ROOT);
    }

    /** Pixels écran par unité de dessin courante (échelle GUI, ou celle imposée par {@link #unites}). */
    public static int echelleGui() {
        return unite > 0 ? unite : Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
    }

    private static int unite;

    /**
     * Côté en pixels écran d'une icône pixel art de {@code base} px visée à {@code voulu} px : le multiple entier de la
     * base le plus proche s'il tombe à 15 % près (net), sinon la taille visée telle quelle. Avant le 15/10, on prenait
     * le multiple inférieur : à l'échelle 0,95 une icône de 32 px tombait d'un coup à 16 px.
     */
    public static int cotePx(float voulu, int base) {
        int k = Math.max(1, Math.round(voulu / base));
        if (Math.abs(k * base - voulu) <= 0.15f * voulu) return k * base;
        return Math.max(4, Math.round(voulu));
    }

    /**
     * Dessine en « unités menu » : 1 unité = {@code k} pixels écran (pose mise à l'échelle k / échelle GUI). Le texte
     * se cale alors sur cette grille. À refermer par {@link #finUnites}.
     */
    public static void unites(GuiGraphicsExtractor ctx, int k) {
        int gs = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        ctx.pose().pushMatrix();
        ctx.pose().scale(k / (float) gs, k / (float) gs);
        unite = k;
    }

    public static void finUnites(GuiGraphicsExtractor ctx) {
        ctx.pose().popMatrix();
        unite = 0;
    }

    /** Facteur d'ArcadePix ×0,5 : net à toutes les échelles (2/3 à l'échelle 3). */
    public static float facteurPetit() {
        int k = echelleGui();
        if (k == 1) return 0.75f;
        return Math.max(1, Math.round(k / 2f)) / (float) k;
    }

    public static float pxEcran() { return 1f / echelleGui(); }

    /**
     * Échelle « menu » (celle de {@link MenuScale} pour un écran Reborn : la taille de l'échelle 2 en 1080p) pour ce qui
     * est dessiné par-dessus le HUD (menu d'interaction, concentration) : la même taille à l'écran quelle que soit
     * l'échelle d'interface du joueur. Entier : 1 texel = {@code echelleMenu()} pixels écran.
     */
    public static int echelleMenu() {
        var w = Minecraft.getInstance().getWindow();
        int best = 1;
        double gap = Double.MAX_VALUE;
        for (int k = 1; k <= 8; k++) {
            int lw = w.getWidth() / k, lh = w.getHeight() / k;
            if (k > 1 && (lw < MenuScale.MIN_W || lh < MenuScale.MIN_H)) break;
            double d = Math.abs(lh - MenuScale.TARGET_H);
            if (d < gap) { gap = d; best = k; }
        }
        return best;
    }

    /** Cale une coordonnée GUI sur la grille des pixels écran (texte net). */
    public static float cale(float v) {
        int k = echelleGui();
        return Math.round(v * k) / (float) k;
    }

    public static void texteEchelle(GuiGraphicsExtractor ctx, Font f, Component c, float x, float y, int col, float s) {
        ctx.pose().pushMatrix();
        ctx.pose().translate(cale(x), cale(y));
        ctx.pose().scale(s, s);
        ctx.text(f, c, 0, 0, col, false);
        ctx.pose().popMatrix();
    }

    public static void texteEchelle(GuiGraphicsExtractor ctx, Font f, FormattedCharSequence c, float x, float y, int col,
                                    float s) {
        ctx.pose().pushMatrix();
        ctx.pose().translate(cale(x), cale(y));
        ctx.pose().scale(s, s);
        ctx.text(f, c, 0, 0, col, false);
        ctx.pose().popMatrix();
    }

    private static int ombreDe(int col) {
        return (col & 0xFF000000) | (((col >> 16) & 0xFF) / 4 << 16) | (((col >> 8) & 0xFF) / 4 << 8) | ((col & 0xFF) / 4);
    }

    /** ArcadePix ×1 (capitales de 7 GUI), haut des capitales en y. ancre : 0 gauche, 1 centre, 2 droite. */
    public static int arcX1(GuiGraphicsExtractor ctx, Font f, Component c, float x, float y, int col, int ancre,
                            boolean ombre) {
        int w = f.width(c) - 1;
        int x0 = Math.round(ancre == 1 ? x - w / 2f : ancre == 2 ? x - w : x);
        int yy = Math.round(y);
        if (ombre) ctx.text(f, c, x0 + 1, yy + 1, 0xFF000000 | 0x0A0406, false);
        ctx.text(f, c, x0, yy, col, false);
        return w;
    }

    public static float largeurFin(Font f, Component c) { return f.width(c) * facteurPetit(); }

    /** ArcadePix ×0,5 : haut des capitales en yHaut ; ombre d'1 px écran. Renvoie la largeur GUI. */
    public static float arcFin(GuiGraphicsExtractor ctx, Font f, Component c, float x, float yHaut, int col, int ancre,
                               boolean ombre) {
        float s = facteurPetit(), px = pxEcran();
        float w = f.width(c) * s;
        float x0 = ancre == 1 ? x - w / 2f : ancre == 2 ? x - w : x;
        if (ombre) texteEchelle(ctx, f, c, x0 + px, yHaut + px, ombreDe(col), s);
        texteEchelle(ctx, f, c, x0, yHaut, col, s);
        return w;
    }

    public static final float INTER9_MIL = 4f, INTER7_MIL = 3f;
    public static final float S7 = 7f / 9f, S8 = 8f / 9f;

    /** Inter 9, milieu des capitales en ymil. Renvoie la largeur GUI. */
    public static int inter9(GuiGraphicsExtractor ctx, Font f, Component c, float x, float ymil, int col, int ancre) {
        int w = f.width(c);
        float x0 = ancre == 1 ? x - w / 2f : ancre == 2 ? x - w : x;
        texteEchelle(ctx, f, c, x0, ymil - INTER9_MIL, col, 1f);
        return w;
    }

    /** Inter 9 avec ombre portée d'1 px écran (texte sur le jeu). */
    public static int inter9o(GuiGraphicsExtractor ctx, Font f, Component c, float x, float ymil, int col, int ancre) {
        int w = f.width(c);
        float x0 = ancre == 1 ? x - w / 2f : ancre == 2 ? x - w : x;
        float px = pxEcran() * Math.max(1, Math.round(echelleGui() / 2f));
        texteEchelle(ctx, f, c, x0 + px, ymil - INTER9_MIL + px, alpha(0xFF0A0608, ((col >>> 24) & 0xFF) / 255f * 0.85f), 1f);
        texteEchelle(ctx, f, c, x0, ymil - INTER9_MIL, col, 1f);
        return w;
    }

    /** Inter 7 (Inter 9 × 7/9), milieu des capitales en ymil. Renvoie la largeur GUI. */
    public static float inter7(GuiGraphicsExtractor ctx, Font f, Component c, float x, float ymil, int col, int ancre) {
        float w = f.width(c) * S7;
        float x0 = ancre == 1 ? x - w / 2f : ancre == 2 ? x - w : x;
        texteEchelle(ctx, f, c, x0, ymil - INTER7_MIL, col, S7);
        return w;
    }

    /** Inter 7 avec ombre d'1 px écran (texte posé sur le jeu). */
    public static float inter7o(GuiGraphicsExtractor ctx, Font f, Component c, float x, float ymil, int col, int ancre) {
        float w = f.width(c) * S7;
        float x0 = ancre == 1 ? x - w / 2f : ancre == 2 ? x - w : x;
        float px = pxEcran() * Math.max(1, Math.round(echelleGui() / 2f));
        texteEchelle(ctx, f, c, x0 + px, ymil - INTER7_MIL + px, alpha(0xFF0A0608, ((col >>> 24) & 0xFF) / 255f * 0.85f), S7);
        texteEchelle(ctx, f, c, x0, ymil - INTER7_MIL, col, S7);
        return w;
    }

    /** Inter 8, milieu des capitales en ymil. Renvoie la largeur GUI. */
    public static float inter8(GuiGraphicsExtractor ctx, Font f, Component c, float x, float ymil, int col, int ancre) {
        float w = f.width(c) * S8;
        float x0 = ancre == 1 ? x - w / 2f : ancre == 2 ? x - w : x;
        texteEchelle(ctx, f, c, x0, ymil - 3.5f, col, S8);
        return w;
    }

    public static float largeur7(Font f, Component c) { return f.width(c) * S7; }

    public static float largeur8(Font f, Component c) { return f.width(c) * S8; }

    /** Étiquette en petites capitales Inter gras 7, interlettre 0,6 GUI (calculée une fois). */
    public record Etiq(Component[] lettres, float[] dx, float w) {}

    public static Etiq etiq(Font f, String s) {
        String t = sansAccents(s);
        Component[] l = new Component[t.length()];
        float[] dx = new float[t.length()];
        float x = 0;
        for (int i = 0; i < t.length(); i++) {
            l[i] = interGras(String.valueOf(t.charAt(i)));
            dx[i] = x;
            x += f.width(l[i]) * S7 + 0.6f;
        }
        return new Etiq(l, dx, Math.max(0, x - 0.6f));
    }

    public static float etiq(GuiGraphicsExtractor ctx, Font f, Etiq e, float x, float ymil, int col) {
        for (int i = 0; i < e.lettres().length; i++) {
            texteEchelle(ctx, f, e.lettres()[i], x + e.dx()[i], ymil - INTER7_MIL, col, S7);
        }
        return e.w();
    }

    /** Kanji 16 GUI (police du jeu ×2), centré en x, haut en y ; ombre d'1 GUI (0 = sans). */
    public static void kanji16(GuiGraphicsExtractor ctx, Font f, String kj, float cx, float yHaut, int col, int ombre) {
        if (kj == null || kj.isEmpty()) return;
        Component c = Component.literal(kj);
        float w = f.width(c) * 2f;
        float x = Math.round(cx - w / 2f);
        if (ombre != 0) texteEchelle(ctx, f, c, x + 1, Math.round(yHaut) + 1, ombre, 2f);
        texteEchelle(ctx, f, c, x, Math.round(yHaut), col, 2f);
    }

    /** Paragraphe découpé une fois : lignes Inter à l'échelle s, interligne en GUI. */
    public record Paragraphe(List<FormattedCharSequence> lignes, float echelle, int interligne) {
        public int hauteur() { return lignes.size() * interligne; }

        public void dessiner(GuiGraphicsExtractor ctx, Font f, float x, float yHaut, int col) {
            for (int i = 0; i < lignes.size(); i++) texteEchelle(ctx, f, lignes.get(i), x, yHaut + i * interligne, col, echelle);
        }
    }

    public static final Paragraphe VIDE = new Paragraphe(List.of(), 1f, 10);

    public static Paragraphe paragraphe(Font f, String txt, float largeur, float s, int interligne, int maxLignes) {
        if (txt == null || txt.isBlank()) return VIDE;
        int lw = Math.max(10, (int) Math.floor(largeur / s));
        List<String> lignes = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String mot : txt.trim().split(" +")) {
            String cand = cur.isEmpty() ? mot : cur + " " + mot;
            if (!cur.isEmpty() && f.width(inter(cand)) > lw) {
                lignes.add(cur.toString());
                cur = new StringBuilder(mot);
            } else {
                cur = new StringBuilder(cand);
            }
        }
        if (!cur.isEmpty()) lignes.add(cur.toString());
        if (maxLignes > 0 && lignes.size() > maxLignes) {
            String reste = String.join(" ", lignes.subList(maxLignes - 1, lignes.size()));
            List<String> k = new ArrayList<>(lignes.subList(0, maxLignes - 1));
            k.add(ellipse(f, reste, lw));
            lignes = k;
        }
        List<FormattedCharSequence> out = new ArrayList<>();
        for (String l : lignes) out.add(inter(l).getVisualOrderText());
        return new Paragraphe(out, s, interligne);
    }

    /** Coupe s (Inter 9) par « … » pour tenir dans maxW unités de police. */
    public static String ellipse(Font f, String s, int maxW) {
        if (s == null) return "";
        if (f.width(inter(s)) <= maxW) return s;
        String e = s;
        while (e.length() > 1 && f.width(inter(e + "…")) > maxW) e = e.substring(0, e.length() - 1);
        return e.trim() + "…";
    }

    /**
     * Touche dessinée (cabochon laqué de 10 GUI, texture 42×12 en (u0, v0) de l'atlas : bords 4 / 6) en (x, ymil),
     * lettre ArcadePix ×0,5 or clair. Renvoie la largeur.
     */
    public static int cabochon(GuiGraphicsExtractor ctx, Font f, Identifier id, int u0, int v0, int tw, int th, Component c,
                               int x, float ymil, float a) {
        int w = (int) Math.ceil(f.width(c) * 0.5f) + 6;
        int y = Math.round(ymil - 5);
        int col = blanc(a);
        if ((col >>> 24) != 0) {
            LotBlit l = LotBlit.ouvrir(ctx, id, tw, th);
            l.blit(x, y, u0, v0, 4, 12, col);
            for (int xx = x + 4; xx < x + w - 4; xx += 16) l.blit(xx, y, u0 + 12, v0, Math.min(16, x + w - 4 - xx), 12, col);
            l.blit(x + w - 4, y, u0 + 36, v0, 6, 12, col);
            l.fermer();
        }
        arcFin(ctx, f, c, x + w / 2f, ymil - 1.75f, alpha(OR_CLAIR, a), 1, false);
        return w;
    }

    /**
     * Aides en bas d'écran (comme la fiche) : paires {touche, action} calées à droite en xDroite, séparées par un point
     * d'or. Action en Inter 8 crème à 60 %.
     */
    public static void aides(GuiGraphicsExtractor ctx, Font f, Identifier id, int u0, int v0, int tw, int th,
                             String[][] items, float xDroite, float ymil, float a) {
        float total = 10f * (items.length - 1);
        Component[] t = new Component[items.length], ac = new Component[items.length];
        for (int i = 0; i < items.length; i++) {
            t[i] = arcade(items[i][0]);
            ac[i] = items[i][1] == null ? null : inter(items[i][1]);
            total += (int) Math.ceil(f.width(t[i]) * 0.5f) + 6 + (ac[i] != null ? 4 + largeur8(f, ac[i]) : 0);
        }
        float x = xDroite - total;
        for (int i = 0; i < items.length; i++) {
            x += cabochon(ctx, f, id, u0, v0, tw, th, t[i], Math.round(x), ymil, a);
            if (ac[i] != null) {
                inter8(ctx, f, ac[i], x + 4, ymil, argb(CREME, 0.6f * a), 0);
                x += 4 + largeur8(f, ac[i]);
            }
            if (i < items.length - 1) {
                int X = Math.round(x + 5);
                ctx.fill(X, Math.round(ymil), X + 1, Math.round(ymil) + 1, alpha(OR_ETEINT, a));
                x += 10;
            }
        }
    }

    public static boolean dans(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    /* ================================================================ pixel réel */

    /** Passe en pixels écran (1 unité = 1 px) jusqu'à {@link #finPx}. */
    public static void debutPx(GuiGraphicsExtractor ctx) {
        float k = 1f / echelleGui();
        ctx.pose().pushMatrix();
        ctx.pose().scale(k, k);
    }

    public static void finPx(GuiGraphicsExtractor ctx) { ctx.pose().popMatrix(); }
}
