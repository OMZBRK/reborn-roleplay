package fr.reborn.hud.notif;

import fr.reborn.hud.menu.inventory.SlotItem;
import fr.reborn.hud.sol.Rarete;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.TexteCache;
import fr.reborn.hud.ui.TexteRaster;
import fr.reborn.hud.ui.TexteRaster.Ancre;
import fr.reborn.hud.ui.TexteRaster.Masque;
import fr.reborn.hud.ui.TexteRaster.Police;
import fr.reborn.hud.ui.TexteRaster.Preset;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pile de <b>notifications en bas à droite</b> (maquette 03_notifications, DA §6 règle 9), au milieu de la colonne
 * bas-droite du lot 2 ({@link BasDroite}, maquette c1, LISEZMOI v2) : une ligne de texte petit, une petite icône (objet,
 * picto d'effet, micro teinté ou petit carré d'accent ; plus de sceau kanji depuis le 18/10 : le {@code kj} envoyé par
 * le serveur devient ce carré), un filet d'or d'1 px. Pas de cadre, pas de bouton, <b>plus de voile</b> (lot 2 : aucun
 * fond derrière les textes flottants).
 * <ul>
 *   <li>4 lignes au plus, la plus récente en bas, les anciennes pâlissent ;</li>
 *   <li>3 s chacune, fondu 0,15 s / 0,4 s ;</li>
 *   <li>gains identiques cumulés sur 1,5 s (+1, +1, +1 → +3).</li>
 * </ul>
 * <b>Gabarit</b> (px 1080p × k, k = kHud / 2, plancher kHud = 2 : une seule échelle pour la colonne) : ligne la plus
 * récente centrée à 128 px du bas (952), pas de 26 ; texte au moteur du lot ({@link TexteRaster}, Inter Bold puis Inter
 * Medium, capitales 10 px, préréglage HUD) fini à 60 px du bord droit (1860, le bord des libellés des rappels) ; icône
 * de 20 px 4 px avant le texte ; filet d'or de 15 px à 25 px du bord (1895), au contour rond des formes. Masquées
 * pendant la carte d'aperçu de capture, remontées au-dessus des sous-titres vanilla. Rien n'est alloué par image :
 * textes, styles et largeurs composés à la réception, lignes expirées retirées sur place.
 */
public final class NotifHud {

    private static final long VIE = 3000, ENTREE = 150, SORTIE = 400, CUMUL = 1500;
    private static final int MAX = 4;
    private static final float[] PALEUR = {1f, 0.82f, 0.6f, 0.38f};
    static final int CREME_DOUX = 0xFFC2B59A, VERM = 0xFFD2523E, AMBRE = 0xFFE0A050;
    /** Texte principal (#F3F0EA, le blanc du HUD du lot 2 ; l'ancien crème DaKit.CREME). */
    private static final int HUD = 0xFFF3F0EA;

    /* ---------------------------------------------------------------- gabarit 1080p (px, × k) */
    /** Centre de la ligne la plus récente : 128 px du bas (952 en 1080p, ancien sh − 60 u remonté de 4 u), pas de 26. */
    private static final int BAS = 128, PAS = 26;
    /** Fin du texte à 60 px du bord droit (1860), filet de 15 px à 25 px du bord (1895). */
    private static final int DROITE_TEXTE = 60, FILET = 25, FILET_H = 15;
    /** Capitales 10 px ; 4 px entre deux morceaux et avant l'icône ; icône de 20 px (objet : 16 px), carré de 6 px. */
    private static final int CAP = 10, ECART = 4, ICONE = 20, OBJET = 16, MICRO = 16, MARQUE = 6, CASE_MARQUE = 8;
    private static final float FILET_ALPHA = 0.80f;

    /**
     * Un morceau de texte : style (police, couleur) composé à la réception, largeur mesurée une fois par échelle. Un
     * cumul (+2 → +3) change le texte sur place : la poignée garde l'ancienne couche jusqu'à la nouvelle (pas de trou).
     */
    private static final class Morceau {
        String texte;
        TexteCache.Style style;
        final TexteCache.Texte poignee = new TexteCache.Texte();
        double largeur = -1, kLargeur;

        Morceau(String texte, TexteCache.Style style) {
            this.texte = texte;
            this.style = style;
        }
    }

    private static final class Ligne {
        final String cle;
        final long nee;
        long maj;
        int q;
        final List<Morceau> morceaux = new ArrayList<>(3);
        ItemStack icone;
        /** Petit carré d'accent (vermillon / ambre / or) quand le serveur demandait un sceau ({@code kj}). */
        boolean marque;
        int marqueCol;
        /** Picto d'effet ({@code textures/gui/effets/<nom>.png}, nourriture) à la place du carré. */
        String picto;
        /** Micro teinté (ligne « Voix », touche « Portée de la voix ») à la place du carré. */
        boolean micro;
        int microTeinte;

        Ligne(String cle, long now) {
            this.cle = cle;
            this.nee = now;
            this.maj = now;
        }

        void liberer() {
            for (Morceau m : morceaux) m.poignee.liberer();
        }
    }

    private static final List<Ligne> LIGNES = new ArrayList<>();
    /**
     * Filet d'or : une poignée par rang (le même masque, l'opacité de l'âge cuite dans la couche, 80 % × 100 / 82 / 60 /
     * 38 %, comme la maquette ; les fondus passent par les sommets).
     */
    private static final TexteCache.Texte[] FILET_T = {new TexteCache.Texte(), new TexteCache.Texte(),
        new TexteCache.Texte(), new TexteCache.Texte()};
    private static Masque filet;
    private static double filetK = -1;

    private NotifHud() {}

    public static void clear() {
        synchronized (LIGNES) {
            for (Ligne l : LIGNES) l.liberer();
            LIGNES.clear();
        }
    }

    /** Des notifications sont affichées (la colonne des rappels passe alors à 3 lignes). */
    public static boolean visible() {
        synchronized (LIGNES) {
            return !LIGNES.isEmpty();
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
            int colGras = "palier".equals(ton) ? AMBRE : HUD;
            l.marque = picto == null && kanji != null && !kanji.isBlank();   // le caractère lui-même n'est jamais dessiné
            l.marqueCol = accent;
            l.picto = picto;
            if (gras != null) l.morceaux.add(morceau(gras, Police.INTER_GRAS, colGras));
            if (texte != null) l.morceaux.add(morceau((gras != null ? "— " : "") + texte, Police.INTER_MEDIUM,
                "palier".equals(ton) ? DaKit.CREME_VAL : CREME_DOUX));
            ajouter(l);
        }
    }

    /**
     * Ligne « Voix » (touche « Portée de la voix », 31/10) : le petit micro à la couleur de la portée (vert / or / rouge,
     * gris si indisponible) au lieu de la marque, un mot en gras, la suite (« Voix » « : chuchotée »). Une seule ligne de
     * voix : un nouvel appui remplace la précédente (en bas, fondu d'entrée), elles ne s'empilent pas. Depuis le lot 2,
     * seulement quand la ligne de rappel « Portée de la voix » n'est pas affichée ({@code keybind/RappelsTouches}).
     */
    public static void voix(int teinte, String gras, String texte) {
        long now = System.currentTimeMillis();
        synchronized (LIGNES) {
            for (int i = LIGNES.size() - 1; i >= 0; i--) if ("voix".equals(LIGNES.get(i).cle)) LIGNES.remove(i).liberer();
            Ligne l = new Ligne("voix", now);
            l.micro = true;
            l.microTeinte = teinte;
            if (gras != null) l.morceaux.add(morceau(gras, Police.INTER_GRAS, HUD));
            if (texte != null) l.morceaux.add(morceau(texte, Police.INTER_MEDIUM, CREME_DOUX));
            ajouter(l);
        }
    }

    private static void composerObjet(Ligne l, boolean gain, String nom, Rarete r, String suite) {
        String q = (gain ? "+" : "−") + l.q;
        poser(l, 0, q, Police.INTER_GRAS, gain ? DaKit.OR : CREME_DOUX);
        int colNom = !gain ? CREME_DOUX : r.ordinal() >= Rarete.PEU_COMMUN.ordinal() ? r.argb() : HUD;
        poser(l, 1, nom, Police.INTER_MEDIUM, colNom);
        if (suite != null && !suite.isBlank()) poser(l, 2, (gain ? "— " : "· ") + suite, Police.INTER_MEDIUM, CREME_DOUX);
        else while (l.morceaux.size() > 2) l.morceaux.remove(l.morceaux.size() - 1).poignee.liberer();
    }

    /** Morceau {@code i} de la ligne : changé sur place s'il existe (cumul), ajouté sinon. */
    private static void poser(Ligne l, int i, String texte, Police p, int argb) {
        TexteCache.Style s = style(p, argb);
        if (i < l.morceaux.size()) {
            Morceau m = l.morceaux.get(i);
            if (!m.texte.equals(texte) || !m.style.equals(s)) {
                m.texte = texte;
                m.style = s;
                m.largeur = -1;
            }
        } else {
            l.morceaux.add(new Morceau(texte, s));
        }
    }

    private static Morceau morceau(String texte, Police p, int argb) { return new Morceau(texte, style(p, argb)); }

    /** Style du lot : Inter Bold / Medium, préréglage HUD, fini à droite, couleur nommée ou écrite (« #RRGGBB »). */
    private static TexteCache.Style style(Police p, int argb) {
        int rgb = argb & 0xFFFFFF;
        if (p == Police.INTER_GRAS && rgb == (HUD & 0xFFFFFF)) return TexteCache.Style.NOTIF;
        if (p == Police.INTER_MEDIUM && rgb == (CREME_DOUX & 0xFFFFFF)) return TexteCache.Style.NOTIF_DETAIL;
        return new TexteCache.Style(p, Preset.HUD, TexteRaster.cleRgb(rgb), Ancre.DROITE, true, false);
    }

    private static void ajouter(Ligne l) {
        LIGNES.add(l);
        while (LIGNES.size() > MAX) LIGNES.remove(0).liberer();
    }

    /* ================================================================ rendu */

    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.gui.hud.isHidden()) return;
        long now = System.currentTimeMillis();
        synchronized (LIGNES) {
            for (int i = LIGNES.size() - 1; i >= 0; i--) if (now - LIGNES.get(i).maj > VIE) LIGNES.remove(i).liberer();
            if (LIGNES.isEmpty()) return;
            // carte d'aperçu de capture (6 s après F2) : la colonne masque ses notifications (maquette c4)
            if (BasDroite.carteCapture() || !Police.INTER_GRAS.chargee() || !Police.INTER_MEDIUM.chargee()) return;
            var win = mc.getWindow();
            double k = BasDroite.k0();
            int W = win.getWidth(), base = win.getHeight() + BasDroite.remontee(mc);
            if (filet == null || filetK != k) {
                filet = Masque.barres(new int[]{TexteRaster.arrondi(FILET_H * k)}, Math.max(1, TexteRaster.arrondi(k)), 1);
                filetK = k;
            }
            DaKit.debutPx(g);
            int n = LIGNES.size();
            for (int i = 0; i < n; i++) {
                Ligne l = LIGNES.get(n - 1 - i);          // la plus récente en bas
                float fondu = Math.min(1f, (now - l.nee) / (float) ENTREE);
                long reste = VIE - (now - l.maj);
                if (reste < SORTIE) fondu *= Math.max(0f, reste / (float) SORTIE);
                int rang = Math.min(i, PALEUR.length - 1);
                if (fondu * PALEUR[rang] <= 0.02f) continue;
                ligne(g, l, W, base - TexteRaster.arrondi((BAS + PAS * i) * k), k, fondu, rang);
            }
            DaKit.finPx(g);
        }
    }

    /**
     * Une ligne en pixels écran (déjà dans {@link DaKit#debutPx}), centrée sur {@code yc} : texte, icône, filet ;
     * {@code fondu} = fondus d'entrée et de sortie, {@code rang} = âge de la ligne (0 = la plus récente, pâlit ensuite).
     */
    private static void ligne(GuiGraphicsExtractor g, Ligne l, int W, int yc, double k, float fondu, int rang) {
        float a = fondu * PALEUR[rang];
        double cap = CAP * k, x = W - TexteRaster.arrondi(DROITE_TEXTE * k);
        int ecart = TexteRaster.arrondi(ECART * k);
        for (int j = l.morceaux.size() - 1; j >= 0; j--) {
            Morceau m = l.morceaux.get(j);
            if (m.largeur < 0 || m.kLargeur != k) {
                m.largeur = TexteRaster.largeur(m.texte, m.style.police(), cap, 0, true);
                m.kLargeur = k;
            }
            m.poignee.ligne(m.texte, m.style, cap, k, 0, x, yc).dessinerPx(g, a);
            x -= m.largeur + ecart;
        }
        int s = TexteRaster.arrondi(ICONE * k);
        int ix = (int) Math.rint(x - s), iy = (int) Math.rint(yc - s / 2.0);
        if (l.icone != null && a > 0.3f) {
            int o = TexteRaster.arrondi(OBJET * k);
            g.pose().pushMatrix();
            g.pose().translate(ix + (s - o) / 2f, iy + (s - o) / 2f);
            g.pose().scale(o / 16f, o / 16f);
            g.item(l.icone, 0, 0);
            g.pose().popMatrix();
        } else if (l.picto != null) {
            fr.reborn.hud.nourriture.EffetsIcones.poserPx(g, l.picto, ix, iy, s, DaKit.blanc(a));
        } else if (l.micro) {
            // l'icône micro des vitals (16 px), teintée à la couleur de la portée
            int o = TexteRaster.arrondi(MICRO * k);
            g.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, fr.reborn.hud.runtime.VitalsHud.micClair(),
                ix + (s - o) / 2, iy + (s - o) / 2, 0f, 0f, o, o, 16, 16, 16, 16, DaKit.alpha(l.microTeinte, a));
        } else if (l.marque) {
            int c = Math.max(4, TexteRaster.arrondi(MARQUE * k)), sm = TexteRaster.arrondi(CASE_MARQUE * k);
            int mx = (int) Math.rint(x - sm) + (sm - c) / 2, my = yc - c / 2;
            g.fill(mx, my, mx + c, my + c, DaKit.alpha(marqueOmbre(l.marqueCol), a));
            g.fill(mx + 1, my + 1, mx + c - 1, my + c - 1, DaKit.alpha(l.marqueCol, a));
        }
        // filet d'or d'1 px à droite, au contour rond des formes (lisible sur la neige et le ciel)
        FILET_T[rang].forme(filet, DaKit.OR & 0xFFFFFF, k, FILET_ALPHA * PALEUR[rang], W - TexteRaster.arrondi(FILET * k),
            yc - filet.h() / 2).dessinerPx(g, fondu);
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
