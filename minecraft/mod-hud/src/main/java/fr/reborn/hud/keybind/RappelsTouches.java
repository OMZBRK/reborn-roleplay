package fr.reborn.hud.keybind;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.animation.MovementAnimations;
import fr.reborn.hud.animation.NarutoRun;
import fr.reborn.hud.douane.DouaneClient;
import fr.reborn.hud.duel.DuelClient;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementBounds;
import fr.reborn.hud.element.HudElementState;
import fr.reborn.hud.faction.FactionClient;
import fr.reborn.hud.interaction.InteractionMode;
import fr.reborn.hud.ko.CorpsClient;
import fr.reborn.hud.ko.KoMasqueHud;
import fr.reborn.hud.meditation.MeditationClient;
import fr.reborn.hud.notif.BasDroite;
import fr.reborn.hud.notif.NotifHud;
import fr.reborn.hud.nourriture.NourritureClient;
import fr.reborn.hud.radio.RadioClient;
import fr.reborn.hud.runtime.VitalsHud;
import fr.reborn.hud.staff.StaffClient;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.LotBlit;
import fr.reborn.hud.ui.TexteCache;
import fr.reborn.hud.ui.TexteRaster;
import fr.reborn.hud.ui.TexteRaster.Masque;
import fr.reborn.hud.ui.TexteRaster.Police;
import fr.reborn.hud.voice.VoiceState;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.EnumSet;

/**
 * <b>Rappels de touches</b>, au bas de la colonne bas-droite (lot 2 « Reprises Ketto », Game Plan n° 17 ; maquettes c1
 * / c3, LISEZMOI v2, brief §4.3) — élément {@link HudElement#RAPPELS} de l'éditeur de HUD. <b>Permanents</b> (décision
 * du 01/11, masquables dans l'éditeur et dans Réglages › Interface) :
 * <ul>
 *   <li>en bas, la ligne de base « Portée de la voix [H] » (voix reliée, plusieurs portées, portée non imposée) ; à la
 *       touche H, elle devient « Voix : chuchotée » (puce à la couleur de la portée) pendant 2,5 s, à la place de
 *       l'ancienne notification ({@link #voix}) ;</li>
 *   <li>au-dessus, les lignes du moment, dans cet ordre : « Arrêter l'animation [.] » (une émote joue : la touche
 *       l'arrête), « Arrêter la course [X] », « Descendre [Espace] » (porté de plein gré : remplace la ligne de
 *       {@code KoHud}), « Se lever [Maj] » (méditation), « Poste de garde [F7] » (mode staff ou animation), « Parler à la
 *       radio [B] » (escouade, sans refus) ; « Concentration » viendra au lot 4 (masquée d'ici là) ;</li>
 *   <li>4 lignes au plus, 3 quand des notifications sont visibles ; une touche non liée masque sa ligne.</li>
 * </ul>
 * Toute la liste se masque quand une invitation ou une demande est affichée (radio, duel, faction, corps, offre de
 * nourriture), en mode interaction, au KO ({@code KoMasqueHud}), en douane côté candidat, pendant la carte d'aperçu de
 * capture et quand les sous-titres vanilla sont activés ({@link BasDroite}), écran ouvert, F1.
 *
 * <p><b>Gabarit</b> (px 1080p × k, k = kHud / 2 × l'échelle de l'éditeur) : centres à 36 / 63 / 90 / 117 px du bas
 * (1044 / 1017 / 990 / 963), cases de 22 px de haut au bord droit à 24 px du bord (1896), coins de 2 px, fond #2A2627 à
 * 90 %, liseré #5A5254, ombre d'1 px (texture {@code inventaire/touche_rappel.png} en 9 tranches, toutes tailles) ;
 * largeur max(22, glyphe + 10), boutons de la souris et barre d'espace en pictogrammes dans des cases de 22 px ;
 * colonne à la largeur de la plus large case visible, cases centrées dedans, tous les libellés finis 14 px avant elle ;
 * glyphe Inter Bold, capitales 10 px, centré (ponctuation centrée sur son encre) ; libellé Inter Bold, capitales 12 px,
 * #F3F0EA, préréglage HUD. Rien n'est alloué par image (contexte relu une fois par tick dans un tableau réutilisé, noms
 * de touches en cache, une seule couche pour toutes les cases).
 */
public final class RappelsTouches {

    private static final Identifier ID = Identifier.fromNamespaceAndPath("reborn-hud", "rappels");

    /* ---------------------------------------------------------------- atlas (tools/gen_inventaire_textures.py) */
    private static final Identifier ATLAS = DaKit.tex("inventaire", "touche_rappel");
    private static final int ATLAS_L = 44, ATLAS_H = 11;
    /** Case en 9 tranches de 3 px (coins fixes, bords et centre d'1 texel étirés), ombre comprise. */
    private static final int TRANCHE = 3;
    /** Pictogrammes : u, largeur, hauteur (souris gauche, droite, molette ; barre d'espace), v = 0. */
    private static final int[][] PICTOS = {{}, {8, 7, 11}, {16, 7, 11}, {24, 7, 11}, {32, 12, 4}};

    /* ---------------------------------------------------------------- gabarit 1080p (px, × k) */
    /** Boîte de l'éditeur : bas à 23 px du bas (13 px sous la ligne de base), bord droit à 20 px (cases finies 4 px plus
     *  à gauche), 4 lignes (107 px), 260 px de large. */
    private static final int BOITE_BAS = 23, BOITE_DROITE = 20, BOITE_H = 107, BOITE_L = 260;
    private static final int MARGE_BAS = 13, PAS = 27, MARGE_DROITE = 4;
    private static final int CASE = 22, MARGE_GLYPHE = 10, ECART_LIBELLE = 14, CAP = 12, CAP_TOUCHE = 10;
    private static final int PUCE = 4, ECART_PUCE = 6;
    private static final int MAX = 4, MAX_AVEC_NOTIFS = 3;

    /* ---------------------------------------------------------------- temps */
    private static final long VOIX_MS = 2500, ENTREE_MS = 150, SORTIE_MS = 200;

    /** Les rappels, du bas vers le haut : la base, puis les lignes du moment dans l'ordre de priorité. */
    public enum Rappel {
        PORTEE("Portée de la voix"),
        ANIMATION("Arrêter l'animation"),
        COURSE("Arrêter la course"),
        DESCENDRE("Descendre"),
        SE_LEVER("Se lever"),
        GARDE("Poste de garde"),
        RADIO("Parler à la radio"),
        /** Lot 4 : la concentration (traces, plaques, menu d'interaction ; décision du 01/11). Masquée jusque-là. */
        CONCENTRATION("Concentration");

        final String libelle;

        Rappel(String libelle) { this.libelle = libelle; }
    }

    /** Banc : rappels imposés (affichés quelle que soit leur condition). */
    public static final EnumSet<Rappel> DEBUG = EnumSet.noneOf(Rappel.class);

    /** État d'une ligne : nom de sa touche, largeur de sa case, poignées. */
    private static final class Ligne {
        final Rappel rappel;
        final NomTouche.Nom nom = new NomTouche.Nom();
        final TexteCache.Texte texte = new TexteCache.Texte(), touche = new TexteCache.Texte();
        /** Nom pour lequel la case a été mesurée, et à quel k. */
        String mesure;
        double kMesure = -1;
        int largeurCase;
        boolean ponctuation;
        /** Apparition (fondu d'entrée), affichée au tick précédent. */
        long depuis;
        boolean active;

        Ligne(Rappel r) { this.rappel = r; }
    }

    private static final Rappel[] RAPPELS = Rappel.values();
    private static final Ligne[] LIGNES = new Ligne[RAPPELS.length];
    /** Lignes à afficher, du bas vers le haut (relues une fois par tick). */
    private static final Ligne[] ACTIVES = new Ligne[RAPPELS.length];
    private static int nActives;

    static {
        for (int i = 0; i < RAPPELS.length; i++) LIGNES[i] = new Ligne(RAPPELS[i]);
    }

    /* ---------------------------------------------------------------- ligne « Voix : … » (touche H) */
    private static final TexteCache.Texte VOIX = new TexteCache.Texte();
    private static final TexteCache.Texte[] VOIX_PUCE = {new TexteCache.Texte(), new TexteCache.Texte(),
        new TexteCache.Texte(), new TexteCache.Texte()};
    private static String voixTexte;
    private static int voixRgb;
    private static long voixJusqua;
    private static double voixLargeur = -1, voixK;

    /* ---------------------------------------------------------------- éditeur de HUD */
    private static final Ligne[] APERCU = {new Ligne(Rappel.PORTEE), new Ligne(Rappel.ANIMATION)};

    /* ---------------------------------------------------------------- dernière image */
    private static float aListe;
    private static boolean baseAffichee, descendreAffiche, seLeverAffiche;
    private static long derniereImage;
    private static int montureTicks;
    private static final Formes FORMES = new Formes();
    private static final double[] P = new double[2];

    private RappelsTouches() {}

    private static int arr(double v) { return TexteRaster.arrondi(v); }

    /** Couche HUD (après qui parle, avant {@code staff-effets}), contexte relu à chaque tick. */
    public static void init() {
        HudElementRegistry.addLast(ID, (g, dt) -> render(g));
        ClientTickEvents.END_CLIENT_TICK.register(RappelsTouches::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> {
            nActives = 0;
            voixJusqua = 0;
            for (Ligne l : LIGNES) l.active = false;
        });
    }

    /* ================================================================ contexte (une fois par tick) */

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.options == null) {
            nActives = 0;
            return;
        }
        long now = System.currentTimeMillis();
        int n = 0;
        for (Ligne l : LIGNES) {
            boolean a = actif(l.rappel, mc) && l.nom.lire(touche(l.rappel, mc));
            if (a && !l.active) l.depuis = now;
            l.active = a;
            if (a) ACTIVES[n++] = l;
        }
        for (int i = n; i < nActives; i++) ACTIVES[i] = null;
        nActives = n;
        // méditation : « Se lever [Maj] » est affiché ; l'invite vanilla « Maj pour descendre » s'efface (comme KoClient)
        if (seLeverAffiche && mc.player.isPassenger()) {
            if (montureTicks++ < 10) mc.gui.hud.setOverlayMessage(Component.empty(), false);
        } else {
            montureTicks = 0;
        }
    }

    /** Condition d'un rappel (lue sur le fil client, une fois par tick). */
    private static boolean actif(Rappel r, Minecraft mc) {
        if (DEBUG.contains(r)) return true;
        return switch (r) {
            case PORTEE -> VoiceState.relie() && VoiceState.nombrePortees() > 1 && !VoiceState.porteeImposee();
            case ANIMATION -> MovementAnimations.isEmotePlaying(mc.player) && HudKeybinds.arretEmoteDisponible(mc);
            case COURSE -> NarutoRun.INSTANCE.isActive();
            case DESCENDRE -> CorpsClient.consenti() && !KoMasqueHud.masque();
            case SE_LEVER -> MeditationClient.jeMedite();
            case GARDE -> StaffClient.modeActif();
            case RADIO -> RadioClient.serveur() && RadioClient.escouade() != null && RadioClient.etat().refus() == null;
            case CONCENTRATION -> false;   // lot 4
        };
    }

    /** Touche d'un rappel (celle que le joueur a liée) ; null = pas encore de touche (la ligne se masque). */
    private static KeyMapping touche(Rappel r, Minecraft mc) {
        return switch (r) {
            case PORTEE -> HudKeybinds.VOIX_PORTEE;
            case ANIMATION -> HudKeybinds.ANIMATIONS;
            case COURSE -> HudKeybinds.COURSE_CHAKRA;
            case DESCENDRE -> mc.options.keyJump;
            case SE_LEVER -> mc.options.keyShift;
            case GARDE -> StaffClient.touche();
            case RADIO -> HudKeybinds.RADIO_PARLER;
            case CONCENTRATION -> null;
        };
    }

    /** Toute la liste masquée : invitation ou demande, mode interaction, KO, douane, carte de capture, sous-titres. */
    private static boolean masquee(Minecraft mc) {
        return RadioClient.invit() != null || DuelClient.hasInvite() || FactionClient.invit() != null
            || CorpsClient.demande() != null || NourritureClient.aOffre() || InteractionMode.INSTANCE.isActive()
            || KoMasqueHud.masque() || DouaneClient.masqueHud() || BasDroite.carteCapture() || BasDroite.sousTitres(mc);
    }

    /* ================================================================ API */

    /**
     * Touche H (portée de la voix) : si la ligne de base est affichée, elle devient {@code texte} (« Voix : chuchotée »)
     * pendant 2,5 s, avec une puce à la couleur {@code argb} ; sinon faux (l'appelant passe par une notification).
     */
    public static boolean voix(int argb, String texte) {
        if (!baseAffichee || texte == null) return false;
        if (!texte.equals(voixTexte)) {
            voixTexte = texte;
            voixLargeur = -1;
        }
        voixRgb = argb & 0xFFFFFF;
        voixJusqua = System.currentTimeMillis() + VOIX_MS;
        return true;
    }

    /** « Descendre [Espace] » affiché à la dernière image : {@code KoHud} ne pose plus sa ligne au centre. */
    public static boolean descendreAffiche() { return descendreAffiche; }

    /* ================================================================ rendu */

    private static void render(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        float dt = Math.max(0f, Math.min(100f, now - derniereImage));
        derniereImage = now;
        baseAffichee = descendreAffiche = seLeverAffiche = false;
        if (mc.player == null || mc.options == null || mc.gui.hud.isHidden() || mc.gui.screen() != null) {
            aListe = 0f;
            return;
        }
        HudElementState st;
        try { st = RebornHudClient.config().stateOf(HudElement.RAPPELS); } catch (RuntimeException e) { return; }
        if (!st.visible() || nActives == 0) {
            aListe = 0f;
            return;
        }
        boolean cachee = masquee(mc);
        aListe = cachee ? Math.max(0f, aListe - dt / SORTIE_MS) : Math.min(1f, aListe + dt / ENTREE_MS);
        if (aListe <= 0f || !Police.INTER_GRAS.chargee()) return;

        var win = mc.getWindow();
        int W = win.getWidth(), H = win.getHeight(), gs = Math.max(1, win.getGuiScale());
        double k0 = BasDroite.k0(), k = k0 * st.scale();
        placer(st, W, H, gs, k0);
        int n = Math.min(nActives, NotifHud.visible() ? MAX_AVEC_NOTIFS : MAX);
        boolean voix = voixTexte != null && now < voixJusqua;
        DaKit.debutPx(ctx);
        dessiner(ctx, ACTIVES, n, (int) Math.round(P[0]) - arr(MARGE_DROITE * k), (int) Math.round(P[1]), k, now, aListe,
            voix, false);
        DaKit.finPx(ctx);
    }

    /**
     * La colonne (déjà dans {@link DaKit#debutPx}) : cases (une seule couche), pictogrammes, glyphes, libellés, puce de
     * « Voix : … ». {@code droite} = bord droit des cases, {@code bas} = bas de la boîte.
     */
    private static void dessiner(GuiGraphicsExtractor ctx, Ligne[] lignes, int n, int droite, int bas, double k, long now,
                                 float a, boolean voix, boolean apercu) {
        Formes f = FORMES.pour(k);
        int colonne = f.caseMin;
        for (int i = 0; i < n; i++) colonne = Math.max(colonne, mesurer(lignes[i], k, f));
        int x0 = droite - colonne, xLibelle = x0 - arr(ECART_LIBELLE * k), h = f.caseMin;
        // 1) cases et pictogrammes : une seule couche (la même texture)
        LotBlit l = LotBlit.ouvrir(ctx, ATLAS, ATLAS_L, ATLAS_H);
        for (int i = 0; i < n; i++) {
            Ligne g = lignes[i];
            float al = a * apparition(g, now, apercu);
            if (al <= 0.004f) continue;
            int yc = bas - arr((MARGE_BAS + PAS * i) * k), cw = g.largeurCase;
            int xc = x0 + (colonne - cw) / 2, yh = yc - h / 2, col = DaKit.blanc(al);
            caseTouche(l, xc, yh, cw, h, col);
            int p = g.nom.picto();
            if (p != NomTouche.TEXTE) {
                int[] pc = PICTOS[p];
                int mw = Math.max(1, (int) Math.rint(pc[1] * k)), mh = Math.max(1, (int) Math.rint(pc[2] * k));
                l.etire(xc + arr((cw - mw) / 2.0), yh + arr((h - mh) / 2.0), mw, mh, pc[0], 0, pc[1], pc[2], col);
            }
        }
        l.fermer();
        // 2) glyphes et libellés
        for (int i = 0; i < n; i++) {
            Ligne g = lignes[i];
            float al = a * apparition(g, now, apercu);
            if (al <= 0.004f) continue;
            int yc = bas - arr((MARGE_BAS + PAS * i) * k), cw = g.largeurCase;
            int xc = x0 + (colonne - cw) / 2;
            if (g.nom.picto() == NomTouche.TEXTE) {
                g.touche.ligne(g.nom.texte(), g.ponctuation ? TexteCache.Style.TOUCHE_PONCT : TexteCache.Style.TOUCHE,
                    CAP_TOUCHE * k, k, 0, xc + cw / 2.0, yc).dessinerPx(ctx, al);
            }
            if (g.rappel == Rappel.PORTEE && voix) {
                VOIX.ligne(voixTexte, TexteCache.Style.LIBELLE, CAP * k, k, 0, xLibelle, yc).dessinerPx(ctx, al);
                if (voixLargeur < 0 || voixK != k) {
                    voixLargeur = TexteRaster.largeur(voixTexte, Police.INTER_GRAS, CAP * k, 0, true);
                    voixK = k;
                }
                if (!Double.isNaN(voixLargeur)) {
                    int np = f.puce.w();
                    int xp = arr(xLibelle - voixLargeur - arr(ECART_PUCE * k) - np);
                    poigneePuce(voixRgb).forme(f.puce, voixRgb, k, xp, yc - np / 2).dessinerPx(ctx, al);
                }
            } else {
                g.texte.ligne(g.rappel.libelle, TexteCache.Style.LIBELLE, CAP * k, k, 0, xLibelle, yc).dessinerPx(ctx, al);
            }
            if (!apercu) {
                if (g.rappel == Rappel.PORTEE) baseAffichee = true;
                else if (g.rappel == Rappel.DESCENDRE) descendreAffiche = true;
                else if (g.rappel == Rappel.SE_LEVER) seLeverAffiche = true;
            }
        }
    }

    /** Fondu d'entrée d'une ligne qui apparaît (150 ms). */
    private static float apparition(Ligne g, long now, boolean apercu) {
        return apercu ? 1f : Math.min(1f, Math.max(0f, (now - g.depuis) / (float) ENTREE_MS));
    }

    /** Largeur de la case (px) : 22 pour un pictogramme, sinon max(22, glyphe + 10) ; mesurée quand le nom change. */
    private static int mesurer(Ligne g, double k, Formes f) {
        String nom = g.nom.texte();
        if (nom != g.mesure || k != g.kMesure) {
            g.mesure = nom;
            g.kMesure = k;
            g.ponctuation = true;
            for (int i = 0; i < nom.length(); i++) if (Character.isLetterOrDigit(nom.charAt(i))) { g.ponctuation = false; break; }
            if (g.nom.picto() != NomTouche.TEXTE) {
                g.largeurCase = f.caseMin;
            } else {
                double w = TexteRaster.largeur(nom, Police.INTER_GRAS, CAP_TOUCHE * k, 0, false);
                g.largeurCase = Double.isNaN(w) ? f.caseMin : Math.max(f.caseMin, (int) Math.ceil(w + arr(MARGE_GLYPHE * k)));
                if (Double.isNaN(w)) g.mesure = null;   // police pas encore ouverte : on remesurera
            }
        }
        return g.largeurCase;
    }

    /**
     * Case de touche w × h (ombre d'1 px en plus, à droite et en bas) en 9 tranches de l'atlas : les coins de 3 × 3
     * (coin rogné de 2 px, liseré, ombre) tels quels, bords et centre étirés — la case des maquettes à toute taille.
     */
    private static void caseTouche(LotBlit l, int x, int y, int w, int h, int col) {
        int t = TRANCHE, cx = w + 1 - 2 * t, cy = h + 1 - 2 * t;
        int xd = x + t + cx, yb = y + t + cy;
        l.blit(x, y, 0, 0, t, t, col);
        l.etire(x + t, y, cx, t, t, 0, 1, t, col);
        l.blit(xd, y, t + 1, 0, t, t, col);
        l.etire(x, y + t, t, cy, 0, t, t, 1, col);
        l.etire(x + t, y + t, cx, cy, t, t, 1, 1, col);
        l.etire(xd, y + t, t, cy, t + 1, t, t, 1, col);
        l.blit(x, yb, 0, t + 1, t, t, col);
        l.etire(x + t, yb, cx, t, t, t + 1, 1, t, col);
        l.blit(xd, yb, t + 1, t + 1, t, t, col);
    }

    private static TexteCache.Texte poigneePuce(int rgb) {
        if (rgb == (VitalsHud.MIC_VERT & 0xFFFFFF)) return VOIX_PUCE[0];
        if (rgb == (VitalsHud.MIC_JAUNE & 0xFFFFFF)) return VOIX_PUCE[1];
        if (rgb == (VitalsHud.MIC_ROUGE & 0xFFFFFF)) return VOIX_PUCE[2];
        return VOIX_PUCE[3];
    }

    /* ================================================================ placement, éditeur */

    private static void placer(HudElementState st, int W, int H, int gs, double k0) {
        int droite = W - arr(BOITE_DROITE * k0), bas = H - arr(BOITE_BAS * k0);
        int l = arr(BOITE_L * k0), h = arr(BOITE_H * k0);
        BasDroite.placer(HudElement.RAPPELS, st, gs, droite - l, bas - h, l, h, P);
    }

    /** Boîte de l'élément à l'échelle 1, en GUI (éditeur de HUD) : quatre lignes au bas de l'écran. */
    public static HudElementBounds boite(int largeurGui, int hauteurGui) {
        var win = Minecraft.getInstance().getWindow();
        int gs = Math.max(1, win.getGuiScale());
        double k0 = BasDroite.k0();
        int droite = win.getWidth() - arr(BOITE_DROITE * k0), bas = win.getHeight() - arr(BOITE_BAS * k0);
        int x = Math.floorDiv(droite - arr(BOITE_L * k0), gs), y = Math.floorDiv(bas - arr(BOITE_H * k0), gs);
        return new HudElementBounds(x, y, Math.ceilDiv(droite, gs) - x, Math.ceilDiv(bas, gs) - y);
    }

    /** Éditeur de HUD : « Portée de la voix » et « Arrêter l'animation », avec les touches du joueur (maquette c1). */
    public static void apercu(GuiGraphicsExtractor ctx, HudElementState st) {
        if (!Police.INTER_GRAS.chargee()) return;
        Minecraft mc = Minecraft.getInstance();
        var win = mc.getWindow();
        int W = win.getWidth(), H = win.getHeight(), gs = Math.max(1, win.getGuiScale());
        double k0 = BasDroite.k0(), k = k0 * st.scale();
        placer(st, W, H, gs, k0);
        int n = 0;
        for (Ligne g : APERCU) {
            if (!g.nom.lire(touche(g.rappel, mc))) break;
            n++;
        }
        if (n == 0) return;
        DaKit.debutPx(ctx);
        dessiner(ctx, APERCU, n, (int) Math.round(P[0]) - arr(MARGE_DROITE * k), (int) Math.round(P[1]), k,
            System.currentTimeMillis(), 1f, false, true);
        DaKit.finPx(ctx);
    }

    /** Masques et tailles qui ne dépendent que de k. */
    private static final class Formes {
        private double k = -1;
        Masque puce;
        int caseMin;

        Formes pour(double kk) {
            if (kk == k) return this;
            k = kk;
            puce = Masque.puce(arr(PUCE * kk));
            caseMin = arr(CASE * kk);
            return this;
        }
    }

    /* ================================================================ banc */

    /** Lignes affichées (du bas vers le haut), pour le journal du banc. */
    public static String bilan() {
        StringBuilder s = new StringBuilder();
        for (int i = 0; i < nActives; i++) {
            Ligne g = ACTIVES[i];
            if (i > 0) s.append(" · ");
            s.append(g.rappel.libelle).append(" [").append(g.nom.texte()).append(']');
        }
        return nActives == 0 ? "aucun" : s.toString();
    }
}
