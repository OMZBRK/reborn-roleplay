package fr.reborn.hud.combat;

import fr.reborn.hud.menu.Colors;
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
 * HUD combat taïjutsu (rendu 2D par-dessus le HUD) — alimenté par {@link CombatState} :
 * <ul>
 *   <li><b>Anneau de stamina</b> autour du curseur (centre écran), fondu vert→rouge,
 *       masqué quand plein et inactif.</li>
 *   <li><b>Damage indicators</b> : nombres flottants projetés au-dessus des cibles,
 *       qui montent et s'effacent.</li>
 *   <li><b>Total de combo</b> près du curseur (cumul de session), fondu après ~2,5 s.</li>
 * </ul>
 * Projection monde→écran reprise du pattern {@code SpeechBubbles} (aucun rendu 3D).
 */
public final class CombatHud {

    private CombatHud() {}

    // Anneau stamina.
    private static final int RING_RADIUS = 9;
    private static final int RING_THICK = 2;
    private static final int RING_BG = 0x66101010;

    public static void render(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options == null || mc.options.hideGui) return;
        if (mc.screen != null) return;

        Font font = mc.font;
        int gw = mc.getWindow().getGuiScaledWidth();
        int gh = mc.getWindow().getGuiScaledHeight();
        int cx = gw / 2, cy = gh / 2;
        long now = System.currentTimeMillis();
        CombatState st = CombatState.INSTANCE;

        // ── Damage indicators (projetés au-dessus des cibles) ──
        Camera cam = mc.gameRenderer.getMainCamera();
        if (cam != null && cam.isInitialized()) {
            Vec3 camPos = cam.position();
            Matrix4f vp = cam.getViewRotationProjectionMatrix(new Matrix4f());
            List<CombatState.DamageIndicator> live = st.liveIndicators(now);
            for (CombatState.DamageIndicator ind : live) {
                Entity e = mc.level.getEntity(ind.entityId);
                if (e == null) continue;
                float age = (now - ind.spawnMs) / (float) CombatState.DMG_LIFE_MS; // 0..1
                Vec3 head = e.getEyePosition(1f).add(0.0, 0.75, 0.0);
                Vector4f clip = vp.transform(new Vector4f(
                    (float) (head.x - camPos.x),
                    (float) (head.y - camPos.y),
                    (float) (head.z - camPos.z), 1f));
                if (clip.w() <= 0.05f) continue;
                float ndcX = clip.x() / clip.w();
                float ndcY = clip.y() / clip.w();
                if (ndcX < -1.1f || ndcX > 1.1f || ndcY < -1.1f || ndcY > 1.1f) continue;
                int sx = Math.round((ndcX * 0.5f + 0.5f) * gw);
                int sy = Math.round((1f - (ndcY * 0.5f + 0.5f)) * gh) - Math.round(age * 16f);
                float alpha = age < 0.6f ? 1f : Math.max(0f, 1f - (age - 0.6f) / 0.4f);
                drawDamage(ctx, font, sx, sy, (int) Math.round(ind.amount), alpha);
            }
        }

        // ── Total de combo (près du curseur) ──
        float comboA = st.comboAlpha(now);
        if (comboA > 0.01f) {
            Component c = RebornFont.bold(String.valueOf((int) Math.round(st.comboTotal())));
            int a = Math.round(comboA * 255f) << 24;
            ctx.pose().pushMatrix();
            ctx.pose().translate(cx + RING_RADIUS + 8, cy - 4);
            ctx.pose().scale(1.3f, 1.3f);
            ctx.text(font, c, 1, 1, 0x00000000 | (Math.round(comboA * 180f) << 24), false); // ombre
            ctx.text(font, c, 0, 0, a | 0x00FFFFFF, false);
            ctx.pose().popMatrix();
        }

        // ── Anneau de stamina autour du curseur ──
        if (st.staminaVisible(now)) {
            float frac = st.staminaFraction();
            ringBand(ctx, cx, cy, RING_RADIUS, RING_THICK, 0f, 360f, RING_BG);
            int col = staminaColor(frac);
            ringBand(ctx, cx, cy, RING_RADIUS, RING_THICK, 0f, 360f * frac, col);
        }
    }

    /** Nombre de dégâts centré, jaune, avec ombre (style fighting-game). */
    private static void drawDamage(GuiGraphicsExtractor ctx, Font font, int cx, int cy, int amount, float alpha) {
        if (alpha <= 0f) return;
        Component t = RebornFont.bold(String.valueOf(amount));
        int a = Math.round(alpha * 255f);
        int shadow = (Math.round(alpha * 200f) << 24);
        int yellow = (a << 24) | 0x00FFD24A;
        float scale = 1.6f;
        int w = font.width(t);
        ctx.pose().pushMatrix();
        ctx.pose().translate(cx - (w * scale) / 2f, cy);
        ctx.pose().scale(scale, scale);
        ctx.text(font, t, 1, 1, shadow, false);
        ctx.text(font, t, 0, 0, yellow, false);
        ctx.pose().popMatrix();
    }

    /** Vert (plein) → orange → rouge (vide). */
    private static int staminaColor(float frac) {
        int lowR = 0xE0, lowG = 0x3B, lowB = 0x30;   // rouge
        int hiR = 0x4A, hiG = 0xD2, hiB = 0x6A;      // vert
        int r = Math.round(lowR + (hiR - lowR) * frac);
        int g = Math.round(lowG + (hiG - lowG) * frac);
        int b = Math.round(lowB + (hiB - lowB) * frac);
        return 0xF0000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * Arc d'anneau [startDeg, startDeg+sweepDeg], sens horaire depuis le HAUT.
     * (pixel-loop + atan2, comme {@code DrawHelpers.dashedRing}).
     */
    private static void ringBand(GuiGraphicsExtractor ctx, int cx, int cy, int radius,
                                 int thickness, float startDeg, float sweepDeg, int color) {
        int rOut = radius, rIn = Math.max(0, radius - thickness);
        int rOutSq = rOut * rOut, rInSq = rIn * rIn;
        for (int dy = -rOut; dy <= rOut; dy++) {
            for (int dx = -rOut; dx <= rOut; dx++) {
                int d2 = dx * dx + dy * dy;
                if (d2 > rOutSq || d2 < rInSq) continue;
                float ang = (float) Math.toDegrees(Math.atan2(dx, -dy)); // 0 en haut, horaire
                if (ang < 0) ang += 360f;
                float rel = ang - startDeg;
                if (rel < 0) rel += 360f;
                if (rel <= sweepDeg) {
                    ctx.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
                }
            }
        }
    }
}
