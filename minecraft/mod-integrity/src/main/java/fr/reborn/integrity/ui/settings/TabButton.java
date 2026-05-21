package fr.reborn.integrity.ui.settings;

import fr.reborn.integrity.ui.Colors;
import fr.reborn.integrity.ui.RebornFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;

/**
 * Bouton d'onglet horizontal pour le screen Paramètres Reborn. Texte
 * centré + underline accent quand actif. Pas de fond (style épuré).
 */
public class TabButton extends ButtonWidget {

    private final String label;
    private final BooleanSupplier isActive;

    public TabButton(int x, int y, int width, int height, String label,
                     BooleanSupplier isActive, PressAction onPress) {
        super(x, y, width, height, Text.literal(label), onPress,
              ButtonWidget.DEFAULT_NARRATION_SUPPLIER);
        this.label = label;
        this.isActive = isActive;
    }

    @Override
    protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        TextRenderer tr = mc.textRenderer;

        boolean active = isActive.getAsBoolean();
        boolean hovered = isHovered();
        int textColor = active ? Colors.ACCENT
                      : hovered ? Colors.WHITE_PURE
                      : Colors.FOREGROUND_SUBTLE;

        Text text = active ? RebornFont.bold(label) : RebornFont.body(label);
        int textW = tr.getWidth(text);
        int textX = getX() + (getWidth() - textW) / 2;
        int textY = getY() + (getHeight() - tr.fontHeight) / 2;
        ctx.drawText(tr, text, textX, textY, textColor, false);

        if (active) {
            int underlineW = textW + 8;
            int underlineX = getX() + (getWidth() - underlineW) / 2;
            int underlineY = getY() + getHeight() - 4;
            ctx.fill(underlineX, underlineY, underlineX + underlineW, underlineY + 2,
                Colors.ACCENT);
        }
    }

    @Override
    public void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(net.minecraft.client.gui.screen.narration.NarrationPart.TITLE,
            "Onglet " + label);
    }
}
