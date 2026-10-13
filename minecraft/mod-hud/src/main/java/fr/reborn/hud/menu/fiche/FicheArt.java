package fr.reborn.hud.menu.fiche;

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
 * Textures de la fiche ({@code textures/gui/fiche/*}, générées par {@code tools/gen_fiche_textures.py} d'après la
 * maquette finale tour 4) et outils de dessin partagés : blits à échelle entière (1 texel = 1 GUI), tranches, coupe
 * des panneaux, et texte (ArcadePix ×1, ArcadePix ×0,5, petites capitales Inter, Inter 9 / 8 / 7, kanji 16 GUI).
 *
 * <p>Les tailles sont celles du CONTRAT de textures (en-tête du générateur) : une texture absente donne un damier
 * violet, sans danger.
 */
final class FicheArt {

    private FicheArt() {}

    /* ================================================================ palette */

    static final int FOND = 0xFF0A0608;
    static final int OR = 0xFFD9A95E, OR_CLAIR = 0xFFF2D08A, OR_MOYEN = 0xFFC8A05A, OR_ETEINT = 0xFF8E6A30;
    static final int OR_ETIQ = 0xFFA8844A;
    static final int BISEAU_CLAIR = 0xFFF0C987;
    static final int CREME = 0xFFF5E9D0, CREME_VAL = 0xFFE8DCC0, CREME_KJ = 0xFFF2E6CC, DOUX = 0xFFC2B59A,
        ROUGE = 0xFFA0182B;
    static final int SEP = 0xFF2D181C, ONGLET_REPOS = 0xFFA88C5E;
    static final int INACTIF = 0xFF6A5C52, BOUTON_TXT = 0xFFD8CCB0, NEUTRE_TXT = 0xFFB8A47E;
    static final int[] R_OR = {0xFF2E2210, 0xFF4A3518, 0xFF6A4E24, 0xFF8E6A30, 0xFFB08442, 0xFFD9A95E, 0xFFE8C27A,
        0xFFF2D59A, 0xFFFFF2C8};
    static final int KONOHA = 0xF2A548, HITODAMA = 0x9AA8FF, COEUR_CHAUD = 0xFFBE6E;
    /** Rangée 0 du ciel des fonds (bandeau du haut quand l'écran dépasse 540 GUI). */
    static final int CIEL_HAUT = 0xFF0B0812;

    /* ================================================================ textures */

    static Identifier tex(String n) {
        return Identifier.fromNamespaceAndPath("reborn", "textures/gui/fiche/" + n + ".png");
    }

    static final Identifier FOND_IDENTITE = tex("fond_identite"), FOND_ATTRIBUTS = tex("fond_attributs"),
        FOND_TECHNIQUES = tex("fond_techniques"), FOND_ETAT = tex("fond_etat");
    static final int FOND_W = 1280, FOND_H = 540;
    static final Identifier VIGNETTE_JEU = tex("vignette_jeu");

    /** Masque de lumière blanc (l'alpha porte le niveau), teint au blit, centré sur (cx, cy). */
    record Masque(Identifier id, int w, int h) {}

    static final Masque LUM_VILLAGE = new Masque(tex("lum_village"), 342, 362);
    static final Masque LUM_COEUR = new Masque(tex("lum_coeur"), 162, 202);
    static final Masque LUM_FLAQUE_L = new Masque(tex("lum_flaque_l"), 302, 62);
    static final Masque LUM_BUSTE = new Masque(tex("lum_buste"), 322, 322);
    static final Masque LUM_FLAQUE_M = new Masque(tex("lum_flaque_m"), 302, 62);
    static final Masque LUM_NUIT = new Masque(tex("lum_nuit"), 302, 322);
    static final Masque LUM_FLAQUE_ETAT = new Masque(tex("lum_flaque_etat"), 302, 62);

    static final Identifier REFLET_SOL = tex("reflet_sol");
    static final int REFLET_W = 7, REFLET_H = 90;
    static final Identifier HALO = tex("halo");
    static final Identifier ENTETE = tex("entete");
    static final Identifier ONGLETS = tex("onglets");
    static final Identifier FERMER = tex("fermer");
    static final Identifier PERCHE = tex("perche");

    /** Largeur commune des planches (maquette : 240, symétriques autour du centre). */
    static final int PW = 240;

    /** Panneau laqué complet : texture (w+8)×(84+h+28), texel (4, 84) = coin haut gauche de la planche. */
    record Panneau(Identifier id, int w, int h) {
        int texW() { return w + 8; }
        int texH() { return 84 + h + 28; }
    }

    static final Panneau P_REGISTRE = new Panneau(tex("panneau_registre"), PW, 262);
    static final Panneau P_LIGNEE = new Panneau(tex("panneau_lignee"), PW, 244);
    static final Panneau P_DETAIL = new Panneau(tex("panneau_detail"), PW, 278);
    static final Panneau P_TECHNIQUES = new Panneau(tex("panneau_techniques"), PW, 336);
    static final Panneau P_CORPS = new Panneau(tex("panneau_corps"), PW, 184);
    static final Panneau P_RESSENS = new Panneau(tex("panneau_ressens"), PW, 184);
    static final Panneau P_RESSENS_GRAND = new Panneau(tex("panneau_ressens_grand"), PW, 272);
    static final Identifier LISTE_BAS = tex("liste_bas");

    static final Identifier INFOBULLE = tex("infobulle");
    static final Identifier BOUTONS = tex("boutons");
    static final Identifier BARRE = tex("barre");
    static final Identifier LANTERNES_S = tex("lanternes_s"), LANTERNES_L = tex("lanternes_l");
    static final Identifier FLAMMES_S = tex("flammes_s"), FLAMMES_L = tex("flammes_l");
    static final Identifier LANTERNE_CONTOUR = tex("lanterne_l_contour");
    static final Identifier HITODAMA_TEX = tex("hitodama");
    static final Identifier OMBRE_PIEDS = tex("ombre_pieds");
    static final Identifier EMA = tex("ema");
    static final Identifier CHEVILLES = tex("chevilles");
    static final Identifier MAT = tex("mat");
    static final Identifier SEUIL = tex("seuil");
    static final Identifier ORNEMENTS = tex("ornements");
    static final Identifier PLAQUES = tex("plaques");
    static final Identifier LIGNE_CHOISIE = tex("ligne_choisie");
    static final Identifier CARTE_BLESSURE = tex("carte_blessure");
    static final Identifier MECHES = tex("meches");
    static final Identifier KUMIHIMO = tex("kumihimo");
    static final Identifier CABOCHON = tex("cabochon");
    static final Identifier HANKO = tex("hanko");

    /** Crans d'éclat pré-dessinés des lanternes (colonnes des atlas lanternes_s / lanternes_l). */
    static final float[] CRANS = {0.45f, 0.70f, 0.80f, 0.875f, 0.94f, 1.00f};

    static int cran(float l) {
        int best = 0;
        float d = Float.MAX_VALUE;
        for (int i = 0; i < CRANS.length; i++) {
            float e = Math.abs(CRANS[i] - l);
            if (e < d) { d = e; best = i; }
        }
        return best;
    }

    /* ---- ornements.png : cellules de 8×8 ---- */
    static final int ORN_ANNEAU = 0, ORN_PERLE = 1, ORN_PASTILLE = 2, ORN_PETIT_ANNEAU = 3, ORN_LOSANGE = 4,
        ORN_TAMPON = 5, ORN_TAMPON_VIDE = 6, ORN_NOEUD = 7;

    /** Ornement centré sur (cx, cy) (anneau, perle, petit anneau : objet au texel (3,3)). */
    static void ornementCentre(GuiGraphicsExtractor ctx, int cell, int cx, int cy) {
        blit(ctx, ORNEMENTS, cx - 3, cy - 3, cell * 8, 0, 8, 8, 80, 8);
    }

    /** Ornement calé en haut à gauche (pastille 5×5, losange 3×3, tampons 5×5, nœud 3×3). */
    static void ornementCoin(GuiGraphicsExtractor ctx, int cell, int x, int y) {
        int s = switch (cell) {
            case ORN_PASTILLE, ORN_TAMPON, ORN_TAMPON_VIDE -> 5;
            default -> 3;
        };
        blit(ctx, ORNEMENTS, x, y, cell * 8, 0, s, s, 80, 8);
    }

    /** Tampon de palier (losange 5×5) centré verticalement sur yc, calé à gauche en x. */
    static void tampon(GuiGraphicsExtractor ctx, int x, int yc, boolean plein) {
        ornementCoin(ctx, plein ? ORN_TAMPON : ORN_TAMPON_VIDE, x, yc - 2);
    }

    /** Soucoupe de laque 16×3 (+ombre) centrée en x, posée en y. */
    static void soucoupe(GuiGraphicsExtractor ctx, int cx, int y) {
        blit(ctx, ORNEMENTS, cx - 8, y, 64, 0, 16, 5, 80, 8);
    }

    /* ---- plaques.png ---- */
    static void plaqueMudra(GuiGraphicsExtractor ctx, int x, int y) { blit(ctx, PLAQUES, x, y, 0, 0, 23, 23, 64, 24); }

    static void plaqueMerite(GuiGraphicsExtractor ctx, int x, int y) { blit(ctx, PLAQUES, x, y, 24, 0, 21, 21, 64, 24); }

    static void plaqueMaitre(GuiGraphicsExtractor ctx, int x, int y) { blit(ctx, PLAQUES, x, y, 46, 0, 13, 13, 64, 24); }

    /* ---- hanko.png : sceaux 18×18 (cellules 20×20) ---- */
    static final int H_CLANS = 0, H_NATURES = 1, H_SERVICE = 2, H_STATS = 3, H_BLESSURES = 4;
    static final int H_SERVICE_VIERGE = 11, H_RANG_D = 6, H_BASE = 11;

    /** Sceau de l'atlas centré sur (cx, cy) (le sceau de 18 GUI occupe cx−9 … cx+8). */
    static void hanko(GuiGraphicsExtractor ctx, int col, int row, int cx, int cy) {
        blit(ctx, HANKO, cx - 9, cy - 9, col * 20, row * 20, 20, 20, 240, 100);
    }

    /** Sceau de rang (D, C, B, A, S, sinon 基) centré. */
    static void sceauRang(GuiGraphicsExtractor ctx, String lettre, int cx, int cy) {
        int col = switch (lettre == null ? "" : lettre) {
            case "D" -> 6;
            case "C" -> 7;
            case "B" -> 8;
            case "A" -> 9;
            case "S" -> 10;
            default -> H_BASE;
        };
        hanko(ctx, col, H_STATS, cx, cy);
    }

    static boolean rangDeBase(String lettre) {
        return lettre == null || !("DCBAS".contains(lettre) && lettre.length() == 1);
    }

    /* ---- emblemes@k, petits@k : atlas rendus à la résolution écran ---- */

    static int atlasK() {
        int s = Minecraft.getInstance().getWindow().getGuiScale();
        return s % 3 == 0 ? 3 : s % 2 == 0 ? 2 : 1;
    }

    private static Identifier atlas(String nom, int k) { return tex(nom + "_" + k); }

    /** Emblème de village t×t (t = 12 ou 16) teinté (konoha, suna, kiri, kumo, iwa, ame). */
    static void embleme(GuiGraphicsExtractor ctx, int idx, int x, int y, int t, int argb) {
        if (idx < 0 || idx > 5) return;
        int k = atlasK();
        String nom = t >= 16 ? "emblemes16" : "emblemes";
        int s = t >= 16 ? 16 : 12;
        ctx.blit(RenderPipelines.GUI_TEXTURED, atlas(nom, k), x, y, idx * s * (float) k, 0f, s, s,
            s * k, s * k, 6 * s * k, s * k, argb);
    }

    static final int PETIT_PERLE = 0, PETIT_PERLE_VIDE = 1, PETIT_POINT = 2;

    /** Petit élément de 4 GUI centré sur (cx, cy). */
    static void petit(GuiGraphicsExtractor ctx, int cell, float cx, float cy) {
        int k = atlasK();
        ctx.pose().pushMatrix();
        ctx.pose().translate(cx - 2f, cy - 2f);
        ctx.blit(RenderPipelines.GUI_TEXTURED, atlas("petits", k), 0, 0, cell * 4f * k, 0f, 4, 4,
            4 * k, 4 * k, 12 * k, 4 * k);
        ctx.pose().popMatrix();
    }

    /* ================================================================ blits */

    static void blit(GuiGraphicsExtractor ctx, Identifier id, int x, int y, float u, float v, int w, int h, int tw, int th) {
        if (w <= 0 || h <= 0) return;
        ctx.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, tw, th);
    }

    static void blit(GuiGraphicsExtractor ctx, Identifier id, int x, int y, float u, float v, int w, int h, int tw, int th,
                     int argb) {
        if (w <= 0 || h <= 0 || (argb >>> 24) == 0) return;
        ctx.blit(RenderPipelines.GUI_TEXTURED, id, x, y, u, v, w, h, tw, th, argb);
    }

    /** Étirement (réservé aux textures lisses : halo, vignette). */
    static void etire(GuiGraphicsExtractor ctx, Identifier id, int x, int y, int w, int h, int tw, int th, int argb) {
        if (w <= 0 || h <= 0 || (argb >>> 24) == 0) return;
        ctx.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, w, h, tw, th, tw, th, argb);
    }

    /** Masque de lumière teint, centré. {@code force} = alpha du cœur. */
    static void lumiere(GuiGraphicsExtractor ctx, Masque m, int cx, int cy, int rgb, float force) {
        blit(ctx, m.id(), cx - m.w() / 2, cy - m.h() / 2, 0, 0, m.w(), m.h(), m.w(), m.h(), argb(rgb, force));
    }

    /** Disque lisse de rayon r (halo.png étiré), centré au sous-pixel près. */
    static void disque(GuiGraphicsExtractor ctx, float cx, float cy, float r, int rgb, float alpha) {
        ovale(ctx, cx, cy, r, r, rgb, alpha);
    }

    /** Ovale plein rx × ry (halo.png étiré), à bord net au pas GUI. */
    static void ovale(GuiGraphicsExtractor ctx, float cx, float cy, float rx, float ry, int rgb, float alpha) {
        if (alpha <= 0.004f || rx < 0.5f || ry < 0.5f) return;
        int dw = Math.max(1, Math.round(rx * 2)), dh = Math.max(1, Math.round(ry * 2));
        ctx.pose().pushMatrix();
        ctx.pose().translate(Math.round(cx - dw / 2f), Math.round(cy - dh / 2f));
        ctx.blit(RenderPipelines.GUI_TEXTURED, HALO, 0, 0, 0f, 0f, dw, dh, 64, 64, 64, 64, argb(rgb, alpha));
        ctx.pose().popMatrix();
    }

    /**
     * Halo de la maquette (halo_px) : ovales concentriques nets, paliers 27 / 53 / 100 % de {@code force} (3 niveaux)
     * ou 40 / 100 % (2 niveaux), ry = r·ryK.
     */
    static void halo(GuiGraphicsExtractor ctx, float cx, float cy, float r, float ryK, int rgb, float force, int niveaux) {
        if (force <= 0.002f) return;
        // Comme halo_px : palier = ⌊(1 − d)·n⌋, valeur 0 au palier 0 → l'anneau extérieur visible est à (n−1)/n·r.
        float[] parts = niveaux >= 3 ? new float[]{0.27f, 0.53f, 1f} : niveaux == 2 ? new float[]{0.4f, 1f} : new float[]{1f};
        int n = parts.length;
        float prev = 0f;
        for (int i = 0; i < n - 1; i++) {
            float k = (n - 1 - i) / (float) n;
            float a = (parts[i] - prev) * force;
            prev = parts[i];
            ovale(ctx, cx, cy, r * k, r * k * ryK, rgb, a);
        }
    }

    /** Coulée de lumière sur le plancher : région 7 × longueur depuis le haut, jamais étirée. */
    static void reflet(GuiGraphicsExtractor ctx, int x, int ySol, float longueur, int rgb, float force) {
        int l = Math.max(1, Math.min(REFLET_H, Math.round(longueur)));
        blit(ctx, REFLET_SOL, x - REFLET_W / 2, ySol + 2, 0, 0, REFLET_W, l, REFLET_W, REFLET_H, argb(rgb, force));
    }

    /** Motif répété horizontalement, phase conservée (u = x mod période). */
    static void repeteH(GuiGraphicsExtractor ctx, Identifier id, int x0, int x1, int y, int u0, int v0, int tw, int th,
                        int periode, int h) {
        int x = x0;
        while (x < x1) {
            int u = Math.floorMod(x, periode);
            int w = Math.min(periode - u, x1 - x);
            blit(ctx, id, x, y, u0 + u, v0, w, h, tw, th);
            x += w;
        }
    }

    /**
     * Panneau laqué à sa place : la planche en (x, y). Si {@code hVoulue} est plus petite que la planche, coupe
     * (mode compact) : le haut jusqu'à y+100, puis le bas recollé plus haut (la bande coupée est sans ornement).
     */
    static void panneau(GuiGraphicsExtractor ctx, Panneau p, int x, int y, int hVoulue) {
        int tw = p.texW(), th = p.texH();
        if (hVoulue >= p.h()) {
            blit(ctx, p.id(), x - 4, y - 84, 0, 0, tw, th, tw, th);
            return;
        }
        int haut = 84 + 100;
        int coupe = p.h() - hVoulue;
        blit(ctx, p.id(), x - 4, y - 84, 0, 0, tw, haut, tw, th);
        int vBas = haut + coupe;
        blit(ctx, p.id(), x - 4, y - 84 + haut, 0, vBas, tw, th - vBas, tw, th);
    }

    /** Kanji du bandeau-titre d'une planche (16 GUI, or, ombré), à droite. */
    static void kanjiTitre(GuiGraphicsExtractor ctx, Font f, String kj, int x, int y) {
        kanji16(ctx, f, kj, x + PW - 20, y + 8, R_OR[6], 0xFF3A0A12);
    }

    /** Perches de bambou : gauche de 0 à xd (embout à droite), droite de xg à w (embout à gauche), y 55. */
    static void perches(GuiGraphicsExtractor ctx, int xd, int xg, int w) {
        int lg = Math.min(300, xd + 2);
        blit(ctx, PERCHE, xd - lg, 55, 300 - lg, 0, lg, 8, 300, 16);
        int ld = Math.min(300, w + 2 - xg);
        blit(ctx, PERCHE, xg, 55, 0, 8, ld, 8, 300, 16);
    }

    /**
     * Infobulle en 3 tranches verticales (infobulle.png 226×44) : la planche de 218 de large en (x, y), hauteur h.
     * Haut = rangées 0-15 (planche au texel y 2), milieu = 16-23 répété, bas = 24-43.
     */
    static void infobulle(GuiGraphicsExtractor ctx, int x, int y, int h) {
        int tx = x - 4;
        blit(ctx, INFOBULLE, tx, y - 2, 0, 0, 226, 16, 226, 44);
        int basY = y + h - 15;
        int yy = y + 14;
        while (yy < basY) {
            int hh = Math.min(8, basY - yy);
            blit(ctx, INFOBULLE, tx, yy, 0, 16, 226, hh, 226, 44);
            yy += hh;
        }
        blit(ctx, INFOBULLE, tx, basY, 0, 24, 226, 20, 226, 44);
    }

    /* ---- boutons.png : cellules de 26 de haut, bouton en (1, 1) ---- */
    static final int BTN_REI = 0, BTN_ANN = 1, BTN_CPT = 2, BTN_VAL = 3;
    /** États : REI (0 neutre, 1 survol, 2 inactif, 3 armé) ; ANN (0 normal, 1 survol, 2 inactif) ; VAL (0 actif, 1 inactif, 2 survol). */
    static void bouton(GuiGraphicsExtractor ctx, int type, int etat, int x, int y) {
        int[] cw = {87, 75, 99, 87};
        blit(ctx, BOUTONS, x - 1, y - 1, etat * cw[type], type * 26, cw[type], 26, 348, 104);
    }

    /* ================================================================ couleurs */

    static int argb(int rgb, float a) {
        int al = Math.round(255 * Math.max(0f, Math.min(1f, a)));
        return (al << 24) | (rgb & 0xFFFFFF);
    }

    static int opaque(int rgb) { return 0xFF000000 | (rgb & 0xFFFFFF); }

    /** Désature comme la maquette : c·k + gris·(1−k). */
    static int desat(int rgb, float k) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        float m = (r + g + b) / 3f;
        return (Math.round(r * k + m * (1 - k)) << 16) | (Math.round(g * k + m * (1 - k)) << 8) | Math.round(b * k + m * (1 - k));
    }

    static int mix(int a, int b, float t) {
        int r = (int) (((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = (int) (((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = (int) ((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    /* ================================================================ texte */

    static final Style INTER_GRAS = Style.EMPTY.withFont(new FontDescription.Resource(RebornFont.BOLD));

    static MutableComponent arcade(String s) { return RebornFont.arcade(sansAccents(s)); }

    static MutableComponent inter(String s) { return RebornFont.body(s); }

    static MutableComponent interGras(String s) { return Component.literal(s).setStyle(INTER_GRAS); }

    static String sansAccents(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "")
            .replace('—', '-').replace('’', '\'').toUpperCase(Locale.ROOT);
    }

    static String cle(String s) {
        if (s == null) return "";
        return Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT).trim();
    }

    /** Facteur d'ArcadePix ×0,5 : net à toutes les échelles (2/3 à l'échelle 3). */
    static float facteurPetit() {
        int k = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        if (k == 1) return 0.75f;
        return Math.max(1, Math.round(k / 2f)) / (float) k;
    }

    static float pxEcran() {
        return 1f / Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
    }

    /** ArcadePix ×1 (capitales de 7 GUI), haut des capitales en y. */
    static void arcadeX1(GuiGraphicsExtractor ctx, Font f, Component c, int x, int y, int col, boolean ombre) {
        ctx.text(f, c, x, y, col, ombre);
    }

    /** Largeur GUI d'ArcadePix ×0,5. */
    static float largeurFin(Font f, Component c) {
        return f.width(c) * facteurPetit();
    }

    /**
     * ArcadePix ×0,5 (maquette arcade_fin) : haut des capitales en yHaut ; ombre d'1 px écran à 25 %. ancre : 0 gauche,
     * 1 centre, 2 droite. Renvoie la largeur GUI.
     */
    static float arcFin(GuiGraphicsExtractor ctx, Font f, Component c, float x, float yHaut, int col, int ancre,
                        boolean ombre) {
        float s = facteurPetit(), px = pxEcran();
        float w = f.width(c) * s;
        float x0 = ancre == 1 ? x - w / 2f : ancre == 2 ? x - w : x;
        if (ombre) {
            int sc = 0xFF000000 | (((col >> 16) & 0xFF) / 4 << 16) | (((col >> 8) & 0xFF) / 4 << 8) | ((col & 0xFF) / 4);
            texteEchelle(ctx, f, c, x0 + px, yHaut + px, sc, s);
        }
        texteEchelle(ctx, f, c, x0, yHaut, col, s);
        return w;
    }

    /** Cale une coordonnée GUI sur la grille des pixels écran (texte net, aucune rangée perdue). */
    static float cale(float v) {
        int k = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        return Math.round(v * k) / (float) k;
    }

    static void texteEchelle(GuiGraphicsExtractor ctx, Font f, Component c, float x, float y, int col, float s) {
        ctx.pose().pushMatrix();
        ctx.pose().translate(cale(x), cale(y));
        ctx.pose().scale(s, s);
        ctx.text(f, c, 0, 0, col, false);
        ctx.pose().popMatrix();
    }

    static void texteEchelle(GuiGraphicsExtractor ctx, Font f, FormattedCharSequence c, float x, float y, int col, float s) {
        ctx.pose().pushMatrix();
        ctx.pose().translate(cale(x), cale(y));
        ctx.pose().scale(s, s);
        ctx.text(f, c, 0, 0, col, false);
        ctx.pose().popMatrix();
    }

    /** Décalage entre le milieu des capitales Inter 9 et le y de la ligne. */
    static final float INTER9_MIL = 4f, INTER7_MIL = 3f;
    static final float S7 = 7f / 9f, S8 = 8f / 9f;

    /** Inter 9, milieu des capitales en ymil. ancre : 0 gauche, 1 centre, 2 droite. Renvoie la largeur. */
    static int inter9(GuiGraphicsExtractor ctx, Font f, Component c, float x, float ymil, int col, int ancre) {
        int w = f.width(c);
        float x0 = ancre == 1 ? x - w / 2f : ancre == 2 ? x - w : x;
        ctx.pose().pushMatrix();
        ctx.pose().translate(cale(x0), cale(ymil - INTER9_MIL));
        ctx.text(f, c, 0, 0, col, false);
        ctx.pose().popMatrix();
        return w;
    }

    /** Inter 7 (Inter 9 × 7/9), milieu des capitales en ymil. Renvoie la largeur GUI. */
    static float inter7(GuiGraphicsExtractor ctx, Font f, Component c, float x, float ymil, int col, int ancre) {
        float w = f.width(c) * S7;
        float x0 = ancre == 1 ? x - w / 2f : ancre == 2 ? x - w : x;
        texteEchelle(ctx, f, c, x0, ymil - INTER7_MIL, col, S7);
        return w;
    }

    /** Inter 8 (aides), milieu des capitales en ymil. Renvoie la largeur GUI. */
    static float inter8(GuiGraphicsExtractor ctx, Font f, Component c, float x, float ymil, int col) {
        texteEchelle(ctx, f, c, x, ymil - 3.5f, col, S8);
        return f.width(c) * S8;
    }

    static float largeur7(Font f, Component c) { return f.width(c) * S7; }

    /** Étiquette d'une ligne de planche (maquette etiq_pc) : petites capitales Inter gras 7, interlettre 0,6 GUI. */
    record Etiq(Component[] lettres, float[] dx, float w) {}

    static Etiq etiq(Font f, String s) {
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

    /** Dessine une étiquette Inter gras 7 espacée, milieu des capitales en ymil. Renvoie la largeur GUI. */
    static float etiq(GuiGraphicsExtractor ctx, Font f, Etiq e, float x, float ymil, int col) {
        for (int i = 0; i < e.lettres().length; i++) {
            texteEchelle(ctx, f, e.lettres()[i], x + e.dx()[i], ymil - INTER7_MIL, col, S7);
        }
        return e.w();
    }

    /** Kanji 16 GUI (police du jeu ×2 : 1 px d'unifont = 1 GUI), centré en x, haut en y ; ombre d'1 GUI (0 = sans). */
    static void kanji16(GuiGraphicsExtractor ctx, Font f, String kj, float cx, float yHaut, int col, int ombre) {
        if (kj == null || kj.isEmpty()) return;
        Component c = Component.literal(kj);
        float w = f.width(c) * 2f;
        float x = Math.round(cx - w / 2f);
        if (ombre != 0) texteEchelle(ctx, f, c, x + 1, Math.round(yHaut) + 1, ombre, 2f);
        texteEchelle(ctx, f, c, x, Math.round(yHaut), col, 2f);
    }

    /** Kanji 16 GUI calé à gauche ; renvoie la largeur. */
    static float kanji16g(GuiGraphicsExtractor ctx, Font f, String kj, float x, float yHaut, int col, int ombre) {
        if (kj == null || kj.isEmpty()) return 0;
        Component c = Component.literal(kj);
        float w = f.width(c) * 2f;
        if (ombre != 0) texteEchelle(ctx, f, c, Math.round(x) + 1, Math.round(yHaut) + 1, ombre, 2f);
        texteEchelle(ctx, f, c, Math.round(x), Math.round(yHaut), col, 2f);
        return w;
    }

    /** Kanji 8 GUI (police du jeu), centré en x, haut en y. */
    static void kanji(GuiGraphicsExtractor ctx, Font f, String kj, float cx, float yHaut, int col) {
        if (kj == null || kj.isEmpty()) return;
        Component c = Component.literal(kj);
        float w = f.width(c);
        ctx.pose().pushMatrix();
        ctx.pose().translate(cale(cx - w / 2f), cale(yHaut));
        ctx.text(f, c, 0, 0, col, false);
        ctx.pose().popMatrix();
    }

    /** Paragraphe découpé (calculé une fois) : lignes Inter à l'échelle s, interligne en GUI. */
    record Paragraphe(List<FormattedCharSequence> lignes, float echelle, int interligne) {
        int hauteur() { return lignes.size() * interligne; }

        void dessiner(GuiGraphicsExtractor ctx, Font f, float x, float yHaut, int col) {
            for (int i = 0; i < lignes.size(); i++) {
                texteEchelle(ctx, f, lignes.get(i), x, yHaut + i * interligne, col, echelle);
            }
        }
    }

    static final Paragraphe VIDE = new Paragraphe(List.of(), 1f, 10);

    /** Découpe un texte Inter en lignes de largeur max (GUI), au plus maxLignes (« … » sur la dernière). */
    static Paragraphe paragraphe(Font f, String txt, float largeur, boolean sept, int interligne, int maxLignes) {
        return paragraphe(f, txt, largeur, sept ? S7 : 1f, interligne, maxLignes, false);
    }

    static Paragraphe paragraphe(Font f, String txt, float largeur, float s, int interligne, int maxLignes, boolean gras) {
        if (txt == null || txt.isBlank()) return VIDE;
        int lw = Math.max(10, (int) Math.floor(largeur / s));
        List<String> lignes = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String mot : txt.trim().split(" +")) {
            String cand = cur.isEmpty() ? mot : cur + " " + mot;
            if (!cur.isEmpty() && f.width(gras ? interGras(cand) : inter(cand)) > lw) {
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
        for (String l : lignes) out.add((gras ? interGras(l) : inter(l)).getVisualOrderText());
        return new Paragraphe(out, s, interligne);
    }

    /** Coupe s (Inter 9) par « … » pour tenir dans maxW unités de police. */
    static String ellipse(Font f, String s, int maxW) {
        if (s == null) return "";
        if (f.width(inter(s)) <= maxW) return s;
        String e = s;
        while (e.length() > 1 && f.width(inter(e + "…")) > maxW) e = e.substring(0, e.length() - 1);
        return e.trim() + "…";
    }

    /* ================================================================ touches (cabochons) */

    /** Largeur GUI d'un cabochon (maquette : ⌈largeur ArcadePix ×0,5⌉ + 7). */
    static int largeurCabochon(Font f, Component c) {
        return (int) Math.ceil(f.width(c) * 0.5f) + 6;
    }

    /** Touche dessinée (cabochon laqué de 10 GUI) en (x, ymil) ; lettre ArcadePix ×0,5 or clair. Renvoie la largeur. */
    static int cabochon(GuiGraphicsExtractor ctx, Font f, Component c, int x, float ymil) {
        int w = largeurCabochon(f, c);
        int y = Math.round(ymil - 5);
        blit(ctx, CABOCHON, x, y, 0, 0, 4, 12, 42, 12);
        int xx = x + 4, fin = x + w - 4;
        while (xx < fin) {
            int ww = Math.min(16, fin - xx);
            blit(ctx, CABOCHON, xx, y, 12, 0, ww, 12, 42, 12);
            xx += ww;
        }
        blit(ctx, CABOCHON, fin, y, 36, 0, 6, 12, 42, 12);
        arcFin(ctx, f, c, x + w / 2f, ymil - 1.75f, OR_CLAIR, 1, false);
        return w;
    }

    /* ================================================================ traits lissés (fils de lumière, cordons) */

    /**
     * Courbe Catmull-Rom passant par pts (GUI), tracée en carrés d'1 px écran ; alpha de a0 à a1 le long du tracé.
     */
    static void courbe(GuiGraphicsExtractor ctx, float[][] pts, int rgb, float a0, float a1) {
        int k = Math.max(1, Minecraft.getInstance().getWindow().getGuiScale());
        int n = pts.length;
        float lon = 0;
        for (int i = 1; i < n; i++) lon += (float) Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]);
        int pasN = Math.max(2, Math.round(lon * k * 1.2f));
        ctx.pose().pushMatrix();
        ctx.pose().scale(1f / k, 1f / k);
        int px = Integer.MIN_VALUE, py = Integer.MIN_VALUE;
        for (int s = 0; s <= pasN; s++) {
            float t = s / (float) pasN;
            float[] p = catmull(pts, t);
            int X = Math.round(p[0] * k), Y = Math.round(p[1] * k);
            if (X == px && Y == py) continue;
            px = X;
            py = Y;
            ctx.fill(X, Y, X + 1, Y + 1, argb(rgb, a0 + (a1 - a0) * t));
        }
        ctx.pose().popMatrix();
    }

    static float[] catmull(float[][] pts, float u) {
        int segs = pts.length - 1;
        float uu = Math.max(0f, Math.min(1f, u)) * segs;
        int i = Math.min((int) uu, segs - 1);
        float t = uu - i;
        float[] p0 = pts[Math.max(0, i - 1)], p1 = pts[i], p2 = pts[i + 1], p3 = pts[Math.min(segs, i + 2)];
        float t2 = t * t, t3 = t2 * t;
        float[] o = new float[2];
        for (int j = 0; j < 2; j++) {
            o[j] = 0.5f * ((2 * p1[j]) + (-p0[j] + p2[j]) * t + (2 * p0[j] - 5 * p1[j] + 4 * p2[j] - p3[j]) * t2
                + (-p0[j] + 3 * p1[j] - 3 * p2[j] + p3[j]) * t3);
        }
        return o;
    }
}
