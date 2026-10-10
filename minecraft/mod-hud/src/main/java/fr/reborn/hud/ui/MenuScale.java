package fr.reborn.hud.ui;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;

/**
 * Échelle d'interface des menus Reborn : la taille de référence est celle de l'échelle 2 en 1080p (écran logique
 * 960×540). Le temps qu'un menu Reborn est ouvert, on prend l'échelle dont la hauteur logique s'approche le plus de
 * {@value #TARGET_H} sans descendre sous {@value #MIN_W}×{@value #MIN_H} — les menus ont donc la même taille à
 * l'écran quel que soit le réglage du joueur (2, 3, 4 ou Auto), qui ne s'applique qu'au HUD en jeu et aux écrans
 * vanilla. Le réglage n'est jamais modifié : l'échelle du joueur revient à la fermeture du menu.
 */
public final class MenuScale {

    public static final int MIN_W = 600, MIN_H = 330, TARGET_H = 540;

    private MenuScale() {}

    public static boolean isReborn(Screen s) {
        return s != null && (s.getClass().getName().startsWith("fr.reborn.hud.")
                || s instanceof PauseScreen || s instanceof TitleScreen || s instanceof OptionsScreen);
    }

    /**
     * Échelle à appliquer. Hors menu Reborn : celle du joueur. Dans un menu Reborn : la plus grande échelle qui
     * laisse au moins {@value #MIN_W}×{@value #MIN_H}, quelle que soit l'échelle choisie — les menus ont ainsi la
     * même taille à l'écran en échelle 2, 3 ou 4 (1080p → 3, 1440p → 4, 720p → 2).
     */
    public static int fit(Window w, int scale, Screen s) {
        if (!isReborn(s)) return scale;
        int best = 1;
        double gap = Double.MAX_VALUE;
        for (int k = 1; k <= 8; k++) {
            int lw = w.getWidth() / k, lh = w.getHeight() / k;
            if (k > 1 && (lw < MIN_W || lh < MIN_H)) break;
            double d = Math.abs(lh - TARGET_H);
            if (d < gap) { gap = d; best = k; }
        }
        return best;
    }

    /** Recalcule l'échelle pour l'écran courant (appelé juste avant son init, ou à sa fermeture). */
    public static void apply(Minecraft mc, Screen s) {
        Window w = mc.getWindow();
        int base = w.calculateScale(mc.options.guiScale().get(), mc.isEnforceUnicode());
        int target = fit(w, base, s);
        if (w.getGuiScale() != target) w.setGuiScale(target);
    }
}
