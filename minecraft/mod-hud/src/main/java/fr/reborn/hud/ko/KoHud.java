package fr.reborn.hud.ko;

import fr.reborn.hud.menu.Colors;
import fr.reborn.hud.menu.DrawHelpers;
import fr.reborn.hud.menu.RebornFont;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.LotGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Affichage du KO / de l'ATA dans la DA Reborn (lot KO-6), <b>version discrète</b> (retour du 13/10 : « faut que ce
 * soit subtil ») — pas de plaque ni de bandeau épais : kanji de la police du jeu, texte du kit ({@link DaKit}) ombré
 * d'un pixel, fils de jauge de 1 à 2 px. Remplace les titres, la barre d'action et la barre de boss vanilla quand le
 * serveur sait que le mod est là.
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

    /** Haut du bloc « À terre » à la dernière image (la Volonté pose son invitation juste au-dessus). */
    private static int dernierY = -1;

    public static int dernierYATerre() { return dernierY; }

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
            else if (s.ata() > 0) ata(ctx, font, s, w, h);
            else if (s.fear()) fearOnly(ctx, font, w);
        }
        card(ctx, font, w, h);
    }

    /* ================================================================ moments forts */

    /**
     * Carte d'un moment fort, discrète : voile horizontal très léger, kanji de la police du jeu, titre ArcadePix
     * à la taille du texte, deux filets d'or qui s'étirent, sous-titre Inter 7. Rien de plus.
     */
    private static void card(GuiGraphicsExtractor ctx, Font font, int w, int h) {
        float t = KoClient.sinceEvent();
        if (t > CARD_IN + CARD_HOLD + CARD_OUT) return;
        float a = t < CARD_IN ? t / CARD_IN : t < CARD_IN + CARD_HOLD ? 1f : 1f - (t - CARD_IN - CARD_HOLD) / CARD_OUT;
        a = Math.max(0f, Math.min(1f, a));
        String title, sub, kanji;
        int color;
        switch (KoClient.lastEvent()) {
            case KoClient.EV_A_TERRE -> { title = "À terre"; sub = "Tu peux encore ramper…"; kanji = "倒"; color = DaKit.ROUGE_CLAIR; }
            case KoClient.EV_KO -> { title = "K.O."; sub = "Tu perds connaissance."; kanji = "気絶"; color = DaKit.ROUGE_CLAIR; }
            case KoClient.EV_REVEIL -> { title = "Réveil"; sub = "Tu reprends conscience."; kanji = "目覚"; color = DaKit.OR_CLAIR; }
            case KoClient.EV_HOPITAL -> { title = "Hôpital"; sub = "On t'a ramené. Tu es encore très faible."; kanji = "病院"; color = DaKit.CREME; }
            case KoClient.EV_RETABLI -> { title = "Rétabli"; sub = "Tes forces sont revenues."; kanji = "回復"; color = DaKit.OR_CLAIR; }
            case KoClient.EV_RELEVE -> { title = "Second souffle"; sub = "Debout… mais ton corps est à bout."; kanji = "志"; color = DaKit.OR_CLAIR; }
            case KoClient.EV_DEFAITE -> { title = "Défaite"; sub = "Relève-toi, l'entraînement continue."; kanji = "敗"; color = DaKit.OR_CLAIR; }
            default -> { return; }
        }
        // inconscient : le bloc du milieu d'écran peut remonter (chat) → la carte passe plus haut
        KoPayload st = KoClient.state();
        int cy = Math.round(h * (st != null && st.phase() == 2 ? 0.17f : 0.30f));
        // voile très léger derrière le texte (deux dégradés = deux éléments)
        int band = Math.round(a * 80);
        DrawHelpers.horizontalGradient(ctx, w / 2 - 160, cy - 18, 160, 42, 0, band << 24 | 0x050304);
        DrawHelpers.horizontalGradient(ctx, w / 2, cy - 18, 160, 42, band << 24 | 0x050304, 0);

        Component kc = Component.literal(kanji);
        ctx.text(font, kc, w / 2 - font.width(kc) / 2, cy - 14, DaKit.alpha(DaKit.OR, a), true);
        Component tc = DaKit.arcade(title);
        int tw = font.width(tc);
        DaKit.arcX1(ctx, font, tc, w / 2f, cy - 2, DaKit.alpha(color, a), 1, true);
        // filets d'or qui s'étirent
        int gold = DaKit.alpha(DaKit.OR, a * 0.9f);
        int len = Math.round(36 * Math.min(1f, t / 0.5f));
        int ry = cy + 1;
        LotGui l = LotGui.ouvrir(ctx);
        l.fill(w / 2 - tw / 2 - 8 - len, ry, w / 2 - tw / 2 - 8, ry + 1, gold);
        l.fill(w / 2 + tw / 2 + 8, ry, w / 2 + tw / 2 + 8 + len, ry + 1, gold);
        diamond(l, w / 2 - tw / 2 - 10 - len, ry, gold);
        diamond(l, w / 2 + tw / 2 + 10 + len, ry, gold);
        l.fermer();
        DaKit.inter7o(ctx, font, DaKit.inter(sub), w / 2f, cy + 13, DaKit.alpha(DaKit.CREME_VAL, a), 1);
    }

    private static void diamond(LotGui l, int x, int y, int color) {
        l.fill(x - 1, y, x + 2, y + 1, color);
        l.fill(x, y - 1, x + 1, y + 2, color);
    }

    /* ================================================================ à terre */

    /** À terre : une ligne (kanji 倒, « À terre », secondes), un fil rouge qui se vide, le conseil. Aucun cadre. */
    private static void downed(GuiGraphicsExtractor ctx, Font font, KoPayload s, int w, int h) {
        float left = Math.max(0f, s.left() - KoClient.sinceState());
        float frac = s.total() > 0 ? left / s.total() : 0f;
        float pulse = KoClient.pulse();
        int pw = 150, ph = 26;
        int x = w / 2 - pw / 2, y = Math.round(h * 0.60f);   // sous le viseur, au-dessus du chat
        // le chat (déplaçable, plus large aux grandes échelles) ne doit pas passer sous le bloc
        int[] chat = box(fr.reborn.hud.element.HudElement.CHAT, w, h);
        if (chat != null && hits(x, y, pw, ph, chat)) {
            int above = chat[1] - ph - 4;
            if (above >= h / 2 + 14) y = above;
            else if (chat[0] + chat[2] + 6 + pw <= w - 4) x = Math.max(x, chat[0] + chat[2] + 6);
            else y = Math.max(h / 2 + 14, above);
        }
        dernierY = y;
        Component k = Component.literal("倒");
        ctx.text(font, k, x, y, DaKit.ROUGE_CLAIR, true);
        DaKit.inter9o(ctx, font, DaKit.inter("À terre"), x + 12, y + 4, DaKit.CREME, 0);
        DaKit.inter9o(ctx, font, DaKit.inter(Math.round(Math.ceil(left)) + " s"), x + pw, y + 4, DaKit.OR_CLAIR, 2);
        // fil qui se vide (2 px), plus vif au battement
        int by = y + 11, fw = Math.max(1, Math.round(pw * frac));
        LotGui l = LotGui.ouvrir(ctx);
        l.fill(x, by, x + pw, by + 2, 0x50000000);
        l.fill(x, by, x + fw, by + 2, DaKit.alpha(0xFFC01E35, 0.7f + 0.3f * pulse));
        l.fill(x, by, x + fw, by + 1, DaKit.alpha(0xFFE26474, 0.6f + 0.4f * pulse));
        l.fermer();
        String hint = "Tu peux ramper · /aide pour appeler à l'aide" + (VolonteHud.available() ? " · Espace pour te relever" : "");
        float hw = DaKit.largeur7(font, DaKit.inter(hint)) / 2f;
        DaKit.inter7o(ctx, font, DaKit.inter(hint), Math.max(4 + hw, Math.min(w - 4 - hw, x + pw / 2f)), by + 9,
            DaKit.CREME_VAL, 1);
    }

    /* ================================================================ inconscient */

    private static void unconscious(GuiGraphicsExtractor ctx, Font font, KoPayload s, int w, int h) {
        float left = Math.max(0f, s.left() - KoClient.sinceState());
        float frac = s.total() > 0 ? left / s.total() : 0f;
        boolean chakra = s.cause() == 1;
        int accent = chakra ? 0xFF6FA8E8 : DaKit.ROUGE_CLAIR;
        int cy = Math.round(h * 0.50f);
        // le bloc (mot, fil, temps, invitation) remonte s'il tombe sur le chat
        int[] chat = box(fr.reborn.hud.element.HudElement.CHAT, w, h);
        if (chat != null && hits(w / 2 - 110, cy, 220, 56, chat)) cy = Math.max(Math.round(h * 0.28f), chat[1] - 60);
        DaKit.arcX1(ctx, font, DaKit.arcade(chakra ? "Épuisement" : "Inconscient"), w / 2f, cy, 0xB0F5E9D0, 1, true);
        // fil d'or qui se vide
        int bw = 140, bx = w / 2 - bw / 2, by = cy + 12;
        int fill = Math.round(bw * frac);
        LotGui l = LotGui.ouvrir(ctx);
        l.fill(bx, by, bx + bw, by + 1, 0x50D9A95E);
        l.fill(w / 2 - fill / 2, by, w / 2 + fill / 2, by + 1, DaKit.OR);
        diamond(l, w / 2, by, accent);
        l.fermer();
        int m = (int) Math.ceil(left) / 60, sec = (int) Math.ceil(left) % 60;
        if (left > 0 || !s.hospital()) {
            DaKit.inter9o(ctx, font, DaKit.inter(String.format("%d:%02d", m, sec)), w / 2f, by + 9, DaKit.OR_CLAIR, 1);
        }
        DaKit.inter7o(ctx, font, DaKit.inter("Tu peux chuchoter et faire des /me"), w / 2f, by + 20, 0xA0C2B59A, 1);
        if (s.hospital()) {
            float blink = 0.65f + 0.35f * (float) Math.sin(System.currentTimeMillis() / 400.0);
            DaKit.inter7o(ctx, font, DaKit.inter("/hopital — se laisser emmener à l'hôpital"), w / 2f, by + 31,
                DaKit.alpha(DaKit.OR_CLAIR, blink), 1);
        }
    }

    /* ================================================================ ATA */

    /** ATA : une ligne en haut d'écran (kanji 痛, état, peur 恐), dix crans d'or fins, le détail. Voile léger. */
    private static void ata(GuiGraphicsExtractor ctx, Font font, KoPayload s, int w, int h) {
        boolean full = s.ata() == 2;
        int pw = 160, ph = 26;
        int x = w / 2 - pw / 2, y = 6;
        // le panneau vitals (haut-gauche par défaut) garde sa place : on se pousse à droite, sinon dessous
        int[] vit = box(fr.reborn.hud.element.HudElement.VITALS, w, h);
        if (vit != null && hits(x, y, pw, ph, vit)) {
            if (vit[0] + vit[2] + 6 + pw <= w - 4) x = vit[0] + vit[2] + 6;
            else y = vit[1] + vit[3] + 6;
        }
        DrawHelpers.horizontalGradient(ctx, x - 12, y - 3, pw / 2 + 12, ph + 4, 0, 0x50050304);
        DrawHelpers.horizontalGradient(ctx, x + pw / 2, y - 3, pw / 2 + 12, ph + 4, 0x50050304, 0);
        ctx.text(font, Component.literal("痛"), x, y, full ? DaKit.ROUGE_CLAIR : DaKit.OR, true);
        DaKit.inter8(ctx, font, DaKit.inter(full ? "Grièvement blessé" : "Blessé"), x + 12, y + 4, DaKit.CREME, 0);
        if (s.fear()) {
            Component fear = Component.literal("恐");
            ctx.text(font, fear, x + pw - font.width(fear), y, DaKit.ROUGE_CLAIR, true);
        }
        // dix crans de repos, fins
        int by = y + 11, seg = 10, gap = 2;
        int sw = (pw - gap * (seg - 1)) / seg;
        int filled = Math.round(s.rest() * seg * 10f);   // dixièmes de cran
        LotGui l = LotGui.ouvrir(ctx);
        for (int i = 0; i < seg; i++) {
            int sx = x + i * (sw + gap);
            l.fill(sx, by, sx + sw, by + 2, 0x60000000);
            int part = Math.max(0, Math.min(10, filled - i * 10));
            if (part > 0) l.fill(sx, by, sx + Math.max(1, sw * part / 10), by + 2, s.resting() ? 0xFF7FD69A : DaKit.OR);
        }
        l.fermer();
        String detail = "Repos " + s.restMin() + "/" + s.restRequired() + " min"
                + (s.resting() ? " · en cours" : "") + " · ou un médic";
        DaKit.inter7o(ctx, font, DaKit.inter(detail), x, by + 9, DaKit.CREME_VAL, 0);
    }

    private static void fearOnly(GuiGraphicsExtractor ctx, Font font, int w) {
        Component k = Component.literal("恐");
        Component c = DaKit.inter("La peur te tient encore");
        float cw = font.width(k) + 4 + DaKit.largeur7(font, c);
        int x = Math.round(w / 2f - cw / 2f);
        ctx.text(font, k, x, 6, DaKit.ROUGE_CLAIR, true);
        DaKit.inter7o(ctx, font, c, x + font.width(k) + 4, 10, DaKit.CREME_VAL, 0);
    }

    /* ================================================================ commun */

    /** Rectangle {x, y, w, h} d'un élément HUD tel que le joueur l'a placé, ou null s'il est masqué. */
    private static int[] box(fr.reborn.hud.element.HudElement e, int w, int h) {
        try {
            var st = fr.reborn.hud.RebornHudClient.config().stateOf(e);
            if (!st.visible()) return null;
            var b = fr.reborn.hud.element.HudElementBounds.currentFor(e, st, w, h);
            return new int[]{b.x(), b.y(), b.width(), b.height()};
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static boolean hits(int x, int y, int w, int h, int[] r) {
        return x < r[0] + r[2] && r[0] < x + w && y < r[1] + r[3] && r[1] < y + h;
    }

}
