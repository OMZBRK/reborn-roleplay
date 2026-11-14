package fr.reborn.hud.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.vertex.PoseStack;
import fr.reborn.hud.byakugan.ByakuganClient;
import fr.reborn.hud.byakugan.ByakuganRenderState;
import fr.reborn.hud.byakugan.ChakraNetworkLayer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
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
 * Byakugan (lot 5, étape B), entités à portée, chez le porteur seulement : corps en silhouette unie dans la couleur-clé
 * du corps (pleine lumière, le filtre la rend cyan pâle), réseau de chakra par {@code ChakraNetworkLayer} et aucune autre
 * couche (vêtements de villageois, armure, objets tenus : leurs textures passaient sur la silhouette), invisibles percés ; derrière un mur, le contour vanilla dans la clé du corps ({@code CLE_CONTOUR}, liseré cyan après le filtre),
 * jamais devant.
 * Porteurs vus par les autres de près : drapeau des yeux pâles ({@code ByakuganSigneLayer}). En concentration (lot 4),
 * l'entité survolée prend le contour lumineux vanilla (blanc, comme l'effet « glowing »), côté client seulement.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererByakuganMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;F)V",
            at = @At("TAIL"))
    private void reborn$byakuganExtract(LivingEntity entity, LivingEntityRenderState state, float partialTick, CallbackInfo ci) {
        boolean cible = ByakuganClient.isTarget(entity);
        ByakuganRenderState b = (ByakuganRenderState) state;
        b.reborn$setByakugan(cible);
        b.reborn$setSigne(!cible && ByakuganClient.signe(entity));
        if (cible) {
            state.isInvisibleToPlayer = false;
            state.lightCoords = LightCoordsUtil.FULL_BRIGHT;
            // liseré seulement derrière un mur : devant, le corps cyan suffit (aucun contour sur ce qu'on voit)
            if (ByakuganClient.cachee(entity)) state.outlineColor = ByakuganClient.CLE_CONTOUR;
        } else if (!state.isInvisibleToPlayer && fr.reborn.hud.concentration.ModeConcentration.halo(entity)) {
            // Concentration (lot 4) : l'entité survolée prend le contour lumineux vanilla (« glowing »), chez soi
            // seulement ; jamais sous le Byakugan (halo() le refuse : le filtre lirait le blanc comme du monde), jamais sur
            // un invisible (sa silhouette apparaîtrait)
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

    @WrapOperation(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/entity/layers/RenderLayer;submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/EntityRenderState;FF)V"))
    private void reborn$byakuganCouches(RenderLayer<?, ?> layer, PoseStack pose, SubmitNodeCollector collector, int light,
                                        EntityRenderState state, float yRot, float xRot, Operation<Void> original) {
        if (state instanceof ByakuganRenderState b && b.reborn$byakugan() && !(layer instanceof ChakraNetworkLayer<?, ?>)) return;
        original.call(layer, pose, collector, light, state, yRot, xRot);
    }
}
