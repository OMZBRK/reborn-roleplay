package fr.reborn.hud.mixin;

import fr.reborn.hud.screenshot.CapturePreview;
import net.minecraft.client.Screenshot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Déclenche la {@link CapturePreview} après chaque sauvegarde de screenshot
 * (F2 ou Mode Photo passent tous les deux par {@code Screenshot.grab}).
 *
 * <p><b>26.2</b> : {@code saveScreenshot(File, Framebuffer, Consumer)} a disparu
 * ; le point d'entrée est désormais {@code grab(File, RenderTarget, Consumer)}
 * (le Mode Photo Reborn l'appelle déjà, cf {@code InGameHudCinemaMixin}).
 */
@Mixin(Screenshot.class)
public class ScreenshotRecorderMixin {

    @Inject(
        method = "grab(Ljava/io/File;Lcom/mojang/blaze3d/pipeline/RenderTarget;Ljava/util/function/Consumer;)V",
        at = @At("TAIL"))
    private static void reborn$onScreenshot(CallbackInfo ci) {
        CapturePreview.INSTANCE.markPending();
    }
}
