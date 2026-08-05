package fr.reborn.hud.menu.widget;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.OSTPlayer;
import fr.reborn.hud.menu.RebornFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.components.ClickableWidget;
import net.minecraft.network.chat.Component;

/**
 * Popup vertical pour ajuster le volume de l'OST player — apparaît
 * au-dessus du bouton volume de l'OST card quand on clique dessus.
 *
 * <p>Layout 36×100 :
 * <pre>
 *   ┌────┐
 *   │ 75 │  ← valeur en %
 *   │────│
 *   │ ░░ │
 *   │ ▓▓ │  ← track vertical
 *   │ ▓▓ │  ← fill du bas vers le haut
 *   │ ▓▓ │
 *   └────┘
 * </pre>
 */
public class OSTVolumePopup extends ClickableWidget {

    public static final int WIDTH = 36;
    public static final int HEIGHT = 100;
    private static final int TRACK_W = 6;
    private static final int VALUE_AREA_H = 22;

    private boolean dragging = false;

    public OSTVolumePopup(int x, int y) {
        super(x, y, WIDTH, HEIGHT, Component.literal("Volume"));
    }

    public boolean isOpen() {
        return OSTPlayer.INSTANCE.isVolumePopupOpen();
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!isOpen()) return false;
        return super.isMouseOver(mouseX, mouseY);
    }

    @Override
    protected void renderWidget(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta) {
        if (!isOpen()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc == null) return;
        var tr = mc.textRenderer;

        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();

        // Card BG.
        DrawHelpers.roundedOutlinedRect(ctx, x, y, w, h, 6,
            Colors.SURFACE_ELEVATED, Colors.BORDER_STRONG);

        // Valeur en % en haut.
        OSTPlayer ost = OSTPlayer.INSTANCE;
        int volPct = Math.round(ost.getVolume() * 100);
        Component valText = RebornFont.bold(volPct + "%");
        int valW = tr.width(valText);
        ctx.text(tr, valText, x + (w - valW) / 2, y + 6, Colors.WHITE_PURE, false);

        // Séparateur.
        ctx.fill(x + 6, y + VALUE_AREA_H, x + w - 6, y + VALUE_AREA_H + 1,
            Colors.BORDER_STRONG);

        // Track vertical.
        int trackX = x + (w - TRACK_W) / 2;
        int trackTop = y + VALUE_AREA_H + 10;
        int trackBot = y + h - 14;
        int trackH = trackBot - trackTop;
        DrawHelpers.roundedRect(ctx, trackX, trackTop, TRACK_W, trackH, TRACK_W / 2,
            Colors.BORDER_STRONG);

        // Fill du bas vers le haut.
        int fillH = Math.round(trackH * ost.getVolume());
        DrawHelpers.roundedRect(ctx, trackX, trackBot - fillH, TRACK_W, fillH, TRACK_W / 2,
            Colors.ACCENT);

        // Thumb (disque sur le track).
        int thumbY = trackBot - fillH;
        DrawHelpers.disc(ctx, trackX + TRACK_W / 2, thumbY, 5, Colors.WHITE_PURE);
        DrawHelpers.disc(ctx, trackX + TRACK_W / 2, thumbY, 3, Colors.ACCENT);
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (!isOpen()) return;
        dragging = true;
        applyMouseVolume(mouseY);
    }

    @Override
    protected void onDrag(double mouseX, double mouseY, double deltaX, double deltaY) {
        if (!isOpen() || !dragging) return;
        applyMouseVolume(mouseY);
    }

    @Override
    public void onRelease(double mouseX, double mouseY) {
        dragging = false;
    }

    private void applyMouseVolume(double mouseY) {
        int trackTop = getY() + VALUE_AREA_H + 10;
        int trackBot = getY() + getHeight() - 14;
        int trackH = trackBot - trackTop;
        float t = (float) (trackBot - mouseY) / Math.max(1, trackH);
        t = Math.max(0f, Math.min(1f, t));
        OSTPlayer.INSTANCE.setVolume(t);
    }

    @Override
    public void updateWidgetNarration(NarrationElementOutput builder) {
        builder.put(net.minecraft.client.gui.narration.NarrationPart.TITLE,
            "Volume " + Math.round(OSTPlayer.INSTANCE.getVolume() * 100) + " pourcent");
    }
}
