package fr.reborn.hud.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.reborn.hud.mur.MurRendu;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Avatar;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rendu du corps qui marche sur un mur (lot 5, essai C0), pour soi et pour les autres : logique dans {@link MurRendu}.
 * <ul>
 *   <li>fin de {@code extractRenderState} : l'état de rendu apprend la face, la bascule et le cap, et perd ce que la pose
 *       de nage du serveur lui ferait (corps à plat ventre, bras de nage) ;</li>
 *   <li>début et fin de {@code setupRotations} : le corps entier tourne autour de son centre, de part et d'autre de la
 *       rotation du jeu, qui n'est pas annulée.</li>
 * </ul>
 * Aucun des trois crochets n'est annulable ni ne lit son {@code CallbackInfo} : Mixin n'en crée pas, rien n'est alloué par
 * image. Un avatar qui n'est pas au mur ne coûte qu'un champ écrit et deux champs lus.
 */
@Mixin(AvatarRenderer.class)
public abstract class AvatarRendererMurMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Avatar;Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;F)V",
            at = @At("TAIL"))
    private void reborn$murExtrait(Avatar entity, AvatarRenderState state, float partialTick, CallbackInfo ci) {
        MurRendu.extraire(entity, state, partialTick);
    }

    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
            at = @At("HEAD"))
    private void reborn$murAvant(AvatarRenderState state, PoseStack poseStack, float bodyRot, float scale, CallbackInfo ci) {
        MurRendu.avant(state, poseStack, bodyRot);
    }

    @Inject(method = "setupRotations(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;FF)V",
            at = @At("TAIL"))
    private void reborn$murApres(AvatarRenderState state, PoseStack poseStack, float bodyRot, float scale, CallbackInfo ci) {
        MurRendu.apres(state, poseStack);
    }
}
