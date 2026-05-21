package fr.reborn.integrity.ui.connect;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.reborn.integrity.ui.Colors;
import fr.reborn.integrity.ui.DrawHelpers;
import fr.reborn.integrity.ui.RebornFont;
import fr.reborn.integrity.ui.SakuraParticles;
import fr.reborn.integrity.ui.menu.BackgroundRenderer;
import fr.reborn.integrity.ui.menu.MainMenuRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Rendu de l'écran de connexion Reborn — appelé par {@code
 * ConnectScreenMixin} en lieu et place du rendu vanilla.
 *
 * <p>Composition :
 * <ul>
 *   <li>BackgroundRenderer (3-band blue gradient)</li>
 *   <li>SakuraParticles overlay</li>
 *   <li>Sigil logo centré, taille moyenne</li>
 *   <li>Titre "CONNEXION AU SERVEUR REBORN" en Bebas Neue</li>
 *   <li>Sous-titre = status vanilla (ex: "Connexion en cours...",
 *       "Authentification...", "Téléchargement du terrain...")</li>
 *   <li>Spinner ring animé (12 segments tournants)</li>
 *   <li>Trois dots pulsants sous le status</li>
 * </ul>
 *
 * <p>Le bouton "Annuler" vanilla n'est pas re-rendu ici — le mixin laisse
 * l'iteration {@code children()} de Screen le faire après notre render.
 */
public final class ConnectingRenderer {

    private static final Identifier SIGIL =
        Identifier.of("reborn", "textures/gui/logo_sigil.png");

    private static final int SIGIL_NATIVE = 256;
    private static final int SIGIL_DISPLAY_BASE = 96;

    private static final long BORN_AT = System.currentTimeMillis();

    private ConnectingRenderer() {}

    public static void render(DrawContext ctx, int screenW, int screenH, Text status) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        TextRenderer tr = client.textRenderer;

        float responsive = MainMenuRenderer.responsiveScale(screenW);
        float t = (System.currentTimeMillis() - BORN_AT) / 1000f;

        // 1. Background bleu nuit.
        BackgroundRenderer.render(ctx, screenW, screenH);

        // 2. Sakura overlay.
        SakuraParticles.INSTANCE.render(ctx, screenW, screenH);

        // 3. Sigil + halo doux pulsant.
        int sigilDisplay = Math.round(SIGIL_DISPLAY_BASE * responsive);
        int sigilCx = screenW / 2;
        int sigilCy = Math.round(screenH * 0.34f);
        int sigilX = sigilCx - sigilDisplay / 2;
        int sigilY = sigilCy - sigilDisplay / 2;

        // Halo derrière le sigil (pulse léger).
        float haloAlpha = 0.35f + 0.15f * (float) Math.sin(t * 1.4f);
        int haloRadius = Math.round(sigilDisplay * (0.62f + 0.04f * (float) Math.sin(t * 1.4f)));
        int haloColor = (Math.round(haloAlpha * 255) << 24) | (Colors.ACCENT & 0x00FFFFFF);
        DrawHelpers.disc(ctx, sigilCx, sigilCy, haloRadius, haloColor & 0x33FFFFFF);

        ctx.getMatrices().push();
        ctx.getMatrices().translate(sigilX, sigilY, 0);
        float sigilScale = (float) sigilDisplay / SIGIL_NATIVE;
        ctx.getMatrices().scale(sigilScale, sigilScale, 1f);
        RenderSystem.enableBlend();
        ctx.drawTexture(SIGIL, 0, 0, 0f, 0f, SIGIL_NATIVE, SIGIL_NATIVE, SIGIL_NATIVE, SIGIL_NATIVE);
        ctx.getMatrices().pop();

        // 4. Spinner ring autour du sigil — 12 segments dont 3 illuminés
        //    qui tournent. Rendu via 12 mini-discs positionnés sur un cercle.
        int spinnerRadius = sigilDisplay / 2 + Math.round(22 * responsive);
        int segCount = 12;
        int activeIdx = (int) ((t * 4f) % segCount); // 4 rotations/sec
        for (int i = 0; i < segCount; i++) {
            double angle = (Math.PI * 2.0 * i) / segCount - Math.PI / 2;
            int dotCx = sigilCx + (int) Math.round(Math.cos(angle) * spinnerRadius);
            int dotCy = sigilCy + (int) Math.round(Math.sin(angle) * spinnerRadius);
            int dist = (i - activeIdx + segCount) % segCount;
            int color;
            if (dist == 0)      color = Colors.WHITE_PURE;
            else if (dist == 1) color = Colors.ACCENT_HOVER;
            else if (dist == 2) color = Colors.ACCENT;
            else                color = 0x33FFFFFF;
            DrawHelpers.disc(ctx, dotCx, dotCy, 2, color);
        }

        // 5. Titre "CONNEXION AU SERVEUR REBORN".
        Text title = RebornFont.display("CONNEXION AU SERVEUR REBORN");
        float titleScale = 1.6f * responsive;
        int titleW = Math.round(tr.getWidth(title) * titleScale);
        int titleX = (screenW - titleW) / 2;
        int titleY = sigilCy + sigilDisplay / 2 + Math.round(48 * responsive);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(titleX, titleY, 0);
        ctx.getMatrices().scale(titleScale, titleScale, 1f);
        ctx.drawText(tr, title, 0, 0, Colors.WHITE_PURE, false);
        ctx.getMatrices().pop();

        // 6. Status vanilla (ex: "Connexion en cours...") sous le titre.
        if (status != null) {
            float statusScale = 1.0f * responsive;
            int statusW = Math.round(tr.getWidth(status) * statusScale);
            int statusX = (screenW - statusW) / 2;
            int statusY = titleY + Math.round(36 * responsive);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(statusX, statusY, 0);
            ctx.getMatrices().scale(statusScale, statusScale, 1f);
            ctx.drawText(tr, status, 0, 0, Colors.FOREGROUND_SUBTLE, false);
            ctx.getMatrices().pop();
        }

        // 7. Trois dots pulsants sous le status — pure ornement visuel.
        int dotsY = sigilCy + sigilDisplay / 2 + Math.round(100 * responsive);
        int dotsCx = screenW / 2;
        int dotSpacing = Math.round(14 * responsive);
        int dotRadius = Math.round(3 * responsive);
        for (int i = -1; i <= 1; i++) {
            float phase = t * 2f + i * 0.4f;
            float pulse = 0.4f + 0.6f * (0.5f + 0.5f * (float) Math.sin(phase));
            int alpha = Math.round(pulse * 255);
            int color = (alpha << 24) | (Colors.ACCENT_HOVER & 0x00FFFFFF);
            DrawHelpers.disc(ctx, dotsCx + i * dotSpacing, dotsY, dotRadius, color);
        }
    }
}
