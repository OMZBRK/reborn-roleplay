package fr.reborn.hud.ko;

import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.LotGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

/**
 * Affichage de toute la chaîne KO / mort dans <b>un seul style</b> (v2 du 14/10, retours de l'utilisateur : « visuellement
 * c'est pas ça », sauf l'écran « inconscient » jugé bien). Tout dérive de ce bloc-là :
 * <b>titre pixel (ArcadePix) centré</b>, <b>filet d'or d'1 px</b> qui se vide autour d'un <b>losange</b>, un temps en
 * Inter 9, un <b>sous-texte gris</b> en Inter 7 — rien d'autre, pas de plaque, pas de kanji, pas de voile rouge.
 * <ul>
 *   <li><b>Transitions</b> (douleur, à terre, K.O., réveil, hôpital, rétabli, second souffle, défaite) : le bloc, sans
 *       temps, le filet qui s'étire, en fondu ≈ 3 s ; la douleur n'a que cette entrée brève, en petit ;</li>
 *   <li><b>À terre (agonie)</b> : le bloc, plus bas (on regarde le sol en rampant), losange qui bat au rythme du cœur ;</li>
 *   <li><b>Inconscient</b> : le bloc au centre ; « /hopital » n'apparaît qu'une fois la commande utilisable ;</li>
 *   <li><b>Porté de force</b> (en douleur seulement) : le filet et « Espace · se débattre », rien de plus ;</li>
 *   <li><b>Exécution</b> : un fin anneau autour du viseur ({@link #anneau}) et un mot (exécuteur : {@link ExecuterClient}) ;</li>
 *   <li><b>ATA</b> : une ligne discrète en haut, filet d'or des minutes de repos.</li>
 * </ul>
 * En agonie et inconscient, le reste du HUD est masqué ({@link KoMasqueHud}). La douleur, elle, se <b>ressent</b>
 * (filtre {@code ko.fsh} mode 4, caméra, sons — {@link KoClient}) au lieu de s'afficher.
 */
public final class KoHud {

    private static final float CARD_IN = 0.2f, CARD_HOLD = 2.1f, CARD_OUT = 0.8f;
    /** Couleurs du bloc (celles de l'écran « inconscient »). */
    private static final int TITRE = 0xB0F5E9D0, FILET_FOND = 0x50D9A95E, GRIS = 0xA0C2B59A;

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
        boolean ko = s != null && s.phase() > 0;
        if (s != null) {
            if (s.phase() == 1) downed(ctx, font, s, w, h);
            else if (s.phase() == 2) unconscious(ctx, font, s, w, h);
            else if (s.ata() > 0) ata(ctx, font, s, w, h);
        }
        if (!ko && CorpsClient.porte() && CorpsClient.lutte() >= 0f) porte(ctx, font, w, h);
        else if (!ko && CorpsClient.consenti())   // porté de plein gré (14 · 24/10) : une ligne, pas de jauge
            DaKit.inter7o(ctx, font, DaKit.inter("Espace · descendre"), w / 2f, Math.round(h * 0.62f) + 8, GRIS, 1);
        CorpsClient.Exe x = CorpsClient.exe();
        if (x != null && x.role() == 1) victime(ctx, font, x, w, h);
        ExecuterClient.render(ctx, font, w, h);
        card(ctx, font, w, h);
    }

    /* ================================================================ le bloc commun */

    /**
     * Le bloc : titre pixel centré en (cx, y), filet d'or de largeur {@code fw} sous le titre, rempli à {@code frac}
     * depuis le centre, losange {@code losange} au milieu ; renvoie l'ordonnée du filet.
     */
    private static int bloc(GuiGraphicsExtractor ctx, Font font, float cx, int y, String titre, int col, int fw,
                            float frac, int losange, float a) {
        int fy = y;
        if (titre != null) {
            DaKit.arcX1(ctx, font, DaKit.arcade(titre), cx, y, DaKit.alpha(col, a), 1, true);
            fy = y + 12;
        }
        filet(ctx, Math.round(cx), fy, fw, frac, losange, a);
        return fy;
    }

    /** Filet d'or d'1 px, rempli depuis le centre, losange au milieu. */
    private static void filet(GuiGraphicsExtractor ctx, int cx, int y, int fw, float frac, int losange, float a) {
        int fill = Math.round(fw * Math.max(0f, Math.min(1f, frac)));
        LotGui l = LotGui.ouvrir(ctx);
        l.fill(cx - fw / 2, y, cx + fw / 2, y + 1, DaKit.alpha(FILET_FOND, a));
        if (fill > 0) l.fill(cx - fill / 2, y, cx + fill / 2, y + 1, DaKit.alpha(DaKit.OR, a));
        diamond(l, cx, y, DaKit.alpha(losange, a));
        l.fermer();
    }

    private static void diamond(LotGui l, int x, int y, int color) {
        l.fill(x - 1, y, x + 2, y + 1, color);
        l.fill(x, y - 1, x + 1, y + 2, color);
    }

    /* ================================================================ transitions */

    /** Transition : le bloc sans temps, filet qui s'étire, sous-texte ; fondu. La douleur : plus petite, plus brève. */
    private static void card(GuiGraphicsExtractor ctx, Font font, int w, int h) {
        float t = KoClient.sinceEvent();
        byte ev = KoClient.lastEvent();
        boolean petit = ev == KoClient.EV_DOULEUR;
        float hold = petit ? 1.2f : CARD_HOLD;
        if (t > CARD_IN + hold + CARD_OUT) return;
        float a = t < CARD_IN ? t / CARD_IN : t < CARD_IN + hold ? 1f : 1f - (t - CARD_IN - hold) / CARD_OUT;
        a = Math.max(0f, Math.min(1f, a));
        String title, sub;
        int color, losange = DaKit.OR;
        switch (ev) {
            case KoClient.EV_A_TERRE -> { title = "À terre"; sub = "Tu rampes encore…"; color = TITRE; losange = DaKit.ROUGE_CLAIR; }
            case KoClient.EV_KO -> { return; }   // le bloc « Inconscient » est lui-même la transition (pas de doublon)
            case KoClient.EV_REVEIL -> { title = "Réveil"; sub = "Tu reprends conscience."; color = TITRE; }
            case KoClient.EV_HOPITAL -> { title = "Hôpital"; sub = "On t'a ramené. Tu es encore très faible."; color = TITRE; }
            case KoClient.EV_RETABLI -> { title = "Rétabli"; sub = "Tes forces sont revenues."; color = TITRE; }
            case KoClient.EV_RELEVE -> { title = "Second souffle"; sub = "Debout… mais à bout de forces."; color = TITRE; }
            case KoClient.EV_DEFAITE -> { title = "Défaite"; sub = "Relève-toi, l'entraînement continue."; color = TITRE; }
            case KoClient.EV_DOULEUR -> { title = null; sub = "à bout de forces"; color = TITRE; losange = DaKit.ROUGE_CLAIR; }
            default -> { return; }
        }
        KoPayload st = KoClient.state();
        int cy = Math.round(h * (st != null && st.phase() == 2 ? 0.30f : petit ? 0.58f : 0.36f));
        int fw = Math.round((petit ? 60 : 140) * Math.min(1f, t / 0.5f));
        int fy = bloc(ctx, font, w / 2f, cy, title, color, fw, 1f, losange, a);
        DaKit.inter7o(ctx, font, DaKit.inter(sub), w / 2f, fy + 8, DaKit.alpha(GRIS, a), 1);
    }

    /* ================================================================ à terre (agonie) */

    /** Agonie : le bloc plus bas, filet qui se vide, losange rouge qui bat, temps, « Tu rampes ». */
    private static void downed(GuiGraphicsExtractor ctx, Font font, KoPayload s, int w, int h) {
        float left = Math.max(0f, s.left() - KoClient.sinceState());
        float frac = s.total() > 0 ? left / s.total() : 0f;
        float pulse = KoClient.pulse();
        int y = Math.round(h * 0.66f);
        dernierY = y;
        int coeur = DaKit.alpha(DaKit.ROUGE_CLAIR, 0.55f + 0.45f * pulse);
        int fy = bloc(ctx, font, w / 2f, y, "À terre", TITRE, 120, frac, coeur, 1f);
        DaKit.inter9o(ctx, font, DaKit.inter(Math.round(Math.ceil(left)) + " s"), w / 2f, fy + 9, DaKit.OR_CLAIR, 1);
        String sous = VolonteHud.available() ? "Tu rampes · Espace pour te relever" : "Tu rampes · tu peux chuchoter";
        DaKit.inter7o(ctx, font, DaKit.inter(sous), w / 2f, fy + 20, GRIS, 1);
    }

    /* ================================================================ inconscient */

    private static void unconscious(GuiGraphicsExtractor ctx, Font font, KoPayload s, int w, int h) {
        float left = Math.max(0f, s.left() - KoClient.sinceState());
        float frac = s.total() > 0 ? left / s.total() : 0f;
        boolean chakra = s.cause() == 1;
        int accent = chakra ? 0xFF6FA8E8 : DaKit.ROUGE_CLAIR;
        int cy = Math.round(h * 0.50f);
        // le bloc remonte s'il tombe sur le chat
        int[] chat = box(fr.reborn.hud.element.HudElement.CHAT, w, h);
        if (chat != null && hits(w / 2 - 110, cy, 220, 56, chat)) cy = Math.max(Math.round(h * 0.28f), chat[1] - 60);
        int by = bloc(ctx, font, w / 2f, cy, chakra ? "Épuisement" : "Inconscient", TITRE, 140, frac, accent, 1f);
        // « /hopital » seulement quand la commande est utilisable (fin des 5 minutes, signalée par le serveur)
        boolean hopital = s.hospital() && left <= 0.5f;
        if (!hopital) {
            int m = (int) Math.ceil(left) / 60, sec = (int) Math.ceil(left) % 60;
            DaKit.inter9o(ctx, font, DaKit.inter(String.format("%d:%02d", m, sec)), w / 2f, by + 9, DaKit.OR_CLAIR, 1);
        }
        DaKit.inter7o(ctx, font, DaKit.inter("Tu peux chuchoter et faire des /me"), w / 2f, by + 20, GRIS, 1);
        if (hopital) {
            float blink = 0.65f + 0.35f * (float) Math.sin(System.currentTimeMillis() / 400.0);
            DaKit.inter7o(ctx, font, DaKit.inter("/hopital — se laisser emmener à l'hôpital"), w / 2f, by + 9,
                DaKit.alpha(DaKit.OR_CLAIR, blink), 1);
        }
    }

    /* ================================================================ porté de force (douleur) */

    /** Porté en douleur : le filet (jauge de lutte) et « Espace · se débattre ». */
    private static void porte(GuiGraphicsExtractor ctx, Font font, int w, int h) {
        int y = Math.round(h * 0.62f);
        filet(ctx, w / 2, y, 90, CorpsClient.lutte(), DaKit.OR, 0.95f);
        DaKit.inter7o(ctx, font, DaKit.inter("Espace · se débattre"), w / 2f, y + 8, GRIS, 1);
    }

    /* ================================================================ exécution */

    /** Victime d'une exécution : anneau sobre autour du viseur, un mot. */
    private static void victime(GuiGraphicsExtractor ctx, Font font, CorpsClient.Exe x, int w, int h) {
        float p = x.progress();
        if (p >= 1f) return;
        anneau(ctx, w / 2, h / 2, p, 0xFFC97A80);
        DaKit.inter7o(ctx, font, DaKit.inter(x.lethal() ? "on t'exécute" : "on te met hors de combat"), w / 2f,
            h / 2f + 17, GRIS, 1);
    }

    /**
     * Fin anneau autour du viseur (rayon 11 GUI) : le tour en or très pâle (1 px écran), la part remplie ({@code frac},
     * sens horaire depuis midi) en {@code col} sur 2 px, avec une petite tête lumineuse.
     */
    public static void anneau(GuiGraphicsExtractor ctx, int cx, int cy, float frac, int col) {
        int k = DaKit.echelleGui();
        float r = 11f * k, ox = cx * k, oy = cy * k;
        int n = Math.max(64, Math.round(2f * (float) Math.PI * r * 1.5f));
        DaKit.debutPx(ctx);
        for (int i = 0; i < n; i++) {
            float u = i / (float) n;
            double ang = -Math.PI / 2 + u * Math.PI * 2;
            float c = (float) Math.cos(ang), s = (float) Math.sin(ang);
            int px = Math.round(ox + c * r), py = Math.round(oy + s * r);
            if (u > frac) {
                ctx.fill(px, py, px + 1, py + 1, 0x55D9A95E);
            } else {
                int qx = Math.round(ox + c * (r - 1)), qy = Math.round(oy + s * (r - 1));
                ctx.fill(px, py, px + 1, py + 1, col);
                ctx.fill(qx, qy, qx + 1, qy + 1, col);
            }
        }
        if (frac > 0f && frac < 1f) {
            double ang = -Math.PI / 2 + frac * Math.PI * 2;
            int hx = Math.round(ox + (float) Math.cos(ang) * (r - 0.5f)), hy = Math.round(oy + (float) Math.sin(ang) * (r - 0.5f));
            ctx.fill(hx - 1, hy - 1, hx + 2, hy + 2, DaKit.alpha(DaKit.OR_CLAIR, 0.9f));
        }
        DaKit.finPx(ctx);
    }

    /* ================================================================ ATA */

    /** ATA : une ligne discrète en haut — « Blessé », filet d'or des minutes de repos, le détail en gris. */
    private static void ata(GuiGraphicsExtractor ctx, Font font, KoPayload s, int w, int h) {
        boolean full = s.ata() == 2;
        int y = 8;
        int[] vit = box(fr.reborn.hud.element.HudElement.VITALS, w, h);
        int cx = w / 2;
        if (vit != null && hits(cx - 80, y - 4, 160, 26, vit)) y = vit[1] + vit[3] + 8;
        DaKit.inter8(ctx, font, DaKit.inter(full ? "Grièvement blessé" : "Blessé"), cx, y, DaKit.CREME, 1);
        filet(ctx, cx, y + 7, 120, s.rest(), s.resting() ? 0xFF7FD69A : DaKit.ROUGE_CLAIR, 1f);
        String detail = "repos " + s.restMin() + "/" + s.restRequired() + " min" + (s.resting() ? " · en cours" : "")
            + " · ou un médic";
        DaKit.inter7o(ctx, font, DaKit.inter(detail), cx, y + 15, GRIS, 1);
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
