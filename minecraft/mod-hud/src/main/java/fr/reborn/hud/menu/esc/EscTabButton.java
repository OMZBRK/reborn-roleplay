package fr.reborn.hud.menu.esc;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

/**
 * Bouton de tab du ESC menu — texte centré, hover avec fond accent
 * soft, danger pour le bouton "Déconnexion" (variant rouge).
 */
public class EscTabButton extends Button {

    private final Component label;
    private final boolean isDanger;

    public EscTabButton(int x, int y, int width, int height, Component label,
                        boolean isDanger, Button.OnPress onPress) {
        super(x, y, width, height, label, onPress, Button.DEFAULT_NARRATION);
        this.label = label;
        this.isDanger = isDanger;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        Font tr = mc.font;

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
            DrawHelpers.roundedRectFull(ctx, getX(), getY(), getWidth(), getHeight(), 6, bgColor);
        }

        int textW = tr.width(label);
        int textX = getX() + (getWidth() - textW) / 2;
        int textY = getY() + (getHeight() - tr.lineHeight) / 2;
        ctx.text(tr, label, textX, textY, textColor, false);
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput builder) {
        
    }
}
