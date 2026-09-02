package fr.reborn.hud.immersion;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Modes de présentation « cinéma » pour l'immersion / les screenshots RP. La
 * touche de bascule (K) <b>cycle sur trois états distincts</b> :
 * <ol>
 *   <li>{@code HUD} — jeu normal, HUD visible, pas de bandes (état de repos) ;</li>
 *   <li>{@code CLEAN} — HUD masqué, <b>écran plein propre</b> (sans bandes) ;</li>
 *   <li>{@code BARS} — HUD masqué + <b>bandes noires</b> (letterbox cinématique).</li>
 * </ol>
 * Le masquage du HUD et le rendu des bandes sont pilotés par
 * {@code InGameHudCinemaMixin} — pas par un HudRenderCallback, sinon ils seraient
 * annulés en même temps que le HUD. Animation de glissement dans {@link #tick()}.
 */
public final class CinemaBars {

    public static final CinemaBars INSTANCE = new CinemaBars();

    private static final float BAR_FRACTION = 0.12f;

    /** Cycle : HUD (repos) → CLEAN (sans HUD) → BARS (bandes) → HUD. */
    public enum Mode { HUD, CLEAN, BARS }

    private Mode mode = Mode.HUD;
    /** Progression d'animation des bandes 0 (rétractées) → 1 (déployées). */
    private float progress = 0f;

    private CinemaBars() {}

    /** Cycle : HUD → CLEAN (écran plein sans HUD) → BARS (bandes noires) → HUD. */
    public void toggle() {
        mode = switch (mode) {
            case HUD -> Mode.CLEAN;
            case CLEAN -> Mode.BARS;
            case BARS -> Mode.HUD;
        };
    }

    public Mode mode() { return mode; }
    /** Vrai dès qu'un effet est actif (HUD masqué ou bandes). */
    public boolean isEnabled() { return mode != Mode.HUD; }
    /** Vrai si le mode courant masque le HUD (CLEAN ou BARS). */
    public boolean hidesHud() { return mode != Mode.HUD; }
    /** Vrai si les bandes doivent être dessinées (mode BARS uniquement). */
    public boolean barsActive() { return mode == Mode.BARS; }

    /** true tant que les bandes sont (au moins partiellement) visibles. */
    public boolean isProgressActive() { return progress > 0.005f; }

    public void registerClient() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick());
    }

    private void tick() {
        float target = (mode == Mode.BARS) ? 1f : 0f;
        progress += (target - progress) * 0.25f; // easing
        if (Math.abs(target - progress) < 0.004f) progress = target;
    }

    /** Dessine les bandes (appelé par le mixin Gui). */
    public void renderBars(GuiGraphicsExtractor ctx) {
        if (progress <= 0.001f) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.gui.hud.isHidden()) return;
        int w = ctx.guiWidth();
        int h = ctx.guiHeight();
        int barH = Math.round(h * BAR_FRACTION * progress);
        if (barH <= 0) return;
        ctx.fill(0, 0, w, barH, 0xFF000000);
        ctx.fill(0, h - barH, w, h, 0xFF000000);
    }
}
