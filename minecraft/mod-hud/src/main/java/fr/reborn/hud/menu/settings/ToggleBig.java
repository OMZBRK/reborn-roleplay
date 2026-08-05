package fr.reborn.hud.menu.settings;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
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
    protected void extractContents(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        int x0 = getX();
        int y0 = getY();
        int w = getWidth();
        int h = getHeight();
        boolean hovered = isHovered();

        int trackBg = checked
            ? (hovered ? Colors.ACCENT_HOVER : Colors.ACCENT)
            : (hovered ? Colors.SURFACE_OVERLAY : Colors.SURFACE_ELEVATED);
        int border = checked ? Colors.ACCENT_HOVER : Colors.BORDER_STRONG;

        DrawHelpers.roundedOutlinedRect(ctx, x0, y0, w, h, h / 2, trackBg, border);

        // Knob — disque blanc à gauche si OFF, à droite si ON.
        int knobR = (h - 6) / 2;
        int knobCy = y0 + h / 2;
        int knobCx = checked ? x0 + w - knobR - 3 : x0 + knobR + 3;
        DrawHelpers.disc(ctx, knobCx, knobCy, knobR, Colors.WHITE_PURE);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        checked = !checked;
        if (onChange != null) onChange.accept(checked);
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput builder) {
        builder.put(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
            checked ? "Activé" : "Désactivé");
    }
}
