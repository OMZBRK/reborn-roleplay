package fr.reborn.hud.mixin;

import fr.reborn.hud.meteo.MeteoClient;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Météo : brouillard en distance (le seul endroit où le jeu connaît la vraie distance de chaque pixel).
 * Sable : de presque rien (petit vent) jusqu'au mur de sable chaud (~12 blocs à 100 %). Brume : la visibilité suit l'intensité sur une
 * courbe exponentielle, d'un voile léger (~70 blocs) jusqu'à un mur blanc (~5 blocs) à 100 %.
 * Pluie : rideau bleu nuit (~40 blocs à 100 %), fondu avec l'intensité, par-dessus le brouillard de pluie du jeu.
 */
@Mixin(FogRenderer.class)
public abstract class FogRendererMeteoMixin {

    @Inject(method = "setupFog(Lnet/minecraft/client/Camera;ILnet/minecraft/client/DeltaTracker;FLnet/minecraft/client/multiplayer/ClientLevel;)Lnet/minecraft/client/renderer/fog/FogData;",
            at = @At("RETURN"))
    private void reborn$meteoFog(Camera camera, int renderDistance, DeltaTracker delta, float darken, ClientLevel level,
                                 CallbackInfoReturnable<FogData> cir) {
        float i = MeteoClient.intensite();
        byte type = MeteoClient.type();
        if (i <= 0.001f || type == MeteoClient.CLAIR) return;
        FogData fog = cir.getReturnValue();
        if (fog == null) return;
        float fin, r, g, b, k;
        if (type == MeteoClient.SABLE) {
            // petit vent (~25 %) : ~100 blocs, vent moyen (~55 %) : ~48, grosse tempête (~90 %) : ~17, 100 % : 12
            k = Math.min(1f, i * 4f);
            fin = lerpDistance(fog.environmentalEnd, (float) (140f * Math.pow(12f / 140f, Math.pow(i, 1.4))), k);
            r = 0.74f; g = 0.58f; b = 0.38f;
        } else if (type == MeteoClient.PLUIE) {
            k = i * i * (3f - 2f * i);
            fin = lerpDistance(fog.renderDistanceEnd, 40f, k);
            r = 0.36f; g = 0.42f; b = 0.53f;
            // le jeu épaissit déjà fortement son brouillard sous la pluie : nos valeurs le remplacent
            // (sauf caméra dans un liquide, où le brouillard sous-marin du jeu doit rester)
            if (level.getFluidState(camera.blockPosition()).isEmpty()) {
                fog.environmentalStart = fin * 0.1f;
                fog.environmentalEnd = fin;
            }
        } else {
            k = Math.min(1f, i * 4f);                         // fondu depuis le brouillard normal (pas de saut)
            fin = lerpDistance(fog.environmentalEnd, (float) (72f * Math.pow(5f / 72f, i)), k);
            r = 0.80f; g = 0.82f; b = 0.84f;
            k = Math.min(1f, i * 2.5f);
        }
        fog.environmentalStart = Math.min(fog.environmentalStart, 0f);
        fog.environmentalEnd = Math.min(fog.environmentalEnd, fin);
        fog.renderDistanceStart = Math.min(fog.renderDistanceStart, 0f);
        fog.renderDistanceEnd = Math.min(fog.renderDistanceEnd, fin);
        fog.skyEnd = Math.min(fog.skyEnd, fin);
        fog.cloudEnd = Math.min(fog.cloudEnd, fin * 1.5f);
        if (fog.color != null) {
            fog.color.set(lerp(fog.color.x(), r, k), lerp(fog.color.y(), g, k), lerp(fog.color.z(), b, k), fog.color.w());
        }
    }

    private static float lerp(float a, float b, float t) { return a + (b - a) * t; }

    /** Fondu entre deux distances en densité (1/d) : le brouillard s'épaissit dès le début du fondu. */
    private static float lerpDistance(float a, float b, float t) {
        return 1f / lerp(1f / Math.max(a, 0.01f), 1f / Math.max(b, 0.01f), t);
    }
}
