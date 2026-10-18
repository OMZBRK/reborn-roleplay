package fr.reborn.hud.menu.fiche;

import fr.reborn.hud.menu.stats.StatDef;
import fr.reborn.hud.menu.stats.StatsData;
import fr.reborn.hud.menu.stats.StatsSounds;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Onglet ATTRIBUTS (maquettes 02 et 03) : six grandes lanternes dont la HAUTEUR dit le palier (jamais de chiffre),
 * deux mâts de bambou tenant le fil d'or du seuil, six ema pendues à leurs piquets (palier écrit, chevilles − / +), une
 * seule barre d'actions sous les pieds. Un point posé fait MONTER la lanterne au palier visé ; sa silhouette reste,
 * pâle, à l'ancienne place. Seul chiffre : le compteur de points.
 */
final class OngletAttributs implements Onglet {

    private static final int LW = 34, LH = 52, EMA_W = 92, EMA_H = 56;

    private FicheScene.Braises braises;
    private final float[] haut = new float[6];         // haut du dôme affiché (animé)
    private boolean hautPret;
    private final long[] trembleAt = new long[6];
    private final Component[] paliers = new Component[6];
    private final Component[] visePaliers = new Component[6];
    private final Component fleche = FicheArt.inter("›"), seuilLib = FicheArt.arcade("SEUIL");
    private Component sousTitre;
    private int survol = -1, chevilleSurvol = -1;
    private int chevillePressee = -1;
    private long chevillePresseeAt;
    private FicheScreen.Bulle bulle;
    private final FicheScene.Rect[] rects = new FicheScene.Rect[6];
    private long lastNs = System.nanoTime();

    /* ---- fil du seuil (recalculé quand le plafond doux change) */
    private int seuilY = Integer.MIN_VALUE, seuilCle = Integer.MIN_VALUE;
    private final List<int[]> seuilRuns = new ArrayList<>();
    private int seuilXa, seuilXb, plaqueX, plaqueY;

    /* ---- barre d'actions (recalculée quand l'attente ou l'armement change) */
    private String cleBas = "";
    private int reiX, annX, cptX, valX, yb;
    private static final int REI_W = 82, ANN_W = 70, CPT_W = 94, VAL_W = 82;
    private Component cptNombre, cptLib, valLib, annLib, reiLib;
    private boolean cptVide;
    private int boutonSurvol = -1;

    /** Flamme-esprit en vol des pieds vers la lanterne (point posé). */
    private record Vol(int stat, float x0, float y0, long debut, int vise) {}

    private final List<Vol> vols = new ArrayList<>();

    @Override
    public Identifier fond() { return FicheArt.FOND_ATTRIBUTS; }

    @Override
    public float lacet() { return 28f; }

    @Override
    public boolean perches() { return false; }

    @Override
    public String[][] aides() { return AIDES; }

    private static final String[][] AIDES = {{"CLIC", "+1"}, {"CLIC DROIT", "−1"}, {"MAJ", "tout"}, {"ENTREE", "valider"}};

    @Override
    public FicheScene.Braises braises() { return braises; }

    /* ================================================================ mise en page */

    @Override
    public void layout(FicheScreen s) {
        braises = new FicheScene.Braises(22, s.villageRgb(), 9, 46, 9, 70);
        sousTitre = FicheArt.inter("Plus une lanterne monte, plus la voie est sûre. Au-delà du fil d'or, chaque pas coûte davantage.");
        textesEma(s);
        cleBas = "";
        seuilCle = Integer.MIN_VALUE;
        bulle = null;
        hautPret = false;
    }

    private void textesEma(FicheScreen s) {
        int max = s.snap().max();
        for (int i = 0; i < 6; i++) {
            Repartition r = s.rep();
            String a = FicheTextes.palier(r.valide(i), max);
            paliers[i] = FicheArt.inter(a);
            visePaliers[i] = r.pending(i) > 0 ? FicheArt.interGras(FicheTextes.palier(r.vise(i), max)) : null;
        }
    }

    private int palier(FicheScreen s, int v) { return FicheTextes.palierIdx(v, s.snap().max()); }

    /** Centre de la lanterne d'une stat (banc : survol forcé). */
    int[] centreLanterne(FicheScreen s, int i) {
        FicheLayout L = s.lay();
        int top = L.yTop(palier(s, s.rep().vise(i)));
        return new int[]{L.lanX[i], top + LH / 2};
    }

    /** Fil d'or : entre le palier du plafond doux et le suivant ; mou de 7 GUI ; découpé en paliers d'1 GUI. */
    private void seuil(FicheScreen s) {
        FicheLayout L = s.lay();
        double soft = s.snap().levers().softCap();
        int ps = FicheTextes.palierIdx((int) Math.floor(soft), s.snap().max());
        int cle = ps * 100000 + L.w;
        if (cle == seuilCle) return;
        seuilCle = cle;
        seuilY = ps >= 5 ? L.yTop(5) - 20 : (L.yTop(ps) + L.yTop(ps + 1)) / 2;
        seuilXa = L.compact ? 4 : L.matG + 1;
        seuilXb = L.compact ? L.w - 4 : L.matD + 1;
        seuilRuns.clear();
        int cur = Integer.MIN_VALUE, x0 = seuilXa;
        for (int x = seuilXa; x <= seuilXb; x++) {
            int dy = dySeuil(x);
            if (dy != cur) {
                if (cur != Integer.MIN_VALUE) seuilRuns.add(new int[]{x0, x, cur});
                cur = dy;
                x0 = x;
            }
        }
        seuilRuns.add(new int[]{x0, seuilXb + 1, cur});
        plaqueX = L.lanX[2] + 54;
        plaqueY = seuilY + dySeuil(plaqueX) + 2;
    }

    private int dySeuil(int x) {
        double t = (x - seuilXa) / (double) Math.max(1, seuilXb - seuilXa);
        return (int) Math.round(7 * 4 * t * (1 - t));
    }

    private void ligneBas(FicheScreen s) {
        Repartition r = s.rep();
        String cle = r.pendingTotal() + ":" + r.remaining() + ":" + r.respecArme() + ":" + r.respecUsable();
        if (cle.equals(cleBas)) return;
        cleBas = cle;
        FicheLayout L = s.lay();
        int n = r.remaining();
        cptVide = n <= 0 && r.pendingTotal() <= 0;
        cptLib = FicheArt.arcade(cptVide ? "AUCUN POINT" : n > 1 ? "POINTS A REPARTIR" : "POINT A REPARTIR");
        cptNombre = FicheArt.arcade(String.valueOf(Math.max(0, n)));
        int p = r.pendingTotal();
        valLib = FicheArt.arcade(p > 0 ? "VALIDER (" + p + ")" : "VALIDER");
        annLib = FicheArt.arcade("ANNULER");
        reiLib = FicheArt.arcade(r.respecArme() ? "CONFIRMER ?" : "REINITIALISER");
        yb = L.barreY + 4;
        reiX = L.barreX + 8;
        annX = reiX + REI_W + 4;
        cptX = annX + ANN_W + 6;
        valX = cptX + CPT_W + 6;
    }

    /* ================================================================ rendu */

    private float intensite(int i, int v) {
        float l = 0.86f + 0.14f * (v / 10f);
        if (survol >= 0) l = i == survol ? Math.min(1f, l + 0.25f) : l * 0.85f;
        return l;
    }

    @Override
    public void scene(FicheScreen s, GuiGraphicsExtractor ctx, int mx, int my) {
        FicheLayout L = s.lay();
        Font f = s.police();
        Repartition r = s.rep();
        FicheScene sc = s.scene();
        StatDef[] st = StatDef.values();
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNs) / 1e9f);
        lastNs = now;
        textesEma(s);
        ligneBas(s);
        seuil(s);

        // ---- positions animées : la lanterne glisse vers le palier visé
        for (int i = 0; i < 6; i++) {
            float cible = L.yTop(palier(s, r.vise(i)));
            if (!hautPret) haut[i] = cible;
            else haut[i] += (cible - haut[i]) * Math.min(1f, dt * 9f);
        }
        hautPret = true;

        // ---- survol (lanterne ou ema) et chevilles
        int sv = -1;
        chevilleSurvol = -1;
        for (int i = 0; i < 6; i++) {
            int x0 = L.lanX[i] - LW / 2, y0 = Math.round(haut[i]);
            if (mx >= x0 - 4 && mx < x0 + LW + 4 && my >= y0 - 6 && my < y0 + LH + 4) sv = i;
            int ex = L.lanX[i] - EMA_W / 2, ey = L.yEma;
            if (mx >= ex && mx < ex + EMA_W && my >= ey - 2 && my < ey + EMA_H) sv = i;
            if (my >= ey + EMA_H - 14 && my < ey + EMA_H - 3) {
                if (mx >= ex + EMA_W - 25 && mx < ex + EMA_W - 15) chevilleSurvol = i * 2;
                else if (mx >= ex + EMA_W - 15 && mx < ex + EMA_W - 4) chevilleSurvol = i * 2 + 1;
            }
        }
        survol = sv;
        if (sv >= 0) s.survol("stat" + sv);

        // ---- lumière de la scène
        if (!s.fondJeu) {
            int c = FicheArt.desat(s.villageRgb(), 0.8f);
            FicheArt.lumiere(ctx, FicheArt.LUM_BUSTE, L.cx, 330 + L.d, c, 0.14f);
            FicheArt.lumiere(ctx, FicheArt.LUM_FLAQUE_M, L.cx, 456 + L.d, c, 0.12f);
        }

        // ---- mâts de bambou et fil d'or du seuil (continu, derrière les lanternes)
        if (!L.compact) {
            FicheArt.blit(ctx, FicheArt.MAT, L.matG - 1 - 4, 87 + L.d, 0, 0, 12, 348, 24, 348);
            FicheArt.blit(ctx, FicheArt.MAT, L.matD - 1 - 4, 87 + L.d, 12, 0, 12, 348, 24, 348);
        }
        for (int[] run : seuilRuns) {
            FicheArt.repeteH(ctx, FicheArt.SEUIL, run[0], run[1], seuilY + run[2], 0, 0, 64, 26, 3, 2);
        }
        FicheArt.blit(ctx, FicheArt.SEUIL, seuilXa + 1 - 4, seuilY + 1 - 4, 48, 0, 9, 9, 64, 26);
        FicheArt.blit(ctx, FicheArt.SEUIL, seuilXb - 1 - 4, seuilY + 1 - 4, 48, 0, 9, 9, 64, 26);
        FicheArt.blit(ctx, FicheArt.SEUIL, plaqueX, plaqueY, 0, 10, 32, 16, 64, 26);
        FicheArt.arcFin(ctx, f, seuilLib, plaqueX + 15, plaqueY + 4 + 5.5f - 1.75f, FicheArt.R_OR[6], 1, false);

        // ---- coulées, piquets et ema
        for (int i = 0; i < 6; i++) {
            float l = intensite(i, r.vise(i));
            sc.coulee(ctx, L.lanX[i], L.ySol, 50, st[i].color, 0.10f + 0.16f * l);
        }
        for (int i = 0; i < 6; i++) {
            int ex = L.lanX[i] - EMA_W / 2;
            FicheArt.blit(ctx, FicheArt.EMA, L.lanX[i] - 48, L.yPiquet - 2, i * 96, survol == i ? 84 : 0, 96, 84, 576, 168);
            int ty = L.yEma + 16;
            if (visePaliers[i] != null) {
                int w1 = FicheArt.inter9(ctx, f, paliers[i], ex + 9, ty + 17, 0xFF8A7C66, 0);
                int w2 = FicheArt.inter9(ctx, f, fleche, ex + 9 + w1 + 3, ty + 17, FicheArt.OR, 0);
                FicheArt.inter9(ctx, f, visePaliers[i], ex + 9 + w1 + w2 + 6, ty + 17, FicheArt.OR_CLAIR, 0);
            } else {
                FicheArt.inter9(ctx, f, paliers[i], ex + 9, ty + 17, FicheArt.CREME_VAL, 0);
            }
            chevilles(ctx, s, i, ex);
        }

        // ---- ancienne place : silhouette pâle + contour d'or sous la lanterne montée
        for (int i = 0; i < 6; i++) {
            if (r.pending(i) <= 0) continue;
            int yOld = L.yTop(palier(s, r.valide(i)));
            if (Math.abs(yOld - haut[i]) < 2) continue;
            int x0 = L.lanX[i] - LW / 2;
            FicheArt.blit(ctx, FicheArt.LANTERNES_L, x0, yOld, 5 * LW, i * LH, LW, LH, 6 * LW, 6 * LH,
                FicheArt.argb(0xFFFFFF, 0.18f));
            int v0 = Math.max(0, Math.round(haut[i]) + LH - yOld);
            if (v0 < LH) {
                FicheArt.blit(ctx, FicheArt.LANTERNE_CONTOUR, x0, yOld + v0, 0, v0, LW, LH - v0, LW, LH,
                    FicheArt.argb(0xFFFFFF, 0.5f));
            }
        }

        // ---- halos puis corps des lanternes
        for (int i = 0; i < 6; i++) {
            float l = intensite(i, r.vise(i));
            int x0 = L.lanX[i] - LW / 2 + tremble(i), y0 = Math.round(haut[i]);
            sc.halo(ctx, FicheScene.Taille.L, x0, y0, st[i].color, l, survol == i ? 1.25f : 1f, FicheScene.Flamme.GRANDE);
        }
        for (int i = 0; i < 6; i++) {
            float l = intensite(i, r.vise(i));
            int cy = Math.round(haut[i]) + LH / 2;
            rects[i] = sc.lanterne(ctx, FicheScene.Taille.L, L.lanX[i] + tremble(i), cy, st[i], l,
                FicheScene.Flamme.GRANDE, survol == i, i);
        }

        // ---- fils de lumière : du foyer de la lanterne à la cheville du piquet (1 px écran, 70 → 15 %)
        for (int i = 0; i < 6; i++) {
            float sx = L.lanX[i] - 0.25f + 0.5f, sy = Math.round(haut[i]) + LH - 1;
            float ey = L.yPiquet + 3;
            if (ey - sy < 4) continue;
            float sw = i % 2 == 1 ? 1.2f : -1.2f;
            float[][] pts = {{sx, sy}, {sx + sw, sy + (ey - sy) * 0.35f}, {sx + sw * 0.6f, sy + (ey - sy) * 0.7f}, {sx, ey}};
            int col = FicheArt.desat(st[i].color, 0.85f);
            FicheArt.courbe(ctx, pts, col, 0.70f, 0.15f);
            int perle = FicheArt.mix(st[i].color, 0xFFF0D2, 0.5f);
            for (float t : new float[]{0.33f, 0.66f}) {
                float[] q = FicheArt.catmull(pts, t);
                FicheArt.disque(ctx, q[0], q[1], 0.6f, perle, 0.7f);
            }
        }
    }

    private int tremble(int i) {
        long t = System.currentTimeMillis() - trembleAt[i];
        if (t > 180) return 0;
        return (int) Math.round(Math.sin(t / 180.0 * Math.PI * 6));
    }

    /** Chevilles − / + : 0 − actif, 1 − inactif, 2 + actif, 3 + inactif, 4 − survol, 5 + survol, 6 − pressé, 7 + pressé. */
    private void chevilles(GuiGraphicsExtractor ctx, FicheScreen s, int i, int ex) {
        Repartition r = s.rep();
        boolean moinsOk = r.pending(i) > 0;
        boolean plusOk = r.remaining() > 0 && r.vise(i) < s.snap().max();
        boolean presse = System.currentTimeMillis() - chevillePresseeAt < 140;
        FicheLayout L = s.lay();
        for (int k = 0; k < 2; k++) {
            boolean ok = k == 0 ? moinsOk : plusOk;
            int id = i * 2 + k;
            int cell;
            if (!ok) cell = k == 0 ? 1 : 3;
            else if (presse && chevillePressee == id) cell = 6 + k;
            else if (chevilleSurvol == id) cell = 4 + k;
            else cell = k == 0 ? 0 : 2;
            FicheArt.blit(ctx, FicheArt.CHEVILLES, ex + EMA_W - 24 + k * 10, L.yEma + EMA_H - 13, cell * 10, 0, 10, 10, 80, 10);
        }
    }

    /** Emplacement de la k-ième flamme-esprit aux pieds (les deux premières sur leurs soucoupes). */
    private static float[] place(FicheLayout L, int k) {
        int[][] off = {{-40, 0}, {40, 0}, {-70, -6}, {70, -6}, {-96, 2}, {96, 2}};
        int[] o = off[Math.min(5, k)];
        float sk = L.echelle / FichePortrait.ECHELLE;
        return new float[]{L.cx + o[0] * sk, L.hitoY + o[1]};
    }

    @Override
    public void avant(FicheScreen s, GuiGraphicsExtractor ctx, int mx, int my) {
        FicheLayout L = s.lay();
        Font f = s.police();
        Repartition r = s.rep();
        FicheScene sc = s.scene();
        // ---- deux soucoupes fixes de part et d'autre des pieds, une hitodama par point restant
        for (int k = 0; k < 2; k++) {
            float[] p = place(L, k);
            FicheArt.soucoupe(ctx, Math.round(p[0]), L.hitoY + 6);
        }
        int rest = Math.min(6, r.remaining());
        int enVol = vols.size();
        for (int k = 0; k < rest; k++) {
            float[] p = place(L, k);
            sc.hitodama(ctx, p[0], p[1], false, 1f, k);
        }
        // ---- vols en cours
        long now = System.currentTimeMillis();
        for (Iterator<Vol> it = vols.iterator(); it.hasNext(); ) {
            Vol v = it.next();
            float t = (now - v.debut()) / 450f;
            FicheScene.Rect rc = rects[v.stat()];
            float x1 = L.lanX[v.stat()], y1 = rc == null ? 200 : rc.y() + rc.h();
            if (t >= 1f) {
                StatsSounds.rise(v.vise());
                it.remove();
                continue;
            }
            float e = FicheScreen.easeOut(t);
            float x = v.x0() + (x1 - v.x0()) * e;
            float y = v.y0() + (y1 - v.y0()) * e - (float) Math.sin(t * Math.PI) * 40f;
            sc.hitodama(ctx, x, y, t > 0.6f, 1f, 20 + v.stat());
        }
        if (enVol > 0 && vols.isEmpty()) cleBas = "";

        // ---- sous-titre
        if (!L.compact) FicheArt.inter7(ctx, f, sousTitre, L.cx, 92, FicheArt.DOUX, 1);

        // ---- barre d'actions : RÉINITIALISER · ANNULER | compteur | VALIDER
        ligneBas(s);
        boutonSurvol = -1;
        if (dansBouton(mx, my, valX, VAL_W)) boutonSurvol = 0;
        else if (dansBouton(mx, my, annX, ANN_W)) boutonSurvol = 1;
        else if (dansBouton(mx, my, reiX, REI_W)) boutonSurvol = 2;
        if (boutonSurvol >= 0) s.survol("bouton" + boutonSurvol);
        FicheArt.blit(ctx, FicheArt.BARRE, L.barreX - 2, L.barreY - 2, 0, 0, 368, 36, 368, 36);

        boolean reiOk = r.canRespec() && r.respecUsable();
        int reiE = !reiOk ? 2 : r.respecArme() ? 3 : boutonSurvol == 2 ? 1 : 0;
        FicheArt.bouton(ctx, FicheArt.BTN_REI, reiE, reiX, yb);
        libelle(ctx, f, reiLib, reiX, REI_W, reiE == 2 ? FicheArt.INACTIF : reiE == 0 ? FicheArt.NEUTRE_TXT : FicheArt.CREME, reiE != 2);

        int p = r.pendingTotal();
        int annE = p <= 0 ? 2 : boutonSurvol == 1 ? 1 : 0;
        FicheArt.bouton(ctx, FicheArt.BTN_ANN, annE, annX, yb);
        libelle(ctx, f, annLib, annX, ANN_W, annE == 2 ? FicheArt.INACTIF : annE == 1 ? FicheArt.CREME : FicheArt.BOUTON_TXT, annE != 2);

        FicheArt.bouton(ctx, FicheArt.BTN_CPT, 0, cptX, yb);
        if (cptVide) {
            FicheArt.arcFin(ctx, f, cptLib, cptX + CPT_W / 2f, yb + 10 - 1.75f, FicheArt.DOUX, 1, true);
        } else {
            int nw = f.width(cptNombre) - 1;
            float lw = FicheArt.largeurFin(f, cptLib);
            int tx = Math.round(cptX + (CPT_W - (nw + 5 + lw)) / 2f);
            FicheArt.arcadeX1(ctx, f, cptNombre, tx, yb + 7, FicheArt.OR_CLAIR, true);
            FicheArt.arcFin(ctx, f, cptLib, tx + nw + 5, yb + 10 - 1.75f, FicheArt.CREME, 0, true);
        }

        int valE = p <= 0 ? 1 : boutonSurvol == 0 ? 2 : 0;
        FicheArt.bouton(ctx, FicheArt.BTN_VAL, valE, valX, yb);
        libelle(ctx, f, valLib, valX, VAL_W, valE == 1 ? FicheArt.INACTIF : FicheArt.CREME, valE != 1);
    }

    /** Libellé centré sur le bouton (plaque nue depuis le 18/10 : plus de kanji cuit à gauche). */
    private void libelle(GuiGraphicsExtractor ctx, Font f, Component lib, int x, int w, int col, boolean ombre) {
        FicheArt.arcFin(ctx, f, lib, x + w / 2f, yb + 10 - 1.75f, col, 1, ombre);
    }

    private boolean dansBouton(double mx, double my, int x, int w) {
        return mx >= x && mx < x + w && my >= yb && my < yb + 20;
    }

    @Override
    public void infobulle(FicheScreen s, GuiGraphicsExtractor ctx, int mx, int my) {
        if (survol < 0 || rects[survol] == null) return;
        Repartition r = s.rep();
        StatsData.Snapshot sn = s.snap();
        StatDef d = StatDef.values()[survol];
        int v = r.valide(survol), vise = r.vise(survol);
        String cle = d.name() + v + ":" + vise + true;
        if (bulle == null || !bulle.cle.equals(cle)) {
            bulle = new FicheScreen.Bulle(s.police(), d, v, vise, sn.max(), true);
        }
        FicheLayout L = s.lay();
        FicheScene.Rect rc = rects[survol];
        // Maquette : dans le ciel, à côté de la lanterne (côté libre), reliée à l'anneau de son sommet.
        float ax = L.lanX[survol] + 0.5f, ay = rc.y() - 4.5f;
        int y = 94;
        int xd = L.lanX[survol] + 30, xg = L.lanX[survol] - 30 - bulle.w;
        boolean droite = survol >= 3 ? xd + bulle.w <= L.w - 8 : xg < 8;
        int x = droite ? xd : xg;
        int yA = y + 58;
        float[][] pts;
        if (droite) {
            pts = new float[][]{{ax, ay}, {ax + 9, ay - 5}, {x - 9, yA + 4}, {x + 1, yA}};
        } else {
            pts = new float[][]{{ax, ay}, {ax - 9, ay - 5}, {x + bulle.w + 9, yA + 4}, {x + bulle.w - 2, yA}};
        }
        s.dessinerBulle(ctx, bulle, x, y, pts);
    }

    /* ================================================================ entrées */

    private void appliquer(FicheScreen s, int i, Repartition.Resultat res) {
        FicheLayout L = s.lay();
        Repartition r = s.rep();
        switch (res) {
            case MONTE -> {
                float[] p = place(L, Math.min(5, r.remaining()));
                vols.add(new Vol(i, p[0], p[1], System.currentTimeMillis(), r.vise(i)));
                StatsSounds.spirit(1.0f);
            }
            case BAISSE -> StatsSounds.lower();
            case REFUS -> {
                StatsSounds.deny();
                if (i >= 0) trembleAt[i] = System.currentTimeMillis();
            }
            case VALIDE -> StatsSounds.validate();
            case REINIT -> StatsSounds.respec();
            case ARME -> StatsSounds.tab();
        }
        cleBas = "";
        bulle = null;
    }

    @Override
    public boolean click(FicheScreen s, double mx, double my, int button, boolean shift) {
        Repartition r = s.rep();
        if (button == 0) {
            if (dansBouton(mx, my, valX, VAL_W)) { appliquer(s, -1, r.validate()); return true; }
            if (dansBouton(mx, my, annX, ANN_W)) { appliquer(s, -1, r.annuler()); return true; }
            if (r.canRespec() && dansBouton(mx, my, reiX, REI_W)) { appliquer(s, -1, r.respec()); return true; }
        }
        if (chevilleSurvol >= 0 && button == 0) {
            int i = chevilleSurvol / 2;
            chevillePressee = chevilleSurvol;
            chevillePresseeAt = System.currentTimeMillis();
            appliquer(s, i, r.step(i, chevilleSurvol % 2 == 0 ? -1 : 1, shift));
            return true;
        }
        if (survol >= 0 && (button == 0 || button == 1)) {
            appliquer(s, survol, r.step(survol, button == 0 ? 1 : -1, shift));
            return true;
        }
        return false;
    }

    @Override
    public boolean key(FicheScreen s, int key, boolean shift) {
        if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
            appliquer(s, -1, s.rep().validate());
            return true;
        }
        if (key >= GLFW.GLFW_KEY_1 && key <= GLFW.GLFW_KEY_6) {
            int i = key - GLFW.GLFW_KEY_1;
            appliquer(s, i, s.rep().step(i, shift ? -1 : 1, false));
            return true;
        }
        return false;
    }
}
