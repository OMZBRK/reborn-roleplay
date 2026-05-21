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

    /** Taille affichée du sigil (downscale du PNG). */
    private static final int SIGIL_DISPLAY = 100;

    /** Scale du texte "RE" / "ORN" par rapport à la font default 8px. */
    private static final float TEXT_SCALE = 8.5f;

    /** Letter-spacing artificiel entre les caractères du logo (en px). */
    private static final int LETTER_SPACING = 8;

    /** Décalage horizontal entre le texte et le sigil (gap). */
    private static final int GAP_TEXT_SIGIL = 14;

    /** Décalage vertical depuis le top de l'écran en fraction. */
    private static final float TOP_FRACTION = 0.18f;

    private CentralLogo() {}

    public static void render(DrawContext ctx, int screenW, int screenH) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        TextRenderer tr = client.textRenderer;

        int sigilY = Math.round(screenH * TOP_FRACTION);
        int sigilX = (screenW - SIGIL_DISPLAY) / 2;

        // ─── Sigil PNG ───
        // Drawn via context.drawTexture avec scaling (PNG natif 256, display 100).
        ctx.getMatrices().push();
        ctx.getMatrices().translate(sigilX, sigilY, 0);
        float sigilScale = (float) SIGIL_DISPLAY / SIGIL_NATIVE;
        ctx.getMatrices().scale(sigilScale, sigilScale, 1f);
        RenderSystem.enableBlend();
        ctx.drawTexture(SIGIL, 0, 0, 0f, 0f, SIGIL_NATIVE, SIGIL_NATIVE, SIGIL_NATIVE, SIGIL_NATIVE);
        ctx.getMatrices().pop();

        // ─── Texte "RE" et "ORN" en Bebas Neue ───
        Text textLeft = RebornFont.display("RE");
        Text textRight = RebornFont.display("ORN");

        int rawTextWLeft = tr.getWidth(textLeft);
        int rawTextWRight = tr.getWidth(textRight);
        int scaledTextHeight = Math.round(tr.fontHeight * TEXT_SCALE);

        // Position Y du texte : centré verticalement avec le sigil.
        int textY = sigilY + SIGIL_DISPLAY / 2 - scaledTextHeight / 2;

        // "RE" à gauche du sigil.
        int textXLeft = sigilX - GAP_TEXT_SIGIL - Math.round(rawTextWLeft * TEXT_SCALE);
        ctx.getMatrices().push();
        ctx.getMatrices().translate(textXLeft, textY, 0);
        ctx.getMatrices().scale(TEXT_SCALE, TEXT_SCALE, 1f);
        ctx.drawText(tr, textLeft, 0, 0, Colors.WHITE_PURE, false);
        ctx.getMatrices().pop();

        // "ORN" à droite du sigil.
        int textXRight = sigilX + SIGIL_DISPLAY + GAP_TEXT_SIGIL;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(textXRight, textY, 0);
        ctx.getMatrices().scale(TEXT_SCALE, TEXT_SCALE, 1f);
        ctx.drawText(tr, textRight, 0, 0, Colors.WHITE_PURE, false);
        ctx.getMatrices().pop();

        // ─── Sous-titre "Roleplay · Shinobi Chronicle" ───
        Text sub = RebornFont.body("ROLEPLAY  ·  SHINOBI CHRONICLE");
        float subScale = 1.5f;
        int subWidth = Math.round(tr.getWidth(sub) * subScale);
        int subX = (screenW - subWidth) / 2;
        int subY = sigilY + SIGIL_DISPLAY + 12;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(subX, subY, 0);
        ctx.getMatrices().scale(subScale, subScale, 1f);
        ctx.drawText(tr, sub, 0, 0, Colors.FOREGROUND_SUBTLE, false);
        ctx.getMatrices().pop();
    }
}
