package fr.reborn.hud.mixin;

import fr.reborn.hud.immersion.PhotoMode;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Fige le joueur pendant le mode photo : on remet à zéro le mouvement calculé
 * par {@link KeyboardInput#tick} pour que les touches ZQSD pilotent la caméra
 * libre ({@link PhotoMode}) et non le personnage.
 *
 * <p>26.1 : l'ancien {@code Input} mutable a disparu. On écrit désormais le
 * {@code moveVector} (Vec2) et le record {@code keyPresses} de {@code ClientInput}
 * (superclasse) via {@code @Shadow} — {@link Input#EMPTY} = toutes touches relâchées
 * (dont saut/sneak), {@link Vec2#ZERO} = aucun déplacement.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputPhotoMixin {

    @Shadow
    protected Vec2 moveVector;

    @Shadow
    public Input keyPresses;

    // 26.1 : KeyboardInput#tick() ne prend plus (boolean slowDown, float factor).
    @Inject(method = "tick", at = @At("TAIL"))
    private void reborn$freezePlayer(CallbackInfo ci) {
        if (!PhotoMode.INSTANCE.isActive()) return;
        this.moveVector = Vec2.ZERO;
        this.keyPresses = Input.EMPTY;
    }
}
