package fr.reborn.hud.mixin;

import fr.reborn.hud.camera.RebornCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Input;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mouvement relatif à la caméra (MMORPG / Elden Ring) pour la caméra épaule.
 *
 * <p>Après le {@code tick} clavier vanilla, on transforme l'input ZQSD en une
 * <b>direction monde relative à la caméra</b> ({@link RebornCamera#camYaw()}),
 * on oriente le joueur (yaw + bodyYaw + headYaw) vers cette direction de façon
 * lissée, et on convertit l'input en « tout droit ». Résultat : la souris
 * orbite la caméra, ZQSD déplace le perso dans le repère caméra, et le corps
 * pivote pour faire face au déplacement.
 *
 * <p>NB : {@code movementForward}/{@code movementSideways} vivent dans la
 * superclasse {@link Input} (pas dans {@link KeyboardInput}), donc on caste
 * {@code (Input)(Object)this} pour y accéder plutôt que {@code @Shadow}.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {

    /** Ticks restants d'aim « collant » après un clic (PVP fluide). */
    @Unique
    private int reborn$aimHold = 0;

    @Inject(method = "tick", at = @At("TAIL"))
    private void reborn$cameraRelativeMovement(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        RebornCamera cam = RebornCamera.INSTANCE;
        if (!cam.isEnabled()) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.options == null) return;

        // AIM-MODE : attaquer (clic gauche) / utiliser (clic droit) → le perso
        // s'aligne sur la caméra pour que minage / combat / placement visent le
        // viseur. « Collant » ~10 ticks après le dernier clic pour un PVP fluide
        // (on peut strafe autour de la cible en gardant l'aim).
        if (mc.options.attackKey.isPressed() || mc.options.useKey.isPressed()) {
            reborn$aimHold = 10;
        }
        if (reborn$aimHold > 0) {
            reborn$aimHold--;
            float cyaw = (float) cam.camYaw();
            float cpitch = (float) cam.camPitch();
            player.setYRot(cyaw);
            player.setXRot(cpitch);
            player.setYBodyRot(cyaw);
            player.setYHeadRot(cyaw);
            return; // déplacement reste relatif caméra (yaw = camYaw)
        }

        Input in = (Input) (Object) this;
        float mf = in.movementForward;
        float ms = in.movementSideways;
        if (mf == 0f && ms == 0f) return; // pas d'input → garde l'orientation

        double cy = Math.toRadians(cam.camYaw());
        // Repère caméra : avant = (-sin, cos) ; latéral = (-cos, -sin).
        // Signe « - » sur le latéral : corrige l'inversion gauche/droite.
        double fx = -Math.sin(cy), fz = Math.cos(cy);
        double lx = -Math.cos(cy), lz = -Math.sin(cy);
        double dx = fx * mf - lx * ms;
        double dz = fz * mf - lz * ms;

        float target = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float ny = Mth.lerpAngleDegrees((float) cam.turnSpeed(), player.getYRot(), target);
        player.setYRot(ny);
        player.setYBodyRot(ny);
        player.setYHeadRot(ny);

        // Le perso court « tout droit » dans son orientation (magnitude conservée
        // pour garder le ralenti sneak/objet).
        in.movementForward = (float) Math.min(1.0, Math.sqrt(mf * mf + ms * ms));
        in.movementSideways = 0f;
    }
}
