package fr.reborn.hud.mixin;

import fr.reborn.hud.cosmetic.RepositionMode;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Caméra du mode repositionnement cosmétique : orbite propre (centrée) autour du
 * joueur pendant que {@code RepositionScreen} est ouvert. Après l'update vanilla,
 * on réécrit position + rotation depuis {@link RepositionMode} (comme
 * {@code CameraPhotoMixin}), avec un clip anti-mur pour ne pas rentrer dans les
 * blocs. Priorité sur la caméra épaule Reborn (qui se retire quand ce mode est actif).
 */
@Mixin(Camera.class)
public abstract class CameraRepositionMixin {

    @Shadow protected abstract void setPosition(Vec3 pos);
    @Shadow protected abstract void setRotation(float yaw, float pitch);

    // Après alignWithEntity (cf. CameraThirdPersonMixin) : le culling se construit ensuite
    // sur notre caméra → pas de trous de chunks.
    @Inject(method = "update", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/Camera;alignWithEntity(F)V", shift = At.Shift.AFTER))
    private void reborn$repositionCamera(DeltaTracker deltaTracker, CallbackInfo ci) {
        RepositionMode mode = RepositionMode.INSTANCE;
        if (!mode.isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;

        Vec3 focus = mode.focus(mc);
        Vec3 target = mode.cameraPosition(mc);
        setRotation(mode.camYaw(), mode.camPitch());
        setPosition(reborn$clip(mc.player, focus, target));
    }

    /** Réduit la distance si un mur est entre le focus et la caméra. */
    @Unique
    private Vec3 reborn$clip(Entity e, Vec3 from, Vec3 to) {
        HitResult hit = e.level().clip(new ClipContext(
            from, to, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, e));
        if (hit.getType() != HitResult.Type.BLOCK) return to;
        Vec3 hp = hit.getLocation();
        Vec3 dir = to.subtract(from);
        double len = dir.length();
        return len < 1.0e-4 ? hp : hp.subtract(dir.scale(0.2 / len));
    }
}
