package fr.reborn.hud.combat;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Inputs de mobilité par DOUBLE-TAP (répliqués/inspirés de ShinobiMobility de Keriox,
 * pour tester/comparer) :
 * <ul>
 *   <li><b>Double-tap ESPACE</b> (sans sneak) → dash « Keriox » pitch-aware (viser
 *       haut = plus de hauteur, viser bas = plus loin).</li>
 *   <li><b>Double-tap A / D / S</b> → esquive latérale/arrière.</li>
 * </ul>
 * Le client calcule la vélocité MONDE et l'envoie ({@code reborn:combatin} kind 6/7) ;
 * le serveur clampe + applique (autoritaire) avec cooldown. Coexiste avec le dash
 * 8-dir (touche V) le temps de choisir. Le double-tap W est ignoré (conflit sprint).
 */
public final class MobilityInput {

    public static final MobilityInput INSTANCE = new MobilityInput();
    private static final long DTAP_MS = 300L;
    private static final double DODGE_SPEED = 1.5;

    // index : 0=W(keyUp) 1=S(keyDown) 2=A(keyLeft) 3=D(keyRight)
    private final long[] lastTap = new long[4];
    private final boolean[] was = new boolean[4];
    private boolean wasSpace = false;
    private long lastSpaceTap = 0L;

    private MobilityInput() {}

    public void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.gui.screen() != null) {
            java.util.Arrays.fill(was, false); wasSpace = false; return;
        }
        long now = System.currentTimeMillis();
        boolean sneaking = mc.options.keyShift.isDown();

        // Double-tap ESPACE (sans sneak, pour ne pas gêner la charge du saut de chakra) → dash.
        boolean sp = mc.options.keyJump.isDown();
        if (sp && !wasSpace) {
            if (!sneaking && now - lastSpaceTap <= DTAP_MS) { sendKerioxDash(p); lastSpaceTap = 0; }
            else lastSpaceTap = now;
        }
        wasSpace = sp;

        // Double-tap A / D / S → esquive (W ignoré : conflit avec le sprint vanilla).
        KeyMapping[] keys = { mc.options.keyUp, mc.options.keyDown, mc.options.keyLeft, mc.options.keyRight };
        for (int i = 1; i < 4; i++) {   // saute W (index 0)
            boolean down = keys[i].isDown();
            if (down && !was[i]) {
                if (now - lastTap[i] <= DTAP_MS) { sendDodge(p, i); lastTap[i] = 0; }
                else lastTap[i] = now;
            }
            was[i] = down;
        }
        was[0] = keys[0].isDown();
    }

    /** Dash « Keriox » : direction de regard à plat, modulée par le pitch. */
    private static void sendKerioxDash(LocalPlayer p) {
        Vec3 look = p.getLookAngle();
        float pitch = p.getXRot();
        double h = 2.0, vy = 1.0;
        if (pitch < -30f) { h *= 0.5; vy *= 1.5; }        // vise haut → plus de hauteur
        else if (pitch > 30f) { vy *= 0.5; h *= 1.5; }    // vise bas → plus de forward
        Vec3 flat = new Vec3(look.x, 0, look.z);
        double len = flat.length();
        if (len < 1.0e-6) {
            double yr = Math.toRadians(p.getYRot());
            flat = new Vec3(-Math.sin(yr), 0, Math.cos(yr)); len = 1.0;
        }
        flat = flat.scale(1.0 / len);
        send(CombatInputPayload.kerioxDash((float) (flat.x * h), (float) vy, (float) (flat.z * h)));
    }

    /** Esquive : A=gauche(2), D=droite(3), S=arrière(1), relatif à la vue. */
    private static void sendDodge(LocalPlayer p, int idx) {
        double yr = Math.toRadians(p.getYRot());
        double fx = -Math.sin(yr), fz = Math.cos(yr);   // avant
        double lx = Math.cos(yr), lz = Math.sin(yr);    // gauche
        double dx, dz;
        switch (idx) {
            case 2 -> { dx = lx;  dz = lz;  }    // A = gauche
            case 3 -> { dx = -lx; dz = -lz; }    // D = droite
            default -> { dx = -fx; dz = -fz; }   // S = arrière
        }
        send(CombatInputPayload.dodge((float) (dx * DODGE_SPEED), 0.15f, (float) (dz * DODGE_SPEED)));
    }

    private static void send(CombatInputPayload payload) {
        if (ClientPlayNetworking.canSend(CombatInputPayload.ID)) ClientPlayNetworking.send(payload);
    }
}
