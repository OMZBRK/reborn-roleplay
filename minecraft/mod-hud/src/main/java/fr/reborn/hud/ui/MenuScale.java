package fr.reborn.hud.ui;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.options.OptionsScreen;

/**
 * Échelle d'interface des menus Reborn : fixée par la taille de la fenêtre, pas par l'option du joueur, le temps
 * que le menu est ouvert ; l'échelle du joueur revient à la fermeture. Le réglage du joueur n'est jamais modifié.
 */
public final class MenuScale {

    public static final int MIN_W = 600, MIN_H = 330;

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
        for (int k = 2; w.getWidth() / k >= MIN_W && w.getHeight() / k >= MIN_H; k++) best = k;
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
