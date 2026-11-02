package fr.reborn.hud.ost;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.communication.BandeHud;
import fr.reborn.hud.douane.DouaneClient;
import fr.reborn.hud.element.HudAnchor;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementBounds;
import fr.reborn.hud.element.HudElementState;
import fr.reborn.hud.menu.settings.RebornPrefs;
import fr.reborn.hud.staff.EffetsStaff;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.TexteCache;
import fr.reborn.hud.ui.TexteRaster;
import fr.reborn.hud.ui.TexteRaster.Masque;
import fr.reborn.hud.ui.TexteRaster.Police;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * <b>Lecteur OST en haut à droite</b> (lot 2 « Reprises Ketto », Game Plan n° 17 ; variante B validée le 01/11) —
 * élément {@link HudElement#OST} « Musique » de l'éditeur de HUD. mod-ost ne fait que publier l'état de la lecture
 * ({@link OstLien}) ; le bloc est tracé ici, au moteur du lot ({@link TexteRaster} / {@link TexteCache} : chaque texte
 * et chaque forme calculés une fois en couche, posés en un blit à 1:1 sur des pixels entiers).
 *
 * <p><b>Gabarit</b> (maquettes {@code b1} / {@code b2}, LISEZMOI v2, recette {@code _maquettes_lot2.ost}), en pixels
 * 1080p multipliés par k = kHud / 2 × l'échelle de l'élément dans l'éditeur — même taille quelle que soit l'échelle
 * d'interface du joueur :
 * <ul>
 *   <li>haut du bloc à 20 px, bord droit du filet à 24 px du bord ; groupe (barres + titre) et filet centrés sur le
 *       même axe, aucun fond ;</li>
 *   <li><b>trois barres</b> de 2 px au pas de 4 (10 px), 4 à 10 px de haut, posées sur la ligne de base du titre, 6 px
 *       avant lui, #F3F0EA à 85 % : elles ondulent tant que la piste joue à 100 %, figées (5, 8, 4) à 70 % ;</li>
 *   <li><b>titre</b> = la catégorie de la piste (décision du 01/11 : « Mystère », « Combat »…, jamais le nom de
 *       fichier), Inter Bold, capitales 12 px (em 16,70), #F3F0EA, préréglage HUD (contour 80 %, ombre 50 %) ;
 *       « … » quand le groupe dépasse 220 px ;</li>
 *   <li><b>filet</b> d'1 px DaKit.OR à 100 %, 6 px sous la ligne de base, groupe + 32 px, de 113 à 219 px et impair
 *       (le losange tombe pile au milieu), effacé sur le quart de chaque bout comme la boussole ; losange de rayon 2 px
 *       OR_CLAIR au centre. Formes au contour rond des formes (80 %) et ombre dure d'1 px (50 %) ;</li>
 *   <li>bloc de 22 px de haut.</li>
 * </ul>
 *
 * <p><b>Comportement</b> (brief §3.2) : nouvelle piste audible (lancement, reprise en cours de piste, entrée dans une
 * zone) → fondu d'entrée de 300 ms, 100 % pendant 10 s, puis 70 % (barres figées) tant que la piste s'entend ; option
 * « Masquer après 10 s » (éditeur de HUD, {@link RebornPrefs#ostMasquerApres10s}) : fondu de sortie de 800 ms à la
 * place. Même piste qui reboucle : rien ne change. Arrêt ou sortie de zone : fondu de 300 ms. <b>Pause</b> : barres
 * masquées, tout le bloc à 55 %, « ‖ » #F3F0EA à la place du losange ; à la reprise, 100 % pendant 4 s. Écran ouvert :
 * bloc masqué, minuteur <b>gelé</b>. Masqué en F1 et en mode photo (le HUD entier est coupé), au KO
 * ({@code ko/KoMasqueHud}), pendant la douane côté candidat ({@code douane/DouaneClient}) et sous l'écran noir du staff
 * (enregistré avant {@code staff-effets}). <b>Cohabitation</b> : sous l'indicateur « Mode staff / animation /
 * examinateur » (28 px plus bas), et sous la pile des bandes de la cloche quand elle le recouvre (1366×768).
 *
 * <p>Les opacités d'état (100 / 70 / 55 %, barres à 85 %) sont <b>cuites dans les couches des formes</b>, comme sur les
 * maquettes : une poignée par état ; le passage d'un état à l'autre est un fondu croisé de 400 ms, le titre passe par
 * l'alpha des sommets (uniforme, comme le Python). Coût par image : quelques comparaisons et 6 à 9 blits, <b>aucune
 * allocation</b> (gabarit, troncature et masques recalculés seulement au changement de titre ou d'échelle).
 */
public final class OstHud {

    private static final Identifier ID = Identifier.fromNamespaceAndPath("reborn-hud", "ost");

    /* ---------------------------------------------------------------- gabarit 1080p (px, × k) */
    private static final int HAUT = 20, DROITE = 24, GROUPE_MAX = 220, FILET_MIN = 112, FILET_MAX = 220, FILET_MARGE = 32;
    private static final int BARRE_L = 2, BARRE_PAS = 4, ECART = 6, CAP = 12, SOUS_BASE = 6;
    /** Hauteur de la boîte de l'éditeur : le bloc de 22 px, contour et ombre compris. */
    private static final int BOITE_H = 24;
    /** Indicateur « Mode staff / animation / examinateur » visible : le bloc descend de 28 px (maquette b2). */
    private static final int SOUS_INDICATEUR = 28;
    /**
     * Pile des bandes de la cloche à moins de 16 px du bloc (à 1280×720, une bande pleine largeur finit à 8 px du
     * titre long) : le bloc passe dessous, 8 px sous la dernière bande.
     */
    private static final int MARGE_BANDES = 16, SOUS_BANDES = 8;
    /** Hauteurs des barres (px 1080p) : 4 à 10 quand elles ondulent ; figées à 70 % comme la maquette b2. */
    private static final int H_MIN = 4, H_MAX = 10;
    private static final int[] FIGEES = {5, 8, 4};
    /** Barres de l'aperçu de l'éditeur (maquette b2, « 100 % »). */
    private static final int[] BARRES_B2 = {6, 10, 8};

    /* ---------------------------------------------------------------- couleurs */
    private static final int BLANC_HUD = 0xF3F0EA, OR = 0xD9A95E, OR_CLAIR = 0xF2D08A;

    /* ---------------------------------------------------------------- temps (brief §3.2) */
    private static final long PLEIN_MS = 10_000, REPRISE_MS = 4_000, PAUSE_OPTION_MS = 4_000;
    private static final long ENTREE_MS = 300, SORTIE_MS = 300, SORTIE_OPTION_MS = 800, FONDU_ETAT_MS = 400;

    /* ---------------------------------------------------------------- états visuels */
    private static final int PLEIN = 0, ATTENUE = 1, EN_PAUSE = 2;
    /** Opacité du bloc par état : 100 %, 70 % après 10 s, 55 % en pause. */
    private static final float[] OPACITE = {1f, 0.70f, 0.55f};
    /** Opacité propre des barres. */
    private static final float BARRES = 0.85f;

    /** Banc ({@code audit/FeaturePreview}) : hauteurs imposées des barres qui ondulent (null en jeu). */
    public static int[] debugBarres;

    /* ---------------------------------------------------------------- poignées (une par texte, par forme et par état) */
    private static final TexteCache.Texte TITRE = new TexteCache.Texte(), TITRE_APERCU = new TexteCache.Texte();
    private static final TexteCache.Texte[] FILET = poignees(3), FILET_APERCU = poignees(3), LOSANGE = poignees(2);
    private static final TexteCache.Texte PAUSE = new TexteCache.Texte();
    /** Barres qui ondulent : une poignée par hauteur (85 %) ; barres figées : une par barre (85 % × 70 %). */
    private static final TexteCache.Texte[] BARRE = poignees(H_MAX - H_MIN + 1), BARRE_FIGEE = poignees(3);

    private static final Gabarit JEU = new Gabarit(), APERCU = new Gabarit();
    private static final Formes FORMES = new Formes();
    private static final Placement PL = new Placement(), PL_APERCU = new Placement();
    private static final int[] ANIM = new int[3];

    private OstHud() {}

    private static TexteCache.Texte[] poignees(int n) {
        TexteCache.Texte[] t = new TexteCache.Texte[n];
        for (int i = 0; i < n; i++) t[i] = new TexteCache.Texte();
        return t;
    }

    private static int arr(double v) { return TexteRaster.arrondi(v); }

    /** Lien avec mod-ost, couche HUD (avant {@code staff-effets} : l'écran noir du staff le couvre), oubli à la déconnexion. */
    public static void init() {
        OstLien.init();
        HudElementRegistry.addLast(ID, (g, dt) -> render(g));
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> oublier());
    }

    /* ================================================================ état du bloc */

    private static final int CACHE = 0, ENTREE = 1, VISIBLE = 2, SORTIE = 3;
    private static int phase = CACHE;
    /** Opacité du bloc (fondus d'entrée et de sortie), 0..1. */
    private static float a;
    private static long dureeSortie = SORTIE_MS;
    /** À la fin de la sortie, montrer la nouvelle piste (son titre diffère de celui qui s'efface). */
    private static boolean relance;
    /** État visuel courant, précédent, et début du fondu croisé (0 = aucun). */
    private static int etatVis = PLEIN, etatPrec = PLEIN;
    private static long fonduA;
    /** Temps passé à 100 % (gelé écran ouvert) et durée voulue (10 s, 4 s à la reprise) ; temps en pause (option). */
    private static long tPlein, dureePlein = PLEIN_MS, tPause;
    /** Titre du bloc affiché (titre du HUD publié quand le bloc s'est montré). */
    private static String titreBloc = "";
    /** Source vue à la dernière image dessinable. */
    private static String idVu = "";
    private static int etatVu = OstLien.RIEN;
    private static boolean actifVu;
    private static long derniereImage;
    /** Décalage vertical (indicateur du staff, pile des bandes), lissé. */
    private static double decalY;

    private static void oublier() {
        phase = CACHE;
        a = 0f;
        relance = false;
        idVu = "";
        etatVu = OstLien.RIEN;
        actifVu = false;
        decalY = 0;
    }

    /**
     * Événements de la lecture (comparée à la dernière image dessinable : un changement survenu pendant qu'un écran
     * était ouvert, en F1 ou au KO se traite au retour), puis minuteurs et fondus, au temps des images dessinées.
     */
    private static void maj(long d, long now) {
        String id = OstLien.piste();
        int etat = OstLien.etat();
        boolean actif = OstLien.present() && !id.isEmpty() && etat != OstLien.RIEN && OstLien.audible();
        if (actif && (!actifVu || !id.equals(idVu))) nouvellePiste(etat, now);
        else if (actif && etat != etatVu) {
            if (etat == OstLien.PAUSE) montrer(EN_PAUSE, PLEIN_MS, now);
            else montrer(PLEIN, REPRISE_MS, now);
        } else if (!actif && actifVu) sortir(SORTIE_MS);
        idVu = id;
        etatVu = etat;
        actifVu = actif;

        switch (phase) {
            case ENTREE -> {
                // le fondu d'entrée part quand le titre est prêt (rendu sur le fil du texte, quelques ms)
                if (JEU.pret && JEU.titre == titreBloc && TITRE.ajour()) {
                    a = Math.min(1f, a + d / (float) ENTREE_MS);
                    if (a >= 1f) phase = VISIBLE;
                }
            }
            case SORTIE -> {
                a = Math.max(0f, a - d / (float) dureeSortie);
                if (a <= 0f) {
                    phase = CACHE;
                    boolean suite = relance && actif;
                    relance = false;
                    if (suite) nouvellePiste(etat, now);
                }
            }
            default -> { }
        }
        if (phase == ENTREE || phase == VISIBLE) {
            boolean masquer = RebornPrefs.INSTANCE.ostMasquerApres10s;
            if (etatVis == PLEIN) {
                tPlein += d;
                if (tPlein >= dureePlein) {
                    if (masquer) sortir(SORTIE_OPTION_MS);
                    else changer(ATTENUE, now);
                }
            } else if (etatVis == EN_PAUSE) {
                tPause += d;
                if (masquer && tPause >= PAUSE_OPTION_MS) sortir(SORTIE_OPTION_MS);
            } else if (masquer) {
                sortir(SORTIE_OPTION_MS);   // option cochée pendant que le bloc était à 70 %
            }
        }
    }

    /** Nouvelle piste audible (lancement, reprise en cours de piste, entrée dans une zone). */
    private static void nouvellePiste(int etat, long now) {
        String t = OstLien.titre();
        int vis = etat == OstLien.PAUSE ? EN_PAUSE : PLEIN;
        if (phase == CACHE) {
            titreBloc = t;
            entrer(vis, PLEIN_MS);
        } else if (t.equals(titreBloc)) {
            montrer(vis, PLEIN_MS, now);    // même titre (même catégorie) : 100 % pendant 10 s, sans clignoter
        } else {
            sortir(SORTIE_MS);              // titre différent : l'ancien s'efface, puis le nouveau entre
            relance = true;
        }
    }

    /** Montre le bloc dans l'état {@code vis} (pause, reprise) : entrée s'il était caché, fondu croisé sinon. */
    private static void montrer(int vis, long duree, long now) {
        if (phase == CACHE) {
            titreBloc = OstLien.titre();
            entrer(vis, duree);
            return;
        }
        if (phase == SORTIE) {
            phase = ENTREE;
            relance = false;
        }
        tPlein = 0;
        tPause = 0;
        dureePlein = duree;
        changer(vis, now);
    }

    private static void entrer(int vis, long duree) {
        phase = ENTREE;
        a = 0f;
        relance = false;
        etatVis = etatPrec = vis;
        fonduA = 0;
        tPlein = 0;
        tPause = 0;
        dureePlein = duree;
    }

    private static void changer(int vis, long now) {
        if (vis == etatVis) return;
        etatPrec = etatVis;
        etatVis = vis;
        fonduA = now;
    }

    private static void sortir(long duree) {
        if (phase == CACHE) return;
        phase = SORTIE;
        dureeSortie = duree;
    }

    /** Hauteurs des barres qui ondulent (px 1080p) : deux sinus par barre, sans allocation. */
    private static int[] animer(long now) {
        if (debugBarres != null) return debugBarres;
        double s = now / 1000.0;
        ANIM[0] = hauteur(7 + 2.2 * Math.sin(s * 4.3 + 0.3) + 0.8 * Math.sin(s * 9.7 + 1.1));
        ANIM[1] = hauteur(7 + 2.2 * Math.sin(s * 3.7 + 2.0) + 0.8 * Math.sin(s * 11.3 + 0.4));
        ANIM[2] = hauteur(7 + 2.2 * Math.sin(s * 5.1 + 4.1) + 0.8 * Math.sin(s * 8.9 + 2.7));
        return ANIM;
    }

    private static int hauteur(double v) { return Math.max(H_MIN, Math.min(H_MAX, (int) Math.round(v))); }

    /* ================================================================ rendu */

    private static void render(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        long now = Util.getMillis();
        long d = now - derniereImage;
        derniereImage = now;
        if (d < 0 || d > 250) d = 0;   // coupure du rendu (HUD masqué, KO…) : rien ne compte
        // écran ouvert (tchat compris) : bloc masqué, minuteur gelé — fini la réapparition à chaque fermeture
        if (mc.player == null || mc.options == null || mc.gui.hud.isHidden() || mc.gui.screen() != null) return;
        HudElementState st;
        try { st = RebornHudClient.config().stateOf(HudElement.OST); } catch (RuntimeException e) { return; }
        if (!st.visible()) return;
        maj(d, now);
        if (phase == CACHE) return;

        var win = mc.getWindow();
        double k0 = TexteRaster.kHud() / 2.0, k = k0 * st.scale();
        if (!JEU.pour(titreBloc, k)) return;   // police pas encore ouverte
        Formes f = FORMES.pour(k);
        placer(st, win.getWidth(), Math.max(1, win.getGuiScale()), k0, JEU.L, PL);

        // cohabitation : sous l'indicateur du mode staff / examinateur, sous la pile des bandes qui le recouvre
        int cible = 0;
        if (PL.coin && (EffetsStaff.indicateurVisible() || DouaneClient.modeExaminateur())) cible = arr(SOUS_INDICATEUR * k0);
        long ms = System.currentTimeMillis();
        if (BandeHud.pileVisible(ms) && PL.x0 - arr(MARGE_BANDES * k0) < BandeHud.pileX1()
            && PL.droite + 2 > BandeHud.pileX0() && PL.haut < BandeHud.pileBas())
            cible = Math.max(cible, BandeHud.pileBas() + arr(SOUS_BANDES * k0) - PL.haut);
        if (a <= 0f) decalY = cible;   // le bloc entre directement à sa place
        else decalY += (cible - decalY) * Math.min(1.0, d / 90.0);
        if (Math.abs(cible - decalY) < 0.5) decalY = cible;

        float u = fonduA == 0 ? 1f : Math.min(1f, (now - fonduA) / (float) FONDU_ETAT_MS);
        if (u >= 1f) etatPrec = etatVis;
        DaKit.debutPx(ctx);
        peindre(ctx, JEU, f, TITRE, FILET, PL.x0, PL.haut + (int) Math.round(decalY), etatVis, etatPrec, u, a, animer(now));
        DaKit.finPx(ctx);
    }

    /** Éditeur de HUD : le bloc à 100 % sur « Mystère » (la catégorie d'une piste), barres de la maquette b2. */
    public static void apercu(GuiGraphicsExtractor ctx, HudElementState st) {
        var win = Minecraft.getInstance().getWindow();
        double k0 = TexteRaster.kHud() / 2.0, k = k0 * st.scale();
        if (!APERCU.pour("Mystère", k)) return;
        Formes f = FORMES.pour(k);
        placer(st, win.getWidth(), Math.max(1, win.getGuiScale()), k0, APERCU.L, PL_APERCU);
        DaKit.debutPx(ctx);
        peindre(ctx, APERCU, f, TITRE_APERCU, FILET_APERCU, PL_APERCU.x0, PL_APERCU.haut, PLEIN, PLEIN, 1f, 1f, BARRES_B2);
        DaKit.finPx(ctx);
    }

    /**
     * Le bloc en pixels écran (déjà dans {@link DaKit#debutPx}) : filet de {@code g.L} px à partir de x0, haut du bloc
     * en {@code haut}, état visuel {@code etat} atteint par un fondu croisé depuis {@code prec} ({@code u} = avancée,
     * 1 = fini), opacité du bloc {@code a}. Ordre des maquettes : barres, titre, filet, losange ou « ‖ ».
     */
    private static void peindre(GuiGraphicsExtractor ctx, Gabarit g, Formes f, TexteCache.Texte titre,
                                TexteCache.Texte[] filets, int x0, int haut, int etat, int prec, float u, float a,
                                int[] anim) {
        double cx = x0 + g.L / 2.0;
        int base = haut + g.r12;
        int gx = arr(cx - g.gw / 2.0);
        int yf = base + g.r6;
        int c = (int) Math.floor(cx);
        boolean croise = u < 1f && prec != etat;
        if (croise) barres(ctx, g, f, prec, gx, base, a * (1f - u), anim);
        barres(ctx, g, f, etat, gx, base, croise ? a * u : a, anim);
        float opacite = croise ? OPACITE[prec] + (OPACITE[etat] - OPACITE[prec]) * u : OPACITE[etat];
        titre.ligne(g.affiche, TexteCache.Style.TITRE_OST, g.cap, g.k, 0, gx + g.largB + g.ecart, haut + g.cap / 2.0)
            .dessinerPx(ctx, a * opacite);
        if (croise) marque(ctx, g, f, filets, prec, x0, yf, c, a * (1f - u));
        marque(ctx, g, f, filets, etat, x0, yf, c, croise ? a * u : a);
    }

    private static void barres(GuiGraphicsExtractor ctx, Gabarit g, Formes f, int etat, int gx, int base, float w,
                               int[] anim) {
        if (etat == EN_PAUSE || w <= 0.004f) return;
        boolean ondule = etat == PLEIN;
        for (int i = 0; i < 3; i++) {
            int hu = ondule ? anim[i] : FIGEES[i];
            Masque m = f.barres[hu - H_MIN];
            TexteCache.Texte t = ondule ? BARRE[hu - H_MIN] : BARRE_FIGEE[i];
            t.forme(m, BLANC_HUD, g.k, BARRES * OPACITE[etat], gx + i * g.bp, base - m.h()).dessinerPx(ctx, w);
        }
    }

    private static void marque(GuiGraphicsExtractor ctx, Gabarit g, Formes f, TexteCache.Texte[] filets, int etat,
                               int x0, int yf, int c, float w) {
        if (w <= 0.004f) return;
        filets[etat].forme(g.filet, OR, g.k, OPACITE[etat], x0, yf).dessinerPx(ctx, w);
        if (etat == EN_PAUSE) {
            Masque m = f.pause;
            PAUSE.forme(m, BLANC_HUD, g.k, OPACITE[EN_PAUSE], c - m.w() / 2, yf - m.h() / 2).dessinerPx(ctx, w);
        } else {
            Masque m = f.losange;
            LOSANGE[etat].forme(m, OR_CLAIR, g.k, OPACITE[etat], c - m.w() / 2, yf - m.h() / 2).dessinerPx(ctx, w);
        }
    }

    /* ================================================================ placement */

    /** Placement en pixels écran : bord gauche et droit du filet, haut du bloc, coin haut droit (indicateur staff). */
    private static final class Placement {
        int x0, droite, haut;
        boolean coin;
    }

    /**
     * Boîte de l'élément en pixels écran, calculée comme {@link HudElementBounds#currentFor} (ancre, décalage et échelle
     * de l'éditeur) mais sans arrondi aux unités GUI ; le bloc s'y aligne selon l'ancre (à droite par défaut).
     */
    private static void placer(HudElementState st, int largeurPx, int gs, double k0, int filet, Placement p) {
        int nd = largeurPx - arr(DROITE * k0), nh = arr(HAUT * k0), nl = arr(GROUPE_MAX * k0), nhh = arr(BOITE_H * k0);
        HudAnchor an = st.effectiveAnchor(HudElement.OST);
        double s = st.scale(), lw = nl * s, lh = nhh * s;
        double ax = nd - nl + nl * an.fx + st.x() * (double) gs, ay = nh + nhh * an.fy + st.y() * (double) gs;
        double gauche = ax - lw * an.fx, haut = ay - lh * an.fy;
        p.x0 = (int) Math.round(gauche + (lw - filet) * an.fx);
        p.droite = p.x0 + filet;
        p.haut = (int) Math.round(haut);
        p.coin = p.haut < arr(48 * k0) && p.droite > largeurPx - arr(300 * k0);
    }

    /** Boîte de l'élément à l'échelle 1, en GUI (éditeur de HUD) : la place du plus long bloc (220 px), haut à 20 px. */
    public static HudElementBounds boite(int largeurGui, int hauteurGui) {
        var win = Minecraft.getInstance().getWindow();
        int gs = Math.max(1, win.getGuiScale());
        double k0 = TexteRaster.kHud() / 2.0;
        int droite = win.getWidth() - arr(DROITE * k0), haut = arr(HAUT * k0);
        int x = Math.floorDiv(droite - arr(GROUPE_MAX * k0), gs), y = Math.floorDiv(haut, gs);
        return new HudElementBounds(x, y, Math.ceilDiv(droite, gs) - x, Math.ceilDiv(haut + arr(BOITE_H * k0), gs) - y);
    }

    /* ================================================================ mesures */

    /** Mesures du bloc pour un titre et un facteur k ({@code ost} des maquettes), recalculées seulement s'ils changent. */
    private static final class Gabarit {
        String titre;
        double k = -1;
        boolean pret;
        /** Titre affiché (tronqué par « … »), capitales, largeur du groupe barres + titre. */
        String affiche = "";
        double cap, gw;
        int bp, ecart, largB, r12, r6, L;
        Masque filet;

        boolean pour(String t, double kk) {
            if (pret && kk == k && (t == titre || t.equals(titre))) {
                titre = t;   // même contenu : on garde la référence courante (comparée par == dans maj)
                return true;
            }
            if (!Police.INTER_GRAS.chargee()) return pret = false;
            boolean memeK = kk == k;
            titre = t;
            k = kk;
            bp = r(BARRE_PAS);
            ecart = r(ECART);
            largB = 2 * bp + r(BARRE_L);
            cap = CAP * kk;
            affiche = TexteRaster.tronquer(t, Police.INTER_GRAS, cap, r(GROUPE_MAX) - (largB + ecart));
            double w = TexteRaster.largeur(affiche, Police.INTER_GRAS, cap, 0, true);
            if (Double.isNaN(w)) return pret = false;
            gw = w + largB + ecart;
            int lo = r(FILET_MIN), hi = r(FILET_MAX);
            // round() de Python : au pair le plus proche (Math.rint), puis impair pour que le losange tombe au milieu
            int l = (int) Math.rint(Math.min(Math.max(gw + r(FILET_MARGE), lo), hi));
            if (l % 2 == 0) l = l < hi ? l + 1 : l - 1;
            r12 = r(CAP);
            r6 = r(SOUS_BASE);
            if (filet == null || l != L || !memeK) filet = Masque.filet(l, Math.max(1, r(1)), true);
            L = l;
            return pret = true;
        }

        private int r(double v) { return arr(v * k); }
    }

    /** Masques qui ne dépendent que de k : une barre par hauteur, losange, « ‖ ». Un objet par état (TexteCache). */
    private static final class Formes {
        private double k = -1;
        final Masque[] barres = new Masque[H_MAX - H_MIN + 1];
        Masque losange, pause;

        Formes pour(double kk) {
            if (kk == k) return this;
            k = kk;
            int bw = Math.max(1, arr(BARRE_L * kk));
            for (int h = H_MIN; h <= H_MAX; h++) barres[h - H_MIN] = Masque.barres(new int[]{Math.max(1, arr(h * kk))}, bw, 1);
            losange = Masque.losange(arr(2 * kk));
            pause = Masque.pause(Math.max(1, arr(kk)), Math.max(1, arr(5 * kk)));
            return this;
        }
    }

    /* ================================================================ banc */

    /** État du bloc pour le journal du banc. */
    public static String bilan() {
        String[] ph = {"caché", "entrée", "visible", "sortie"}, et = {"100 %", "70 %", "pause"};
        int[] h = etatVis == ATTENUE ? FIGEES : debugBarres != null ? debugBarres : ANIM;
        return ph[phase] + " · " + et[etatVis] + " · opacité " + Math.round(a * 100) + " % · titre « " + titreBloc
            + " » · barres " + h[0] + "/" + h[1] + "/" + h[2] + " · lien mod-ost " + (OstLien.present() ? "oui" : "non");
    }

    /** Le bloc est dessiné (opacité non nulle). */
    public static boolean dessine() { return phase != CACHE && a > 0f; }
}
