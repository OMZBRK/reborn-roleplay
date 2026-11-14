package fr.reborn.hud.mixin;

import fr.reborn.hud.byakugan.ByakuganClient;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.fog.FogData;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.material.FogType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Byakugan (lot 5, étape B, 4e tour) : <b>le Byakugan voit loin</b>. Tant que son filtre est posé, le brouillard du jeu
 * recule avec le fondu : le voile de la distance de rendu ne commence plus qu'à 97 % de celle-ci (90 % en vanilla) et le
 * brouillard de l'air (pluie comprise) part au-delà. Sans cela, un village vu d'en haut tournait au blanc sur blanc (fond
 * voilé, traits blancs illisibles). La fin de ce voile et sa couleur (l'horizon) sont transmises au filtre (pixels 5 et 18
 * des paramètres) : le terrain du bord y passe au ton de l'horizon, l'encre avec lui. Dans l'eau, la lave, la neige poudreuse, sous Cécité ou Ténèbres : rien ne change.
 * Priorité 500 comme {@link FogRendererMeteoMixin} : avant la copie du brouillard par Sodium.
 */
@Mixin(value = FogRenderer.class, priority = 500)
public abstract class FogRendererByakuganMixin {

    @Inject(method = "setupFog(Lnet/minecraft/client/Camera;ILnet/minecraft/client/DeltaTracker;FLnet/minecraft/client/multiplayer/ClientLevel;)Lnet/minecraft/client/renderer/fog/FogData;",
            at = @At("RETURN"))
    private void reborn$byakuganFog(Camera camera, int renderDistance, DeltaTracker delta, float darken, ClientLevel level,
                                    CallbackInfoReturnable<FogData> cir) {
        float n = ByakuganClient.niveau();
        if (n <= 0f || !ByakuganClient.filtreAffiche()) return;
        FogType type = camera.getFluidInCamera();
        if (type != FogType.NONE && type != FogType.ATMOSPHERIC) return;
        var p = Minecraft.getInstance().player;
        if (p != null && (p.hasEffect(MobEffects.BLINDNESS) || p.hasEffect(MobEffects.DARKNESS))) return;
        FogData fog = cir.getReturnValue();
        if (fog == null) return;
        float fin = fog.renderDistanceEnd;
        ByakuganClient.brouillard(fin, fog.color.x(), fog.color.y(), fog.color.z());
        fog.renderDistanceStart += (Math.max(fog.renderDistanceStart, fin * 0.97f) - fog.renderDistanceStart) * n;
        fog.environmentalStart += (Math.max(fog.environmentalStart, fin) - fog.environmentalStart) * n;
        fog.environmentalEnd += (Math.max(fog.environmentalEnd, fin * 2f) - fog.environmentalEnd) * n;
    }
}
