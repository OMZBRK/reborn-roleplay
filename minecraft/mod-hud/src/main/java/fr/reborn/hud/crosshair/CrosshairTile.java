package fr.reborn.hud.crosshair;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.narration.NarrationPart;
import net.minecraft.client.gui.components.ClickableWidget;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Vignette cliquable d'un preset de viseur dans la grille de l'onglet Viseur.
 * Affiche un aperçu du crosshair centré ; bordure accent si sélectionné.
 */
public class CrosshairTile extends ClickableWidget {

    private static final int TEX = 33;

    private final int index;
    private final IntSupplier current;
    private final IntConsumer onSelect;

    public CrosshairTile(int x, int y, int size, int index,
                         IntSupplier current, IntConsumer onSelect) {
        super(x, y, size, size, Component.literal("Viseur " + (index + 1)));
        this.index = index;
        this.current = current;
        this.onSelect = onSelect;
    }

    @Override
    protected void renderWidget(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        boolean selected = current.getAsInt() == index;
        boolean hovered = isHovered();

        int border = selected ? Colors.ACCENT
                   : hovered ? Colors.BORDER_STRONG
                   : Colors.BORDER;
        DrawHelpers.roundedOutlinedRect(ctx, getX(), getY(), getWidth(), getHeight(), 6,
            selected ? Colors.ACCENT_SOFT : Colors.SURFACE, border);

        // Aperçu centré, mis à l'échelle pour tenir dans la vignette.
        int inner = getWidth() - 14;
        float scale = inner / (float) TEX;
        ctx.pose().pushMatrix();
        ctx.pose().translate(getX() + getWidth() / 2f, getY() + getHeight() / 2f);
        ctx.pose().scale(scale, scale);
        RenderSystem.enableBlend();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        ctx.drawTexture(CrosshairManager.preset(index), -TEX / 2, -TEX / 2,
            0f, 0f, TEX, TEX, TEX, TEX);
        ctx.pose().popMatrix();
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        onSelect.accept(index);
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput builder) {
        builder.put(NarrationPart.TITLE, "Viseur " + (index + 1));
    }
}
