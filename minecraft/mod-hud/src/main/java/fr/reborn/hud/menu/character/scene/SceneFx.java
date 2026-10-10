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
 *   <li>un ensō de lumière dorée posé au sol sous un perso — projeté en perspective ; l'arc de derrière s'efface
 *       là où passent les jambes, l'arc de devant passe devant les pieds ;</li>
 *   <li>des lucioles dorées qui dérivent lentement dans la nuit.</li>
 * </ul>
 */
public final class SceneFx {

    private SceneFx() {}

    private static float[] project(Matrix4f vp, Vec3 cp, Vec3 p, int w, int h) {
        Vector4f c = vp.transform(new Vector4f((float) (p.x - cp.x), (float) (p.y - cp.y), (float) (p.z - cp.z), 1f));
        if (c.w() <= 0.05f) return null;
        return new float[]{(c.x() / c.w() * 0.5f + 0.5f) * w, (1f - (c.y() / c.w() * 0.5f + 0.5f)) * h, c.w()};
    }

    /** Ensō doré au sol ({@code strength} 0..1 : plein pour le perso choisi, discret pour les autres). */
    public static void ring(GuiGraphicsExtractor g, Vec3 feet, double radius, float strength, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        Camera cam = mc.gameRenderer.mainCamera();
        if (cam == null || !cam.isInitialized() || strength <= 0.01f) return;
        Matrix4f vp = cam.getViewRotationProjectionMatrix(new Matrix4f());
        Vec3 cp = cam.position();
        Vec3 toCam = new Vec3(cp.x - feet.x, 0, cp.z - feet.z).normalize();
        double spin = (System.currentTimeMillis() % 60000) / 60000.0 * Math.PI * 2 * 0.6;
        int n = 160;
        for (int layer = 0; layer < 3; layer++) {
            double r = radius * (1.0 + layer * 0.06);
            int alpha = Math.round((layer == 0 ? 210 : layer == 1 ? 70 : 30) * strength);
            for (int i = 0; i < n; i++) {
                double u = i / (double) n;
                if (u > 0.9) continue;                                   // l'ouverture de l'ensō
                double a = spin + u * Math.PI * 2;
                double dx = Math.cos(a) * r, dz = Math.sin(a) * r;
                boolean back = dx * toCam.x + dz * toCam.z < 0;
                double side = Math.abs(dx * toCam.z - dz * toCam.x);
                if (back && side < 0.32) continue;                      // derrière les jambes
                float[] s = project(vp, cp, feet.add(dx, 0.02, dz), w, h);
                if (s == null) continue;
                double taper = Math.sin(Math.PI * Math.min(1, u / 0.9));
                int px = Math.max(1, (int) Math.round((layer == 0 ? 2.2 : 3.5) * (0.4 + 0.6 * taper) * 4.0 / s[2]));
                int col = Math.round(alpha * (float) (0.35 + 0.65 * taper)) << 24 | (layer == 0 ? 0xF2C878 : 0xD9A95E);
                int x = Math.round(s[0]), y = Math.round(s[1]);
                g.fill(x - px / 2, y - Math.max(1, px / 3), x + px / 2 + 1, y + Math.max(1, px / 3) + 1, col);
            }
        }
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
