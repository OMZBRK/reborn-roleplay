package fr.reborn.integrity.ui.menu;

import fr.reborn.integrity.ui.Colors;
import fr.reborn.integrity.ui.DrawHelpers;
import fr.reborn.integrity.ui.RebornFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Prompt "Appuyez sur ESPACE pour entrer dans Reborn" — bouton cliquable
 * + raccourci clavier (géré dans le mixin via {@code keyPressed}).
 *
 * <p>Layout :
 * <pre>
 *   ┌──────────────────────────────────────────────┐
 *   │  ▢ ESPACE ▢   APPUYEZ POUR ENTRER DANS REBORN│
 *   └──────────────────────────────────────────────┘
 * </pre>
 *
 * <p>Le keycap "ESPACE" est dessiné comme un mini-bouton encadré
 * (Inter Black blanc sur fond foncé encadré beige). Le reste du texte
 * est en Inter Medium gris clair, letter-spacing élevé.
 */
public class PressSpacePrompt extends ButtonWidget {

    private static final String KEY_LABEL = "ESPACE";
    private static final String PROMPT_TEXT = "APPUYEZ POUR ENTRER DANS REBORN";

    private static final int PADDING_X = 18;
    private static final int PADDING_Y = 10;
    private static final int KEYCAP_PADDING = 6;
    private static final int GAP_KEYCAP_TEXT = 14;
    private static final float TEXT_SCALE = 1.1f;
    private static final float KEY_SCALE = 1.2f;

    public PressSpacePrompt(int x, int y, int width, int height, PressAction onPress) {
        super(x, y, width, height, Text.literal(PROMPT_TEXT),
              onPress, ButtonWidget.DEFAULT_NARRATION_SUPPLIER);
    }

    /**
     * Calcule la largeur idéale du prompt pour s'ajuster au texte.
     * À appeler depuis le mixin pour positionner le widget correctement.
     */
    public static int computeWidth(TextRenderer tr) {
        int keyW = Math.round(tr.getWidth(RebornFont.black(KEY_LABEL)) * KEY_SCALE) + 2 * KEYCAP_PADDING;
        int textW = Math.round(tr.getWidth(RebornFont.body(PROMPT_TEXT)) * TEXT_SCALE);
        return PADDING_X * 2 + keyW + GAP_KEYCAP_TEXT + textW;
    }

    public static int computeHeight(TextRenderer tr) {
        return Math.round(tr.fontHeight * KEY_SCALE) + 2 * KEYCAP_PADDING + 2 * PADDING_Y;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        TextRenderer tr = client.textRenderer;

        int x0 = getX();
        int y0 = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hovered = isHovered();

        // Background card.
        int bg = hovered ? Colors.SURFACE_ELEVATED : Colors.SURFACE;
        int border = hovered ? Colors.ACCENT : Colors.BORDER_STRONG;
        DrawHelpers.roundedOutlinedRect(context, x0, y0, w, h, 10, bg, border);

        // Glow en hover.
        if (hovered) {
            DrawHelpers.glowRect(context, x0, y0, w, h, Colors.ACCENT_GLOW, 4);
            DrawHelpers.roundedOutlinedRect(context, x0, y0, w, h, 10, bg, Colors.ACCENT);
        }

        // Keycap "ESPACE" à gauche.
        Text keyText = RebornFont.black(KEY_LABEL);
        int keyTextW = Math.round(tr.getWidth(keyText) * KEY_SCALE);
        int keyTextH = Math.round(tr.fontHeight * KEY_SCALE);
        int keyW = keyTextW + 2 * KEYCAP_PADDING;
        int keyH = keyTextH + 2 * KEYCAP_PADDING;
        int keyX = x0 + PADDING_X;
        int keyY = y0 + (h - keyH) / 2;

        DrawHelpers.roundedOutlinedRect(context, keyX, keyY, keyW, keyH, 4,
            Colors.BACKGROUND, hovered ? Colors.ACCENT : Colors.BORDER_STRONG);

        context.getMatrices().push();
        context.getMatrices().translate(keyX + KEYCAP_PADDING, keyY + KEYCAP_PADDING, 0);
        context.getMatrices().scale(KEY_SCALE, KEY_SCALE, 1f);
        context.drawText(tr, keyText, 0, 0, Colors.WHITE_PURE, false);
        context.getMatrices().pop();

        // Texte du prompt à droite du keycap.
        Text promptText = RebornFont.body(PROMPT_TEXT);
        int promptTextH = Math.round(tr.fontHeight * TEXT_SCALE);
        int promptX = keyX + keyW + GAP_KEYCAP_TEXT;
        int promptY = y0 + (h - promptTextH) / 2;
        int promptColor = hovered ? Colors.FOREGROUND : Colors.FOREGROUND_SUBTLE;

        context.getMatrices().push();
        context.getMatrices().translate(promptX, promptY, 0);
        context.getMatrices().scale(TEXT_SCALE, TEXT_SCALE, 1f);
        context.drawText(tr, promptText, 0, 0, promptColor, false);
        context.getMatrices().pop();
    }

    @Override
    public void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(net.minecraft.client.gui.screen.narration.NarrationPart.TITLE,
            "Appuyez sur Espace pour entrer dans Reborn");
    }
}
