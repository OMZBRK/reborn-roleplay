package fr.reborn.hud.notif;

import fr.reborn.hud.menu.inventory.SlotItem;
import fr.reborn.hud.sol.Rarete;
import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pile de <b>notifications en bas à droite</b> (maquette 03_notifications, DA §6 règle 9) : une ligne de texte petit
 * (Inter 7), une petite icône (objet 8 unités, picto d'effet ou petit carré d'accent ; plus de sceau kanji depuis le
 * 18/10 : le {@code kj} envoyé par le serveur devient ce carré), un voile nuit très léger, un filet d'or d'1 px. Pas de
 * cadre, pas de bouton.
 * <ul>
 *   <li>4 lignes au plus, la plus récente en bas, les anciennes pâlissent ;</li>
 *   <li>3 s chacune, fondu 0,15 s / 0,4 s ;</li>
 *   <li>gains identiques cumulés sur 1,5 s (+1, +1, +1 → +3).</li>
 * </ul>
 * Dessinée en unités menu ({@link DaKit#echelleMenu}) : même taille à l'écran quelle que soit l'échelle du HUD.
 */
public final class NotifHud {

    private static final Identifier VOILE = DaKit.tex("inventaire", "voile_droite");
    private static final long VIE = 3000, ENTREE = 150, SORTIE = 400, CUMUL = 1500;
    private static final int MAX = 4;
    private static final float[] PALEUR = {1f, 0.82f, 0.6f, 0.38f};
    static final int CREME_DOUX = 0xFFC2B59A, VERM = 0xFFD2523E, AMBRE = 0xFFE0A050;

    private record Morceau(Component c, int col) {}

    private static final class Ligne {
        final String cle;
        final long nee;
        long maj;
        int q;
        final List<Morceau> morceaux = new ArrayList<>();
        ItemStack icone;
        /** Petit carré d'accent (vermillon / ambre / or) quand le serveur demandait un sceau ({@code kj}). */
        boolean marque;
        int marqueCol;
        /** Picto d'effet ({@code textures/gui/effets/<nom>.png}, nourriture) à la place du carré. */
        String picto;
        float largeur = -1;

        Ligne(String cle, long now) {
            this.cle = cle;
            this.nee = now;
            this.maj = now;
        }
    }

    private static final List<Ligne> LIGNES = new ArrayList<>();

    private NotifHud() {}

    public static void clear() {
        synchronized (LIGNES) {
            LIGNES.clear();
        }
    }

    /** « +3 Kunai » / « −1 Ration · lâchée ». Les gains identiques récents se cumulent. */
    public static void objet(boolean gain, int q, String nom, String rar, String mat, String model, String suite) {
        long now = System.currentTimeMillis();
        String cle = (gain ? "gain|" : "perte|") + nom + "|" + rar + "|" + (suite == null ? "" : suite);
        synchronized (LIGNES) {
            for (Ligne l : LIGNES) {
                if (l.cle.equals(cle) && now - l.maj < CUMUL && gain) {
                    l.q += q;
                    l.maj = now;
                    composerObjet(l, true, nom, Rarete.parseOr(rar, Rarete.COMMUN), suite);
                    return;
                }
            }
            Ligne l = new Ligne(cle, now);
            l.q = q;
            if (mat != null) {
                try {
                    l.icone = new SlotItem(mat, model, 1, null, 0).toStack();
                } catch (RuntimeException ignored) { }
            }
            composerObjet(l, gain, nom, Rarete.parseOr(rar, Rarete.COMMUN), suite);
            ajouter(l);
        }
    }

    /** Une ligne simple : marque, mot en gras, suite (« Plus assez de place — libère une case »). */
    public static void message(String ton, String kanji, String gras, String texte) {
        message(ton, kanji, gras, texte, null);
    }

    /** Idem avec un petit picto d'effet ({@code "ic"} : regeneration, ryo, faim…) au lieu de la marque. */
    public static void message(String ton, String kanji, String gras, String texte, String picto) {
        long now = System.currentTimeMillis();
        String cle = "msg|" + ton + "|" + gras + "|" + texte;
        synchronized (LIGNES) {
            for (Ligne l : LIGNES) {
                if (l.cle.equals(cle)) { l.maj = now; return; }   // même message : on le prolonge
            }
            Ligne l = new Ligne(cle, now);
            int accent = switch (ton == null ? "" : ton) {
                case "refus", "etat" -> VERM;
                case "palier" -> AMBRE;
                default -> DaKit.OR;
            };
            int colGras = "palier".equals(ton) ? AMBRE : DaKit.CREME;
            l.marque = picto == null && kanji != null && !kanji.isBlank();   // le caractère lui-même n'est jamais dessiné
            l.marqueCol = accent;
            l.picto = picto;
            if (gras != null) l.morceaux.add(new Morceau(DaKit.interGras(gras), colGras));
            if (texte != null) l.morceaux.add(new Morceau(DaKit.inter((gras != null ? "— " : "") + texte),
                "palier".equals(ton) ? DaKit.CREME_VAL : CREME_DOUX));
            ajouter(l);
        }
    }

    private static void composerObjet(Ligne l, boolean gain, String nom, Rarete r, String suite) {
        l.morceaux.clear();
        l.largeur = -1;
        String q = (gain ? "+" : "−") + l.q;
        l.morceaux.add(new Morceau(DaKit.interGras(q), gain ? DaKit.OR : CREME_DOUX));
        int colNom = !gain ? CREME_DOUX : r.ordinal() >= Rarete.PEU_COMMUN.ordinal() ? r.argb() : DaKit.CREME;
        l.morceaux.add(new Morceau(DaKit.inter(nom), colNom));
        if (suite != null && !suite.isBlank()) {
            l.morceaux.add(new Morceau(DaKit.inter((gain ? "— " : "· ") + suite), CREME_DOUX));
        }
    }

    private static void ajouter(Ligne l) {
        LIGNES.add(l);
        while (LIGNES.size() > MAX) LIGNES.remove(0);
    }

    /* ================================================================ rendu */

    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden()) return;
        List<Ligne> vue;
        long now = System.currentTimeMillis();
        synchronized (LIGNES) {
            LIGNES.removeIf(l -> now - l.maj > VIE);
            if (LIGNES.isEmpty()) return;
            vue = new ArrayList<>(LIGNES);
        }
        var win = mc.getWindow();
        int km = DaKit.echelleMenu();
        int sw = win.getWidth() / km, sh = win.getHeight() / km;
        Font f = mc.font;
        DaKit.unites(g, km);
        float xr = sw - 12, ybas = sh - 60;
        for (int i = 0; i < vue.size(); i++) {
            Ligne l = vue.get(vue.size() - 1 - i);          // la plus récente en bas
            float a = Math.min(1f, (now - l.nee) / (float) ENTREE);
            long reste = VIE - (now - l.maj);
            if (reste < SORTIE) a *= Math.max(0f, reste / (float) SORTIE);
            a *= PALEUR[Math.min(i, PALEUR.length - 1)];
            if (a <= 0.02f) continue;
            ligne(g, f, l, xr, ybas - i * 13, a);
        }
        DaKit.finUnites(g);
    }

    private static void ligne(GuiGraphicsExtractor g, Font f, Ligne l, float xr, float ym, float a) {
        if (l.largeur < 0) {
            float w = 0;
            for (Morceau m : l.morceaux) w += DaKit.largeur7(f, m.c()) + 2;
            l.largeur = w;
        }
        float W = l.largeur + (l.icone != null || l.picto != null ? 10 : l.marque ? 6 : 0) + 13;
        int x0 = Math.round(xr - W);
        DaKit.etire(g, VOILE, x0, Math.round(ym) - 6, Math.round(W), 12, 64, 16, DaKit.blanc(a));
        // filet d'or d'1 px écran, à droite
        int k = DaKit.echelleGui();
        DaKit.debutPx(g);
        g.fill(Math.round(xr * k) - 1, Math.round((ym - 4) * k), Math.round(xr * k), Math.round((ym + 3.5f) * k),
            DaKit.argb(0xD9A95E, 0.8f * a));
        DaKit.finPx(g);
        float x = xr - 5;
        for (int j = l.morceaux.size() - 1; j >= 0; j--) {
            Morceau m = l.morceaux.get(j);
            float w = DaKit.largeur7(f, m.c());
            DaKit.inter7o(g, f, m.c(), x - w, ym, DaKit.alpha(m.col(), a), 0);
            x -= w + 2;
        }
        if (l.icone != null && a > 0.3f) {
            g.pose().pushMatrix();
            g.pose().translate(x - 9, ym - 4);
            g.pose().scale(0.5f, 0.5f);
            g.item(l.icone, 0, 0);
            g.pose().popMatrix();
        } else if (l.picto != null) {
            fr.reborn.hud.nourriture.EffetsIcones.poser(g, l.picto, x - 10, ym - 4.5f, 9, DaKit.blanc(a));
        } else if (l.marque) {
            int k2 = DaKit.echelleGui();
            DaKit.debutPx(g);
            int mx = Math.round((x - 4) * k2), my = Math.round(ym * k2), c = Math.max(2, k2 * 3 / 2);
            g.fill(mx - c, my - c, mx + c, my + c, DaKit.alpha(marqueOmbre(l.marqueCol), a));
            g.fill(mx - c + 1, my - c + 1, mx + c - 1, my + c - 1, DaKit.alpha(l.marqueCol, a));
            DaKit.finPx(g);
        }
    }

    /** Bord sombre de la marque (accent assombri de moitié). */
    private static int marqueOmbre(int col) {
        int r = (col >> 16 & 0xFF) / 2, v = (col >> 8 & 0xFF) / 2, b = (col & 0xFF) / 2;
        return 0xFF000000 | r << 16 | v << 8 | b;
    }

    static String fmt(double v) {
        return String.format(Locale.FRANCE, "%.1f", v);
    }
}
