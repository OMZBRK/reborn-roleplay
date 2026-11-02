package fr.reborn.hud.notif;

import fr.reborn.hud.element.HudAnchor;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementState;
import fr.reborn.hud.mixin.HudAccessor;
import fr.reborn.hud.mixin.SousTitresAccessor;
import fr.reborn.hud.screenshot.CapturePreview;
import fr.reborn.hud.ui.TexteRaster;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

/**
 * <b>Colonne bas-droite</b> du lot 2 « Reprises Ketto » (Game Plan n° 17, maquettes c1 à c4, LISEZMOI v2) : qui parle
 * en haut ({@code voice/QuiParleHud}), les notifications au milieu ({@link NotifHud}), les rappels de touches en bas
 * ({@code keybind/RappelsTouches}), alignés à droite, sans aucun fond. <b>Une seule échelle</b> pour les trois :
 * k = kHud / 2 (plancher kHud = 2 — à 1280×720 l'échelle menu vaut 1 et tout tomberait à demi-taille), distances
 * mesurées en 1080p depuis les bords ; l'échelle de l'élément dans l'éditeur de HUD s'y ajoute (qui parle, rappels).
 *
 * <p>Ce que les trois partagent :
 * <ul>
 *   <li><b>sous-titres vanilla</b> (option d'accessibilité, en bas à droite, à l'échelle d'interface) : les rappels se
 *       masquent ; qui parle et les notifications remontent ensemble, 15 px au-dessus de la case la plus haute, tout
 *       de suite, et ne redescendent qu'après 1 s sans en avoir eu besoin (pas de saut à chaque son) ;</li>
 *   <li><b>carte d'aperçu de capture</b> (6 s après F2, en unités kHud) : notifications et rappels masqués, qui parle
 *       reste au-dessus.</li>
 * </ul>
 * Rien n'est alloué par image.
 */
public final class BasDroite {

    /** Écart (px 1080p) entre la case de sous-titres la plus haute et le bas des notifications remontées. */
    private static final int MARGE_SOUS_TITRES = 15;
    /** Bas de la zone des notifications (px 1080p depuis le bas) : centre de la plus basse (128) moins 12. */
    static final int BAS_NOTIFS = 116;
    /** Les sous-titres partis, la colonne attend 1 s puis redescend en glissant. */
    private static final long DESCENTE_MS = 1000, GLISSE_MS = 120;

    private static double remontee;
    private static long besoinA, calculA = -1;

    private BasDroite() {}

    /** k = kHud / 2 : 1 en 1080p et en 720p (plancher), 1,5 en 1440p, 2 en 4K. */
    public static double k0() { return TexteRaster.kHud() / 2.0; }

    /** Carte d'aperçu de capture affichée : notifications et rappels masqués. */
    public static boolean carteCapture() { return CapturePreview.INSTANCE.visible(); }

    /** Option « sous-titres » de Minecraft active : rappels masqués. */
    public static boolean sousTitres(Minecraft mc) {
        return mc.options != null && Boolean.TRUE.equals(mc.options.showSubtitles().get());
    }

    /** Haut (px écran) de la case de sous-titres la plus haute affichée, -1 s'il n'y en a pas. */
    private static int hautSousTitres(Minecraft mc) {
        if (!sousTitres(mc)) return -1;
        int n;
        try {
            n = ((SousTitresAccessor) ((HudAccessor) mc.gui.hud).reborn$sousTitres()).reborn$audibles().size();
        } catch (RuntimeException e) {
            return -1;
        }
        if (n <= 0) return -1;
        var win = mc.getWindow();
        // ligne i centrée à guiHeight − 35 − 10 i, case de ±5 unités GUI (SubtitleOverlay#extractRenderState)
        return (win.getGuiScaledHeight() - 35 - 10 * (n - 1) - 5) * Math.max(1, win.getGuiScale());
    }

    /**
     * Remontée commune (px, ≤ 0) de qui parle et des notifications au-dessus des sous-titres vanilla. Monte tout de
     * suite, redescend après 1 s ; calculée une fois par image (le premier des deux éléments qui la demande).
     */
    public static int remontee(Minecraft mc) {
        long now = Util.getMillis();
        if (now != calculA) {
            long d = calculA < 0 ? 0 : Math.max(0, Math.min(250, now - calculA));
            calculA = now;
            int haut = hautSousTitres(mc);
            double k = k0();
            int cible = 0;
            if (haut >= 0) {
                int basNotifs = mc.getWindow().getHeight() - TexteRaster.arrondi(BAS_NOTIFS * k);
                cible = Math.min(0, haut - TexteRaster.arrondi(MARGE_SOUS_TITRES * k) - basNotifs);
            }
            if (cible <= remontee) {
                remontee = cible;
                besoinA = now;
            } else if (now - besoinA >= DESCENTE_MS) {
                remontee += (cible - remontee) * Math.min(1.0, d / (double) GLISSE_MS);
                if (cible - remontee < 0.5) remontee = cible;
            }
        }
        return (int) Math.round(remontee);
    }

    /**
     * Coin bas droit (px écran) de la boîte d'un élément de la colonne, placée comme {@code HudElementBounds#currentFor}
     * (ancre, décalage et échelle de l'éditeur) mais sans arrondi aux unités GUI : {@code out[0]} = bord droit,
     * {@code out[1]} = bord bas. La boîte par défaut est donnée en px à l'échelle 1 (gauche, haut, largeur, hauteur).
     */
    public static void placer(HudElement e, HudElementState st, int gs, double gauche, double haut, double largeur,
                              double hauteur, double[] out) {
        HudAnchor an = st.effectiveAnchor(e);
        double s = st.scale(), lw = largeur * s, lh = hauteur * s;
        double ax = gauche + largeur * an.fx + st.x() * (double) gs, ay = haut + hauteur * an.fy + st.y() * (double) gs;
        out[0] = ax - lw * an.fx + lw;
        out[1] = ay - lh * an.fy + lh;
    }
}
