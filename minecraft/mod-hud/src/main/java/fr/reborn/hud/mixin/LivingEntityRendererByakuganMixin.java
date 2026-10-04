package fr.reborn.hud.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.reborn.hud.byakugan.ByakuganClient;
import fr.reborn.hud.byakugan.ByakuganRenderState;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Byakugan, vue « aux rayons X » des entités à portée (uniquement chez le porteur) :
 * <ul>
 *   <li>le corps est rendu par le chemin vanilla « corps non visible mais pas invisible pour moi » (celui des
 *       coéquipiers invisibles) : translucide ; l'opacité/teinte vanilla (0x26FFFFFF, 15 %) est remplacée par
 *       {@link ByakuganClient#CORPS_FANTOME} (bleuté, ~35 %) ;</li>
 *   <li>les entités réellement invisibles redeviennent visibles (le Byakugan les perce) ;</li>
 *   <li>contour lumineux bleu (aura, visible à travers les murs) via {@code outlineColor}.</li>
 * </ul>
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
            state.outlineColor = ByakuganClient.AURA;
        }
    }

    @Inject(method = "isBodyVisible(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;)Z",
            at = @At("HEAD"), cancellable = true)
    private void reborn$byakuganBody(LivingEntityRenderState state, CallbackInfoReturnable<Boolean> cir) {
        if (((ByakuganRenderState) state).reborn$byakugan()) cir.setReturnValue(false);
    }

    @ModifyConstant(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            constant = @Constant(intValue = 0x26FFFFFF))
    private int reborn$byakuganGhost(int original, LivingEntityRenderState state, PoseStack poseStack,
                                     SubmitNodeCollector collector, CameraRenderState camera) {
        return ((ByakuganRenderState) state).reborn$byakugan() ? ByakuganClient.CORPS_FANTOME : original;
    }
}
