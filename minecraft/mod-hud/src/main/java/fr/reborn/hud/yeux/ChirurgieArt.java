package fr.reborn.hud.yeux;

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

import java.text.Normalizer;
import java.util.Locale;

/**
 * Habillage de la chirurgie des yeux au niveau de la fiche shinobi (DA §4.1) : textures {@code textures/gui/yeux/*}
 * générées par {@code tools/gen_chirurgie_textures.py} (contrat des tailles et ancres en tête du script), affichées
 * à 1 texel = 1 GUI en un blit ; et la typographie de la fiche (ArcadePix ×1 et ×0,5, Inter 9 / 8 / 7, étiquettes en
 * petites capitales, touches en cabochons ; aucun kanji depuis le 18/10). Le code ne dessine que ce qui bouge.
 */
final class ChirurgieArt {

    private ChirurgieArt() {}

    /* ================================================================ palette (celle de la fiche) */

    static final int OR = 0xFFD9A95E, OR_CLAIR = 0xFFF2D08A, OR_ETEINT = 0xFF8E6A30, OR_ETIQ = 0xFFA8844A;
    static final int CREME = 0xFFF5E9D0, CREME_VAL = 0xFFE8DCC0, DOUX = 0xFFC2B59A, ROUGE = 0xFFA0182B;
    static final int ROUGE_VIF = 0xFFE04848, SEP = 0xFF2D181C, REPOS = 0xFFA88C5E;
    static final int[] R_OR = {0xFF2E2210, 0xFF4A3518, 0xFF6A4E24, 0xFF8E6A30, 0xFFB08442, 0xFFD9A95E, 0xFFE8C27A,
        0xFFF2D59A, 0xFFFFF2C8};

    /* ================================================================ textures */

    static Identifier tex(String n) { return Identifier.fromNamespaceAndPath("reborn", "textures/gui/yeux/" + n + ".png"); }

    static final Identifier ENTETE_PRELEVEMENT = tex("entete_prelevement"), ENTETE_GREFFE = tex("entete_greffe");
    static final Identifier ETAPES = tex("etapes"), PERCHE = tex("perche");
    static final Identifier P_PATIENT = tex("panneau_patient"), P_FIOLE = tex("panneau_fiole"),
        P_TROUSSE = tex("panneau_trousse");
    static final Identifier FIOLE_FOND = tex("fiole_fond"), FIOLE_FACE = tex("fiole_face");
    static final Identifier TROUSSE = tex("trousse"), TROUSSE_SEL = tex("trousse_sel");
    static final Identifier JAUGE_V = tex("jauge_v"), JAUGE_H = tex("jauge_h"), PIONS = tex("pions");
    static final Identifier RESULTAT = tex("resultat"), RESULTAT_SCEAUX = tex("resultat_sceaux");
    static final Identifier BOUTON = tex("bouton"), CABOCHON = tex("cabochon"), VIGNETTE = tex("vignette");

    /** Largeur des planches ; hauteurs nominales du contrat. */
    static final int PW = 200, H_PATIENT = 152, H_DROITE = 204;
    static final int FIOLE_W = 64, FIOLE_H = 112, TROUSSE_W = 168, TROUSSE_H = 132;

    static final int PION_ERREUR_VIDE = 0, PION_ERREUR = 1, PION_PERLE_VIDE = 2, PION_PERLE = 3, PION_COUPE = 4;

    static void blit(GuiGraphicsExtractor g, Identifier id, int x, int y, float u, float v, int w, int h, int tw, int th) {
        if (w <= 0 || h <= 0) return;
        g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, tw, th);
    }

    static void blit(GuiGraphicsExtractor g, Identifier id, int x, int y, int w, int h) {
        blit(g, id, x, y, 0, 0, w, h, w, h);
    }

    /** Étirement (textures lisses seulement : vignette, rampes). */
    static void etire(GuiGraphicsExtractor g, Identifier id, int x, int y, int w, int h, int tw, int th, int argb) {
        if (w <= 0 || h <= 0 || (argb >>> 24) == 0) return;
        g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, w, h, tw, th, tw, th, argb);
    }

    /** Planche laquée suspendue : texture (PW+8)×(84+h+28) posée en (x − 4, 0) ; planche en (x, 84). */
    static void planche(GuiGraphicsExtractor g, Identifier id, int x, int h) {
        blit(g, id, x - 4, 0, 0, 0, PW + 8, 84 + h + 28, PW + 8, 84 + h + 28);
    }

    /** Perches de bambou (y 55) : gauche de 0 à xd (embout à droite), droite de xg à w (embout à gauche). */
    static void perches(GuiGraphicsExtractor g, int xd, int xg, int w) {
        int lg = Math.min(300, xd + 2);
        blit(g, PERCHE, xd - lg, 55, 300 - lg, 0, lg, 8, 300, 16);
        int ld = Math.min(300, w + 2 - xg);
        blit(g, PERCHE, xg, 55, 0, 8, ld, 8, 300, 16);
    }

    /** Pion 12×12 de l'atlas, coin haut gauche en (x, y). */
    static void pion(GuiGraphicsExtractor g, int cell, int x, int y) {
        blit(g, PIONS, x, y, cell * 12, 0, 12, 12, 60, 12);
    }

    /** Jauge verticale 3 tranches : cadre en (x, y), hauteur totale h (intérieur x+3..x+9, y+3..y+h−3). */
    static void jaugeV(GuiGraphicsExtractor g, int x, int y, int h) {
        blit(g, JAUGE_V, x, y, 0, 0, 13, 8, 13, 24);
        int yy = y + 8, fin = y + h - 8;
        while (yy < fin) {
            int hh = Math.min(8, fin - yy);
            blit(g, JAUGE_V, x, yy, 0, 8, 13, hh, 13, 24);
            yy += hh;
        }
        blit(g, JAUGE_V, x, fin, 0, 16, 13, 8, 13, 24);
    }

    /* ================================================================ texte */

    static final Style INTER_GRAS = Style.EMPTY.withFont(new FontDescription.Resource(RebornFont.BOLD));
    static final float S7 = 7f / 9f, S8 = 8f / 9f;

    static MutableComponent arcade(String s) { return RebornFont.arcade(sansAccents(s)); }

    static MutableComponent inter(String s) { return RebornFont.body(s); }

    static MutableComponent interGras(String s) { return Component.literal(s).setStyle(INTER_GRAS); }

    static String sansAccents(String s) {
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
            .replace('—', '-').replace('’', '\'').replace("Œ", "OE").replace("œ", "oe").toUpperCase(Locale.ROOT);
    }

    static int gs() { return Math.max(1, Minecraft.getInstance().getWindow().getGuiScale()); }

    static float cale(float v) {
        int k = gs();
        return Math.round(v * k) / (float) k;
    }

    static float facteurPetit() {
        int k = gs();
        if (k == 1) return 0.75f;
        return Math.max(1, Math.round(k / 2f)) / (float) k;
    }

    static void texteEchelle(GuiGraphicsExtractor g, Font f, Component c, float x, float y, int col, float s) {
        g.pose().pushMatrix();
        g.pose().translate(cale(x), cale(y));
        g.pose().scale(s, s);
        g.text(f, c, 0, 0, col, false);
        g.pose().popMatrix();
    }

    /** ArcadePix ×1 (capitales de 7 GUI), haut en y ; ancre 0 gauche, 1 centre, 2 droite. */
    static int arcadeX1(GuiGraphicsExtractor g, Font f, Component c, float x, int y, int col, int ancre, boolean ombre) {
        int w = f.width(c);
        int x0 = Math.round(ancre == 1 ? x - w / 2f : ancre == 2 ? x - w : x);
        g.text(f, c, x0, y, col, ombre);
        return w;
    }

    /** ArcadePix ×0,5, haut des capitales en yHaut ; ombre d'1 px écran. Renvoie la largeur GUI. */
    static float arcFin(GuiGraphicsExtractor g, Font f, Component c, float x, float yHaut, int col, int ancre, boolean ombre) {
        float s = facteurPetit(), px = 1f / gs();
        float w = f.width(c) * s;
        float x0 = ancre == 1 ? x - w / 2f : ancre == 2 ? x - w : x;
        if (ombre) {
            int sc = 0xFF000000 | (((col >> 16) & 0xFF) / 4 << 16) | (((col >> 8) & 0xFF) / 4 << 8) | ((col & 0xFF) / 4);
            texteEchelle(g, f, c, x0 + px, yHaut + px, sc, s);
        }
        texteEchelle(g, f, c, x0, yHaut, col, s);
        return w;
    }

    /** Inter 9, milieu des capitales en ymil. */
    static int inter9(GuiGraphicsExtractor g, Font f, Component c, float x, float ymil, int col, int ancre) {
        int w = f.width(c);
        float x0 = ancre == 1 ? x - w / 2f : ancre == 2 ? x - w : x;
        g.pose().pushMatrix();
        g.pose().translate(cale(x0), cale(ymil - 4f));
        g.text(f, c, 0, 0, col, false);
        g.pose().popMatrix();
        return w;
    }

    /** Inter 7, milieu des capitales en ymil. */
    static float inter7(GuiGraphicsExtractor g, Font f, Component c, float x, float ymil, int col, int ancre) {
        float w = f.width(c) * S7;
        float x0 = ancre == 1 ? x - w / 2f : ancre == 2 ? x - w : x;
        texteEchelle(g, f, c, x0, ymil - 3f, col, S7);
        return w;
    }

    /** Inter 8 (aides), milieu des capitales en ymil. */
    static float inter8(GuiGraphicsExtractor g, Font f, Component c, float x, float ymil, int col) {
        texteEchelle(g, f, c, x, ymil - 3.5f, col, S8);
        return f.width(c) * S8;
    }

    /** Étiquette d'une ligne de planche : petites capitales Inter gras 7, interlettre 0,6 GUI. */
    static float etiq(GuiGraphicsExtractor g, Font f, String s, float x, float ymil, int col) {
        String t = sansAccents(s);
        float xx = x;
        for (int i = 0; i < t.length(); i++) {
            Component l = interGras(String.valueOf(t.charAt(i)));
            texteEchelle(g, f, l, xx, ymil - 3f, col, S7);
            xx += f.width(l) * S7 + 0.6f;
        }
        return xx - x;
    }

    /** Touche dessinée (cabochon laqué) ; lettre ArcadePix ×0,5 or clair. Renvoie la largeur. */
    static int cabochon(GuiGraphicsExtractor g, Font f, Component c, int x, float ymil) {
        int w = largeurCabochon(f, c);
        int y = Math.round(ymil - 5);
        blit(g, CABOCHON, x, y, 0, 0, 4, 12, 42, 12);
        int xx = x + 4, fin = x + w - 4;
        while (xx < fin) {
            int ww = Math.min(16, fin - xx);
            blit(g, CABOCHON, xx, y, 12, 0, ww, 12, 42, 12);
            xx += ww;
        }
        blit(g, CABOCHON, fin, y, 36, 0, 6, 12, 42, 12);
        arcFin(g, f, c, x + w / 2f, ymil - 1.75f, OR_CLAIR, 1, false);
        return w;
    }

    static int largeurCabochon(Font f, Component c) { return (int) Math.ceil(f.width(c) * 0.5f) + 6; }

    static int argb(int rgb, float a) {
        int al = Math.round(255 * Math.max(0f, Math.min(1f, a)));
        return (al << 24) | (rgb & 0xFFFFFF);
    }
}
