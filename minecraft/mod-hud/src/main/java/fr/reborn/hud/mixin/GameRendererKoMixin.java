package fr.reborn.hud.mixin;

import fr.reborn.hud.ko.KoClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** KO : fondus, rythme cardiaque et texture de paramètres du filtre mis à jour à chaque image. */
@Mixin(GameRenderer.class)
public abstract class GameRendererKoMixin {

    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("HEAD"))
    private void reborn$koFrame(DeltaTracker delta, boolean renderLevel, CallbackInfo ci) {
        KoClient.frame();
    }
}
