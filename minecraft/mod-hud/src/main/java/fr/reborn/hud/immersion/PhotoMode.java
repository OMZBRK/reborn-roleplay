package fr.reborn.hud.immersion;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.math.Vec3d;

/**
 * Mode Photo : caméra libre (free-cam) pendant que le joueur reste figé. On vole
 * avec ZQSD + Espace/Shift (Sprint = ×2.5). L'UI (curseur, look, bouton
 * Capturer) est gérée par {@link fr.reborn.hud.ui.PhotoModeScreen} ; la caméra
 * est repositionnée par {@code CameraPhotoMixin}.
 *
 * <p>On force la <b>3e personne</b> pendant le mode (sinon en 1ère personne on
 * ne voit pas le perso et la main apparaît) et on bloque le changement de vue.
 */
public final class PhotoMode {

    public static final PhotoMode INSTANCE = new PhotoMode();

    private static final float MOVE_SPEED = 0.35f;
    private static final float SENS = 0.15f;

    private boolean active = false;
    private double x, y, z;
    private float yaw, pitch;
    private boolean pendingCapture = false;
    private Perspective savedPerspective = Perspective.FIRST_PERSON;

    private PhotoMode() {}

    public boolean isActive() { return active; }

    /** Démarre le mode depuis la caméra joueur actuelle. */
    public void begin(MinecraftClient mc) {
        if (mc.player == null) return;
        Vec3d eye = mc.player.getEyePos();
        x = eye.x; y = eye.y; z = eye.z;
        yaw = mc.player.getYaw();
        pitch = mc.player.getPitch();
        savedPerspective = mc.options.getPerspective();
        mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        active = true;
    }

    public void end(MinecraftClient mc) {
        active = false;
        if (mc.options != null) mc.options.setPerspective(savedPerspective);
    }

    public Vec3d pos() { return new Vec3d(x, y, z); }
    public float yaw() { return yaw; }
    public float pitch() { return pitch; }

    /** Rotation depuis un delta souris (drag). */
    public void rotate(double dx, double dy) {
        yaw += (float) (dx * SENS);
        pitch = (float) Math.max(-90, Math.min(90, pitch + dy * SENS));
    }

    /** Déplacement free-cam + maintien de la 3e personne. Appelé chaque tick. */
    public void tickMovement(MinecraftClient mc) {
        if (!active || mc.options == null) return;
        // Bloque le changement de vue (F5) pendant le mode.
        if (mc.options.getPerspective() != Perspective.THIRD_PERSON_BACK) {
            mc.options.setPerspective(Perspective.THIRD_PERSON_BACK);
        }
        long win = mc.getWindow().getHandle();
        float f = 0, s = 0, up = 0;
        if (down(mc, win, mc.options.forwardKey)) f += 1;
        if (down(mc, win, mc.options.backKey)) f -= 1;
        if (down(mc, win, mc.options.rightKey)) s += 1;
        if (down(mc, win, mc.options.leftKey)) s -= 1;
        if (down(mc, win, mc.options.jumpKey)) up += 1;
        if (down(mc, win, mc.options.sneakKey)) up -= 1;
        double yr = Math.toRadians(yaw);
        double fx = -Math.sin(yr), fz = Math.cos(yr);
        double rx = -Math.cos(yr), rz = -Math.sin(yr);
        double spd = MOVE_SPEED * (down(mc, win, mc.options.sprintKey) ? 2.5 : 1.0);
        x += (fx * f + rx * s) * spd;
        z += (fz * f + rz * s) * spd;
        y += up * spd;
    }

    /** État physique d'une touche (marche même avec un écran ouvert). */
    private static boolean down(MinecraftClient mc, long win, KeyBinding kb) {
        try {
            InputUtil.Key key = InputUtil.fromTranslationKey(kb.getBoundKeyTranslationKey());
            return InputUtil.isKeyPressed(win, key.getCode());
        } catch (RuntimeException e) {
            return false;
        }
    }

    public void requestCapture() { pendingCapture = true; }

    public boolean consumeCapture() {
        if (!pendingCapture) return false;
        pendingCapture = false;
        return true;
    }
}
