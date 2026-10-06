package fr.reborn.hud.mixin;

import fr.reborn.hud.meteo.MeteoClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Météo « pluie » : le monde du client se croit sous la pluie avec l'intensité de la météo. Ça active la pluie en 3D
 * (colonnes autour du joueur, arrêtées par les toits), les éclaboussures au sol, le son et le ciel couvert du jeu.
 * L'apparence des gouttes est la nôtre ({@code WeatherEffectRendererMeteoMixin}). Seulement côté client.
 */
@Mixin(Level.class)
public abstract class LevelRainMeteoMixin {

    @Inject(method = "getRainLevel(F)F", at = @At("RETURN"), cancellable = true)
    private void reborn$meteoPluie(float partialTick, CallbackInfoReturnable<Float> cir) {
        if (!((Object) this instanceof ClientLevel) || !MeteoClient.pluie3D()) return;
        float i = MeteoClient.intensite();
        if (i > cir.getReturnValueF()) cir.setReturnValue(i);
    }
}
