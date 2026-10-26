package fr.reborn.hud.douane;

import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.LotGui;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Kit des écrans de la douane côté candidat (maquettes J1 à J5 du 24/10, {@code Maquettes/gameplan/candidature/jeu/}) :
 * palette, textures ({@code tools/gen_douane_textures.py}) et une plume qui regroupe le dessin en lots (aplats, filets
 * d'1 px écran, textes : quelques éléments GUI par image, DA §6). Signature des écrans : la barrière du poste-frontière,
 * fermée au hall et au refus, levée à « Bienvenue ». Aucun kanji (DA §3).
 */
final class DouaneArt {

    private DouaneArt() {}

    /* ================================================================ palette (mêmes valeurs que les maquettes) */

    static final int OR = 0xFFD9A95E, OR_CLAIR = 0xFFF2D08A, CREME = 0xFFF5E9D0, CREME_DOUX = 0xFFC2B59A,
            GRIS = 0xFF9A9088, GRIS_FONCE = 0xFF6E645E, AMBRE = 0xFFE2A04A, VERM = 0xFFD2523E, TERNE = 0xFF8A7E70,
            TERNE_CLAIR = 0xFFB4A898, FILET_TERNE = 0xFF5A5048, VOILE = 0x0D080A;

    /* ================================================================ textures (contrat : gen_douane_textures.py) */

    static final Identifier BARRIERE = DaKit.tex("douane", "barriere"), NUIT = DaKit.tex("douane", "nuit"),
            NUIT_FROID = DaKit.tex("douane", "nuit_froid");
    static final int ATLAS_W = 96, ATLAS_H = 64, NUIT_W = 960, NUIT_H = 540;
    /** Régions de l'atlas : {u, v, largeur, hauteur}. */
    static final int[] LEVEE = {0, 0, 48, 55}, FERMEE = {48, 0, 48, 22}, PICTO = {0, 57, 15, 7};

    /** Fond de nuit qui couvre tout l'écran (unités menu), opaque : jamais le monde derrière. */
    static void nuit(GuiGraphicsExtractor g, boolean froid, float sw, float sh, float a) {
        float k = Math.max(sw / NUIT_W, sh / NUIT_H);
        int w = Math.round(NUIT_W * k), h = Math.round(NUIT_H * k);
        int x = Math.round((sw - w) / 2f), y = Math.round((sh - h) / 2f);
        LotGui l = LotGui.ouvrir(g);
        l.fill(0, 0, Math.round(sw) + 1, Math.round(sh) + 1, DaKit.argb(0x0A0608, a));
        l.fermer();
        g.blit(RenderPipelines.GUI_TEXTURED, froid ? NUIT_FROID : NUIT, x, y, 0f, 0f, w, h, NUIT_W, NUIT_H, NUIT_W, NUIT_H,
                DaKit.blanc(a));
    }

    /**
     * Sprite pixel art de l'atlas, net : {@code px} pixels écran par texel, bas centré en (cx, yBas) (unités menu
     * courantes, {@code km} pixels écran par unité).
     */
    static void sprite(GuiGraphicsExtractor g, int[] r, float cx, float yBas, int px, int km, int argb) {
        float s = px / (float) km;
        g.pose().pushMatrix();
        g.pose().translate(DaKit.cale(cx - r[2] * s / 2f), DaKit.cale(yBas - r[3] * s));
        g.pose().scale(s, s);
        g.blit(RenderPipelines.GUI_TEXTURED, BARRIERE, 0, 0, r[0], r[1], r[2], r[3], ATLAS_W, ATLAS_H, argb);
        g.pose().popMatrix();
    }

    /* ================================================================ plume */

    /** Pinceau des écrans de la douane, à l'échelle de dessin courante (donc sous {@link DaKit#unites}). */
    static final class Plume {
        private record Txt(FormattedCharSequence c, float x, float y, int col, float s) { }

        private final List<int[]> fonds = new ArrayList<>(32);
        private final List<int[]> filets = new ArrayList<>(32);
        private final List<Txt> textes = new ArrayList<>(48);
        private Font font;

        void police(Font f) { this.font = f; }

        Font police() { return font; }

        private static int k() { return DaKit.echelleGui(); }

        void fond(float x0, float y0, float x1, float y1, int col) {
            int a = Math.round(x0), b = Math.round(y0), c = Math.round(x1), d = Math.round(y1);
            if (c > a && d > b && (col >>> 24) != 0) fonds.add(new int[]{a, b, c, d, col});
        }

        /** Rectangle au pixel écran. */
        void px(float x0, float y0, float x1, float y1, int col) {
            int k = k();
            int X0 = Math.round(x0 * k), Y0 = Math.round(y0 * k), X1 = Math.round(x1 * k), Y1 = Math.round(y1 * k);
            if (X1 > X0 && Y1 > Y0 && (col >>> 24) != 0) filets.add(new int[]{X0, Y0, X1, Y1, col});
        }

        /** Filet horizontal d'1 px écran. */
        void h(float x0, float x1, float y, int col) {
            int k = k();
            int X0 = Math.round(x0 * k), X1 = Math.round(x1 * k), Y = Math.round(y * k);
            if (X1 > X0 && (col >>> 24) != 0) filets.add(new int[]{X0, Y, X1, Y + 1, col});
        }

        void v(float x, float y0, float y1, int col) {
            int k = k();
            int X = Math.round(x * k), Y0 = Math.round(y0 * k), Y1 = Math.round(y1 * k);
            if (Y1 > Y0 && (col >>> 24) != 0) filets.add(new int[]{X, Y0, X + 1, Y1, col});
        }

        /** Petit losange plein (filets de titre), centré, demi-diagonale r pixels écran. */
        void losange(float cx, float cy, int r, int col) {
            int k = k();
            int X = Math.round(cx * k), Y = Math.round(cy * k);
            for (int j = -r; j <= r; j++) {
                int w = r - Math.abs(j);
                filets.add(new int[]{X - w, Y + j, X + w + 1, Y + j + 1, col});
            }
        }

        /** Texte Inter (taille en px de maquette à l'échelle 2), milieu de ligne en cy. Renvoie la largeur. */
        float texte(String s, float x, float cy, int taille, int col, boolean gras) {
            if (s == null || s.isEmpty() || (col >>> 24) == 0) return largeur(s, taille, gras);
            Component c = gras ? DaKit.interGras(s) : RebornFont.body(s);
            float k = taille / 18f;
            textes.add(new Txt(c.getVisualOrderText(), x, cy - 4f * k, col, k));
            return font.width(c) * k;
        }

        float texteC(String s, float xc, float cy, int taille, int col, boolean gras) {
            float w = largeur(s, taille, gras);
            texte(s, xc - w / 2f, cy, taille, col, gras);
            return w;
        }

        float largeur(String s, int taille, boolean gras) {
            if (s == null || s.isEmpty()) return 0;
            return font.width(gras ? DaKit.interGras(s) : RebornFont.body(s)) * (taille / 18f);
        }

        /** ArcadePix (petites capitales), haut des capitales en y, échelle sc. */
        float arcade(String s, float x, float y, int col, float sc) {
            Component c = DaKit.arcade(s);
            if ((col >>> 24) != 0) textes.add(new Txt(c.getVisualOrderText(), x, y, col, sc));
            return (font.width(c) - 1) * sc;
        }

        float largeurArcade(String s, float sc) { return (font.width(DaKit.arcade(s)) - 1) * sc; }

        /** Petites capitales espacées (rubriques), Inter gras, centrées en cx. */
        void capitalesC(String s, float cx, float cy, int taille, int col) {
            String t = DaKit.sansAccents(s);
            float esp = 1.6f;
            float w = 0;
            for (int i = 0; i < t.length(); i++) w += largeur(String.valueOf(t.charAt(i)), taille, true) + esp;
            float x = cx - (w - esp) / 2f;
            for (int i = 0; i < t.length(); i++) x += texte(String.valueOf(t.charAt(i)), x, cy, taille, col, true) + esp;
        }

        /** Coupe un texte en lignes de largeur max (mots entiers ; au-delà de max lignes, « … »). */
        List<String> lignes(String s, float maxW, int taille, boolean gras, int max) {
            List<String> out = new ArrayList<>();
            if (s == null || s.isBlank()) return out;
            StringBuilder cur = new StringBuilder();
            for (String mot : s.trim().split("\\s+")) {
                String essai = cur.isEmpty() ? mot : cur + " " + mot;
                if (!cur.isEmpty() && largeur(essai, taille, gras) > maxW) {
                    out.add(cur.toString());
                    cur = new StringBuilder(mot);
                } else cur = new StringBuilder(essai);
            }
            if (!cur.isEmpty()) out.add(cur.toString());
            if (max > 0 && out.size() > max) {
                List<String> l = new ArrayList<>(out.subList(0, max));
                String der = l.get(max - 1);
                while (der.length() > 1 && largeur(der + "…", taille, gras) > maxW) der = der.substring(0, der.length() - 1);
                l.set(max - 1, der.stripTrailing() + "…");
                return l;
            }
            return out;
        }

        /** Soumet tout ce qui a été peint (aplats, filets, textes), chacun en un lot. */
        void vider(GuiGraphicsExtractor ctx) {
            if (!fonds.isEmpty()) {
                LotGui l = LotGui.ouvrir(ctx);
                for (int[] f : fonds) l.fill(f[0], f[1], f[2], f[3], f[4]);
                l.fermer();
                fonds.clear();
            }
            if (!filets.isEmpty()) {
                DaKit.debutPx(ctx);
                LotGui l = LotGui.ouvrir(ctx);
                for (int[] f : filets) l.fill(f[0], f[1], f[2], f[3], f[4]);
                l.fermer();
                filets.clear();
                DaKit.finPx(ctx);
            }
            if (!textes.isEmpty()) {
                LotGui.Textes t = LotGui.textes(ctx);
                for (Txt x : textes) {
                    ctx.pose().pushMatrix();
                    ctx.pose().translate(DaKit.cale(x.x()), DaKit.cale(x.y()));
                    if (x.s() != 1f) ctx.pose().scale(x.s(), x.s());
                    t.texte(font, x.c(), 0, 0, x.col(), false);
                    ctx.pose().popMatrix();
                }
                t.fermer();
                textes.clear();
            }
        }
    }
}
