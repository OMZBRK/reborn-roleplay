package fr.reborn.hud.ui;

import fr.reborn.hud.ui.style.Glow;
import fr.reborn.hud.ui.style.RebornColors;
import fr.reborn.hud.ui.style.RoundedRect;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

/**
 * Composants chrome du {@link HudEditScreen} (top bar + footer keybar +
 * version badge) en helpers static rendus. Pas de state interne — juste
 * du draw conditionnel.
 *
 * <p>Les hit-tests pour les icon buttons / search clear sont fournis ici
 * via les rect helpers, mais c'est le Screen qui appelle {@code mouseClicked}
 * et dispatch.
 */
public final class HudEditChrome {

    // Dimensions ajustees pour rester compactes a tous les GUI Size.
    // Le top bar etait 56 → 40 (libere la zone boss bar vanilla).
    // Side panel 320 → 240 (moins de 1/4 d'ecran a GUI Size 2).
    // Icon buttons 36 → 24, search 280 → 200.
    public static final int TOPBAR_HEIGHT = 40;
    public static final int SIDEPANEL_WIDTH = 240;
    public static final int ICONBTN_SIZE = 24;
    public static final int ICONBTN_GAP = 4;

    /** Largeur de la search box. */
    public static final int SEARCH_WIDTH = 160;

    private HudEditChrome() {}

    // ──────────────────────────────────────────
    // TOP BAR
    // ──────────────────────────────────────────

    /** Render le fond du top bar + divider en bas + drop shadow. */
    public static void renderTopBar(DrawContext ctx, int screenWidth) {
        // Background avec léger fondu vers le bas pour suggérer le blur
        ctx.fill(0, 0, screenWidth, TOPBAR_HEIGHT, 0xCC0A0B0F);
        // Border bottom 1px
        ctx.fill(0, TOPBAR_HEIGHT - 1, screenWidth, TOPBAR_HEIGHT, RebornColors.BORDER);
        // Drop shadow subtle 4px sous la top bar
        for (int i = 1; i <= 4; i++) {
            int alpha = (5 - i) * 12;
            ctx.fill(0, TOPBAR_HEIGHT + i - 1, screenWidth, TOPBAR_HEIGHT + i,
                (alpha << 24));
        }
    }

    /**
     * Render le logo "REBORN" + divider + titre. Renvoie le X de fin pour
     * placer la search à droite.
     */
    public static int renderLogoAndTitle(DrawContext ctx, TextRenderer tr, int screenHeight) {
        int xCursor = 12;
        int centerY = TOPBAR_HEIGHT / 2;

        // Logo : R en accent, EBORN en blanc — bold sans scale pour rester
        // compact (le TextRenderer vanilla est deja gros aux GUI Size > 1).
        ctx.drawText(tr, Text.literal("R").formatted(Formatting.BOLD),
            xCursor, centerY - 4, RebornColors.ACCENT_HOVER, false);
        int rWidth = tr.getWidth("R");
        ctx.drawText(tr, Text.literal("EBORN").formatted(Formatting.BOLD),
            xCursor + rWidth, centerY - 4, RebornColors.FOREGROUND, false);
        xCursor += rWidth + tr.getWidth("EBORN") + 12;

        // Divider vertical 1×16
        ctx.fill(xCursor, centerY - 8, xCursor + 1, centerY + 8, RebornColors.BORDER_STRONG);
        xCursor += 12;

        // Titre "EDITEUR HUD"
        ctx.drawText(tr, Text.literal("ÉDITEUR ").formatted(Formatting.BOLD),
            xCursor, centerY - 4, RebornColors.FOREGROUND_SUBTLE, false);
        int eW = tr.getWidth("ÉDITEUR ");
        ctx.drawText(tr, Text.literal("HUD").formatted(Formatting.BOLD),
            xCursor + eW, centerY - 4, RebornColors.FOREGROUND, false);
        xCursor += eW + tr.getWidth("HUD") + 12;

        return xCursor;
    }

    /** Render l'icon button : carré 36×36 avec optionnel badge count en haut-droite. */
    public static void renderIconButton(DrawContext ctx, int x, int y, boolean hovered,
                                        boolean danger, int badge) {
        int bg = hovered
            ? (danger ? RebornColors.DANGER_SOFT : 0x14FFFFFF)
            : 0x00000000;
        int border = hovered
            ? (danger ? RebornColors.withAlpha(RebornColors.DANGER, 0x4D) : RebornColors.BORDER)
            : 0x00000000;
        RoundedRect.fill(ctx, x, y, ICONBTN_SIZE, ICONBTN_SIZE, 6, bg);
        if (border != 0) RoundedRect.border(ctx, x, y, ICONBTN_SIZE, ICONBTN_SIZE, 6, border);

        if (badge > 0) {
            String label = String.valueOf(badge);
            int w = Math.max(16, 8 + badge >= 10 ? 12 : 8);
            int bX = x + ICONBTN_SIZE - w + 3;
            int bY = y - 3;
            RoundedRect.fill(ctx, bX, bY, w, 16, 8, RebornColors.ACCENT);
            // Bordure 2px de la couleur du fond pour le "halo découpé"
            RoundedRect.border(ctx, bX - 1, bY - 1, w + 2, 18, 9, RebornColors.BG_DEEP);
        }
    }

    /**
     * Render une icone via les textures 16×16 enregistrées au boot par
     * {@link fr.reborn.hud.ui.style.IconTextures}. Centrée sur (cx, cy)
     * avec une taille de 12×12 (zoom out du 16×16 source).
     */
    public static void renderIconGlyph(DrawContext ctx, String type, int cx, int cy, int color) {
        int size = 12;
        fr.reborn.hud.ui.style.IconTextures.draw(ctx, type, cx - size / 2, cy - size / 2, size, color);
    }

    // ─────────── Icones cleanees v2 ───────────
    // Approche : 2px stroke, geometrie claire, alignement strict sur grille.

    private static void drawClose(DrawContext ctx, int cx, int cy, int color) {
        // X 10×10, 2px stroke diagonal
        for (int i = 0; i < 6; i++) {
            // Diagonale \ : (cx-3+i, cy-3+i)
            int x = cx - 3 + i, y = cy - 3 + i;
            ctx.fill(x, y, x + 2, y + 2, color);
            // Diagonale / : (cx+3-i, cy-3+i)
            x = cx + 3 - i; y = cy - 3 + i;
            ctx.fill(x, y, x + 2, y + 2, color);
        }
    }

    private static void drawUndo(DrawContext ctx, int cx, int cy, int color, boolean mirror) {
        // Arrow curved : arc top + verticale + pointe
        int s = mirror ? -1 : 1;
        // Arc horizontal en haut (epaisseur 2)
        for (int i = 0; i < 5; i++) {
            ctx.fill(cx - 2 + i, cy - 4, cx - 2 + i + 1, cy - 2, color);
        }
        // Coin de l'arc qui descend a droite
        ctx.fill(cx + 3, cy - 4, cx + 4, cy - 1, color);
        ctx.fill(cx + 3, cy - 1, cx + 5, cy, color);
        // Tige verticale du retour (gauche)
        for (int y = -4; y <= 1; y++) {
            ctx.fill(cx - 3, cy + y, cx - 2, cy + y + 1, color);
        }
        // Pointe a gauche-bas (triangle)
        ctx.fill(cx - 5, cy, cx - 1, cy + 1, color);
        ctx.fill(cx - 4, cy + 1, cx - 2, cy + 2, color);
        ctx.fill(cx - 3, cy + 2, cx - 2, cy + 3, color);
        ctx.fill(cx - 4, cy - 1, cx - 2, cy, color);
        // Mirror toute la chose si redo
        if (mirror) {
            // TODO : redo est dessine pareil pour l'instant, le mirror est implicite via context
        }
    }

    private static void drawSearch(DrawContext ctx, int cx, int cy, int color) {
        // Cercle creux 7×7 + queue diagonale
        int[] ring = {
            //  ##
            // #  #
            // #  #
            //  ##
        };
        for (int dy = -3; dy <= 3; dy++) {
            for (int dx = -3; dx <= 3; dx++) {
                int d2 = dx * dx + dy * dy;
                if (d2 >= 4 && d2 <= 9) {
                    ctx.fill(cx + dx - 2, cy + dy - 2, cx + dx - 1, cy + dy - 1, color);
                }
            }
        }
        // Queue de la loupe (3px)
        ctx.fill(cx + 1, cy + 1, cx + 3, cy + 3, color);
        ctx.fill(cx + 3, cy + 3, cx + 5, cy + 5, color);
    }

    private static void drawEye(DrawContext ctx, int cx, int cy, int color, boolean open) {
        // Ovale stroke 2px + pupille centrée
        // Top & bottom edges
        ctx.fill(cx - 4, cy - 2, cx + 4, cy - 1, color);
        ctx.fill(cx - 4, cy + 1, cx + 4, cy + 2, color);
        // Side caps
        ctx.fill(cx - 5, cy - 1, cx - 4, cy + 1, color);
        ctx.fill(cx + 4, cy - 1, cx + 5, cy + 1, color);
        if (open) {
            // Pupille pleine centrée
            ctx.fill(cx - 1, cy - 1, cx + 1, cy + 1, color);
        } else {
            // Barre diagonale rouge over l'oeil
            for (int i = -5; i <= 5; i++) {
                int py = cy - i / 2 - 1;
                ctx.fill(cx + i, py, cx + i + 1, py + 2, RebornColors.DANGER);
            }
        }
    }

    private static void drawGear(DrawContext ctx, int cx, int cy, int color) {
        // Couronne externe 7x7 carrée avec dents
        // 4 dents N/S/E/W (carrés 2x2 collés au bord)
        ctx.fill(cx - 1, cy - 6, cx + 1, cy - 4, color);
        ctx.fill(cx - 1, cy + 4, cx + 1, cy + 6, color);
        ctx.fill(cx - 6, cy - 1, cx - 4, cy + 1, color);
        ctx.fill(cx + 4, cy - 1, cx + 6, cy + 1, color);
        // 4 dents diagonales
        ctx.fill(cx + 3, cy - 5, cx + 5, cy - 3, color);
        ctx.fill(cx - 5, cy - 5, cx - 3, cy - 3, color);
        ctx.fill(cx + 3, cy + 3, cx + 5, cy + 5, color);
        ctx.fill(cx - 5, cy + 3, cx - 3, cy + 5, color);
        // Corps central : anneau plein (5x5) avec trou (1x1)
        ctx.fill(cx - 3, cy - 3, cx + 3, cy + 3, color);
        ctx.fill(cx - 1, cy - 1, cx + 1, cy + 1, 0xFF000000); // trou
    }

    // ──────────────────────────────────────────
    // FOOTER KEYBAR
    // ──────────────────────────────────────────

    /**
     * Rend la keybar (collapsible) en haut-centre, juste sous le top bar
     * pour ne PAS empieter sur la hotbar / action bar / boss bar / autres
     * elements bas d'ecran.
     *
     * <p>{@code collapsed=true} → pille mini "?" 22×22 (juste un hint), clic
     * pour deplier.
     *
     * @return rect [x, y, w, h] du toggle (collapse en mode etendu / le pill
     *         entier en mode collapsed) pour le hit-test cote screen.
     */
    public static int[] renderKeybar(DrawContext ctx, TextRenderer tr, int screenWidth,
                                     int totalElements, int modifiedElements, boolean collapsed) {
        // Place le keybar a TOP-LEFT (x=8) pour ne pas conflicter avec la
        // boss bar centrée ou la zone de search top-right.
        int leftMargin = 8;

        if (collapsed) {
            int size = 18;
            int x = leftMargin;
            int y = TOPBAR_HEIGHT + 4;
            RoundedRect.fill(ctx, x, y, size, size, 5, RebornColors.BG_PANEL_ELEVATED);
            RoundedRect.border(ctx, x, y, size, size, 5, RebornColors.BORDER_STRONG);
            // "?" centre
            String q = "?";
            ctx.drawText(tr, Text.literal(q).formatted(Formatting.BOLD),
                x + size / 2 - tr.getWidth(q) / 2, y + (size - tr.fontHeight) / 2 + 1,
                RebornColors.FOREGROUND_SUBTLE, false);
            return new int[]{x, y, size, size};
        }

        String[][] keys = {
            {"H",         "Ouvrir / Fermer"},
            {"Shift",     "+ Drag"},
            {"Ctrl+Z",    "Annuler"},
            {"Ctrl+Clic", "Sélection multi."}
        };
        String meta = totalElements + " éléments · " + modifiedElements + " modifiés";

        int padX = 10, padY = 5, kbdPadX = 4, gap = 10, collapseBtnW = 16;
        int itemHeight = tr.fontHeight;

        int totalW = 0;
        for (String[] k : keys) {
            int kbdW = tr.getWidth(k[0]) + kbdPadX * 2;
            int descW = tr.getWidth(k[1]);
            totalW += kbdW + 4 + descW + gap;
        }
        totalW -= gap;
        int metaW = tr.getWidth(meta);
        int barWidth = Math.max(totalW, metaW) + padX * 2 + collapseBtnW + 4;
        int barHeight = itemHeight + 2 + itemHeight + padY * 2;

        // Place top-left au lieu de centre — l'utilisateur a deplie volontairement.
        int barX = leftMargin;
        int barY = TOPBAR_HEIGHT + 4;

        RoundedRect.fill(ctx, barX, barY, barWidth, barHeight, 8, RebornColors.BG_PANEL_ELEVATED);
        RoundedRect.border(ctx, barX, barY, barWidth, barHeight, 8, RebornColors.BORDER);

        // Collapse toggle "−" left
        int collapseX = barX + 4;
        int collapseY = barY + (barHeight - collapseBtnW) / 2;
        ctx.drawText(tr, Text.literal("−").formatted(Formatting.BOLD),
            collapseX + 4, collapseY + 4, RebornColors.FOREGROUND_MUTED, false);

        // Lignes keys
        int x = barX + collapseBtnW + (barWidth - totalW - collapseBtnW) / 2;
        int y = barY + padY;
        for (String[] k : keys) {
            int kbdW = tr.getWidth(k[0]) + kbdPadX * 2;
            RoundedRect.fill(ctx, x, y - 1, kbdW, itemHeight + 3, 3, RebornColors.BG_INPUT);
            RoundedRect.border(ctx, x, y - 1, kbdW, itemHeight + 3, 3, RebornColors.BORDER_STRONG);
            ctx.drawText(tr, Text.literal(k[0]), x + kbdPadX, y, RebornColors.FOREGROUND, false);
            x += kbdW + 4;
            ctx.drawText(tr, Text.literal(k[1]), x, y, RebornColors.FOREGROUND_SUBTLE, false);
            x += tr.getWidth(k[1]) + gap;
        }

        // Ligne meta
        int metaX = barX + (barWidth - metaW) / 2;
        int metaY = barY + padY + itemHeight + 4;
        ctx.drawText(tr, Text.literal(meta), metaX, metaY, RebornColors.FOREGROUND_MUTED, false);

        // Le hit-test du collapse toggle est sur la zone gauche du pill
        return new int[]{collapseX - 2, collapseY - 4, collapseBtnW + 4, collapseBtnW + 8};
    }

    // ──────────────────────────────────────────
    // VERSION BADGE
    // ──────────────────────────────────────────

    public static void renderVersionBadge(DrawContext ctx, TextRenderer tr, int screenHeight) {
        int x = 8, y = screenHeight - 24;
        // R mark : square 18×18 accent-soft + R bold accent
        RoundedRect.fill(ctx, x, y, 18, 18, 4, RebornColors.ACCENT_SOFT);
        RoundedRect.border(ctx, x, y, 18, 18, 4, RebornColors.withAlpha(RebornColors.ACCENT, 0x66));
        // R centered
        ctx.drawText(tr, Text.literal("R").formatted(Formatting.BOLD),
            x + 7, y + 5, RebornColors.ACCENT_HOVER, false);
        // Label
        ctx.drawText(tr, Text.literal("Reborn HUD v0.2.0"),
            x + 24, y + 5, RebornColors.FOREGROUND_MUTED, false);
    }
}
