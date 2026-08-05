package fr.reborn.hud.menu.settings;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.RebornFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;

import java.util.function.BooleanSupplier;

/**
 * Bouton d'onglet horizontal pour le screen Paramètres Reborn. Texte
 * centré + underline accent quand actif. Pas de fond (style épuré).
 */
public class TabButton extends Button {

    private final String label;
    private final BooleanSupplier isActive;

    public TabButton(int x, int y, int width, int height, String label,
                     BooleanSupplier isActive, Button.OnPress onPress) {
        super(x, y, width, height, Component.literal(label), onPress,
              Button.DEFAULT_NARRATION);
        this.label = label;
        this.isActive = isActive;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        Font tr = mc.font;

        boolean active = isActive.getAsBoolean();
        boolean hovered = isHovered();
        int textColor = active ? Colors.ACCENT
                      : hovered ? Colors.WHITE_PURE
                      : Colors.FOREGROUND_SUBTLE;

        Component text = active ? RebornFont.bold(label) : RebornFont.body(label);
        int textW = tr.width(text);
        int textX = getX() + (getWidth() - textW) / 2;
        int textY = getY() + (getHeight() - tr.lineHeight) / 2;
        ctx.text(tr, text, textX, textY, textColor, false);

        if (active) {
            int underlineW = textW + 8;
            int underlineX = getX() + (getWidth() - underlineW) / 2;
            int underlineY = getY() + getHeight() - 4;
            ctx.fill(underlineX, underlineY, underlineX + underlineW, underlineY + 2,
                Colors.ACCENT);
        }
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput builder) {
        builder.put(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
            "Onglet " + label);
    }
}
