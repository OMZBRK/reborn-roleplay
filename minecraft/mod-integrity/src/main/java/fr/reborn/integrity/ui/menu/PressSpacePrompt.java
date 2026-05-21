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
 * Prompt "Appuyez pour entrer dans Reborn" — style référence
 * ({@code renduprompt.png}) : card rounded sombre avec keycap "ESPACE"
 * à gauche + texte body à droite.
 *
 * <p>Layout :
 * <pre>
 *   ┌────────────────────────────────────────────────┐
 *   │  [ ESPACE ]  Appuyez pour entrer dans Reborn   │
 *   └────────────────────────────────────────────────┘
 * </pre>
 */
public class PressSpacePrompt extends ButtonWidget {

    private static final String KEY_LABEL = "ESPACE";
    private static final String PROMPT_TEXT = "Appuyez pour entrer dans Reborn";

    private static final int PADDING_X = 14;
    private static final int PADDING_Y = 8;
    private static final int KEYCAP_PADDING_X = 8;
    private static final int KEYCAP_PADDING_Y = 4;
    private static final int GAP_KEYCAP_TEXT = 12;

    public PressSpacePrompt(int x, int y, int width, int height, PressAction onPress) {
        super(x, y, width, height, Text.literal(PROMPT_TEXT),
              onPress, ButtonWidget.DEFAULT_NARRATION_SUPPLIER);
    }

    public static int computeWidth(TextRenderer tr, float responsiveScale) {
        int keyW = tr.getWidth(RebornFont.bold(KEY_LABEL)) + 2 * KEYCAP_PADDING_X;
        int textW = tr.getWidth(RebornFont.body(PROMPT_TEXT));
        // Buffer +40px de sécurité : tr.getWidth() peut sous-estimer la
        // largeur réelle des TTF customs Inter (advance values imprécises
        // sur certains glyphs). 40 > 24 pour confort visuel.
        int total = PADDING_X * 2 + keyW + GAP_KEYCAP_TEXT + textW + 40;
        return Math.round(total * responsiveScale);
    }

    public static int computeHeight(TextRenderer tr, float responsiveScale) {
        int base = tr.fontHeight + 2 * KEYCAP_PADDING_Y + 2 * PADDING_Y;
        return Math.round(base * responsiveScale);
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

        // Fond rounded sombre.
        int bg = hovered ? Colors.SURFACE_ELEVATED : Colors.SURFACE;
        int border = hovered ? Colors.ACCENT : Colors.BORDER_STRONG;
        DrawHelpers.roundedOutlinedRect(context, x0, y0, w, h, 8, bg, border);

        // Keycap "ESPACE" à gauche.
        int keyTextW = tr.getWidth(RebornFont.bold(KEY_LABEL));
        int keyTextH = tr.fontHeight;
        int keyW = keyTextW + 2 * KEYCAP_PADDING_X;
        int keyH = keyTextH + 2 * KEYCAP_PADDING_Y;
        int keyX = x0 + PADDING_X;
        int keyY = y0 + (h - keyH) / 2;

        DrawHelpers.roundedOutlinedRect(context, keyX, keyY, keyW, keyH, 4,
            Colors.BACKGROUND, hovered ? Colors.ACCENT : Colors.BORDER_STRONG);
        context.drawText(tr, RebornFont.bold(KEY_LABEL),
            keyX + KEYCAP_PADDING_X, keyY + KEYCAP_PADDING_Y, Colors.WHITE_PURE, false);

        // Texte du prompt à droite.
        int promptX = keyX + keyW + GAP_KEYCAP_TEXT;
        int promptY = y0 + (h - tr.fontHeight) / 2;
        int promptColor = hovered ? Colors.FOREGROUND : Colors.FOREGROUND_SUBTLE;
        context.drawText(tr, RebornFont.body(PROMPT_TEXT),
            promptX, promptY, promptColor, false);
    }

    @Override
    public void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(net.minecraft.client.gui.screen.narration.NarrationPart.TITLE,
            "Appuyez sur Espace pour entrer dans Reborn");
    }
}
