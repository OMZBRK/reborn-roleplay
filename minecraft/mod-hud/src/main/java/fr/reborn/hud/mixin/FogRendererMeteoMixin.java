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
 * <p><b>Sodium</b> copie le brouillard pour ses chunks à la sortie de {@code setupFog} (son propre {@code @Inject} RETURN) :
 * priorité 500 pour passer <b>avant</b> lui, sinon le terrain garde le brouillard du jeu (seules les entités étaient
 * noyées — constaté le 15/10).
 * Pluie : rideau bleu nuit (~40 blocs à 100 %), fondu avec l'intensité, par-dessus le brouillard de pluie du jeu.
 * Blizzard (blanc-bleuté) et tempête de sable (ocre) : mur dense dont la distance suit {@link MeteoClient#visibilite}
 * (6 blocs à 100 %, 12 à 75 %), fondu depuis le brouillard normal, et dont la couleur s'assombrit avec le ciel (nuit).
 */
@Mixin(value = FogRenderer.class, priority = 500)
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
        if (type == MeteoClient.BLIZZARD || type == MeteoClient.TEMPETE) {
            // mur de neige / de sable : la visibilité suit l'intensité (courbe partagée avec le plugin)
            k = Math.min(1f, i * 3f);
            float vis = MeteoClient.visibilite(i);
            fin = lerpDistance(fog.environmentalEnd, vis * 1.15f, k);
            if (type == MeteoClient.BLIZZARD) { r = 0.80f; g = 0.85f; b = 0.92f; }
            else { r = 0.72f; g = 0.59f; b = 0.40f; }
            // jour / nuit : on garde la luminosité du brouillard du jeu (qui suit le ciel)
            if (fog.color != null) {
                float lum = 0.299f * fog.color.x() + 0.587f * fog.color.y() + 0.114f * fog.color.z();
                float jour = Math.max(0.1f, Math.min(1f, lum / 0.72f));
                r *= jour; g *= jour; b *= jour;
            }
        } else if (type == MeteoClient.SABLE) {
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
        if (type == MeteoClient.BLIZZARD || type == MeteoClient.TEMPETE) {
            // tempêtes : début du mur posé explicitement (le début « pluie » du jeu part très loin dans le négatif, ce
            // qui noie tout, même à 1 bloc, et que Sodium n'applique pas au terrain) ; le proche reste lisible
            // (son personnage en 3e personne), le mur commence vers 1/3 de la visibilité
            float debut = Math.max(0f, Math.min(fog.environmentalStart, fin));
            debut = lerp(debut, fin * 0.3f, k);
            fog.environmentalStart = debut;
            fog.renderDistanceStart = Math.min(Math.max(0f, fog.renderDistanceStart), debut);
        } else {
            fog.environmentalStart = Math.min(fog.environmentalStart, 0f);
            fog.renderDistanceStart = Math.min(fog.renderDistanceStart, 0f);
        }
        fog.environmentalEnd = Math.min(fog.environmentalEnd, fin);
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
