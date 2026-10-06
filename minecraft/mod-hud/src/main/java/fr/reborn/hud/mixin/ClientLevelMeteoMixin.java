package fr.reborn.hud.mixin;

import fr.reborn.hud.meteo.MeteoClient;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Météo « pluie » : il pleut partout (pas de neige ni de zone sèche selon le biome) tant que la météo est active. */
@Mixin(ClientLevel.class)
public abstract class ClientLevelMeteoMixin {

    @Inject(method = "getPrecipitationAt", at = @At("HEAD"), cancellable = true)
    private void reborn$meteoPrecipitation(BlockPos pos, CallbackInfoReturnable<Biome.Precipitation> cir) {
        if (MeteoClient.pluie3D()) cir.setReturnValue(Biome.Precipitation.RAIN);
    }
}
