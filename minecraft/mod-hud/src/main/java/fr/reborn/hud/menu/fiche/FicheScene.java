package fr.reborn.hud.menu.fiche;

import fr.reborn.hud.menu.stats.StatDef;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.Random;

/**
 * Ce qui bouge dans le décor : lumières teintées (masques lum_*), coulées sur le plancher, halos en ovales nets
 * (maquette halo_px), lanternes (sprite + calque de flamme + balancement ±1 + survol), braises (moitié derrière,
 * moitié devant le personnage), hitodamas, ombre des pieds. Tout le reste est cuit dans le fond.
 */
final class FicheScene {

    /** Taille de lanterne : S (couronnes, 30×48) ou L (Attributs, 34×52). */
    enum Taille {
        S(30, 48), L(34, 52);
        final int w, h;
        Taille(int w, int h) { this.w = w; this.h = h; }
    }

    enum Flamme { GRANDE, MOYENNE, BRAISE, AUCUNE }

    private final long t0 = System.currentTimeMillis();
    /** Vrai en variante « jeu voilé » : ni lumière sur le décor, ni coulée, ni braises. */
    boolean fondJeu;

    long ms() { return System.currentTimeMillis() - t0; }

    /** Rectangle d'une lanterne dessinée (pour le survol et les infobulles). */
    record Rect(int x, int y, int w, int h) {
        boolean dans(double mx, double my) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    }

    /** Balancement vertical ±1 GUI, phase propre à chaque lanterne (période 3 à 4 s). */
    int balance(int i) {
        double per = 3.2 + 0.15 * i;
        return (int) Math.round(Math.sin(ms() / 1000.0 * 2 * Math.PI / per + i * 1.7) * 1.0);
    }

    /** Coulée de la lanterne sur le plancher, exactement sous elle (maquette reflet()). */
    void coulee(GuiGraphicsExtractor ctx, int x, int ySol, float longueur, int rgb, float force) {
        if (fondJeu) return;
        FicheArt.reflet(ctx, x, ySol, longueur, rgb, force);
    }

    /**
     * Halo de la maquette (lanterne()) : 3 ovales nets autour du point chaud (x0 + W/2, y0 + 0,6·Hp), teinte de la stat
     * tirée vers le chaud, puis cœur chaud sous la flamme.
     */
    void halo(GuiGraphicsExtractor ctx, Taille t, int x0, int y0, int rgb, float l, float haloK, Flamme fl) {
        int hp = t.h - 6;
        float hx = x0 + t.w / 2f, hy = y0 + hp * 0.60f;
        int c = FicheArt.desat(FicheArt.mix(rgb, FicheArt.KONOHA, 0.22f), 0.92f);
        float f = (0.06f + 0.11f * l) * haloK;
        FicheArt.halo(ctx, hx, hy, t.w * 1.08f, 1.15f, c, f, 3);
        if (fl == Flamme.GRANDE || fl == Flamme.MOYENNE) {
            FicheArt.halo(ctx, hx, y0 + t.h - 4, t.w * 0.30f, 1f, FicheArt.desat(FicheArt.COEUR_CHAUD, 0.8f), 0.10f * haloK * l, 1);
        }
    }

    /** Sprite de lanterne (cran d'éclat le plus proche) + calque de flamme animé. Renvoie le rectangle. */
    Rect lanterne(GuiGraphicsExtractor ctx, Taille t, int cx, int cy, StatDef stat, float l, Flamme fl, boolean survol,
                  int i) {
        int x0 = cx - t.w / 2, y0 = cy - t.h / 2;
        int col = FicheArt.cran(l), row = stat.ordinal();
        boolean petit = t == Taille.S;
        FicheArt.blit(ctx, petit ? FicheArt.LANTERNES_S : FicheArt.LANTERNES_L, x0, y0, col * t.w, row * t.h, t.w, t.h,
            6 * t.w, 6 * t.h);
        if (fl != Flamme.AUCUNE) {
            int base = fl == Flamme.GRANDE ? 0 : fl == Flamme.MOYENNE ? 2 : 4;
            int img = imageFlamme(i);
            FicheArt.blit(ctx, petit ? FicheArt.FLAMMES_S : FicheArt.FLAMMES_L, x0, y0, (base + img) * t.w, 0, t.w, t.h,
                6 * t.w, t.h);
        }
        if (survol) FicheArt.ornementCentre(ctx, FicheArt.ORN_ANNEAU, cx, y0 - 2);   // le cordon de l'infobulle s'y accroche
        return new Rect(x0, y0, t.w, t.h);
    }

    /** Image de flamme (0/1) qui alterne toutes les 120 à 200 ms, au hasard mais stable. */
    int imageFlamme(int i) {
        long m = ms() + i * 977L;
        long slot = m / 160;
        long h = (slot * 0x9E3779B97F4A7C15L + i * 31L) >>> 60;
        return (int) (h & 1);
    }

    /* ---------------------------------------------------------------- hitodama */

    /** Hitodama : pied de la tête en (cx, ybas) ; halo froid en 2 ovales ; sprite 12×20 (ou 9×15 si petit). */
    void hitodama(GuiGraphicsExtractor ctx, float cx, float ybas, boolean petit, float force, int i) {
        float e = petit ? 0.75f : 1f;
        float fl = (float) Math.sin(ms() / 1000.0 * 2.2 + i) * 1.2f;
        float y = ybas + fl;
        FicheArt.halo(ctx, cx, y - 6 * e, 11 * e, 1.1f, FicheArt.HITODAMA, 0.30f * force, 2);
        int img = (int) ((ms() / 250 + i) & 1);
        int w = petit ? 9 : 12, h = petit ? 15 : 20;
        int u = (petit ? 24 : 0) + img * 12;
        FicheArt.blit(ctx, FicheArt.HITODAMA_TEX, Math.round(cx - w / 2f), Math.round(y - h), u, 0, w, h, 48, 20);
    }

    /* ---------------------------------------------------------------- ombre, braises */

    void ombrePieds(GuiGraphicsExtractor ctx, int cx, int piedsY) {
        FicheArt.blit(ctx, FicheArt.OMBRE_PIEDS, cx - 30, piedsY - 7, 0, 0, 60, 14, 60, 14);
    }

    /** Braises de chakra précalculées (graine fixe) : anneau elliptique aux pieds + quelques-unes qui montent. */
    static final class Braises {
        final float[] ang, rr, x, haut, a, vit;
        final boolean[] anneau, gros, devantFixe;
        final int n;
        final float rx, ry, monte;
        final int coul;

        Braises(int n, int coul, long seed, float rx, float ry, float monte) {
            this.n = n;
            this.coul = coul;
            this.rx = rx;
            this.ry = ry;
            this.monte = monte;
            Random rs = new Random(seed);
            ang = new float[n]; rr = new float[n]; x = new float[n]; haut = new float[n]; a = new float[n];
            vit = new float[n]; anneau = new boolean[n]; gros = new boolean[n]; devantFixe = new boolean[n];
            for (int k = 0; k < n; k++) {
                anneau[k] = k < (int) (n * 0.7f);
                ang[k] = (float) (rs.nextFloat() * Math.PI * 2);
                rr[k] = 0.75f + rs.nextFloat() * 0.4f;
                x[k] = (rs.nextFloat() * 1.6f - 0.8f) * rx;
                haut[k] = 10 + rs.nextFloat() * (monte - 10);
                a[k] = 0.5f + rs.nextFloat() * 0.5f;
                vit[k] = 4f + rs.nextFloat() * 6f;
                gros[k] = rs.nextFloat() < 0.35f;
                devantFixe[k] = rs.nextFloat() < 0.5f;
            }
        }
    }

    /** Dessine la moitié arrière (devant = false) ou avant des braises. */
    void braises(GuiGraphicsExtractor ctx, Braises b, int cx, int cy, boolean devant, float echelle) {
        if (b == null || fondJeu) return;
        float s = echelle / FichePortrait.ECHELLE;
        float t = ms() / 1000f;
        int pale = 0xFFEEC8;
        int coeurG = FicheArt.mix(b.coul, pale, 0.6f), coeurP = FicheArt.mix(b.coul, pale, 0.3f);
        for (int k = 0; k < b.n; k++) {
            float x, y, a;
            boolean av;
            if (b.anneau[k]) {
                float ang = b.ang[k] + t * 0.12f;
                x = cx + (float) Math.cos(ang) * b.rx * s * b.rr[k];
                y = cy + (float) Math.sin(ang) * b.ry * s * b.rr[k];
                av = Math.sin(ang) > 0;
                a = b.a[k];
            } else {
                float h = (b.haut[k] + t * b.vit[k]) % b.monte;
                x = cx + b.x[k] * s;
                y = cy - h * s;
                av = b.devantFixe[k];
                a = b.a[k] * (1 - 0.6f * h / b.monte) * Math.min(1f, h / 6f);
            }
            if (av != devant) continue;
            int X = (int) x, Y = (int) y;
            if (b.gros[k]) {
                int c = FicheArt.argb(b.coul, a * 0.42f);
                ctx.fill(X - 1, Y, X + 2, Y + 1, c);
                ctx.fill(X, Y - 1, X + 1, Y + 2, c);
                ctx.fill(X, Y, X + 1, Y + 1, FicheArt.argb(coeurG, a));
            } else {
                ctx.fill(X, Y, X + 1, Y + 1, FicheArt.argb(coeurP, a));
            }
        }
    }
}
