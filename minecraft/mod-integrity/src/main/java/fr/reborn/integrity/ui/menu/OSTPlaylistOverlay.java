package fr.reborn.integrity.ui.menu;

import fr.reborn.integrity.ui.Colors;
import fr.reborn.integrity.ui.DrawHelpers;
import fr.reborn.integrity.ui.OSTPlayer;
import fr.reborn.integrity.ui.RebornFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

/**
 * Overlay playlist OST — sidebar à gauche du screen avec la liste
 * scrollable des 43 pistes. Visible/cliquable seulement quand
 * {@link OSTPlayer#isPlaylistOpen()} est true.
 *
 * <p>Layout (260 × full-height-ish) :
 * <pre>
 *   ┌──────────────────────┐
 *   │ Bande-son Reborn  43 │  ← header
 *   ├──────────────────────┤
 *   │ ▶  Track 01  03:00   │  ← row track (active highlight)
 *   │    Track 02  03:00   │
 *   │    Track 03  03:00   │
 *   │ ...                  │
 *   ├──────────────────────┤
 *   │ 43 pistes · 47 min   │  ← footer
 *   └──────────────────────┘
 * </pre>
 */
public class OSTPlaylistOverlay extends ClickableWidget {

    public static final int WIDTH = 260;
    private static final int HEADER_H = 36;
    private static final int FOOTER_H = 24;
    private static final int ROW_H = 30;

    private int scrollOffset = 0;

    public OSTPlaylistOverlay(int x, int y, int height) {
        super(x, y, WIDTH, height, Text.literal("Playlist OST"));
    }

    public boolean isOpen() {
        return OSTPlayer.INSTANCE.isPlaylistOpen();
    }

    @Override
    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!isOpen()) return false;
        return super.isMouseOver(mouseX, mouseY);
    }

    @Override
    protected void renderWidget(DrawContext ctx, int mouseX, int mouseY, float delta) {
        if (!isOpen()) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null) return;
        TextRenderer tr = mc.textRenderer;

        int x = getX();
        int y = getY();
        int w = getWidth();
        int h = getHeight();
        OSTPlayer ost = OSTPlayer.INSTANCE;

        // Card BG.
        DrawHelpers.roundedOutlinedRect(ctx, x, y, w, h, 10,
            Colors.SURFACE, Colors.BORDER_STRONG);

        // ─── Header ───
        ctx.drawText(tr, RebornFont.bold("BANDE-SON · REBORN"),
            x + 14, y + 14, Colors.WHITE_PURE, false);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + w - 30, y + 16, 0);
        ctx.getMatrices().scale(0.85f, 0.85f, 1f);
        ctx.drawText(tr, RebornFont.body(OSTPlayer.TOTAL_TRACKS + ""),
            0, 0, Colors.FOREGROUND_MUTED, false);
        ctx.getMatrices().pop();
        ctx.fill(x + 8, y + HEADER_H - 1, x + w - 8, y + HEADER_H, Colors.BORDER);

        // ─── Body (rows scrollables) ───
        int bodyTop = y + HEADER_H;
        int bodyBot = y + h - FOOTER_H;
        int visibleRows = (bodyBot - bodyTop) / ROW_H;

        // Clip pour le scroll.
        ctx.enableScissor(x, bodyTop, x + w, bodyBot);

        int firstVisible = scrollOffset / ROW_H;
        for (int i = firstVisible; i < OSTPlayer.TOTAL_TRACKS && i < firstVisible + visibleRows + 2; i++) {
            int rowY = bodyTop + i * ROW_H - scrollOffset;
            renderTrackRow(ctx, tr, x, rowY, w, i + 1, mouseX, mouseY);
        }

        ctx.disableScissor();

        // ─── Footer ───
        int footY = y + h - FOOTER_H;
        ctx.fill(x + 8, footY, x + w - 8, footY + 1, Colors.BORDER);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + 14, footY + 8, 0);
        ctx.getMatrices().scale(0.85f, 0.85f, 1f);
        ctx.drawText(tr, RebornFont.body(OSTPlayer.TOTAL_TRACKS + " pistes embarquées"),
            0, 0, Colors.FOREGROUND_MUTED, false);
        ctx.getMatrices().pop();
    }

    private void renderTrackRow(DrawContext ctx, TextRenderer tr, int parentX, int rowY,
                                int parentW, int trackIdx, int mouseX, int mouseY) {
        OSTPlayer ost = OSTPlayer.INSTANCE;
        boolean active = ost.getCurrentTrack() == trackIdx;
        boolean hovered = mouseX >= parentX && mouseX < parentX + parentW
            && mouseY >= rowY && mouseY < rowY + ROW_H && isOpen();

        // Background si active ou hover.
        if (active) {
            DrawHelpers.roundedRect(ctx, parentX + 4, rowY + 2, parentW - 8, ROW_H - 4, 4,
                Colors.ACCENT_SOFT);
        } else if (hovered) {
            DrawHelpers.roundedRect(ctx, parentX + 4, rowY + 2, parentW - 8, ROW_H - 4, 4,
                Colors.SURFACE_ELEVATED);
        }

        // Indicateur play à gauche.
        if (active && ost.isPlaying()) {
            // Mini triangle play.
            int triX = parentX + 12;
            int triCy = rowY + ROW_H / 2;
            for (int dy = -3; dy <= 3; dy++) {
                int width = 3 - Math.abs(dy);
                if (width > 0) {
                    ctx.fill(triX, triCy + dy, triX + width, triCy + dy + 1, Colors.ACCENT);
                }
            }
        }

        // Nom de la piste.
        int textColor = active ? Colors.WHITE_PURE : (hovered ? Colors.FOREGROUND : Colors.FOREGROUND_SUBTLE);
        ctx.drawText(tr, RebornFont.bold(ost.getTrackNameAt(trackIdx)),
            parentX + 24, rowY + 6, textColor, false);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(parentX + 24, rowY + 18, 0);
        ctx.getMatrices().scale(0.8f, 0.8f, 1f);
        ctx.drawText(tr, RebornFont.body("Reborn OST"), 0, 0, Colors.FOREGROUND_MUTED, false);
        ctx.getMatrices().pop();
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        if (!isOpen()) return;

        int bodyTop = getY() + HEADER_H;
        int bodyBot = getY() + getHeight() - FOOTER_H;
        if (mouseY < bodyTop || mouseY >= bodyBot) return;

        int relY = (int) (mouseY - bodyTop + scrollOffset);
        int trackIdx = relY / ROW_H + 1;
        if (trackIdx >= 1 && trackIdx <= OSTPlayer.TOTAL_TRACKS) {
            OSTPlayer.INSTANCE.playTrack(trackIdx);
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                 double horizontalAmount, double verticalAmount) {
        if (!isOpen() || !isMouseOver(mouseX, mouseY)) return false;
        int maxScroll = Math.max(0, OSTPlayer.TOTAL_TRACKS * ROW_H
            - (getHeight() - HEADER_H - FOOTER_H));
        scrollOffset = Math.max(0,
            Math.min(maxScroll, scrollOffset - (int) (verticalAmount * 30)));
        return true;
    }

    @Override
    public void appendClickableNarrations(NarrationMessageBuilder builder) {
        builder.put(net.minecraft.client.gui.screen.narration.NarrationPart.TITLE,
            "Playlist OST — " + OSTPlayer.TOTAL_TRACKS + " pistes");
    }
}
