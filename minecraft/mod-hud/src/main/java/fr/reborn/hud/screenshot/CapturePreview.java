package fr.reborn.hud.screenshot;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

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
    private static final int TW = 112, TH = 63, PAD = 7;

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
        if (mc.gui.hud.isHidden() || mc.gui.screen() != null) return;
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

        // Carte laquée DA : tirage dans un passe-partout crème + légende.
        fr.reborn.hud.ui.Da.panel(ctx, x, y, w, h);
        int ix = x + PAD, iy = y + PAD;
        ctx.fill(ix - 2, iy - 2, ix + TW + 2, iy + TH + 2, 0xFFF0E8D6);
        ScreenshotTextures.Tex tex = ScreenshotTextures.thumb(path);
        if (tex != null) {
            ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, tex.id(), ix, iy, 0f, 0f, TW, TH, tex.w(), tex.h(), tex.w(), tex.h());
        } else {
            ctx.fill(ix, iy, ix + TW, iy + TH, 0xFF2A2022);
        }
        float sc = fr.reborn.hud.ui.Da.small();
        fr.reborn.hud.ui.Da.text(ctx, tr, "Capture enregistree", x + PAD, y + TH + PAD + 5, sc, fr.reborn.hud.ui.Da.GOLD, 0);
        fr.reborn.hud.ui.Da.text(ctx, tr, "[G] Galerie", x + PAD, y + TH + PAD + 14, sc, fr.reborn.hud.ui.Da.MUTED, 0);
    }
}
