package fr.reborn.hud.immersion;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.math.Vec3d;

/**
 * Mode Photo : caméra libre (free-cam) pendant que le joueur reste figé. On
 * vole avec ZQSD + Espace/Shift, on regarde à la souris ; <b>clic gauche =
 * capture</b> (screenshot propre, sans HUD). HUD masqué + petit panneau pendant
 * le mode. Toggle via la touche bind (P).
 *
 * <p>La caméra est repositionnée par {@code CameraPhotoMixin}, le joueur figé
 * par {@code KeyboardInputPhotoMixin}, la souris/clic par {@code
 * MouseInteractionMixin}, et le rendu/capture par {@code InGameHudCinemaMixin}.
 */
public final class PhotoMode {

    public static final PhotoMode INSTANCE = new PhotoMode();

    private static final float MOVE_SPEED = 0.35f;
    private static final float SENS = 0.15f;

    private boolean active = false;
    private double x, y, z;
    private float yaw, pitch;
    private boolean pendingCapture = false;

    private PhotoMode() {}

    public boolean isActive() { return active; }

    public void toggle() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (active) { active = false; return; }
        if (mc.player == null || mc.currentScreen != null) return;
        Vec3d eye = mc.player.getEyePos();
        x = eye.x; y = eye.y; z = eye.z;
        yaw = mc.player.getYaw();
        pitch = mc.player.getPitch();
        active = true;
    }

    public Vec3d pos() { return new Vec3d(x, y, z); }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }

    /** Rotation depuis le delta souris (px). */
    public void rotate(double dx, double dy) {
        yaw += (float) (dx * SENS);
        pitch = (float) Math.max(-90, Math.min(90, pitch + dy * SENS));
    }

    /** Déplacement free-cam (lu chaque tick depuis les touches de mouvement). */
    public void tickMovement(MinecraftClient mc) {
        if (!active || mc.options == null) return;
        float f = 0, s = 0, up = 0;
        if (mc.options.forwardKey.isPressed()) f += 1;
        if (mc.options.backKey.isPressed()) f -= 1;
        if (mc.options.rightKey.isPressed()) s += 1;
        if (mc.options.leftKey.isPressed()) s -= 1;
        if (mc.options.jumpKey.isPressed()) up += 1;
        if (mc.options.sneakKey.isPressed()) up -= 1;
        double yr = Math.toRadians(yaw);
        double fx = -Math.sin(yr), fz = Math.cos(yr);
        double rx = -Math.cos(yr), rz = -Math.sin(yr);
        double spd = MOVE_SPEED * (mc.options.sprintKey.isPressed() ? 2.5 : 1.0);
        x += (fx * f + rx * s) * spd;
        z += (fz * f + rz * s) * spd;
        y += up * spd;
    }

    public void requestCapture() { pendingCapture = true; }

    /** À consommer côté rendu (HEAD du HUD) pour capturer une frame propre. */
    public boolean consumeCapture() {
        if (!pendingCapture) return false;
        pendingCapture = false;
        return true;
    }
}
