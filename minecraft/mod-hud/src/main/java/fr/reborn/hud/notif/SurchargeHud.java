package fr.reborn.hud.notif;

import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Indicateur de <b>surcharge</b> sous les vitals (maquette 02_surpoids) : un kanji, un mot, un pourcentage, et une
 * jauge d'un pixel écran à paliers (0 → 130 %, repères à 100, 115 et 130 %). Rien d'autre, et seulement quand le
 * personnage est au moins « Chargé » (DA §6 règle 9). Les vitals eux-mêmes ne changent pas.
 *
 * <p>Posé en coordonnées GUI, aligné sur les barres des vitals (élément {@code VITALS} de l'éditeur de HUD).
 */
public final class SurchargeHud {

    private static final String[] MOT = {"", "Chargé", "Surchargé", "Immobile"};
    private static final String[] KANJI = {"", "軽", "重", "止"};
    private static final int[] COL = {DaKit.CREME, 0xFFE8C27A, NotifHud.AMBRE, NotifHud.VERM};

    private static int cPalier = -1, cPct = -1;
    private static Component cMot, cPctC, cKanji;

    private SurchargeHud() {}

    /**
     * @param x     gauche des barres des vitals (GUI)
     * @param y     haut de l'indicateur (GUI), juste sous les vitals
     * @param w     largeur des barres (GUI)
     * @param scale échelle de l'élément vitals
     */
    public static void render(GuiGraphicsExtractor g, float x, float y, float w, float scale) {
        NotifClient.Poids p = NotifClient.poids();
        if (p == null || p.palier() < NotifClient.CHARGE) return;
        Minecraft mc = Minecraft.getInstance();
        Font f = mc.font;
        int pal = Math.min(3, p.palier());
        int pct = (int) Math.round(p.ratio() * 100);
        if (pal != cPalier || pct != cPct) {
            cPalier = pal;
            cPct = pct;
            cMot = DaKit.interGras(MOT[pal]);
            cPctC = DaKit.inter(pct + " %");
            cKanji = Component.literal(KANJI[pal]);
        }
        int col = COL[pal];
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        if (scale != 1f) g.pose().scale(scale, scale);
        float ym = 4;
        float kw = f.width(cKanji) * 0.875f;
        DaKit.texteEchelle(g, f, cKanji, 2, ym - 3.5f, col, 0.875f);
        float tx = 2 + kw + 3;
        tx += DaKit.inter7o(g, f, cMot, tx, ym, col, 0) + 3;
        DaKit.inter7o(g, f, cPctC, tx, ym, DaKit.CREME_VAL, 0);
        // jauge d'1 px écran : fond nuit, remplissage à la couleur du palier, repères 100 / 115 / 130 %
        int k = DaKit.echelleGui();
        float jy = ym + 6;
        DaKit.debutPx(g);
        int X0 = Math.round(0 * k), X1 = Math.round(w * k), Y = Math.round(jy * k);
        g.fill(X0, Y, X1, Y + 1, DaKit.argb(0x0A0608, 0.6f));
        int fx = X0 + Math.round((X1 - X0) * (float) Math.min(1.3, p.ratio()) / 1.3f);
        g.fill(X0, Y, fx, Y + 1, col);
        int[] reperes = {0xFFD9A95E, NotifHud.AMBRE, NotifHud.VERM};
        double[] seuils = {1.0, 1.15, 1.3};
        for (int i = 0; i < 3; i++) {
            int rx = X0 + (int) Math.round((X1 - X0) * seuils[i] / 1.3) - (i == 2 ? 1 : 0);
            g.fill(rx, Y - 2, rx + 1, Y + 3, reperes[i]);
        }
        DaKit.finPx(g);
        g.pose().popMatrix();
    }
}
