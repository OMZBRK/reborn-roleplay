package fr.reborn.hud.menu.esc;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.RebornFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Rendu de la barre de tabs du ESC menu — texte stylisé avec
 * le LogoSigil au centre. Référence : {@code esc-menu.jsx::EscTabs}.
 *
 * <p>Layout : [Reprendre · Paramètres] · [Sigil] · [Report · Déconnexion]
 *
 * <p>Les hits clicks sont gérés par des widgets séparés dans le mixin
 * — cette classe n'est que pour le rendu passif.
 *
 * <h2>Adaptatif</h2>
 * La largeur naturelle de la barre (~564px) dépasse le viewport sur
 * petit écran ou à GUI Scale élevé (Auto choisit 3-4 sur grand moniteur
 * → la résolution scaled rétrécit). Sans adaptation, les tabs sortent de
 * l'écran à gauche/droite. Toutes les métriques (largeur tab, gaps,
 * sigil) sont donc multipliées par un {@link #fitScale(int)} ≤ 1 calculé
 * pour tenir dans {@code screenW} avec une marge. Le mixin lit
 * {@link #tabW(int)} / {@link #tabH()} pour dimensionner les widgets
 * cliquables, donc rendu et hitbox restent cohérents.
 */
public final class EscTabs {

    private static final Identifier SIGIL =
        Identifier.of("reborn", "textures/gui/logo_sigil.png");
    private static final int SIGIL_NATIVE = 256;

    /** Métriques de base (échelle 1.0), compressées par fitScale sur petit écran. */
    private static final int BASE_TAB_W = 110;
    private static final int BASE_TAB_GAP = 14;
    private static final int BASE_SIDE_GAP = 30;
    private static final int BASE_SIGIL = 36;
    /** Marge horizontale minimale entre la barre et les bords de l'écran. */
    private static final int H_MARGIN = 20;

    /** Hauteur des tabs — jamais le facteur limitant, gardée fixe. */
    public static final int TAB_H = 32;

    private EscTabs() {}

    /**
     * Facteur de compression ≤ 1 pour que la barre tienne dans screenW.
     * Plancher à 0.5 : en-dessous le texte devient illisible, on préfère
     * un léger clip des bords plutôt que des tabs minuscules.
     */
    private static float fitScale(int screenW) {
        int natural = 4 * BASE_TAB_W + 2 * BASE_TAB_GAP + 2 * BASE_SIDE_GAP + BASE_SIGIL;
        int avail = screenW - 2 * H_MARGIN;
        if (avail >= natural) return 1f;
        return Math.max(0.5f, avail / (float) natural);
    }

    public static int tabW(int screenW) {
        return Math.round(BASE_TAB_W * fitScale(screenW));
    }

    public static int tabH() {
        return TAB_H;
    }

    private static int tabGap(int screenW) {
        return Math.round(BASE_TAB_GAP * fitScale(screenW));
    }

    private static int sideGap(int screenW) {
        return Math.round(BASE_SIDE_GAP * fitScale(screenW));
    }

    private static int sigilDisplay(int screenW) {
        return Math.round(BASE_SIGIL * fitScale(screenW));
    }

    public static void renderPassive(DrawContext ctx, int screenW, int barY) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        TextRenderer tr = mc.textRenderer;

        int sigilDisplay = sigilDisplay(screenW);
        int sideGap = sideGap(screenW);

        // Sigil au centre.
        int sigilX = (screenW - sigilDisplay) / 2;
        int sigilY = barY + (TAB_H - sigilDisplay) / 2;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(sigilX, sigilY, 0);
        float scale = (float) sigilDisplay / SIGIL_NATIVE;
        ctx.getMatrices().scale(scale, scale, 1f);
        RenderSystem.enableBlend();
        ctx.drawTexture(SIGIL, 0, 0, 0f, 0f, SIGIL_NATIVE, SIGIL_NATIVE,
            SIGIL_NATIVE, SIGIL_NATIVE);
        ctx.getMatrices().pop();

        // Séparateurs · à gauche et à droite du sigil.
        int sepLeftX = sigilX - sideGap / 2;
        int sepRightX = sigilX + sideGap / 2 + sigilDisplay;
        int sepY = barY + (TAB_H - tr.fontHeight) / 2;
        ctx.drawText(tr, RebornFont.body("·"), sepLeftX, sepY, Colors.FOREGROUND_MUTED, false);
        ctx.drawText(tr, RebornFont.body("·"), sepRightX, sepY, Colors.FOREGROUND_MUTED, false);
    }

    /** Calcule la position X du tab d'index (0 ou 1 = gauche du sigil, 2 ou 3 = droite). */
    public static int tabX(int screenW, int index) {
        int sigilDisplay = sigilDisplay(screenW);
        int tabW = tabW(screenW);
        int sideGap = sideGap(screenW);
        int tabGap = tabGap(screenW);
        int sigilX = (screenW - sigilDisplay) / 2;
        switch (index) {
            case 0: return sigilX - sideGap - 2 * tabW - tabGap;
            case 1: return sigilX - sideGap - tabW;
            case 2: return sigilX + sigilDisplay + sideGap;
            case 3: return sigilX + sigilDisplay + sideGap + tabW + tabGap;
            default: return 0;
        }
    }

    public static Text tabLabel(int index) {
        switch (index) {
            case 0: return RebornFont.bold("REPRENDRE");
            case 1: return RebornFont.bold("PARAMÈTRES");
            case 2: return RebornFont.bold("REPORT");
            case 3: return RebornFont.bold("DÉCONNEXION");
            default: return RebornFont.body("");
        }
    }
}
