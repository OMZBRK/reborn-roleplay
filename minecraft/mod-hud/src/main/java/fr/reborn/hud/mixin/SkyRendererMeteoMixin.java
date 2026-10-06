package fr.reborn.hud.mixin;

import fr.reborn.hud.meteo.MeteoClient;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Météo « pluie » : le disque du soleil disparaît derrière les nuages (dans le sable et la brume il reste, voilé —
 * c'est lui qui donne les faisceaux de lumière).
 */
@Mixin(SkyRenderer.class)
public abstract class SkyRendererMeteoMixin {

    @ModifyVariable(method = "renderSun", at = @At("HEAD"), argsOnly = true, ordinal = 0)
    private float reborn$soleilSousLaPluie(float alpha) {
        if (MeteoClient.type() != MeteoClient.PLUIE) return alpha;
        return alpha * Math.max(0f, 1f - MeteoClient.intensite() * 2f);
    }
}
