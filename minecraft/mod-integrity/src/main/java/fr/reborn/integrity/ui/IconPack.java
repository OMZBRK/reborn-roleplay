package fr.reborn.integrity.ui;

import net.minecraft.client.gui.DrawContext;

/**
 * Icônes UI Reborn — rendues en primitives DrawContext (pas de PNG)
 * pour éviter la dépendance à des textures externes et garder le mod
 * léger. Chaque icône est dessinée dans une box {@code size×size} dont
 * (x, y) est le coin haut-gauche.
 *
 * <p>Référence : tous les icons SVG du {@code shared.jsx} du design
 * system. Taille par défaut visée : 18px. Les icônes plus petites
 * (12-14px) ou plus grandes (24px+) restent lisibles parce que les
 * shapes sont géométriques (pas de détails sub-pixel).
 *
 * <p>API : {@code IconPack.play(ctx, x, y, 18, color)}. Convention :
 * la couleur passée s'applique au "stroke" / "fill" principal.
 */
public final class IconPack {

    private IconPack() {}

    // ──────────────────────────────────────────────
    // Helpers internes
    // ──────────────────────────────────────────────

    /** Convertit une coord 0..24 (viewBox SVG d'origine) en pixel local. */
    private static int p(int x, int size) {
        return Math.round(x * size / 24f);
    }

    /** Convertit une dimension 0..24 en pixel local. */
    private static int d(int v, int size) {
        return Math.max(1, Math.round(v * size / 24f));
    }

    // ──────────────────────────────────────────────
    // Media controls
    // ──────────────────────────────────────────────

    /** ▶ Triangle play (rempli). */
    public static void play(DrawContext ctx, int x, int y, int size, int color) {
        int s = size;
        int rows = Math.max(5, s);
        int margin = Math.max(1, s / 6);
        for (int row = 0; row < rows; row++) {
            float t = (float) row / (rows - 1);
            int barX0 = x + margin;
            int barX1 = x + s - margin - Math.round(t * (s - 2 * margin));
            int barY = y + Math.round(t * (s - 1));
            int barH = Math.max(1, (s - 1) / rows + 1);
            ctx.fill(barX0, barY, barX1, barY + barH, color);
        }
        // Approximation plus propre : triangle rempli ligne par ligne.
        int[] tri = triangleRows(s);
        for (int row = 0; row < s; row++) {
            int w = tri[row];
            if (w > 0) ctx.fill(x + margin, y + row, x + margin + w, y + row + 1, color);
        }
    }

    private static int[] triangleRows(int size) {
        int[] r = new int[size];
        int peak = size / 2;
        for (int i = 0; i < size; i++) {
            float t = Math.abs(i - peak) / (float) peak;
            r[i] = Math.max(0, Math.round((1f - t) * (size - 2)));
        }
        return r;
    }

    /** ⏸ Pause (2 rectangles verticaux). */
    public static void pause(DrawContext ctx, int x, int y, int size, int color) {
        int bar = Math.max(2, size / 5);
        int gap = Math.max(1, size / 6);
        int top = y + size / 6;
        int bot = y + size - size / 6;
        int leftX = x + (size - 2 * bar - gap) / 2;
        ctx.fill(leftX, top, leftX + bar, bot, color);
        ctx.fill(leftX + bar + gap, top, leftX + 2 * bar + gap, bot, color);
    }

    /** ⏮ Previous (triangle + barre verticale à gauche). */
    public static void skipPrev(DrawContext ctx, int x, int y, int size, int color) {
        int bar = Math.max(1, size / 8);
        ctx.fill(x, y + size / 6, x + bar, y + size - size / 6, color);
        int[] tri = triangleRows(size);
        for (int row = 0; row < size; row++) {
            int w = tri[row];
            if (w > 0) ctx.fill(x + size - w - bar, y + row, x + size - bar, y + row + 1, color);
        }
        // Triangle pointant à gauche : mirror.
        // Note : le triangle ci-dessus pointe à droite. Pour la version "prev"
        // on inverse la direction.
    }

    /** ⏭ Next (triangle + barre verticale à droite). */
    public static void skipNext(DrawContext ctx, int x, int y, int size, int color) {
        int bar = Math.max(1, size / 8);
        int[] tri = triangleRows(size);
        for (int row = 0; row < size; row++) {
            int w = tri[row];
            if (w > 0) ctx.fill(x, y + row, x + w, y + row + 1, color);
        }
        ctx.fill(x + size - bar, y + size / 6, x + size, y + size - size / 6, color);
    }

    /** 🔉 Speaker simple (cône + corps). */
    public static void volume(DrawContext ctx, int x, int y, int size, int color) {
        int mid = y + size / 2;
        // Corps du haut-parleur (carré central).
        int bodyTop = mid - size / 6;
        int bodyBot = mid + size / 6;
        ctx.fill(x + size / 6, bodyTop, x + size / 3, bodyBot, color);
        // Cône (triangle qui s'élargit).
        for (int i = 0; i < size / 3; i++) {
            int spread = (i * size) / (size + 2);
            ctx.fill(x + size / 3 + i, mid - spread - 1, x + size / 3 + i + 1, mid + spread + 1, color);
        }
        // Deux arcs de son (approximation 2 demi-cercles dégradés).
        int arcStart = x + size * 2 / 3;
        for (int i = 0; i < size / 4; i++) {
            int arcY = mid - size / 4 + i * 2;
            ctx.fill(arcStart, arcY, arcStart + 1, arcY + 1, color);
        }
    }

    /** 🔇 Speaker barré (muet). */
    public static void volumeMuted(DrawContext ctx, int x, int y, int size, int color) {
        volume(ctx, x, y, size, color);
        // Barre diagonale à travers.
        DrawHelpers.line(ctx, x + 2, y + 2, x + size - 2, y + size - 2, Colors.DANGER);
    }

    // ──────────────────────────────────────────────
    // Navigation
    // ──────────────────────────────────────────────

    /** ☰ Menu hamburger (3 lignes horizontales). */
    public static void menu(DrawContext ctx, int x, int y, int size, int color) {
        int barH = Math.max(1, size / 12);
        int marginX = size / 6;
        int spacing = (size - 3 * barH) / 4;
        int yy = y + spacing;
        for (int i = 0; i < 3; i++) {
            ctx.fill(x + marginX, yy, x + size - marginX, yy + barH, color);
            yy += barH + spacing;
        }
    }

    /** ⚙️ Settings (carré central + 4 picots). */
    public static void settings(DrawContext ctx, int x, int y, int size, int color) {
        int cx = x + size / 2;
        int cy = y + size / 2;
        DrawHelpers.ring(ctx, cx, cy, size / 3, Math.max(1, size / 10), color);
        DrawHelpers.disc(ctx, cx, cy, size / 8, color);
        // 4 picots (haut, bas, gauche, droite).
        int pickW = Math.max(2, size / 6);
        int pickH = Math.max(1, size / 8);
        ctx.fill(cx - pickH / 2, y, cx + pickH / 2 + 1, y + pickW, color);
        ctx.fill(cx - pickH / 2, y + size - pickW, cx + pickH / 2 + 1, y + size, color);
        ctx.fill(x, cy - pickH / 2, x + pickW, cy + pickH / 2 + 1, color);
        ctx.fill(x + size - pickW, cy - pickH / 2, x + size, cy + pickH / 2 + 1, color);
    }

    /** 🌐 Globe (cercle + méridiens). */
    public static void globe(DrawContext ctx, int x, int y, int size, int color) {
        int cx = x + size / 2;
        int cy = y + size / 2;
        int r = size / 2 - 1;
        DrawHelpers.ring(ctx, cx, cy, r, 1, color);
        // Méridien vertical (ellipse approximée).
        DrawHelpers.ring(ctx, cx, cy, r, 1, Colors.withAlpha(color, 0.6f));
        ctx.fill(cx, cy - r, cx + 1, cy + r, color);
        // Méridien horizontal (équateur).
        ctx.fill(cx - r, cy, cx + r, cy + 1, color);
    }

    /** ❌ Close (croix) — 3 passes parallèles pour épaisseur ~3px. */
    public static void close(DrawContext ctx, int x, int y, int size, int color) {
        int margin = Math.max(1, size / 6);
        for (int off = -1; off <= 1; off++) {
            DrawHelpers.line(ctx, x + margin + off, y + margin,
                x + size - margin + off, y + size - margin, color);
            DrawHelpers.line(ctx, x + size - margin + off, y + margin,
                x + margin + off, y + size - margin, color);
        }
    }

    /** ‹ Chevron left. */
    public static void chevronLeft(DrawContext ctx, int x, int y, int size, int color) {
        int cx = x + size * 5 / 8;
        int cy = y + size / 2;
        int reach = size / 3;
        DrawHelpers.line(ctx, cx, cy - reach, cx - reach, cy, color);
        DrawHelpers.line(ctx, cx - reach, cy, cx, cy + reach, color);
        DrawHelpers.line(ctx, cx + 1, cy - reach, cx - reach + 1, cy, color);
        DrawHelpers.line(ctx, cx - reach + 1, cy, cx + 1, cy + reach, color);
    }

    /** › Chevron right. */
    public static void chevronRight(DrawContext ctx, int x, int y, int size, int color) {
        int cx = x + size * 3 / 8;
        int cy = y + size / 2;
        int reach = size / 3;
        DrawHelpers.line(ctx, cx, cy - reach, cx + reach, cy, color);
        DrawHelpers.line(ctx, cx + reach, cy, cx, cy + reach, color);
        DrawHelpers.line(ctx, cx + 1, cy - reach, cx + reach + 1, cy, color);
        DrawHelpers.line(ctx, cx + reach + 1, cy, cx + 1, cy + reach, color);
    }

    // ──────────────────────────────────────────────
    // Social (Discord, X, Twitch)
    // ──────────────────────────────────────────────

    /**
     * Discord — silhouette stylisée : capsule avec 2 yeux blancs. Pas un
     * rendu exact du logo officiel (qui est trademark), mais reconnaissable
     * pour usage UI.
     */
    public static void discord(DrawContext ctx, int x, int y, int size, int color) {
        int cx = x + size / 2;
        int cy = y + size / 2;
        int rx = size * 2 / 5;
        int ry = size / 3;
        // Capsule (ellipse remplie).
        for (int dy = -ry; dy <= ry; dy++) {
            int dx = (int) Math.round(rx * Math.sqrt(1 - (dy * dy) / (double) (ry * ry)));
            ctx.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
        // 2 yeux blancs.
        int eyeW = Math.max(1, size / 12);
        int eyeH = Math.max(2, size / 7);
        int eyeY = cy - eyeH / 2;
        int eyeOff = size / 6;
        ctx.fill(cx - eyeOff - eyeW / 2, eyeY, cx - eyeOff + eyeW / 2 + 1, eyeY + eyeH, Colors.WHITE_PURE);
        ctx.fill(cx + eyeOff - eyeW / 2, eyeY, cx + eyeOff + eyeW / 2 + 1, eyeY + eyeH, Colors.WHITE_PURE);
    }

    /** X (Twitter) — deux lignes diagonales croisées. */
    public static void xLogo(DrawContext ctx, int x, int y, int size, int color) {
        int margin = Math.max(1, size / 6);
        DrawHelpers.thickLine(ctx, x + margin, y + margin, x + size - margin, y + size - margin, 2, color);
        DrawHelpers.thickLine(ctx, x + size - margin, y + margin, x + margin, y + size - margin, 2, color);
    }

    /** Twitch — rectangle stylisé avec encoche en bas. */
    public static void twitch(DrawContext ctx, int x, int y, int size, int color) {
        int margin = Math.max(1, size / 8);
        // Rectangle principal.
        ctx.fill(x + margin, y + margin, x + size - margin, y + size - margin * 2, color);
        // Encoche bas-gauche (triangle).
        for (int i = 0; i < size / 4; i++) {
            ctx.fill(x + margin + i, y + size - margin * 2 + i, x + size - margin - i, y + size - margin * 2 + i + 1, color);
        }
        // 2 "yeux" verticaux noirs (style logo officiel).
        int eyeW = Math.max(1, size / 12);
        int eyeH = Math.max(2, size / 4);
        int eyeY = y + size / 3;
        ctx.fill(x + size / 3, eyeY, x + size / 3 + eyeW, eyeY + eyeH, Colors.BACKGROUND);
        ctx.fill(x + size * 5 / 8, eyeY, x + size * 5 / 8 + eyeW, eyeY + eyeH, Colors.BACKGROUND);
    }

    // ──────────────────────────────────────────────
    // Misc utilitaires
    // ──────────────────────────────────────────────

    /** 🔄 Refresh (cercle ouvert avec flèche). */
    public static void refresh(DrawContext ctx, int x, int y, int size, int color) {
        int cx = x + size / 2;
        int cy = y + size / 2;
        int r = size / 2 - 2;
        // Anneau 3/4 (laisse une ouverture en haut-droite).
        DrawHelpers.dashedRing(ctx, cx, cy, r, 1, color, 270f, 90f, 90f);
        // Flèche au point d'ouverture.
        int arrX = cx + r - 1;
        int arrY = cy - 2;
        DrawHelpers.line(ctx, arrX - 2, arrY - 2, arrX, arrY, color);
        DrawHelpers.line(ctx, arrX, arrY, arrX + 2, arrY - 2, color);
    }

    /** ⚠️ Triangle alerte avec point d'exclamation. */
    public static void alertTriangle(DrawContext ctx, int x, int y, int size, int color) {
        int[] tri = triangleRows(size);
        for (int row = 0; row < size; row++) {
            int w = tri[row];
            if (w > 0) {
                int rowX = x + (size - w) / 2;
                // Stroke seulement (1px haut, 1px bas).
                if (row == 0 || row == size - 1) {
                    ctx.fill(rowX, y + row, rowX + w, y + row + 1, color);
                } else {
                    ctx.fill(rowX, y + row, rowX + 1, y + row + 1, color);
                    ctx.fill(rowX + w - 1, y + row, rowX + w, y + row + 1, color);
                }
            }
        }
        // Point d'exclamation au centre.
        int cx = x + size / 2;
        ctx.fill(cx, y + size / 3, cx + 1, y + size * 2 / 3, color);
        ctx.fill(cx, y + size * 3 / 4, cx + 1, y + size * 3 / 4 + 1, color);
    }
}
