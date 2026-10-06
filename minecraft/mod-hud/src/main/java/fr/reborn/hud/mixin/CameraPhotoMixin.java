package fr.reborn.hud.mixin;

import fr.reborn.hud.immersion.PhotoMode;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * En mode photo, on détache la caméra du joueur : après l'update vanilla on
 * réécrit position + rotation avec l'état free-cam de {@link PhotoMode}, on ajoute
 * l'<b>inclinaison</b> (roulis) et on impose le <b>champ de vision</b> du panneau.
 *
 * <p>26.1 : {@code Camera#update(DeltaTracker)} + {@code setPos→setPosition}.
 */
@Mixin(Camera.class)
public abstract class CameraPhotoMixin {

    @Shadow protected abstract void setPosition(Vec3 pos);
    @Shadow protected abstract void setRotation(float yaw, float pitch);
    @Shadow @Final private Quaternionf rotation;

    // Après alignWithEntity (cf. CameraThirdPersonMixin) : le culling de MC se construit
    // ensuite sur notre caméra → pas de trous de chunks.
    @Inject(method = "update", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
    private void reborn$photoCamera(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!PhotoMode.INSTANCE.isActive()) return;
        this.setRotation(PhotoMode.INSTANCE.yaw(), PhotoMode.INSTANCE.pitch());
        float roll = PhotoMode.INSTANCE.roll();
        if (roll != 0f) this.rotation.rotateZ((float) Math.toRadians(roll));
        this.setPosition(PhotoMode.INSTANCE.pos());
    }

    /** Champ de vision du mode photo (remplace celui des options pendant le mode). */
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void reborn$photoFov(CallbackInfoReturnable<Float> cir) {
        if (PhotoMode.INSTANCE.isActive()) cir.setReturnValue(PhotoMode.INSTANCE.fov());
    }
}
