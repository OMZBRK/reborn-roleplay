package fr.reborn.hud.mixin;

import fr.reborn.hud.corde.CordePose;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pose du ligoté (prototype de la corde, 26/11) : logique dans {@link CordePose}. Fin de {@code setupAnim}, priorité
 * au-dessus de celle du {@code PlayerModelMixin} de PlayerAnimationLib (2001, crochet au RETURN de {@code setupAnim}) :
 * appliqué après lui, ce crochet passe donc après ses calques (démarches, emotes, méditation). Crochet non annulable,
 * rien n'est alloué par le mod ; un avatar qui n'est pas ligoté ne coûte qu'un entier lu.
 */
@Mixin(value = PlayerModel.class, priority = 2500)
public abstract class PlayerModelCordeMixin {

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void reborn$cordePose(AvatarRenderState state, CallbackInfo ci) {
        CordePose.appliquer((PlayerModel) (Object) this, state);
    }
}
