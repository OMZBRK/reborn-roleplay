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
    @Shadow public abstract Vec3 position();
    @Shadow public abstract org.joml.Matrix4f getViewRotationMatrix(org.joml.Matrix4f dest);
    @Shadow private org.joml.Matrix4f createProjectionMatrixForCulling() { return null; }
    @Shadow private void prepareCullFrustum(org.joml.Matrix4fc proj, org.joml.Matrix4f view, Vec3 pos) { }

    @Inject(method = "update", at = @At("TAIL"))
    private void reborn$photoCamera(DeltaTracker deltaTracker, CallbackInfo ci) {
        if (!PhotoMode.INSTANCE.isActive()) return;
        this.setRotation(PhotoMode.INSTANCE.yaw(), PhotoMode.INSTANCE.pitch());
        this.setPosition(PhotoMode.INSTANCE.pos());
        // Frustum de culling sur notre caméra (cf. CameraThirdPersonMixin) — sinon trous.
        try {
            prepareCullFrustum(createProjectionMatrixForCulling(),
                getViewRotationMatrix(new org.joml.Matrix4f()), position());
        } catch (Throwable ignored) { }
    }
}
