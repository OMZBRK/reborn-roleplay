package fr.reborn.hud.monnaie;

import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * « Fouille de Kenta · 2 s · ne bouge pas » (maquette 05, vue réelle) : une ligne petite au-dessus de la barre d'action
 * et un filet de 2 px écran qui se remplit en 3 s — comme préparer / manger (règle 9 de la DA : subtil, aucun kanji).
 */
public final class FouilleHud {

    private static final int CREME_DOUX = 0xFFC2B59A;
    private static final int FILET = 72;

    private static String nom;
    private static long debut;
    private static int secondes;

    private FouilleHud() {}

    public static void debut(String n, int s) {
        nom = n;
        secondes = Math.max(1, s);
        debut = System.currentTimeMillis();
    }

    public static void fin() { nom = null; }

    public static boolean actif() { return nom != null; }

    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (nom == null || mc.player == null || mc.gui.hud.isHidden() || mc.gui.screen() != null) return;
        long now = System.currentTimeMillis();
        float av = Math.min(1f, (now - debut) / (secondes * 1000f));
        int reste = Math.max(0, (int) Math.ceil(secondes - (now - debut) / 1000f));
        var win = mc.getWindow();
        int gs = Math.max(1, win.getGuiScale());
        int km = DaKit.echelleMenu();
        int sw = win.getWidth() / km;
        float barre = (win.getGuiScaledHeight() - 22) * gs / (float) km;
        Font f = mc.font;
        DaKit.unites(g, km);
        float y = barre - 12, ym = y - 7, cx = sw / 2f;
        Component ct = DaKit.interGras("Fouille de " + nom), cd = DaKit.inter(reste + " s · ne bouge pas");
        float wt = DaKit.largeur8(f, ct), wd = DaKit.largeur7(f, cd);
        float x = cx - (wt + 4 + wd) / 2f;
        x += DaKit.inter8(g, f, ct, x, ym, DaKit.CREME, 0) + 4;
        DaKit.inter7o(g, f, cd, x, ym, CREME_DOUX, 0);
        int k = DaKit.echelleGui();
        DaKit.debutPx(g);
        int x0 = Math.round((cx - FILET / 2f) * k), x1 = Math.round((cx + FILET / 2f) * k), yy = Math.round(y * k);
        g.fill(x0, yy, x1, yy + 2, 0x60140A0C);
        g.fill(x0, yy, x0 + Math.round((x1 - x0) * av), yy + 2, DaKit.OR);
        DaKit.finPx(g);
        DaKit.finUnites(g);
    }
}
