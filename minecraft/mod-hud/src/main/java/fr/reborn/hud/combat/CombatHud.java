package fr.reborn.hud.combat;

import fr.reborn.hud.menu.RebornFont;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

import java.util.List;

/**
 * HUD combat taïjutsu (rendu 2D par-dessus le HUD), alimenté par {@link CombatState} :
 * <ul>
 *   <li><b>Damage indicators</b> : nombres chunky projetés au-dessus des cibles,
 *       pop-scale + montée + dispersion, contour sombre, or (style
 *       <i>immersive-damage-indicators</i> / réf {@code stylevpvp.png}).</li>
 *   <li><b>Réticule de combat</b> : n'apparaît qu'en combat — anneau + 4 ticks +
 *       point central ; l'anneau EST la jauge de stamina (vert→rouge).</li>
 *   <li><b>Total de combo</b> près du réticule.</li>
 * </ul>
 * Projection monde→écran reprise du pattern {@code SpeechBubbles} (aucun rendu 3D).
 */
public final class CombatHud {

    private CombatHud() {}

    // Petit anneau discret AUTOUR du viseur Reborn (le vrai curseur reste géré par
    // CrosshairManager / l'éditeur de viseur). Volontairement fin et compact.
    private static final int RING_RADIUS = 7;
    private static final int RING_THICK = 1;

    // Instances scratch réutilisées par frame pour la projection monde→écran :
    // évite une alloc Matrix4f + un Vector4f par indicateur/frame. ThreadLocal par sûreté.
    private static final ThreadLocal<Matrix4f> VP = ThreadLocal.withInitial(Matrix4f::new);
    private static final ThreadLocal<Vector4f> CLIP = ThreadLocal.withInitial(Vector4f::new);

    /** LUT d'angles par géométrie (rOut,rIn) pour l'anneau : atan2 + test de bande
     *  constants à géométrie fixe (NaN = hors bande) → 1 calcul/géométrie, pas par frame. */
    private static final java.util.Map<Long, float[]> RING_ANGLE = new java.util.concurrent.ConcurrentHashMap<>();

    public static void render(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options == null || mc.gui.hud.isHidden()) return;
        if (mc.gui.screen() != null) return;

        Font font = mc.font;
        int gw = mc.getWindow().getGuiScaledWidth();
        int gh = mc.getWindow().getGuiScaledHeight();
        int cx = gw / 2, cy = gh / 2;
        long now = System.currentTimeMillis();
        CombatState st = CombatState.INSTANCE;

        // ── Damage indicators (projetés au-dessus des cibles) ──
        Camera cam = mc.gameRenderer.mainCamera();
        if (cam != null && cam.isInitialized()) {
            Vec3 camPos = cam.position();
            Matrix4f vp = cam.getViewRotationProjectionMatrix(VP.get());
            List<CombatState.DamageIndicator> live = st.liveIndicators(now);
            for (CombatState.DamageIndicator ind : live) {
                Entity e = mc.level.getEntity(ind.entityId);
                if (e == null) continue;
                float age = (now - ind.spawnMs) / (float) CombatState.DMG_LIFE_MS; // 0..1
                Vec3 head = e.getEyePosition(1f).add(0.0, 0.7, 0.0);
                Vector4f clip = vp.transform(CLIP.get().set(
                    (float) (head.x - camPos.x),
                    (float) (head.y - camPos.y),
                    (float) (head.z - camPos.z), 1f));
                if (clip.w() <= 0.05f) continue;
                float ndcX = clip.x() / clip.w();
                float ndcY = clip.y() / clip.w();
                if (ndcX < -1.15f || ndcX > 1.15f || ndcY < -1.15f || ndcY > 1.15f) continue;
                int sx = Math.round((ndcX * 0.5f + 0.5f) * gw + ind.dx);
                float rise = (1f - (1f - age) * (1f - age)) * 20f;           // ease-out
                int sy = Math.round((1f - (ndcY * 0.5f + 0.5f)) * gh + ind.dy - rise);
                float pop = age < 0.12f ? 1f + (1f - age / 0.12f) * (1f - age / 0.12f) * 0.55f : 1f;
                float alpha = age < 0.7f ? 1f : Math.max(0f, 1f - (age - 0.7f) / 0.3f);
                drawDamage(ctx, font, sx, sy, (int) Math.round(ind.amount), pop, alpha);
            }
        }

        // ── Réticule de combat + stamina (seulement en combat) ──
        float rA = st.combatModeAlpha(now);
        if (rA > 0.01f) {
            drawReticle(ctx, cx, cy, st.staminaFraction(), rA);

            float comboA = st.comboAlpha(now) * rA;
            if (comboA > 0.01f) {
                Component c = RebornFont.bold(String.valueOf((int) Math.round(st.comboTotal())));
                int a = Math.round(comboA * 255f);
                ctx.pose().pushMatrix();
                ctx.pose().translate(cx + RING_RADIUS + 10, cy - 5);
                ctx.pose().scale(1.25f, 1.25f);
                ctx.text(font, c, 1, 1, (Math.round(comboA * 190f) << 24), false);
                ctx.text(font, c, 0, 0, (a << 24) | 0x00FFFFFF, false);
                ctx.pose().popMatrix();
            }
        }

        // ── Flash de parade timée (deflect) ──
        float pf = st.parryFlashAlpha(now);
        if (pf > 0.01f) {
            if (st.parryRole() == 0) {
                // Deflect réussi : anneau blanc-cyan qui s'étend en s'estompant.
                int r = RING_RADIUS + 3 + Math.round((1f - pf) * 12f);
                ringBand(ctx, cx, cy, r, 2, 0f, 360f, applyAlpha(0xFFCFEFFF, pf));
            } else {
                // Fait parer (ouverture subie) : voile rouge léger plein écran.
                ctx.fill(0, 0, gw, gh, applyAlpha(0x40FF3020, pf * 0.8f));
            }
        }

        // La barre d'endurance est un ÉLÉMENT HUD DÉPLAÇABLE (cf. renderEnduranceBar
        // + registre "combat-endurance" dans RebornHudClient) → plus rendue ici au centre.
    }

    private static final int BAR_W = 90, BAR_H = 5;

    /**
     * Barre d'endurance de combat rendue à une position HUD (élément déplaçable via
     * l'éditeur). Visible seulement en combat ou pendant la garde. {@code (x,y)} =
     * coin haut-gauche ; {@code scale} depuis l'état HUD.
     */
    public static void renderEnduranceBar(GuiGraphicsExtractor ctx, int x, int y, float scale) {
        CombatState st = CombatState.INSTANCE;
        boolean blocking = CombatInput.INSTANCE.isBlocking();
        float barA = Math.max(st.combatModeAlpha(System.currentTimeMillis()), blocking ? 1f : 0f);
        if (barA <= 0.01f) return;
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        if (scale != 1f) ctx.pose().scale(scale, scale);
        drawEnduranceBar(ctx, 0, 0, st.staminaFraction(), barA, blocking);
        ctx.pose().popMatrix();
    }

    /** Dessine la barre depuis le coin haut-gauche {@code (x,y)} (bleutée en garde). */
    private static void drawEnduranceBar(GuiGraphicsExtractor ctx, int x, int y,
                                         float frac, float alpha, boolean blocking) {
        int bg = (Math.round(alpha * 150f) << 24);
        int border = blocking ? applyAlpha(0xFF7FB4FF, alpha)
                              : (Math.round(alpha * 90f) << 24) | 0x00FFFFFF;
        ctx.fill(x - 1, y - 1, x + BAR_W + 1, y + BAR_H + 1, border);
        ctx.fill(x, y, x + BAR_W, y + BAR_H, bg);
        int fw = Math.round(BAR_W * Math.max(0f, Math.min(1f, frac)));
        if (fw > 0) ctx.fill(x, y, x + fw, y + BAR_H, applyAlpha(staminaColor(frac), alpha));
    }

    /**
     * Petit anneau de stamina AUTOUR du viseur (pas un réticule complet) : on ne
     * dessine QUE la fine jauge, sans ticks ni point central — le viseur Reborn
     * ({@code CrosshairManager}, éditable) reste le curseur. Discret, en combat.
     */
    private static void drawReticle(GuiGraphicsExtractor ctx, int cx, int cy, float frac, float alpha) {
        // Anneau de fond très discret.
        ringBand(ctx, cx, cy, RING_RADIUS, RING_THICK, 0f, 360f, (Math.round(alpha * 90f) << 24));
        // Jauge de stamina (arc horaire depuis le haut).
        ringBand(ctx, cx, cy, RING_RADIUS, RING_THICK, 0f, 360f * frac, applyAlpha(staminaColor(frac), alpha));
    }

    /** Nombre de dégâts centré, or, contour sombre, pop-scale. */
    private static void drawDamage(GuiGraphicsExtractor ctx, Font font, int cx, int cy,
                                   int amount, float scale, float alpha) {
        if (alpha <= 0f) return;
        Component t = RebornFont.bold(String.valueOf(amount));
        int a = Math.round(alpha * 255f);
        int outline = (Math.round(alpha * 230f) << 24);        // presque noir
        int gold = (a << 24) | 0x00FFD24A;
        int w = font.width(t);
        ctx.pose().pushMatrix();
        ctx.pose().translate(cx - (w * scale) / 2f, cy);
        ctx.pose().scale(scale, scale);
        // Contour 4 directions puis remplissage.
        ctx.text(font, t, -1, 0, outline, false);
        ctx.text(font, t, 1, 0, outline, false);
        ctx.text(font, t, 0, -1, outline, false);
        ctx.text(font, t, 0, 1, outline, false);
        ctx.text(font, t, 0, 0, gold, false);
        ctx.pose().popMatrix();
    }

    private static int staminaColor(float frac) {
        int lowR = 0xE0, lowG = 0x3B, lowB = 0x30;   // rouge (vide)
        int hiR = 0x4A, hiG = 0xD2, hiB = 0x6A;      // vert (plein)
        int r = Math.round(lowR + (hiR - lowR) * frac);
        int g = Math.round(lowG + (hiG - lowG) * frac);
        int b = Math.round(lowB + (hiB - lowB) * frac);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static int applyAlpha(int argb, float alpha) {
        int a = Math.round(((argb >>> 24) & 0xFF) * alpha);
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /** Arc d'anneau [startDeg, startDeg+sweepDeg], horaire depuis le HAUT. */
    private static void ringBand(GuiGraphicsExtractor ctx, int cx, int cy, int radius,
                                 int thickness, float startDeg, float sweepDeg, int color) {
        if (sweepDeg <= 0f) return;
        int rOut = radius, rIn = Math.max(0, radius - thickness);
        float[] ang = ringAngleLut(rOut, rIn);   // angles précalculés (NaN hors bande)
        int span = 2 * rOut + 1;
        // Batch : une span horizontale par run contigu au lieu d'un fill 1×1/pixel.
        for (int dy = -rOut; dy <= rOut; dy++) {
            int base = (dy + rOut) * span;
            int runStart = Integer.MIN_VALUE;
            for (int dx = -rOut; dx <= rOut; dx++) {
                float a = ang[base + dx + rOut];
                boolean on;
                if (Float.isNaN(a)) {
                    on = false;
                } else {
                    float rel = a - startDeg;
                    if (rel < 0) rel += 360f;
                    on = rel <= sweepDeg;
                }
                if (on) {
                    if (runStart == Integer.MIN_VALUE) runStart = dx;
                } else if (runStart != Integer.MIN_VALUE) {
                    ctx.fill(cx + runStart, cy + dy, cx + dx, cy + dy + 1, color);
                    runStart = Integer.MIN_VALUE;
                }
            }
            if (runStart != Integer.MIN_VALUE) {
                ctx.fill(cx + runStart, cy + dy, cx + rOut + 1, cy + dy + 1, color);
            }
        }
    }

    /** Angles (deg, 0 en haut, horaire) de l'anneau [rIn,rOut] ; NaN hors bande. 1 calcul/géométrie. */
    private static float[] ringAngleLut(int rOut, int rIn) {
        long key = ((long) rOut << 32) | (rIn & 0xffffffffL);
        return RING_ANGLE.computeIfAbsent(key, k -> {
            int span = 2 * rOut + 1;
            float[] a = new float[span * span];
            int rOutSq = rOut * rOut, rInSq = rIn * rIn;
            for (int dy = -rOut; dy <= rOut; dy++) {
                for (int dx = -rOut; dx <= rOut; dx++) {
                    int idx = (dy + rOut) * span + (dx + rOut);
                    int d2 = dx * dx + dy * dy;
                    if (d2 > rOutSq || d2 < rInSq) {
                        a[idx] = Float.NaN;
                    } else {
                        float angle = (float) Math.toDegrees(Math.atan2(dx, -dy));
                        if (angle < 0) angle += 360f;
                        a[idx] = angle;
                    }
                }
            }
            return a;
        });
    }
}
