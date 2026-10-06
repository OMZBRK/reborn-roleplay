package fr.reborn.hud.ko;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Affichage du KO / de l'ATA dans la DA Reborn (lot KO-6) — remplace les titres, la barre d'action et la barre de
 * boss vanilla quand le serveur sait que le mod est là.
 * <ul>
 *   <li><b>Moments forts</b> (à terre, KO, réveil, hôpital, rétabli, défaite) : bandeau sombre en travers de l'écran,
 *       titre en police Reborn bordé d'or, kanji au-dessus, sous-titre ;</li>
 *   <li><b>À terre</b> : plaque laquée en bas d'écran, sceau 倒, décompte qui se vide en rouge ;</li>
 *   <li><b>Inconscient</b> : presque rien (les paupières sont lourdes) — un mot pâle, un fil d'or qui se vide, et
 *       l'invitation à l'hôpital quand elle arrive ;</li>
 *   <li><b>ATA</b> : petite plaque en haut d'écran, sceau 痛, barre de repos à crans d'or, marque 恐 de la peur.</li>
 * </ul>
 */
public final class KoHud {

    private static final float CARD_IN = 0.2f, CARD_HOLD = 2.1f, CARD_OUT = 0.8f;

    private KoHud() {}

    public static void render(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden()) return;
        int w = mc.getWindow().getGuiScaledWidth();
        int h = mc.getWindow().getGuiScaledHeight();
        Font font = mc.font;
        KoPayload s = KoClient.state();
        if (s != null) {
            if (s.phase() == 1) downed(ctx, font, s, w, h);
            else if (s.phase() == 2) unconscious(ctx, font, s, w, h);
            else if (s.ata() > 0) ata(ctx, font, s, w);
            else if (s.fear()) fearOnly(ctx, font, w);
        }
        card(ctx, font, w, h);
    }

    /* ================================================================ moments forts */

    private static void card(GuiGraphicsExtractor ctx, Font font, int w, int h) {
        float t = KoClient.sinceEvent();
        if (t > CARD_IN + CARD_HOLD + CARD_OUT) return;
        float a = t < CARD_IN ? t / CARD_IN : t < CARD_IN + CARD_HOLD ? 1f : 1f - (t - CARD_IN - CARD_HOLD) / CARD_OUT;
        a = Math.max(0f, Math.min(1f, a));
        String title, sub, kanji;
        int color;
        switch (KoClient.lastEvent()) {
            case KoClient.EV_A_TERRE -> { title = "À TERRE"; sub = "Tu peux encore ramper…"; kanji = "倒"; color = Colors.ACCENT_HOVER; }
            case KoClient.EV_KO -> { title = "K.O."; sub = "Tu perds connaissance."; kanji = "気絶"; color = Colors.ACCENT; }
            case KoClient.EV_REVEIL -> { title = "RÉVEIL"; sub = "Tu reprends conscience."; kanji = "目覚"; color = Colors.GOLD; }
            case KoClient.EV_HOPITAL -> { title = "HÔPITAL"; sub = "On t'a ramené. Tu es encore très faible."; kanji = "病院"; color = Colors.FOREGROUND; }
            case KoClient.EV_RETABLI -> { title = "RÉTABLI"; sub = "Tes forces sont revenues."; kanji = "回復"; color = Colors.GOLD; }
            case KoClient.EV_DEFAITE -> { title = "DÉFAITE"; sub = "Relève-toi, l'entraînement continue."; kanji = "敗"; color = Colors.GOLD; }
            default -> { return; }
        }
        int cy = Math.round(h * 0.34f);
        // bandeau sombre en travers de l'écran
        int band = Math.round(a * 150);
        DrawHelpers.horizontalGradient(ctx, 0, cy - 30, w / 2, 62, 0, band << 24 | 0x050304);
        DrawHelpers.horizontalGradient(ctx, w / 2, cy - 30, w - w / 2, 62, band << 24 | 0x050304, 0);

        Component tc = RebornFont.display(title);
        float scale = 3f;
        int tw = Math.round(font.width(tc) * scale);
        int alpha = Math.round(a * 255) << 24;
        // kanji (police du jeu : elle a les idéogrammes)
        Component kc = Component.literal(kanji);
        int kw = font.width(kc);
        ctx.pose().pushMatrix();
        ctx.pose().translate(w / 2f - kw * 1.5f / 2f, cy - 27);
        ctx.pose().scale(1.5f, 1.5f);
        ctx.text(font, kc, 0, 0, alpha | (Colors.GOLD & 0xFFFFFF), false);
        ctx.pose().popMatrix();
        // titre bordé
        ctx.pose().pushMatrix();
        ctx.pose().translate(w / 2f - tw / 2f, cy - 12);
        ctx.pose().scale(scale, scale);
        int outline = Math.round(a * 220) << 24 | 0x0A0608;
        ctx.text(font, tc, -1, 0, outline, false);
        ctx.text(font, tc, 1, 0, outline, false);
        ctx.text(font, tc, 0, -1, outline, false);
        ctx.text(font, tc, 0, 1, outline, false);
        ctx.text(font, tc, 0, 0, alpha | (color & 0xFFFFFF), false);
        ctx.pose().popMatrix();
        // filets d'or de part et d'autre
        int gold = alpha | (Colors.GOLD & 0xFFFFFF);
        int ry = cy - 1;
        int len = Math.round(60 * Math.min(1f, t / 0.5f));
        ctx.fill(w / 2 - tw / 2 - 10 - len, ry, w / 2 - tw / 2 - 10, ry + 1, gold);
        ctx.fill(w / 2 + tw / 2 + 10, ry, w / 2 + tw / 2 + 10 + len, ry + 1, gold);
        diamond(ctx, w / 2 - tw / 2 - 12 - len, ry, gold);
        diamond(ctx, w / 2 + tw / 2 + 12 + len, ry, gold);
        // sous-titre
        Component sc = RebornFont.body(sub);
        int sw = font.width(sc);
        ctx.text(font, sc, w / 2 - sw / 2, cy + 18, alpha | (Colors.FOREGROUND_SUBTLE & 0xFFFFFF), true);
    }

    private static void diamond(GuiGraphicsExtractor ctx, int x, int y, int color) {
        ctx.fill(x - 1, y - 1, x + 2, y + 2, color);
        ctx.fill(x, y - 2, x + 1, y + 3, color);
        ctx.fill(x - 2, y, x + 3, y + 1, color);
    }

    /* ================================================================ à terre */

    private static void downed(GuiGraphicsExtractor ctx, Font font, KoPayload s, int w, int h) {
        float left = Math.max(0f, s.left() - KoClient.sinceState());
        float frac = s.total() > 0 ? left / s.total() : 0f;
        float pulse = KoClient.pulse();
        int pw = 210, ph = 40;
        int x = w / 2 - pw / 2, y = Math.round(h * 0.60f);   // sous le viseur, au-dessus du chat
        // plaque laquée, bord d'or, lueur rouge qui bat
        int glow = Math.round(40 + 90 * pulse);
        DrawHelpers.rect(ctx, x - 2, y - 2, pw + 4, ph + 4, glow << 24 | (Colors.ACCENT & 0xFFFFFF));
        DrawHelpers.outlinedRect(ctx, x, y, pw, ph, 0xE6150A0D, Colors.GOLD);
        DrawHelpers.outlinedRect(ctx, x + 2, y + 2, pw - 4, ph - 4, 0, 0x803A2C14);
        // sceau 倒
        seal(ctx, font, x + 6, y + 6, 28, "倒", Colors.ACCENT, Colors.FOREGROUND);
        // libellé + décompte
        ctx.text(font, RebornFont.display("À TERRE"), x + 42, y + 8, Colors.ACCENT_HOVER, true);
        Component secs = RebornFont.display(Math.round(Math.ceil(left)) + " s");
        ctx.text(font, secs, x + pw - 10 - font.width(secs), y + 8, Colors.GOLD, true);
        // jauge qui se vide
        int bx = x + 42, by = y + 24, bw = pw - 52, bh = 6;
        DrawHelpers.rect(ctx, bx, by, bw, bh, 0x80000000);
        DrawHelpers.horizontalGradient(ctx, bx, by, Math.max(1, Math.round(bw * frac)), bh, Colors.ACCENT_PRESSED, Colors.ACCENT_HOVER);
        for (int i = 1; i < 6; i++) ctx.fill(bx + bw * i / 6, by, bx + bw * i / 6 + 1, by + bh, 0x60000000);
        // conseil
        Component hint = RebornFont.body("Tu peux ramper · /aide pour appeler à l'aide");
        ctx.text(font, hint, w / 2 - font.width(hint) / 2, y + ph + 5, Colors.FOREGROUND_SUBTLE, true);
    }

    /* ================================================================ inconscient */

    private static void unconscious(GuiGraphicsExtractor ctx, Font font, KoPayload s, int w, int h) {
        float left = Math.max(0f, s.left() - KoClient.sinceState());
        float frac = s.total() > 0 ? left / s.total() : 0f;
        boolean chakra = s.cause() == 1;
        int accent = chakra ? 0xFF6FA8E8 : Colors.ACCENT_HOVER;
        int cy = Math.round(h * 0.50f);
        Component label = RebornFont.display(chakra ? "ÉPUISEMENT" : "INCONSCIENT");
        int lw = Math.round(font.width(label) * 2f);
        ctx.pose().pushMatrix();
        ctx.pose().translate(w / 2f - lw / 2f, cy);
        ctx.pose().scale(2f, 2f);
        ctx.text(font, label, 0, 0, 0xB0F5E9D0, false);
        ctx.pose().popMatrix();
        // fil d'or qui se vide
        int bw = 170, bx = w / 2 - bw / 2, by = cy + 22;
        ctx.fill(bx, by, bx + bw, by + 1, 0x60D9A95E);
        int fill = Math.round(bw * frac);
        ctx.fill(w / 2 - fill / 2, by, w / 2 + fill / 2, by + 1, Colors.GOLD);
        diamond(ctx, w / 2, by, accent);
        int m = (int) Math.ceil(left) / 60, sec = (int) Math.ceil(left) % 60;
        Component time = RebornFont.display(String.format("%d:%02d", m, sec));
        ctx.text(font, time, w / 2 - font.width(time) / 2, by + 6, Colors.GOLD, true);
        Component hint = RebornFont.body("Tu peux chuchoter et faire des /me");
        ctx.text(font, hint, w / 2 - font.width(hint) / 2, by + 19, 0xA0C2B59A, true);
        if (s.hospital()) {
            float blink = 0.65f + 0.35f * (float) Math.sin(System.currentTimeMillis() / 400.0);
            Component hosp = RebornFont.body("[ /hopital ]  Se laisser emmener à l'hôpital");
            int a = Math.round(255 * blink);
            ctx.text(font, hosp, w / 2 - font.width(hosp) / 2, by + 34, a << 24 | (Colors.GOLD & 0xFFFFFF), true);
        }
    }

    /* ================================================================ ATA */

    private static void ata(GuiGraphicsExtractor ctx, Font font, KoPayload s, int w) {
        boolean full = s.ata() == 2;
        int pw = 236, ph = 32;
        int x = w / 2 - pw / 2, y = 5;
        DrawHelpers.outlinedRect(ctx, x, y, pw, ph, 0xD9150A0D, full ? Colors.ACCENT : Colors.GOLD);
        // cordons de suspension (kit : plaque suspendue)
        ctx.fill(x + 24, 0, x + 25, y, Colors.GOLD);
        ctx.fill(x + pw - 25, 0, x + pw - 24, y, Colors.GOLD);
        seal(ctx, font, x + 5, y + 5, 22, "痛", full ? Colors.ACCENT : 0xFF8A6A2A, Colors.FOREGROUND);
        Component title = RebornFont.body(full ? "ATA · Grièvement blessé" : "ATA · Blessé");
        ctx.text(font, title, x + 33, y + 5, Colors.FOREGROUND, true);
        if (s.fear()) {
            Component fear = Component.literal("恐");
            int fx = x + pw - 18;
            DrawHelpers.outlinedRect(ctx, fx - 2, y + 3, 14, 12, 0x803A0A12, Colors.ACCENT);
            ctx.text(font, fear, fx + 1, y + 5, Colors.ACCENT_HOVER, false);
        }
        // barre de repos à 10 crans d'or
        int bx = x + 33, by = y + 17, bw = pw - 33 - 8, seg = 10, gap = 2;
        int sw = (bw - gap * (seg - 1)) / seg;
        int filled = Math.round(s.rest() * seg * 10f);   // dixièmes de cran
        for (int i = 0; i < seg; i++) {
            int sx = bx + i * (sw + gap);
            DrawHelpers.rect(ctx, sx, by, sw, 4, 0x70000000);
            int part = Math.max(0, Math.min(10, filled - i * 10));
            if (part > 0) DrawHelpers.rect(ctx, sx, by, Math.max(1, sw * part / 10), 4,
                    s.resting() ? 0xFF7FD69A : Colors.GOLD);
        }
        String detail = "Repos " + s.restMin() + "/" + s.restRequired() + " min"
                + (s.resting() ? " · en cours" : "") + " · ou un médic";
        Component dc = RebornFont.body(detail);
        ctx.pose().pushMatrix();
        ctx.pose().translate(bx, by + 6);
        ctx.pose().scale(0.75f, 0.75f);
        ctx.text(font, dc, 0, 0, Colors.FOREGROUND_SUBTLE, true);
        ctx.pose().popMatrix();
    }

    private static void fearOnly(GuiGraphicsExtractor ctx, Font font, int w) {
        Component c = Component.literal("恐").append(RebornFont.body("  La peur te tient encore"));
        int cw = font.width(c) + 12;
        int x = w / 2 - cw / 2;
        DrawHelpers.outlinedRect(ctx, x, 5, cw, 15, 0xC0150A0D, Colors.ACCENT);
        ctx.text(font, c, x + 6, 9, Colors.FOREGROUND_SUBTLE, true);
    }

    /* ================================================================ commun */

    /** Sceau carré laqué avec un idéogramme centré (police du jeu). */
    private static void seal(GuiGraphicsExtractor ctx, Font font, int x, int y, int size, String glyph, int fill, int ink) {
        DrawHelpers.rect(ctx, x, y, size, size, fill);
        DrawHelpers.outlinedRect(ctx, x + 1, y + 1, size - 2, size - 2, 0, 0x60F5E9D0);
        Component g = Component.literal(glyph);
        float sc = size >= 26 ? 2f : 1.5f;
        int gw = Math.round(font.width(g) * sc);
        ctx.pose().pushMatrix();
        ctx.pose().translate(x + size / 2f - gw / 2f, y + size / 2f - 4f * sc);
        ctx.pose().scale(sc, sc);
        ctx.text(font, g, 0, 0, ink, false);
        ctx.pose().popMatrix();
    }
}
