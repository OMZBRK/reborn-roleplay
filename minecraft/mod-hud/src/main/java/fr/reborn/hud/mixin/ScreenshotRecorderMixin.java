package fr.reborn.hud.mixin;

import fr.reborn.hud.screenshot.CapturePreview;
import net.minecraft.client.util.ScreenshotRecorder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Déclenche la {@link CapturePreview} après chaque sauvegarde de screenshot
 * (F2 ou Mode Photo passent tous les deux par {@code saveScreenshot}).
 */
@Mixin(ScreenshotRecorder.class)
public class ScreenshotRecorderMixin {

    @Inject(
        method = "saveScreenshot(Ljava/io/File;Lnet/minecraft/client/gl/Framebuffer;Ljava/util/function/Consumer;)V",
        at = @At("TAIL"))
    private static void reborn$onScreenshot(CallbackInfo ci) {
        CapturePreview.INSTANCE.markPending();
    }
}
