package fr.reborn.hud.menu.esc;

import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Textures du menu Échap « Tokonoma » ({@code textures/gui/echap/*}, générées par
 * {@code tools/gen_echap_textures.py} : tailles et ancres = contrat en tête du générateur) et texte de la scène.
 *
 * <p>La scène fait 640×360 unités, affichée à {@code k} pixels écran par unité (entier : 3 en 1080p, 2 en 1366×768,
 * 4 en 1440p) via {@link DaKit#unites}. Le texte suit la typographie de la fiche, ramenée à la scène : ArcadePix au
 * pixel écran (petit, moyen = taille ×1 de la fiche, grand), Inter proportionnel (Inter 9 de la fiche = 18 px en
 * 1080p).
 */
final class EchapArt {

    private EchapArt() {}

    static Identifier tex(String n) { return DaKit.tex("echap", n); }

    static final Identifier PIECE = tex("piece"), FENETRE = tex("fenetre"), PLAQUES = tex("plaques"),
        KAMONS = tex("kamons"), CHOCHIN = tex("chochin"), OMAMORI = tex("omamori"), KAWARABAN = tex("kawaraban"),
        RESEAUX = tex("reseaux"), PIECES = tex("pieces"), INFOBULLE = tex("infobulle"), HALO = tex("halo"),
        FUSUMA_L = tex("fusuma_l"), FUSUMA_R = tex("fusuma_r");

    static final int VW = 640, VH = 360;

    /* ---- couleurs (fiche) ---- */
    static final int CREME = DaKit.CREME, CREME_DOUX = 0xFFD8CCB0, OR = DaKit.OR, OR_CLAIR = DaKit.OR_CLAIR,
        OR_ETIQ = DaKit.OR_ETIQ, ENCRE = DaKit.ENCRE, ENCRE_DOUCE = DaKit.ENCRE_DOUCE, ENCRE_ROUGE = DaKit.ENCRE_ROUGE,
        INACTIF = DaKit.INACTIF;

    /** Pixels écran par unité de scène (fixé à chaque image). */
    static int k = 3;

    /* ================================================================ texte */

    static final int PETIT = 1, MOYEN = 2, GRAND = 4;

    /** Facteur ArcadePix : un pixel de fonte = ⌈taille·k/3⌉ pixels écran (au moins 1 ; lisible en 1366×768). */
    static float sArc(int taille) {
        return Math.max(1, (int) Math.ceil(taille * k / 3f - 0.01f)) / (float) k;
    }

    static float largeurArc(Font f, String s, int taille) {
        return (f.width(DaKit.arcade(s)) - 1) * sArc(taille);
    }

    /** ArcadePix, haut des capitales en yHaut ; ancre 0 gauche, 1 centre, 2 droite. Renvoie la largeur. */
    static float arc(GuiGraphicsExtractor g, Font f, String s, float x, float yHaut, int col, int ancre, int taille,
                     boolean ombre) {
        Component c = DaKit.arcade(s);
        float sc = sArc(taille);
        float w = (f.width(c) - 1) * sc;
        float x0 = ancre == 1 ? x - w / 2f : ancre == 2 ? x - w : x;
        if (ombre) DaKit.texteEchelle(g, f, c, x0 + sc, yHaut + sc, 0xFF000000 | 0x140608, sc);
        DaKit.texteEchelle(g, f, c, x0, yHaut, col, sc);
        return w;
    }

    /** ArcadePix MOYEN s'il tient dans maxW unités, sinon PETIT (petites fenêtres : k = 2). */
    static int tailleQuiTient(Font f, String s, float maxW) {
        return largeurArc(f, s, MOYEN) <= maxW ? MOYEN : PETIT;
    }

    /** Échelle Inter pour une taille « fiche » (9 = corps de texte). */
    static float sInter(float taille) { return taille * 2f / 27f; }

    static float largeurInter(Font f, String s, float taille) { return f.width(DaKit.inter(s)) * sInter(taille); }

    /** Inter, milieu des capitales en ymil. Renvoie la largeur. */
    static float inter(GuiGraphicsExtractor g, Font f, String s, float x, float ymil, int col, int ancre, float taille) {
        Component c = DaKit.inter(s);
        float sc = sInter(taille);
        float w = f.width(c) * sc;
        float x0 = ancre == 1 ? x - w / 2f : ancre == 2 ? x - w : x;
        DaKit.texteEchelle(g, f, c, x0, ymil - 4f * sc, col, sc);
        return w;
    }

    /** Inter gras (petites capitales d'étiquette : sans accents, majuscules). */
    static float interGras(GuiGraphicsExtractor g, Font f, String s, float x, float ymil, int col, int ancre, float taille) {
        Component c = DaKit.interGras(s);
        float sc = sInter(taille);
        float w = f.width(c) * sc;
        float x0 = ancre == 1 ? x - w / 2f : ancre == 2 ? x - w : x;
        DaKit.texteEchelle(g, f, c, x0, ymil - 4f * sc, col, sc);
        return w;
    }

    /** Coupe s (Inter, taille donnée) par « … » pour tenir dans maxW unités. */
    static String ellipse(Font f, String s, float maxW, float taille) {
        if (s == null) return "";
        if (largeurInter(f, s, taille) <= maxW) return s;
        String e = s;
        while (e.length() > 1 && largeurInter(f, e + "…", taille) > maxW) e = e.substring(0, e.length() - 1);
        return e.trim() + "…";
    }

    static String ellipseArc(Font f, String s, float maxW, int taille) {
        if (s == null) return "";
        if (largeurArc(f, s, taille) <= maxW) return s;
        String e = s;
        while (e.length() > 1 && largeurArc(f, e + "..", taille) > maxW) e = e.substring(0, e.length() - 1);
        return e.trim() + "..";
    }

    /** Découpe en lignes Inter (au plus maxLignes, la dernière coupée par « … »). */
    static java.util.List<String> lignes(Font f, String s, float maxW, float taille, int maxLignes) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (s == null || s.isBlank()) return out;
        StringBuilder cur = new StringBuilder();
        String[] mots = s.trim().split(" +");
        int i = 0;
        for (; i < mots.length; i++) {
            String cand = cur.isEmpty() ? mots[i] : cur + " " + mots[i];
            if (!cur.isEmpty() && largeurInter(f, cand, taille) > maxW) {
                out.add(cur.toString());
                cur = new StringBuilder(mots[i]);
                if (out.size() == maxLignes) break;
            } else {
                cur = new StringBuilder(cand);
            }
        }
        if (out.size() < maxLignes && !cur.isEmpty()) out.add(cur.toString());
        else if (out.size() == maxLignes && i < mots.length) {
            StringBuilder reste = new StringBuilder(out.get(maxLignes - 1));
            for (int j = i; j < mots.length; j++) reste.append(' ').append(mots[j]);
            out.set(maxLignes - 1, ellipse(f, reste.toString(), maxW, taille));
        }
        return out;
    }

    /* ================================================================ pièces */

    static void blit(GuiGraphicsExtractor g, Identifier id, int x, int y, int u, int v, int w, int h, int tw, int th) {
        DaKit.blit(g, id, x, y, u, v, w, h, tw, th);
    }

    static void blit(GuiGraphicsExtractor g, Identifier id, int x, int y, int u, int v, int w, int h, int tw, int th,
                     int argb) {
        DaKit.blit(g, id, x, y, u, v, w, h, tw, th, argb);
    }

    /** Halo en crans (48×48, blanc) teint, centré, à la taille 48 × m (m entier ≥ 1) ou 24 (m = 0). */
    static void halo(GuiGraphicsExtractor g, float cx, float cy, int m, int argb) {
        int s = m <= 0 ? 24 : 48 * m;
        DaKit.etire(g, HALO, Math.round(cx - s / 2f), Math.round(cy - s / 2f), s, s, 48, 48, argb);
    }

    /** Cabochon de touche (42×12, bords 4 / 6) à la largeur w, en (x, y). */
    static void cabochon(GuiGraphicsExtractor g, int x, int y, int w, float a) {
        int col = DaKit.blanc(a);
        DaKit.blit(g, PIECES, x, y, 0, 0, 4, 12, 128, 48, col);
        for (int xx = x + 4; xx < x + w - 6; xx += 32) DaKit.blit(g, PIECES, xx, y, 4, 0, Math.min(32, x + w - 6 - xx), 12, 128, 48, col);
        DaKit.blit(g, PIECES, x + w - 6, y, 36, 0, 6, 12, 128, 48, col);
    }

    /** Badge laqué (rouge = LIVE, noir = hors ligne) à la largeur w (coupe horizontale). */
    static void badge(GuiGraphicsExtractor g, int x, int y, int w, boolean rouge) {
        DaKit.coupeH(g, PIECES, x, y, w, rouge ? 0 : 46, 14, 44, 11, 4, 128, 48);
    }

    static void fleche(GuiGraphicsExtractor g, int x, int y, boolean droite, boolean survol) {
        int u = (survol ? 60 : 44) + (droite ? 8 : 0);
        DaKit.blit(g, PIECES, x, y, u, 0, 8, 9, 128, 48);
    }

    static void piece(GuiGraphicsExtractor g, int x, int y) {
        DaKit.blit(g, PIECES, x, y, 78, 0, 10, 10, 128, 48);
    }

    static void ryo(GuiGraphicsExtractor g, int x, int y) {
        DaKit.blit(g, PIECES, x, y, 98, 14, 6, 6, 128, 48);
    }

    static void filet(GuiGraphicsExtractor g, int x, int y, int w) {
        DaKit.blit(g, PIECES, x, y, 0, 28, Math.min(60, w), 1, 128, 48);
    }

    /** Infobulle de washi (coupe : capD = capB = 10) : boîte (x, y, w, h), texture posée 2 unités plus haut / à gauche. */
    static void infobulle(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        DaKit.coupe(g, INFOBULLE, x - 2, y - 2, w + 8, h + 8, 0, 0, 176, 80, 10, 10, 176, 80);
    }
}
