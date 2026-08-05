package fr.reborn.hud.menu.settings;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.ClickableWidget;
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
public class SliderWidget extends ClickableWidget {

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
    protected void renderWidget(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;

        // Layout : track à gauche prend ~70% de la largeur, valeur à droite.
        int x0 = getX();
        int y0 = getY();
        int w = getWidth();
        int h = getHeight();

        int trackW = Math.round(w * 0.72f);
        int trackH = 4;
        int trackY = y0 + (h - trackH) / 2;
        int trackX = x0;

        // BG track.
        DrawHelpers.roundedRect(ctx, trackX, trackY, trackW, trackH, 2, Colors.BORDER_STRONG);

        // Fill (accent).
        float t = max > min ? (value - min) / (float) (max - min) : 0f;
        int fillW = Math.round(trackW * t);
        DrawHelpers.roundedRect(ctx, trackX, trackY, fillW, trackH, 2, Colors.ACCENT);

        // Thumb (cercle).
        int thumbX = trackX + fillW;
        int thumbY = trackY + trackH / 2;
        DrawHelpers.disc(ctx, thumbX, thumbY, 6,
            isHovered() || dragging ? Colors.ACCENT_HOVER : Colors.WHITE_PURE);
        DrawHelpers.disc(ctx, thumbX, thumbY, 4, Colors.ACCENT);

        // Valeur à droite (texte body).
        var tr = client.textRenderer;
        String valueStr = value + suffix;
        Component valueText = RebornFont.bold(valueStr);
        int valueW = tr.width(valueText);
        int valueX = x0 + w - valueW - 4;
        int valueY = y0 + (h - tr.lineHeight) / 2;
        ctx.text(tr, valueText, valueX, valueY, Colors.FOREGROUND, false);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        dragging = true;
        applyMouseValue(mouseX);
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (dragging) applyMouseValue(mouseX);
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
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
        builder.put(net.minecraft.client.gui.narration.NarrationPart.TITLE,
            "Slider valeur " + value + suffix);
    }
}
