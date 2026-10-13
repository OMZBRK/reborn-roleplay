package fr.reborn.hud.duel;

import fr.reborn.hud.interaction.InteractionMode;
import fr.reborn.hud.keybind.HudKeybinds;
import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Affichage du duel consenti — version <b>HUD subtil</b> (retour du 13/10 : « c'est gros, faut que ce soit subtil »,
 * DA §6) : aucune plaque, aucun gros bouton. Du texte petit sur un voile nuit très léger, un minuscule sceau 決, un fil
 * d'or d'un pixel. Trois petites textures ({@code tools/gen_duel_textures.py}) ; tout le reste est du texte. Dessiné en
 * unités menu ({@link DaKit#echelleMenu}).
 * <ul>
 *   <li><b>Invitation</b> : deux lignes au-dessus de la barre d'action — « 決 Kazuki Uchiha te propose un duel », puis
 *       « Entrée accepter · Retour refuser » en petit ; fil d'or d'1 px qui se vide. En mode interaction (R), les mots
 *       « accepter » / « refuser » se cliquent (soulignés au survol).</li>
 *   <li><b>Attente</b> (proposant) : une ligne grise.</li>
 *   <li><b>Pendant le duel</b> : indicateur fin en haut au centre « 決 Kazuki Uchiha · 4:56 · abandon » ; il passe en rouge
 *       pâle quand on s'éloigne ; « abandon » se clique en mode interaction.</li>
 *   <li><b>Compte à rebours</b> : chiffres sobres en fondu, puis « COMBAT » bref.</li>
 *   <li><b>Fin</b> : un mot centré en fondu, un filet d'or fin, la raison en petit gris.</li>
 * </ul>
 */
public final class DuelHud {

    private static final Identifier SCEAU = DaKit.tex("duel", "sceau"), VOILE = DaKit.tex("duel", "voile"),
        PIECES = DaKit.tex("duel", "pieces");

    private static final long FIN_MS = 2800;
    private static final int GRIS = 0xFFB4A88E, GRIS_DOUX = 0xFF8C826E, ROUGE_PALE = 0xFFE8A0A2;

    /** Mots cliquables posés à l'image précédente (unités menu {x, y, w, h}), pour le clic en mode interaction. */
    private static int[] rAccept, rRefuse, rAbandon;
    /** Banc d'essai : survol forcé (0 accepter, 1 refuser, 2 abandon, -1 aucun). */
    public static int debugHover = -1;

    private DuelHud() {}

    public static void render(GuiGraphicsExtractor g) {
        rAccept = rRefuse = rAbandon = null;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden()) return;
        boolean any = DuelClient.hasInvite() || DuelClient.waiting != null || DuelClient.duel != null || DuelClient.end != null;
        if (!any) return;
        long now = System.currentTimeMillis();
        var win = mc.getWindow();
        int gs = Math.max(1, win.getGuiScale());
        int km = DaKit.echelleMenu();
        int sw = win.getWidth() / km, sh = win.getHeight() / km;
        double mx = -1, my = -1;
        boolean souris = InteractionMode.INSTANCE.isActive();
        if (souris) {
            mx = InteractionMode.INSTANCE.cursorGuiX() * gs / (double) km;
            my = InteractionMode.INSTANCE.cursorGuiY() * gs / (double) km;
        }
        Font f = mc.font;
        // bas de la zone libre au-dessus des cœurs / de la barre d'action
        int bas = (int) Math.floor((win.getGuiScaledHeight() - 39) * gs / (double) km) - 4;
        DaKit.unites(g, km);
        if (DuelClient.hasInvite()) invitation(g, f, DuelClient.invite, sw / 2, bas, mx, my, souris, now);
        else if (DuelClient.waiting != null && DuelClient.duel == null) {
            if (DuelClient.waiting.left(now) <= 0) DuelClient.waiting = null;
            else attente(g, f, DuelClient.waiting, sw / 2, bas, now);
        }
        DuelClient.Duel d = DuelClient.duel;
        if (d != null) {
            indicateur(g, f, sw / 2, d, mx, my, now);
            compte(g, f, sw / 2, sh, d, now);
        }
        DuelClient.End e = DuelClient.end;
        if (e != null) {
            if (now - e.at() > FIN_MS) DuelClient.end = null;
            else fin(g, f, sw / 2, sh, e, now);
        }
        DaKit.finUnites(g);
    }

    /* ================================================================ invitation */

    private static void invitation(GuiGraphicsExtractor g, Font f, DuelClient.Invite inv, int cx, int bas,
                                   double mx, double my, boolean souris, long now) {
        float a = Math.min(1f, (now - inv.at()) / 250f);
        float left = inv.left(now);

        // ligne 1 : sceau · « Nom te propose un duel »
        Component nom = DaKit.interGras(DaKit.ellipse(f, inv.fromName(), 110));
        Component suite = DaKit.inter(" te propose un duel");
        float w1 = 11 + 4 + DaKit.largeur8(f, nom) + DaKit.largeur8(f, suite);
        // ligne 2 : touches et mots (petit, gris)
        Component kA = DaKit.inter(touche(HudKeybinds.DUEL_ACCEPT, "Entrée")), mA = DaKit.inter(" accepter");
        Component kR = DaKit.inter(touche(HudKeybinds.DUEL_REFUSE, "Retour")), mR = DaKit.inter(" refuser");
        Component sep = DaKit.inter("  ·  "), tps = DaKit.inter(Math.max(0, (int) Math.ceil(left)) + " s");
        float wA = DaKit.largeur7(f, kA) + DaKit.largeur7(f, mA), wR = DaKit.largeur7(f, kR) + DaKit.largeur7(f, mR);
        float w2 = wA + wR + DaKit.largeur7(f, tps) + 2 * DaKit.largeur7(f, sep);
        int w = Math.round(Math.max(w1, w2));
        int x0 = cx - w / 2;
        float y1 = bas - 18, y2 = bas - 8;

        DaKit.etire(g, VOILE, x0 - 22, bas - 27, w + 44, 30, 64, 16, DaKit.blanc(a));

        // ligne 1
        float x = cx - w1 / 2f;
        sceau(g, f, Math.round(x), Math.round(y1) - 6, a);
        x += 15;
        x += DaKit.inter8(g, f, nom, x, y1, DaKit.alpha(DaKit.OR_CLAIR, a), 0);
        DaKit.inter8(g, f, suite, x, y1, DaKit.alpha(DaKit.CREME_VAL, a), 0);

        // ligne 2 : « Entrée accepter · Retour refuser · 24 s »
        x = cx - w2 / 2f;
        rAccept = new int[] {Math.round(x) - 2, Math.round(y2) - 5, Math.round(wA) + 4, 10};
        x = mot(g, f, kA, mA, x, y2, a, debugHover == 0 || DaKit.dans(mx, my, rAccept[0], rAccept[1], rAccept[2], rAccept[3]));
        x += DaKit.inter7(g, f, sep, x, y2, DaKit.alpha(GRIS_DOUX, a), 0);
        rRefuse = new int[] {Math.round(x) - 2, Math.round(y2) - 5, Math.round(wR) + 4, 10};
        x = mot(g, f, kR, mR, x, y2, a, debugHover == 1 || DaKit.dans(mx, my, rRefuse[0], rRefuse[1], rRefuse[2], rRefuse[3]));
        x += DaKit.inter7(g, f, sep, x, y2, DaKit.alpha(GRIS_DOUX, a), 0);
        DaKit.inter7(g, f, tps, x, y2, DaKit.alpha(left < 8 ? ROUGE_PALE : GRIS_DOUX, a), 0);

        // fil d'or d'1 px écran qui se vide vers le centre, losanges aux bouts
        fil(g, x0, w, bas - 2, Math.min(1f, left / Math.max(1, inv.secs())), a);
    }

    /** Touche (or terne) + mot (gris ; crème souligné au survol). Renvoie le x de fin. */
    private static float mot(GuiGraphicsExtractor g, Font f, Component k, Component m, float x, float ym, float a, boolean hot) {
        float x0 = x;
        x += DaKit.inter7(g, f, k, x, ym, DaKit.alpha(hot ? DaKit.OR_CLAIR : DaKit.OR_ETIQ, a), 0);
        x += DaKit.inter7(g, f, m, x, ym, DaKit.alpha(hot ? DaKit.CREME : GRIS, a), 0);
        if (hot) souligne(g, x0, x, ym + 4, DaKit.alpha(DaKit.OR, a));
        return x;
    }

    /* ================================================================ attente (proposant) */

    private static void attente(GuiGraphicsExtractor g, Font f, DuelClient.Waiting w, int cx, int bas, long now) {
        float a = Math.min(1f, (now - w.at()) / 250f) * 0.9f;
        Component c = DaKit.inter("Duel proposé à " + w.toName() + " — en attente · "
            + Math.max(0, (int) Math.ceil(w.left(now))) + " s");
        DaKit.inter7o(g, f, c, cx, bas - 6, DaKit.alpha(GRIS, a), 1);
    }

    /* ================================================================ indicateur pendant le duel */

    private static void indicateur(GuiGraphicsExtractor g, Font f, int cx, DuelClient.Duel d, double mx, double my, long now) {
        float a = Math.min(1f, (now - d.at) / 300f);
        boolean loin = d.max > 0 && d.dist > d.max * 0.8;
        long sf = d.sinceFight(now);
        String temps;
        if (sf < 0) temps = "prêts";
        else {
            long s = d.secondsLeft(now);
            temps = s / 60 + ":" + String.format("%02d", s % 60);
        }
        Component nom = DaKit.inter(DaKit.ellipse(f, d.advName, 100));
        Component sep = DaKit.inter("  ·  "), tc = DaKit.inter(temps), ab = DaKit.inter("abandon");
        Component mains = d.techniques ? null : DaKit.inter("mains nues  ·  ");
        float wSep = DaKit.largeur7(f, sep);
        float w = 11 + 5 + DaKit.largeur8(f, nom) + wSep + (mains != null ? DaKit.largeur7(f, mains) : 0)
            + DaKit.largeur8(f, tc) + wSep + DaKit.largeur7(f, ab);
        float x = cx - w / 2f, ym = 9;
        DaKit.etire(g, VOILE, Math.round(x) - 18, 1, Math.round(w) + 36, 17, 64, 16, DaKit.blanc(0.85f * a));
        int texte = loin ? ROUGE_PALE : DaKit.CREME_VAL;
        sceau(g, f, Math.round(x), Math.round(ym) - 6, a);
        x += 16;
        x += DaKit.inter8(g, f, nom, x, ym, DaKit.alpha(texte, a), 0);
        x += DaKit.inter7(g, f, sep, x, ym, DaKit.alpha(GRIS_DOUX, a), 0);
        if (mains != null) x += DaKit.inter7(g, f, mains, x, ym, DaKit.alpha(GRIS, a), 0);
        x += DaKit.inter8(g, f, tc, x, ym, DaKit.alpha(loin ? ROUGE_PALE : DaKit.OR, a), 0);
        x += DaKit.inter7(g, f, sep, x, ym, DaKit.alpha(GRIS_DOUX, a), 0);
        float wa = DaKit.largeur7(f, ab);
        rAbandon = new int[] {Math.round(x) - 2, Math.round(ym) - 5, Math.round(wa) + 4, 10};
        boolean hot = debugHover == 2 || DaKit.dans(mx, my, rAbandon[0], rAbandon[1], rAbandon[2], rAbandon[3]);
        DaKit.inter7(g, f, ab, x, ym, DaKit.alpha(hot ? DaKit.CREME : GRIS, a), 0);
        if (hot) souligne(g, x, x + wa, ym + 4, DaKit.alpha(DaKit.OR, a));

        // rappel, quelques secondes au début du combat seulement
        if (sf >= 0 && sf < 5000 && !InteractionMode.INSTANCE.isActive()) {
            float r = sf > 4000 ? (5000 - sf) / 1000f : 1f;
            DaKit.inter7o(g, f, DaKit.inter("sans séquelles  ·  R puis clic sur « abandon » pour abandonner"), cx, 24,
                DaKit.alpha(GRIS, 0.85f * r * a), 1);
        }
    }

    /* ================================================================ compte à rebours */

    private static void compte(GuiGraphicsExtractor g, Font f, int cx, int sh, DuelClient.Duel d, long now) {
        long sf = d.sinceFight(now);
        if (sf >= 800) return;
        int cy = Math.round(sh * 0.40f);
        if (sf < 0) {
            int n = (int) Math.ceil(-sf / 1000.0);
            if (n > 3) return;
            float p = 1f - ((-sf) % 1000) / 1000f;          // 0 → 1 dans la seconde
            float a = Math.min(1f, p / 0.12f) * (p > 0.55f ? Math.max(0f, 1f - (p - 0.55f) / 0.45f) : 1f);
            Component c = DaKit.arcade(String.valueOf(n));
            float tw = (f.width(c) - 1) * 2f;
            float y = cy - 7;
            DaKit.texteEchelle(g, f, c, cx - tw / 2f + 1, y + 1, DaKit.argb(0x0A0406, 0.7f * a), 2f);
            DaKit.texteEchelle(g, f, c, cx - tw / 2f, y, DaKit.alpha(DaKit.CREME, a), 2f);
        } else {
            float a = Math.min(1f, sf / 80f) * (sf > 400 ? Math.max(0f, 1f - (sf - 400) / 400f) : 1f);
            int w = DaKit.arcX1(g, f, DaKit.arcade("COMBAT"), cx, cy - 3.5f, DaKit.alpha(DaKit.OR_CLAIR, a), 1, true);
            DaKit.blit(g, PIECES, cx - w / 2 - 8, cy - 1, 0, 0, 3, 3, 8, 4, DaKit.blanc(a));
            DaKit.blit(g, PIECES, cx + w / 2 + 6, cy - 1, 0, 0, 3, 3, 8, 4, DaKit.blanc(a));
        }
    }

    /* ================================================================ fin */

    private static void fin(GuiGraphicsExtractor g, Font f, int cx, int sh, DuelClient.End e, long now) {
        long t = now - e.at();
        float a = Math.min(1f, t / 250f) * (t > FIN_MS - 700 ? Math.max(0f, (FIN_MS - t) / 700f) : 1f);
        if (a <= 0.01f) return;
        String titre;
        int col;
        switch (e.issue()) {
            case "victoire" -> { titre = "VICTOIRE"; col = DaKit.OR_CLAIR; }
            case "defaite" -> { titre = "DÉFAITE"; col = ROUGE_PALE; }
            case "nul" -> { titre = "MATCH NUL"; col = DaKit.CREME; }
            default -> { titre = "DUEL ANNULÉ"; col = GRIS; }
        }
        Component tc = DaKit.arcade(titre);
        float tw = (f.width(tc) - 1) * 2f;
        float y = Math.round(sh * 0.30f);
        DaKit.texteEchelle(g, f, tc, cx - tw / 2f + 1, y + 1, DaKit.argb(0x0A0406, 0.7f * a), 2f);
        DaKit.texteEchelle(g, f, tc, cx - tw / 2f, y, DaKit.alpha(col, a), 2f);
        // filet d'or d'1 px écran, losanges aux bouts
        int fw = Math.round(tw) + 24;
        fil(g, cx - fw / 2, fw, Math.round(y) + 20, 1f, a);
        StringBuilder sub = new StringBuilder();
        if (!e.advName().isEmpty()) sub.append(e.issue().equals("victoire") ? "sur " : "contre ").append(e.advName());
        if (!e.reason().isEmpty()) sub.append(sub.isEmpty() ? "" : "  ·  ").append(e.reason());
        if (!sub.isEmpty()) DaKit.inter7o(g, f, DaKit.inter(sub.toString()), cx, y + 29, DaKit.alpha(GRIS, a), 1);
    }

    /* ================================================================ pièces communes */

    /** Petit sceau 決 (11 unités) : texture laquée + kanji de la police du jeu, crème. */
    private static void sceau(GuiGraphicsExtractor g, Font f, int x, int y, float a) {
        DaKit.blit(g, SCEAU, x, y, 0, 0, 12, 12, 12, 12, DaKit.blanc(a));
        Component k = Component.literal("決");
        float kw = f.width(k);
        DaKit.texteEchelle(g, f, k, x + 5.5f - kw / 2f, y + 1.5f, DaKit.alpha(0xFFF2E6CC, a), 1f);
    }

    /** Fil d'or d'1 px écran sur la largeur w (unités) à la hauteur y : piste éteinte, part vive centrée (frac). */
    private static void fil(GuiGraphicsExtractor g, int x0, int w, int y, float frac, float a) {
        int k = DaKit.echelleGui();
        DaKit.debutPx(g);
        int X0 = x0 * k, X1 = (x0 + w) * k, Y = y * k;
        g.fill(X0, Y, X1, Y + 1, DaKit.argb(0x8E6A30, 0.45f * a));
        int half = Math.round((X1 - X0) / 2f * frac), mid = (X0 + X1) / 2;
        if (half > 0) g.fill(mid - half, Y, mid + half, Y + 1, DaKit.alpha(DaKit.OR, a));
        DaKit.finPx(g);
        DaKit.blit(g, PIECES, x0 - 4, y - 1, 0, 0, 3, 3, 8, 4, DaKit.blanc(a));
        DaKit.blit(g, PIECES, x0 + w + 1, y - 1, 0, 0, 3, 3, 8, 4, DaKit.blanc(a));
    }

    /** Soulignement d'1 px écran de x0 à x1 (unités) à la hauteur y. */
    private static void souligne(GuiGraphicsExtractor g, float x0, float x1, float y, int col) {
        int k = DaKit.echelleGui();
        DaKit.debutPx(g);
        g.fill(Math.round(x0 * k), Math.round(y * k), Math.round(x1 * k), Math.round(y * k) + 1, col);
        DaKit.finPx(g);
    }

    /* ================================================================ entrées */

    /** Clic du mode interaction (coordonnées GUI) : un mot du duel ? Renvoie true s'il a été pris. */
    public static boolean click(double guiX, double guiY) {
        Minecraft mc = Minecraft.getInstance();
        int gs = Math.max(1, mc.getWindow().getGuiScale());
        int km = DaKit.echelleMenu();
        double mx = guiX * gs / km, my = guiY * gs / km;
        if (rAccept != null && DaKit.dans(mx, my, rAccept[0], rAccept[1], rAccept[2], rAccept[3])) { DuelClient.accept(); return true; }
        if (rRefuse != null && DaKit.dans(mx, my, rRefuse[0], rRefuse[1], rRefuse[2], rRefuse[3])) { DuelClient.refuse(); return true; }
        if (rAbandon != null && DaKit.dans(mx, my, rAbandon[0], rAbandon[1], rAbandon[2], rAbandon[3])) { DuelClient.forfeit(); return true; }
        return false;
    }

    /** Banc d'essai : centre GUI du mot i (0 accepter, 1 refuser, 2 abandon), ou null. */
    public static double[] debugCentre(int i) {
        int[] r = i == 0 ? rAccept : i == 1 ? rRefuse : rAbandon;
        if (r == null) return null;
        Minecraft mc = Minecraft.getInstance();
        double k = DaKit.echelleMenu() / (double) Math.max(1, mc.getWindow().getGuiScale());
        return new double[] {(r[0] + r[2] / 2.0) * k, (r[1] + r[3] / 2.0) * k};
    }

    /** Libellé court d'une touche (« Retour arrière » → Retour). */
    static String touche(KeyMapping km, String def) {
        if (km == null) return def;
        String save = km.saveString();
        if ("key.keyboard.enter".equals(save)) return "Entrée";
        if ("key.keyboard.backspace".equals(save)) return "Retour";
        if ("key.keyboard.unknown".equals(save)) return "—";
        String s = km.getTranslatedKeyMessage().getString();
        return s.length() > 10 ? s.substring(0, 10) : s;
    }
}
