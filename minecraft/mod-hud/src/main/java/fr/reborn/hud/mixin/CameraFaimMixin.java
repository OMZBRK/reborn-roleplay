package fr.reborn.hud.mixin;

import fr.reborn.hud.nourriture.FaimCamera;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FAIM (satiété à 0 %, GAMEPLAN §4 du 19/10) : la vue tangue légèrement. Ajouté à la fin de la mise à jour de la
 * caméra (après la caméra épaule, le mode photo…) ; le regard du joueur n'est pas touché. Amplitude et réglage
 * « Effets de distorsion » dans {@link FaimCamera#cameraDecalage()}.
 */
@Mixin(Camera.class)
public abstract class CameraFaimMixin {

    @Shadow private float xRot;
    @Shadow private float yRot;

    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void reborn$faimTangue(DeltaTracker deltaTracker, CallbackInfo ci) {
        float[] d = FaimCamera.cameraDecalage();
        if (d == null) return;
        setRotation(yRot + d[0], xRot + d[1]);
    }
}
