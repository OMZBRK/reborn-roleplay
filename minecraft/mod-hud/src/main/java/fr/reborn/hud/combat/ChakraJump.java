package fr.reborn.hud.combat;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.ArrayList;
import java.util.List;

/**
 * Saut de chakra : <b>sneak maintenu + espace maintenu</b> charge un saut (le saut
 * vanilla est neutralisé pendant la charge via {@code PlayerJumpMixin}). Une
 * <b>courbe de visée</b> montre la trajectoire (direction = regard horizontal,
 * portée/hauteur = charge). À la relâche : on envoie la vélocité au serveur
 * ({@code reborn:combatin} kind=5) qui l'applique, et on lance le cooldown.
 */
public final class ChakraJump {

    public static final ChakraJump INSTANCE = new ChakraJump();

    private static final long MAX_CHARGE_MS = 900L;
    private static final float MIN_POWER = 0.15f;

    private boolean charging = false;
    private long chargeStartMs = 0L;

    private ChakraJump() {}

    public boolean isCharging() { return charging; }

    public float power() {
        if (!charging) return 0f;
        long el = System.currentTimeMillis() - chargeStartMs;
        return Math.max(0f, Math.min(1f, el / (float) MAX_CHARGE_MS));
    }

    /** Vrai si le geste de charge est actif → le mixin neutralise le saut vanilla. */
    public boolean suppressesJump() {
        if (charging) return true;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        return p != null && mc.options != null && mc.gui.screen() == null
            && mc.options.keyShift.isDown() && mc.options.keyJump.isDown() && p.onGround();
    }

    /** À appeler chaque client tick (piloté par {@code HudKeybinds}). */
    public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gui.screen() != null) { charging = false; return; }
        boolean keys = mc.options.keyShift.isDown() && mc.options.keyJump.isDown();
        long now = System.currentTimeMillis();
        if (charging) {
            if (!keys) { release(p); }
        } else if (keys && p.onGround()
                && CooldownState.INSTANCE.fraction(CooldownState.Ability.CHAKRA_JUMP, now) <= 0f) {
            charging = true;
            chargeStartMs = now;
        }
    }

    private void release(LocalPlayer p) {
        float pw = power();
        charging = false;
        if (pw < MIN_POWER) return;
        Vec3 v = leapVelocity(p.getYRot(), pw);
        if (ClientPlayNetworking.canSend(CombatInputPayload.ID)) {
            ClientPlayNetworking.send(CombatInputPayload.chakraJump((float) v.x, (float) v.y, (float) v.z));
        }
        CooldownState.INSTANCE.trigger(CooldownState.Ability.CHAKRA_JUMP, CooldownState.CHAKRA_JUMP_CD_MS);
    }

    /** Vélocité de saut selon le yaw + la charge (avant horizontal + vertical). */
    private static Vec3 leapVelocity(float yaw, float power) {
        double yr = Math.toRadians(yaw);
        double hx = -Math.sin(yr), hz = Math.cos(yr);
        double h = 0.35 + power * 1.15;   // 0.35..1.5
        double vy = 0.55 + power * 0.55;  // 0.55..1.1
        return new Vec3(hx * h, vy, hz * h);
    }

    public void reset() { charging = false; }

    // ── Courbe de visée (rendu HUD, points de trajectoire projetés depuis le monde) ──
    public void renderAimCurve(GuiGraphicsExtractor ctx) {
        if (!charging) return;
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer p = mc.player;
        if (p == null) return;
        Camera cam = mc.gameRenderer.mainCamera();
        if (cam == null || !cam.isInitialized()) return;

        List<Vec3> pts = trajectory(p, power());
        Vec3 camPos = cam.position();
        Matrix4f vp = cam.getViewRotationProjectionMatrix(new Matrix4f());
        int gw = mc.getWindow().getGuiScaledWidth(), gh = mc.getWindow().getGuiScaledHeight();
        int color = 0xFF9B7FE0;   // violet chakra
        for (int i = 0; i < pts.size(); i++) {
            Vec3 wp = pts.get(i);
            Vector4f clip = vp.transform(new Vector4f(
                (float) (wp.x - camPos.x), (float) (wp.y - camPos.y), (float) (wp.z - camPos.z), 1f));
            if (clip.w() <= 0.05f) continue;
            float nx = clip.x() / clip.w(), ny = clip.y() / clip.w();
            if (nx < -1.1f || nx > 1.1f || ny < -1.1f || ny > 1.1f) continue;
            int sx = Math.round((nx * 0.5f + 0.5f) * gw);
            int sy = Math.round((1f - (ny * 0.5f + 0.5f)) * gh);
            int r = (i == pts.size() - 1) ? 3 : 2;   // point d'atterrissage plus gros
            ctx.fill(sx - r, sy - r, sx + r, sy + r, color);
        }
    }

    /** Simule la parabole (physique joueur approx.) pour prévisualiser le saut. */
    private static List<Vec3> trajectory(LocalPlayer p, float power) {
        Vec3 pos = p.position().add(0, 0.4, 0);
        Vec3 v = leapVelocity(p.getYRot(), power);
        List<Vec3> pts = new ArrayList<>();
        double startY = p.getY();
        for (int i = 0; i < 40; i++) {
            pts.add(pos);
            pos = pos.add(v);
            v = new Vec3(v.x * 0.91, (v.y - 0.08) * 0.98, v.z * 0.91);
            if (i > 3 && pos.y < startY) { pts.add(pos); break; }   // atterrissage
        }
        return pts;
    }
}
