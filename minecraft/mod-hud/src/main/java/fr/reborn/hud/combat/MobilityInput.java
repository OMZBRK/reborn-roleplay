package fr.reborn.hud.combat;

import fr.reborn.hud.camera.RebornCamera;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.phys.Vec3;

/**
 * Inputs de mobilité (client) :
 * <ul>
 *   <li><b>Double-tap W/A/S/D</b> → DASH 8-directions (direction WASD relative au
 *       corps, valeurs du dash serveur {@code KIND_DASH}) + particules.</li>
 *   <li><b>Espace en l'air</b> (sans sneak) → DOUBLE SAUT visé à la SOURIS/caméra.
 *       Une charge par phase aérienne (rechargée au contact du sol) ; en combat,
 *       un cooldown de quelques secondes en plus.</li>
 * </ul>
 * Le client calcule la vélocité MONDE et l'envoie ({@code reborn:combatin}) ; le
 * serveur clampe + applique. La visée (souris) = orbite caméra Reborn en vue épaule,
 * sinon regard du joueur.
 */
public final class MobilityInput {

    public static final MobilityInput INSTANCE = new MobilityInput();

    private static final long DTAP_MS = 300L;
    private static final long DASH_MIN_MS = 400L;          // anti double-déclenchement
    private static final long DJUMP_COMBAT_CD_MS = 3000L;  // CD du double saut EN COMBAT

    // index : 0=W 1=S 2=A 3=D
    private final long[] lastTap = new long[4];
    private final boolean[] was = new boolean[4];
    private long lastDashMs = 0L;

    private boolean wasSpace = false;
    private boolean hasDoubleJump = true;
    private long lastDoubleJumpMs = 0L;

    private MobilityInput() {}

    public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gui.screen() != null) {
            java.util.Arrays.fill(was, false); wasSpace = false; return;
        }
        long now = System.currentTimeMillis();
        boolean sneaking = mc.options.keyShift.isDown();

        // Recharge le double saut au contact du sol.
        if (p.onGround()) hasDoubleJump = true;

        // ── DASH 8-dir : double-tap d'une touche de déplacement ──
        KeyMapping[] keys = { mc.options.keyUp, mc.options.keyDown, mc.options.keyLeft, mc.options.keyRight };
        for (int i = 0; i < 4; i++) {
            boolean down = keys[i].isDown();
            if (down && !was[i]) {
                if (now - lastTap[i] <= DTAP_MS && now - lastDashMs >= DASH_MIN_MS) {
                    doDash(mc, p);
                    lastDashMs = now;
                    lastTap[i] = 0;
                } else {
                    lastTap[i] = now;
                }
            }
            was[i] = down;
        }

        // ── DOUBLE SAUT : espace pressé EN L'AIR, sans sneak (sneak+espace = saut de chakra) ──
        boolean sp = mc.options.keyJump.isDown();
        if (sp && !wasSpace && !p.onGround() && !sneaking && hasDoubleJump) {
            boolean inCombat = CombatState.INSTANCE.inCombat(now);
            if (!inCombat || now - lastDoubleJumpMs >= DJUMP_COMBAT_CD_MS) {
                doDoubleJump(p);
                hasDoubleJump = false;
                lastDoubleJumpMs = now;
            }
        }
        wasSpace = sp;
    }

    /** Dash dans la direction WASD courante (8 dirs, relative au corps) + particules. */
    private void doDash(Minecraft mc, LocalPlayer p) {
        double yr = Math.toRadians(p.getYRot());
        double fx = -Math.sin(yr), fz = Math.cos(yr);   // avant
        double lx = Math.cos(yr), lz = Math.sin(yr);    // gauche
        int f = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
        int l = (mc.options.keyLeft.isDown() ? 1 : 0) - (mc.options.keyRight.isDown() ? 1 : 0);
        double dx, dz;
        if (f == 0 && l == 0) { dx = -fx; dz = -fz; }   // aucune touche → arrière
        else { dx = f * fx + l * lx; dz = f * fz + l * lz; }
        double len = Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0e-6) return;
        dx /= len; dz /= len;

        send(CombatInputPayload.dash((float) dx, (float) dz));
        CooldownState.INSTANCE.trigger(CooldownState.Ability.DASH, CooldownState.DASH_CD_MS);
        spawnTrail(mc, p, new Vec3(dx, 0.1, dz), ParticleTypes.CLOUD, 8);
    }

    /** Double saut visé à la souris/caméra (toujours un peu de lift). */
    private void doDoubleJump(LocalPlayer p) {
        Vec3 aim = aimDir(p);
        double h = 1.0;
        double vy = 0.7 + Math.max(0.0, aim.y) * 0.7;   // vise haut → plus de hauteur
        send(CombatInputPayload.kerioxDash((float) (aim.x * h), (float) vy, (float) (aim.z * h)));
        Minecraft mc = Minecraft.getInstance();
        spawnTrail(mc, p, new Vec3(0, 1, 0), ParticleTypes.END_ROD, 6);
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

    private static void spawnTrail(Minecraft mc, LocalPlayer p, Vec3 dir, net.minecraft.core.particles.ParticleOptions particle, int n) {
        Vec3 base = p.position().add(0, 0.9, 0);
        for (int i = 0; i < n; i++) {
            Vec3 w = base.add(dir.scale(0.35 * i));
            mc.level.addParticle(particle, w.x, w.y, w.z, 0, 0, 0);
        }
    }

    private static void send(CombatInputPayload payload) {
        if (ClientPlayNetworking.canSend(CombatInputPayload.ID)) ClientPlayNetworking.send(payload);
    }
}
