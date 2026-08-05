package fr.reborn.hud.mixin;

import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.runtime.HudTransform;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin sur {@link BossHealthOverlay#extractRenderState} : applique offset +
 * scale + visibilité via {@link HudTransform}. Pattern HEAD push + RETURN pop,
 * cancel si !visible.
 *
 * <p>26.1 : {@code BossBarHud} → {@code BossHealthOverlay} ;
 * {@code render(...)} → {@code extractRenderState(GuiGraphicsExtractor)}.
 */
@Mixin(BossHealthOverlay.class)
public abstract class BossBarHudMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void reborn$pushBossBar(GuiGraphicsExtractor ctx, CallbackInfo ci) {
        if (!HudTransform.isVisible(HudElement.BOSS_BAR)) {
            ci.cancel();
            return;
        }
        HudTransform.apply(ctx, HudElement.BOSS_BAR);
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void reborn$popBossBar(GuiGraphicsExtractor ctx, CallbackInfo ci) {
        if (!HudTransform.isVisible(HudElement.BOSS_BAR)) return;
        HudTransform.revert(ctx);
    }
}
