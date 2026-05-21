package fr.reborn.integrity.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

/**
 * Bouton style Reborn — minimaliste, cohérent avec OSTPlayerWidget /
 * ServerInfoWidget. Remplace le ButtonWidget vanilla (gris pixelisé).
 *
 * <p>Specs visuelles :
 * <ul>
 *   <li>Fond noir semi-transparent (80% en idle, 60% en hover, 50% en pressed)</li>
 *   <li>Bordure 1px beige dorée — visible toujours, plus brillante au hover</li>
 *   <li>Trait d'accent doré à gauche (3px) qui s'étire en pleine hauteur au hover</li>
 *   <li>Texte centré, blanc cassé (FFFFAF0), shadow off</li>
 *   <li>Désactivé : opacity 40%</li>
 * </ul>
 */
public class RebornButton extends ButtonWidget {

    // Palette — alignée avec les autres widgets Reborn pour cohérence.
    private static final int BG_IDLE = 0xCC0A0A0A;
    private static final int BG_HOVER = 0x99141414;
    private static final int BG_PRESSED = 0x80202020;
    private static final int BORDER = 0xFFC9A66B;
    private static final int BORDER_HOVER = 0xFFE8C896;
    private static final int ACCENT = 0xFFC9A66B;
    private static final int TEXT = 0xFFFFFAF0;
    private static final int TEXT_DISABLED = 0x66FFFAF0;

    private static final int ACCENT_WIDTH_IDLE = 3;

    public RebornButton(int x, int y, int width, int height, Text message, PressAction onPress) {
        super(x, y, width, height, message, onPress, ButtonWidget.DEFAULT_NARRATION_SUPPLIER);
    }

    @Override
    protected void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        TextRenderer tr = client.textRenderer;

        int x0 = getX();
        int y0 = getY();
        int x1 = x0 + getWidth();
        int y1 = y0 + getHeight();
        boolean hovered = this.isHovered();
        boolean enabled = this.active;

        int bg = !enabled ? BG_IDLE : (hovered ? BG_HOVER : BG_IDLE);
        int border = !enabled ? BG_IDLE : (hovered ? BORDER_HOVER : BORDER);
        int textColor = enabled ? TEXT : TEXT_DISABLED;

        // Fond.
        context.fill(x0, y0, x1, y1, bg);

        // Bordures (4 lignes 1px).
        context.fill(x0, y0, x1, y0 + 1, border);          // top
        context.fill(x0, y1 - 1, x1, y1, border);          // bottom
        context.fill(x0, y0, x0 + 1, y1, border);          // left
        context.fill(x1 - 1, y0, x1, y1, border);          // right

        // Trait d'accent doré à gauche (en idle = 3px, en hover = 4px légèrement plus large).
        int accentW = hovered ? ACCENT_WIDTH_IDLE + 1 : ACCENT_WIDTH_IDLE;
        if (enabled) {
            context.fill(x0, y0, x0 + accentW, y1, ACCENT);
        }

        // Texte centré.
        Text msg = getMessage();
        int textWidth = tr.getWidth(msg);
        int textY = y0 + (getHeight() - tr.fontHeight) / 2 + 1;
        int textX = x0 + (getWidth() - textWidth) / 2;
        context.drawText(tr, msg, textX, textY, textColor, false);
    }
}
