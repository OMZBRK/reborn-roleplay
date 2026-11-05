package fr.reborn.hud.ui;

import com.mojang.blaze3d.platform.Window;
import fr.reborn.hud.menu.settings.RebornPrefs;
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

    /**
     * {@code TARGET_H} : hauteur logique visée en « Automatique » ; {@code TARGET_H_PETITE} : en « Petite » (lot 3,
     * Paramètres › Graphismes › Affichage › Taille des menus Reborn, {@code RebornPrefs.menusPetits}) — même échelle
     * en 1080p (2), 2 au lieu de 3 en 1440p, 3 au lieu de 4 en 4K ; jamais l'échelle 1 si « Automatique » donne 2
     * (1366 × 768 reste à 2, voir {@link #fit}).
     */
    public static final int MIN_W = 600, MIN_H = 330, TARGET_H = 540, TARGET_H_PETITE = 720;

    private MenuScale() {}

    /** Hauteur logique visée par les menus Reborn selon « Taille des menus Reborn ». */
    public static int cibleH() {
        RebornPrefs p = RebornPrefs.INSTANCE;
        p.ensureLoaded();
        return p.menusPetits ? TARGET_H_PETITE : TARGET_H;
    }

    /**
     * Écran Reborn qui exige un écran logique minimal (mise en page fixe, sans mode compact) : on prend alors la plus
     * grande échelle qui le respecte (1366×768 → échelle 1 plutôt que 683×384). Utilisé par la sélection et la
     * création de personnage.
     */
    public interface Plancher {
        int plancherW();

        int plancherH();
    }

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
        int minW = MIN_W, minH = MIN_H;
        if (s instanceof Plancher p) {
            minW = Math.max(minW, p.plancherW());
            minH = Math.max(minH, p.plancherH());
        }
        return echelle(w, minW, minH);
    }

    /**
     * Échelle des menus Reborn pour un écran logique minimal {@code minW}×{@code minH} (aussi {@code DaKit.echelleMenu}
     * pour les menus dessinés par-dessus le HUD).
     */
    public static int echelle(Window w, int minW, int minH) {
        int best = meilleure(w, minW, minH, cibleH());
        // « Petite » ne descend jamais à l'échelle 1 si « Automatique » donne 2 ou plus : à l'échelle 1, l'aide en
        // Inter 7 tombe à 7 px écran (règle : jamais sous 8,6 px). 1366 × 768 garde donc l'échelle 2 (intégration lot 3).
        if (best < 2 && cibleH() != TARGET_H) best = Math.min(2, meilleure(w, minW, minH, TARGET_H));
        return best;
    }

    private static int meilleure(Window w, int minW, int minH, int cible) {
        int best = 1;
        double gap = Double.MAX_VALUE;
        for (int k = 1; k <= 8; k++) {
            int lw = w.getWidth() / k, lh = w.getHeight() / k;
            if (k > 1 && (lw < minW || lh < minH)) break;
            double d = Math.abs(lh - cible);
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
