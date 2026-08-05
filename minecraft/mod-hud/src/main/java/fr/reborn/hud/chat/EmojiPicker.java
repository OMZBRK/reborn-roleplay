package fr.reborn.hud.chat;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.Consumer;

/**
 * Bouton emoji (au bout de la barre de saisie, façon Paladium) + sélecteur qui
 * s'ouvre à droite du chat. Cliquer un emoji insère son glyphe dans la saisie.
 * Les glyphes sont les caractères privés U+E000.. déclarés dans la font
 * {@code minecraft:default} du mod (chacun = un PNG 16×16). Comme tous les
 * joueurs ont le mod, tout le monde voit les emojis.
 */
public final class EmojiPicker {

    private static boolean open = false;

    /** Nombre d'emojis Reborn (glyphes U+E000..). */
    private static final int EMOJI_COUNT = 22;
    /** Premier codepoint privé utilisé. */
    private static final int EMOJI_BASE = 0xE000;

    // Glyphes construits depuis les codepoints (pas d'embedding de chars).
    private static final String[] EMOTES = buildEmotes();

    private static String[] buildEmotes() {
        String[] e = new String[EMOJI_COUNT];
        for (int i = 0; i < EMOJI_COUNT; i++) {
            e[i] = new String(Character.toChars(EMOJI_BASE + i));
        }
        return e;
    }

    private static final int BTN = 13;
    private static final int COLS = 6;
    private static final int CELL_W = 22;
    private static final int CELL_H = 20;
    private static final int HDR = 12;
    private static final float EMOJI_SCALE = 1.7f;

    private EmojiPicker() {}

    public static void onClose() { open = false; }

    // ─── Géométrie ─── bouton au bout de la barre de saisie (largeur du chat).
    private static int buttonX(int screenW) { return ChatLayout.emojiBtnX(screenW); }
    private static int buttonY(int screenH) { return screenH - BTN - 2; }

    private static int rows() { return (EMOTES.length + COLS - 1) / COLS; }
    private static int pickerW() { return COLS * CELL_W + 8; }
    private static int pickerH() { return rows() * CELL_H + 8 + HDR; }
    // S'ouvre à DROITE du chat (aligné au bouton), pas par-dessus les messages.
    private static int pickerX(int screenW) {
        return Math.min(buttonX(screenW), screenW - pickerW() - 2);
    }
    // Bas aligné avec le bouton, grandit vers le haut.
    private static int pickerY(int screenH) {
        return (buttonY(screenH) + BTN) - pickerH();
    }

    public static void render(GuiGraphicsExtractor ctx, int mouseX, int mouseY, int screenW, int screenH) {
        Minecraft mc = Minecraft.getInstance();
        var tr = mc.font;

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
        ctx.text(tr, fr.reborn.hud.menu.RebornFont.bold("EMOJI"),
            px + 6, py + 3, Colors.FOREGROUND_SUBTLE, false);
        ctx.fill(px + 4, py + HDR - 1, px + pickerW() - 4, py + HDR, Colors.BORDER);

        for (int i = 0; i < EMOTES.length; i++) {
            int col = i % COLS, row = i / COLS;
            int cx = px + 4 + col * CELL_W;
            int cy = py + 4 + HDR + row * CELL_H;
            boolean hot = inside(mouseX, mouseY, cx, cy, CELL_W, CELL_H);
            if (hot) DrawHelpers.roundedRect(ctx, cx, cy, CELL_W, CELL_H, 3, Colors.ACCENT_SOFT);
            // Emoji agrandi, centré, tint blanc (= vraies couleurs du glyphe).
            ctx.pose().pushMatrix();
            ctx.pose().translate(cx + CELL_W / 2f, cy + CELL_H / 2f);
            ctx.pose().scale(EMOJI_SCALE, EMOJI_SCALE);
            int gw = tr.width(EMOTES[i]);
            ctx.text(tr, EMOTES[i], -gw / 2, -tr.lineHeight / 2, 0xFFFFFFFF, false);
            ctx.pose().popMatrix();
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
                insert.accept(EMOTES[i]);
                return true; // reste ouvert pour en ajouter plusieurs
            }
        }
        if (!inside((int) mx, (int) my, px, py, pickerW(), pickerH())) {
            open = false;
        }
        return false;
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
