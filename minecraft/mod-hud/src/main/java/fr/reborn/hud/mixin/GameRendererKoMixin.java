package fr.reborn.hud.mixin;

import fr.reborn.hud.ko.KoClient;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** KO et concentration : fondus, rythme cardiaque et textures de paramètres des filtres mis à jour à chaque image. */
@Mixin(GameRenderer.class)
public abstract class GameRendererKoMixin {

    @Inject(method = "render(Lnet/minecraft/client/DeltaTracker;Z)V", at = @At("HEAD"))
    private void reborn$koFrame(DeltaTracker delta, boolean renderLevel, CallbackInfo ci) {
        KoClient.frame();
        fr.reborn.hud.concentration.ModeConcentration.frame();   // avancement de l'effet de la concentration
    }
}
