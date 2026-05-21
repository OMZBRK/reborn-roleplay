package fr.reborn.integrity.ui.menu;

import com.mojang.blaze3d.systems.RenderSystem;
import fr.reborn.integrity.ui.Colors;
import fr.reborn.integrity.ui.RebornFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Logo central "RE [sigil] ORN" du main menu.
 *
 * <p>Composition :
 * <ul>
 *   <li>"RE" à gauche en Bebas Neue display (large, blanc cassé)</li>
 *   <li>{@code logo_sigil.png} (256×256, cercle bleu + R brushed)
 *       centré entre les deux mots</li>
 *   <li>"ORN" à droite, même style que "RE"</li>
 *   <li>Sous-titre {@code "Roleplay · Shinobi Chronicle"} en Inter Medium,
 *       letter-spacing élevé, gris muté</li>
 * </ul>
 *
 * <p>Le sigil PNG est dessiné à sa taille native (256) mais le texte
 * "RE" / "ORN" est scalé pour matcher. On vise une hauteur de logo
 * autour de 110-130 px (proportionnel à la hauteur d'écran).
 */
public final class CentralLogo {

    private static final Identifier SIGIL =
        Identifier.of("reborn", "textures/gui/logo_sigil.png");

    /** Taille native du PNG dans le bundle. */
    private static final int SIGIL_NATIVE = 256;

    /** Taille affichée du sigil sur écran cible 1280+ (downscale). */
    private static final int SIGIL_DISPLAY_BASE = 100;

    /**
     * Scale du texte "RE" / "ORN" — divise par font.size (32 dans le JSON
     * pour Bebas Neue), ce qui donne un texte ~80px de haut sur écran cible.
     * Anciennement 8.5x avec size=11 → flou. Maintenant 2.5x avec size=32 →
     * pixel-perfect.
     */
    private static final float TEXT_SCALE_BASE = 2.5f;

    /** Décalage horizontal entre le texte et le sigil (gap). */
    private static final int GAP_TEXT_SIGIL_BASE = 14;

    /** Décalage vertical depuis le top de l'écran en fraction. */
    private static final float TOP_FRACTION = 0.10f;

    private CentralLogo() {}

    public static void render(DrawContext ctx, int screenW, int screenH) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        TextRenderer tr = client.textRenderer;

        // Responsive scale — réduit l'ensemble du logo sur petites fenêtres.
        float responsive = fr.reborn.integrity.ui.menu.MainMenuRenderer.responsiveScale(screenW);

        int sigilDisplay = Math.round(SIGIL_DISPLAY_BASE * responsive);
        float textScale = TEXT_SCALE_BASE * responsive;
        int gapTextSigil = Math.round(GAP_TEXT_SIGIL_BASE * responsive);

        int sigilY = Math.round(screenH * TOP_FRACTION);
        int sigilX = (screenW - sigilDisplay) / 2;

        // ─── Sigil PNG ───
        ctx.getMatrices().push();
        ctx.getMatrices().translate(sigilX, sigilY, 0);
        float sigilScale = (float) sigilDisplay / SIGIL_NATIVE;
        ctx.getMatrices().scale(sigilScale, sigilScale, 1f);
        RenderSystem.enableBlend();
        ctx.drawTexture(SIGIL, 0, 0, 0f, 0f, SIGIL_NATIVE, SIGIL_NATIVE, SIGIL_NATIVE, SIGIL_NATIVE);
        ctx.getMatrices().pop();

        // ─── Texte "RE" et "ORN" en Bebas Neue (size 32 dans le JSON) ───
        Text textLeft = RebornFont.display("RE");
        Text textRight = RebornFont.display("ORN");

        int rawTextWLeft = tr.getWidth(textLeft);
        int rawTextWRight = tr.getWidth(textRight);
        int scaledTextHeight = Math.round(tr.fontHeight * textScale);

        int textY = sigilY + sigilDisplay / 2 - scaledTextHeight / 2;

        int textXLeft = sigilX - gapTextSigil - Math.round(rawTextWLeft * textScale);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(textXLeft, textY, 0);
        ctx.getMatrices().scale(textScale, textScale, 1f);
        ctx.drawText(tr, textLeft, 0, 0, Colors.WHITE_PURE, false);
        ctx.getMatrices().pop();

        int textXRight = sigilX + sigilDisplay + gapTextSigil;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(textXRight, textY, 0);
        ctx.getMatrices().scale(textScale, textScale, 1f);
        ctx.drawText(tr, textRight, 0, 0, Colors.WHITE_PURE, false);
        ctx.getMatrices().pop();

        // ─── Sous-titre "ROLEPLAY · SHINOBI CHRONICLE" ───
        Text sub = RebornFont.body("ROLEPLAY  ·  SHINOBI CHRONICLE");
        float subScale = 1.3f * responsive;
        int subWidth = Math.round(tr.getWidth(sub) * subScale);
        int subX = (screenW - subWidth) / 2;
        int subY = sigilY + sigilDisplay + Math.round(12 * responsive);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(subX, subY, 0);
        ctx.getMatrices().scale(subScale, subScale, 1f);
        ctx.drawText(tr, sub, 0, 0, Colors.FOREGROUND_SUBTLE, false);
        ctx.getMatrices().pop();
    }
}
