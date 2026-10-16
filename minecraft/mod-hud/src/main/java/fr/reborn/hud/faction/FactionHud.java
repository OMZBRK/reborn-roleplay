package fr.reborn.hud.faction;

import fr.reborn.hud.keybind.HudKeybinds;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.LotBlit;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * Invitation dans une section, chez l'invité (FACTION.md §9, maquette v2 05) — même grammaire que le duel et la radio,
 * <b>HUD subtil</b> (retour du 13/10) : deux lignes au-dessus de la barre d'action, sur un voile nuit très léger,
 * l'<b>insigne</b> de la section au trait (aucun kanji, aucun sceau), puis « Entrée accepter · Retour refuser · 9 s »
 * et un fil d'or d'1 px qui se vide. 10 s en face à face, 30 s depuis le panneau.
 */
public final class FactionHud {

    private static final Identifier VOILE = DaKit.tex("duel", "voile"), INSIGNES = DaKit.tex("faction", "insignes");
    private static final int GRIS = 0xFFB4A88E, ROUGE_PALE = 0xFFE8A0A2;

    private FactionHud() {}

    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden()) return;
        FactionClient.Invit inv = FactionClient.invit();
        if (inv == null) return;
        // le duel et la radio occupent la même place : pas deux invitations l'une sur l'autre
        if (fr.reborn.hud.duel.DuelClient.hasInvite() || fr.reborn.hud.radio.RadioClient.invit() != null) return;
        long now = System.currentTimeMillis();
        var win = mc.getWindow();
        int gs = Math.max(1, win.getGuiScale());
        int km = DaKit.echelleMenu();
        int sw = win.getWidth() / km;
        int bas = (int) Math.floor((win.getGuiScaledHeight() - 39) * gs / (double) km) - 4;
        Font f = mc.font;
        DaKit.unites(g, km);
        dessiner(g, f, inv, sw / 2, bas, km, now);
        DaKit.finUnites(g);
    }

    private static void dessiner(GuiGraphicsExtractor g, Font f, FactionClient.Invit inv, int cx, int bas, int km, long now) {
        float a = Math.min(1f, (now - inv.at()) / 250f);
        float reste = Math.max(0, inv.reste(now));
        Component nom = DaKit.interGras(DaKit.ellipse(f, inv.de(), 110));
        Component mil = DaKit.inter(" t'invite dans la section ");
        Component sec = DaKit.interGras(inv.section());
        float w1 = 11 + 4 + DaKit.largeur8(f, nom) + DaKit.largeur8(f, mil) + DaKit.largeur8(f, sec);
        String t2 = touche(HudKeybinds.DUEL_ACCEPT, "Entrée") + " accepter  ·  " + touche(HudKeybinds.DUEL_REFUSE, "Retour")
                + " refuser  ·  " + (int) Math.ceil(reste) + " s";
        Component c2 = DaKit.inter(t2);
        float w2 = DaKit.largeur7(f, c2);
        int w = Math.round(Math.max(w1, w2));
        int x0 = cx - w / 2;
        float y1 = bas - 18, y2 = bas - 8;
        DaKit.etire(g, VOILE, x0 - 22, bas - 27, w + 44, 30, 64, 16, DaKit.blanc(a * 0.9f));

        float x = cx - w1 / 2f;
        insigne(g, inv.insigne(), Math.round(x), Math.round(y1) - 6, 11, km, DaKit.alpha(DaKit.OR, a));
        x += 15;
        x += DaKit.inter8(g, f, nom, x, y1, DaKit.alpha(DaKit.OR_CLAIR, a), 0);
        x += DaKit.inter8(g, f, mil, x, y1, DaKit.alpha(DaKit.CREME_VAL, a), 0);
        DaKit.inter8(g, f, sec, x, y1, DaKit.alpha(DaKit.OR_CLAIR, a), 0);
        DaKit.inter7(g, f, c2, cx - w2 / 2f, y2, DaKit.alpha(reste < 4 ? ROUGE_PALE : GRIS, a), 0);

        // fil d'or d'1 px écran, qui se vide vers le centre
        int k = DaKit.echelleGui();
        DaKit.debutPx(g);
        int X0 = x0 * k, X1 = (x0 + w) * k, Y = (bas - 2) * k;
        g.fill(X0, Y, X1, Y + 1, DaKit.argb(0x8E6A30, 0.45f * a));
        int half = Math.round((X1 - X0) / 2f * Math.min(1f, reste / Math.max(1, inv.secs()))), mid = (X0 + X1) / 2;
        if (half > 0) g.fill(mid - half, Y, mid + half, Y + 1, DaKit.alpha(DaKit.OR, a));
        DaKit.finPx(g);
    }

    /** Insigne de l'atlas à la rangée exacte (t unités × k px), net. */
    private static void insigne(GuiGraphicsExtractor g, String nom, int x, int y, int t, int k, int col) {
        String[] noms = {"anbu", "police", "medical", "academie", "missions", "garde", "conseil", "bandeau", "lune",
                "konoha", "suna", "kiri", "kumo", "iwa", "ame"};
        int[] tailles = {8, 10, 11, 12, 14, 16, 18, 20, 22, 24, 28, 30, 32, 36, 40, 44, 48, 54, 60, 64, 72, 80, 96};
        int i = 4;
        for (int j = 0; j < noms.length; j++) if (noms[j].equals(nom)) i = j;
        int p = t * k, q = tailles[tailles.length - 1], yq = 0, w = 0, h = 0;
        for (int s : tailles) if (s >= p) { q = s; break; }
        for (int s : tailles) { if (s == q) yq = h; h += s + 2; w = Math.max(w, noms.length * (s + 2)); }
        LotBlit b = LotBlit.ouvrir(g, INSIGNES, w, h);
        b.etire(x, y, t, t, i * (q + 2), yq, q, q, col);
        b.fermer();
    }

    static String touche(KeyMapping km, String def) {
        if (km == null) return def;
        String save = km.saveString();
        if ("key.keyboard.enter".equals(save)) return "Entrée";
        if ("key.keyboard.backspace".equals(save)) return "Retour";
        String s = km.getTranslatedKeyMessage().getString();
        return s.length() > 10 ? s.substring(0, 10) : s;
    }
}
