package fr.reborn.hud.recit;

import com.google.gson.JsonObject;
import com.mojang.blaze3d.platform.NativeImage;
import fr.reborn.hud.chat.ChatPanel;
import fr.reborn.hud.element.HudAnchor;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementBounds;
import fr.reborn.hud.element.HudElementState;
import fr.reborn.hud.immersion.CinemaBars;
import fr.reborn.hud.immersion.PhotoMode;
import fr.reborn.hud.ko.KoClient;
import fr.reborn.hud.ko.KoHud;
import fr.reborn.hud.ko.KoPayload;
import fr.reborn.hud.runtime.HudTransform;
import fr.reborn.hud.staff.EffetsStaff;
import fr.reborn.hud.ui.DaKit;
import fr.reborn.hud.ui.EcranOpaque;
import fr.reborn.hud.ui.TexteCache;
import fr.reborn.hud.ui.TexteRaster;
import fr.reborn.hud.ui.TexteRaster.Equilibrage;
import fr.reborn.hud.ui.TexteRaster.Mise;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

/**
 * Dessin du <b>texte au centre</b> ({@link Recit} : file, minuteurs, pages) — élément HUD {@code reborn-hud:recit},
 * posé juste après {@code reborn-hud:staff-effets} (donc au-dessus de l'écran noir du staff) : un seul blit à 1:1, au
 * moteur du lot ({@link TexteRaster} / {@link TexteCache}).
 *
 * <p><b>Allure</b> : celle du {@link Gabarit} de chaque texte, relue à chaque image — police, préréglage (contour,
 * ombre, gamma), capitales et interligne en 1080p × f, largeur du bloc, centre vertical par défaut, rayon du contour,
 * voile ou vignette plein écran, fondus. La narration du staff est en {@link Gabarit#NARRATION} (plein écran, blanc pur, plus grande,
 * décision du 02/11) ; une stèle ou une plaque lue en concentration (lot 4) en {@link Gabarit#STELE} (maquettes a1 à
 * a8 : capitales 12 px, interligne 21 px, 340 px, centre à 54 %). Lignes centrées et équilibrées, typographie française
 * du moteur, aucun fond. Fondus d'opacité seule (ni glissement ni changement de taille) par l'alpha des sommets (la
 * couche entière, d'un bloc) : entrée (ease-out), sortie, coupe (depuis l'opacité du moment) et pages aux temps du
 * gabarit (narration : 600 / 700 ms, stèle : 250 / 350 ms, vidéos de Ketto) ; retour après un masquage : 180 ms. Pendant
 * la concentration ({@link Recit#concentration}, lot 4), les bords de l'écran s'assombrissent légèrement
 * ({@link Gabarit#BORDS_CONCENTRATION}), avec ou sans texte ; avec une narration, la plus forte des deux vignettes.
 * Vignette et bords sont posés <b>sous le HUD</b> (couche {@code reborn-hud:recit-vignette}, après les surcouches
 * vanilla : le monde seul s'assombrit) ; la vignette de la narration suit la narration entière, pas chaque page.
 *
 * <p><b>Place</b>, quand le message ne donne pas {@code pos} (centre du gabarit sinon) :
 * <ul>
 *   <li>à terre : centre à 40 %, et le bloc reste une interligne au-dessus du groupe « À terre »
 *       ({@link KoHud#hautATerrePx} : n'agit que sur un grand texte) ; inconscient : centre = max(60 % ; bas du bloc KO
 *       + 2 interlignes + n × P/2), n = lignes de la page, P = interligne ({@link KoHud#basInconscientPx}) ;</li>
 *   <li>écran noir du staff qui porte un texte : haut du bloc = bas de ce texte + 2 interlignes
 *       ({@link EffetsStaff#basTexteNoirPx}) ;</li>
 * </ul>
 * et toujours : tchat ouvert dont le panneau touche le bloc → centre x = bord droit du tchat + 24 px + largeur/2 si le
 * bloc y tient avec 24 px de marge des deux côtés, sinon centré et remonté au-dessus du panneau (ou posé sur le tchat
 * faute de place) (panneau {@link ChatPanel} à l'échelle et au décalage de l'élément CHAT) ; le bloc reste dans l'écran. Les
 * déplacements se font par pixels entiers depuis (W/2 ; centre par défaut), glissés en 120 ms : aucune texture n'est
 * recalculée.
 *
 * <p><b>Visibilité</b> (les deux allures) : HUD normal et tchat ouvert ; F1 « BARS » (dessiné après les bandes par
 * {@code mixin/InGameHudCinemaMixin}, le reste du HUD coupé) ; par-dessus l'écran noir du staff ; à terre et inconscient
 * (pas dans {@code KoMasqueHud}) ; pendant la douane. Masqué, minuteur qui continue : F1 « CLEAN » et mode photo (le HUD
 * entier est coupé), tout autre écran ouvert, HUD masqué. Pendant l'affichage, le titre / sous-titre vanilla est
 * masqué et son minuteur attend (il passe ensuite en entier) ; le viseur seulement si le bloc couvre le centre ; noms au regard, bulles et /me qui touchent le bloc élargi de 12 px passent à 25 %
 * ({@link #attenuation}, appelée par {@code Nametags}, {@code SpeechBubbles} et {@code MeBulles}).
 *
 * <p>Deux poignées : la page affichée et la suivante (page d'après, narration en attente, plaque qui reviendra), rendue
 * d'avance sur le fil du texte, chacune à son gabarit. Coût par image : quelques comparaisons, un blit, <b>aucune
 * allocation</b> ; textures libérées quand plus rien n'est prévu.
 */
public final class RecitHud {

    public static final Identifier ID = Identifier.fromNamespaceAndPath("reborn-hud", "recit");
    private static final Identifier STAFF_EFFETS = Identifier.fromNamespaceAndPath("reborn-hud", "staff-effets");

    /* ---------------------------------------------------------------- règles de place (brief §2.4, les deux allures) */
    /** À terre : centre à 40 % ; inconscient : 60 % au moins. */
    private static final double A_TERRE = 0.40, INCONSCIENT = 0.60;
    /** Sous le bloc KO et sous le texte de l'écran noir : 2 interlignes. */
    private static final int INTERLIGNES_SOUS = 2;
    /** Écart au panneau du tchat et marge au bord de l'écran (px 1080p × f). */
    private static final int MARGE_TCHAT = 24;
    /** Centre dégagé : bloc élargi de 12 px (× f) ; au-delà, retour progressif sur 6 px de plus (pas de saut). */
    private static final int ELARGI = 12, RAMPE = 6;
    private static final float ATTENUE = 0.25f;
    /** Glissement d'un décalage, retour après un masquage, absence (ms, temps réel). */
    private static final long GLISSE_MS = 120, RETOUR_MS = 180, ABSENT_MS = 250;

    /** Banc : la couche n'est pas posée (fond nu de la même image : viseur, noms à 25 %, tout le reste identique). */
    public static boolean debugMasque;

    /* ---------------------------------------------------------------- plaque ancrée devant l'objet (lot 4, étape B) */
    /** Point du monde où poser le texte d'une plaque ({@code "plaque:<id>"}) ; faux = texte au centre de l'écran. */
    @FunctionalInterface
    public interface AncreTexte {
        boolean point(String recitId, double[] out);
    }

    /**
     * Fourni par {@code concentration.Plaques} : la plaque lue est <b>ancrée devant son objet</b> (planche c4) — point
     * projeté à chaque image ({@link fr.reborn.hud.anchor.ProjectionMonde}), capitales = 84 / profondeur (12 px à 7 blocs
     * de la caméra), bornées de 10 à 16 px, par paliers de 1 px (le texte n'est re-rastérisé qu'au changement de palier),
     * toute la mise en page à l'échelle (largeur, interligne, contour, ombre) ; halo derrière ({@code HaloPlaque}), repère
     * « 1 / 2 › » sous le bloc. Hors champ (derrière la caméra) : fondu de sortie, jamais collé au bord.
     */
    public static volatile AncreTexte ancres;
    /** Capitales d'une plaque ancrée : 84 / profondeur, de 10 à 16 px (1080p). */
    public static final double ANCRE_K = 84.0;
    public static final int PALIER_MIN = 10, PALIER_MAX = 16;
    private static final double[] PT = new double[3], PT2 = new double[3];
    private static final org.joml.Vector3f PX = new org.joml.Vector3f();
    /** Palier de capitales (px 1080p) de la plaque ancrée, à la dernière image. */
    private static int palier = 12;
    /** Plaque ancrée dans le champ (1) ou hors champ (0), fondu de 150 ms entre les deux. */
    private static float champ = 1f;
    /** Repère de page « 1 / 2 › » gardé (aucune allocation tant qu'il ne change pas). */
    private static int repereP = -1, repereN = -1;
    private static net.minecraft.network.chat.Component repere;
    /** Banc : la dernière image a posé une plaque ancrée. */
    private static boolean ancreeD;

    /* ---------------------------------------------------------------- poignées : page affichée, page suivante */
    private static TexteCache.Texte texte = new TexteCache.Texte(), suivant = new TexteCache.Texte();
    /** Contenu visé par chaque poignée (la chaîne de la page, comparée par référence). */
    private static String texteDe, suivantDe;
    private static boolean libere = true;

    /* ---------------------------------------------------------------- ce qui est à l'écran */
    private static Recit.Entree aff;
    private static int pAff;
    /** Passage à l'écran de cette page, et première image où sa texture est prête (MAX_VALUE : pas encore). */
    private static long debutAff, pretA = Long.MAX_VALUE;
    /** Fondu de sortie en cours (coupe, ou autre texte voulu) : départ, durée, opacité de départ. */
    private static long sortieA = -1, sortieMs;
    private static float sortieA0;

    /* ---------------------------------------------------------------- place */
    private static int dxC, dyC;
    private static double dxL, dyL;
    private static boolean placeFaite;

    /* ---------------------------------------------------------------- dernière image (temps réel) */
    private static long derniereImage, vuA = Long.MIN_VALUE / 4, retourA = Long.MIN_VALUE / 4;
    /**
     * Dernier instant où le texte était masqué (autre écran, HUD masqué, F1 « CLEAN », mode photo, menu opaque) : au
     * retour, fondu d'entrée. Une image en retard (téléportation, redimensionnement) n'est pas un masquage.
     */
    private static long masqueA = Long.MIN_VALUE / 4;
    /** Bloc posé à la dernière image (couche, px écran), son opacité et l'échelle GUI : règle des 25 %, viseur. */
    private static int bx0, by0, bx1, by1, echelleGui = 2;
    private static float blocA;
    private static long blocVuA = Long.MIN_VALUE / 4;
    private static double fBloc = 1.0;
    /** Dernière image où un texte était à l'écran (fondus compris, texture prête ou non) : viseur et titre masqués. */
    private static long poseA = Long.MIN_VALUE / 4;

    /* ---------------------------------------------------------------- tchat (relu à chaque tick, px écran) */
    private static boolean tchat;
    private static int tchatX1, tchatY0, tchatY1;

    /* ---------------------------------------------------------------- banc : dernière demande posée */
    private static Gabarit gD = Gabarit.STELE;
    private static boolean figeeD;
    private static TexteRaster.Ombre ombreD;
    private static double cxD, cyD, capD, lD, fD, rD, iD;
    private static int pD, wD, hD, appelsCinema;
    private static float aD, bordsD, forceD;
    /** Banc : ce que la dernière image a fait (« posé », « masqué », « rien »…). */
    private static String etatImage = "—";

    private RecitHud() {}

    /**
     * Couche HUD juste après {@code staff-effets} (à appeler après {@code StaffClient.init}), viseur et titre vanilla
     * masqués pendant l'affichage, minuteurs et tchat relus à chaque tick, tout oublié à la déconnexion.
     */
    public static void init() {
        // vignette et bords de la concentration : sous tout le HUD (juste après les surcouches vanilla), sur le monde seul
        HudElementRegistry.attachElementAfter(VanillaHudElements.MISC_OVERLAYS, VIGNETTE_COUCHE, (g, dt) -> coucheVignette(g));
        HudElementRegistry.attachElementAfter(STAFF_EFFETS, ID, (g, dt) -> render(g));
        // viseur masqué : texte au centre, ou concentration (lot 4 : le curseur le remplace)
        HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, orig -> (g, dt) -> {
            if (!masqueViseur() && !fr.reborn.hud.concentration.ModeConcentration.actif()) orig.extractRenderState(g, dt);
        });
        HudElementRegistry.replaceElement(VanillaHudElements.TITLE_AND_SUBTITLE,
            orig -> (g, dt) -> { if (!masqueVanilla()) orig.extractRenderState(g, dt); });
        ClientTickEvents.END_CLIENT_TICK.register(RecitHud::tick);
        ClientPlayConnectionEvents.DISCONNECT.register((h, c) -> c.execute(Recit::vider));
    }

    private static void tick(Minecraft mc) {
        Recit.avancer(Recit.horloge.getAsLong());
        // titre vanilla reçu pendant un texte au centre : masqué, son minuteur attend (il passe après, en entier)
        if (masqueVanilla() && mc.gui != null && mc.gui.hud != null) {
            fr.reborn.hud.mixin.HudAccessor h = (fr.reborn.hud.mixin.HudAccessor) mc.gui.hud;
            if (h.reborn$titreTemps() > 0) h.reborn$titreTemps(h.reborn$titreTemps() + 1);
        }
        lireTchat(mc);
        // masquages où la couche n'est même pas appelée (HUD coupé par F1 « CLEAN », le mode photo, un menu opaque)
        if (CinemaBars.INSTANCE.hidesHud() && !CinemaBars.INSTANCE.barsActive() || PhotoMode.INSTANCE.isActive()
            || EcranOpaque.mondeCache()) masqueA = Util.getMillis();
        // plus rien à montrer et rien à l'écran depuis 1 s (F1 « CLEAN », écran ouvert…) : textures libérées sans attendre
        if (!libere && !Recit.actif() && (aff == null || Util.getMillis() - vuA > 1000)) {
            aff = null;
            sortieA = -1;
            liberer();
        }
    }

    /** Oubli (déconnexion) : rien à l'écran, textures libérées. */
    static void oublier() {
        vigCible = 0;
        vigDe = 0f;
        vigDepart = Long.MIN_VALUE / 4;
        vigEntree = null;
        vignetteForce = 0f;
        aff = null;
        sortieA = -1;
        pretA = Long.MAX_VALUE;
        blocA = 0f;
        liberer();
    }

    /* ================================================================ règles pour les autres éléments */

    /**
     * Facteur d'opacité d'un dessin posé sur le jeu (nom au regard, bulle, /me), boîte en px GUI : 25 % s'il touche le
     * bloc (la couche du texte) élargi de 12 px (× f), retour progressif sur les 6 px suivants (un nom qui glisse quand la
     * caméra tourne ne saute pas d'un coup), suivant l'opacité du bloc (fondus compris) ; 1 si rien n'est affiché. Sans
     * allocation.
     */
    public static float attenuation(float x0, float y0, float x1, float y1) {
        float a = blocA;
        if (a <= 0f || Util.getMillis() - blocVuA > ABSENT_MS) return 1f;
        float g = echelleGui, marge = (float) (ELARGI * fBloc), rampe = (float) (RAMPE * fBloc);
        float dx = Math.max(Math.max(bx0 - x1 * g, x0 * g - bx1), 0f);
        float dy = Math.max(Math.max(by0 - y1 * g, y0 * g - by1), 0f);
        float d = Math.max(dx, dy) - marge;
        float s = d <= 0f ? 1f : d >= rampe ? 0f : 1f - d / rampe;
        return 1f - (1f - ATTENUE) * a * s;
    }

    /** Banc : la page affichée a sa texture (son fondu d'entrée est parti). */
    public static boolean pret() { return aff != null && pretA != Long.MAX_VALUE; }

    /** Titre vanilla masqué (son minuteur attend) : un texte est à l'écran, fondus compris (image courante ou précédente). */
    public static boolean masqueVanilla() { return Util.getMillis() - poseA <= 100; }

    /**
     * Viseur masqué : seulement quand le bloc posé (élargi de 12 px × f) couvre le centre de l'écran — une stèle au
     * centre ; la narration, bas du bloc à 47 %, laisse le viseur libre (un event avec {@code +tenir} se joue viseur
     * compris).
     */
    public static boolean masqueViseur() { return masqueVanilla() && couvreCentre; }

    /* ---------------------------------------------------------------- vignette (couche sous le HUD) */
    private static final Identifier VIGNETTE_COUCHE = Identifier.fromNamespaceAndPath("reborn-hud", "recit-vignette");
    /** Force aux coins voulue à la dernière image (vignette de la narration, bords de la concentration : la plus forte). */
    private static float vignetteForce;
    private static long vignetteVuA = Long.MIN_VALUE / 4;
    /** La couche sous le HUD a posé la vignette à cette image (sinon, F1 « BARS » : posée avec le texte). */
    private static boolean vignetteFaite;
    /**
     * Niveau (0 à 1) de la vignette du gabarit : il suit la <b>narration entière</b>, pas chaque page — entrée avec le
     * fondu d'entrée de la première page, 1 entre les pages et pendant la pause d'une file de narrations, sortie avec le
     * fondu de sortie de la dernière page ou de l'arrêt. Rampe depuis {@code vigDe} (niveau en {@code vigDepart}) vers
     * {@code vigCible}, en {@code vigDuree} ms (ease-out en montée, linéaire en descente, comme le texte).
     */
    private static int vigCible;
    private static long vigDepart = Long.MIN_VALUE / 4, vigDuree = 1;
    private static float vigDe, vigNiveau;
    private static Gabarit vigG;
    private static Recit.Entree vigEntree;
    /** Bloc posé couvrant le centre de l'écran (viseur). */
    private static boolean couvreCentre;

    /** Couche {@code reborn-hud:recit-vignette}, sous le HUD : la vignette voulue à la dernière image. */
    private static void coucheVignette(GuiGraphicsExtractor ctx) {
        vignetteFaite = true;
        if (debugMasque || vignetteForce <= 0.002f || Util.getMillis() - vignetteVuA > 100) return;
        poserVignette(ctx, vignetteForce);
    }

    private static float niveauVignette(long t) {
        float u = clamp((t - vigDepart) / (float) Math.max(1, vigDuree));
        return vigCible == 1 ? vigDe + (1f - vigDe) * easeOut(u) : vigDe * (1f - u);
    }

    private static void viserVignette(int cible, long depuis, long duree) {
        if (cible == vigCible) return;
        vigDe = niveauVignette(depuis);
        vigCible = cible;
        vigDepart = depuis;
        vigDuree = Math.max(1, duree);
    }

    /** Une narration à vignette attend dans la file (la vignette reste pendant la pause entre deux). */
    private static boolean vignetteAttend() {
        Recit.Entree s = Recit.suivante();
        return s != null && !s.plaque && s.gabarit.vignette() > 0f && !s.coupee();
    }

    /** Met à jour la rampe de la vignette du gabarit à l'image {@code now} (après {@link #afficher}). */
    private static void majVignette(long now, Recit.Entree v) {
        Recit.Entree e = aff;
        boolean suite = vignetteAttend();
        if (e != null && e.gabarit.vignette() > 0f) {
            vigG = e.gabarit;
            if (vigEntree != e) {   // nouvelle narration : la vignette entre avec son texte (ou reste, si elle y était)
                if (pretA == Long.MAX_VALUE || sortieA >= 0) return;   // texture en route : rien ne change encore
                vigEntree = e;
                viserVignette(1, Math.max(Math.max(e.debutPage(pAff), debutAff), pretA), e.gabarit.entreeMs());
                return;
            }
            boolean suivanteVignette = v != null && v != e && !v.plaque && v.gabarit.vignette() > 0f;
            if (sortieA >= 0 && e.coupee()) {   // arrêt (ou coupe) : sort avec le texte, sauf si une narration suit
                if (!suivanteVignette && !suite) viserVignette(0, sortieA, sortieMs);
                return;
            }
            int der = e.pages.length - 1;
            long fin = e.finPages[der];
            if (pAff == der && fin != Long.MAX_VALUE && !suite && now >= fin - e.gabarit.sortieMs())
                viserVignette(0, fin - e.gabarit.sortieMs(), e.gabarit.sortieMs());
            else if (sortieA < 0) viserVignette(1, now, e.gabarit.entreeMs());   // une narration arrive pendant la sortie
            return;
        }
        if (e == null && suite) return;   // pause de la file : la vignette reste
        viserVignette(0, now, vigG != null ? vigG.sortieMs() : 350);
    }

    /** Un texte est à l'écran (dernière image dessinable), fondus compris. */
    public static boolean visible() { return masqueVanilla(); }

    /* ================================================================ dessin */

    private static void render(GuiGraphicsExtractor ctx) { dessiner(ctx); }

    /** F1 « BARS » : appelé par {@code mixin/InGameHudCinemaMixin} après les bandes (le reste du HUD est coupé). */
    public static void cinema(GuiGraphicsExtractor ctx) {
        appelsCinema++;
        dessiner(ctx);
    }

    private static void dessiner(GuiGraphicsExtractor ctx) {
        Minecraft mc = Minecraft.getInstance();
        long reel = Util.getMillis();
        long d = reel - derniereImage;
        derniereImage = reel;
        if (d < 0 || d > 250) d = 0;
        long now = Recit.horloge.getAsLong();
        Recit.avancer(now);
        Screen ecran = mc.gui.screen();
        if (mc.player == null || mc.options == null || mc.gui.hud.isHidden()
            || (ecran != null && !(ecran instanceof ChatScreen))) {
            blocA = 0f;
            masqueA = reel;
            etatImage = "masqué";
            vignetteForce = 0f;
            vignetteFaite = false;
            couvreCentre = false;
            return;
        }
        if (masqueA > vuA) retourA = reel;   // de retour après un masquage : fondu d'entrée
        vuA = reel;
        // concentration (lot 4) : bords assombris, avec ou sans texte ; quand le filtre de la concentration est à l'écran
        // (il assombrit déjà les bords, plus fort), la vignette ne s'y ajoute pas : elle ne sert que de repli (Byakugan)
        float bords = fr.reborn.hud.concentration.ModeConcentration.filtreAffiche() ? 0f
            : Recit.bords(now) * Gabarit.BORDS_CONCENTRATION;
        bordsD = bords;

        Recit.Entree v = Recit.voulue();
        if (v != null && v.coupee()) v = null;   // une entrée coupée ne revient pas : son fondu suit celle affichée
        int pv = v == null ? -1 : v.page(now);
        if (aff != null && aff.coupee() && sortieA < 0) {
            long t = Math.min(now, aff.coupeA);
            sortieA0 = alphaNaturel(t);
            sortieA = t;
            sortieMs = aff.coupeMs;
        }
        if (aff == null) {
            if (v != null) afficher(v, pv, now);
        } else if (sortieA >= 0) {
            if (now >= sortieA + sortieMs) afficher(v, pv, now);
        } else if (aff != v || pAff != pv) {
            float a0 = alphaNaturel(now);
            if (a0 <= 0.001f) afficher(v, pv, now);
            else {
                sortieA = now;
                sortieA0 = a0;
                sortieMs = aff.gabarit.coupeMs();   // l'allure qui s'en va garde son fondu
            }
        }

        var win = mc.getWindow();
        int W = win.getWidth(), H = Math.max(1, win.getHeight());
        echelleGui = Math.max(1, win.getGuiScale());
        preparer(v, pv, W, H);
        if (aff == null) {
            majVignette(now, v);
            vignette(ctx, now, bords, reel);
            blocA = 0f;
            aD = 0f;
            couvreCentre = false;
            etatImage = "rien";
            if (Recit.suivante() == null && v == null) liberer();
            return;
        }
        libere = false;
        poseA = reel;

        Gabarit g = aff.gabarit;
        // plaque lue en concentration : ancrée devant son objet (palier de capitales selon la profondeur)
        int ancre = aff.plaque ? ancrer(aff.id) : 0;   // 0 : au centre ; 1 : dans le champ ; -1 : hors champ
        if (ancre != 0) champ = ancre > 0 ? Math.min(1f, champ + d / 150f) : Math.max(0f, champ - d / 150f);
        else champ = 1f;
        double f = g.facteur(W, H) * (ancre != 0 ? palier / g.capitales() : 1.0), cap = g.cap(f);
        double L = aff.figee ? TexteRaster.SANS_LIMITE : g.largeur(f, W);
        double r = g.contour(f);
        int P = g.pas(f);
        String page = aff.pages[pAff];
        Mise m = texte.pret() ? texte.mise() : null;
        double cx0 = W / 2.0, cy0 = centreY(aff, pAff, W, H);
        if (ancre != 0) {
            // décalages entiers depuis (W/2 ; centre du gabarit) : la partie fractionnaire ne change pas, aucun re-rendu
            dxC = (int) Math.round(PX.x - cx0);
            dyC = (int) Math.round(PX.y - cy0);
            placeFaite = false;
        } else cible(aff, m, W, H, f, P, cx0, cy0);
        ancreeD = ancre != 0;
        if (!placeFaite || blocA <= 0f) {
            dxL = dxC;
            dyL = dyC;
        } else {
            double u = Math.min(1.0, d / (double) GLISSE_MS);
            dxL += (dxC - dxL) * u;
            dyL += (dyC - dyL) * u;
            if (Math.abs(dxC - dxL) < 0.5) dxL = dxC;
            if (Math.abs(dyC - dyL) < 0.5) dyL = dyC;
        }
        placeFaite = m != null;
        double cx = cx0 + Math.round(dxL), cy = cy0 + Math.round(dyL);
        texte.bloc(page, g.police(), g.preset(), cap, L, P, r, cx, cy, g.approche(f),
            aff.figee ? Equilibrage.JAMAIS : Equilibrage.AUTO, ombre(g, f));
        if (pretA == Long.MAX_VALUE && texte.pret()) pretA = now;
        majVignette(now, v);
        vignette(ctx, now, bords, reel);

        float a = alpha(now) * champ;
        if (reel - retourA < RETOUR_MS) a *= easeOut((reel - retourA) / (float) RETOUR_MS);
        gD = g;
        figeeD = aff.figee;
        ombreD = ombre(g, f);
        cxD = cx;
        cyD = cy;
        capD = cap;
        lD = L;
        fD = f;
        rD = r;
        iD = g.approche(f);
        pD = P;
        wD = W;
        hD = H;
        aD = a;
        etatImage = !texte.pret() ? "texture en route" : a <= 0.002f ? "opacité nulle" : debugMasque ? "masque du banc" : "posé";
        if (a > 0.002f && texte.pret()) {
            if (!debugMasque) {
                voile(ctx, g, a);
                if (ancre != 0) {
                    // halo lumineux derrière le texte (naît et meurt avec lui), puis le texte
                    DaKit.debutPx(ctx);
                    fr.reborn.hud.concentration.HaloPlaque.dessiner(ctx, cx, cy, f, a, now);
                    DaKit.finPx(ctx);
                }
                texte.dessiner(ctx, a);
                if (aff.plaque && aff.pages.length > 1) repere(ctx, mc, cx, texte.y() + texte.h(), f, a);
            }
            bx0 = texte.x();
            by0 = texte.y();
            bx1 = bx0 + texte.w();
            by1 = by0 + texte.h();
            fBloc = f;
            blocA = a;
            blocVuA = reel;
            double mc0 = ELARGI * f;
            couvreCentre = bx0 - mc0 <= W / 2.0 && W / 2.0 <= bx1 + mc0 && by0 - mc0 <= H / 2.0 && H / 2.0 <= by1 + mc0;
        } else {
            blocA = 0f;
            couvreCentre = false;
        }
    }

    /**
     * Vignette de l'image : celle du gabarit (au niveau de {@link #majVignette}, qui suit la narration entière) ou les
     * bords de la concentration, la plus forte ; gardée pour la couche sous le HUD ({@link #coucheVignette}, image
     * suivante). Si cette couche n'a pas tourné (F1 « BARS » : le HUD est coupé), posée ici, sous le texte.
     */
    private static void vignette(GuiGraphicsExtractor ctx, long now, float bords, long reel) {
        float force = Math.max(vigG == null ? 0f : vigG.vignette() * niveauVignette(now), bords);
        vignetteForce = force;
        vignetteVuA = reel;
        vigNiveau = vigG == null ? 0f : niveauVignette(now);
        forceD = force;
        if (!vignetteFaite && force > 0.002f && !debugMasque) poserVignette(ctx, force);
        vignetteFaite = false;
    }

    /** Voile plein écran du gabarit à l'encre, sous le texte, à l'opacité du texte (aucun gabarit n'en a aujourd'hui). */
    private static void voile(GuiGraphicsExtractor ctx, Gabarit g, float a) {
        if (g.voile() <= 0f) return;
        var win = Minecraft.getInstance().getWindow();
        int a8 = Math.round(Math.min(1f, g.voile() * a) * 255f);
        if (a8 > 0) ctx.fill(0, 0, win.getGuiScaledWidth() + 1, win.getGuiScaledHeight() + 1, a8 << 24 | TexteRaster.ENCRE);
    }

    /**
     * Effets d'écran du gabarit sous le texte, à l'opacité du texte {@code a} (fondus compris) : voile plein écran à
     * l'encre du moteur, vignette (coins assombris, centre intact) ; {@code bords} = assombrissement de la concentration
     * (force aux coins), fondu avec la vignette du gabarit par le maximum. Rien si aucun n'est demandé.
     */
    private static void effetsEcran(GuiGraphicsExtractor ctx, Gabarit g, float a, float bords) {
        if (g.voile() > 0f) {
            var win = Minecraft.getInstance().getWindow();
            int a8 = Math.round(Math.min(1f, g.voile() * a) * 255f);
            if (a8 > 0) ctx.fill(0, 0, win.getGuiScaledWidth() + 1, win.getGuiScaledHeight() + 1, a8 << 24 | TexteRaster.ENCRE);
        }
        poserVignette(ctx, Math.max(g.vignette() * a, bords));
    }

    /** Vignette plein écran à la force {@code force} aux coins (0 : rien). */
    private static void poserVignette(GuiGraphicsExtractor ctx, float force) {
        int v8 = Math.round(Math.min(1f, force) * 255f);
        if (v8 <= 0) return;
        var win = Minecraft.getInstance().getWindow();
        DaKit.etire(ctx, vignette(), 0, 0, win.getGuiScaledWidth() + 1, win.getGuiScaledHeight() + 1, VIGNETTE_W,
            VIGNETTE_H, v8 << 24 | 0xFFFFFF);
    }

    /** Masque de la vignette (noir, alpha = smoothstep de la distance au centre), créé une fois, gardé jusqu'à la fin. */
    private static final int VIGNETTE_W = 320, VIGNETTE_H = 180;
    private static final Identifier VIGNETTE = Identifier.fromNamespaceAndPath("reborn-hud", "recit/vignette");
    private static boolean vignettePrete;

    /**
     * Recette {@code vignette} de la planche d1 : distance elliptique normalisée (0 au centre, 1 aux coins), smoothstep de
     * 0,30 à 1 ; posée à l'opacité {@code force × a}, elle multiplie le monde par 1 − force × t comme la maquette.
     */
    private static Identifier vignette() {
        if (vignettePrete) return VIGNETTE;
        NativeImage img = new NativeImage(VIGNETTE_W, VIGNETTE_H, false);
        for (int y = 0; y < VIGNETTE_H; y++) {
            for (int x = 0; x < VIGNETTE_W; x++) {
                double dx = (x + 0.5 - VIGNETTE_W / 2.0) / (VIGNETTE_W / 2.0), dy = (y + 0.5 - VIGNETTE_H / 2.0) / (VIGNETTE_H / 2.0);
                double t = Math.max(0.0, Math.min(1.0, (Math.sqrt(dx * dx + dy * dy) / Math.sqrt(2.0) - 0.30) / 0.70));
                t = t * t * (3.0 - 2.0 * t);
                img.setPixel(x, y, (int) Math.round(t * 255.0) << 24);
            }
        }
        Minecraft.getInstance().getTextureManager().register(VIGNETTE, new DynamicTexture(() -> "reborn-recit-vignette", img));
        vignettePrete = true;
        return VIGNETTE;
    }

    /** Passe à l'écran l'entrée {@code v}, page {@code pv} (ou rien) ; la page préparée d'avance est reprise telle quelle. */
    private static void afficher(Recit.Entree v, int pv, long now) {
        aff = v;
        pAff = pv;
        debutAff = now;
        pretA = Long.MAX_VALUE;
        sortieA = -1;
        placeFaite = false;
        if (v == null) return;
        String page = v.pages[pv];
        if (page == texteDe) return;
        if (page == suivantDe) {
            TexteCache.Texte t = texte;
            texte = suivant;
            suivant = t;
            String s = texteDe;
            texteDe = suivantDe;
            suivantDe = s;
        } else {
            texte.liberer();   // jamais l'ancien contenu à la place du nouveau
            texteDe = page;
        }
    }

    /**
     * Rend d'avance ce qui viendra, à son propre gabarit : le texte voulu (fondu de sortie en cours), la page d'après, la
     * narration suivante.
     */
    private static void preparer(Recit.Entree v, int pv, int W, int H) {
        Recit.Entree e;
        int p;
        if (v != null && (v != aff || pv != pAff)) {
            e = v;
            p = pv;
        } else if (aff != null && pAff + 1 < aff.pages.length) {
            e = aff;
            p = pAff + 1;
        } else {
            e = Recit.suivante();
            p = e == null ? 0 : e.premiere;   // rattrapage : la page dont la tranche contient « ecoule »
        }
        if (e == null || e.coupee()) return;
        String page = e.pages[p];
        if (page == texteDe) return;
        if (page != suivantDe) {
            suivant.liberer();
            suivantDe = page;
        }
        Gabarit g = e.gabarit;
        double f = g.facteur(W, H);
        AncreTexte an = ancres;
        if (e.plaque && an != null && an.point(e.id, PT2)) f *= palier / g.capitales();
        suivant.bloc(page, g.police(), g.preset(), g.cap(f), e.figee ? TexteRaster.SANS_LIMITE : g.largeur(f, W), g.pas(f),
            g.contour(f), W / 2.0, centreY(e, p, W, H), g.approche(f), e.figee ? Equilibrage.JAMAIS : Equilibrage.AUTO,
            ombre(g, f));
        libere = false;
    }

    /* ---------------------------------------------------------------- ombre douce en px écran, gardée par (gabarit, f) */
    private static Gabarit ombreG1, ombreG2;
    private static double ombreF1 = Double.NaN, ombreF2 = Double.NaN;
    private static TexteRaster.Ombre ombreO1, ombreO2;

    /**
     * Ombre douce du gabarit au facteur f (null : ombre dure), la même instance d'une image à l'autre : deux entrées
     * gardées (page affichée et page suivante peuvent avoir deux allures), rien n'est alloué tant que rien ne change.
     */
    private static TexteRaster.Ombre ombre(Gabarit g, double f) {
        if (g.ombre() == null) return null;
        if (g == ombreG1 && f == ombreF1) return ombreO1;
        if (g == ombreG2 && f == ombreF2) return ombreO2;
        ombreG2 = ombreG1;
        ombreF2 = ombreF1;
        ombreO2 = ombreO1;
        ombreG1 = g;
        ombreF1 = f;
        ombreO1 = g.ombrePx(f);
        return ombreO1;
    }

    private static void liberer() {
        if (libere) return;
        texte.liberer();
        suivant.liberer();
        texteDe = suivantDe = null;
        libere = true;
    }

    /* ================================================================ plaque ancrée */

    /**
     * Point d'ancre de la plaque {@code id} projeté sur l'écran ({@link #PX} en px physiques, {@code z} = profondeur) et
     * palier de capitales : 0 si la plaque n'a pas d'ancre (banc, aperçu : texte au centre), 1 dans le champ, -1 hors
     * champ (derrière la caméra, loin hors du bord : le texte s'efface à sa dernière place). Aucune allocation.
     */
    private static int ancrer(String id) {
        AncreTexte an = ancres;
        if (an == null || !an.point(id, PT)) return 0;
        float x = PX.x, y = PX.y, z = PX.z;
        if (!fr.reborn.hud.anchor.ProjectionMonde.versPx(PT[0], PT[1], PT[2], PX)) {
            PX.set(x, y, z);
            return -1;
        }
        palier = palier(PX.z, palier);
        return 1;
    }

    /** Capitales (px 1080p) d'une plaque à {@code profondeur} blocs de la caméra : 84 / profondeur, de 10 à 16. */
    public static int palier(double profondeur) {
        int p = (int) Math.round(ANCRE_K / Math.max(0.5, profondeur));
        return Math.max(PALIER_MIN, Math.min(PALIER_MAX, p));
    }

    /** Marge d'hystérésis d'un palier (px) : au-delà de l'arrondi, 0,3 px de plus avant de changer. */
    public static final double PALIER_MARGE = 0.3;

    /**
     * Palier avec hystérésis : {@code actuel} est gardé tant que 84 / profondeur reste à moins de 0,5 + {@value
     * #PALIER_MARGE} px de lui — un joueur qui s'accroupit ou marche à la limite d'un palier (vers 6,7 blocs entre 12 et
     * 13 px) ne fait plus rastériser deux pages à chaque passage.
     */
    public static int palier(double profondeur, int actuel) {
        double v = ANCRE_K / Math.max(0.5, profondeur);
        if (actuel >= PALIER_MIN && actuel <= PALIER_MAX && Math.abs(v - actuel) < 0.5 + PALIER_MARGE
            && !(actuel == PALIER_MIN && v < PALIER_MIN) && !(actuel == PALIER_MAX && v > PALIER_MAX)) return actuel;
        return palier(profondeur);
    }

    /**
     * Repère « 1 / 2 › » sous le bloc d'une plaque de plusieurs pages (planche c4-g) : Inter 9 à l'échelle du texte,
     * beige à 85 % avec une ombre d'un pixel, centré, 4 px (× f) sous le bloc, à l'opacité du texte. Le clic gauche tourne
     * la page (sans viser).
     */
    private static void repere(GuiGraphicsExtractor ctx, Minecraft mc, double cx, int basPx, double f, float a) {
        // reconstruit au seul changement de page (ou de nombre de pages) : rien n'est alloué d'une image à l'autre
        if (aff.pageLue != repereP || aff.pages.length != repereN) {
            repereP = aff.pageLue;
            repereN = aff.pages.length;
            repere = DaKit.inter((repereP + 1) + " / " + repereN + "  ›");
        }
        dessinerRepere(ctx, mc, repere, cx, basPx, f, a, echelleGui);
    }

    /** Le repère {@code c} centré en {@code cx}, 4 px (× f) sous {@code basPx} (px écran), échelle GUI {@code gs}. */
    static void dessinerRepere(GuiGraphicsExtractor ctx, Minecraft mc, net.minecraft.network.chat.Component repere, double cx,
                               int basPx, double f, float a, float gs) {
        float s = (float) (1.3 * f / gs);   // capitales d'environ 7 px à f = 1 (le repère reste discret)
        float w = mc.font.width(repere) * s;
        float x = (float) (cx / gs) - w / 2f, y = (float) ((basPx + 4 * f) / gs);
        DaKit.texteEchelle(ctx, mc.font, repere, x + 1f / gs, y + 1f / gs, DaKit.argb(0x000000, 0.45f * a), s);
        DaKit.texteEchelle(ctx, mc.font, repere, x, y, DaKit.argb(0xE8DCC0, 0.85f * a), s);
    }

    /** Banc : la dernière image a posé une plaque ancrée devant son objet (opacité non nulle). */
    public static boolean ancree() { return ancreeD && aD > 0.002f; }

    /** Banc : palier de capitales de la plaque ancrée (px 1080p). */
    public static int palier() { return palier; }

    /** Banc : centre du texte posé à la dernière image (px écran) et son opacité. */
    public static double centreX() { return cxD; }

    public static double centreYPose() { return cyD; }

    public static float opacite() { return aD; }

    /* ================================================================ opacité */

    /** Opacité de la page affichée : fondu de sortie en cours, sinon entrée (dès que la texture est prête) et sortie. */
    private static float alpha(long t) {
        if (sortieA >= 0) return sortieA0 * clamp(1f - (t - sortieA) / (float) Math.max(1, sortieMs));
        return alphaNaturel(t);
    }

    /**
     * Opacité sans coupe : entrée dès que la texture est prête (fondu d'entrée du gabarit sur la première page montrée,
     * rattrapage compris ; fondu de page entre deux pages), sortie à la fin <b>naturelle</b> de la page.
     */
    private static float alphaNaturel(long t) {
        if (aff == null || pretA == Long.MAX_VALUE) return 0f;
        Gabarit g = aff.gabarit;
        boolean derniere = pAff == aff.pages.length - 1;
        long s = Math.max(Math.max(aff.debutPage(pAff), debutAff), pretA);
        long e = aff.finPages[pAff];   // pas la fin avancée par une coupe : la coupe a son propre fondu (sortieA0)
        float entree = t <= s ? 0f : easeOut((t - s) / (float) (pAff == aff.premiere ? g.entreeMs() : g.pageMs()));
        float sortie = e == Long.MAX_VALUE ? 1f : clamp((e - t) / (float) (derniere ? g.sortieMs() : g.pageMs()));
        return Math.min(entree, sortie);
    }

    private static float easeOut(float u) {
        u = clamp(u);
        return 1f - (1f - u) * (1f - u);
    }

    private static float clamp(float v) { return v <= 0f ? 0f : v >= 1f ? 1f : v; }

    /* ================================================================ place */

    /**
     * Centre vertical par défaut (px) : {@code pos} % de la hauteur, sinon le centre du gabarit ; gabarit au bas ancré
     * (coupes figées) : bas du bloc à cette part de la hauteur, les n lignes de la page {@code p} au-dessus.
     */
    private static double centreY(Recit.Entree e, int p, int W, int H) {
        if (e.pos >= 0) return e.pos / 100.0 * H;
        Gabarit g = e.gabarit;
        if (g.bas() > 0 && e.figee) {
            // plusieurs pages : le haut du bloc reste celui de la plus haute page (la première ligne ne saute pas)
            int n = e.lignes[p], P = g.pas(g.facteur(W, H));
            return g.bas() * H - e.lignesMax * P + n * P / 2.0;
        }
        return g.centre() * H;
    }

    /** Décalages entiers voulus depuis (W/2 ; centre par défaut) : règles du KO, de l'écran noir, du tchat. */
    private static void cible(Recit.Entree e, Mise m, int W, int H, double f, int P, double cx0, double cy0) {
        int n = m != null ? m.nbLignes : 0;
        double wb = m != null ? m.largeurBloc : 0.0, demi = n * P / 2.0;
        double cy = cy0;
        if (e.pos < 0) {
            KoPayload ko = KoClient.state();
            int phase = ko == null ? 0 : ko.phase();
            if (phase == 1) {
                cy = A_TERRE * H;
                int haut = KoHud.hautATerrePx();   // un grand texte reste au-dessus du groupe « À terre »
                if (haut >= 0 && n > 0) cy = Math.min(cy, haut - P - demi);
            } else if (phase == 2) {
                cy = INCONSCIENT * H;
                int bas = KoHud.basInconscientPx();
                if (bas >= 0) cy = Math.max(cy, bas + INTERLIGNES_SOUS * P + demi);
            }
            int noir = EffetsStaff.basTexteNoirPx();
            if (noir >= 0) cy = Math.max(cy, noir + INTERLIGNES_SOUS * P + demi);
        }
        if (n > 0 && H > 2 * (demi + P)) cy = Math.max(demi + P, Math.min(H - demi - P, cy));
        double cx = cx0;
        if (tchat && n > 0) {
            double marge = ELARGI * f, mT = MARGE_TCHAT * f, voulu = tchatX1 + mT + wb / 2;
            if (voulu > cx0 && tchatY0 < cy + demi + marge && tchatY1 > cy - demi - marge) {
                if (W - tchatX1 - 2 * mT >= wb) cx = Math.min(voulu, W - mT - wb / 2);   // tient à droite du tchat
                else {
                    // trop large (narration plein écran, petite fenêtre) : reste centré, remonte au-dessus du panneau ;
                    // sans la place, il passe sur le tchat, centré
                    double haut = tchatY0 - marge - demi;
                    if (haut >= demi + P) cy = Math.min(cy, haut);
                }
            }
        }
        dxC = (int) Math.round(cx - cx0);
        dyC = (int) Math.round(cy - cy0);
    }

    /** Panneau du tchat ouvert, en px écran (élément CHAT : décalage et échelle du joueur) ; relu à chaque tick. */
    private static void lireTchat(Minecraft mc) {
        tchat = false;
        if (mc.gui == null || !(mc.gui.screen() instanceof ChatScreen)) return;
        HudElementState st = HudTransform.readStateSafely(HudElement.CHAT);
        if (!st.visible()) return;
        var win = mc.getWindow();
        int sw = win.getGuiScaledWidth(), sh = win.getGuiScaledHeight(), gs = Math.max(1, win.getGuiScale());
        HudElementBounds b = ChatPanel.bounds(sw, sh);
        HudAnchor an = st.effectiveAnchor(HudElement.CHAT);
        double ax = b.x() + Math.round(b.width() * an.fx), ay = b.y() + Math.round(b.height() * an.fy), s = st.scale();
        tchatX1 = (int) Math.ceil((ax + (b.right() - ax) * s + st.x()) * gs);
        tchatY0 = (int) Math.floor((ay + (b.y() - ay) * s + st.y()) * gs);
        tchatY1 = (int) Math.ceil((ay + (b.bottom() - ay) * s + st.y()) * gs);
        tchat = true;
    }

    /* ================================================================ aperçu (Poste de garde) */

    /**
     * <b>Aperçu à taille réelle</b> d'un texte au centre, pour l'outil « Narration » du Poste de garde : la même entrée
     * que le joueur recevrait ({@link Recit#apercu} : nettoyage, 1 000 caractères, pages, coupes figées) et la même
     * demande au moteur que {@link RecitHud} (gabarit, f de la fenêtre, capitales, largeur, interligne, contour, ombre,
     * approche, équilibrage), centrée sur le centre vertical du gabarit ; seul le centre horizontal est choisi par
     * l'appelant (à gauche du volet, qui couvre la droite de l'écran). Un blit à 1:1 ; rien n'est alloué tant que le texte
     * et la fenêtre ne changent pas. Fil de rendu ; {@link #liberer} à la fermeture de l'outil.
     */
    public static final class Apercu {
        private final TexteCache.Texte t = new TexteCache.Texte();
        private java.util.List<Recit.Segment> de;
        private Gabarit gDe;
        private int wDe, hDe, page;
        private Recit.Entree e;
        private Gabarit gOmbre;
        private double fOmbre = Double.NaN;
        private TexteRaster.Ombre ombre;

        /**
         * Texte à prévisualiser : {@code segments} comparés par référence (une nouvelle liste = un nouveau texte ; null ou
         * vide = rien). Les pages suivent la fenêtre comme chez le joueur (sauf coupes figées).
         */
        public void poser(java.util.List<Recit.Segment> segments, Gabarit g) {
            var win = Minecraft.getInstance().getWindow();
            int W = Math.max(1, win.getWidth()), H = Math.max(1, win.getHeight());
            if (segments == de && g == gDe && (g.coupesFigees() || W == wDe && H == hDe)) return;
            de = segments;
            gDe = g;
            wDe = W;
            hDe = H;
            e = segments == null || segments.isEmpty() ? null : Recit.apercu(segments, g);
            if (e == null) {
                page = 0;
                t.liberer();
            } else page = Math.min(page, e.pages.length - 1);
        }

        /** Pages du texte (0 : rien à montrer). */
        public int pages() { return e == null ? 0 : e.pages.length; }

        /** Page montrée (0 = la première). */
        public int page() { return page; }

        /** Page suivante (revient à la première après la dernière). */
        public void pageSuivante() { if (e != null) page = (page + 1) % e.pages.length; }

        /** Lignes de la page montrée (0 tant que sa texture n'est pas prête). */
        public int lignes() { return t.pret() && t.mise() != null ? t.mise().nbLignes : 0; }

        /** La page montrée a sa texture. */
        public boolean pret() { return e != null && t.pret(); }

        /** Couche posée à la dernière image (px écran). */
        public int x() { return t.x(); }

        public int y() { return t.y(); }

        public int w() { return t.w(); }

        public int h() { return t.h(); }

        /**
         * Dessine la page montrée, centre horizontal {@code cx} (px écran, entier de préférence : un demi-pixel de plus
         * relance le rendu), centre vertical du gabarit (le bloc reste dans l'écran), à l'opacité {@code alpha}.
         */
        public void dessiner(GuiGraphicsExtractor ctx, double cx, float alpha) { dessiner(ctx, cx, alpha, 1.0); }

        /** Réduction du rendu affiché (1 : taille réelle). */
        public double reduction() { return reductionTex; }

        private double reductionTex = 1.0;

        /**
         * Comme {@link #dessiner(GuiGraphicsExtractor, double, float)}, réduit de {@code reduction} (0 à 1, 1 = taille
         * réelle) : mêmes lignes (coupes figées) et même place verticale, tailles × reduction — pour un aperçu qui ne
         * tient pas à gauche du volet. Une nouvelle réduction relance le rendu : la donner arrondie.
         */
        public void dessiner(GuiGraphicsExtractor ctx, double cx, float alpha, double reduction) {
            if (e == null) return;
            var win = Minecraft.getInstance().getWindow();
            int W = Math.max(1, win.getWidth()), H = Math.max(1, win.getHeight());
            Gabarit g = e.gabarit;
            reduction = Math.max(0.1, Math.min(1.0, reduction));
            double f = g.facteur(W, H) * reduction;
            int P = g.pas(f);
            double cy = reduction >= 1.0 ? centreY(e, page, W, H)
                : g.bas() > 0 && e.figee ? g.bas() * H - e.lignesMax * P + e.lignes[page] * P / 2.0 : g.centre() * H;
            Mise m = t.pret() ? t.mise() : null;
            if (m != null) {
                double demi = m.nbLignes * P / 2.0;
                if (H > 2 * (demi + P)) cy = Math.max(demi + P, Math.min(H - demi - P, cy));
            }
            if (g.ombre() != null && (g != gOmbre || f != fOmbre)) {
                gOmbre = g;
                fOmbre = f;
                ombre = g.ombrePx(f);
            }
            t.bloc(e.pages[page], g.police(), g.preset(), g.cap(f), e.figee ? TexteRaster.SANS_LIMITE : g.largeur(f, W), P,
                g.contour(f), cx, Math.round(cy), g.approche(f), e.figee ? Equilibrage.JAMAIS : Equilibrage.AUTO,
                g.ombre() == null ? null : ombre);
            if (t.ajour()) reductionTex = reduction;
            if (!t.pret() || alpha <= 0.002f) return;
            effetsEcran(ctx, g, alpha, 0f);
            t.dessiner(ctx, alpha);
        }

        private final org.joml.Vector3f px = new org.joml.Vector3f();
        private int palierA = 12;

        /**
         * <b>Aperçu ancré</b> (outil « Plaques », lot 4) : la page montrée posée <b>devant l'objet</b> au point du monde
         * ({@code x, y, z}), au palier de capitales de la profondeur (84 / profondeur, 10 à 16 px), avec le halo derrière
         * si {@code halo} — exactement comme un joueur concentré la lira. Faux si le point est derrière la caméra (rien de
         * dessiné). Le texte n'est re-rastérisé qu'au changement de palier ; aucune allocation par image.
         */
        public boolean dessinerAncre(GuiGraphicsExtractor ctx, double x, double y, double z, float alpha, boolean halo) {
            if (e == null) return false;
            if (!fr.reborn.hud.anchor.ProjectionMonde.versPx(x, y, z, px)) return false;
            palierA = palier(px.z, palierA);
            var win = Minecraft.getInstance().getWindow();
            int W = Math.max(1, win.getWidth()), H = Math.max(1, win.getHeight());
            Gabarit g = e.gabarit;
            double f = g.facteur(W, H) * palierA / g.capitales();
            int P = g.pas(f);
            double cx0 = W / 2.0, cy0 = g.centre() * H;
            double cx = cx0 + Math.round(px.x - cx0), cy = cy0 + Math.round(px.y - cy0);
            if (g.ombre() != null && (g != gOmbre || f != fOmbre)) {
                gOmbre = g;
                fOmbre = f;
                ombre = g.ombrePx(f);
            }
            t.bloc(e.pages[page], g.police(), g.preset(), g.cap(f), g.largeur(f, W), P, g.contour(f), cx, cy, g.approche(f),
                Equilibrage.AUTO, g.ombre() == null ? null : ombre);
            reductionTex = 1.0;
            if (!t.pret() || alpha <= 0.002f) return true;
            if (halo) {
                DaKit.debutPx(ctx);
                fr.reborn.hud.concentration.HaloPlaque.dessiner(ctx, cx, cy, f, alpha, Util.getMillis());
                DaKit.finPx(ctx);
            }
            t.dessiner(ctx, alpha);
            // repère « 1 / 2 › » sous le bloc, comme chez le joueur (le staff voit le découpage des pages avant de poser)
            if (e.pages.length > 1) {
                if (page != repereP || e.pages.length != repereN) {
                    repereP = page;
                    repereN = e.pages.length;
                    repere = DaKit.inter((page + 1) + " / " + repereN + "  ›");
                }
                dessinerRepere(ctx, Minecraft.getInstance(), repere, cx, t.y() + t.h(), f, alpha, Math.max(1, win.getGuiScale()));
            }
            return true;
        }

        private int repereP = -1, repereN = -1;
        private net.minecraft.network.chat.Component repere;

        /** Palier de capitales du dernier aperçu ancré (px 1080p). */
        public int palierAncre() { return palierA; }

        /** Texture libérée, texte oublié. */
        public void liberer() {
            t.liberer();
            reductionTex = 1.0;
            de = null;
            gDe = null;
            e = null;
            page = 0;
        }
    }

    /* ================================================================ banc */

    /** Une ligne d'état (journal du banc). */
    public static String bilan() {
        String id = aff == null ? "—" : aff.id + " (" + aff.gabarit.nom() + ")";
        int n = aff == null ? 0 : aff.pages.length;
        Mise m = texte.pret() ? texte.mise() : null;
        return String.format(java.util.Locale.ROOT, "texte « %s » page %d/%d, opacité %.2f, ancre (%.1f ; %.1f), couche %d,%d "
                + "%dx%d, %d lignes, f %.2f, file %d, image : %s, appels F1 %d%s%s", id, pAff + 1, n, aD, cxD, cyD, texte.x(),
            texte.y(), texte.w(), texte.h(), m == null ? 0 : m.nbLignes, fD, Recit.enAttente(), etatImage, appelsCinema,
            tchat ? ", tchat " + tchatX1 : "", TexteCache.texturesVivantes() > 0 ? ", " + TexteCache.bilan() : "");
    }

    /**
     * Demande posée à la dernière image, avec l'allure (gabarit, police, préréglage, rayon du contour, voile) :
     * comparaison au moteur des maquettes ({@code tools/centre_compare.py}).
     */
    public static JsonObject etatBanc() {
        JsonObject o = new JsonObject();
        o.addProperty("W", wD);
        o.addProperty("H", hD);
        o.addProperty("gabarit", gD.nom());
        o.addProperty("police", gD.police().name());
        JsonObject pr = new JsonObject();
        pr.addProperty("nom", gD.preset().nom);
        pr.addProperty("contour", (double) gD.preset().contour);
        pr.addProperty("ombre", (double) gD.preset().ombre);
        pr.addProperty("gamma", (double) gD.preset().gamma);
        o.add("preset", pr);
        o.addProperty("voile", (double) gD.voile());
        o.addProperty("vignette", (double) gD.vignette());
        o.addProperty("bords", (double) bordsD);
        // vignette réellement posée (la plus forte de celle du gabarit, qui suit la narration entière, et des bords)
        o.addProperty("vignette_force", (double) forceD);
        o.addProperty("vignette_niveau", (double) vigNiveau);
        if (ombreD != null) {
            JsonObject om = new JsonObject();
            om.addProperty("dx", ombreD.dx());
            om.addProperty("dy", ombreD.dy());
            om.addProperty("flou", ombreD.flou());
            o.add("ombre", om);
        }
        o.addProperty("figee", figeeD);
        o.addProperty("equilibrage", figeeD ? "JAMAIS" : "AUTO");
        o.addProperty("interlettre", iD);
        o.addProperty("f", fD);
        o.addProperty("rayon", rD);
        o.addProperty("cap", capD);
        o.addProperty("L", lD);
        o.addProperty("P", pD);
        o.addProperty("cx", cxD);
        o.addProperty("cy", cyD);
        o.addProperty("a", aD);
        o.addProperty("texte", aff == null ? "" : aff.pages[pAff]);
        o.addProperty("id", aff == null ? "" : aff.id);
        o.addProperty("page", pAff);
        o.addProperty("pages", aff == null ? 0 : aff.pages.length);
        Mise m = texte.pret() ? texte.mise() : null;
        o.addProperty("lignes", m == null ? 0 : m.nbLignes);
        o.addProperty("largeur_bloc", m == null ? 0.0 : m.largeurBloc);
        o.addProperty("couche", texte.x() + "," + texte.y() + "," + texte.w() + "," + texte.h());
        return o;
    }
}
