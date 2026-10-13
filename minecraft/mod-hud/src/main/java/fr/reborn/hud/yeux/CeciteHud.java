package fr.reborn.hud.yeux;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Cécité d'un œil (orbite vide ou œil abîmé) : la moitié de l'écran de ce côté s'assombrit, avec un bord doux vers
 * le centre (pas de voile rouge « RPG »). Les deux yeux : seul un halo très sombre laisse deviner les silhouettes
 * proches. Un bandage (porté) adoucit l'effet du côté bandé.
 */
public final class CeciteHud {

    private static boolean leftBlind, rightBlind, bandaged;

    private CeciteHud() {}

    public static void set(boolean left, boolean right, boolean bandage) {
        leftBlind = left;
        rightBlind = right;
        bandaged = bandage;
    }

    public static boolean active() { return leftBlind || rightBlind; }

    public static void render(GuiGraphicsExtractor g, int w, int h) {
        if (!active()) return;
        if (leftBlind && rightBlind) {
            g.fill(0, 0, w, h, 0xF0050304);
            return;
        }
        int dark = bandaged ? 0xFF1A1410 : 0xFF040203;
        int soft = Math.max(24, w / 7);
        int mid = w / 2;
        if (leftBlind) {
            g.fill(0, 0, mid - soft / 2, h, dark);
            for (int i = 0; i < soft; i++) {
                int a = Math.round(255f * (1f - (i + 1f) / soft) * (1f - (i + 1f) / soft));
                int x = mid - soft / 2 + i;
                g.fill(x, 0, x + 1, h, a << 24 | (dark & 0xFFFFFF));
            }
        }
        if (rightBlind) {
            g.fill(mid + soft / 2, 0, w, h, dark);
            for (int i = 0; i < soft; i++) {
                int a = Math.round(255f * (1f - (i + 1f) / soft) * (1f - (i + 1f) / soft));
                int x = mid + soft / 2 - 1 - i;
                g.fill(x, 0, x + 1, h, a << 24 | (dark & 0xFFFFFF));
            }
        }
    }
}
