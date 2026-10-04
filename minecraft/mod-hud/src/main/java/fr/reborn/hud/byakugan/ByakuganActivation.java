package fr.reborn.hud.byakugan;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Effet d'activation du Byakugan (0,6 s) : flash blanc qui s'éteint et lignes radiales qui convergent vers le centre,
 * comme le « zoom » de l'anime. Ensuite, plus rien à l'écran : seul le négatif reste.
 */
public final class ByakuganActivation {

    private static final float DUREE = 0.6f;
    private static final int LIGNES = 56;

    private ByakuganActivation() {}

    public static void render(GuiGraphicsExtractor ctx) {
        float t = ByakuganClient.depuisActivation();
        if (t >= DUREE) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        float p = t / DUREE;

        // flash
        int flash = Math.round(230 * Math.max(0f, 1f - t / 0.22f));
        if (flash > 0) ctx.fill(0, 0, w, h, (flash << 24) | 0xF4F8FF);

        // lignes radiales qui partent des bords et filent vers le centre
        float cx = w / 2f, cy = h / 2f;
        float diag = (float) Math.hypot(cx, cy);
        int alpha = Math.round(200 * (1f - p));
        if (alpha <= 0) return;
        long graine = 0x5DEECE66DL;
        for (int i = 0; i < LIGNES; i++) {
            graine = graine * 6364136223846793005L + 1442695040888963407L;
            float jitter = ((graine >>> 33) % 1000) / 1000f;
            float ang = (float) (i * Math.PI * 2 / LIGNES + jitter * 0.09);
            float r1 = diag * (0.55f - 0.35f * p) * (0.85f + 0.3f * jitter);
            float longueur = diag - r1;
            float epaisseur = 1f + 1.5f * jitter;
            ctx.pose().pushMatrix();
            ctx.pose().translate(cx, cy);
            ctx.pose().rotate(ang);
            ctx.fill(Math.round(r1), Math.round(-epaisseur / 2), Math.round(r1 + longueur), Math.max(1, Math.round(epaisseur / 2)),
                    (alpha << 24) | 0xE6EEFF);
            ctx.pose().popMatrix();
        }
    }
}
