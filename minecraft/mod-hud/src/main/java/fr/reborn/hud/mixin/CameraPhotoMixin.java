package fr.reborn.hud.mixin;

import fr.reborn.hud.immersion.PhotoMode;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * En mode photo, on détache la caméra du joueur : après l'update vanilla on
 * réécrit position + rotation avec l'état free-cam de {@link PhotoMode}.
 */
@Mixin(Camera.class)
public abstract class CameraPhotoMixin {

    @Shadow protected abstract void setPos(Vec3 pos);
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void reborn$photoCamera(BlockGetter area, Entity focusedEntity, boolean thirdPerson,
                                    boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (!PhotoMode.INSTANCE.isActive()) return;
        this.setRotation(PhotoMode.INSTANCE.yaw(), PhotoMode.INSTANCE.pitch());
        this.setPos(PhotoMode.INSTANCE.pos());
    }
}
