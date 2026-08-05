package fr.reborn.hud.menu.config;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarrationPart;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/**
 * Item de la sidebar du hub de config (façon OneConfig) : libellé aligné à
 * gauche, fond hover discret, barre d'accent à gauche quand actif. Style
 * sobre — pas d'underline, on s'appuie sur le fond + la barre.
 */
public class ConfigNavButton extends Button {

    private final String label;
    private final BooleanSupplier isActive;

    public ConfigNavButton(int x, int y, int width, int height, String label,
                           BooleanSupplier isActive, PressAction onPress) {
        super(x, y, width, height, Component.literal(label), onPress,
              Button.DEFAULT_NARRATION_SUPPLIER);
        this.label = label;
        this.isActive = isActive;
    }

    @Override
    protected void renderWidget(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        Font tr = mc.textRenderer;

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
        Component text = active ? RebornFont.bold(label) : RebornFont.body(label);
        int textY = getY() + (getHeight() - tr.lineHeight) / 2;
        ctx.text(tr, text, getX() + 14, textY, color, false);
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput builder) {
        builder.put(NarrationPart.TITLE, label);
    }
}
