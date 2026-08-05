package fr.reborn.hud.mixin;

import fr.reborn.hud.camera.RebornCamera;
import fr.reborn.hud.immersion.PhotoMode;
import net.minecraft.client.renderer.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.BlockView;
import net.minecraft.world.RaycastContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Caméra épaule Reborn (over-the-shoulder, style Zenkai / anime-RP).
 *
 * <p>Après l'{@code update} vanilla (qui a placé la caméra derrière le joueur
 * en 3e personne arrière), on repositionne la caméra à la distance voulue puis
 * on la décale latéralement/verticalement en over-the-shoulder, avec un clip
 * anti-mur (raycast). N'agit QUE si {@link RebornCamera#isEnabled()} et 3e
 * personne arrière. Le mode photo garde la priorité sur la caméra.
 *
 * @see fr.reborn.hud.camera.RebornCamera
 */
@Mixin(Camera.class)
public abstract class CameraThirdPersonMixin {

    @Shadow public abstract Vec3 getPos();
    @Shadow protected abstract void setPos(Vec3 pos);
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void reborn$shoulderCamera(BlockView area, Entity focusedEntity, boolean thirdPerson,
                                       boolean inverseView, float tickDelta, CallbackInfo ci) {
        if (!thirdPerson || inverseView) return;
        if (PhotoMode.INSTANCE.isActive()) return;
        RebornCamera cam = RebornCamera.INSTANCE;
        if (!cam.isEnabled() || focusedEntity == null) return;

        // Orientation caméra = orbite Reborn (découplée du regard du joueur).
        float cy = (float) cam.camYaw();
        float cp = (float) cam.camPitch();
        double yr = Math.toRadians(cy), pr = Math.toRadians(cp);
        // Direction de visée de la caméra (là où elle regarde).
        Vec3 viewDir = new Vec3(
            -Math.sin(yr) * Math.cos(pr),
            -Math.sin(pr),
            Math.cos(yr) * Math.cos(pr));
        Vec3 backDir = viewDir.multiply(-1.0);            // de l'oeil vers la caméra
        Vec3 right = viewDir.crossProduct(new Vec3(0, 1, 0));
        if (right.lengthSquared() < 1.0e-6) {
            right = new Vec3(1, 0, 0);
        } else {
            right = right.normalize();
        }
        Vec3 up = right.crossProduct(viewDir).normalize();

        Vec3 eye = focusedEntity.getCameraPosVec(tickDelta);
        Vec3 target = eye
            .add(backDir.multiply(cam.distance()))
            .add(right.multiply(cam.rightOffset()))
            .add(up.multiply(cam.upOffset()));

        setRotation(cy, cp);
        setPos(reborn$clip(focusedEntity, eye, target));
    }

    /** Réduit la distance si un mur est entre l'oeil et la caméra cible. */
    @Unique
    private Vec3 reborn$clip(Entity e, Vec3 from, Vec3 to) {
        HitResult hit = e.getWorld().raycast(new RaycastContext(
            from, to, RaycastContext.ShapeType.VISUAL, RaycastContext.FluidHandling.NONE, e));
        if (hit.getType() != HitResult.Type.BLOCK) return to;
        Vec3 hp = hit.getPos();
        Vec3 dir = to.subtract(from);
        double len = dir.length();
        // Recule de 0.2 bloc vers l'oeil pour ne pas coller/clipper dans le mur.
        return len < 1.0e-4 ? hp : hp.subtract(dir.multiply(0.2 / len));
    }
}
