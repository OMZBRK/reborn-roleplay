package fr.reborn.hud.immersion;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Bandes noires « cinéma » (letterbox) en haut et en bas de l'écran, pour
 * l'immersion / les screenshots RP. Toggle (touche bind) avec une animation
 * de glissement. Chaque bande fait ~12% de la hauteur écran.
 */
public final class CinemaBars {

    public static final CinemaBars INSTANCE = new CinemaBars();

    private static final float BAR_FRACTION = 0.12f;

    private boolean enabled = false;
    /** Progression d'animation 0 (rétractées) → 1 (déployées). */
    private float progress = 0f;

    private CinemaBars() {}

    public void toggle() { enabled = !enabled; }
    public boolean isEnabled() { return enabled; }

    public void registerClient() {
        HudRenderCallback.EVENT.register((ctx, tickDelta) -> render(ctx));
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    private void tick() {
        float target = enabled ? 1f : 0f;
        progress += (target - progress) * 0.25f; // easing
        if (Math.abs(target - progress) < 0.004f) progress = target;
    }

    private void render(DrawContext ctx) {
        if (progress <= 0.001f) return;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options.hudHidden) return;
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        int barH = Math.round(h * BAR_FRACTION * progress);
        if (barH <= 0) return;
        ctx.fill(0, 0, w, barH, 0xFF000000);
        ctx.fill(0, h - barH, w, h, 0xFF000000);
    }
}
