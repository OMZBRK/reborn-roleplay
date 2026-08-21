package fr.reborn.hud.combat;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/**
 * Saut de chakra : <b>sneak maintenu + espace maintenu</b> charge un vrai bond (le
 * saut vanilla est neutralisé pendant la charge via {@code PlayerJumpMixin}). Un
 * <b>aperçu de trajectoire en PARTICULES vanilla</b> montre où l'on va (direction =
 * regard horizontal, portée/hauteur = charge). À la relâche : on envoie la vélocité
 * au serveur ({@code reborn:combatin} kind=5) qui l'applique (clampée), et on lance
 * le cooldown. Pas de coût — long CD.
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
            if (!keys) { release(p); return; }
            spawnAimParticles(mc, p, power());   // aperçu de visée en particules vanilla
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
        Vec3 v = leapVelocity(p, pw);
        if (ClientPlayNetworking.canSend(CombatInputPayload.ID)) {
            ClientPlayNetworking.send(CombatInputPayload.chakraJump((float) v.x, (float) v.y, (float) v.z));
        }
        CooldownState.INSTANCE.trigger(CooldownState.Ability.CHAKRA_JUMP, CooldownState.CHAKRA_JUMP_CD_MS);
    }

    /**
     * Vélocité de saut façon ShinobiMobility (Keriox) : on utilise la direction de
     * regard COMPLÈTE (yaw + PITCH) → visable en l'air (à plat = loin, vers le haut =
     * arc haut, portée projectile). Horizontal fixe, vertical = base + charge.
     */
    private static Vec3 leapVelocity(LocalPlayer p, float power) {
        Vec3 aim = MobilityInput.aimDir(p);    // visée SOURIS/caméra (pas le corps)
        double speed = 2.4 + power * 2.2;      // GRAND bond ninja, croît avec la charge (2.4..4.6)
        Vec3 v = aim.scale(speed);             // va OÙ c'est visé : bas → descend (pas de saut)
        return new Vec3(v.x, v.y + 0.5, v.z);  // léger base up → arc à plat, GROS arc en visant haut
    }

    public void reset() { charging = false; }

    /** Dust violet chakra pour la ligne de trajectoire (couleur, petite taille). */
    private static final net.minecraft.core.particles.DustParticleOptions CHAKRA_DUST =
        new net.minecraft.core.particles.DustParticleOptions(0x9B7FE0, 0.9f);

    /** LIGNE continue de trajectoire (dust interpolé) + CERCLE au point d'atterrissage. */
    private static void spawnAimParticles(Minecraft mc, LocalPlayer p, float power) {
        List<Vec3> pts = trajectory(p, power);
        for (int i = 0; i + 1 < pts.size(); i++) {   // interpole entre points → ligne pleine
            Vec3 a = pts.get(i), b = pts.get(i + 1);
            for (int s = 0; s < 2; s++) {
                double t = s / 2.0;
                mc.level.addParticle(CHAKRA_DUST,
                    a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t, a.z + (b.z - a.z) * t, 0.0, 0.0, 0.0);
            }
        }
        if (!pts.isEmpty()) {   // cercle d'atterrissage dense
            Vec3 last = pts.get(pts.size() - 1);
            int n = 20; double r = 0.9;
            for (int i = 0; i < n; i++) {
                double ang = 2 * Math.PI * i / n;
                mc.level.addParticle(CHAKRA_DUST,
                    last.x + Math.cos(ang) * r, last.y + 0.05, last.z + Math.sin(ang) * r, 0.0, 0.0, 0.0);
            }
        }
    }

    /** Simule la parabole (physique joueur approx.) pour l'aperçu de saut. */
    private static List<Vec3> trajectory(LocalPlayer p, float power) {
        Vec3 pos = p.position().add(0, 0.4, 0);
        Vec3 v = leapVelocity(p, power);
        List<Vec3> pts = new ArrayList<>();
        double startY = p.getY();
        for (int i = 0; i < 60; i++) {
            pts.add(pos);
            pos = pos.add(v);
            v = new Vec3(v.x * 0.91, (v.y - 0.08) * 0.98, v.z * 0.91);
            if (i > 3 && pos.y < startY) { pts.add(pos); break; }   // atterrissage
        }
        return pts;
    }
}
