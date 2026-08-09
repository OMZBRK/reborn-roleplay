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
 * Bulle de parole subtile au-dessus de la tête des joueurs qui parlent (voix
 * PlasmoVoice → {@link VoiceState}). Rendu en <b>2D HUD</b> : on projette la
 * position de la tête à l'écran via la matrice caméra, puis on dessine une
 * petite bulle « … » (indicateur de parole animé). Aucun rendu monde 3D → pas de
 * risque de crash sur l'API de rendu ; tout est défensif.
 */
public final class SpeechBubbles {

    private SpeechBubbles() {}

    private static final int BUBBLE_BG = 0xB0140D0A;   // fond sombre translucide
    private static final int DOT_ON    = 0xFFEDEDED;   // point actif
    private static final int DOT_OFF   = 0x66C8C8C8;   // point éteint
    private static final double MAX_DIST = 48.0;

    public static void render(GuiGraphicsExtractor ctx) {
        if (!VoiceState.available()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.options == null || mc.options.hideGui) return;

        Camera cam = mc.gameRenderer.getMainCamera();
        if (cam == null || !cam.isInitialized()) return;
        Vec3 camPos = cam.position();
        Matrix4f vp = cam.getViewRotationProjectionMatrix(new Matrix4f());
        int gw = mc.getWindow().getGuiScaledWidth();
        int gh = mc.getWindow().getGuiScaledHeight();
        int activeDot = (int) ((System.currentTimeMillis() / 350L) % 3L);

        // Les autres joueurs qui parlent (proximity).
        for (int id : VoiceState.speakingEntityIds()) {
            Entity e = mc.level.getEntity(id);
            if (e == null || e == mc.player) continue;
            drawFor(ctx, e, camPos, vp, gw, gh, activeDot);
        }
        // Soi-même quand on parle — indicateur visible en solo. Seulement en vue
        // 3e personne (en 1re personne la tête est sur la caméra → pas d'ancrage).
        if (VoiceState.selfSpeaking() && !mc.options.getCameraType().isFirstPerson()) {
            drawFor(ctx, mc.player, camPos, vp, gw, gh, activeDot);
        }
    }

    /** Projette la tête de {@code e} à l'écran et dessine la bulle si visible. */
    private static void drawFor(GuiGraphicsExtractor ctx, Entity e, Vec3 camPos,
                                Matrix4f vp, int gw, int gh, int activeDot) {
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
        drawBubble(ctx, sx, sy, activeDot, dist);
    }

    private static void drawBubble(GuiGraphicsExtractor ctx, int cx, int cy, int activeDot, double dist) {
        int w = 20, h = 12;
        int x = cx - w / 2, y = cy - h - 4;
        int bg = fade(BUBBLE_BG, dist);
        DrawHelpers.roundedRectFull(ctx, x, y, w, h, 4, bg);
        // Petite pointe sous la bulle.
        ctx.fill(cx - 1, y + h, cx + 2, y + h + 2, bg);
        // 3 points, celui « actif » plus clair → indicateur de parole animé.
        int dotY = y + h / 2 - 1;
        for (int i = 0; i < 3; i++) {
            int dx = x + 5 + i * 5;
            ctx.fill(dx, dotY, dx + 2, dotY + 2, fade(i == activeDot ? DOT_ON : DOT_OFF, dist));
        }
    }

    /** Atténue l'alpha avec la distance (bulle plus discrète de loin). */
    private static int fade(int argb, double dist) {
        double f = Math.max(0.35, 1.0 - dist / 60.0);
        int a = (int) (((argb >>> 24) & 0xFF) * f);
        return (a << 24) | (argb & 0xFFFFFF);
    }
}
