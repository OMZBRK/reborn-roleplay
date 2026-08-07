package fr.reborn.hud.mixin;

import fr.reborn.hud.animation.NarutoRun;
import fr.reborn.hud.camera.RebornCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Force l'orientation du corps vers la caméra en <b>marche/course (mode base)</b>,
 * en <b>post-tick</b>.
 *
 * <p>Pourquoi ici : {@code KeyboardInputMixin} pose le yaw/bodyYaw vers la caméra,
 * mais la logique vanilla de {@link net.minecraft.world.entity.LivingEntity}
 * (dans {@code aiStep}) re-tourne ensuite {@code yBodyRot} vers la <b>direction de
 * déplacement</b>, écrasant notre valeur — d'où « le perso ne suit la caméra que
 * quand je clique » (le clic active la pose de visée qui garde le corps face au
 * regard). En ré-appliquant l'orientation au TAIL de {@link LocalPlayer#tick()}
 * (après {@code aiStep}), le corps suit bien la caméra (souris) en marche.
 *
 * <p>Naruto run : on ne touche à rien — le corps doit alors faire face à la
 * direction de déplacement (Elden Ring), ce que fait déjà la logique vanilla.
 * À l'arrêt : rien non plus → free-look (on tourne autour du perso).
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerBodyMixin {

    @Inject(method = "tick", at = @At("TAIL"))
    private void reborn$bodyFollowsCamera(CallbackInfo ci) {
        RebornCamera cam = RebornCamera.INSTANCE;
        if (!cam.isEnabled()) return;                       // vue épaule uniquement
        if (NarutoRun.INSTANCE.isActive()) return;          // naruto = corps vers déplacement

        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return;
        boolean moving = mc.options.keyUp.isDown() || mc.options.keyDown.isDown()
            || mc.options.keyLeft.isDown() || mc.options.keyRight.isDown();
        if (!moving) return;                                // arrêt → free-look

        LocalPlayer self = (LocalPlayer) (Object) this;
        float cyaw = (float) cam.camYaw();
        // Instant (comme la pose de visée) : le corps colle à la caméra en marche.
        self.setYRot(cyaw);
        self.setYBodyRot(cyaw);
        self.setYHeadRot(cyaw);
    }
}
