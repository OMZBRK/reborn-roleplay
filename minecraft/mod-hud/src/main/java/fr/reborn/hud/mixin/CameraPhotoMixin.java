package fr.reborn.hud.mixin;

import fr.reborn.hud.immersion.PhotoMode;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * En mode photo, on détache la caméra du joueur : après l'update vanilla on
 * réécrit position + rotation avec l'état free-cam de {@link PhotoMode}.
 *
 * <p>26.1 : {@code Camera#update(DeltaTracker)} + {@code setPos→setPosition}.
 */
@Mixin(Camera.class)
public abstract class CameraPhotoMixin {

    @Shadow protected abstract void setPosition(Vec3 pos);
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    // Avant le frustum de culling (cf CameraThirdPersonMixin) — sinon chunks non rendus.
    @Inject(method = "update", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/Camera;createProjectionMatrixForCulling()Lorg/joml/Matrix4f;",
        shift = At.Shift.BEFORE))
    private void reborn$photoCamera(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!PhotoMode.INSTANCE.isActive()) return;
        this.setRotation(PhotoMode.INSTANCE.yaw(), PhotoMode.INSTANCE.pitch());
        this.setPosition(PhotoMode.INSTANCE.pos());
    }
}
