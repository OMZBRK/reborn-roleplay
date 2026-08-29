package fr.reborn.hud.mixin;

import fr.reborn.hud.animation.NarutoRun;
import fr.reborn.hud.camera.RebornCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Orientation du corps/tête en <b>post-tick</b> (après {@code aiStep}) pour la
 * caméra épaule.
 *
 * <p>Deux comportements selon l'état :
 * <ul>
 *   <li><b>En déplacement (mode base)</b> : le corps suit la caméra (souris).
 *   {@code KeyboardInputMixin} pose déjà yaw/bodyYaw vers la caméra, mais la
 *   logique vanilla de {@code LivingEntity#aiStep} re-tourne ensuite
 *   {@code yBodyRot} vers la direction de déplacement — d'où « le perso ne suit
 *   la caméra que quand je clique ». On ré-applique donc l'orientation au TAIL.</li>
 *   <li><b>À l'arrêt</b> : le corps reste <b>figé</b> (free-look), mais la
 *   <b>tête suit la caméra</b> (bornée à ±{@link #REBORN_MAX_HEAD_YAW}°) pour
 *   regarder autour de soi sans pivoter le corps. C'est le comportement demandé :
 *   bouger la tête quand on ne se déplace plus, tout en gardant le free-look.</li>
 * </ul>
 *
 * <p>Naruto run : on ne touche à rien — le corps fait face à la direction de
 * déplacement (Elden Ring), ce que fait déjà la logique vanilla.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerBodyMixin {

    /** Débattement max de la tête par rapport au corps à l'arrêt (nuque humaine). */
    @Unique
    private static final float REBORN_MAX_HEAD_YAW = 70.0f;

    @Inject(method = "tick", at = @At("TAIL"))
    private void reborn$bodyFollowsCamera(CallbackInfo ci) {
        RebornCamera cam = RebornCamera.INSTANCE;
        if (!cam.isEnabled()) return;                       // vue épaule uniquement
        if (NarutoRun.INSTANCE.isActive()) return;          // naruto = corps vers déplacement

        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return;
        boolean moving = mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
            || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();

        LocalPlayer self = (LocalPlayer) (Object) this;
        float camYaw = (float) cam.camYaw();

        // ── À l'arrêt : corps figé (free-look), la TÊTE suit la caméra ──
        if (!moving) {
            // Tête bornée autour du corps pour rester naturel (pas de 180°).
            float bodyYaw = self.yBodyRot;
            float offset = Mth.clamp(Mth.wrapDegrees(camYaw - bodyYaw),
                -REBORN_MAX_HEAD_YAW, REBORN_MAX_HEAD_YAW);
            self.setYHeadRot(bodyYaw + offset);
            self.setXRot((float) cam.camPitch());           // regard vertical
            return;
        }

        // ── En déplacement : free-look fige le corps, sinon il suit la caméra ──
        if (cam.freeLook()) return;                         // free-look = corps figé, caméra orbite
        // Instant (comme la pose de visée) : le corps colle à la caméra en marche.
        self.setYRot(camYaw);
        self.setYBodyRot(camYaw);
        self.setYHeadRot(camYaw);
    }
}
