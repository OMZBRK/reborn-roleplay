package fr.reborn.hud.menu.character.perso;

import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.ui.LotBlit;
import fr.reborn.hud.ui.LotGui;
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
 * Textures de la sélection et de la création de personnage ({@code textures/gui/personnage/*}, générées par
 * {@code tools/gen_personnage_textures.py} d'après les maquettes validées) et outils de dessin : blits à échelle
 * entière (1 texel = 1 GUI), emblèmes à la résolution écran, halos, texte (ArcadePix ×1 / ×1,5 / ×0,5, Inter 7 à 16,
 * petites capitales). Aucun kanji décoratif (règle du 14/10, retirés de la création le 18/10).
 */
final class PersoArt {

    private PersoArt() {}

    /* ================================================================ palette */

    static final int OR = 0xFFD9A95E, OR_CLAIR = 0xFFF2D08A, OR_ETIQ = 0xFFA8844A, OR_ETEINT = 0xFF8E6A30;
    static final int CREME = 0xFFF5E9D0, DOUX = 0xFFC2B59A, ROUGE = 0xFFA0182B, SEP = 0xFF2D181C;
    static final int[] R_OR = {0xFF2E2210, 0xFF4A3518, 0xFF6A4E24, 0xFF8E6A30, 0xFFB08442, 0xFFD9A95E, 0xFFE8C27A,
        0xFFF2D59A, 0xFFFFF2C8};
    static final int LUCIOLE = 0xE2F07A, LUCIOLE_COEUR = 0xFCFFD8;
    /** Rangée 0 du ciel (bandeau du haut quand l'écran dépasse 540 GUI) et eau du bas. */
    static final int[] CIEL_HAUT = {0xFF07060F, 0xFF05060E, 0xFF05070D, 0xFF06060C, 0xFF07060A, 0xFF06070C};
    static final int[] EAU_BAS = {0xFF07060C, 0xFF07060A, 0xFF05080C, 0xFF07070C, 0xFF08060A, 0xFF06070A};

    /* ================================================================ textures */

    static Identifier tex(String n) {
        return Identifier.fromNamespaceAndPath("reborn", "textures/gui/personnage/" + n + ".png");
    }

    static final String[] VILLAGES = {"konoha", "suna", "kiri", "kumo", "iwa", "ame"};
    static final Identifier[] FONDS = new Identifier[6], ROSEAUX = new Identifier[6];

    static {
        for (int i = 0; i < 6; i++) {
            FONDS[i] = tex("fond_" + VILLAGES[i]);
            ROSEAUX[i] = tex("roseaux_" + VILLAGES[i]);
        }
    }

    static final Identifier PIERRES_LIT = tex("pierres_lit"), PIERRES = tex("pierres"), LOTUS = tex("lotus"),
        OMBRE = tex("ombre"), LUM_CLE = tex("lum_cle"), LUM_FLAQUE = tex("lum_flaque"), HALO = tex("halo"),
        BRUME_AVANT = tex("brume_avant"),
        SILHOUETTE = tex("silhouette"), PLAQUE_TITRE = tex("plaque_titre"), PROGRESSION = tex("progression"),
        SCEAUX = tex("sceaux"), CHIP = tex("chip"), PERCHE = tex("perche"), PLAQUE_NOM = tex("plaque_nom"),
        BOUTONS = tex("boutons"), FLECHES = tex("fleches"), CURSEURS = tex("curseurs"), TOISE = tex("toise"),
        ORNEMENTS = tex("ornements"), CABOCHON = tex("cabochon"), CARTOUCHE = tex("cartouche");

    /** Pierres éclairées (pierres_lit.png) : colonnes. */
    static final int LIT_CREA = 0, LIT_DAIS = 1, LIT_SEL = 2, LIT_COTE = 3;
    /** Pierres éteintes (pierres.png) : cellules. */
    static final int P_VIDE = 0, P_REN = 1, P_15 = 2, P_15B = 3, P_11 = 4, P_10 = 5, P_24 = 6, P_14 = 7, P_16 = 8, P_9 = 9;

    /** Index du clan dans les atlas d'emblèmes (lus au lancement dans gui/clans/, cf. {@link PersoEmblemes}). */
    static int clanIndex(String slug) {
        return PersoEmblemes.clan(slug);
    }

    /** Panneau : (largeur, hauteur nominale) ; texture (w+8)×(16+h+28), texel (4, 16) = coin. */
    record Panneau(Identifier id, int w, int h) {
        int texW() { return w + 8; }
        int texH() { return 16 + h + 28; }
    }

    static final Panneau P_VILLAGE = new Panneau(tex("panneau_village"), 240, 300),
        P_CLAN = new Panneau(tex("panneau_clan"), 240, 330), P_SEXE = new Panneau(tex("panneau_sexe"), 240, 232),
        P_AGE = new Panneau(tex("panneau_age"), 240, 184), P_TAILLE = new Panneau(tex("panneau_taille"), 240, 172),
        P_APPARENCE = new Panneau(tex("panneau_apparence"), 286, 352),
        P_TENUE = new Panneau(tex("panneau_tenue"), 276, 352), P_DOSSIER = new Panneau(tex("panneau_dossier"), 240, 254),
        P_REGISTRE = new Panneau(tex("panneau_registre"), 240, 214),
        P_REGISTRE_RECAP = new Panneau(tex("panneau_registre_recap"), 240, 262);

    /** Tuile : taille et nombre d'états (normal, survol, actif, verrou) ; cellules (w+4)×(h+5), tuile en (1, 1). */
    record Tuile(Identifier id, int w, int h, int etats) {}

    static final Tuile T_VILLAGE = new Tuile(tex("tuiles_village"), 220, 38, 3),
        T_CLAN = new Tuile(tex("tuiles_clan"), 70, 64, 4), T_SEXE = new Tuile(tex("tuiles_sexe"), 104, 168, 3),
        T_AGE = new Tuile(tex("tuiles_age"), 68, 34, 3), T_CHEVEUX = new Tuile(tex("tuiles_cheveux"), 60, 46, 3),
        T_TENUE = new Tuile(tex("tuiles_tenue"), 80, 92, 3), T_VUE = new Tuile(tex("tuiles_vue"), 66, 84, 3);
    static final int NORMAL = 0, SURVOL = 1, ACTIF = 2, VERROU = 3;

    /** Boutons (boutons.png) : rangées de 24, cellule 104 de large, états normal, survol, inactif. */
    static final int B_RETOUR = 0, B_SUIVANT = 1, B_ENTRER = 2, B_CREER = 3, B_OUI = 4, B_HASARD = 5, B_LARGE = 6,
        B_FINE = 7;
    static final int[] B_W = {57, 71, 100, 100, 93, 69, 70, 70};
    static final String[] B_LIB = {"RETOUR", "SUIVANT", "ENTRER EN JEU", "CREER", "OUI, C'EST MOI", "AU HASARD",
        "LARGE", "FINE"};
    static final boolean[] B_ROUGE = {false, true, true, true, true, false, false, false};

    /* ================================================================ blits */

    static void blit(GuiGraphicsExtractor g, Identifier id, int x, int y, float u, float v, int w, int h, int tw, int th) {
        if (w <= 0 || h <= 0) return;
        g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, tw, th);
    }

    static void blit(GuiGraphicsExtractor g, Identifier id, int x, int y, float u, float v, int w, int h, int tw, int th,
                     int argb) {
        if (w <= 0 || h <= 0 || (argb >>> 24) == 0) return;
        g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, tw, th, argb);
    }

    /** Région (u, v, rw, rh) étirée en w×h GUI. */
    static void etire(GuiGraphicsExtractor g, Identifier id, int x, int y, int w, int h, float u, float v, int rw, int rh,
                      int tw, int th, int argb) {
        if (w <= 0 || h <= 0 || (argb >>> 24) == 0) return;
        g.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, rw, rh, tw, th, argb);
    }

    /** Panneau à sa place (coin (x, y)) ; plus court que nominal : coupé au milieu (bande sans ornement). */
    static void panneau(GuiGraphicsExtractor g, Panneau p, int x, int y, int hVoulue) {
        int tw = p.texW(), th = p.texH();
        if (hVoulue >= p.h()) {
            blit(g, p.id(), x - 4, y - 16, 0, 0, tw, th, tw, th);
            return;
        }
        int haut = 16 + 60;
        int coupe = p.h() - Math.max(90, hVoulue);
        blit(g, p.id(), x - 4, y - 16, 0, 0, tw, haut, tw, th);
        int vBas = haut + coupe;
        blit(g, p.id(), x - 4, y - 16 + haut, 0, vBas, tw, th - vBas, tw, th);
    }

    /** Tuile w×h nominale à l'état e ; hVoulue plus petite : coupée au milieu. */
    static void tuile(GuiGraphicsExtractor g, Tuile t, int e, int x, int y, int hVoulue) {
        int cw = t.w() + 4, ch = t.h() + 5, tw = cw * t.etats();
        float u = Math.min(e, t.etats() - 1) * cw;
        if (hVoulue >= t.h()) {
            blit(g, t.id(), x - 1, y - 1, u, 0, cw, ch, tw, ch);
            return;
        }
        int haut = Math.max(6, hVoulue / 2);
        blit(g, t.id(), x - 1, y - 1, u, 0, cw, haut + 1, tw, ch);
        int bas = hVoulue - haut;
        blit(g, t.id(), x - 1, y + haut, u, ch - bas - 4, cw, bas + 4, tw, ch);
    }

    /**
     * Bouton-plaque (sans kanji depuis le 18/10 : plaque nue) + libellé ArcadePix ×0,5 centré ; etat 0 normal,
     * 1 survol, 2 inactif.
     */
    static void bouton(GuiGraphicsExtractor g, Font f, int b, int etat, int x, int y, String libelle) {
        blit(g, BOUTONS, x - 1, y - 1, etat * 104, b * 24, B_W[b] + 4, 24, 312, 192);
        int col;
        if (b == B_LARGE || b == B_FINE) col = etat == 2 ? CREME : etat == 1 ? 0xFFD8CCB0 : 0xFFB8A47E;
        else if (etat == 2) col = 0xFF6A5C52;
        else col = (B_ROUGE[b] || etat == 1) ? CREME : 0xFFD8CCB0;
        String lib = libelle == null ? B_LIB[b] : libelle;
        float cx = x + B_W[b] / 2f;
        if (Minecraft.getInstance().getWindow().getGuiScale() == 1) {
            inter(g, f, interGras(sansAccents(lib)), cx, y + 10, 7, col, 1);
        } else {
            arcFin(g, f, arcade(lib), cx, y + 10 - 1.75f, col, 1, etat != 2);
        }
    }

    static boolean dans(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    /* ---- emblèmes (villages 0-5 puis clans) à la résolution écran ---- */

    static int idxVillage(int v) { return Math.max(0, Math.min(5, v)); }

    static int idxClan(String slug) { return 6 + clanIndex(slug); }

    /** Emblème de l'atlas (index), taille GUI t, coin (x, y), teinte argb. */
    static void embleme(GuiGraphicsExtractor g, int index, float x, float y, int t, int argb) {
        int k = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        int px = t * k;
        int row, s;
        if (px >= 60) { row = 0; s = 64; }
        else if (px >= 50) { row = 120; s = 56; }
        else if (px >= 30) { row = 64; s = 32; }
        else { row = 96; s = 24; }
        g.pose().pushMatrix();
        g.pose().translate(cale(x), cale(y));
        etire(g, PersoEmblemes.atlas(), 0, 0, t, t, index * 64f, row, s, s, PersoEmblemes.atlasW(), 176, argb);
        g.pose().popMatrix();
    }

    static void emblemeCentre(GuiGraphicsExtractor g, int index, float cx, float cy, int t, int argb) {
        embleme(g, index, cx - t / 2f, cy - t / 2f, t, argb);
    }

    /**
     * Sceau vermillon du registre (récapitulatif), penché, centré en (cx, cy) : l'emblème d'index {@code index}
     * (village ou clan, comme {@link #embleme}) réservé dans le tampon ; {@link PersoEmblemes#sceauKanji()} = l'ancien 認.
     */
    static void sceauRegistre(GuiGraphicsExtractor g, int index, int cx, int cy) {
        int t = PersoEmblemes.SCEAU_GUI, r = PersoEmblemes.SCEAU_TEX;
        etire(g, PersoEmblemes.sceaux(), cx - t / 2, cy - t / 2, t, t, index * r, 0, r, r, PersoEmblemes.sceauxW(), r,
            0xFFFFFFFF);
    }

    /* ---- ornements ---- */

    static void losange(GuiGraphicsExtractor g, int x, int y) { blit(g, ORNEMENTS, x, y, 0, 0, 3, 3, 48, 10); }

    static void cadenas(GuiGraphicsExtractor g, int x, int y) { blit(g, ORNEMENTS, x, y, 4, 0, 7, 8, 48, 10); }

    static void chevron(GuiGraphicsExtractor g, int cx, int y) { blit(g, ORNEMENTS, cx - 2, y, 12, 0, 5, 5, 48, 10); }

    static void pointe(GuiGraphicsExtractor g, int cx, int y) { blit(g, ORNEMENTS, cx - 2, y, 18, 0, 5, 3, 48, 10); }

    /* ---- halos (ovales nets, halo.png étiré) ---- */

    static void ovale(GuiGraphicsExtractor g, float cx, float cy, float rx, float ry, int rgb, float alpha) {
        LotBlit l = halos(g);
        ovale(l, cx, cy, rx, ry, rgb, alpha);
        l.fermer();
    }

    /** Lot de halos (halo.png) : tous les ovales d'un lot ne font qu'un élément GUI (DA §6, règle 7). */
    static LotBlit halos(GuiGraphicsExtractor g) { return LotBlit.ouvrir(g, HALO, 64, 64); }

    /** Ovale net (halo.png étiré en dw × dh, coin calé au GUI entier) dans un lot de halos. */
    static void ovale(LotBlit l, float cx, float cy, float rx, float ry, int rgb, float alpha) {
        if (alpha <= 0.004f || rx < 0.5f || ry < 0.5f) return;
        int dw = Math.max(1, Math.round(rx * 2)), dh = Math.max(1, Math.round(ry * 2));
        l.etire(Math.round(cx - dw / 2f), Math.round(cy - dh / 2f), dw, dh, 0f, 0f, 64, 64, argb(rgb, alpha));
    }

    /** halo_px de la maquette : 2 ou 3 ovales concentriques nets, en un seul élément GUI. */
    static void halo(GuiGraphicsExtractor g, float cx, float cy, float r, float ryK, int rgb, float force, int niveaux) {
        if (force <= 0.002f) return;
        LotBlit l = halos(g);
        halo(l, cx, cy, r, ryK, rgb, force, niveaux);
        l.fermer();
    }

    static void halo(LotBlit l, float cx, float cy, float r, float ryK, int rgb, float force, int niveaux) {
        if (force <= 0.002f) return;
        float[] parts = niveaux >= 3 ? new float[]{0.27f, 0.53f, 1f} : niveaux == 2 ? new float[]{0.4f, 1f} : new float[]{1f};
        int n = parts.length;
        float prev = 0f;
        for (int i = 0; i < n; i++) {
            float k = (n - i) / (float) n;
            float a = (parts[i] - prev) * force;
            prev = parts[i];
            ovale(l, cx, cy, r * k, r * k * ryK, rgb, a);
        }
    }

    /** Masque de lumière blanc teint, centré. */
    static void lumiere(GuiGraphicsExtractor g, Identifier id, int w, int h, int cx, int cy, int rgb, float force) {
        blit(g, id, cx - w / 2, cy - h / 2, 0, 0, w, h, w, h, argb(desat(rgb, 0.8f), force));
    }

    /* ================================================================ couleurs */

    static int argb(int rgb, float a) {
        int al = Math.round(255 * Math.max(0f, Math.min(1f, a)));
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    static int alpha(int argb, float f) {
        int al = Math.round(((argb >>> 24) & 0xFF) * Math.max(0f, Math.min(1f, f)));
        return al << 24 | (argb & 0xFFFFFF);
    }

    static int desat(int rgb, float k) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        float m = (r + g + b) / 3f;
        return (Math.round(r * k + m * (1 - k)) << 16) | (Math.round(g * k + m * (1 - k)) << 8) | Math.round(b * k + m * (1 - k));
    }

    static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int gg = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (r << 16) | (gg << 8) | bl;
    }

    static int opaque(int rgb) { return 0xFF000000 | (rgb & 0xFFFFFF); }

    /* ================================================================ texte */

    static final Style INTER_GRAS = Style.EMPTY.withFont(new FontDescription.Resource(RebornFont.BOLD));

    static MutableComponent arcade(String s) { return RebornFont.arcade(sansAccents(s)); }

    static MutableComponent inter(String s) { return RebornFont.body(s); }

    static MutableComponent interGras(String s) { return Component.literal(s).setStyle(INTER_GRAS); }

    static String sansAccents(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
            .replace('—', '-').replace('’', '\'').replace('–', '-').toUpperCase(Locale.ROOT);
    }

    static float facteurPetit() {
        int k = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        if (k == 1) return 1f;                      // échelle 1 (1366×768) : ×1 net plutôt que ×0,75 illisible
        return Math.max(1, Math.round(k / 2f)) / (float) k;
    }

    static float pxEcran() { return 1f / Math.max(1, Minecraft.getInstance().getWindow().getGuiScale()); }

    static float cale(float v) {
        int k = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        return Math.round(v * k) / (float) k;
    }

    static void texteEchelle(GuiGraphicsExtractor g, Font f, Component c, float x, float y, int col, float s) {
        g.pose().pushMatrix();
        g.pose().translate(cale(x), cale(y));
        g.pose().scale(s, s);
        g.text(f, c, 0, 0, col, false);
        g.pose().popMatrix();
    }

    /** {@link #texteEchelle(GuiGraphicsExtractor, Font, Component, float, float, int, float)} dans un lot de textes. */
    static void texteEchelle(LotGui.Textes t, GuiGraphicsExtractor g, Font f, Component c, float x, float y, int col, float s) {
        g.pose().pushMatrix();
        g.pose().translate(cale(x), cale(y));
        g.pose().scale(s, s);
        t.texte(f, c, 0, 0, col, false);
        g.pose().popMatrix();
    }

    static int ombreDe(int col) {
        return (col & 0xFF000000) | (((col >> 16) & 0xFF) / 4 << 16) | (((col >> 8) & 0xFF) / 4 << 8) | ((col & 0xFF) / 4);
    }

    /** ArcadePix ×0,5 (haut des capitales en yHaut) ; ancre 0 gauche, 1 centre, 2 droite. Renvoie la largeur. */
    static float arcFin(GuiGraphicsExtractor g, Font f, Component c, float x, float yHaut, int col, int ancre, boolean ombre) {
        LotGui.Textes t = LotGui.textes(g);                 // texte + ombre : un seul élément GUI
        float w = arcFin(t, g, f, c, x, yHaut, col, ancre, ombre);
        t.fermer();
        return w;
    }

    static float arcFin(LotGui.Textes t, GuiGraphicsExtractor g, Font f, Component c, float x, float yHaut, int col, int ancre,
                        boolean ombre) {
        float s = facteurPetit(), px = pxEcran();
        float w = f.width(c) * s;
        float x0 = ancre == 1 ? x - w / 2f : ancre == 2 ? x - w : x;
        if (ombre) texteEchelle(t, g, f, c, x0 + px, yHaut + px, ombreDe(col), s);
        texteEchelle(t, g, f, c, x0, yHaut, col, s);
        return w;
    }

    /** ArcadePix à l'échelle s (1 = capitales de 7 GUI) ; ombre d'1 GUI × s/… ; renvoie la largeur. */
    static float arcadeS(GuiGraphicsExtractor g, Font f, Component c, float x, float yHaut, int col, float s, int ancre,
                         boolean ombre) {
        float w = f.width(c) * s;
        float x0 = ancre == 1 ? x - w / 2f : ancre == 2 ? x - w : x;
        LotGui.Textes t = LotGui.textes(g);                 // texte + ombre : un seul élément GUI
        if (ombre) texteEchelle(t, g, f, c, x0 + 1, yHaut + 1, ombreDe(col), s);
        texteEchelle(t, g, f, c, x0, yHaut, col, s);
        t.fermer();
        return w;
    }

    /** Inter à la taille t (9 = corps), milieu des capitales en ymil ; renvoie la largeur GUI. */
    static float inter(GuiGraphicsExtractor g, Font f, Component c, float x, float ymil, float t, int col, int ancre) {
        float s = t / 9f;
        float w = f.width(c) * s;
        float x0 = ancre == 1 ? x - w / 2f : ancre == 2 ? x - w : x;
        texteEchelle(g, f, c, x0, ymil - 4f * s, col, s);
        return w;
    }

    /** {@link #inter(GuiGraphicsExtractor, Font, Component, float, float, float, int, int)} dans un lot de textes. */
    static float inter(LotGui.Textes lot, GuiGraphicsExtractor g, Font f, Component c, float x, float ymil, float t, int col,
                       int ancre) {
        float s = t / 9f;
        float w = f.width(c) * s;
        float x0 = ancre == 1 ? x - w / 2f : ancre == 2 ? x - w : x;
        texteEchelle(lot, g, f, c, x0, ymil - 4f * s, col, s);
        return w;
    }

    static float largeur(Font f, Component c, float t) { return f.width(c) * t / 9f; }

    /** Étiquette petites capitales Inter gras 7 espacées de 0,6 GUI, en un seul élément GUI ; renvoie la largeur. */
    static float etiq(GuiGraphicsExtractor g, Font f, String s, float x, float ymil, int col) {
        LotGui.Textes lot = LotGui.textes(g);
        float w = etiq(lot, g, f, s, x, ymil, col);
        lot.fermer();
        return w;
    }

    /** {@link #etiq(GuiGraphicsExtractor, Font, String, float, float, int)} dans un lot de textes (une lettre = un texte). */
    static float etiq(LotGui.Textes lot, GuiGraphicsExtractor g, Font f, String s, float x, float ymil, int col) {
        String t = sansAccents(s);
        float xx = x;
        float k = 7f / 9f;
        for (int i = 0; i < t.length(); i++) {
            Component c = interGras(String.valueOf(t.charAt(i)));
            texteEchelle(lot, g, f, c, xx, ymil - 3f, col, k);
            xx += f.width(c) * k + 0.6f;
        }
        return Math.max(0, xx - x - 0.6f);
    }

    /* ================================================================ aides (cabochons) */

    static int cabochon(GuiGraphicsExtractor g, Font f, String t, int x, float ymil) {
        Component c = arcade(t);
        int w = (int) Math.ceil(f.width(c) * facteurPetit()) + 7;
        int y = Math.round(ymil - 5);
        LotBlit l = LotBlit.ouvrir(g, CABOCHON, 42, 12);   // les morceaux du cabochon : un seul élément GUI
        l.blit(x, y, 0, 0, 4, 12);
        int xx = x + 4, fin = x + w - 4;
        while (xx < fin) {
            int ww = Math.min(16, fin - xx);
            l.blit(xx, y, 12, 0, ww, 12);
            xx += ww;
        }
        l.blit(fin, y, 36, 0, 6, 12);
        l.fermer();
        arcFin(g, f, c, x + w / 2f, ymil - 1.75f, OR_CLAIR, 1, false);
        return w;
    }

    /** Aides en bas à droite : touche en cabochon puis action Inter 8 crème à 60 %. */
    static void aides(GuiGraphicsExtractor g, Font f, String[][] items, int xDroite, float y) {
        float total = 0;
        float[] tw = new float[items.length], aw = new float[items.length];
        for (int i = 0; i < items.length; i++) {
            tw[i] = (int) Math.ceil(f.width(arcade(items[i][0])) * facteurPetit()) + 7;
            aw[i] = largeur(f, inter(items[i][1]), 8);
            total += tw[i] + 4 + aw[i] + (i < items.length - 1 ? 10 : 0);
        }
        float x = xDroite - total;
        for (int i = 0; i < items.length; i++) {
            cabochon(g, f, items[i][0], Math.round(x), y);
            x += tw[i];
            inter(g, f, inter(items[i][1]), x + 4, y, 8, 0x99F5E9D0, 0);
            x += 4 + aw[i];
            if (i < items.length - 1) {
                g.fill(Math.round(x + 5), Math.round(y), Math.round(x + 6), Math.round(y) + 1, OR_ETEINT);
                x += 10;
            }
        }
    }
}
