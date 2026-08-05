package fr.reborn.hud.chat.command;

import fr.reborn.hud.ui.style.RebornColors;
import fr.reborn.hud.ui.style.RoundedRect;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Formatting;

import java.util.function.Consumer;

/**
 * Popover qui s'affiche au-dessus de l'input chat quand l'utilisateur
 * clique sur le pill {@code /me ▾}. Navigable au clavier (↑↓ Enter) et
 * fermable via Escape.
 *
 * <p>État (visible + selection index) maintenu en dehors — ce widget
 * est purement render + hit-test.
 */
public final class QuickCommandDropdown {

    private static final int ROW_HEIGHT = 16;
    private static final int PAD_X = 8;

    private QuickCommandDropdown() {}

    /**
     * Renvoie {@code true} si la souris hover le rectangle du popover.
     * Side-effect : si click détecté sur une commande, exécute
     * {@code onPick} avec la commande choisie.
     */
    public static int[] render(GuiGraphicsExtractor ctx, Font tr, int anchorX, int anchorY,
                                int mouseX, int mouseY, int selectionIdx,
                                Consumer<QuickCommand> onPick) {
        QuickCommand[] all = QuickCommand.values();

        // Compute width = max command + max description + padding
        int maxCmdW = 0, maxDescW = 0;
        for (QuickCommand c : all) {
            maxCmdW  = Math.max(maxCmdW,  tr.width(c.command()));
            maxDescW = Math.max(maxDescW, tr.width(c.description()));
        }
        int popW = PAD_X * 2 + maxCmdW + 8 + maxDescW + 6;
        int popH = 18 + ROW_HEIGHT * all.length + 4;
        int popX = anchorX;
        int popY = anchorY - popH;
        if (popY < 4) popY = 4;

        // BG
        RoundedRect.fill(ctx, popX, popY, popW, popH, 6, RebornColors.BG_PANEL_ELEVATED);
        RoundedRect.border(ctx, popX, popY, popW, popH, 6, RebornColors.BORDER_STRONG);

        // Header "COMMANDES RAPIDES"
        ctx.text(tr, Component.literal("COMMANDES RAPIDES").formatted(Formatting.BOLD),
            popX + PAD_X, popY + 6, RebornColors.FOREGROUND_MUTED, false);

        // Items
        for (int i = 0; i < all.length; i++) {
            QuickCommand c = all[i];
            int rowY = popY + 18 + i * ROW_HEIGHT;
            boolean isHover = inside(mouseX, mouseY, popX + 3, rowY, popW - 6, ROW_HEIGHT);
            boolean isSel   = i == selectionIdx;
            if (isHover || isSel) {
                RoundedRect.fill(ctx, popX + 3, rowY, popW - 6, ROW_HEIGHT, 4,
                    RebornColors.ACCENT_SOFT);
            }
            ctx.text(tr, Component.literal(c.command()),
                popX + PAD_X, rowY + 4, RebornColors.ACCENT_HOVER, false);
            ctx.text(tr, Component.literal(c.description()),
                popX + PAD_X + maxCmdW + 8, rowY + 4, RebornColors.FOREGROUND_MUTED, false);
        }

        return new int[]{popX, popY, popW, popH};
    }

    /** Hit-test : renvoie l'index de la commande cliquee, ou -1. */
    public static int handleClick(double mouseX, double mouseY, int[] popRect) {
        if (popRect == null) return -1;
        int popX = popRect[0], popY = popRect[1], popW = popRect[2];
        QuickCommand[] all = QuickCommand.values();
        for (int i = 0; i < all.length; i++) {
            int rowY = popY + 18 + i * ROW_HEIGHT;
            if (inside((int) mouseX, (int) mouseY, popX + 3, rowY, popW - 6, ROW_HEIGHT)) {
                return i;
            }
        }
        return -1;
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
