package fr.reborn.hud.mixin;

import fr.reborn.hud.meteo.MeteoClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Météo : fondus + texture de paramètres du filtre mis à jour à chaque image (même HUD masqué). */
@Mixin(GameRenderer.class)
public abstract class GameRendererMeteoMixin {

    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("HEAD"))
    private void reborn$meteoFrame(DeltaTracker delta, boolean renderLevel, CallbackInfo ci) {
        MeteoClient.frame();
    }
}
