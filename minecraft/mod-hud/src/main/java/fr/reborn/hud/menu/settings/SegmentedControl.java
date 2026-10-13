package fr.reborn.hud.menu.settings;

import fr.reborn.hud.ui.DaKit;
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
    protected void extractWidgetRenderState(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        Minecraft client = Minecraft.getInstance();
        if (client == null || options.length == 0) return;
        var tr = client.font;

        int segW = getWidth() / options.length;
        int h = getHeight();
        int py = getY() + (h - 16) / 2;

        for (int i = 0; i < options.length; i++) {
            Option opt = options[i];
            int sx = getX() + i * segW;
            boolean active = opt.value.equals(currentValue);
            boolean hovered = isHovered() && mouseX >= sx && mouseX < sx + segW;
            ReglagesArt.segment(ctx, sx + 1, py, segW - 3, active ? 2 : hovered ? 1 : 0);
            int color = active ? DaKit.CREME : hovered ? 0xFFE8DCC0 : 0xFFB8A47E;
            DaKit.inter8(ctx, tr, DaKit.inter(opt.label), sx + segW / 2f - 1, py + 8, color, 1);
        }
    }

    @Override
    public void onClick(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x(), mouseY = event.y();
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
        
    }
}
