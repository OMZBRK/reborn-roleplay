package fr.reborn.hud.chat;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.function.Consumer;

/**
 * Bouton emoji (en bas à droite de la barre de saisie, façon Paladium) + un
 * petit sélecteur d'émoticônes texte qui s'ouvre au-dessus. Cliquer une
 * émoticône l'insère dans la saisie. Rendu/clics pilotés par
 * {@code ChatScreenMixin}. Émoticônes ASCII pour l'instant (vrais glyphes
 * emoji = quand le texture pack aura les caractères).
 */
public final class EmojiPicker {

    private static boolean open = false;

    private static final String[] EMOTES = {
        ":)", ":D", ";)", ":(", ":P", "xD",
        "<3", ":o", "^^", ":3", ">:(", ":|",
        ":')", "o/", "8)", "T_T", ":/", "._."
    };

    private static final int BTN = 13;
    private static final int COLS = 6;
    private static final int CELL_W = 28;
    private static final int CELL_H = 15;
    private static final int HDR = 12;

    private EmojiPicker() {}

    public static void close() { open = false; }

    // ─── Géométrie ─── bouton au bout de la barre de saisie (largeur du chat).
    private static int buttonX(int screenW) { return ChatLayout.emojiBtnX(screenW); }
    private static int buttonY(int screenH) { return screenH - BTN - 2; }

    private static int rows() { return (EMOTES.length + COLS - 1) / COLS; }
    private static int pickerW() { return COLS * CELL_W + 8; }
    private static int pickerH() { return rows() * CELL_H + 8 + HDR; }
    private static int pickerX(int screenW) { return Math.max(4, buttonX(screenW) + BTN - pickerW()); }
    private static int pickerY(int screenH) { return buttonY(screenH) - pickerH() - 3; }

    public static void render(DrawContext ctx, int mouseX, int mouseY, int screenW, int screenH) {
        MinecraftClient mc = MinecraftClient.getInstance();
        var tr = mc.textRenderer;

        int bx = buttonX(screenW), by = buttonY(screenH);
        // Bouton rouge (façon Paladium), smiley blanc.
        boolean btnHover = inside(mouseX, mouseY, bx, by, BTN, BTN);
        DrawHelpers.roundedOutlinedRect(ctx, bx, by, BTN, BTN, 3,
            (btnHover || open) ? Colors.ACCENT_HOVER : Colors.ACCENT, Colors.ACCENT_PRESSED);
        int eye = 0xFFFFFFFF;
        ctx.fill(bx + 4, by + 4, bx + 5, by + 5, eye);
        ctx.fill(bx + 8, by + 4, bx + 9, by + 5, eye);
        ctx.fill(bx + 4, by + 8, bx + 5, by + 9, eye);
        ctx.fill(bx + 8, by + 8, bx + 9, by + 9, eye);
        ctx.fill(bx + 5, by + 9, bx + 8, by + 10, eye);

        if (!open) return;

        int px = pickerX(screenW), py = pickerY(screenH);
        DrawHelpers.roundedOutlinedRect(ctx, px, py, pickerW(), pickerH(), 5,
            Colors.BACKDROP_85, Colors.BORDER_STRONG);
        // Header "EMOJI".
        ctx.drawText(tr, fr.reborn.hud.menu.RebornFont.bold("EMOJI"),
            px + 6, py + 3, Colors.FOREGROUND_SUBTLE, false);
        ctx.fill(px + 4, py + HDR - 1, px + pickerW() - 4, py + HDR, Colors.BORDER);
        for (int i = 0; i < EMOTES.length; i++) {
            int col = i % COLS, row = i / COLS;
            int cx = px + 4 + col * CELL_W;
            int cy = py + 4 + HDR + row * CELL_H;
            boolean hot = inside(mouseX, mouseY, cx, cy, CELL_W, CELL_H);
            if (hot) DrawHelpers.roundedRect(ctx, cx, cy, CELL_W, CELL_H, 3, Colors.ACCENT_SOFT);
            int w = tr.getWidth(EMOTES[i]);
            ctx.drawText(tr, EMOTES[i], cx + (CELL_W - w) / 2, cy + (CELL_H - tr.fontHeight) / 2,
                hot ? Colors.WHITE_PURE : Colors.FOREGROUND_SUBTLE, false);
        }
    }

    /** @return true si le clic a été géré (consommé). */
    public static boolean handleClick(double mx, double my, int screenW, int screenH,
                                      Consumer<String> insert) {
        int bx = buttonX(screenW), by = buttonY(screenH);
        if (inside((int) mx, (int) my, bx, by, BTN, BTN)) {
            open = !open;
            return true;
        }
        if (!open) return false;
        int px = pickerX(screenW), py = pickerY(screenH);
        for (int i = 0; i < EMOTES.length; i++) {
            int col = i % COLS, row = i / COLS;
            int cx = px + 4 + col * CELL_W;
            int cy = py + 4 + HDR + row * CELL_H;
            if (inside((int) mx, (int) my, cx, cy, CELL_W, CELL_H)) {
                insert.accept(EMOTES[i] + " ");
                return true; // reste ouvert pour en ajouter plusieurs
            }
        }
        // Clic en dehors du picker → ferme (sans consommer, pour focus saisie).
        if (!inside((int) mx, (int) my, px, py, pickerW(), pickerH())) {
            open = false;
        }
        return false;
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
