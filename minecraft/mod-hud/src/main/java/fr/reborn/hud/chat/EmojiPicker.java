package fr.reborn.hud.chat;

import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

import java.util.function.Consumer;

/**
 * Sélecteur d'emojis ouvert par le bouton smiley de la barre de saisie du
 * {@link ChatPanel}. Cliquer un emoji insère son glyphe dans la saisie.
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

    private static final int COLS = 6;
    private static final int CELL_W = 22;
    private static final int CELL_H = 20;
    private static final int HDR = 12;
    private static final net.minecraft.network.chat.Component TITRE = DaKit.arcade("Emojis");
    private static final float EMOJI_SCALE = 1.7f;

    private EmojiPicker() {}

    public static void onClose() { open = false; }

    public static void toggle() { open = !open; }

    public static boolean isOpen() { return open; }

    // ─── Géométrie (coordonnées locales du chat) : au-dessus du bouton emoji du panneau, aligné à sa droite.
    private static int pickerW() { return 140; }
    private static int pickerH() { return 100; }
    private static int pickerX() { int[] b = ChatPanel.emojiRect(); return b[0] + b[2] - pickerW(); }
    private static int pickerY() { return ChatPanel.emojiRect()[1] - pickerH() - 4; }

    /** Dessine le sélecteur (le bouton fait partie du panneau). Coordonnées locales du chat. */
    public static void render(GuiGraphicsExtractor ctx, double mouseX, double mouseY) {
        if (!open) return;
        var tr = Minecraft.getInstance().font;
        int px = pickerX(), py = pickerY();
        int pw = pickerW(), ph = pickerH();
        // panneau laqué (tools/gen_chat_textures.py : emojis.png 140×100 + ombre, filet d'or y 12)
        DaKit.blit(ctx, ChatPanel.EMOJIS, px, py, 0, 0, 144, 104, 144, 104);
        DaKit.arcFin(ctx, tr, TITRE, px + 8, py + 5, DaKit.OR, 0, true);
        for (int i = 0; i < EMOTES.length; i++) {
            int col = i % COLS, row = i / COLS;
            int cx = px + 4 + col * CELL_W;
            int cy = py + 4 + HDR + row * CELL_H;
            if (inside(mouseX, mouseY, cx, cy, CELL_W, CELL_H)) {
                DaKit.blit(ctx, ChatPanel.PIECES, cx, cy, 122, 8, CELL_W, CELL_H, 150, 120);
            }
            ctx.pose().pushMatrix();
            ctx.pose().translate(cx + CELL_W / 2f, cy + CELL_H / 2f);
            ctx.pose().scale(EMOJI_SCALE, EMOJI_SCALE);
            int gw = tr.width(EMOTES[i]);
            ctx.text(tr, EMOTES[i], -gw / 2, -tr.lineHeight / 2, 0xFFFFFFFF, false);
            ctx.pose().popMatrix();
        }
    }

    /** @return true si le clic (coordonnées locales) a été consommé par le sélecteur. */
    public static boolean handleClick(double mx, double my, Consumer<String> insert) {
        if (!open) return false;
        int px = pickerX(), py = pickerY();
        for (int i = 0; i < EMOTES.length; i++) {
            int col = i % COLS, row = i / COLS;
            int cx = px + 4 + col * CELL_W;
            int cy = py + 4 + HDR + row * CELL_H;
            if (inside(mx, my, cx, cy, CELL_W, CELL_H)) {
                insert.accept(EMOTES[i]);
                return true; // reste ouvert pour en ajouter plusieurs
            }
        }
        if (inside(mx, my, px, py, pickerW(), pickerH())) return true;
        int[] b = ChatPanel.emojiRect();
        if (!inside(mx, my, b[0], b[1], b[2], b[3])) open = false;
        return false;
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }
}
