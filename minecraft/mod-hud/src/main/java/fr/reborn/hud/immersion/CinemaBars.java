package fr.reborn.hud.immersion;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

/**
 * Bandes noires « cinéma » (letterbox) en haut et en bas, pour l'immersion / les
 * screenshots RP. Quand actives, <b>tout le HUD est masqué sauf le chat</b> (via
 * {@code InGameHudCinemaMixin}). Toggle (touche bind) avec animation de
 * glissement. Le rendu des bandes + chat est piloté par le mixin (pas un
 * HudRenderCallback, sinon il serait annulé en même temps que le HUD).
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

    /** true tant que les bandes sont (au moins partiellement) visibles. */
    public boolean isProgressActive() { return progress > 0.005f; }

    public void registerClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    private void tick() {
        float target = enabled ? 1f : 0f;
        progress += (target - progress) * 0.25f; // easing
        if (Math.abs(target - progress) < 0.004f) progress = target;
    }

    /** Dessine les bandes (appelé par le mixin InGameHud). */
    public void renderBars(DrawContext ctx) {
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
