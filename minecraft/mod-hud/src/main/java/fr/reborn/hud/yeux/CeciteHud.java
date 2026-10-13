package fr.reborn.hud.yeux;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

/**
 * Cécité d'un œil (orbite vide ou œil trop abîmé, état envoyé par le serveur sur {@code reborn:yeux}) : la moitié de
 * l'écran de ce côté s'assombrit, avec un bord doux vers le centre (pas de voile rouge « RPG »). Les deux yeux : seul
 * un halo très sombre laisse deviner les silhouettes proches. Un bandage adoucit l'effet du côté bandé (voile un
 * peu moins dense, teinte de lin).
 *
 * <p>DA §6 : un aplat + une rampe lisse ({@code cecite_bord.png}, étirée) par côté, aucun dégradé recalculé.
 */
public final class CeciteHud {

    private static final Identifier BORD = Identifier.fromNamespaceAndPath("reborn", "textures/gui/yeux/cecite_bord.png");

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
            g.fill(0, 0, w, h, bandaged ? 0xE8120E0A : 0xF0050304);
            return;
        }
        int a = bandaged ? 0xE0 : 0xFF;
        int dark = (a << 24) | (bandaged ? 0x16110C : 0x040203);
        int soft = Math.max(24, w / 7);
        int mid = w / 2;
        if (leftBlind) {
            int x = mid - soft / 2;
            g.fill(0, 0, x, h, dark);
            g.blit(RenderPipelines.GUI_TEXTURED, BORD, x, 0, 0f, 0f, soft, h, 64, 4, 64, 4, dark);
        }
        if (rightBlind) {
            int x = mid + soft / 2;
            g.fill(x, 0, w, h, dark);
            // rampe retournée : u de 64 à 0
            g.blit(RenderPipelines.GUI_TEXTURED, BORD, x - soft, 0, 64f, 0f, soft, h, -64, 4, 64, 4, dark);
        }
    }
}
