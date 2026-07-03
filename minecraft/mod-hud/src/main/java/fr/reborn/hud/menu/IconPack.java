package fr.reborn.hud.menu;

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

    /** ▶ Triangle play (rempli pointant à droite). */
    public static void play(DrawContext ctx, int x, int y, int size, int color) {
        int marginX = Math.max(1, size / 5);
        int marginY = Math.max(1, size / 7);
        int tipX = x + size - marginX;
        int baseX = x + marginX;
        int topY = y + marginY;
        int botY = y + size - marginY;
        int rows = botY - topY;
        for (int row = 0; row < rows; row++) {
            float t = Math.abs((row - rows / 2f) / (rows / 2f));
            int rowW = Math.round((1f - t) * (tipX - baseX));
            if (rowW < 1) rowW = 1;
            ctx.fill(baseX, topY + row, baseX + rowW, topY + row + 1, color);
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

    /** 🔉 Speaker simple (rectangle haut-parleur + cône + 2 arcs de son). */
    public static void volume(DrawContext ctx, int x, int y, int size, int color) {
        int mid = y + size / 2;

        // Corps rectangulaire du haut-parleur (à gauche).
        int bodyX0 = x + Math.max(1, size / 8);
        int bodyX1 = x + size / 3;
        int bodyTop = mid - size / 7;
        int bodyBot = mid + size / 7;
        ctx.fill(bodyX0, bodyTop, bodyX1, bodyBot + 1, color);

        // Cône triangulaire qui s'élargit vers la droite.
        int coneStart = bodyX1;
        int coneEnd = x + size * 5 / 9;
        int coneLen = coneEnd - coneStart;
        for (int i = 0; i <= coneLen; i++) {
            float t = i / (float) Math.max(1, coneLen);
            int spread = Math.round(t * (size * 3 / 8));
            ctx.fill(coneStart + i, mid - spread, coneStart + i + 1, mid + spread + 1, color);
        }

        // 2 arcs de son courbes — petit (proche) et grand (loin).
        int arcCx = coneEnd;
        int arcCy = mid;
        // Petit arc 90° (étroit côté droit) — épaisseur 1.
        DrawHelpers.dashedRing(ctx, arcCx, arcCy, size / 5 + 1, 1, color, 90f, 270f, -45f);
        // Grand arc, plus loin.
        DrawHelpers.dashedRing(ctx, arcCx, arcCy, size / 3 + 1, 1, color, 90f, 270f, -45f);
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

    /**
     * ⚙️ Settings — cog wheel à 8 dents équidistantes, anneau central
     * creux + petit disque au cœur.
     */
    public static void settings(DrawContext ctx, int x, int y, int size, int color) {
        int cx = x + size / 2;
        int cy = y + size / 2;
        int ringR = size * 3 / 8;
        int ringThickness = Math.max(1, size / 8);

        // 8 dents tout autour, espacées de 45°.
        int toothLen = Math.max(2, size / 7);
        int toothW = Math.max(1, size / 9);
        for (int i = 0; i < 8; i++) {
            double angle = Math.toRadians(i * 45);
            double dx = Math.cos(angle);
            double dy = Math.sin(angle);
            int innerR = ringR - 1;
            int outerR = ringR + toothLen;
            // Approximation : petit rectangle perpendiculaire à l'angle.
            int midR = (innerR + outerR) / 2;
            int midX = cx + (int) Math.round(dx * midR);
            int midY = cy + (int) Math.round(dy * midR);
            // Rectangle plus large perpendiculairement à (dx, dy).
            int px = -(int) Math.round(dy * toothW / 2);
            int py = (int) Math.round(dx * toothW / 2);
            for (int k = -(outerR - innerR) / 2; k <= (outerR - innerR) / 2; k++) {
                int sx = midX + (int) Math.round(dx * k);
                int sy = midY + (int) Math.round(dy * k);
                ctx.fill(sx + px, sy + py, sx + px + 1, sy + py + 1, color);
                ctx.fill(sx - px, sy - py, sx - px + 1, sy - py + 1, color);
                ctx.fill(sx, sy, sx + 1, sy + 1, color);
            }
        }

        // Anneau extérieur de l'engrenage.
        DrawHelpers.ring(ctx, cx, cy, ringR, ringThickness, color);
        // Trou central (creux noir).
        DrawHelpers.disc(ctx, cx, cy, Math.max(2, size / 6), Colors.BACKGROUND);
        // Cœur central plein.
        DrawHelpers.disc(ctx, cx, cy, Math.max(1, size / 10), color);
    }

    /** 🌐 Globe — cercle + équateur + 2 méridiens (ellipses étroites). */
    public static void globe(DrawContext ctx, int x, int y, int size, int color) {
        int cx = x + size / 2;
        int cy = y + size / 2;
        int r = size / 2 - 1;
        // Anneau extérieur 1px.
        DrawHelpers.ring(ctx, cx, cy, r, 1, color);
        // Equateur (ligne horizontale 1px).
        ctx.fill(cx - r, cy, cx + r + 1, cy + 1, color);
        // Tropique nord (ligne horizontale demi-largeur, plus haut).
        int tropicY = cy - r / 2;
        int tropicW = (int) Math.round(Math.sqrt(r * r - (r / 2.0) * (r / 2.0)));
        ctx.fill(cx - tropicW, tropicY, cx + tropicW + 1, tropicY + 1, color);
        // Tropique sud.
        int tropicY2 = cy + r / 2;
        ctx.fill(cx - tropicW, tropicY2, cx + tropicW + 1, tropicY2 + 1, color);
        // Méridien central (ellipse étroite : ligne verticale au centre).
        ctx.fill(cx, cy - r, cx + 1, cy + r + 1, color);
        // 2 méridiens latéraux — arcs verticaux à demi-largeur.
        int meridianX = r / 2;
        for (int dy = -r; dy <= r; dy++) {
            float t = dy / (float) r;
            // Demi-cercle aplati : largeur varie en sqrt(1 - t²).
            int meridianHalf = (int) Math.round(meridianX * Math.sqrt(Math.max(0, 1 - t * t)));
            if (meridianHalf > 0) {
                ctx.fill(cx - meridianHalf, cy + dy, cx - meridianHalf + 1, cy + dy + 1, color);
                ctx.fill(cx + meridianHalf, cy + dy, cx + meridianHalf + 1, cy + dy + 1, color);
            }
        }
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
     * Discord — silhouette mascotte façon logo officiel : capsule arrondie
     * avec sourire impliciste, 2 yeux ovales noirs, et 2 petits "pieds"
     * (corner bumps) en bas. Pas une copie pixel-perfect du logo trademark,
     * mais visuellement reconnaissable.
     */
    public static void discord(DrawContext ctx, int x, int y, int size, int color) {
        int cx = x + size / 2;
        int cy = y + size / 2;
        int bodyRx = size * 9 / 20;
        int bodyRy = size * 3 / 8;

        // Corps : ellipse remplie (capsule horizontale).
        for (int dy = -bodyRy; dy <= bodyRy; dy++) {
            float t = dy / (float) bodyRy;
            int dx = (int) Math.round(bodyRx * Math.sqrt(Math.max(0, 1 - t * t)));
            ctx.fill(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }

        // 2 petits "pieds" en bas (asymétrie iconique du logo).
        int footY = cy + bodyRy - 1;
        int footH = Math.max(2, size / 8);
        int footW = Math.max(2, size / 7);
        int footOff = size / 5;
        ctx.fill(cx - footOff - footW / 2, footY, cx - footOff + footW / 2 + 1, footY + footH, color);
        ctx.fill(cx + footOff - footW / 2, footY, cx + footOff + footW / 2 + 1, footY + footH, color);

        // 2 yeux ovales (en couleur de fond pour effet "trou").
        int eyeW = Math.max(2, size / 9);
        int eyeH = Math.max(3, size / 5);
        int eyeY = cy - eyeH / 2 - 1;
        int eyeOff = size / 5;
        // Eyes en BACKGROUND pour contraster sur le body.
        for (int dy = 0; dy < eyeH; dy++) {
            float t = (dy - eyeH / 2f) / (eyeH / 2f);
            int dx = (int) Math.round((eyeW / 2f) * Math.sqrt(Math.max(0, 1 - t * t)));
            ctx.fill(cx - eyeOff - dx, eyeY + dy, cx - eyeOff + dx + 1, eyeY + dy + 1,
                Colors.BACKGROUND);
            ctx.fill(cx + eyeOff - dx, eyeY + dy, cx + eyeOff + dx + 1, eyeY + dy + 1,
                Colors.BACKGROUND);
        }
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

    // ──────────────────────────────────────────────
    // Onglets Paramètres
    // ──────────────────────────────────────────────

    /** 🖥️ Moniteur (écran + pied) — onglet Vidéo. */
    public static void monitor(DrawContext ctx, int x, int y, int size, int color) {
        int mx = x + size / 8, my = y + size / 6;
        int mw = size - 2 * (size / 8), mh = size * 11 / 20;
        DrawHelpers.roundedOutlinedRect(ctx, mx, my, mw, mh, Math.max(1, size / 10),
            Colors.BACKGROUND, color);
        int cx = x + size / 2;
        ctx.fill(cx - 1, my + mh, cx + 1, my + mh + size / 8, color);
        ctx.fill(cx - size / 6, my + mh + size / 8, cx + size / 6,
            my + mh + size / 8 + Math.max(1, size / 12), color);
    }

    /** ⌨️ Clavier (cadre + touches) — onglet Contrôles. */
    public static void keyboard(DrawContext ctx, int x, int y, int size, int color) {
        int kx = x + size / 10, ky = y + size / 4;
        int kw = size - 2 * (size / 10), kh = size / 2;
        DrawHelpers.roundedOutlinedRect(ctx, kx, ky, kw, kh, Math.max(1, size / 10),
            Colors.BACKGROUND, color);
        int dot = Math.max(1, size / 12);
        for (int r = 0; r < 2; r++) {
            for (int c = 0; c < 3; c++) {
                int dx = kx + kw * (c + 1) / 4 - dot / 2;
                int dy = ky + kh * (r + 1) / 3 - dot / 2;
                ctx.fill(dx, dy, dx + dot, dy + dot, color);
            }
        }
    }

    /** 💬 Bulle de chat — onglet Chat. */
    public static void chatBubble(DrawContext ctx, int x, int y, int size, int color) {
        int bx = x + size / 8, by = y + size / 6;
        int bw = size - 2 * (size / 8), bh = size * 9 / 20;
        DrawHelpers.roundedOutlinedRect(ctx, bx, by, bw, bh, Math.max(2, size / 6),
            Colors.BACKGROUND, color);
        int tx = bx + bw / 4;
        for (int i = 0; i < size / 6; i++) {
            ctx.fill(tx + i, by + bh, tx + i + 1, by + bh + (size / 6 - i), color);
        }
        int dot = Math.max(1, size / 12);
        int dy = by + bh / 2 - dot / 2;
        ctx.fill(bx + bw / 3 - dot / 2, dy, bx + bw / 3 + dot / 2, dy + dot, color);
        ctx.fill(bx + 2 * bw / 3 - dot / 2, dy, bx + 2 * bw / 3 + dot / 2, dy + dot, color);
    }

    /** ✛ Viseur (anneau + ticks + point) — onglet Viseur. */
    public static void crosshair(DrawContext ctx, int x, int y, int size, int color) {
        int cx = x + size / 2, cy = y + size / 2, r = size / 2 - 2;
        DrawHelpers.ring(ctx, cx, cy, r, 1, color);
        int len = size / 4;
        ctx.fill(cx, cy - r, cx + 1, cy - r + len, color);
        ctx.fill(cx, cy + r - len, cx + 1, cy + r, color);
        ctx.fill(cx - r, cy, cx - r + len, cy + 1, color);
        ctx.fill(cx + r - len, cy, cx + r, cy + 1, color);
        DrawHelpers.disc(ctx, cx, cy, Math.max(1, size / 12), color);
    }

    /** 👤 Utilisateur (tête + épaules) — onglet Compte. */
    public static void user(DrawContext ctx, int x, int y, int size, int color) {
        int cx = x + size / 2;
        DrawHelpers.disc(ctx, cx, y + size / 3, size / 6, color);
        int shR = size * 2 / 5, shCy = y + size - 1;
        for (int dy = -shR; dy <= 0; dy++) {
            int w = (int) Math.round(shR * Math.sqrt(Math.max(0, 1 - ((double) dy / shR) * ((double) dy / shR))));
            ctx.fill(cx - w, shCy + dy, cx + w + 1, shCy + dy + 1, color);
        }
    }

    /** ⬛ Bloc (façon grass block) — onglet Minecraft. */
    public static void cube(DrawContext ctx, int x, int y, int size, int color) {
        int m = size / 6;
        int bx = x + m, by = y + m, bw = size - 2 * m, bh = size - 2 * m;
        DrawHelpers.roundedOutlinedRect(ctx, bx, by, bw, bh, 1, Colors.BACKGROUND, color);
        ctx.fill(bx, by, bx + bw, by + bh / 3, color);
    }

    /** ▟ Layout HUD (cadre + élément) — onglet HUD. */
    public static void layout(DrawContext ctx, int x, int y, int size, int color) {
        int m = size / 6;
        int bx = x + m, by = y + m, bw = size - 2 * m, bh = size - 2 * m;
        DrawHelpers.roundedOutlinedRect(ctx, bx, by, bw, bh, 1, Colors.BACKGROUND, color);
        int ew = bw / 3, eh = bh / 4;
        ctx.fill(bx + 2, by + 2, bx + 2 + ew, by + 2 + eh, color);
        ctx.fill(bx + 2, by + bh - 2 - eh, bx + bw - 2, by + bh - 2, color);
    }

    /** 📷 Caméra (corps + objectif + bosse viseur) — onglet Caméra (vue 3e pers). */
    public static void camera(DrawContext ctx, int x, int y, int size, int color) {
        int bx = x + size / 8, bw = size - 2 * (size / 8);
        int by = y + size * 5 / 16, bh = size * 7 / 16;
        // Bosse viseur au-dessus du corps.
        int bumpW = Math.max(3, bw / 3), bumpH = Math.max(2, size / 8);
        ctx.fill(bx + bw / 6, by - bumpH, bx + bw / 6 + bumpW, by, color);
        // Corps de l'appareil.
        DrawHelpers.roundedOutlinedRect(ctx, bx, by, bw, bh, Math.max(1, size / 10),
            Colors.BACKGROUND, color);
        // Objectif centré.
        int cx = x + size / 2, cy = by + bh / 2;
        int r = Math.max(2, bh / 3);
        DrawHelpers.ring(ctx, cx, cy, r, Math.max(1, size / 12), color);
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
