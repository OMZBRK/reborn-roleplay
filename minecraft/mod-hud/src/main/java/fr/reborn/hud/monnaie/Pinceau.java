package fr.reborn.hud.monnaie;

import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.LotGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;

/**
 * Pinceau des écrans de la monnaie (guichet, « Combien ? ») — même principe que celui du Poste de garde v3
 * ({@code staff/GardeArt.Pinceau}) : on peint dans n'importe quel ordre, {@link #vider} soumet en lots (aplats, filets
 * d'1 px écran, textes) pour garder peu d'éléments GUI (DA §6, règle 7). Textes Inter à la taille des maquettes
 * (px à l'échelle 2 : 12 à 26), coordonnées en unités GUI.
 */
public final class Pinceau {

    private record Txt(FormattedCharSequence c, float x, float y, int col, float s) { }

    private final List<int[]> fonds = new ArrayList<>(64);
    private final List<int[]> filets = new ArrayList<>(64);
    private final List<Txt> textes = new ArrayList<>(96);
    private Font font;

    public void police(Font f) { this.font = f; }

    static int echelle() { return Math.max(1, Minecraft.getInstance().getWindow().getGuiScale()); }

    public void fond(float x0, float y0, float x1, float y1, int col) {
        int a = Math.round(x0), b = Math.round(y0), c = Math.round(x1), d = Math.round(y1);
        if (c > a && d > b && (col >>> 24) != 0) fonds.add(new int[]{a, b, c, d, col});
    }

    public void filetH(float x0, float x1, float y, int col) {
        int k = echelle();
        int X0 = Math.round(x0 * k), X1 = Math.round(x1 * k), Y = Math.round(y * k);
        if (X1 > X0) filets.add(new int[]{X0, Y, X1, Y + 1, col});
    }

    public void filetV(float x, float y0, float y1, int col) {
        int k = echelle();
        int X = Math.round(x * k), Y0 = Math.round(y0 * k), Y1 = Math.round(y1 * k);
        if (Y1 > Y0) filets.add(new int[]{X, Y0, X + 1, Y1, col});
    }

    /** Rectangle au pixel écran. */
    public void px(float x0, float y0, float x1, float y1, int col) {
        int k = echelle();
        int X0 = Math.round(x0 * k), Y0 = Math.round(y0 * k), X1 = Math.round(x1 * k), Y1 = Math.round(y1 * k);
        if (X1 > X0 && Y1 > Y0) filets.add(new int[]{X0, Y0, X1, Y1, col});
    }

    /** Contour d'1 px écran. */
    public void cadre(float x, float y, float w, float h, int col) {
        int k = echelle();
        int X0 = Math.round(x * k), Y0 = Math.round(y * k), X1 = Math.round((x + w) * k), Y1 = Math.round((y + h) * k);
        filets.add(new int[]{X0, Y0, X1, Y0 + 1, col});
        filets.add(new int[]{X0, Y1 - 1, X1, Y1, col});
        filets.add(new int[]{X0, Y0 + 1, X0 + 1, Y1 - 1, col});
        filets.add(new int[]{X1 - 1, Y0 + 1, X1, Y1 - 1, col});
    }

    /** Disque plein (pastille) de rayon r GUI, au pixel écran. */
    public void pastille(float cx, float cy, float r, int col) {
        int k = echelle();
        float R = r * k;
        int X = Math.round(cx * k), Y = Math.round(cy * k), n = (int) Math.ceil(R);
        for (int j = -n; j <= n; j++) {
            int w = (int) Math.floor(Math.sqrt(Math.max(0, R * R - j * j)));
            if (w > 0) filets.add(new int[]{X - w, Y + j, X + w, Y + j + 1, col});
        }
    }

    /** Texte Inter, milieu de ligne en cy. Renvoie la largeur GUI. */
    public float texte(String s, float x, float cy, int taillePx, int col, boolean gras) {
        if (s == null || s.isEmpty()) return 0;
        Component c = gras ? DaKit.interGras(s) : RebornFont.body(s);
        float k = taillePx / 18f;
        textes.add(new Txt(c.getVisualOrderText(), x, cy - 4f * k, col, k));
        return font.width(c) * k;
    }

    public float texteC(String s, float xc, float cy, int taillePx, int col, boolean gras) {
        float w = largeur(s, taillePx, gras);
        texte(s, xc - w / 2f, cy, taillePx, col, gras);
        return w;
    }

    public float texteD(String s, float xd, float cy, int taillePx, int col, boolean gras) {
        float w = largeur(s, taillePx, gras);
        texte(s, xd - w, cy, taillePx, col, gras);
        return w;
    }

    /** ArcadePix (petites capitales des étiquettes), haut des capitales en y, échelle s. */
    public float arcade(String s, float x, float y, int col, float sc) {
        Component c = DaKit.arcade(s);
        textes.add(new Txt(c.getVisualOrderText(), x, y, col, sc));
        return (font.width(c) - 1) * sc;
    }

    public float arcadeC(String s, float xc, float y, int col, float sc) {
        float w = (font.width(DaKit.arcade(s)) - 1) * sc;
        return arcade(s, xc - w / 2f, y, col, sc);
    }

    public float largeur(String s, int taillePx, boolean gras) {
        if (s == null || s.isEmpty()) return 0;
        return font.width(gras ? DaKit.interGras(s) : RebornFont.body(s)) * (taillePx / 18f);
    }

    public void vider(GuiGraphicsExtractor ctx) {
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
