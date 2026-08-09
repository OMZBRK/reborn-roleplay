package fr.reborn.hud.voice;

import fr.reborn.hud.menu.DrawHelpers;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * Bulles au-dessus de la tête des joueurs, rendu <b>2D HUD</b> (projection de la
 * tête à l'écran via la matrice caméra, aucun rendu monde 3D → défensif) :
 * <ul>
 *   <li><b>Voix</b> (PlasmoVoice → {@link VoiceState}) : <b>1 point</b> pulsant
 *       quand un joueur parle (proximity) + sur soi en solo (3e pers).</li>
 *   <li><b>Frappe chat</b> ({@link fr.reborn.hud.chat.TypingState}) : <b>3 points</b>
 *       animés quand un joueur écrit dans le chat.</li>
 * </ul>
 */
public final class SpeechBubbles {

    private SpeechBubbles() {}

    private static final int BUBBLE_BG = 0xB0140D0A;   // fond sombre translucide
    private static final int DOT_ON    = 0xFFEDEDED;   // point (couleur de base)
    private static final double MAX_DIST = 48.0;

    public static void render(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options == null || mc.options.hideGui) return;

        Camera cam = mc.gameRenderer.getMainCamera();
        if (cam == null || !cam.isInitialized()) return;
        Vec3 camPos = cam.position();
        Matrix4f vp = cam.getViewRotationProjectionMatrix(new Matrix4f());
        int gw = mc.getWindow().getGuiScaledWidth();
        int gh = mc.getWindow().getGuiScaledHeight();
        long now = System.currentTimeMillis();

        // Voix (1 point) — si PlasmoVoice présent.
        if (VoiceState.available()) {
            for (int id : VoiceState.speakingEntityIds()) {
                Entity e = mc.level.getEntity(id);
                if (e == null || e == mc.player) continue;
                drawFor(ctx, e, camPos, vp, gw, gh, now, 1);
            }
            if (VoiceState.selfSpeaking() && !mc.options.getCameraType().isFirstPerson()) {
                drawFor(ctx, mc.player, camPos, vp, gw, gh, now, 1);
            }
        }
        // Frappe chat (3 points) — proximity, poussé par ShinobiCore. Affiché AUSSI
        // au-dessus de soi (sauf en 1re personne où la tête est sur la caméra).
        boolean firstPerson = mc.options.getCameraType().isFirstPerson();
        for (int id : fr.reborn.hud.chat.TypingState.typingEntityIds()) {
            Entity e = mc.level.getEntity(id);
            if (e == null) continue;
            if (e == mc.player && firstPerson) continue;
            drawFor(ctx, e, camPos, vp, gw, gh, now, 3);
        }
    }

    /** Projette la tête de {@code e} à l'écran et dessine la bulle si visible. */
    private static void drawFor(GuiGraphicsExtractor ctx, Entity e, Vec3 camPos,
                                Matrix4f vp, int gw, int gh, long now, int dots) {
        double dist = e.position().distanceTo(camPos);
        if (dist > MAX_DIST) return;
        Vec3 head = e.getEyePosition(1f).add(0.0, 0.55, 0.0);
        Vector4f clip = vp.transform(new Vector4f(
            (float) (head.x - camPos.x),
            (float) (head.y - camPos.y),
            (float) (head.z - camPos.z), 1f));
        if (clip.w() <= 0.05f) return;                      // derrière la caméra
        float ndcX = clip.x() / clip.w();
        float ndcY = clip.y() / clip.w();
        if (ndcX < -1.1f || ndcX > 1.1f || ndcY < -1.1f || ndcY > 1.1f) return;
        int sx = Math.round((ndcX * 0.5f + 0.5f) * gw);
        int sy = Math.round((1f - (ndcY * 0.5f + 0.5f)) * gh);
        drawBubble(ctx, sx, sy, now, dist, dots);
    }

    private static void drawBubble(GuiGraphicsExtractor ctx, int cx, int cy, long now, double dist, int dots) {
        int w = dots >= 3 ? 20 : 11, h = 10;
        int x = cx - w / 2, y = cy - h - 4;
        int bg = fade(BUBBLE_BG, dist);
        DrawHelpers.roundedRectFull(ctx, x, y, w, h, 4, bg);
        ctx.fill(cx - 1, y + h, cx + 2, y + h + 2, bg); // pointe
        int dotY = y + h / 2;
        if (dots >= 3) {
            // 3 points animés en séquence (indicateur de frappe « … »).
            int active = (int) ((now / 300L) % 3L);
            for (int i = 0; i < 3; i++) {
                int dx = x + 5 + i * 5;
                int base = DOT_ON & 0x00FFFFFF;
                int col = fade(i == active ? (0xFF000000 | base) : (0x66000000 | base), dist);
                ctx.fill(dx, dotY - 1, dx + 2, dotY + 1, col);
            }
        } else {
            // 1 point pulsant (indicateur de parole).
            float pulse = 0.55f + 0.45f * (float) Math.sin(now / 260.0);
            int a = Math.round(0xEE * pulse);
            int dot = fade((a << 24) | (DOT_ON & 0x00FFFFFF), dist);
            ctx.fill(cx - 1, dotY - 1, cx + 1, dotY + 1, dot);
        }
    }

    /** Atténue l'alpha avec la distance (bulle plus discrète de loin). */
    private static int fade(int argb, double dist) {
        double f = Math.max(0.35, 1.0 - dist / 60.0);
        int a = (int) (((argb >>> 24) & 0xFF) * f);
        return (a << 24) | (argb & 0xFFFFFF);
    }
}
