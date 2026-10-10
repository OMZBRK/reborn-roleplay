package fr.reborn.hud.menu.character.scene;

import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Effets du vide sombre des scènes, dessinés en lumière (non soumis à l'éclairage du monde) :
 * <ul>
 *   <li>{@link #aura} : sous le perso, des braises de chakra qui tournent lentement en cercle au sol et d'autres
 *       qui s'élèvent et s'éteignent — à la couleur du village ; les points derrière les jambes sont masqués ;</li>
 *   <li>{@link #fireflies} : des lucioles dorées qui dérivent dans la nuit.</li>
 * </ul>
 */
public final class SceneFx {

    private SceneFx() {}

    private static float[] project(Matrix4f vp, Vec3 cp, Vec3 p, int w, int h) {
        Vector4f c = vp.transform(new Vector4f((float) (p.x - cp.x), (float) (p.y - cp.y), (float) (p.z - cp.z), 1f));
        if (c.w() <= 0.05f) return null;
        return new float[]{(c.x() / c.w() * 0.5f + 0.5f) * w, (1f - (c.y() / c.w() * 0.5f + 0.5f)) * h, c.w()};
    }

    /**
     * Braises sous les pieds. {@code strength} 0..1 (plein pour le perso choisi), {@code color} RGB du village,
     * {@code seed} pour que deux persos n'aient pas le même motif.
     */
    public static void aura(GuiGraphicsExtractor g, Vec3 feet, double radius, float strength, int color, int seed,
                            int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        Camera cam = mc.gameRenderer.mainCamera();
        if (cam == null || !cam.isInitialized() || strength <= 0.01f) return;
        Matrix4f vp = cam.getViewRotationProjectionMatrix(new Matrix4f());
        Vec3 cp = cam.position();
        Vec3 toCam = new Vec3(cp.x - feet.x, 0, cp.z - feet.z).normalize();
        double t = System.currentTimeMillis() / 1000.0;
        int rgb = color & 0xFFFFFF;
        java.util.Random r = new java.util.Random(seed * 7919L);
        // braises au sol : tournent lentement, scintillent
        int n = 26;
        for (int i = 0; i < n; i++) {
            double a = i * Math.PI * 2 / n + t * (0.18 + r.nextDouble() * 0.06) + r.nextDouble() * 0.4;
            double rr = radius * (0.82 + r.nextDouble() * 0.3);
            double dx = Math.cos(a) * rr, dz = Math.sin(a) * rr;
            if (hidden(dx, dz, toCam)) continue;
            float tw = 0.45f + 0.55f * (float) (0.5 + 0.5 * Math.sin(t * (1.5 + r.nextDouble() * 2) + i * 2.3));
            dot(g, vp, cp, feet.add(dx, 0.04 + Math.sin(t * 1.3 + i) * 0.015, dz), w, h, rgb, strength * tw, 1.0f);
        }
        // braises qui s'élèvent : chacune naît au sol, monte d'environ 0,8 bloc et s'éteint
        int m = 14;
        for (int i = 0; i < m; i++) {
            double period = 2.6 + r.nextDouble() * 1.6;
            double ph = ((t + r.nextDouble() * period) % period) / period;          // 0 → 1
            double a = r.nextDouble() * Math.PI * 2 + ph * 0.6;
            double rr = radius * (0.5 + r.nextDouble() * 0.6) * (1 - ph * 0.35);
            double dx = Math.cos(a) * rr, dz = Math.sin(a) * rr;
            if (hidden(dx, dz, toCam) && ph < 0.55) continue;
            float life = (float) (ph < 0.15 ? ph / 0.15 : 1 - (ph - 0.15) / 0.85);
            dot(g, vp, cp, feet.add(dx + Math.sin(t * 2 + i) * 0.04, 0.05 + ph * 0.85, dz), w, h, rgb,
                    strength * life * 0.9f, 0.8f);
        }
        // lueur très diffuse au sol (quelques grands points pâles)
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4 + t * 0.05;
            double dx = Math.cos(a) * radius * 0.45, dz = Math.sin(a) * radius * 0.45;
            if (hidden(dx, dz, toCam)) continue;
            dot(g, vp, cp, feet.add(dx, 0.02, dz), w, h, rgb, strength * 0.22f, 2.6f);
        }
    }

    /** Vrai si le point (au sol, relatif aux pieds) est derrière les jambes vu de la caméra. */
    private static boolean hidden(double dx, double dz, Vec3 toCam) {
        boolean back = dx * toCam.x + dz * toCam.z < 0;
        double side = Math.abs(dx * toCam.z - dz * toCam.x);
        return back && side < 0.3;
    }

    /** Point lumineux doux : halo pâle + cœur clair, taille selon la profondeur. */
    private static void dot(GuiGraphicsExtractor g, Matrix4f vp, Vec3 cp, Vec3 p, int w, int h, int rgb, float alpha,
                            float size) {
        float[] s = project(vp, cp, p, w, h);
        if (s == null || alpha <= 0.02f) return;
        float k = Math.max(0.6f, Math.min(2.4f, 4.5f / s[2])) * size;
        int x = Math.round(s[0]), y = Math.round(s[1]);
        int halo = Math.max(1, Math.round(2.2f * k)), core = Math.max(1, Math.round(0.7f * k));
        int ha = Math.round(46 * Math.min(1f, alpha));
        g.fill(x - halo, y - halo / 2, x + halo + 1, y + halo / 2 + 1, ha << 24 | rgb);
        g.fill(x - halo / 2, y - halo, x + halo / 2 + 1, y + halo + 1, ha << 24 | rgb);
        int ca = Math.round(230 * Math.min(1f, alpha));
        int light = lighten(rgb);
        g.fill(x - core / 2, y - core / 2, x - core / 2 + core, y - core / 2 + core, ca << 24 | light);
    }

    private static int lighten(int rgb) {
        int r = (rgb >> 16) & 0xFF, gg = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        r = r + (255 - r) / 2; gg = gg + (255 - gg) / 2; b = b + (255 - b) / 2;
        return r << 16 | gg << 8 | b;
    }

    /** Lucioles : petits points dorés qui dérivent et scintillent dans la moitié gauche de l'écran. */
    public static void fireflies(GuiGraphicsExtractor g, int w, int h, float alpha, int count) {
        double t = System.currentTimeMillis() / 1000.0;
        java.util.Random r = new java.util.Random(9);
        for (int i = 0; i < count; i++) {
            double bx = r.nextDouble() * w * 0.6, by = h * 0.25 + r.nextDouble() * h * 0.65;
            double x = bx + Math.sin(t * (0.2 + r.nextDouble() * 0.3) + i) * 18;
            double y = by + Math.cos(t * (0.15 + r.nextDouble() * 0.25) + i * 1.7) * 12 - (t * 4 + i * 37) % 40;
            float glow = (float) (0.5 + 0.5 * Math.sin(t * (1.2 + r.nextDouble()) + i * 2.1));
            int a1 = Math.round(50 * glow * alpha), a2 = Math.round((120 + 135 * glow) * alpha);
            int xi = (int) x, yi = (int) y;
            g.fill(xi - 2, yi - 2, xi + 3, yi + 3, a1 << 24 | 0xF2C870);
            g.fill(xi, yi, xi + 1, yi + 1, a2 << 24 | 0xFFE6A0);
        }
    }
}
