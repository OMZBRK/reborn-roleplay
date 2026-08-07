package fr.reborn.hud.immersion;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Bandes noires « cinéma » (letterbox) en haut et en bas, pour l'immersion / les
 * screenshots RP. La touche de bascule (K) <b>cycle sur trois états</b> :
 * <ol>
 *   <li>{@code OFF} — rien ;</li>
 *   <li>{@code BARS_HUD} — bandes <b>par-dessus le HUD</b> (HUD visible) ;</li>
 *   <li>{@code BARS_NO_HUD} — bandes seules, <b>HUD masqué</b> (rendu clean).</li>
 * </ol>
 * Le rendu des bandes (et le masquage du HUD) est piloté par
 * {@code InGameHudCinemaMixin} — pas par un HudRenderCallback, sinon il serait
 * annulé en même temps que le HUD. Animation de glissement dans {@link #tick()}.
 */
public final class CinemaBars {

    public static final CinemaBars INSTANCE = new CinemaBars();

    private static final float BAR_FRACTION = 0.12f;

    /** Cycle : OFF → bandes+HUD → bandes sans HUD → OFF. */
    public enum Mode { OFF, BARS_HUD, BARS_NO_HUD }

    private Mode mode = Mode.OFF;
    /** Progression d'animation 0 (rétractées) → 1 (déployées). */
    private float progress = 0f;

    private CinemaBars() {}

    /** Cycle : OFF → avec HUD → sans HUD → OFF. */
    public void toggle() {
        mode = switch (mode) {
            case OFF -> Mode.BARS_HUD;
            case BARS_HUD -> Mode.BARS_NO_HUD;
            case BARS_NO_HUD -> Mode.OFF;
        };
    }

    public Mode mode() { return mode; }
    public boolean isEnabled() { return mode != Mode.OFF; }
    /** Vrai si le mode courant masque le HUD (bandes seules, rendu clean). */
    public boolean hidesHud() { return mode == Mode.BARS_NO_HUD; }

    /** true tant que les bandes sont (au moins partiellement) visibles. */
    public boolean isProgressActive() { return progress > 0.005f; }

    public void registerClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    private void tick() {
        float target = (mode != Mode.OFF) ? 1f : 0f;
        progress += (target - progress) * 0.25f; // easing
        if (Math.abs(target - progress) < 0.004f) progress = target;
    }

    /** Dessine les bandes (appelé par le mixin Gui). */
    public void renderBars(GuiGraphicsExtractor ctx) {
        if (progress <= 0.001f) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui) return;
        int w = ctx.guiWidth();
        int h = ctx.guiHeight();
        int barH = Math.round(h * BAR_FRACTION * progress);
        if (barH <= 0) return;
        ctx.fill(0, 0, w, barH, 0xFF000000);
        ctx.fill(0, h - barH, w, h, 0xFF000000);
    }
}
