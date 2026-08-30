package fr.reborn.hud.mixin;

import fr.reborn.hud.camera.RebornCamera;
import fr.reborn.hud.immersion.PhotoMode;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;
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
 * <p>26.1 : {@code Camera#update} a pour signature {@code update(DeltaTracker)}
 * et ne passe plus (area, focusedEntity, thirdPerson, inverseView, tickDelta).
 * On lit donc l'entité ciblée via le champ shadow {@code entity}, le tickDelta
 * via le {@link DeltaTracker}, et la vue (3e pers arrière vs avant vs 1re pers)
 * via {@code Options#getCameraType()}.
 *
 * @see fr.reborn.hud.camera.RebornCamera
 */
@Mixin(Camera.class)
public abstract class CameraThirdPersonMixin {

    /** Entité ciblée par la caméra (le joueur local en général). Champ vanilla. */
    @Shadow private Entity entity;

    @Shadow public abstract Vec3 position();
    @Shadow protected abstract void setPosition(Vec3 pos);
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    // Pour reconstruire le frustum de culling sur NOTRE caméra (cf. reborn$rebuildCullFrustum).
    @Shadow public abstract org.joml.Matrix4f getViewRotationMatrix(org.joml.Matrix4f dest);
    @Shadow private org.joml.Matrix4f createProjectionMatrixForCulling() { return null; }
    @Shadow private void prepareCullFrustum(org.joml.Matrix4fc proj, org.joml.Matrix4f view, Vec3 pos) { }

    @Inject(method = "update", at = @At("TAIL"))
    private void reborn$shoulderCamera(DeltaTracker deltaTracker, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        // 3e personne ARRIÈRE uniquement (équiv. ancien !thirdPerson || inverseView :
        // THIRD_PERSON_FRONT et FIRST_PERSON sont exclus).
        if (mc.options == null || mc.options.getCameraType() != CameraType.THIRD_PERSON_BACK) return;
        if (PhotoMode.INSTANCE.isActive()) return;
        // Le mode repositionnement prend le contrôle de la caméra (orbite dédiée).
        if (fr.reborn.hud.cosmetic.RepositionMode.INSTANCE.isActive()) return;
        RebornCamera cam = RebornCamera.INSTANCE;
        Entity focusedEntity = this.entity;
        if (!cam.isEnabled() || focusedEntity == null) return;

        float tickDelta = deltaTracker.getGameTimeDeltaPartialTick(true);

        // Orientation caméra = orbite Reborn (découplée du regard du joueur).
        float cy = (float) cam.camYaw();
        float cp = (float) cam.camPitch();
        double yr = Math.toRadians(cy), pr = Math.toRadians(cp);
        // Direction de visée de la caméra (là où elle regarde).
        Vec3 viewDir = new Vec3(
            -Math.sin(yr) * Math.cos(pr),
            -Math.sin(pr),
            Math.cos(yr) * Math.cos(pr));
        Vec3 backDir = viewDir.scale(-1.0);            // de l'oeil vers la caméra
        Vec3 right = viewDir.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1.0e-6) {
            right = new Vec3(1, 0, 0);
        } else {
            right = right.normalize();
        }
        Vec3 up = right.cross(viewDir).normalize();

        Vec3 eye = focusedEntity.getEyePosition(tickDelta);
        Vec3 target = eye
            .add(backDir.scale(cam.distance()))
            .add(right.scale(cam.rightOffset()))
            .add(up.scale(cam.upOffset() + cam.landImpact()));

        setRotation(cy, cp);
        setPosition(reborn$clip(focusedEntity, eye, target));
        reborn$rebuildCullFrustum();
    }

    /**
     * Reconstruit le frustum de culling de MC sur NOTRE caméra. En 26.x,
     * {@code Camera#update} bâtit ce frustum (getViewRotationMatrix → createProjection →
     * prepareCullFrustum) AU MILIEU de la méthode, sur la rotation/position VANILLA, avant
     * notre repositionnement au TAIL → l'occlusion cull les sections hors du regard joueur
     * (= chunks non rendus / trous en caméra épaule orbitée). On réémet les MÊMES appels que
     * MC mais après notre setRotation/setPosition, donc sur notre vue → plus de trous.
     */
    @Unique
    private void reborn$rebuildCullFrustum() {
        try {
            prepareCullFrustum(createProjectionMatrixForCulling(),
                getViewRotationMatrix(new org.joml.Matrix4f()), position());
        } catch (Throwable ignored) {
            // API caméra différente : on n'aggrave pas (au pire, l'ancien comportement).
        }
    }

    /** Réduit la distance si un mur est entre l'oeil et la caméra cible. */
    @Unique
    private Vec3 reborn$clip(Entity e, Vec3 from, Vec3 to) {
        HitResult hit = e.level().clip(new ClipContext(
            from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, e));
        if (hit.getType() != HitResult.Type.BLOCK) return to;
        Vec3 hp = hit.getLocation();
        Vec3 dir = to.subtract(from);
        double len = dir.length();
        // Recule de 0.2 bloc vers l'oeil pour ne pas coller/clipper dans le mur.
        return len < 1.0e-4 ? hp : hp.subtract(dir.scale(0.2 / len));
    }
}
