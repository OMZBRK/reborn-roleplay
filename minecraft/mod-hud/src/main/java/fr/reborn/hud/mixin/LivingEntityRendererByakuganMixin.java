package fr.reborn.hud.mixin;

import fr.reborn.hud.byakugan.ByakuganClient;
import fr.reborn.hud.byakugan.ByakuganRenderState;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.util.LightCoordsUtil;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Byakugan, entités à portée (uniquement chez le porteur) : corps rendu en silhouette unie (texture blanche, pleine
 * lumière — le filtre négatif en fait une silhouette sombre), invisibles percés, aura (contour) visible à travers les
 * murs dans la couleur « inverse » du chakra. En concentration (lot 4), l'entité survolée prend le contour lumineux
 * vanilla (blanc, comme l'effet « glowing »), côté client seulement.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererByakuganMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void reborn$byakuganExtract(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
        boolean cible = ByakuganClient.isTarget(entity);
        ((ByakuganRenderState) state).reborn$setByakugan(cible);
        if (cible) {
            state.isInvisibleToPlayer = false;
            state.outlineColor = ByakuganClient.CHAKRA_INVERSE;
            state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
        } else if (!state.isInvisibleToPlayer && fr.reborn.hud.concentration.ModeConcentration.halo(entity)) {
            // Concentration (lot 4) : l'entité survolée prend le contour lumineux vanilla (« glowing »), chez soi
            // seulement ; jamais sous le Byakugan (halo() le refuse : son filtre négatif l'inverserait), jamais sur un invisible
            // (sa silhouette apparaîtrait)
            state.outlineColor = fr.reborn.hud.concentration.ModeConcentration.HALO;
        }
    }

    @Inject(method = "isBodyVisible(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Z",
            at = @At("HEAD"), cancellable = true)
    private void reborn$byakuganBody(LivingEntityRenderState state, CallbackInfoReturnable<Boolean> cir) {
        if (((ByakuganRenderState) state).reborn$byakugan()) cir.setReturnValue(true);
    }

    @Inject(method = "getRenderType(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;ZZZ)Lnet/minecraft/client/renderer/rendertype/RenderType;",
            at = @At("HEAD"), cancellable = true)
    private void reborn$byakuganSilhouette(LivingEntityRenderState state, boolean bodyVisible, boolean translucent, boolean glowing,
                                           CallbackInfoReturnable<RenderType> cir) {
        if (((ByakuganRenderState) state).reborn$byakugan()) {
            Object self = this;
            boolean humanoide = ((LivingEntityRenderer<?, ?, ?>) self).getModel() instanceof HumanoidModel<?>;
            cir.setReturnValue(RenderTypes.entityCutout(humanoide ? ByakuganClient.SILHOUETTE_JOUEUR : ByakuganClient.SILHOUETTE));
        }
    }
}
