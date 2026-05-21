package fr.reborn.integrity.ui.esc;

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
 * Bouton de tab du ESC menu — texte centré, hover avec fond accent
 * soft, danger pour le bouton "Déconnexion" (variant rouge).
 */
public class EscTabButton extends ButtonWidget {

    private final Text label;
    private final boolean isDanger;

    public EscTabButton(int x, int y, int width, int height, Text label,
                        boolean isDanger, PressAction onPress) {
        super(x, y, width, height, label, onPress, ButtonWidget.DEFAULT_NARRATION_SUPPLIER);
        this.label = label;
        this.isDanger = isDanger;
    }

    @Override
    protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        TextRenderer tr = mc.textRenderer;

        boolean hovered = isHovered();

        int textColor;
        if (isDanger) {
            textColor = hovered ? Colors.DANGER : Colors.FOREGROUND_SUBTLE;
        } else {
            textColor = hovered ? Colors.WHITE_PURE : Colors.FOREGROUND_SUBTLE;
        }

        // Hover background subtle.
        if (hovered) {
            int bgColor = isDanger ? Colors.DANGER_SOFT : Colors.SURFACE_ELEVATED;
            DrawHelpers.roundedRect(ctx, getX(), getY(), getWidth(), getHeight(), 6, bgColor);
        }

        int textW = tr.getWidth(label);
        int textX = getX() + (getWidth() - textW) / 2;
        int textY = getY() + (getHeight() - tr.fontHeight) / 2;
        ctx.drawText(tr, label, textX, textY, textColor, false);
    }

    @Override
    public void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(net.minecraft.client.gui.screen.narration.NarrationPart.TITLE,
            label.getString());
    }
}
