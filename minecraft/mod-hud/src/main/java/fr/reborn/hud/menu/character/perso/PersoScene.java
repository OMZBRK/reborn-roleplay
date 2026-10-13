package fr.reborn.hud.menu.character.perso;

import fr.reborn.hud.ui.LotBlit;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static fr.reborn.hud.menu.character.perso.PersoArt.*;

/**
 * Le décor commun de la sélection et de la création : fond du village (texture fixe, fondu quand le village change),
 * roseaux, lumière clé, pierres de gué, lotus, ensō, emblème du clan gravé, brume ; et ce qui bouge, en sprites
 * légers : ~40 lucioles sur trois profondeurs (dérive, scintillement, reflets, traînées) et les braises de chakra à la
 * couleur du village. Positions des lucioles tirées une fois (à distance minimale, hors des panneaux) par écran.
 */
final class PersoScene {

    static final int Y_HOR = 316;

    private int village = 0, precedent = -1;
    private long changeAt;

    int village() { return village; }

    void setVillage(int v) {
        v = Math.max(0, Math.min(5, v));
        if (v == village) return;
        precedent = village;
        village = v;
        changeAt = System.currentTimeMillis();
    }

    void forcerVillage(int v) {
        village = Math.max(0, Math.min(5, v));
        precedent = -1;
    }

    static int couleur(int v) {
        return fr.reborn.hud.menu.character.CharacterRules.villageColor(v) & 0xFFFFFF;
    }

    int couleur() { return couleur(village); }

    /* ================================================================ fond */

    /** Décalage vertical du fond : centré (bandes de ciel en haut, d'eau en bas) ; écran bas : collé en haut (la lune reste sous la question). */
    static int oyFond(int h) { return Math.max(0, (h - 540) / 2); }

    void fond(GuiGraphicsExtractor g, int w, int h) {
        float t = precedent < 0 ? 1f : Math.min(1f, (System.currentTimeMillis() - changeAt) / 700f);
        if (t >= 1f) precedent = -1;
        if (precedent >= 0) fond1(g, FONDS[precedent], w, h, 0xFFFFFFFF);
        fond1(g, FONDS[village], w, h, argb(0xFFFFFF, precedent >= 0 ? t : 1f));
    }

    /**
     * Une texture de fond (1 280 × 540) ; au-delà, copies en MIROIR (aucune couture) : à gauche et à droite si
     * l'écran dépasse 1 280, au-dessus (90 premières rangées du ciel, puis ciel uni) et en dessous (eau).
     */
    private void fond1(GuiGraphicsExtractor g, net.minecraft.resources.Identifier id, int w, int h, int col) {
        int oy = oyFond(h);
        int ox = w / 2 - 640;
        if (oy > 90) g.fill(0, 0, w, oy - 90, alpha(CIEL_HAUT[village], (col >>> 24) / 255f));
        bande(g, id, ox, oy, h, col, false);
        if (ox > 0) bande(g, id, ox - 1280, oy, h, col, true);
        if (ox + 1280 < w) bande(g, id, ox + 1280, oy, h, col, true);
    }

    /**
     * La texture à x (bord gauche), éventuellement en miroir horizontal, avec ses reflets haut et bas. Les miroirs
     * passent par une région de texture négative (la géométrie reste à l'endroit : le pipeline GUI élimine les
     * faces retournées).
     */
    private static void bande(GuiGraphicsExtractor g, net.minecraft.resources.Identifier id, int x, int oy, int h,
                              int col, boolean miroir) {
        float u = miroir ? 1280 : 0;
        int rw = miroir ? -1280 : 1280;
        g.blit(RenderPipelines.GUI_TEXTURED, id, x, oy, u, 0, 1280, 540, rw, 540, 1280, 540, col);
        if (oy > 0) {
            int hh = Math.min(90, oy);
            g.blit(RenderPipelines.GUI_TEXTURED, id, x, oy - hh, u, hh, 1280, hh, rw, -hh, 1280, 540, col);
        }
        int hb = Math.min(540, h - (oy + 540));
        if (hb > 0) g.blit(RenderPipelines.GUI_TEXTURED, id, x, oy + 540, u, 540, 1280, hb, rw, -hb, 1280, 540, col);
    }

    void roseaux(GuiGraphicsExtractor g, int w, int h) {
        blit(g, ROSEAUX[village], 0, h - 180, 0, 0, 260, 180, 520, 180);
        blit(g, ROSEAUX[village], w - 260, h - 180, 260, 0, 260, 180, 520, 180);
    }

    /** Lumière clé : halo derrière le personnage + flaque sur l'eau autour de la pierre. */
    void lueur(GuiGraphicsExtractor g, int cx, int pieds, int rgb, float force) {
        lumiere(g, LUM_CLE, 302, 342, cx, pieds - 120, rgb, force * 0.8f);
        lumiere(g, LUM_FLAQUE, 262, 58, cx, pieds + 4, rgb, force);
    }

    /** Pierre éclairée (crea, dais, sel, cote) au contre-jour du village v, centre du dessus en (cx, cy). */
    void pierreLit(GuiGraphicsExtractor g, int kind, int v, int cx, int cy) {
        blit(g, PIERRES_LIT, cx - 75, cy - 20, kind * 150, v * 48, 150, 48, 600, 288);
    }

    static void pierre(GuiGraphicsExtractor g, int idx, int cx, int cy) {
        blit(g, PIERRES, cx - 36, cy - 9, idx * 72, 0, 72, 24, 720, 24);
    }

    static void lotus(GuiGraphicsExtractor g, int idx, int cx, int cy) {
        blit(g, LOTUS, cx - 12, cy - 8, idx * 24, 0, 24, 14, 192, 14);
    }

    /** Ombre de contact sous les pieds (rendue ×2, posée en 40×12 GUI). */
    static void ombre(GuiGraphicsExtractor g, int cx, int pieds, float a) {
        etire(g, OMBRE, cx - 20, pieds - 6, 40, 12, 0, 0, 80, 24, 80, 24, argb(0x000000, a));
    }

    /** Halo de l'ensō gravé (l'anneau lui-même est cuit dans la pierre). */
    static void ensoHalo(GuiGraphicsExtractor g, int cx, int cy, float rx, float ry, int rgb) {
        halo(g, cx, cy, rx * 1.12f, ry / rx, desat(rgb, 0.8f), 0.10f, 2);
    }

    /** Emblème du clan gravé en lumière sur le dais, devant le personnage (pieds en (cx, pieds)). */
    static void kamonSol(GuiGraphicsExtractor g, String slug, int clanRgb, int cx, int pieds, float a) {
        int col = mix(clanRgb, 0xFFFFFF, 0.30f);
        int yc = pieds + 8;
        halo(g, cx, yc + 4, 50, 0.26f, desat(clanRgb, 0.8f), 0.14f * a, 2);
        int kh = PersoEmblemes.kamonH();
        int c = argb(col, a);
        if ((c >>> 24) == 0) return;
        LotBlit l = LotBlit.ouvrir(g, PersoEmblemes.kamon(), 416, kh);
        l.blit(cx - 58, yc - 15, 0, 0, 116, 30, c);
        int i = clanIndex(slug);
        l.blit(cx - 26, yc + 5 - 8, (i % 8) * 52, 30 + (i / 8) * 16, 52, 16, c);
        l.fermer();
    }

    static void brumeAvant(GuiGraphicsExtractor g, int cx, int y) {
        blit(g, BRUME_AVANT, cx - 640, y, 0, 0, 1280, 180, 1280, 180);
    }

    /* ================================================================ lucioles */

    private record Luciole(int prof, float x, float y, float ph, float w1, float w2, float w3, float amp, boolean trainee) {}

    private final List<Luciole> lucioles = new ArrayList<>();
    private int lw = -1, lh = -1;
    private List<int[]> zonesL = List.of();

    /** Tire les lucioles (une fois par taille d'écran) ; zones : rectangles {x0, y0, x1, y1} à éviter. */
    void lucioles(int w, int h, List<int[]> zones, int seed) {
        if (w == lw && h == lh && zones.equals(zonesL)) return;
        lw = w;
        lh = h;
        zonesL = zones;
        lucioles.clear();
        int d = h >= 540 ? (h - 540) / 2 : h - 540;
        int hor = Y_HOR + oyFond(h);
        Random rs = new Random(seed);
        List<float[]> poses = new ArrayList<>();
        int[][] spec = {{16, hor - 40, hor + 26, 26}, {18, hor - 70, 470 + d, 46}, {6, 150 + d, 500 + d, 90}};
        float sx = w / 960f;
        for (int p = 0; p < 3; p++) {
            for (int n = 0; n < spec[p][0]; n++) {
                for (int essai = 0; essai < 4000; essai++) {
                    float x = 8 + rs.nextFloat() * (w - 16), y = spec[p][1] + rs.nextFloat() * (spec[p][2] - spec[p][1]);
                    if (dansZone(x, y, zones)) continue;
                    boolean ok = true;
                    for (float[] q : poses) {
                        if (Math.hypot(q[0] - x, (q[1] - y) * 1.4) < spec[p][3] * Math.min(1f, sx)) { ok = false; break; }
                    }
                    if (!ok) continue;
                    poses.add(new float[]{x, y});
                    lucioles.add(new Luciole(p, x, y, rs.nextFloat() * 6.28f, 0.25f + rs.nextFloat() * 0.35f,
                        0.2f + rs.nextFloat() * 0.3f, 1.2f + rs.nextFloat() * 1.8f,
                        p == 2 ? 10 + rs.nextFloat() * 8 : p == 1 ? 5 + rs.nextFloat() * 5 : 2 + rs.nextFloat() * 3,
                        p == 2 && n % 2 == 0));
                    break;
                }
            }
        }
    }

    private static boolean dansZone(float x, float y, List<int[]> zones) {
        for (int[] z : zones) if (x >= z[0] && x <= z[2] && y >= z[1] && y <= z[3]) return true;
        return false;
    }

    private static float[] position(Luciole l, float t) {
        float x = l.x() + l.amp() * (float) Math.sin(t * l.w1() + l.ph());
        float y = l.y() + l.amp() * 0.6f * (float) Math.sin(t * l.w2() + l.ph() * 1.7f)
            - (l.prof() == 2 ? 3f * (float) Math.sin(t * 0.13f + l.ph()) : 0f);
        return new float[]{x, y};
    }

    /** Lucioles lointaines et moyennes (derrière le personnage) : un blit de l'atlas par luciole (+ reflet). */
    void luciolesFond(GuiGraphicsExtractor g, int h, float alpha) {
        float t = (System.currentTimeMillis() % 3_600_000L) / 1000f;
        int hor = Y_HOR + oyFond(h);
        LotBlit lot = PersoSprites.lot(g);                  // toutes les lucioles du fond : un seul élément GUI
        for (Luciole l : lucioles) {
            if (l.prof() == 2) continue;
            float[] p = position(l, t);
            int X = Math.round(p[0]), Y = Math.round(p[1]);
            float a = (0.55f + 0.45f * (float) Math.sin(t * l.w3() + l.ph())) * alpha;
            if (l.prof() == 0) {
                PersoSprites.point(lot, X, Y, a * 0.6f);
                continue;
            }
            PersoSprites.lucioleMoyenne(lot, X, Y, a);
            if (Y < hor + 120 && Y > hor - 60) {
                int ys = Y < hor ? Math.round(hor + (hor - Y) * 0.15f + 12) : Y + 14;
                int dx = Math.round((float) Math.sin(t * 2.1f + l.ph()));
                PersoSprites.reflet(lot, X + dx, ys, a);
            }
        }
        lot.fermer();
    }

    /** Lucioles proches (devant le personnage), avec traînées de vol. */
    void luciolesDevant(GuiGraphicsExtractor g, float alpha) {
        float t = (System.currentTimeMillis() % 3_600_000L) / 1000f;
        LotBlit lot = PersoSprites.lot(g);                  // lucioles proches et traînées : un seul élément GUI
        for (Luciole l : lucioles) {
            if (l.prof() != 2) continue;
            float[] p = position(l, t);
            int X = Math.round(p[0]), Y = Math.round(p[1]);
            float a = (0.7f + 0.3f * (float) Math.sin(t * l.w3() + l.ph())) * alpha;
            PersoSprites.lucioleProche(lot, X, Y, a);
            if (l.trainee()) {
                for (int s = 1; s <= 4; s++) {
                    float[] q = position(l, t - s * 0.11f);
                    PersoSprites.point(lot, Math.round(q[0]), Math.round(q[1]), (0.45f - s * 0.09f) * a);
                }
            }
        }
        lot.fermer();
    }

    /* ================================================================ braises */

    /**
     * Braises de chakra : 70 % sur un anneau aux pieds (scintillent), 30 % montent en s'éteignant. devant : la moitié
     * avant de l'anneau (dessinée après le personnage).
     */
    static void braises(GuiGraphicsExtractor g, int cx, int cy, int rgb, int n, float rx, float ry, float monte,
                        boolean devant, int seed, float alpha) {
        float t = (System.currentTimeMillis() % 3_600_000L) / 1000f;
        Random rs = new Random(seed * 7919L + 13);
        LotBlit lot = PersoSprites.lot(g);                  // toutes les braises de l'anneau : un seul élément GUI
        for (int k = 0; k < n; k++) {
            float ang = rs.nextFloat() * 6.2832f, rr = 0.75f + rs.nextFloat() * 0.4f;
            float per = 2.2f + rs.nextFloat() * 1.8f, ph = rs.nextFloat();
            boolean gros = rs.nextFloat() < 0.35f;
            float u = (t / per + ph) % 1f;
            float x, y, a;
            boolean av;
            if (k < (int) (n * 0.7f)) {
                float a2 = ang + t * 0.12f;
                x = cx + (float) Math.cos(a2) * rx * rr;
                y = cy + (float) Math.sin(a2) * ry * rr;
                av = Math.sin(a2) > 0;
                a = 0.5f + 0.5f * (float) Math.abs(Math.sin(Math.PI * u));
            } else {
                float haut = monte * u;
                x = cx + (rs.nextFloat() - 0.5f) * rx * 1.6f + 2f * (float) Math.sin(t * 2 + k);
                y = cy - 4 - haut;
                av = (k & 1) == 0;
                a = (float) Math.sin(Math.PI * Math.min(1f, u * 1.4f)) * (1 - 0.6f * u);
            }
            if (av != devant) continue;
            a *= alpha;
            if (a <= 0.02f) continue;
            boolean montante = k >= (int) (n * 0.7f);
            PersoSprites.braise(lot, rgb, (gros ? 1 : 0) + (montante ? 2 : 0), Math.round(x), Math.round(y), a);
        }
        lot.fermer();
    }
}
