package fr.reborn.hud.combat;

import fr.reborn.hud.camera.RebornCamera;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * <b>Double saut</b> : DOUBLE-TAP barre espace (le 1er saut décolle, le 2e en l'air
 * = double saut) — sans sneak (sneak+espace maintenus = saut de chakra). Visé à la
 * SOURIS/caméra. Une charge par phase aérienne (rechargée au contact du sol) ; en
 * combat, un cooldown de quelques secondes en plus. Pas de particules (retour user).
 *
 * <p>Le dash 8-dir, lui, est sur la touche V ({@link CombatInput#dash}).
 */
public final class MobilityInput {

    public static final MobilityInput INSTANCE = new MobilityInput();

    private static final long DTAP_MS = 300L;
    private static final long DJUMP_COMBAT_CD_MS = 3000L;

    private boolean wasSpace = false;
    private long lastSpaceTap = 0L;
    private boolean hasDoubleJump = true;
    private long lastDoubleJumpMs = 0L;

    private MobilityInput() {}

    public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gui.screen() != null) { wasSpace = false; return; }
        long now = System.currentTimeMillis();
        boolean sneaking = mc.options.keyShift.isDown();

        if (p.onGround()) hasDoubleJump = true;   // recharge au sol

        boolean sp = mc.options.keyJump.isDown();
        if (sp && !wasSpace) {   // front montant d'un appui espace
            if (!sneaking && now - lastSpaceTap <= DTAP_MS) {
                // 2e tap → double saut (si en l'air + dispo).
                if (!p.onGround() && hasDoubleJump) {
                    boolean inCombat = CombatState.INSTANCE.inCombat(now);
                    if (!inCombat || now - lastDoubleJumpMs >= DJUMP_COMBAT_CD_MS) {
                        doDoubleJump(p);
                        hasDoubleJump = false;
                        lastDoubleJumpMs = now;
                    }
                }
                lastSpaceTap = 0;
            } else {
                lastSpaceTap = now;
            }
        }
        wasSpace = sp;
    }

    /** Double saut visé à la souris/caméra (toujours un peu de lift). Sans particules. */
    private void doDoubleJump(LocalPlayer p) {
        Vec3 aim = aimDir(p);
        double h = 1.0;
        double vy = 0.7 + Math.max(0.0, aim.y) * 0.7;   // vise haut → plus de hauteur
        if (ClientPlayNetworking.canSend(CombatInputPayload.ID)) {
            ClientPlayNetworking.send(CombatInputPayload.kerioxDash(
                (float) (aim.x * h), (float) vy, (float) (aim.z * h)));
        }
    }

    /** Direction de visée = orbite caméra Reborn (souris) en vue épaule, sinon regard joueur. */
    static Vec3 aimDir(LocalPlayer p) {
        RebornCamera cam = RebornCamera.INSTANCE;
        float yaw, pitch;
        if (cam.isEnabled()) { yaw = (float) cam.camYaw(); pitch = (float) cam.camPitch(); }
        else { yaw = p.getYRot(); pitch = p.getXRot(); }
        double yr = Math.toRadians(yaw), pr = Math.toRadians(pitch);
        double x = -Math.sin(yr) * Math.cos(pr);
        double y = -Math.sin(pr);
        double z = Math.cos(yr) * Math.cos(pr);
        return new Vec3(x, y, z);
    }
}
