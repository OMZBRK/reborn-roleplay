package fr.reborn.hud.mixin;

import fr.reborn.hud.immersion.PhotoMode;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fige le joueur pendant le mode photo : on remet à zéro le mouvement calculé
 * par {@link KeyboardInput#tick} pour que les touches ZQSD pilotent la caméra
 * libre ({@link PhotoMode}) et non le personnage.
 *
 * <p>26.1 : {@code moveVector} (protected) est hérité de {@code ClientInput} —
 * on l'écrit via {@link ClientInputAccessor} (Mixin ne résout pas les
 * {@code @Shadow} de champs hérités). {@code keyPresses} est public sur
 * {@code ClientInput} → accès direct par cast. {@code Input.EMPTY} = toutes
 * touches relâchées, {@code Vec2.ZERO} = aucun déplacement.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputPhotoMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void reborn$freezePlayer(CallbackInfo ci) {
        if (!PhotoMode.INSTANCE.isActive()) return;
        ((ClientInputAccessor) (Object) this).reborn$setMoveVector(Vec2.ZERO);
        ((ClientInput) (Object) this).keyPresses = Input.EMPTY;
    }
}
