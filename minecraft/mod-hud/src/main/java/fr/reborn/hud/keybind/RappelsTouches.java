package fr.reborn.hud.keybind;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.animation.MovementAnimations;
import fr.reborn.hud.animation.NarutoRun;
import fr.reborn.hud.combat.CombatInput;
import fr.reborn.hud.combat.CombatState;
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
import fr.reborn.hud.menu.settings.RebornPrefs;
import fr.reborn.hud.notif.BasDroite;
import fr.reborn.hud.notif.NotifHud;
import fr.reborn.hud.nourriture.NourritureClient;
import fr.reborn.hud.radio.RadioClient;
import fr.reborn.hud.runtime.VitalsHud;
import fr.reborn.hud.staff.StaffClient;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.HudEditScreen;
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
import java.util.function.BooleanSupplier;

/**
 * <b>Rappels de touches</b>, au bas de la colonne bas-droite — élément {@link HudElement#RAPPELS} de l'éditeur de HUD
 * (ancre bas-droite, déplaçable, taille réglable). Lot 2 « Reprises Ketto » pour le gabarit, <b>lot 3</b> (décision du
 * 05/11, CADRAGE §9, planche r1) pour la liste : plus de base permanente, une liste selon le moment.
 * <ul>
 *   <li><b>Temps normal</b> : « Concentration [Y] » seule (la touche réelle, lue par {@link NomTouche}), <b>dès que le
 *       lot 4 branche la concentration</b> ({@link #concentrationBranchee}) ; d'ici là, aucune ligne permanente
 *       (CADRAGE §9 : la touche ne fait encore rien).</li>
 *   <li><b>En concentration</b> (lot 4, {@link #concentration}) : de haut en bas Sacoche, Animations, Mode photo, Vue,
 *       Portée de la voix, Concentration.</li>
 *   <li><b>En combat</b> (garde levée, duel, coups ou dépense d'endurance hors sprint depuis moins de 5 s) : Parer,
 *       Dash, Dégainer, au-dessus de Concentration.</li>
 *   <li><b>Action en cours</b> : la ligne du moment va <b>tout en bas</b>, sous Concentration (comme « Se débattre »
 *       chez Ketto) : Arrêter l'animation, Arrêter la course, Descendre, Se lever, Poste de garde, Parler à la radio.</li>
 *   <li><b>Touche H</b> : « Voix : chuchotée » (puce à la couleur de la portée) 2,5 s juste au-dessus de Concentration,
 *       puis rien ({@link #voix}) ; « Portée de la voix » n'est plus permanente. Réglage {@code rappelsPortee}.</li>
 *   <li>Plafond : 6 lignes en concentration, 4 en combat, 3 sinon ; 2 quand des notifications sont visibles (réglage
 *       {@code rappelsAvecNotifs} : faux = la liste se masque tant qu'une notification est affichée). Au-delà du
 *       plafond, on garde dans l'ordre Concentration, « Voix : … » (l'appui sur H a toujours sa réponse), les actions
 *       en cours, puis les autres lignes de bas en haut : ce sont les lignes du haut qui tombent.</li>
 *   <li>Apparition et disparition <b>instantanées</b>, comme chez Ketto (plus de fondu de 150 ms).</li>
 * </ul>
 * Toute la liste se masque quand une invitation ou une demande est affichée (radio, duel, faction, corps, offre de
 * nourriture), au menu d'interaction hors concentration (en concentration elle reste, menu ouvert compris), au KO ({@code KoMasqueHud}), en douane côté candidat, pendant la carte d'aperçu de
 * capture et quand les sous-titres vanilla sont activés ({@link BasDroite}), écran ouvert, F1 ; et bien sûr quand
 * l'élément est masqué (éditeur, Paramètres › Interface › Rappels de touches).
 *
 * <p><b>Gabarit</b> (inchangé depuis le lot 2 ; px 1080p × k, k = kHud / 2 × l'échelle de l'éditeur) : centres à
 * 36 / 63 / 90 … px du bas, cases de 22 px de haut au bord droit à 24 px du bord (1896), coins de 2 px, fond #2A2627 à
 * 90 %, liseré #5A5254, ombre d'1 px (texture {@code inventaire/touche_rappel.png} en 9 tranches, toutes tailles) ;
 * largeur max(22, glyphe + 10), boutons de la souris et barre d'espace en pictogrammes dans des cases de 22 px ;
 * colonne à la largeur de la plus large case visible, cases centrées dedans, tous les libellés finis 14 px avant elle ;
 * glyphe Inter Bold, capitales 10 px, centré (ponctuation centrée sur son encre) ; libellé Inter Bold, capitales 12 px,
 * #F3F0EA, préréglage HUD. Rien n'est alloué par image (contexte relu une fois par tick dans un tableau réutilisé, noms
 * de touches en cache, une seule couche pour toutes les cases).
 *
 * <p><b>API pour les Paramètres</b> (Interface › Rappels de touches) : {@link #ouvrirEditeur(Minecraft)} (« Déplacer »)
 * et {@link #remettreParDefaut()} (« Remettre »).
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
    /** Boîte de l'éditeur : bas à 23 px du bas (13 px sous la ligne du bas), bord droit à 20 px (cases finies 4 px plus
     *  à gauche), <b>6 lignes</b> (le gabarit le plus grand, en concentration : maquette t2-c ; 161 px), 260 px de large.
     *  L'ancre est en bas : le bas de la colonne ne bouge pas quand la boîte grandit. */
    private static final int BOITE_BAS = 23, BOITE_DROITE = 20, BOITE_H = 161, BOITE_L = 260;
    private static final int MARGE_BAS = 13, PAS = 27, MARGE_DROITE = 4;
    private static final int CASE = 22, MARGE_GLYPHE = 10, ECART_LIBELLE = 14, CAP = 12, CAP_TOUCHE = 10;
    private static final int PUCE = 4, ECART_PUCE = 6;
    /** Plafonds de lignes : concentration, combat, temps normal, avec des notifications. */
    private static final int MAX_CONCENTRATION = 6, MAX_COMBAT = 4, MAX = 3, MAX_AVEC_NOTIFS = 2;

    /* ---------------------------------------------------------------- temps */
    private static final long VOIX_MS = 2500;

    /** Le moment : il choisit les lignes du haut et le plafond. */
    public enum Mode { NORMAL, CONCENTRATION, COMBAT }

    /** Les rappels. L'ordre d'affichage est fixé par {@link #ORDRE}, pas par celui de l'énumération. */
    public enum Rappel {
        // action en cours : tout en bas, sous Concentration
        ANIMATION("Arrêter l'animation"),
        COURSE("Arrêter la course"),
        DESCENDRE("Descendre"),
        SE_LEVER("Se lever"),
        GARDE("Poste de garde"),
        RADIO("Parler à la radio"),
        // la base : seule en temps normal
        CONCENTRATION("Concentration"),
        /** 2,5 s après un appui sur H (« Voix : chuchotée »), et en concentration. */
        PORTEE("Portée de la voix"),
        // combat, de bas en haut
        DEGAINER("Dégainer"),
        DASH("Dash"),
        PARER("Parer"),
        // concentration, de bas en haut
        VUE("Vue"),
        PHOTO("Mode photo"),
        MENU_ANIMATIONS("Animations"),
        SACOCHE("Sacoche");

        final String libelle;

        Rappel(String libelle) { this.libelle = libelle; }
    }

    /** Ordre d'affichage, du bas vers le haut. */
    private static final Rappel[] ORDRE = {
        Rappel.ANIMATION, Rappel.COURSE, Rappel.DESCENDRE, Rappel.SE_LEVER, Rappel.GARDE, Rappel.RADIO,
        Rappel.CONCENTRATION, Rappel.PORTEE,
        Rappel.DEGAINER, Rappel.DASH, Rappel.PARER,
        Rappel.VUE, Rappel.PHOTO, Rappel.MENU_ANIMATIONS, Rappel.SACOCHE,
    };

    /**
     * En concentration ? Branché par le lot 4 (la bascule de {@link HudKeybinds#CONCENTRATION}) ; d'ici là, jamais.
     * Lu une fois par tick, sur le fil client.
     */
    public static volatile BooleanSupplier concentration = () -> false;

    /**
     * La touche Concentration fait-elle quelque chose ? Tant que c'est faux (avant le lot 4, qui le passe à vrai en
     * branchant {@link #concentration}), la ligne « Concentration » n'est pas affichée en temps normal : un rappel
     * permanent d'une touche sans effet tromperait le joueur. Le banc la montre quand même ({@link #DEBUG},
     * {@link #debugMode}).
     */
    public static volatile boolean concentrationBranchee = false;

    /** Banc : rappels imposés (affichés quelle que soit leur condition). */
    public static final EnumSet<Rappel> DEBUG = EnumSet.noneOf(Rappel.class);
    /** Banc : moment imposé (null = lu dans le jeu). */
    public static volatile Mode debugMode = null;

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

        Ligne(Rappel r) { this.rappel = r; }
    }

    private static final Ligne[] LIGNES = new Ligne[Rappel.values().length];
    /** Lignes à afficher, du bas vers le haut (relues une fois par tick). */
    private static final Ligne[] ACTIVES = new Ligne[LIGNES.length];
    private static int nActives;
    /** Lignes gardées sous le plafond, du bas vers le haut (choisies à chaque image, tableaux réutilisés). */
    private static final Ligne[] GARDEES = new Ligne[LIGNES.length];
    private static final boolean[] GARDE = new boolean[LIGNES.length];
    private static Mode mode = Mode.NORMAL;

    static {
        for (Rappel r : Rappel.values()) LIGNES[r.ordinal()] = new Ligne(r);
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
    /** Aperçu de l'éditeur : le gabarit le plus grand (concentration, six lignes), du bas vers le haut. */
    private static final Ligne[] APERCU = {new Ligne(Rappel.CONCENTRATION), new Ligne(Rappel.PORTEE),
        new Ligne(Rappel.VUE), new Ligne(Rappel.PHOTO), new Ligne(Rappel.MENU_ANIMATIONS), new Ligne(Rappel.SACOCHE)};
    private static final Ligne[] APERCU_LIEES = new Ligne[APERCU.length];

    /* ---------------------------------------------------------------- dernière image */
    /** La colonne pouvait s'afficher à la dernière image (élément visible, rien ne la masque) : la touche H y écrit. */
    private static boolean colonnePossible;
    private static boolean descendreAffiche, seLeverAffiche;
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
        });
    }

    /* ================================================================ contexte (une fois par tick) */

    private static void tick(Minecraft mc) {
        if (mc.player == null || mc.options == null) {
            nActives = 0;
            return;
        }
        long now = System.currentTimeMillis();
        Mode m = lireMode(now);
        mode = m;
        int n = 0;
        for (Rappel r : ORDRE) {
            Ligne l = LIGNES[r.ordinal()];
            if (actif(r, m, mc, now) && l.nom.lire(touche(r, mc))) ACTIVES[n++] = l;
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

    /** Le moment : concentration (lot 4), combat, ou temps normal. */
    private static Mode lireMode(long now) {
        Mode d = debugMode;
        if (d != null) return d;
        boolean conc;
        try {
            conc = concentration.getAsBoolean();
        } catch (RuntimeException e) {
            conc = false;
        }
        if (conc) return Mode.CONCENTRATION;
        if (CombatInput.INSTANCE.isBlocking() || DuelClient.inDuel() || CombatState.INSTANCE.inCombat(now)) {
            return Mode.COMBAT;
        }
        return Mode.NORMAL;
    }

    /** Voix de proximité reliée, plusieurs portées, portée non imposée : la touche H a un sens. */
    private static boolean porteeUtile() {
        return VoiceState.relie() && VoiceState.nombrePortees() > 1 && !VoiceState.porteeImposee();
    }

    /** Condition d'un rappel (lue sur le fil client, une fois par tick). */
    private static boolean actif(Rappel r, Mode m, Minecraft mc, long now) {
        if (DEBUG.contains(r)) return true;
        return switch (r) {
            case ANIMATION -> MovementAnimations.isEmotePlaying(mc.player) && HudKeybinds.arretEmoteDisponible(mc);
            case COURSE -> NarutoRun.INSTANCE.isActive();
            case DESCENDRE -> CorpsClient.consenti() && !KoMasqueHud.masque();
            case SE_LEVER -> MeditationClient.jeMedite();
            case GARDE -> StaffClient.modeActif();
            case RADIO -> RadioClient.serveur() && RadioClient.escouade() != null && RadioClient.etat().refus() == null;
            // pendant le Malaxage du chakra, la touche ne fait rien (lot 4) : pas de rappel trompeur
            case CONCENTRATION -> (concentrationBranchee || m == Mode.CONCENTRATION || debugMode != null)
                && !fr.reborn.hud.parchemin.ConcentrationHud.actif();
            case PORTEE -> porteeUtile() && (now < voixJusqua && voixTexte != null || m == Mode.CONCENTRATION);
            case DEGAINER, DASH, PARER -> m == Mode.COMBAT;
            case VUE, PHOTO, MENU_ANIMATIONS, SACOCHE -> m == Mode.CONCENTRATION;
        };
    }

    /** Touche d'un rappel (celle que le joueur a liée) ; null ou non liée = la ligne se masque. */
    private static KeyMapping touche(Rappel r, Minecraft mc) {
        return switch (r) {
            case ANIMATION, MENU_ANIMATIONS -> HudKeybinds.ANIMATIONS;
            case COURSE -> HudKeybinds.COURSE_CHAKRA;
            case DESCENDRE -> mc.options.keyJump;
            case SE_LEVER -> mc.options.keyShift;
            case GARDE -> StaffClient.touche();
            case RADIO -> HudKeybinds.RADIO_PARLER;
            case CONCENTRATION -> HudKeybinds.concentrationEffective();
            case PORTEE -> HudKeybinds.VOIX_PORTEE;
            case DEGAINER -> HudKeybinds.DEGAINER;
            case DASH -> HudKeybinds.DASH;
            case PARER -> HudKeybinds.PARRY;
            case VUE -> mc.options.keyTogglePerspective;
            case PHOTO -> HudKeybinds.PHOTO;
            case SACOCHE -> mc.options.keyInventory;
        };
    }

    /**
     * Toute la liste masquée : invitation ou demande, menu d'interaction hors concentration, KO, douane, carte de
     * capture, sous-titres. En concentration (lot 4), la liste reste affichée, menu ouvert compris (comme chez Ketto).
     */
    private static boolean masquee(Minecraft mc) {
        return RadioClient.invit() != null || DuelClient.hasInvite() || FactionClient.invit() != null
            || CorpsClient.demande() != null || NourritureClient.aOffre()
            || InteractionMode.INSTANCE.isActive() && !fr.reborn.hud.concentration.ModeConcentration.actif()
            || KoMasqueHud.masque() || DouaneClient.masqueHud() || BasDroite.carteCapture() || BasDroite.sousTitres(mc);
    }

    /** Réglages du joueur (Paramètres › Interface › Rappels de touches). */
    private static RebornPrefs prefs() {
        RebornPrefs p = RebornPrefs.INSTANCE;
        p.ensureLoaded();
        return p;
    }

    /* ================================================================ API */

    /**
     * Touche H (portée de la voix) : la ligne « Voix : chuchotée » ({@code texte}, puce à la couleur {@code argb})
     * s'affiche 2,5 s juste au-dessus de Concentration. Faux si les rappels ne peuvent pas l'afficher (élément masqué,
     * liste masquée, réglage « Portée de la voix » coupé, voix sans plusieurs portées, H non liée) : l'appelant passe
     * alors par une notification.
     */
    public static boolean voix(int argb, String texte) {
        if (!colonnePossible || texte == null || !prefs().rappelsPortee || !porteeUtile()) return false;
        KeyMapping h = HudKeybinds.VOIX_PORTEE;
        if (h == null || h.isUnbound()) return false;
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

    /** Le moment lu au dernier tick (banc, Paramètres). */
    public static Mode mode() { return mode; }

    /**
     * Paramètres › Interface › Rappels de touches › « Déplacer » : ouvre l'éditeur de HUD avec les rappels déjà choisis
     * (le panneau de l'éditeur passe à gauche, replié, puisque les rappels sont à droite) ; Échap y revient à l'écran
     * d'où l'on vient. À appeler sur le fil client.
     */
    public static void ouvrirEditeur(Minecraft mc) {
        if (mc == null) return;
        mc.setScreenAndShow(new HudEditScreen(mc.gui.screen(), HudElement.RAPPELS));
    }

    /**
     * Paramètres › Interface › Rappels de touches › « Remettre » : position et taille d'origine (en bas à droite,
     * 100 %), visibilité gardée ; sauvegardé tout de suite.
     */
    public static void remettreParDefaut() {
        var cfg = RebornHudClient.config();
        boolean visible = cfg.stateOf(HudElement.RAPPELS).visible();
        cfg.setState(HudElement.RAPPELS, HudElement.RAPPELS.defaultState().withVisible(visible));
        cfg.save();
    }

    /* ================================================================ rendu */

    private static void render(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        colonnePossible = descendreAffiche = seLeverAffiche = false;
        if (mc.player == null || mc.options == null || mc.gui.hud.isHidden() || mc.gui.screen() != null) return;
        HudElementState st;
        try { st = RebornHudClient.config().stateOf(HudElement.RAPPELS); } catch (RuntimeException e) { return; }
        if (!st.visible() || masquee(mc)) return;
        boolean notifs = NotifHud.visible();
        RebornPrefs p = prefs();
        if (notifs && !p.rappelsAvecNotifs) return;
        colonnePossible = true;
        if (nActives == 0 || !Police.INTER_GRAS.chargee()) return;

        var win = mc.getWindow();
        int W = win.getWidth(), H = win.getHeight(), gs = Math.max(1, win.getGuiScale());
        double k0 = BasDroite.k0(), k = k0 * st.scale();
        placer(st, W, H, gs, k0);
        int max = switch (mode) {
            case CONCENTRATION -> MAX_CONCENTRATION;
            case COMBAT -> MAX_COMBAT;
            case NORMAL -> MAX;
        };
        boolean voix = voixTexte != null && now < voixJusqua;
        int n = plafonner(notifs ? Math.min(max, MAX_AVEC_NOTIFS) : max, voix);
        DaKit.debutPx(ctx);
        dessiner(ctx, GARDEES, n, (int) Math.round(P[0]) - arr(MARGE_DROITE * k), (int) Math.round(P[1]), k, voix,
            false);
        DaKit.finPx(ctx);
    }

    /**
     * Applique le plafond à {@link #ACTIVES} (dans {@link #GARDEES}, ordre d'affichage gardé). Priorités : Concentration,
     * puis « Voix : … » (réponse à la touche H), puis les actions en cours (de bas en haut), puis le reste de bas en
     * haut. Rien n'est alloué.
     */
    private static int plafonner(int max, boolean voix) {
        int n = nActives;
        if (n <= max) {
            System.arraycopy(ACTIVES, 0, GARDEES, 0, n);
            return n;
        }
        java.util.Arrays.fill(GARDE, 0, n, false);
        int pris = 0;
        for (int passe = 0; passe < 4 && pris < max; passe++) {
            for (int i = 0; i < n && pris < max; i++) {
                if (GARDE[i]) continue;
                Rappel r = ACTIVES[i].rappel;
                boolean ok = switch (passe) {
                    case 0 -> r == Rappel.CONCENTRATION;
                    case 1 -> r == Rappel.PORTEE && voix;
                    case 2 -> r.ordinal() < Rappel.CONCENTRATION.ordinal();   // actions en cours
                    default -> true;
                };
                if (ok) {
                    GARDE[i] = true;
                    pris++;
                }
            }
        }
        int m = 0;
        for (int i = 0; i < n; i++) if (GARDE[i]) GARDEES[m++] = ACTIVES[i];
        return m;
    }

    /**
     * La colonne (déjà dans {@link DaKit#debutPx}) : cases (une seule couche), pictogrammes, glyphes, libellés, puce de
     * « Voix : … ». {@code droite} = bord droit des cases, {@code bas} = bas de la boîte.
     */
    private static void dessiner(GuiGraphicsExtractor ctx, Ligne[] lignes, int n, int droite, int bas, double k,
                                 boolean voix, boolean apercu) {
        Formes f = FORMES.pour(k);
        int colonne = f.caseMin;
        for (int i = 0; i < n; i++) colonne = Math.max(colonne, mesurer(lignes[i], k, f));
        int x0 = droite - colonne, xLibelle = x0 - arr(ECART_LIBELLE * k), h = f.caseMin;
        int col = DaKit.blanc(1f);
        // 1) cases et pictogrammes : une seule couche (la même texture)
        LotBlit l = LotBlit.ouvrir(ctx, ATLAS, ATLAS_L, ATLAS_H);
        for (int i = 0; i < n; i++) {
            Ligne g = lignes[i];
            int yc = bas - arr((MARGE_BAS + PAS * i) * k), cw = g.largeurCase;
            int xc = x0 + (colonne - cw) / 2, yh = yc - h / 2;
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
            int yc = bas - arr((MARGE_BAS + PAS * i) * k), cw = g.largeurCase;
            int xc = x0 + (colonne - cw) / 2;
            if (g.nom.picto() == NomTouche.TEXTE) {
                g.touche.ligne(g.nom.texte(), g.ponctuation ? TexteCache.Style.TOUCHE_PONCT : TexteCache.Style.TOUCHE,
                    CAP_TOUCHE * k, k, 0, xc + cw / 2.0, yc).dessinerPx(ctx, 1f);
            }
            if (g.rappel == Rappel.PORTEE && voix) {
                VOIX.ligne(voixTexte, TexteCache.Style.LIBELLE, CAP * k, k, 0, xLibelle, yc).dessinerPx(ctx, 1f);
                if (voixLargeur < 0 || voixK != k) {
                    voixLargeur = TexteRaster.largeur(voixTexte, Police.INTER_GRAS, CAP * k, 0, true);
                    voixK = k;
                }
                if (!Double.isNaN(voixLargeur)) {
                    int np = f.puce.w();
                    int xp = arr(xLibelle - voixLargeur - arr(ECART_PUCE * k) - np);
                    poigneePuce(voixRgb).forme(f.puce, voixRgb, k, xp, yc - np / 2).dessinerPx(ctx, 1f);
                }
            } else {
                g.texte.ligne(g.rappel.libelle, TexteCache.Style.LIBELLE, CAP * k, k, 0, xLibelle, yc).dessinerPx(ctx, 1f);
            }
            if (!apercu) {
                if (g.rappel == Rappel.DESCENDRE) descendreAffiche = true;
                else if (g.rappel == Rappel.SE_LEVER) seLeverAffiche = true;
            }
        }
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

    /** Boîte de l'élément à l'échelle 1, en GUI (éditeur de HUD) : six lignes au bas de l'écran. */
    public static HudElementBounds boite(int largeurGui, int hauteurGui) {
        var win = Minecraft.getInstance().getWindow();
        int gs = Math.max(1, win.getGuiScale());
        double k0 = BasDroite.k0();
        int droite = win.getWidth() - arr(BOITE_DROITE * k0), bas = win.getHeight() - arr(BOITE_BAS * k0);
        int x = Math.floorDiv(droite - arr(BOITE_L * k0), gs), y = Math.floorDiv(bas - arr(BOITE_H * k0), gs);
        return new HudElementBounds(x, y, Math.ceilDiv(droite, gs) - x, Math.ceilDiv(bas, gs) - y);
    }

    /**
     * Éditeur de HUD : le gabarit le plus grand (concentration : Sacoche, Animations, Mode photo, Vue, Portée de la voix,
     * Concentration), avec les touches du joueur, pour qu'on le place sans surprise (maquette t2-c). Une touche non liée
     * retire sa ligne.
     */
    public static void apercu(GuiGraphicsExtractor ctx, HudElementState st) {
        if (!Police.INTER_GRAS.chargee()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) return;
        var win = mc.getWindow();
        int W = win.getWidth(), H = win.getHeight(), gs = Math.max(1, win.getGuiScale());
        double k0 = BasDroite.k0(), k = k0 * st.scale();
        placer(st, W, H, gs, k0);
        int n = 0;
        for (Ligne g : APERCU) if (g.nom.lire(touche(g.rappel, mc))) APERCU_LIEES[n++] = g;
        if (n == 0) return;
        DaKit.debutPx(ctx);
        dessiner(ctx, APERCU_LIEES, n, (int) Math.round(P[0]) - arr(MARGE_DROITE * k), (int) Math.round(P[1]), k,
            false, true);
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
        s.append(mode.name().toLowerCase(java.util.Locale.ROOT)).append(" : ");
        for (int i = 0; i < nActives; i++) {
            Ligne g = ACTIVES[i];
            if (i > 0) s.append(" · ");
            s.append(g.rappel.libelle).append(" [").append(g.nom.texte()).append(']');
        }
        if (nActives == 0) s.append("aucun");
        return s.toString();
    }
}
