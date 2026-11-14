package fr.reborn.hud.combat;

import fr.reborn.hud.ui.DaKit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Ce que le saut chargé (lot 5, étape A) écrit à l'écran : la <b>barre de charge</b> près du réticule, là où l'on regarde,
 * la même en 1re et en 3e personne, et une ligne de texte fine.
 *
 * <p><b>Barre</b> (6e tour du 10/11) : en pixels d'interface entiers, sans fond ni plaque ; remplie du violet de la
 * poussière ({@link SautApercu#VIOLET}, plus pâle sous le seuil), la piste vide en violet très transparent (pas un gris
 * de plaque), un liseré sombre d'un pixel à faible opacité pour la tenir sur un ciel clair, un repère crème au seuil pris
 * dans l'épaisseur, une crête claire au bout du plein. Jauge <b>verticale fine juste à droite du réticule</b>
 * ({@value #A_L} × {@value #A_H} px, à {@value #A_DX} px du centre), remplie du bas : choisie par le propriétaire le 10/11
 * (« a c'est mieux ») contre un croissant et une règle horizontale, retirés. Elle apparaît en 0,1 s au début de la charge,
 * s'efface en 0,15 s à la relâche.
 *
 * <p>La ligne de texte, au-dessus de la barre d'action, centrée, seulement si elle dit quelque chose : la raison d'un refus
 * en vermillon (1,6 s : « Pas assez de chakra », « Étourdi »…), ou, pendant le geste, « Portée bornée » quand la distance
 * de vue raccourcit le saut, ou « Charge bornée par le chakra » quand la réserve ne paie pas la charge entière.
 */
public final class SautHud {

    private static final int AU_DESSUS = 36;
    private static final int VERM = 0xFFE0563C;
    private static final long GO_MS = 450L, REFUS_MS = 1600L;
    /* barre : plein (violet de la poussière), crête, piste vide, liseré sombre, repère du seuil */
    private static final int PLEIN = SautApercu.VIOLET, CRETE = 0xD9CCFA, PISTE = SautApercu.VIOLET, LISERE = 0x0E0A18, REPERE = 0xF4E8D2;
    private static final float PISTE_A = 0.22f, LISERE_A = 0.26f, SOUS_SEUIL_A = 0.55f;
    /* jauge verticale (px d'interface) : largeur, hauteur, écart au centre du réticule */
    private static final int A_L = 3, A_H = 24, A_DX = 10;

    private static float alpha;
    private static long dernierRendu;
    /* textes mis en cache : aucune chaîne ni aucun Component créés par image (seulement quand le texte change) */
    private static final net.minecraft.network.chat.Component TXT_CHAKRA = DaKit.inter("Charge bornée par le chakra");
    private static String txtCache;
    private static net.minecraft.network.chat.Component compCache;
    private static long porteeCache = Long.MIN_VALUE;
    private static net.minecraft.network.chat.Component compPortee;
    /** Banc : horloge figée, pas de fondu d'apparition. */
    public static boolean banc;

    private SautHud() {}

    /** Le geste occupe la place du filet de sprint (lu par {@code AllureHud}) : la ligne de texte s'y écrit. */
    public static boolean occupe() {
        ChakraJump j = ChakraJump.INSTANCE;
        long now = ChakraJump.horloge.getAsLong();
        return j.phase() == ChakraJump.Phase.CHARGE || j.phase() == ChakraJump.Phase.ATTENTE
            || (j.goMs() >= 0 && now - j.goMs() < GO_MS) || (j.refusMs() >= 0 && now - j.refusMs() < REFUS_MS);
    }

    public static void render(GuiGraphicsExtractor g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options == null || mc.gui.hud.isHidden() || mc.gui.screen() != null) return;
        barre(g, mc);

        ChakraJump j = ChakraJump.INSTANCE;
        long now = ChakraJump.horloge.getAsLong();
        long reel = System.currentTimeMillis();
        float dt = dernierRendu == 0 ? 0.05f : Math.min(0.1f, Math.max(0f, (reel - dernierRendu) / 1000f));
        dernierRendu = reel;

        boolean charge = j.phase() == ChakraJump.Phase.CHARGE, attente = j.phase() == ChakraJump.Phase.ATTENTE;
        boolean go = !charge && !attente && j.goMs() >= 0 && now - j.goMs() < GO_MS;
        boolean refus = !charge && !attente && j.refusMs() >= 0 && now - j.refusMs() < REFUS_MS;
        boolean voulu = charge || attente || go || refus;
        // banc (horloge figée) : apparition franche
        if (banc && voulu) alpha = 1f;
        alpha = voulu ? Math.min(1f, alpha + dt * 8f) : Math.max(0f, alpha - dt * 3f);
        if (alpha <= 0.01f) return;
        float a = alpha;
        if (go) a *= 1f - Math.max(0f, (now - j.goMs() - 200f) / (GO_MS - 200f));
        if (refus) a *= Math.min(1f, (REFUS_MS - (now - j.refusMs())) / 300f);
        if (a <= 0.01f) return;

        // chakra connu (vitals) : la part payable
        double payable = 1;
        float ck = fr.reborn.hud.runtime.VitalsMode.chakraPart();
        if (ck >= 0) payable = SautChargeLoi.chargePayable(ck * 100.0, j.regles());

        net.minecraft.network.chat.Component txt = null;
        int tcol = DaKit.alpha(DaKit.CREME, 0.78f * a);
        if (refus) {
            String r = j.refusTexte();
            if (r != null && !r.isEmpty()) {
                if (!r.equals(txtCache)) { txtCache = r; compCache = DaKit.inter(r); }
                txt = compCache;
            }
            tcol = DaKit.alpha(VERM, a);
        } else if ((charge || attente || go) && j.bridePrevu()) {
            long n = Math.round(j.porteeBorne());
            if (n != porteeCache) { porteeCache = n; compPortee = DaKit.inter("Portée bornée : " + n + " blocs (distance de vue)"); }
            txt = compPortee;
        } else if (go && j.brideChakra()) {
            txt = TXT_CHAKRA;
        } else if (charge && ck >= 0 && payable < 0.999 && j.charge() >= payable) {
            txt = TXT_CHAKRA;
        }
        if (txt != null) {
            int gw = mc.getWindow().getGuiScaledWidth(), gh = mc.getWindow().getGuiScaledHeight();
            DaKit.inter7o(g, mc.font, txt, gw / 2f, gh - AU_DESSUS, tcol, 1);
        }
    }

    /* ================================================================ barre de charge */

    /**
     * La barre de charge autour du réticule (1re et 3e personne), en pixels d'interface entiers : on dessine en pixels écran
     * ({@link DaKit#debutPx}), une unité = {@code echelleGui} pixels, à partir du centre du réticule.
     */
    private static void barre(GuiGraphicsExtractor g, Minecraft mc) {
        SautApercu.maj();
        float a = SautApercu.barre();
        if (a <= 0.01f) return;
        ChakraJump j = ChakraJump.INSTANCE;
        float c = Math.max(0f, Math.min(1f, SautApercu.chargeBarre())), seuil = (float) j.regles().seuil();
        int u = DaKit.echelleGui();
        int cx = mc.getWindow().getGuiScaledWidth() / 2 * u, cy = mc.getWindow().getGuiScaledHeight() / 2 * u;
        int plein = DaKit.argb(PLEIN, (c + 1e-5f >= seuil ? 1f : SOUS_SEUIL_A) * a), crete = DaKit.argb(CRETE, a),
            piste = DaKit.argb(PISTE, PISTE_A * a), lisere = DaKit.argb(LISERE, LISERE_A * a), repere = DaKit.argb(REPERE, 0.95f * a);
        DaKit.debutPx(g);
        jauge(g, cx, cy, u, c, seuil, plein, crete, piste, lisere, repere);
        DaKit.finPx(g);
    }

    /** Jauge verticale à droite du réticule, remplie du bas ; repère du seuil en travers, dans l'épaisseur. */
    private static void jauge(GuiGraphicsExtractor g, int cx, int cy, int u, float c, float seuil,
                              int plein, int crete, int piste, int lisere, int repere) {
        int x0 = cx + A_DX * u, x1 = x0 + A_L * u, y1 = cy + A_H / 2 * u, y0 = y1 - A_H * u;
        int n = Math.round(A_H * c), yc = y1 - n * u, ys = y1 - Math.round(A_H * seuil) * u;
        g.fill(x0 - u, y0 - u, x1 + u, y0, lisere);
        g.fill(x0 - u, y1, x1 + u, y1 + u, lisere);
        g.fill(x0 - u, y0, x0, y1, lisere);
        g.fill(x1, y0, x1 + u, y1, lisere);
        if (yc > y0) g.fill(x0, y0, x1, yc, piste);
        if (n > 0) {
            g.fill(x0, yc + u, x1, y1, plein);
            g.fill(x0, yc, x1, yc + u, crete);
        }
        g.fill(x0, ys, x1, ys + u, repere);
    }
}
