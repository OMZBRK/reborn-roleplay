package fr.reborn.hud.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.reborn.hud.anchor.TeteAncre;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Borne la passe des entités du monde ({@code LevelRenderer#submitEntities}, privée) pour la capture de
 * {@link TeteAncre} : avec l'identité de la caméra du monde, c'est ce qui écarte les aperçus GUI et toute autre passe
 * qui soumettrait des avatars ailleurs (une passe d'ombre de shaders, par exemple).
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererTeteMixin {

    @Inject(method = "submitEntities(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
            at = @At("HEAD"))
    private void reborn$debutEntites(PoseStack poseStack, LevelRenderState state, SubmitNodeCollector collector, CallbackInfo ci) {
        TeteAncre.debutEntites();
    }

    @Inject(method = "submitEntities(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/state/level/LevelRenderState;Lnet/minecraft/client/renderer/SubmitNodeCollector;)V",
            at = @At("RETURN"))
    private void reborn$finEntites(PoseStack poseStack, LevelRenderState state, SubmitNodeCollector collector, CallbackInfo ci) {
        TeteAncre.finEntites();
    }
}
