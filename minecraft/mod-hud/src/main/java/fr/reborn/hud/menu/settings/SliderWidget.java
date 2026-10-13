package fr.reborn.hud.menu.settings;

import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.function.IntConsumer;

/**
 * Slider horizontal — track + fill + thumb + valeur affichée à droite.
 *
 * <p>Référence design : {@code settings.jsx::Slider}.
 *
 * <p>Layout standard 200×24 :
 * <pre>
 *   ┌──────────────────────────────────┐
 *   │ ━━━━●━━━━━━━━━━━━━━━━   144 fps  │
 *   └──────────────────────────────────┘
 * </pre>
 */
public class SliderWidget extends AbstractWidget {

    private int value;
    private final int min;
    private final int max;
    private final String suffix;
    private final IntConsumer onChange;
    private boolean dragging = false;

    public SliderWidget(int x, int y, int width, int height,
                        int initialValue, int min, int max, String suffix,
                        IntConsumer onChange) {
        super(x, y, width, height, Component.literal(""));
        this.value = initialValue;
        this.min = min;
        this.max = max;
        this.suffix = suffix == null ? "" : suffix;
        this.onChange = onChange;
    }

    public int getValue() {
        return value;
    }

    public void setValue(int v) {
        int clamped = Math.max(min, Math.min(max, v));
        if (clamped != value) {
            value = clamped;
            if (onChange != null) onChange.accept(value);
        }
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;

        // Réglette d'or à gauche (~72 % de la largeur), valeur Inter 9 or clair à droite.
        int x0 = getX();
        int y0 = getY();
        int w = getWidth();
        int h = getHeight();
        int trackW = Math.round(w * 0.72f);
        float t = max > min ? (value - min) / (float) (max - min) : 0f;
        ReglagesArt.reglette(ctx, x0, y0 + (h - 10) / 2, trackW, t, isHovered() || dragging);

        var tr = client.font;
        DaKit.inter9(ctx, tr, DaKit.inter(value + suffix), x0 + w - 2, y0 + h / 2f, DaKit.OR_CLAIR, 2);
    }

    @Override
    public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x(), mouseY = event.y();
        dragging = true;
        applyMouseValue(mouseX);
    }

    @Override
    protected void onDrag(net.minecraft.client.input.MouseButtonEvent event, double deltaX, double deltaY) {
        double mouseX = event.x(), mouseY = event.y();
        if (dragging) applyMouseValue(mouseX);
    }

    @Override
    public void onRelease(net.minecraft.client.input.MouseButtonEvent event) {
        double mouseX = event.x(), mouseY = event.y();
        dragging = false;
    }

    private void applyMouseValue(double mouseX) {
        int trackW = Math.round(getWidth() * 0.72f);
        float t = (float) (mouseX - getX()) / Math.max(1, trackW);
        t = Math.max(0f, Math.min(1f, t));
        int newValue = min + Math.round(t * (max - min));
        setValue(newValue);
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput builder) {
        
    }
}
