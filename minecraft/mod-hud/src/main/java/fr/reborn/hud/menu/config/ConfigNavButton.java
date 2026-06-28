package fr.reborn.hud.menu.config;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.screen.narration.NarrationPart;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;

/**
 * Item de la sidebar du hub de config (façon OneConfig) : libellé aligné à
 * gauche, fond hover discret, barre d'accent à gauche quand actif. Style
 * sobre — pas d'underline, on s'appuie sur le fond + la barre.
 */
public class ConfigNavButton extends ButtonWidget {

    private final String label;
    private final BooleanSupplier isActive;

    public ConfigNavButton(int x, int y, int width, int height, String label,
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

        if (active) {
            DrawHelpers.roundedRect(ctx, getX(), getY(), getWidth(), getHeight(), 6,
                Colors.ACCENT_SOFT);
            // Barre d'accent à gauche.
            ctx.fill(getX(), getY() + 4, getX() + 3, getY() + getHeight() - 4, Colors.ACCENT);
        } else if (hovered) {
            DrawHelpers.roundedRect(ctx, getX(), getY(), getWidth(), getHeight(), 6,
                Colors.SURFACE_ELEVATED);
        }

        int color = active ? Colors.WHITE_PURE
                  : hovered ? Colors.FOREGROUND
                  : Colors.FOREGROUND_SUBTLE;
        Text text = active ? RebornFont.bold(label) : RebornFont.body(label);
        int textY = getY() + (getHeight() - tr.fontHeight) / 2;
        ctx.drawText(tr, text, getX() + 14, textY, color, false);
    }

    @Override
    public void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(NarrationPart.TITLE, label);
    }
}
