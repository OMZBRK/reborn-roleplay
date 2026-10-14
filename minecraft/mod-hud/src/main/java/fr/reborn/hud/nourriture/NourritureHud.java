package fr.reborn.hud.nourriture;

import fr.reborn.hud.duel.DuelClient;
import fr.reborn.hud.keybind.HudKeybinds;
import fr.reborn.hud.menu.inventory.SlotItem;
import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

/**
 * HUD de la nourriture au-dessus de la barre d'action — règle 9 de la DA (subtil, épuré, petit ; aucun kanji) :
 * <ul>
 *   <li><b>Préparer / manger</b> (maquettes v3 01 et 03) : une ligne de texte petit centrée (picto, nom en gras,
 *       détail) et un filet de 2 px écran qui se remplit, à la place du filet de sprint ; gris pendant une pause
 *       (« Repas en pause — reprend dans 28 s ») ;</li>
 *   <li><b>On me tend</b> (maquette 02, comme l'invitation de duel, sans sceau) : « Teuchi te tend un Ramen shōyu —
 *       chaud » puis « F prendre · Retour refuser », fil d'or d'1 px qui se vide en 10 s ;</li>
 *   <li><b>Je tends</b> : une ligne grise.</li>
 * </ul>
 * Dessiné en unités menu ({@link DaKit#echelleMenu}) : même taille à l'écran quelle que soit l'échelle du HUD.
 */
public final class NourritureHud {

    private static final Identifier VOILE = DaKit.tex("duel", "voile");
    private static final int GRIS = 0xFFB4A88E, GRIS_DOUX = 0xFF8C826E, CREME_DOUX = 0xFFC2B59A, ROUGE_PALE = 0xFFE8A0A2;
    private static final int FILET = 72;

    private static ItemStack iconeOffre;
    private static Object cleIcone;

    private NourritureHud() {}

    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden() || mc.gui.screen() != null) return;
        boolean offre = NourritureClient.aOffre();
        NourritureClient.Action act = NourritureClient.action;
        NourritureClient.Attente att = NourritureClient.attente;
        long now = System.currentTimeMillis();
        if (att != null && att.reste(now) <= 0) { NourritureClient.attente = null; att = null; }
        if (!offre && act == null && att == null) {
            invite(g, mc, now);
            return;
        }
        var win = mc.getWindow();
        int gs = Math.max(1, win.getGuiScale());
        int km = DaKit.echelleMenu();
        int sw = win.getWidth() / km;
        float barre = (win.getGuiScaledHeight() - 22) * gs / (float) km;   // haut de la barre d'action (unités)
        Font f = mc.font;
        DaKit.unites(g, km);
        float yFilet = barre - 12;
        if (act != null) action(g, f, act, sw / 2f, yFilet, now);
        float bas = act != null ? yFilet - 16 : barre - 8;
        if (offre && !DuelClient.hasInvite()) offre(g, f, NourritureClient.offre, sw / 2f, bas, now);
        else if (att != null) DaKit.inter7o(g, f, DaKit.inter("Tu tends " + att.quoi() + " à " + att.a() + " — "
            + (int) Math.ceil(att.reste(now)) + " s"), sw / 2f, bas - 6, DaKit.alpha(GRIS, 0.9f), 1);
        DaKit.finUnites(g);
    }

    /* ================================================================ invite « [R] Tendre … » */

    /** Banc d'essai : cible imposée (un mannequin) et objet tenu considéré comme un plat. */
    public static net.minecraft.world.entity.Entity debugCible;

    private static final Identifier TOUCHE = DaKit.tex("inventaire", "touche"), VOILE_C = DaKit.tex("inventaire", "voile_centre");

    /**
     * Plat (ou ration, panier, bol) en main et quelqu'un tout près dans le viseur : « [R] Tendre Ramen shōyu » au-dessus
     * de lui (maquette 02), comme « [F] Ramasser ». Le geste lui-même passe par le menu d'interaction (R).
     */
    private static void invite(GuiGraphicsExtractor g, Minecraft mc, long now) {
        if (fr.reborn.hud.interaction.InteractionMode.INSTANCE.isActive()) return;
        net.minecraft.world.entity.Entity e = debugCible != null ? debugCible : mc.crosshairPickEntity;
        if (!(e instanceof net.minecraft.world.entity.player.Player) && e != debugCible) return;
        if (e == null || e.distanceTo(mc.player) > 3.5f) return;
        ItemStack main = mc.player.getMainHandItem();
        if (main.isEmpty() || (debugCible == null && !nourriture(main))) return;
        float pt = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        double[] px = fr.reborn.hud.sol.RamasserClient.projeter(mc, e.getPosition(pt).add(0, e.getBbHeight() + 0.35, 0));
        if (px == null) return;
        int km = DaKit.echelleMenu();
        Font f = mc.font;
        float cx = (float) (px[0] / km), ym = (float) (px[1] / km) - 6;
        Component cT = DaKit.interGras("Tendre"), cN = DaKit.inter(DaKit.ellipse(f, main.getHoverName().getString(), 120));
        float w = 11 + 4 + DaKit.largeur8(f, cT) + 3 + DaKit.largeur8(f, cN);
        float x0 = cx - w / 2f;
        DaKit.unites(g, km);
        DaKit.etire(g, VOILE_C, Math.round(x0) - 12, Math.round(ym) - 9, Math.round(w) + 24, 18, 64, 16, DaKit.blanc(1f));
        DaKit.blit(g, TOUCHE, Math.round(x0), Math.round(ym) - 6, 0, 0, 11, 11, 11, 11, DaKit.blanc(1f));
        DaKit.arcFin(g, f, DaKit.arcade(touche(HudKeybinds.INTERACTION, "R")), x0 + 5.5f, ym - 2.5f, DaKit.CREME, 1, false);
        float x = x0 + 15;
        x += DaKit.inter8(g, f, cT, x, ym, DaKit.CREME, 0) + 3;
        x += DaKit.inter8(g, f, cN, x, ym, CREME_DOUX, 0);
        int k = DaKit.echelleGui();
        DaKit.debutPx(g);
        g.fill(Math.round((x0 + 3) * k), Math.round((ym + 6) * k), Math.round((x - 4) * k), Math.round((ym + 6) * k) + 1,
            DaKit.argb(0xD9A95E, 0.6f));
        DaKit.finPx(g);
        DaKit.finUnites(g);
    }

    /** Objet de la nourriture (modèle {@code reborn:plat_*}, ration, panier, bol vide) ? */
    private static boolean nourriture(ItemStack s) {
        var m = s.get(net.minecraft.core.component.DataComponents.ITEM_MODEL);
        if (m == null || !"reborn".equals(m.getNamespace())) return false;
        String p = m.getPath();
        return p.startsWith("plat_") || p.equals("ration_seche") || p.equals("panier_ingredients") || p.equals("bol_vide");
    }

    /* ================================================================ préparer / manger */

    private static void action(GuiGraphicsExtractor g, Font f, NourritureClient.Action a, float cx, float y, long now) {
        int pause = a.pause(now);
        boolean pausee = pause > 0, marche = a.repas && a.vitesse == 0 && !pausee;
        float av = a.avance(now);
        String titre, detail;
        if (pausee) { titre = "Repas en pause"; detail = "reprend dans " + pause + " s"; }
        else if (!a.repas) { titre = a.nom; detail = "au fourneau · " + Math.max(0, (int) Math.ceil(a.duree * (1 - av))) + " s"; }
        else if (marche) { titre = a.nom; detail = "marche pour manger"; }
        else {
            titre = a.nom;
            detail = (a.froid ? "froid · " : "") + a.secondesRestantes(now) + " s" + (a.vitesse >= 2 ? " · assis" : "");
        }
        Component ct = DaKit.interGras(titre), cd = DaKit.inter(detail);
        float wt = DaKit.largeur8(f, ct), wd = DaKit.largeur7(f, cd);
        float tot = 11 + 4 + wt + 4 + wd;
        float x = cx - tot / 2f, ym = y - 7;
        EffetsIcones.poser(g, a.icone, x, ym - 5.5f, 10, pausee ? 0xD8A09A94 : DaKit.blanc(0.95f));
        x += 15;
        x += DaKit.inter8(g, f, ct, x, ym, pausee ? GRIS : DaKit.CREME, 0) + 4;
        DaKit.inter7o(g, f, cd, x, ym, CREME_DOUX, 0);
        // filet de 2 px écran
        int k = DaKit.echelleGui();
        DaKit.debutPx(g);
        int X0 = Math.round((cx - FILET / 2f) * k), X1 = Math.round((cx + FILET / 2f) * k), Y = Math.round(y * k);
        g.fill(X0, Y, X1, Y + 2, DaKit.argb(0x1E1614, 0.75f));
        int fx = X0 + Math.round((X1 - X0) * av);
        if (fx > X0) g.fill(X0, Y, fx, Y + 2, pausee ? GRIS : DaKit.OR);
        DaKit.finPx(g);
    }

    /* ================================================================ on me tend */

    private static void offre(GuiGraphicsExtractor g, Font f, NourritureClient.Offre o, float cx, float bas, long now) {
        float a = Math.min(1f, (now - o.a()) / 250f);
        float left = o.reste(now);
        Component nom = DaKit.interGras(DaKit.ellipse(f, o.de(), 110));
        Component suite = DaKit.inter(" te tend " + o.quoi());
        Component etat = o.etat() == null ? null : DaKit.inter(" — " + o.etat());
        float w1 = 11 + 4 + DaKit.largeur8(f, nom) + DaKit.largeur8(f, suite) + (etat == null ? 0 : DaKit.largeur8(f, etat));
        Component kP = DaKit.inter(touche(HudKeybinds.PICKUP, "F")), mP = DaKit.interGras(" prendre");
        Component kR = DaKit.inter(touche(HudKeybinds.DUEL_REFUSE, "Retour")), mR = DaKit.inter(" refuser");
        Component sep = DaKit.inter("  ·  "), tps = DaKit.inter("  ·  " + Math.max(0, (int) Math.ceil(left)) + " s");
        float wP = DaKit.largeur7(f, kP) + DaKit.largeur7(f, mP), wR = DaKit.largeur7(f, kR) + DaKit.largeur7(f, mR);
        float w2 = wP + DaKit.largeur7(f, sep) + wR + DaKit.largeur7(f, tps);
        int w = Math.round(Math.max(w1, w2));
        int x0 = Math.round(cx - w / 2f);
        float y1 = bas - 18, y2 = bas - 8;
        DaKit.etire(g, VOILE, x0 - 22, Math.round(bas) - 27, w + 44, 30, 64, 16, DaKit.blanc(a));

        float x = cx - w1 / 2f;
        icone(g, o, x, y1 - 5.5f, a);
        x += 15;
        x += DaKit.inter8(g, f, nom, x, y1, DaKit.alpha(DaKit.OR_CLAIR, a), 0);
        x += DaKit.inter8(g, f, suite, x, y1, DaKit.alpha(DaKit.CREME_VAL, a), 0);
        if (etat != null) DaKit.inter8(g, f, etat, x, y1, DaKit.alpha(CREME_DOUX, a), 0);

        x = cx - w2 / 2f;
        x += DaKit.inter7o(g, f, kP, x, y2, DaKit.alpha(DaKit.CREME, a), 0);
        float xm = x;
        x += DaKit.inter7o(g, f, mP, x, y2, DaKit.alpha(DaKit.OR_CLAIR, a), 0);
        souligne(g, xm + 2, x, y2 + 4, DaKit.alpha(DaKit.OR, 0.7f * a));
        x += DaKit.inter7o(g, f, sep, x, y2, DaKit.alpha(GRIS_DOUX, a), 0);
        x += DaKit.inter7o(g, f, kR, x, y2, DaKit.alpha(DaKit.CREME, a), 0);
        x += DaKit.inter7o(g, f, mR, x, y2, DaKit.alpha(GRIS, a), 0);
        DaKit.inter7o(g, f, tps, x, y2, DaKit.alpha(left < 4 ? ROUGE_PALE : GRIS_DOUX, a), 0);

        // fil d'or d'1 px écran qui se vide vers le centre
        int k = DaKit.echelleGui();
        DaKit.debutPx(g);
        int X0 = x0 * k, X1 = (x0 + w) * k, Y = Math.round((bas - 2) * k);
        g.fill(X0, Y, X1, Y + 1, DaKit.argb(0x8E6A30, 0.45f * a));
        int half = Math.round((X1 - X0) / 2f * Math.min(1f, left / Math.max(1, o.secs()))), mid = (X0 + X1) / 2;
        if (half > 0) g.fill(mid - half, Y, mid + half, Y + 1, DaKit.alpha(DaKit.OR, a));
        DaKit.finPx(g);
    }

    /** Icône de l'objet tendu (rendu de l'objet, comme les notifications) ou picto (ryo). */
    private static void icone(GuiGraphicsExtractor g, NourritureClient.Offre o, float x, float y, float a) {
        if (o.mat() != null) {
            if (cleIcone != o) {
                cleIcone = o;
                try { iconeOffre = new SlotItem(o.mat(), o.model(), 1, null, 0).toStack(); } catch (RuntimeException e) { iconeOffre = null; }
            }
            if (iconeOffre != null && a > 0.3f) {
                g.pose().pushMatrix();
                g.pose().translate(x - 0.5f, y - 0.5f);
                g.pose().scale(0.6875f, 0.6875f);
                g.item(iconeOffre, 0, 0);
                g.pose().popMatrix();
                return;
            }
        }
        EffetsIcones.poser(g, o.ic() == null ? "repas" : o.ic(), x, y, 10, DaKit.blanc(a));
    }

    private static void souligne(GuiGraphicsExtractor g, float x0, float x1, float y, int col) {
        int k = DaKit.echelleGui();
        DaKit.debutPx(g);
        g.fill(Math.round(x0 * k), Math.round(y * k), Math.round(x1 * k), Math.round(y * k) + 1, col);
        DaKit.finPx(g);
    }

    static String touche(KeyMapping km, String def) {
        if (km == null) return def;
        String save = km.saveString();
        if ("key.keyboard.backspace".equals(save)) return "Retour";
        if ("key.keyboard.enter".equals(save)) return "Entrée";
        if ("key.keyboard.unknown".equals(save)) return "—";
        String s = km.getTranslatedKeyMessage().getString();
        return s.length() > 8 ? s.substring(0, 8) : s.toUpperCase(java.util.Locale.ROOT);
    }
}
