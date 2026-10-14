package fr.reborn.hud.mixin;

import fr.reborn.hud.staff.EffetsStaff;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Séisme staff ({@code reborn:ecran} {@code seisme}) : légère secousse de la vue, ajoutée à la fin de la mise à jour
 * de la caméra (après la caméra épaule, le mode photo, etc.). Le regard du joueur n'est pas touché ; amplitude et
 * réglage d'accessibilité dans {@link EffetsStaff#secousse()}.
 */
@Mixin(Camera.class)
public abstract class CameraSeismeMixin {

    @Shadow private float xRot;
    @Shadow private float yRot;

    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void reborn$seisme(DeltaTracker deltaTracker, CallbackInfo ci) {
        float[] s = EffetsStaff.secousse();
        if (s == null) return;
        setRotation(yRot + s[0], xRot + s[1]);
    }
}
