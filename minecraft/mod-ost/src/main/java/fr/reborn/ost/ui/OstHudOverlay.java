package fr.reborn.ost.ui;

import fr.reborn.ost.audio.OstAudioEngine;
import fr.reborn.ost.audio.OstCategory;
import fr.reborn.ost.audio.OstTrack;
import fr.reborn.ost.audio.OstTrackMeta;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.Optional;

/**
 * HUD top-right « now-playing » (style Reborn) : petit vinyl tournant + titre +
 * barre de progression. Auto-masquage après 5 s sans changement de piste ;
 * masqué quand un écran est ouvert.
 */
public final class OstHudOverlay {

    private static final long AUTO_HIDE_DELAY_MS = 5_000L;
    private static final int PAD = 6, RIGHT_MARGIN = 8, TOP_MARGIN = 8, VINYL_SZ = 22;

    private static final Identifier VINYL = Identifier.of("reborn-ost", "textures/gui/vinyl.png");
    private static final int BACKDROP = 0xCC0C0709;
    private static final int ACCENT   = 0xFFA0182B;
    private static final int GOLD     = 0xFFD9A95E;
    private static final int MUTED    = 0xFF9A8B78;

    private final OstAudioEngine engine;
    private String lastSeenTrackId = null;
    private long lastChangeAtMs = 0L;

    public OstHudOverlay(OstAudioEngine engine) {
        this.engine = engine;
    }

    public void registerClient() {
        HudRenderCallback.EVENT.register((ctx, tickDelta) -> render(ctx));
    }

    private void render(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.options.hudHidden) return;

        Optional<OstTrack> currentOpt = engine.currentTrack();
        if (currentOpt.isEmpty()) { lastSeenTrackId = null; return; }
        OstTrack current = currentOpt.get();

        long now = System.currentTimeMillis();
        if (!current.trackId().equals(lastSeenTrackId)) {
            lastSeenTrackId = current.trackId();
            lastChangeAtMs = now;
        }
        if (client.currentScreen != null) { lastChangeAtMs = now; return; }
        if (now - lastChangeAtMs > AUTO_HIDE_DELAY_MS) return;

        TextRenderer tr = client.textRenderer;
        String title = OstTrackMeta.title(current.trackId(), current.displayName());
        long el = engine.elapsedMs(), du = engine.durationMs();
        String time = mmss(el / 1000) + " / " + mmss(du / 1000);

        int textW = Math.max(tr.getWidth("♪ " + title), tr.getWidth(time));
        int w = PAD + VINYL_SZ + 8 + textW + PAD;
        int h = PAD + VINYL_SZ + PAD;
        int x = ctx.getScaledWindowWidth() - w - RIGHT_MARGIN;
        int y = TOP_MARGIN;

        ctx.fill(x, y, x + w, y + h, BACKDROP);
        ctx.fill(x, y, x + w, y + 1, ACCENT);
        ctx.fill(x, y + h - 1, x + w, y + h, ACCENT);

        drawVinyl(ctx, client, current, x + PAD, y + PAD, VINYL_SZ, el / 1000f * 70f);

        int tx = x + PAD + VINYL_SZ + 8;
        ctx.drawText(tr, Text.literal("♪ " + title), tx, y + PAD, GOLD, false);
        ctx.drawText(tr, Text.literal(time), tx, y + PAD + 11, MUTED, false);

        int bx = tx, bw = (x + w - PAD) - tx, by = y + h - PAD + 1;
        ctx.fill(bx, by, bx + bw, by + 2, 0x40FFFFFF);
        if (du > 0) {
            int fw = (int) (bw * Math.min(1.0, el / (double) du));
            ctx.fill(bx, by, bx + fw, by + 2, GOLD);
        }
    }

    private void drawVinyl(DrawContext ctx, MinecraftClient mc, OstTrack track,
                           int x, int y, int size, float angle) {
        if (!mc.getResourceManager().getResource(VINYL).isPresent()) {
            ctx.fill(x, y, x + size, y + size, categoryColor(track.category()));
            return;
        }
        ctx.getMatrices().push();
        ctx.getMatrices().translate(x + size / 2f, y + size / 2f, 0);
        ctx.getMatrices().multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Z.rotationDegrees(angle));
        ctx.getMatrices().translate(-(x + size / 2f), -(y + size / 2f), 0);
        ctx.drawTexture(VINYL, x, y, 0f, 0f, size, size, 64, 64);
        int cx = x + size / 2, cy = y + size / 2;
        int tint = (categoryColor(track.category()) & 0x00FFFFFF) | 0x88000000;
        int r = Math.max(2, Math.round(size * 0.20f));
        for (int dy = -r; dy <= r; dy++) {
            int dx = (int) Math.round(Math.sqrt((double) r * r - dy * dy));
            ctx.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, tint);
        }
        ctx.getMatrices().pop();
    }

    private static int categoryColor(OstCategory cat) {
        return switch (cat) {
            case APAISANT -> 0xFF3FA89B;
            case COMBAT -> 0xFFB23A3A;
            case MISSION -> 0xFF3A6BB2;
            case MOTIVATION -> 0xFFD98E3A;
            case MYSTERE -> 0xFF7E3AB2;
            case TRISTE -> 0xFF5A6B7E;
            case FAVORIS -> 0xFFB23A6B;
        };
    }

    private static String mmss(long seconds) {
        return String.format("%d:%02d", seconds / 60, seconds % 60);
    }
}
