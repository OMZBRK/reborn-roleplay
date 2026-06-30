package fr.reborn.hud.mixin;

import fr.reborn.hud.immersion.PhotoMode;
import net.minecraft.client.input.Input;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fige le joueur pendant le mode photo : on remet à zéro le mouvement calculé
 * par {@link KeyboardInput#tick} pour que les touches ZQSD pilotent la caméra
 * libre ({@link PhotoMode}) et non le personnage. Les champs de mouvement sont
 * publics dans la superclasse {@link Input} — accès direct par cast.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputPhotoMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void reborn$freezePlayer(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        if (!PhotoMode.INSTANCE.isActive()) return;
        Input self = (Input) (Object) this;
        self.movementForward = 0f;
        self.movementSideways = 0f;
        self.jumping = false;
        self.sneaking = false;
    }
}
