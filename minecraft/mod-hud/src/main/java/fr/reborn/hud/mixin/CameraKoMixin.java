package fr.reborn.hud.mixin;

import fr.reborn.hud.ko.KoClient;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Ressenti du KO (14/10) : en douleur, la vue vacille et tressaille au battement ; à terre, elle tangue à chaque
 * poussée de reptation. Ajouté à la fin de la mise à jour de la caméra (le regard du joueur n'est pas touché) ;
 * amplitude et réglage « Effets de distorsion » dans {@link KoClient#cameraDecalage()}.
 */
@Mixin(Camera.class)
public abstract class CameraKoMixin {

    @Shadow private float xRot;
    @Shadow private float yRot;

    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void reborn$koRessenti(DeltaTracker deltaTracker, CallbackInfo ci) {
        float[] d = KoClient.cameraDecalage();
        if (d == null) return;
        setRotation(yRot + d[0], xRot + d[1]);
    }
}
