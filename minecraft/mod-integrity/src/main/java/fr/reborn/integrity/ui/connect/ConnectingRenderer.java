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
 * Rendu de l'écran de connexion Reborn — appelé par
 * {@code ConnectScreenMixin} en lieu et place du rendu vanilla.
 *
 * <p>Composition (centrée verticalement) :
 * <ol>
 *   <li>Background bleu nuit + sakura overlay.</li>
 *   <li>Sigil au centre, entouré d'un anneau pointillé rotatif (spinner).</li>
 *   <li>Titre "CONNEXION AU SERVEUR" en Bebas Neue, taille modérée.</li>
 *   <li>Status vanilla (handshake, login…) wrapped en Inter body.</li>
 *   <li>3 dots pulsants ornement.</li>
 * </ol>
 *
 * <p>Le bouton Annuler vanilla est repositionné par
 * {@code ConnectScreenMixin#init} vers le bas de l'écran — il ne se trouve
 * plus au milieu de notre composition. Le rendu de ce bouton se fait via
 * l'iteration {@code children()} dans le mixin.
 */
public final class ConnectingRenderer {

    private static final Identifier SIGIL =
        Identifier.of("reborn", "textures/gui/logo_sigil.png");

    private static final int SIGIL_NATIVE = 256;
    private static final int SIGIL_DISPLAY_BASE = 64;

    private static final long BORN_AT = System.currentTimeMillis();

    private ConnectingRenderer() {}

    public static void render(DrawContext ctx, int screenW, int screenH, Text status) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        TextRenderer tr = client.textRenderer;

        float responsive = MainMenuRenderer.responsiveScale(screenW);
        float t = (System.currentTimeMillis() - BORN_AT) / 1000f;

        // 1. Background bleu nuit + sakura.
        BackgroundRenderer.render(ctx, screenW, screenH);
        SakuraParticles.INSTANCE.render(ctx, screenW, screenH);

        // 2. Position centrale (un peu au-dessus du milieu).
        int sigilDisplay = Math.round(SIGIL_DISPLAY_BASE * responsive);
        int centerX = screenW / 2;
        int centerY = Math.round(screenH * 0.42f);
        int sigilX = centerX - sigilDisplay / 2;
        int sigilY = centerY - sigilDisplay / 2;

        // 3. Halo doux derrière le sigil (statique, juste une lueur).
        int haloR = sigilDisplay;
        DrawHelpers.disc(ctx, centerX, centerY, haloR, 0x223B5BDB);
        DrawHelpers.disc(ctx, centerX, centerY, (int) (haloR * 0.7f), 0x33304CB8);

        // 4. Spinner — anneau pointillé de base + arc rotatif lumineux.
        int spinnerR = sigilDisplay / 2 + Math.round(18 * responsive);
        // Anneau gris très subtil (50% du tour en dashs courts).
        DrawHelpers.ring(ctx, centerX, centerY, spinnerR, 1, 0x33FFFFFF);
        // Arc principal qui tourne — 90° d'arc, rotation à 90°/sec.
        float rotation = (t * 90f) % 360f;
        DrawHelpers.dashedRing(ctx, centerX, centerY, spinnerR, 2,
            Colors.ACCENT_HOVER, 90f, 270f, rotation);
        // Petit arc trailing lumineux (15°) blanc pur à la tête.
        DrawHelpers.dashedRing(ctx, centerX, centerY, spinnerR, 2,
            Colors.WHITE_PURE, 15f, 345f, rotation);

        // 5. Sigil au-dessus du spinner.
        ctx.getMatrices().push();
        ctx.getMatrices().translate(sigilX, sigilY, 0);
        float sigilScale = (float) sigilDisplay / SIGIL_NATIVE;
        ctx.getMatrices().scale(sigilScale, sigilScale, 1f);
        RenderSystem.enableBlend();
        ctx.drawTexture(SIGIL, 0, 0, 0f, 0f, SIGIL_NATIVE, SIGIL_NATIVE, SIGIL_NATIVE, SIGIL_NATIVE);
        ctx.getMatrices().pop();

        // 6. Titre "CONNEXION AU SERVEUR" — taille modérée, sous le sigil.
        Text title = RebornFont.display("CONNEXION AU SERVEUR");
        float titleScale = 1.0f * responsive;
        int titleW = Math.round(tr.getWidth(title) * titleScale);
        int titleX = (screenW - titleW) / 2;
        int titleY = centerY + sigilDisplay / 2 + Math.round(36 * responsive);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(titleX, titleY, 0);
        ctx.getMatrices().scale(titleScale, titleScale, 1f);
        ctx.drawText(tr, title, 0, 0, Colors.WHITE_PURE, false);
        ctx.getMatrices().pop();

        // 7. Status vanilla — wrap en Inter body pour cohérence visuelle.
        if (status != null) {
            Text statusWrapped = RebornFont.body(status.getString());
            float statusScale = 0.9f * responsive;
            int statusW = Math.round(tr.getWidth(statusWrapped) * statusScale);
            int statusX = (screenW - statusW) / 2;
            int statusY = titleY + Math.round(22 * responsive);
            ctx.getMatrices().push();
            ctx.getMatrices().translate(statusX, statusY, 0);
            ctx.getMatrices().scale(statusScale, statusScale, 1f);
            ctx.drawText(tr, statusWrapped, 0, 0, Colors.FOREGROUND_SUBTLE, false);
            ctx.getMatrices().pop();
        }

        // 8. Trois dots pulsants sous le status.
        int dotsY = titleY + Math.round(46 * responsive);
        int dotSpacing = Math.round(10 * responsive);
        int dotRadius = Math.round(2 * responsive);
        for (int i = -1; i <= 1; i++) {
            float phase = t * 2.5f + i * 0.5f;
            float pulse = 0.3f + 0.7f * (0.5f + 0.5f * (float) Math.sin(phase));
            int alpha = Math.round(pulse * 255);
            int color = (alpha << 24) | (Colors.ACCENT_HOVER & 0x00FFFFFF);
            DrawHelpers.disc(ctx, centerX + i * dotSpacing, dotsY, dotRadius, color);
        }
    }
}
