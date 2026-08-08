package fr.reborn.hud.mixin;

import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.runtime.HudTransform;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.contextualbar.ExperienceBarRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rend la barre d'XP repositionnable via l'éditeur HUD ({@link HudElement#EXPERIENCE_BAR}).
 *
 * <p>En 26.1 la barre d'XP n'est PLUS extraite par {@code Gui.extract*} mais par
 * {@link ExperienceBarRenderer} (un {@code ContextualBarRenderer}) — d'où un mixin
 * dédié. On enrobe {@code extractBackground} + {@code extractRenderState} d'un
 * push/pop de la transform (fond + remplissage suivent le même offset). Un HEAD
 * annulable masque la barre si l'élément est caché.
 */
@Mixin(ExperienceBarRenderer.class)
public abstract class ExperienceBarRendererMixin {

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void reborn$pushXpBg(GuiGraphicsExtractor ctx, DeltaTracker tick, CallbackInfo ci) {
        if (!HudTransform.isVisible(HudElement.EXPERIENCE_BAR)) { ci.cancel(); return; }
        HudTransform.apply(ctx, HudElement.EXPERIENCE_BAR);
    }

    @Inject(method = "extractBackground", at = @At("RETURN"))
    private void reborn$popXpBg(GuiGraphicsExtractor ctx, DeltaTracker tick, CallbackInfo ci) {
        if (!HudTransform.isVisible(HudElement.EXPERIENCE_BAR)) return;
        HudTransform.revert(ctx);
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void reborn$pushXp(GuiGraphicsExtractor ctx, DeltaTracker tick, CallbackInfo ci) {
        if (!HudTransform.isVisible(HudElement.EXPERIENCE_BAR)) { ci.cancel(); return; }
        HudTransform.apply(ctx, HudElement.EXPERIENCE_BAR);
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void reborn$popXp(GuiGraphicsExtractor ctx, DeltaTracker tick, CallbackInfo ci) {
        if (!HudTransform.isVisible(HudElement.EXPERIENCE_BAR)) return;
        HudTransform.revert(ctx);
    }
}
