package fr.reborn.hud.menu.widget;

import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.menu.RebornVersion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Écran d'accroche (SPLASH) — façon Paladium Reforged.
 *
 * <p>Ne dessine QUE le premier plan (logo + prompt + crédits) sur un léger
 * voile sombre. Le fond MCEF et son <b>flou gaussien</b> sont gérés en amont
 * par {@code TitleScreenMixin} (qui a accès au {@code Screen} pour appeler
 * {@code applyBlur}). N'importe quelle touche ou clic bascule vers
 * {@link fr.reborn.hud.menu.MainMenuFlow.Stage#MENU}.
 */
public final class SplashOverlay {

    /** Logo Reborn Roleplay (render 3D blocky) — 2048×717, fond transparent. */
    private static final Identifier LOGO = Identifier.fromNamespaceAndPath("reborn", "textures/gui/title/logo.png");
    private static final int LOGO_TEX_W = 2048;
    private static final int LOGO_TEX_H = 717;

    /** Prompt en police pixel ArcadePix (dessiné lettre par lettre pour
     *  resserrer l'espacement — MC ne gère pas le letter-spacing nativement). */
    private static final String PROMPT_STR = "APPUIE SUR UNE TOUCHE";

    /** Crédits bas — ArcadePix, textes partagés avec le menu. */
    private static final Component CREDIT_1 = RebornFont.arcade(RebornVersion.SPLASH_CREDIT_1);
    private static final Component CREDIT_2 = RebornFont.arcade(RebornVersion.SPLASH_CREDIT_2);

    private static final float PROMPT_SCALE = 1.8f;
    /** Tracking négatif (px non-scalés retirés entre chaque lettre du prompt). */
    private static final float PROMPT_TRACKING = 1.5f;
    private static final float CREDIT_SCALE = 0.8f;

    private SplashOverlay() {}

    public static void render(GuiGraphicsExtractor ctx, int screenW, int screenH) {
        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        Font tr = client.font;

        // Léger voile sombre par-dessus le fond flouté — donne du contraste
        // au logo/texte sans habiller l'écran (le flou fait le reste).
        ctx.fill(0, 0, screenW, screenH, 0x40000000);

        // ── Logo central (texture PNG, ratio conservé) ──────────
        int destW = Math.min(Math.round(screenW * 0.42f), 540);
        int destH = Math.round(destW * (float) LOGO_TEX_H / LOGO_TEX_W);
        int logoX = (screenW - destW) / 2;
        int logoY = Math.round(screenH * 0.36f) - destH / 2;
        ctx.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, LOGO, logoX, logoY, 0f, 0f, destW, destH, LOGO_TEX_W, LOGO_TEX_H);

        // ── Prompt gras clignotant (~72% hauteur) ───────────────
        float t = (System.currentTimeMillis() % 1400L) / 1400f;
        float wave = 0.5f + 0.5f * (float) Math.sin(t * Math.PI * 2.0);
        int alpha = Math.round((0.3f + 0.7f * wave) * 255f);
        int color = (alpha << 24) | 0x00FFFFFF;

        int promptY = Math.round(screenH * 0.72f);
        drawTrackedCentered(ctx, tr, PROMPT_STR, PROMPT_SCALE, PROMPT_TRACKING,
            screenW, promptY, color);

        // ── Crédits pixel centrés en bas (2 lignes) ─────────────
        drawCentered(ctx, tr, CREDIT_1, screenW, screenH - 20, 0xFFE5E7EB);
        drawCentered(ctx, tr, CREDIT_2, screenW, screenH - 11, 0xFFB6BAC2);
    }

    /**
     * Dessine {@code text} centré en resserrant l'espacement des lettres :
     * chaque glyphe est posé un par un et on retire {@code tracking} px
     * (non-scalés) à l'avance de chaque lettre. Ombre incluse.
     */
    private static void drawTrackedCentered(GuiGraphicsExtractor ctx, Font tr, String text,
                                            float scale, float tracking,
                                            int screenW, int y, int color) {
        float total = 0f;
        for (int i = 0; i < text.length(); i++) {
            total += tr.width(RebornFont.arcade(String.valueOf(text.charAt(i))));
            if (i < text.length() - 1) total -= tracking;
        }
        float startX = (screenW - total * scale) / 2f;
        ctx.pose().pushMatrix();
        ctx.pose().translate(startX, y);
        ctx.pose().scale(scale, scale);
        float cx = 0f;
        for (int i = 0; i < text.length(); i++) {
            Component ch = RebornFont.arcade(String.valueOf(text.charAt(i)));
            int ix = Math.round(cx);
            ctx.text(tr, ch, ix + 1, 1, 0x99000000, false);
            ctx.text(tr, ch, ix, 0, color, false);
            cx += tr.width(ch) - tracking;
        }
        ctx.pose().popMatrix();
    }

    private static void drawCentered(GuiGraphicsExtractor ctx, Font tr, Component text,
                                     int screenW, int y, int color) {
        int w = Math.round(tr.width(text) * CREDIT_SCALE);
        int x = (screenW - w) / 2;
        ctx.pose().pushMatrix();
        ctx.pose().translate(x, y);
        ctx.pose().scale(CREDIT_SCALE, CREDIT_SCALE);
        ctx.text(tr, text, 0, 0, color, true);
        ctx.pose().popMatrix();
    }
}
