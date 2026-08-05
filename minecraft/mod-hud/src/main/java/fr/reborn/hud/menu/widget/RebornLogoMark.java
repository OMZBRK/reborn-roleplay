package fr.reborn.hud.menu.widget;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.RebornFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Wordmark central du main menu — version epuree.
 *
 * <p>Composition minimaliste, aucun effet decoratif :
 * <ul>
 *   <li>"REBORN" en font display, ivoire chaud, une seule ombre douce
 *       pour le detacher du BG MCEF.</li>
 *   <li>"ROLEPLAY" sub-line discrete en foreground attenue, letterspacing.</li>
 *   <li>Fine hairline statique sous le sub-line (1px, sobre).</li>
 * </ul>
 *
 * <p>Plus de light beam, de halo radial ni de glow accent : le fond 3D
 * MCEF porte deja la richesse visuelle, le wordmark reste net et calme.
 * Tout est text-based ; on pourra le remplacer par une texture PNG custom
 * en swappant le bloc du titre par un drawTexture.
 */
public final class RebornLogoMark {

    private static final String TITLE = "REBORN";
    private static final String SUB = "R O L E P L A Y";
    private static final Component TITLE_TEXT = RebornFont.display(TITLE);
    private static final Component SUB_TEXT = RebornFont.body(SUB);

    /** Echelle du titre — plus sobre que l'ancien 4.5f. */
    private static final float TITLE_SCALE = 3.4f;

    /** Hauteur totale approximative de la composition (logo + sub + hairline). */
    public static int totalHeight() {
        return 48 + 12 + 12;
    }

    /** Position Y du centre logique du logo (titre baseline approx). */
    public static int centerY(int screenH) {
        // ~35% from top — donne de la place au prompt en dessous.
        return Math.round(screenH * 0.35f);
    }

    private RebornLogoMark() {}

    public static void render(GuiGraphicsExtractor ctx, int screenW, int screenH) {
        int cx = screenW / 2;
        int cy = centerY(screenH);

        Minecraft client = Minecraft.getInstance();
        if (client == null) return;
        Font tr = client.textRenderer;

        // ── "REBORN" — font display, ombre douce unique ──────────
        int titleW = (int) (tr.width(TITLE_TEXT) * TITLE_SCALE);
        int titleH = (int) (tr.lineHeight * TITLE_SCALE);
        int titleX = cx - titleW / 2;
        int titleY = cy - titleH / 2;

        ctx.pose().pushMatrix();
        ctx.pose().translate(titleX, titleY);
        ctx.pose().scale(TITLE_SCALE, TITLE_SCALE);
        ctx.text(tr, TITLE_TEXT, 1, 1, 0x66000000, false);
        ctx.text(tr, TITLE_TEXT, 0, 0, Colors.FOREGROUND, false);
        ctx.pose().popMatrix();

        // ── "ROLEPLAY" — sub-line discrete, foreground attenue ──
        int subW = tr.width(SUB_TEXT);
        int subX = cx - subW / 2;
        int subY = titleY + titleH + 10;
        ctx.text(tr, SUB_TEXT, subX + 1, subY + 1, 0x66000000, false);
        ctx.text(tr, SUB_TEXT, subX, subY, Colors.FOREGROUND_SUBTLE, false);

        // ── Hairline statique fine sous le sub-line ─────────────
        int hairW = Math.min(subW, 160);
        int hairX = cx - hairW / 2;
        int hairY = subY + tr.lineHeight + 5;
        ctx.fill(hairX, hairY, hairX + hairW, hairY + 1,
            Colors.withAlpha(Colors.FOREGROUND_SUBTLE, 0.35f));
    }
}
