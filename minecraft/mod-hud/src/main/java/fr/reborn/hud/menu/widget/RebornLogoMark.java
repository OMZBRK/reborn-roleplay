package fr.reborn.hud.menu.widget;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.RebornFont;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * Logo central du main menu — composition Stray/Sineru-style :
 * <ul>
 *   <li>Light beam vertical doux derriere (gradient transparent -> accent
 *       soft -> transparent) qui donne l'impression d'un projecteur.</li>
 *   <li>"REBORN" en font display gros au centre, ivoire chaud + glow accent.</li>
 *   <li>"ROLEPLAY" sub-line en petit, espacement letterspacing, accent gold.</li>
 *   <li>Underline accent crimson fine sous le sub-line.</li>
 * </ul>
 *
 * <p>Tout est text-based pour eviter une dependance asset PNG. L'user
 * pourra remplacer le rendu texte par une texture custom dessinee dans
 * Aseprite (cf docs/REBORN_ASEPRITE_PALETTE.md) en remplaçant le bloc
 * du title par un drawTexture sur un Identifier reborn:textures/gui/title/reborn.png.
 */
public final class RebornLogoMark {

    private static final String TITLE = "REBORN";
    private static final String SUB = "R O L E P L A Y";
    private static final Text TITLE_TEXT = RebornFont.display(TITLE);
    private static final Text SUB_TEXT = RebornFont.body(SUB);

    /** Hauteur totale approximative de la composition (logo + sub + underline). */
    public static int totalHeight() {
        return 64 + 12 + 14; // big text + gap + sub + underline area
    }

    /** Position Y du centre logique du logo (titre baseline approx). */
    public static int centerY(int screenH) {
        // ~37% from top — donne de la place au prompt en dessous.
        return Math.round(screenH * 0.36f);
    }

    private RebornLogoMark() {}

    public static void render(DrawContext ctx, int screenW, int screenH) {
        int cx = screenW / 2;
        int cy = centerY(screenH);

        // ── (1) Light beam vertical derriere le logo ────────────
        // Fauche douce du haut vers le bas — 3 couches alpha descendantes
        // pour effet projecteur "diffuse". Largeur ~screenW/2.
        int beamW = Math.min(screenW / 2, 720);
        int beamHTop = (int) (screenH * 0.30f);
        int beamHBot = (int) (screenH * 0.30f);
        int beamX = cx - beamW / 2;

        // Top half : transparent -> accent_soft (au niveau du logo)
        verticalFade(ctx, beamX, cy - beamHTop, beamW, beamHTop,
            0x00000000, Colors.withAlpha(Colors.ACCENT, 0.10f));
        // Bottom half : accent_soft -> transparent
        verticalFade(ctx, beamX, cy, beamW, beamHBot,
            Colors.withAlpha(Colors.ACCENT, 0.10f), 0x00000000);

        // Halo radial central plus serre (glow autour du logo)
        int haloR = 110;
        for (int r = haloR; r >= 10; r -= 10) {
            float t = (float) r / haloR;
            int alpha = Math.round((1f - t) * 18); // max alpha 18, fade vers 0
            if (alpha <= 0) continue;
            int color = (alpha << 24) | (Colors.ACCENT & 0xFFFFFF);
            ctx.fill(cx - r, cy - r / 4, cx + r, cy + r / 4, color);
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;
        TextRenderer tr = client.textRenderer;

        // ── (2) "REBORN" en font display tres gros centre ──────
        // On utilise le scale matrix pour rendre le texte 3x plus gros
        // sans dependre d'une font separee.
        float titleScale = 4.5f;
        int titleW = (int) (tr.getWidth(TITLE_TEXT) * titleScale);
        int titleH = (int) (tr.fontHeight * titleScale);
        int titleX = cx - titleW / 2;
        int titleY = cy - titleH / 2;

        ctx.getMatrices().push();
        ctx.getMatrices().translate(titleX, titleY, 0);
        ctx.getMatrices().scale(titleScale, titleScale, 1f);
        // Shadow soft derriere le titre pour le detacher du BG
        ctx.drawText(tr, TITLE_TEXT, 1, 1, 0x70000000, false);
        ctx.drawText(tr, TITLE_TEXT, 0, 0, Colors.FOREGROUND, false);
        ctx.getMatrices().pop();

        // Glow rouge subtil sous le titre (overlay accent halo)
        int glowAlpha = 28;
        for (int i = 1; i <= 3; i++) {
            int color = ((glowAlpha / i) << 24) | (Colors.ACCENT_HOVER & 0xFFFFFF);
            ctx.fill(titleX - i * 2, titleY + titleH - 2 - i,
                titleX + titleW + i * 2, titleY + titleH + i + 4, color);
        }

        // ── (3) Sub-line "ROLEPLAY" en gold, letterspacing ─────
        // Le texte est deja espace dans la string ("R O L E P L A Y"),
        // on l'affiche en small caps gold sous le titre.
        int subW = tr.getWidth(SUB_TEXT);
        int subX = cx - subW / 2;
        int subY = titleY + titleH + 14;
        ctx.drawText(tr, SUB_TEXT, subX + 1, subY + 1, 0x80000000, false);
        ctx.drawText(tr, SUB_TEXT, subX, subY, Colors.GOLD, false);

        // ── (4) Underline accent fine ───────────────────────────
        int underlineW = Math.min(subW + 24, 180);
        int underlineX = cx - underlineW / 2;
        int underlineY = subY + tr.fontHeight + 6;
        // Ligne centrale plus opaque, fade vers les extremites
        for (int i = 0; i < underlineW / 2; i++) {
            float t = (float) i / (underlineW / 2);
            int alpha = Math.round((1f - t) * 200);
            int color = (alpha << 24) | (Colors.ACCENT & 0xFFFFFF);
            ctx.fill(underlineX + (underlineW / 2) - i, underlineY,
                underlineX + (underlineW / 2) - i + 1, underlineY + 1, color);
            ctx.fill(underlineX + (underlineW / 2) + i, underlineY,
                underlineX + (underlineW / 2) + i + 1, underlineY + 1, color);
        }
    }

    /**
     * Helper : remplit un rectangle avec un gradient vertical entre 2 couleurs
     * ARGB. Implementation lineaire en N rangees de 1px (suffisant pour notre
     * usage, alternative serait un shader custom).
     */
    private static void verticalFade(DrawContext ctx, int x, int y, int w, int h, int colorTop, int colorBot) {
        if (h <= 0 || w <= 0) return;
        for (int i = 0; i < h; i++) {
            float t = (float) i / h;
            int color = Colors.lerp(colorTop, colorBot, t);
            ctx.fill(x, y + i, x + w, y + i + 1, color);
        }
    }
}
