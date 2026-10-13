package fr.reborn.hud.parchemin;

import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.LotBlit;
import fr.reborn.hud.ui.LotGui;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

import java.util.HashMap;
import java.util.Map;

/**
 * Textures de la bibliothèque, de la lecture et des réglages staff ({@code textures/gui/bibliotheque/*}, générées par
 * {@code tools/gen_bibliotheque_textures.py} ; tailles = contrat en tête du générateur) et petites pièces partagées
 * par les trois écrans : en-tête suspendu, sceaux et étiquettes de rang, brocart, plaques-boutons, réglettes, aides.
 */
final class BiblioArt {

    private BiblioArt() {}

    static Identifier tex(String n) { return DaKit.tex("bibliotheque", n); }

    static final Identifier ENTETE = tex("entete"), MEUBLE = tex("meuble"), CASIERS = tex("casiers"),
        FUDAS = tex("fudas"), SCEAUX = tex("sceaux"), CARTE = tex("carte"), BROCART = tex("brocart"),
        PIECES = tex("pieces"), REGLAGES = tex("reglages"), TOAST = tex("toast"), VIGNETTE = tex("vignette"),
        ROULEAU = tex("rouleau"), BAGUETTES = tex("baguettes"), KANJI = tex("kanji"), PERLES = tex("perles"),
        SIGNE = tex("signe"), BOUTON = tex("bouton"), PANNEAU = tex("panneau"), PLAQUES = tex("plaques"),
        REGLETTE = tex("reglette"), CHEVILLES = tex("chevilles"), CHAMP = tex("champ"), MINI = tex("mini"),
        ONGLETS = tex("onglets"), ACCES = tex("acces"), REGISTRE = tex("registre");

    /** Dimensions du meuble (texture 376×274) et de ses casiers. */
    static final int MEUBLE_W = 376, MEUBLE_H = 274, CW = 104, CH = 62;

    static int rang(char r) {
        int i = "DCBAS".indexOf(r);
        return i < 0 ? 0 : i;
    }

    /** Voile sur le jeu + vignette (le jeu reste visible derrière). */
    static void fond(GuiGraphicsExtractor g, int w, int h, float voile) {
        g.fill(0, 0, w, h, DaKit.argb(0x080406, voile));
        DaKit.etire(g, VIGNETTE, 0, 0, w, h, 160, 90, DaKit.blanc(0.9f));
    }

    /** En-tête suspendu : plaque laquée, nom en ArcadePix ×1 crème, sous-titre en Inter 7 sous la plaque. */
    static void entete(GuiGraphicsExtractor g, Font f, int w, Component nom, Component sous) {
        int x = w / 2 - 180;
        DaKit.blit(g, ENTETE, x, 0, 0, 0, 360, 48, 360, 48);
        DaKit.arcX1(g, f, nom, w / 2f, 19, DaKit.CREME, 1, true);
        if (sous != null) DaKit.inter7o(g, f, sous, w / 2f, 46, DaKit.argb(0xE8DCC0, 0.75f), 1);
    }

    /** Étiquette de rang (fuda) : fil depuis (x + 6, y), étiquette 10×13 en (x + 1, y + 6). */
    static void fuda(GuiGraphicsExtractor g, int x, int y, char r) {
        DaKit.blit(g, FUDAS, x, y, rang(r) * 14, 0, 14, 20, 70, 20);
    }

    static LotBlit fudas(GuiGraphicsExtractor g) { return LotBlit.ouvrir(g, FUDAS, 70, 20); }

    static void fuda(LotBlit l, int x, int y, char r) { l.blit(x, y, rang(r) * 14, 0, 14, 20); }

    /** Sceau de rang de 18 GUI centré en (cx, cy). */
    static void sceau18(GuiGraphicsExtractor g, int cx, int cy, char r) {
        DaKit.blit(g, SCEAUX, cx - 9, cy - 9, rang(r) * 20, 0, 20, 20, 240, 28);
    }

    /** Sceau de rang de 26 GUI centré en (cx, cy). */
    static void sceau26(GuiGraphicsExtractor g, int cx, int cy, char r) {
        DaKit.blit(g, SCEAUX, cx - 13, cy - 13, 100 + rang(r) * 28, 0, 28, 28, 240, 28);
    }

    /** Bande de brocart du rang, 8 GUI de haut, largeur w (≤ 440). */
    static void brocart(GuiGraphicsExtractor g, int x, int y, int w, char r) {
        DaKit.coupeH(g, BROCART, x, y, w, 0, rang(r) * 8, 440, 8, 4, 440, 40);
    }

    /** Bande de brocart de hauteur réduite (barres de la simulation). */
    static void brocartH(GuiGraphicsExtractor g, int x, int y, int w, int h, char r) {
        if (w <= 0) return;
        DaKit.blit(g, BROCART, x, y, 0, rang(r) * 8 + 1, Math.min(w, 440), h, 440, 40);
    }

    /** {@link #brocartH} dans un lot (texture BROCART 440×40). */
    static void brocartH(LotBlit l, int x, int y, int w, int h, char r) {
        if (w <= 0) return;
        l.blit(x, y, 0, rang(r) * 8 + 1, Math.min(w, 440), h);
    }

    /** Trait de pinceau vermillon (largeur ≤ 160). */
    static void trait(GuiGraphicsExtractor g, int x, int y, int w) {
        DaKit.blit(g, PIECES, x, y, 0, 0, Math.min(160, w), 4, 210, 50);
    }

    /** Aides en bas à droite (cabochons de touches). */
    static void aides(GuiGraphicsExtractor g, Font f, int w, int h, String[][] items) {
        DaKit.aides(g, f, PIECES, 0, 6, 210, 50, items, w - 18, h - 14, 1f);
    }

    static int cabochon(GuiGraphicsExtractor g, Font f, String t, int x, float ymil, float a) {
        return DaKit.cabochon(g, f, PIECES, 0, 6, 210, 50, DaKit.arcade(t), x, ymil, a);
    }

    /** Jauge laquée (sacoche) : étiquette et valeur au-dessus, rainure 200 GUI, remplissage or (laque si presque plein). */
    static void jauge(GuiGraphicsExtractor g, Font f, int x, int y, float v, DaKit.Etiq etiq, Component valeur) {
        Txt t = new Txt(g, f);
        t.etiq(etiq, x, y + 3, DaKit.OR_ETIQ);
        t.inter8(valeur, x + 200, y + 3, DaKit.CREME_VAL, 2);
        t.fermer();
        LotBlit l = LotBlit.ouvrir(g, PIECES, 210, 50);
        l.blit(x - 1, y + 8, 0, 20, 203, 11);
        int fw = Math.round(196 * Math.max(0, Math.min(1, v)));
        l.blit(x + 2, y + 10, 0, v > 0.9f ? 42 : 34, fw, 6);
        l.fermer();
    }

    /** Plaque-bouton à la largeur voulue : état 0 normal, 1 survol, 2 actif (laque rouge), 3 inactif. */
    static void plaque(GuiGraphicsExtractor g, int x, int y, int w, int etat) {
        DaKit.coupeH(g, PLAQUES, x - 1, y - 1, w + 4, 0, etat * 20, 164, 20, 6, 164, 80);
    }

    /** Lot de plaques-boutons (une rangée = un seul élément GUI), à fermer avant le lot de textes de leurs libellés. */
    static LotBlit plaques(GuiGraphicsExtractor g) { return LotBlit.ouvrir(g, PLAQUES, 164, 80); }

    /** Équivalent de {@link #plaque(GuiGraphicsExtractor, int, int, int, int)} dans un lot de plaques. */
    static void plaque(LotBlit l, int x, int y, int w, int etat) {
        x -= 1;
        y -= 1;
        w = Math.min(w + 4, 164);
        l.blit(x, y, 0, etat * 20, w - 6, 20);
        l.blit(x + w - 6, y, 164 - 6, etat * 20, 6, 20);
    }

    /** Plaque-bouton + libellé ArcadePix ×0,5 centré. */
    static void plaque(GuiGraphicsExtractor g, Font f, int x, int y, int w, int h, String lib, boolean actif, boolean survol,
                       boolean ok) {
        LotBlit l = plaques(g);
        Txt t = new Txt(g, f);
        plaque(l, t, x, y, w, h, lib, actif, survol, ok);
        l.fermer();
        t.fermer();
    }

    /** Plaque-bouton dans un lot de plaques et son libellé dans un lot de textes (fermer les plaques d'abord). */
    static void plaque(LotBlit l, Txt t, int x, int y, int w, int h, String lib, boolean actif, boolean survol, boolean ok) {
        int etat = !ok ? 3 : actif ? 2 : survol ? 1 : 0;
        if (h >= 16) plaque(l, x, y + (h - 16) / 2, w, etat);
        else plaque(l, x, y - (16 - h) / 2, w, etat);
        int col = !ok ? DaKit.INACTIF : actif || survol ? DaKit.CREME : 0xFFD8CCB0;
        t.arcFin(DaKit.arcade(lib), x + w / 2f, y + h / 2f - 1.75f, col, 1, ok);
    }

    /** Réglette or : étiquette, valeur, rainure graduée, remplissage et curseur. {@code v} ∈ [0, 1]. */
    static void reglette(GuiGraphicsExtractor g, Font f, int x, int y, int w, float v, String lib, String valeur, boolean hot) {
        LotBlit l = reglettes(g);
        Txt t = new Txt(g, f);
        reglette(l, t, x, y, w, v, lib, valeur, hot);
        t.fermer();
        l.fermer();
    }

    /** Lot de réglettes (rainures, remplissages et curseurs d'une page = un seul élément GUI). */
    static LotBlit reglettes(GuiGraphicsExtractor g) { return LotBlit.ouvrir(g, REGLETTE, 404, 30); }

    /** Réglette dans un lot de réglettes ; étiquette et valeur dans un lot de textes. */
    static void reglette(LotBlit l, Txt t, int x, int y, int w, float v, String lib, String valeur, boolean hot) {
        t.etiq(etiq(t.f, lib), x, y + 3, DaKit.OR_ETIQ);
        t.inter8(DaKit.inter(valeur), x + w, y + 3, DaKit.OR_CLAIR, 2);
        int ry = y + 9;
        int wr = Math.min(w, 404);
        l.blit(x, ry, 0, 0, wr - 6, 10);
        l.blit(x + wr - 6, ry, 404 - 6, 0, 6, 10);
        v = Math.max(0, Math.min(1, v));
        int fw = Math.round((w - 4) * v);
        l.blit(x + 2, ry + 3, 2, 12, fw, 4);
        int kx = x + 2 + fw - 3;
        l.blit(kx, ry - 1, hot ? 8 : 0, 18, 7, 12);
    }

    /** Onglet pendu (plaque 16 de haut, largeur w ≤ 138) : 0 repos, 1 survol, 2 actif. */
    static void onglet(GuiGraphicsExtractor g, int x, int y, int w, int etat) {
        DaKit.coupeH(g, ONGLETS, x - 1, y - 1, w + 3, 0, etat * 20, 140, 20, 6, 140, 70);
    }

    /** Perche de bambou laqué (7 de haut, embouts de laiton) de largeur quelconque : bambou répété au milieu. */
    static void perche(GuiGraphicsExtractor g, int x, int y, int w) {
        LotBlit l = LotBlit.ouvrir(g, ONGLETS, 140, 70);
        l.blit(x, y, 0, 61, 6, 7);
        for (int xx = x + 6; xx < x + w - 6; xx += 128) l.blit(xx, y, 6, 61, Math.min(128, x + w - 6 - xx), 7);
        l.blit(x + w - 6, y, 134, 61, 6, 7);
        l.fermer();
    }

    /** Fil d'attache vertical (cordelette d'or, 1 GUI) : un pixel par rangée, le tout en un seul élément GUI. */
    static void fil(GuiGraphicsExtractor g, int x, int y0, int y1) {
        LotGui l = LotGui.ouvrir(g);
        fil(l, x, y0, y1);
        l.fermer();
    }

    static void fil(LotGui l, int x, int y0, int y1) {
        for (int y = y0; y < y1; y++) l.fill(x, y, x + 1, y + 1, (y & 1) == 0 ? 0xFFE8C27A : 0xFFC8A05A);
    }

    /** Sceau 秘 « réservé aux membres » (24 GUI) centré en (cx, cy) : vermillon (non-membre) ou laque et or (membre). */
    static void sceauAcces(GuiGraphicsExtractor g, int cx, int cy, boolean membre) {
        DaKit.blit(g, ACCES, cx - 14, cy - 14, membre ? 28 : 0, 0, 28, 28, 84, 28);
    }

    /** Cadenas 9×11 : fermé (vermillon) ou ouvert (or). */
    static void cadenas(GuiGraphicsExtractor g, int x, int y, boolean ouvert) {
        DaKit.blit(g, ACCES, x, y, ouvert ? 70 : 58, 0, 9, 11, 84, 28);
    }

    static LotBlit acces(GuiGraphicsExtractor g) { return LotBlit.ouvrir(g, ACCES, 84, 28); }

    static void cadenas(LotBlit l, int x, int y, boolean ouvert) { l.blit(x, y, ouvert ? 70 : 58, 0, 9, 11); }

    /** Feuille de registre (washi) de w×h ≤ 328×260 en (x, y), ombre comprise autour. */
    static void registre(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        DaKit.coupe(g, REGISTRE, x - 2, y - 2, w + 8, h + 8, 0, 0, 336, 268, 8, 16, 336, 268);
    }

    /** Petit bouton suspendu (設 réglages = 0, 記 journal = 1), cellule 26×38 posée en (x, 0). */
    static void bouton26(GuiGraphicsExtractor g, int x, int kanji, boolean survol) {
        DaKit.blit(g, REGLAGES, x, 0, (kanji * 2 + (survol ? 1 : 0)) * 26, 0, 26, 38, 104, 38);
    }

    /** Cheville − / + (12×11). */
    static void cheville(GuiGraphicsExtractor g, int x, int y, boolean plus, boolean hot) {
        DaKit.blit(g, CHEVILLES, x, y, (plus ? 26 : 0) + (hot ? 13 : 0), 0, 13, 12, 52, 12);
    }

    static LotBlit chevilles(GuiGraphicsExtractor g) { return LotBlit.ouvrir(g, CHEVILLES, 52, 12); }

    static void cheville(LotBlit l, int x, int y, boolean plus, boolean hot) {
        l.blit(x, y, (plus ? 26 : 0) + (hot ? 13 : 0), 0, 13, 12);
    }

    static LotBlit onglets(GuiGraphicsExtractor g) { return LotBlit.ouvrir(g, ONGLETS, 140, 70); }

    /** {@link #onglet} dans un lot (même texture que la perche). */
    static void onglet(LotBlit l, int x, int y, int w, int etat) {
        x -= 1;
        y -= 1;
        w = Math.min(w + 3, 140);
        l.blit(x, y, 0, etat * 20, w - 6, 20);
        l.blit(x + w - 6, y, 140 - 6, etat * 20, 6, 20);
    }

    /* ---------------------------------------------------------------- étiquettes et textes en lot (DA §6, règle 7) */

    private static final Map<String, DaKit.Etiq> ETIQS = new HashMap<>();

    /** Étiquette espacée (petites capitales Inter gras 7), découpée une fois par libellé. */
    static DaKit.Etiq etiq(Font f, String s) {
        DaKit.Etiq e = ETIQS.get(s);
        if (e == null) {
            if (ETIQS.size() > 512) ETIQS.clear();
            e = DaKit.etiq(f, s);
            ETIQS.put(s, e);
        }
        return e;
    }

    /** Équivalent de {@code DaKit.etiq(g, f, e, x, ymil, col)} en un seul élément GUI (au lieu d'un par lettre). */
    static float etiq(GuiGraphicsExtractor g, Font f, DaKit.Etiq e, float x, float ymil, int col) {
        Txt t = new Txt(g, f);
        float w = t.etiq(e, x, ymil, col);
        t.fermer();
        return w;
    }

    /**
     * Lot de textes ({@link LotGui.Textes}) aux métriques exactes de {@link DaKit} (calage sur la grille écran, Inter 7 /
     * 8 / 9, ArcadePix ×0,5 et ×1, étiquettes espacées) : tous les textes d'un lot ne font qu'un élément GUI. Rien de ce
     * qui est dessiné entre l'ouverture et la fermeture ne doit chevaucher ces textes (ils passent au-dessus).
     */
    static final class Txt {

        private final GuiGraphicsExtractor g;
        final Font f;
        private final LotGui.Textes t;

        Txt(GuiGraphicsExtractor g, Font f) {
            this.g = g;
            this.f = f;
            this.t = LotGui.textes(g);
        }

        /** = {@code DaKit.texteEchelle}. */
        void echelle(Component c, float x, float y, int col, float s) {
            g.pose().pushMatrix();
            g.pose().translate(DaKit.cale(x), DaKit.cale(y));
            g.pose().scale(s, s);
            t.texte(f, c, 0, 0, col, false);
            g.pose().popMatrix();
        }

        void echelle(FormattedCharSequence c, float x, float y, int col, float s) {
            g.pose().pushMatrix();
            g.pose().translate(DaKit.cale(x), DaKit.cale(y));
            g.pose().scale(s, s);
            t.texte(f, c, 0, 0, col, false);
            g.pose().popMatrix();
        }

        /** = {@code DaKit.etiq(g, f, e, x, ymil, col)}. */
        float etiq(DaKit.Etiq e, float x, float ymil, int col) {
            for (int i = 0; i < e.lettres().length; i++) echelle(e.lettres()[i], x + e.dx()[i], ymil - DaKit.INTER7_MIL, col, DaKit.S7);
            return e.w();
        }

        /** = {@code DaKit.arcFin}. */
        float arcFin(Component c, float x, float yHaut, int col, int ancre, boolean ombre) {
            float s = DaKit.facteurPetit(), px = DaKit.pxEcran();
            float w = f.width(c) * s;
            float x0 = ancre == 1 ? x - w / 2f : ancre == 2 ? x - w : x;
            if (ombre) echelle(c, x0 + px, yHaut + px, ombreDe(col), s);
            echelle(c, x0, yHaut, col, s);
            return w;
        }

        /** = {@code DaKit.arcX1}. */
        int arcX1(Component c, float x, float y, int col, int ancre, boolean ombre) {
            int w = f.width(c) - 1;
            int x0 = Math.round(ancre == 1 ? x - w / 2f : ancre == 2 ? x - w : x);
            int yy = Math.round(y);
            if (ombre) t.texte(f, c, x0 + 1, yy + 1, 0xFF000000 | 0x0A0406, false);
            t.texte(f, c, x0, yy, col, false);
            return w;
        }

        /** = {@code DaKit.inter7o}. */
        float inter7o(Component c, float x, float ymil, int col, int ancre) {
            float w = f.width(c) * DaKit.S7;
            float x0 = ancre == 1 ? x - w / 2f : ancre == 2 ? x - w : x;
            float px = DaKit.pxEcran() * Math.max(1, Math.round(DaKit.echelleGui() / 2f));
            echelle(c, x0 + px, ymil - DaKit.INTER7_MIL + px, DaKit.alpha(0xFF0A0608, ((col >>> 24) & 0xFF) / 255f * 0.85f), DaKit.S7);
            echelle(c, x0, ymil - DaKit.INTER7_MIL, col, DaKit.S7);
            return w;
        }

        /** = {@code p.dessiner(g, f, x, yHaut, col)}. */
        void paragraphe(DaKit.Paragraphe p, float x, float yHaut, int col) {
            for (int i = 0; i < p.lignes().size(); i++) echelle(p.lignes().get(i), x, yHaut + i * p.interligne(), col, p.echelle());
        }

        /** = {@code DaKit.inter7}. */
        float inter7(Component c, float x, float ymil, int col, int ancre) {
            float w = f.width(c) * DaKit.S7;
            float x0 = ancre == 1 ? x - w / 2f : ancre == 2 ? x - w : x;
            echelle(c, x0, ymil - DaKit.INTER7_MIL, col, DaKit.S7);
            return w;
        }

        /** = {@code DaKit.inter8}. */
        float inter8(Component c, float x, float ymil, int col, int ancre) {
            float w = f.width(c) * DaKit.S8;
            float x0 = ancre == 1 ? x - w / 2f : ancre == 2 ? x - w : x;
            echelle(c, x0, ymil - 3.5f, col, DaKit.S8);
            return w;
        }

        /** = {@code DaKit.inter9}. */
        int inter9(Component c, float x, float ymil, int col, int ancre) {
            int w = f.width(c);
            float x0 = ancre == 1 ? x - w / 2f : ancre == 2 ? x - w : x;
            echelle(c, x0, ymil - DaKit.INTER9_MIL, col, 1f);
            return w;
        }

        void fermer() { t.fermer(); }

        private static int ombreDe(int col) {
            return (col & 0xFF000000) | (((col >> 16) & 0xFF) / 4 << 16) | (((col >> 8) & 0xFF) / 4 << 8) | ((col & 0xFF) / 4);
        }
    }
}
