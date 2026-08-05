package fr.reborn.hud.menu.settings;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.network.chat.Component;

import java.util.function.Consumer;

/**
 * Contrôle segmenté — N boutons côte à côte, un seul "active" à la fois.
 * Référence design : {@code settings.jsx::Seg}.
 *
 * <p>Layout :
 * <pre>
 *   ┌────────┬────────┬────────┬────────┐
 *   │  HD    │ [FHD]  │  QHD   │  4K    │
 *   └────────┴────────┴────────┴────────┘
 * </pre>
 */
public class SegmentedControl extends AbstractWidget {

    /** Une option du segmented control : (valueKey, label affiché). */
    public record Option(String value, String label) {}

    private final Option[] options;
    private String currentValue;
    private final Consumer<String> onChange;

    public SegmentedControl(int x, int y, int width, int height,
                            Option[] options, String initialValue,
                            Consumer<String> onChange) {
        super(x, y, width, height, Component.literal(""));
        this.options = options;
        this.currentValue = initialValue;
        this.onChange = onChange;
    }

    public String getValue() {
        return currentValue;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || options.length == 0) return;
        var tr = client.font;

        int segW = getWidth() / options.length;
        int h = getHeight();

        // Container BG.
        DrawHelpers.roundedOutlinedRect(ctx, getX(), getY(), getWidth(), h, 6,
            Colors.SURFACE, Colors.BORDER_STRONG);

        for (int i = 0; i < options.length; i++) {
            Option opt = options[i];
            int sx = getX() + i * segW;
            boolean active = opt.value.equals(currentValue);
            boolean hovered = isHovered() && mouseX >= sx && mouseX < sx + segW;

            if (active) {
                // Segment actif — fond accent soft + texte blanc.
                DrawHelpers.roundedRect(ctx, sx + 2, getY() + 2, segW - 4, h - 4, 4,
                    Colors.ACCENT);
            } else if (hovered) {
                DrawHelpers.roundedRect(ctx, sx + 2, getY() + 2, segW - 4, h - 4, 4,
                    Colors.SURFACE_ELEVATED);
            }

            Component label = RebornFont.body(opt.label);
            int labelW = tr.width(label);
            int textX = sx + (segW - labelW) / 2;
            int textY = getY() + (h - tr.lineHeight) / 2;
            int color = active ? Colors.WHITE_PURE :
                        hovered ? Colors.FOREGROUND : Colors.FOREGROUND_SUBTLE;
            ctx.text(tr, label, textX, textY, color, false);
        }
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (options.length == 0) return;
        int segW = getWidth() / options.length;
        int idx = (int) ((mouseX - getX()) / segW);
        if (idx >= 0 && idx < options.length) {
            String newVal = options[idx].value;
            if (!newVal.equals(currentValue)) {
                currentValue = newVal;
                if (onChange != null) onChange.accept(newVal);
            }
        }
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput builder) {
        builder.put(net.minecraft.client.gui.narration.NarratedElementType.TITLE,
            "Sélecteur — valeur " + currentValue);
    }
}
