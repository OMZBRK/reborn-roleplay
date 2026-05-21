package fr.reborn.integrity.ui.menu;

import fr.reborn.integrity.ui.Colors;
import fr.reborn.integrity.ui.RebornFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Prompt "APPUYER POUR ENTRER DANS REBORN" — version épurée :
 * juste un texte centré qui pulse (alpha qui varie de 0.55 à 1.0 en
 * sinusoïde lente). Pas de fond ni de bordure — c'est un widget
 * cliquable invisible (hit area large = toute la zone du texte).
 *
 * <p>Au hover, l'animation pulse s'arrête sur l'alpha max et la couleur
 * vire vers blanc pur. Effet "ça réagit, mais sobre".
 */
public class PressSpacePrompt extends ButtonWidget {

    private static final String PROMPT_TEXT = "APPUYER POUR ENTRER DANS REBORN";
    private static final float TEXT_SCALE_BASE = 1.0f;

    private final long bornAtMs = System.currentTimeMillis();

    public PressSpacePrompt(int x, int y, int width, int height, PressAction onPress) {
        super(x, y, width, height, Text.literal(PROMPT_TEXT),
              onPress, ButtonWidget.DEFAULT_NARRATION_SUPPLIER);
    }

    public static int computeWidth(TextRenderer tr, float responsiveScale) {
        float scale = TEXT_SCALE_BASE * responsiveScale;
        return Math.round(tr.getWidth(RebornFont.body(PROMPT_TEXT)) * scale) + 60;
    }

    public static int computeHeight(TextRenderer tr, float responsiveScale) {
        return Math.round(tr.fontHeight * TEXT_SCALE_BASE * responsiveScale) + 16;
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        TextRenderer tr = client.textRenderer;

        boolean hovered = isHovered();

        // Animation pulse en idle, fixe en hover.
        float alpha;
        if (hovered) {
            alpha = 1.0f;
        } else {
            float t = (System.currentTimeMillis() - bornAtMs) / 1000f;
            // Sinusoïde 2.4s par cycle, range 0.45..1.0.
            float pulse = (float) (0.5 + 0.5 * Math.sin(t * Math.PI * 2.0 / 2.4));
            alpha = 0.45f + pulse * 0.55f;
        }

        // Determine la scale responsive depuis la largeur du widget vs le
        // computeWidth de base.
        float widgetScale = (getWidth() - 60f) / Math.max(1, tr.getWidth(RebornFont.body(PROMPT_TEXT)));
        widgetScale = Math.max(0.7f, Math.min(1.8f, widgetScale));

        Text promptText = RebornFont.body(PROMPT_TEXT);
        int textWidth = Math.round(tr.getWidth(promptText) * widgetScale);
        int textHeight = Math.round(tr.fontHeight * widgetScale);
        int textX = getX() + (getWidth() - textWidth) / 2;
        int textY = getY() + (getHeight() - textHeight) / 2;

        int baseColor = hovered ? Colors.WHITE_PURE : Colors.FOREGROUND;
        int finalColor = Colors.withAlpha(baseColor, alpha);

        context.getMatrices().push();
        context.getMatrices().translate(textX, textY, 0);
        context.getMatrices().scale(widgetScale, widgetScale, 1f);
        context.drawText(tr, promptText, 0, 0, finalColor, false);
        context.getMatrices().pop();
    }

    @Override
    public void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(net.minecraft.client.gui.screen.narration.NarrationPart.TITLE,
            "Appuyer pour entrer dans Reborn");
    }
}
