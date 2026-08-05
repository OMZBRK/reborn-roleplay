package fr.reborn.hud.screenshot;

import fr.reborn.hud.menu.Colors;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

import java.nio.file.Path;
import java.util.List;

/**
 * Preview automatique après une capture : une petite carte glisse dans le coin
 * bas-droit avec la vignette du dernier screenshot + un rappel « [G] Galerie »,
 * puis disparaît. Déclenchée par {@code ScreenshotRecorderMixin} (F2 + photo).
 */
public final class CapturePreview {

    public static final CapturePreview INSTANCE = new CapturePreview();

    private static final long DISPLAY_MS = 6000, SLIDE_MS = 250;
    private static final int TW = 128, TH = 72, PAD = 6;

    private Path path;
    private long shownAt = 0;
    private boolean pending = false;
    private long pendingSince = 0;

    private CapturePreview() {}

    /** Appelé juste après une sauvegarde de screenshot (fichier écrit en async). */
    public void markPending() {
        pending = true;
        pendingSince = System.currentTimeMillis();
    }

    public void registerClient() {
        HudElementRegistry.addLast(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("reborn-hud", "capture-preview"),
            (ctx, tickCounter) -> render(ctx));
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    private void tick() {
        // Laisse ~0.4s au writer async, puis prend le plus récent.
        if (pending && System.currentTimeMillis() - pendingSince > 400) {
            pending = false;
            List<ScreenshotLibrary.Entry> list = ScreenshotLibrary.list(false);
            if (!list.isEmpty()) {
                path = list.get(0).path();
                shownAt = System.currentTimeMillis();
            }
        }
    }

    private void render(GuiGraphicsExtractor ctx) {
        if (path == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        long age = System.currentTimeMillis() - shownAt;
        if (age > DISPLAY_MS) return;

        // Slide-in / slide-out.
        float in = Math.min(1f, age / (float) SLIDE_MS);
        float out = age > DISPLAY_MS - SLIDE_MS ? (DISPLAY_MS - age) / (float) SLIDE_MS : 1f;
        float t = Math.max(0f, Math.min(in, out));
        float ease = 1f - (1f - t) * (1f - t);

        var tr = mc.font;
        int w = TW + PAD * 2, h = TH + PAD * 2 + 22;
        int fullX = ctx.guiWidth() - w - 8;
        int x = fullX + (int) ((1f - ease) * (w + 12));
        int y = ctx.guiHeight() - h - 8;

        ctx.fill(x, y, x + w, y + h, 0xE60C0709);
        ctx.fill(x, y, x + w, y + 1, Colors.ACCENT);

        ScreenshotTextures.Tex tex = ScreenshotTextures.get(path);
        int ix = x + PAD, iy = y + PAD;
        if (tex != null) {
            ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, tex.id(), ix, iy, 0f, 0f, TW, TH, tex.w(), tex.h());
        } else {
            ctx.fill(ix, iy, ix + TW, iy + TH, 0xFF1A0E12);
        }
        ctx.fill(ix - 1, iy - 1, ix + TW + 1, iy, Colors.BORDER);

        ctx.text(tr, Component.literal("Capture enregistrée").withStyle(s -> s.withBold(true)),
            x + PAD, y + TH + PAD + 2, Colors.GOLD, false);
        ctx.text(tr, Component.literal("[G] Galerie"), x + PAD, y + TH + PAD + 12, Colors.FOREGROUND_MUTED, false);
    }
}
