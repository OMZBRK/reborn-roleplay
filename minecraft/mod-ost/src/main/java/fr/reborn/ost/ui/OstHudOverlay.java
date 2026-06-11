package fr.reborn.ost.ui;

import fr.reborn.ost.audio.OstAudioEngine;
import fr.reborn.ost.audio.OstTrack;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

import java.util.Optional;

/**
 * HUD top-right qui affiche la piste en cours et le temps écoulé /
 * total. Sobre : texte blanc semi-transparent sur fond rounded sombre,
 * auto-masquage après 5 s sans changement.
 *
 * <p>Reste indépendant du {@link OstScreen} : la HUD s'affiche même
 * pendant qu'on joue, en hors-menu.
 */
public final class OstHudOverlay {

    private static final long AUTO_HIDE_DELAY_MS = 5_000L;
    private static final int PADDING = 6;
    private static final int RIGHT_MARGIN = 8;
    private static final int TOP_MARGIN = 8;

    private final OstAudioEngine engine;
    /** trackId de la dernière piste vue, pour détecter un changement. */
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
        if (currentOpt.isEmpty()) {
            lastSeenTrackId = null;
            return;
        }
        OstTrack current = currentOpt.get();

        long now = System.currentTimeMillis();
        if (!current.trackId().equals(lastSeenTrackId)) {
            lastSeenTrackId = current.trackId();
            lastChangeAtMs = now;
        }

        // Un écran ouvert (OstScreen, ESC, inventaire) occulte la HUD. On
        // gèle le timer pour que l'utilisateur ait son 5s plein une fois
        // l'écran fermé (sinon le menu OST consomme le délai et la HUD
        // n'apparaît jamais après sélection d'une piste).
        if (client.currentScreen != null) {
            lastChangeAtMs = now;
            return;
        }

        // Auto-hide après le délai : la piste continue mais on n'affiche
        // plus la HUD jusqu'au prochain changement.
        if (now - lastChangeAtMs > AUTO_HIDE_DELAY_MS) return;

        TextRenderer tr = client.textRenderer;
        String title = current.displayName();
        long elapsed = engine.elapsedMs() / 1000L;
        long total = engine.durationMs() / 1000L;
        String time = formatTime(elapsed) + " / " + formatTime(total);

        Text titleText = Text.literal("♪ " + title);
        Text timeText = Text.literal(time);
        int titleW = tr.getWidth(titleText);
        int timeW = tr.getWidth(timeText);
        int width = Math.max(titleW, timeW) + PADDING * 2;
        int height = tr.fontHeight * 2 + PADDING * 2 + 2;

        int x = ctx.getScaledWindowWidth() - width - RIGHT_MARGIN;
        int y = TOP_MARGIN;

        // Card BG + bord subtil.
        ctx.fill(x, y, x + width, y + height, 0xB0000000);
        ctx.fill(x, y, x + width, y + 1, 0x66FFFFFF);
        ctx.fill(x, y + height - 1, x + width, y + height, 0x66FFFFFF);
        ctx.fill(x, y, x + 1, y + height, 0x66FFFFFF);
        ctx.fill(x + width - 1, y, x + width, y + height, 0x66FFFFFF);

        ctx.drawText(tr, titleText, x + PADDING, y + PADDING, 0xFFFFFFFF, false);
        ctx.drawText(tr, timeText, x + PADDING, y + PADDING + tr.fontHeight + 2, 0xAAB5B5B5, false);
    }

    private static String formatTime(long seconds) {
        long m = seconds / 60;
        long s = seconds % 60;
        return String.format("%d:%02d", m, s);
    }
}
