package fr.reborn.hud.menu.settings;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Toggle switch grand format — style iOS / Material Design.
 *
 * <p>Layout 44×22 :
 * <pre>
 *   OFF: ⬜───
 *        ───⬜  : ON
 * </pre>
 */
public class ToggleBig extends AbstractWidget {

    private boolean checked;
    private final Consumer<Boolean> onChange;

    public static final int DEFAULT_WIDTH = 44;
    public static final int DEFAULT_HEIGHT = 22;

    public ToggleBig(int x, int y, boolean initial, Consumer<Boolean> onChange) {
        super(x, y, DEFAULT_WIDTH, DEFAULT_HEIGHT, Component.literal(""));
        this.checked = initial;
        this.onChange = onChange;
    }

    public boolean isChecked() {
        return checked;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        // Interrupteur laqué 40×14 (ReglagesArt) centré dans la zone cliquable 44×22.
        ReglagesArt.interrupteur(ctx, getX() + (getWidth() - 40) / 2, getY() + (getHeight() - 14) / 2, checked, isHovered());
    }

    @Override
    public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x(), mouseY = event.y();
        checked = !checked;
        if (onChange != null) onChange.accept(checked);
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput builder) {
        
    }
}
