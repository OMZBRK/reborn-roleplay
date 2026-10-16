package fr.reborn.hud.communication;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.LotBlit;
import fr.reborn.hud.ui.LotGui;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Kit de la cloche et des volières (maquettes v3c, {@code tools/gen_communication_textures.py}) : palette nuit / laque /
 * or des écrans centrés (celle du guichet), textures, texte riche (Inter, <b>gras</b> = Inter Bold, <i>italique</i> =
 * Inter penché par le style), pixel art posé à multiple entier en pixels écran, et la {@link Plume} qui peint par lots.
 */
final class CommArt {

    private CommArt() {}

    /* ================================================================ palette (_maquettes_cloche_voliere*.py) */

    static final int CREME = 0xFFEEE4D2, CREME_DOUX = 0xFFC2B59A, OR = 0xFFD9A95E, OR_CLAIR = 0xFFF2D08A,
            OR_SOMBRE = 0xFF8E6A30, OR_FONCE = 0xFF6A4E24, AMBRE = 0xFFE2A04A, ROUGE = 0xFFE46676, VERT = 0xFF6FBF8E,
            GRIS = 0xFF8C8478, GRIS_FONCE = 0xFF6E645E, FILET = 0xFF3A2A1E, FILET_ZONE = 0xFF2E2024, NUIT_ZONE = 0xFF090507,
            SURVOL = 0xFF28161C, PUCE_ON = 0xFF28161C, PUCE_OFF = 0xFF0E090B, PUCE_BORD = 0xFF3A2E30;
    static final int IMPORTANTE = 0xFFE8873A, DANGER = 0xFFD2523E;

    static int couleurType(String type) {
        return switch (type == null ? "" : type) { case "importante" -> IMPORTANTE; case "danger" -> DANGER; default -> OR; };
    }

    /* ================================================================ textures */

    static Identifier tex(String nom) { return DaKit.tex("communication", nom); }

    static final Identifier PANNEAU = tex("panneau"), MOTIF_CLOCHE = tex("motif_cloche"), MOTIF_CORBEAU = tex("motif_corbeau"),
            CACHET = tex("cachet"), CACHET_BRISE = tex("cachet_brise"), MISSIVE = tex("missive_scellee"),
            MISSIVE_OUVERTE = tex("missive_descellee"), MISSIVE_BANDE = tex("missive_bande"), COCHE = tex("coche"),
            LOUPE = tex("loupe"), PIECE = DaKit.tex("monnaie", "piece");
    static final int PW = 452, PH = 392;

    static Identifier cloche(String type) { return tex("cloche_" + (type == null || type.isEmpty() ? "normale" : type)); }

    static int largeurCloche(String type) { return "danger".equals(type) ? 19 : "importante".equals(type) ? 15 : 9; }

    /** Panneau laqué (coupe de la grande planche), ombre comprise : la face fait (w, h). */
    static void panneau(GuiGraphicsExtractor ctx, int x, int y, int w, int h) {
        DaKit.coupe(ctx, PANNEAU, x, y, w + 6, h + 6, 0, 0, PW, PH, 16, 16, PW, PH);
    }

    /**
     * Pixel art net : la texture (tw × th) posée en (x, y) GUI à environ {@code largeurGui} de large, agrandie d'un
     * multiple entier en pixels écran (jamais de pixel irrégulier). Renvoie la largeur GUI réellement posée.
     */
    static float sprite(GuiGraphicsExtractor ctx, Identifier id, int tw, int th, float x, float y, float largeurGui, int argb) {
        int k = DaKit.echelleGui();
        int m = Math.max(1, Math.round(largeurGui * k / tw));
        DaKit.debutPx(ctx);
        LotBlit b = LotBlit.ouvrir(ctx, id, tw, th);
        b.etire(Math.round(x * k), Math.round(y * k), tw * m, th * m, 0, 0, tw, th, argb);
        b.fermer();
        DaKit.finPx(ctx);
        return tw * m / (float) k;
    }

    /** Visage d'un contact (skin RP prêté, sinon celui du serveur, sinon par défaut), net, coin (x, y), côté en GUI. */
    static void visage(GuiGraphicsExtractor ctx, java.util.UUID id, float x, float y, float taille) {
        Identifier skin;
        if (id == null) skin = net.minecraft.client.resources.DefaultPlayerSkin.getDefaultTexture();
        else {
            Identifier rp = fr.reborn.hud.skin.RebornSkins.overrideFor(id);
            if (rp != null) skin = rp;
            else {
                skin = null;
                try {
                    var conn = net.minecraft.client.Minecraft.getInstance().getConnection();
                    var info = conn == null ? null : conn.getPlayerInfo(id);
                    if (info != null) skin = info.getSkin().body().texturePath();
                } catch (RuntimeException ignored) { }
                if (skin == null) skin = net.minecraft.client.resources.DefaultPlayerSkin.get(id).body().texturePath();
            }
        }
        int k = DaKit.echelleGui();
        int s = Math.max(8, Math.round(taille * k / 8f) * 8);
        DaKit.debutPx(ctx);
        ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, skin, Math.round(x * k), Math.round(y * k), 8f, 8f, s, s, 8, 8, 64, 64, -1);
        ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, skin, Math.round(x * k), Math.round(y * k), 40f, 8f, s, s, 8, 8, 64, 64, -1);
        DaKit.finPx(ctx);
    }

    /* ================================================================ texte riche */

    /** Un segment : texte et style ({@code ""}, {@code "g"}, {@code "i"}, {@code "gi"}). */
    record Seg(String t, String st) {}

    static Style style(String st) {
        Style s = st != null && st.contains("g") ? DaKit.INTER_GRAS : RebornFont.BODY_STYLE;
        return st != null && st.contains("i") ? s.withItalic(true) : s;
    }

    static List<Seg> segs(JsonElement e) {
        List<Seg> l = new ArrayList<>();
        if (e == null) return l;
        if (e.isJsonPrimitive()) { l.add(new Seg(e.getAsString(), "")); return l; }
        if (!e.isJsonArray()) return l;
        for (JsonElement x : e.getAsJsonArray()) {
            if (!x.isJsonArray() || x.getAsJsonArray().isEmpty()) continue;
            JsonArray a = x.getAsJsonArray();
            l.add(new Seg(a.get(0).getAsString(), a.size() > 1 ? a.get(1).getAsString() : ""));
        }
        return l;
    }

    static JsonArray json(List<Seg> l) {
        JsonArray a = new JsonArray();
        for (Seg s : l) {
            JsonArray x = new JsonArray();
            x.add(s.t());
            x.add(s.st());
            a.add(x);
        }
        return a;
    }

    static String plat(List<Seg> l) {
        StringBuilder b = new StringBuilder();
        for (Seg s : l) b.append(s.t());
        return b.toString();
    }

    static Component riche(List<Seg> l) {
        MutableComponent c = Component.empty();
        for (Seg s : l) c.append(Component.literal(s.t()).setStyle(style(s.st())));
        return c;
    }

    /**
     * Coupe un texte riche en lignes de {@code largeur} GUI à la taille {@code taille} (px de maquette), sur les espaces
     * et les retours à la ligne. Au plus {@code maxLignes} (la dernière finit par « … »).
     */
    static List<List<Seg>> lignes(Font f, List<Seg> segs, float largeur, int taille, int maxLignes) {
        // aplatit en caractères stylés
        StringBuilder t = new StringBuilder();
        List<String> st = new ArrayList<>();
        for (Seg s : segs) for (int i = 0; i < s.t().length(); i++) { t.append(s.t().charAt(i)); st.add(s.st()); }
        float k = taille / 18f;
        List<List<Seg>> out = new ArrayList<>();
        int deb = 0, n = t.length();
        while (deb < n) {
            float w = 0;
            int i = deb, coupe = -1;
            while (i < n) {
                char c = t.charAt(i);
                if (c == '\n') break;
                float cw = f.getSplitter().stringWidth(Component.literal(String.valueOf(c)).setStyle(style(st.get(i)))) * k;
                if (w + cw > largeur && i > deb) break;
                if (c == ' ') coupe = i;
                w += cw;
                i++;
            }
            int fin = i;
            if (i < n && t.charAt(i) != '\n' && coupe > deb) fin = coupe;
            out.add(tranche(t, st, deb, fin));
            deb = fin;
            if (deb < n && (t.charAt(deb) == '\n' || t.charAt(deb) == ' ')) deb++;
            if (maxLignes > 0 && out.size() == maxLignes && deb < n) {
                List<Seg> d = out.get(out.size() - 1);
                d.add(new Seg("…", ""));
                break;
            }
        }
        return out;
    }

    private static List<Seg> tranche(StringBuilder t, List<String> st, int a, int b) {
        List<Seg> l = new ArrayList<>();
        int i = a;
        while (i < b) {
            int j = i;
            while (j < b && st.get(j).equals(st.get(i))) j++;
            l.add(new Seg(t.substring(i, j), st.get(i)));
            i = j;
        }
        return l;
    }

    /* ================================================================ la plume */

    /**
     * Pinceau des écrans de la cloche et des volières (même principe que {@code monnaie/Pinceau} : aplats, filets d'1 px
     * écran, textes, soumis en lots par {@link #vider}), à l'échelle de dessin courante ({@link DaKit#echelleGui}, donc
     * aussi sous {@link DaKit#unites} pour la bande du HUD), avec le texte riche.
     */
    static final class Plume {
        private record Txt(FormattedCharSequence c, float x, float y, int col, float s) { }

        private final List<int[]> fonds = new ArrayList<>(64);
        private final List<int[]> filets = new ArrayList<>(96);
        private final List<Txt> textes = new ArrayList<>(96);
        private Font font;

        void police(Font f) { this.font = f; }

        Font police() { return font; }

        static int k() { return DaKit.echelleGui(); }

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
            if (X1 > X0) filets.add(new int[]{X0, Y, X1, Y + 1, col});
        }

        void v(float x, float y0, float y1, int col) {
            int k = k();
            int X = Math.round(x * k), Y0 = Math.round(y0 * k), Y1 = Math.round(y1 * k);
            if (Y1 > Y0) filets.add(new int[]{X, Y0, X + 1, Y1, col});
        }

        void cadre(float x, float y, float w, float h, int col) {
            int k = k();
            int X0 = Math.round(x * k), Y0 = Math.round(y * k), X1 = Math.round((x + w) * k), Y1 = Math.round((y + h) * k);
            filets.add(new int[]{X0, Y0, X1, Y0 + 1, col});
            filets.add(new int[]{X0, Y1 - 1, X1, Y1, col});
            filets.add(new int[]{X0, Y0 + 1, X0 + 1, Y1 - 1, col});
            filets.add(new int[]{X1 - 1, Y0 + 1, X1, Y1 - 1, col});
        }

        /** Disque plein de rayon r GUI. */
        void disque(float cx, float cy, float r, int col) {
            int k = k();
            float R = r * k;
            int X = Math.round(cx * k), Y = Math.round(cy * k), n = (int) Math.ceil(R);
            for (int j = -n; j <= n; j++) {
                int w = (int) Math.floor(Math.sqrt(Math.max(0, R * R - (j + 0.5f) * (j + 0.5f))) + 0.5f);
                if (w > 0) filets.add(new int[]{X - w, Y + j, X + w, Y + j + 1, col});
            }
        }

        /** Anneau (bouton radio) : disque d'or et creux de nuit. */
        void anneau(float cx, float cy, float r, float ep, int col, int fondCol) {
            disque(cx, cy, r, col);
            disque(cx, cy, r - ep, fondCol);
        }

        float texte(String s, float x, float cy, int taille, int col, boolean gras) {
            if (s == null || s.isEmpty()) return 0;
            Component c = gras ? DaKit.interGras(s) : RebornFont.body(s);
            return composant(c, x, cy, taille, col);
        }

        float italique(String s, float x, float cy, int taille, int col) {
            if (s == null || s.isEmpty()) return 0;
            return composant(Component.literal(s).setStyle(RebornFont.BODY_STYLE.withItalic(true)), x, cy, taille, col);
        }

        float composant(Component c, float x, float cy, int taille, int col) {
            float k = taille / 18f;
            textes.add(new Txt(c.getVisualOrderText(), x, cy - 4f * k, col, k));
            return font.width(c) * k;
        }

        float riche(List<Seg> l, float x, float cy, int taille, int col) { return composant(CommArt.riche(l), x, cy, taille, col); }

        float largeurRiche(List<Seg> l, int taille) { return font.width(CommArt.riche(l)) * (taille / 18f); }

        float texteC(String s, float xc, float cy, int taille, int col, boolean gras) {
            float w = largeur(s, taille, gras);
            texte(s, xc - w / 2f, cy, taille, col, gras);
            return w;
        }

        float texteD(String s, float xd, float cy, int taille, int col, boolean gras) {
            float w = largeur(s, taille, gras);
            texte(s, xd - w, cy, taille, col, gras);
            return w;
        }

        float largeur(String s, int taille, boolean gras) {
            if (s == null || s.isEmpty()) return 0;
            return font.width(gras ? DaKit.interGras(s) : RebornFont.body(s)) * (taille / 18f);
        }

        float largeurItalique(String s, int taille) {
            return s == null ? 0 : font.width(Component.literal(s).setStyle(RebornFont.BODY_STYLE.withItalic(true))) * (taille / 18f);
        }

        /** ArcadePix (petites capitales), haut des capitales en y, échelle sc. */
        float arcade(String s, float x, float y, int col, float sc) {
            Component c = DaKit.arcade(s);
            textes.add(new Txt(c.getVisualOrderText(), x, y, col, sc));
            return (font.width(c) - 1) * sc;
        }

        float largeurArcade(String s, float sc) { return (font.width(DaKit.arcade(s)) - 1) * sc; }

        /** Petites capitales des titres de rubrique (« TYPE », « POUR QUI ») : Inter gras 11. */
        float rubrique(String s, float x, float cy, int col) { return texte(s.toUpperCase(java.util.Locale.ROOT), x, cy, 11, col, true); }

        String ellipse(String s, float maxW, int taille, boolean gras) {
            if (s == null || largeur(s, taille, gras) <= maxW) return s;
            String e = s;
            while (e.length() > 1 && largeur(e + "…", taille, gras) > maxW) e = e.substring(0, e.length() - 1);
            return e.trim() + "…";
        }

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

    /* ================================================================ pièces communes */

    /** Zone d'écriture encastrée : nuit plus profonde que la laque, ombre interne, arête basse, filet d'or au focus. */
    static void zone(Plume pz, float x, float y, float w, float h, boolean focus) {
        pz.fond(x, y, x + w, y + h, NUIT_ZONE);
        pz.cadre(x, y, w, h, focus ? OR_FONCE : FILET);
        pz.px(x + 0.5f, y + 0.5f, x + w - 0.5f, y + 1.5f, 0xFF030102);
        pz.h(x + 0.5f, x + w - 0.5f, y + h - 1, 0xFF2C1E22);
    }

    /** Case à cocher (7 GUI). */
    static void caseACocher(Plume pz, float x, float y, boolean on, boolean survol) {
        pz.fond(x, y, x + 7, y + 7, 0xFF0A0608);
        pz.cadre(x, y, 7, 7, on ? OR : survol ? OR_SOMBRE : 0xFF5A4C4C);
    }

    /** Bouton radio (rayon 3,5 GUI). */
    static void radio(Plume pz, float cx, float cy, boolean on) {
        pz.anneau(cx, cy, 3.5f, 0.65f, on ? OR : 0xFF6A5C5C, 0xFF0A0608);
        if (on) pz.disque(cx, cy, 1.6f, OR_CLAIR);
    }
}
