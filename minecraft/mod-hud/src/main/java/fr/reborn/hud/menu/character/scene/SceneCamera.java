package fr.reborn.hud.menu.character.scene;

import net.minecraft.world.phys.Vec3;

/**
 * Caméra de mise en scène (sélection du personnage) : quand elle est active, {@code CameraPhotoMixin} remplace la
 * position, l'orientation et le champ de vision de la caméra du jeu. Les plans s'enchaînent par travelling
 * (interpolation douce), avec un léger mouvement « à l'épaule » pour que l'image vive.
 */
public final class SceneCamera {

    public record Shot(Vec3 pos, float yaw, float pitch, float fov) {
        /** Plan qui regarde {@code target} depuis {@code from}. */
        public static Shot look(Vec3 from, Vec3 target, float fov) {
            Vec3 d = target.subtract(from);
            float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
            float pitch = (float) -Math.toDegrees(Math.atan2(d.y, Math.sqrt(d.x * d.x + d.z * d.z)));
            return new Shot(from, yaw, pitch, fov);
        }

        Shot mix(Shot o, double t) {
            return new Shot(pos.lerp(o.pos, t), lerpAngle(yaw, o.yaw, t), (float) (pitch + (o.pitch - pitch) * t),
                    (float) (fov + (o.fov - fov) * t));
        }

        private static float lerpAngle(float a, float b, double t) {
            float d = ((b - a) % 360 + 540) % 360 - 180;
            return (float) (a + d * t);
        }
    }

    private static boolean active;
    private static Shot from, to;
    private static long startMs, durMs = 1;
    private static float sway = 1f;

    private SceneCamera() {}

    public static boolean active() { return active; }

    public static void start(Shot s) {
        from = to = s;
        startMs = System.currentTimeMillis();
        durMs = 1;
        active = true;
    }

    /** Travelling vers {@code s} en {@code ms} millisecondes (depuis la position courante). */
    public static void moveTo(Shot s, long ms) {
        if (!active) { start(s); return; }
        from = current(false);
        to = s;
        startMs = System.currentTimeMillis();
        durMs = Math.max(1, ms);
    }

    public static void setSway(float k) { sway = k; }

    public static void stop() { active = false; }

    public static Shot current() { return current(true); }

    private static Shot current(boolean withSway) {
        double t = Math.min(1, (System.currentTimeMillis() - startMs) / (double) durMs);
        double e = t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;   // easeInOutCubic
        Shot s = from.mix(to, e);
        if (!withSway || sway <= 0) return s;
        double now = System.currentTimeMillis() / 1000.0;
        Vec3 drift = new Vec3(Math.sin(now * 0.31) * 0.035, Math.sin(now * 0.47 + 1) * 0.025, Math.sin(now * 0.23 + 2) * 0.03)
                .scale(sway);
        return new Shot(s.pos.add(drift), s.yaw + (float) (Math.sin(now * 0.29) * 0.35 * sway),
                s.pitch + (float) (Math.sin(now * 0.37 + 0.5) * 0.25 * sway), s.fov);
    }
}
