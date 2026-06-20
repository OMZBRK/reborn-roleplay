package fr.reborn.hud.mixin;

import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.runtime.HudTransform;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.BossBarHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin sur {@link BossBarHud#render} : applique offset + scale + visibilité
 * via {@link HudTransform}. Pattern HEAD push + RETURN pop, cancel si
 * !visible.
 */
@Mixin(BossBarHud.class)
public abstract class BossBarHudMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void reborn$pushBossBar(DrawContext ctx, CallbackInfo ci) {
        if (!HudTransform.isVisible(HudElement.BOSS_BAR)) {
            ci.cancel();
            return;
        }
        HudTransform.apply(ctx, HudElement.BOSS_BAR);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void reborn$popBossBar(DrawContext ctx, CallbackInfo ci) {
        if (!HudTransform.isVisible(HudElement.BOSS_BAR)) return;
        HudTransform.revert(ctx);
    }
}
