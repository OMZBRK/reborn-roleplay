package fr.reborn.hud.runtime;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.element.HudAnchor;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementBounds;
import fr.reborn.hud.element.HudElementState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Helper utilise par les mixins HUD (BossBarHud, Gui, ChatComponent) pour
 * appliquer offset + scale + visibilité d'un élément.
 *
 * <p>Doit etre HORS du package {@code fr.reborn.hud.mixin.*} parce que
 * Mixin refuse les references directes a des classes de ce package depuis
 * d'autres classes Mixin (les Mixin transformer les chargent specialement
 * dans un classloader dedié).
 *
 * <p>Usage typique :
 * <pre>
 *   {@code @Inject(method = "render...", at = @At("HEAD"), cancellable = true)}
 *   private void rebornHead(..., CallbackInfo ci) {
 *       if (!HudTransform.isVisible(MY_ELEMENT)) { ci.cancel(); return; }
 *       HudTransform.apply(ctx, MY_ELEMENT);
 *   }
 *
 *   {@code @Inject(method = "render...", at = @At("RETURN"))}
 *   private void rebornReturn(..., CallbackInfo ci) {
 *       if (!HudTransform.isVisible(MY_ELEMENT)) return;
 *       HudTransform.revert(ctx);
 *   }
 * </pre>
 */
public final class HudTransform {

    private HudTransform() {}

    public static HudElementState readStateSafely(HudElement element) {
        try {
            return RebornHudClient.config().stateOf(element);
        } catch (IllegalStateException e) {
            return HudElementState.DEFAULT;
        }
    }

    public static boolean isVisible(HudElement element) {
        return readStateSafely(element).visible();
    }

    /** Push + translate + scale autour de l'anchor de l'élément. */
    public static void apply(GuiGraphicsExtractor ctx, HudElement element) {
        HudElementState state = readStateSafely(element);
        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        HudAnchor anchor = state.effectiveAnchor(element);
        HudElementBounds vanilla = HudElementBounds.vanillaFor(element, screenW, screenH);
        int ax = vanilla.x() + Math.round(vanilla.width()  * anchor.fx);
        int ay = vanilla.y() + Math.round(vanilla.height() * anchor.fy);

        ctx.pose().pushMatrix();
        ctx.pose().translate(state.x(), state.y());
        if (state.scale() != 1.0f) {
            ctx.pose().translate(ax, ay);
            ctx.pose().scale(state.scale(), state.scale());
            ctx.pose().translate(-ax, -ay);
        }
    }

    public static void revert(GuiGraphicsExtractor ctx) {
        ctx.pose().popMatrix();
    }
}
