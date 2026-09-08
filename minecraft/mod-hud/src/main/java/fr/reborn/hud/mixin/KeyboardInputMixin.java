package fr.reborn.hud.mixin;

import fr.reborn.hud.camera.RebornCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec2;
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
 * <p>NB (26.1) : l'ancien {@code Input} mutable a disparu. Le mouvement calculé
 * vit désormais dans {@code ClientInput#moveVector} (un {@link Vec2} :
 * {@code x} = latéral, {@code y} = avant), superclasse de {@link KeyboardInput}
 * — on y accède par {@code @Shadow}.
 */
@Mixin(KeyboardInput.class)
public abstract class KeyboardInputMixin {

    /** Ticks restants d'aim « collant » après un clic (PVP fluide). */
    @Unique
    private int reborn$aimHold = 0;

    // 26.1 : KeyboardInput#tick() ne prend plus (boolean slowDown, float factor).
    @Inject(method = "tick", at = @At("TAIL"))
    private void reborn$cameraRelativeMovement(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        // GARDE / PARADE (touche C maintenue) : le joueur est cloué au sol — aucun
        // déplacement horizontal possible. On zéro le moveVector calculé par le tick
        // vanilla (le saut est neutralisé par PlayerJumpMixin, le dash/double-saut/
        // saut-chakra par leurs gardes côté CombatInput/MobilityInput/ChakraJump).
        if (player != null && fr.reborn.hud.combat.CombatInput.INSTANCE.isBlocking()) {
            ((ClientInputAccessor) (Object) this).reborn$setMoveVector(Vec2.ZERO);
            return;
        }
        RebornCamera cam = RebornCamera.INSTANCE;
        if (!cam.isEnabled()) return;
        if (player == null || mc.options == null) return;

        float cyaw = (float) cam.camYaw();

        // AIM-MODE : attaquer (clic gauche) / utiliser (clic droit) → le perso
        // s'aligne sur la caméra pour que minage / combat / placement visent le
        // viseur. « Collant » ~10 ticks après le dernier clic pour un PVP fluide
        // (on peut strafe autour de la cible en gardant l'aim).
        if (mc.options.keyAttack.isDown() || mc.options.keyUse.isDown()) {
            reborn$aimHold = 10;
        }
        if (reborn$aimHold > 0) {
            reborn$aimHold--;
            float cpitch = (float) cam.camPitch();
            player.setYRot(cyaw);
            player.setXRot(cpitch);
            player.setYBodyRot(cyaw);
            player.setYHeadRot(cyaw);
            return; // déplacement reste relatif caméra (yaw = camYaw)
        }

        // FREE-LOOK (maintien ALT) : le perso est gelé — aucune orientation posée
        // ici, et LocalPlayerBodyMixin ré-applique la pose capturée au TAIL du
        // tick (sinon vanilla la rétablirait vers la direction de déplacement).
        // Le déplacement reste relatif au perso (input vanilla), la caméra orbite.
        if (cam.freeLook()) return;

        Vec2 mv = ((ClientInput) (Object) this).getMoveVector();
        float mf = mv.y; // avant
        float ms = mv.x; // latéral

        // ARRÊT : la tête suit la caméra. On pose yRot/xRot ICI et pas au TAIL du
        // tick joueur, parce que LocalPlayer#sendPosition part APRÈS aiStep : posé
        // au TAIL, le mouvement de tête arrivait un tick en retard côté serveur (et
        // le yaw n'était même pas envoyé du tout — les autres ne voyaient que le
        // haut/bas). Le VISUEL corps/tête reste géré par LocalPlayerBodyMixin.
        if (mf == 0f && ms == 0f) {
            float align = cam.alignFactor();
            float yaw = Mth.rotLerp(align, player.getYRot(), cyaw);
            player.setYRot(yaw);
            player.setXRot(Mth.lerp(align, player.getXRot(), (float) cam.camPitch()));
            player.setYHeadRot(yaw);
            return;
        }

        // BASE (marche / course) — principe Minecraft : le perso suit la caméra
        // (souris). On pose yaw = camYaw pour que le DÉPLACEMENT soit relatif
        // caméra ce tick ; l'orientation VISIBLE du corps est re-forcée en
        // post-tick (LocalPlayerBodyMixin) car vanilla tourne sinon le corps vers
        // la direction de déplacement, écrasant notre valeur. Le pivot vers la
        // direction de déplacement = Naruto run.
        if (!fr.reborn.hud.animation.NarutoRun.INSTANCE.isActive()) {
            // alignFactor() == 1 hors rattrapage post-free-look → collage instantané
            // (comportement d'origine) ; < 1 pendant ~5 ticks après un relâchement.
            float yaw = Mth.rotLerp(cam.alignFactor(), player.getYRot(), cyaw);
            player.setYRot(yaw);
            player.setYBodyRot(yaw);
            player.setYHeadRot(yaw);
            return;
        }

        // NARUTO RUN — principe Elden Ring : le corps pivote vers la direction de
        // déplacement (free-run), caméra libre.

        double cy = Math.toRadians(cam.camYaw());
        // Repère caméra : avant = (-sin, cos) ; latéral = (-cos, -sin).
        // Signe « - » sur le latéral : corrige l'inversion gauche/droite.
        double fx = -Math.sin(cy), fz = Math.cos(cy);
        double lx = -Math.cos(cy), lz = -Math.sin(cy);
        double dx = fx * mf - lx * ms;
        double dz = fz * mf - lz * ms;

        float target = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float ny = Mth.rotLerp((float) cam.turnSpeed(), player.getYRot(), target);
        player.setYRot(ny);
        player.setYBodyRot(ny);
        player.setYHeadRot(ny);

        // Le perso court « tout droit » dans son orientation (magnitude conservée
        // pour garder le ralenti sneak/objet).
        ((ClientInputAccessor) (Object) this)
            .reborn$setMoveVector(new Vec2(0f, (float) Math.min(1.0, Math.sqrt(mf * mf + ms * ms))));
    }
}
