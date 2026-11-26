package fr.reborn.hud.mur;

import fr.reborn.hud.camera.RebornCamera;
import fr.reborn.hud.menu.settings.RebornPrefs;
import fr.reborn.hud.runtime.RebornSession;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * <b>Caméra de paroi</b> (lot 5, étape C1a, cahier §1) : « la paroi devient le sol ». Le temps du mur, la caméra d'épaule
 * vit dans un <b>repère de surface</b> dont le haut est la normale {@code n} de la paroi, roulis compris. Rien d'autre ne
 * change pour le joueur : la souris tourne le regard autour de {@code n} et l'incline (± 89°), exactement les gestes du
 * sol ; distance, épaule et hauteur sont celles de son réglage ({@link RebornCamera}), appliquées dans le repère tourné.
 *
 * <p><b>Repère.</b> L'orientation de la caméra est {@code repère × R(lacet, tangage)}, où {@code R} est la rotation
 * ordinaire de la caméra du jeu et {@code lacet}, {@code tangage} l'orbite de {@link RebornCamera} : au mur, ces deux
 * angles sont <b>relatifs à la paroi</b>. Le repère visé d'une face est un quart de tour autour de sa tangente : il
 * couche le haut du monde sur {@code n} et transforme « regarder le mur » en « regarder vers le haut du mur ». Au sol, le
 * repère visé est le monde. Le repère courant rejoint le repère visé par interpolation sphérique lissée
 * ({@value #RATTRAPAGE} par seconde), plafonnée à 100° par seconde : un quart de tour dure environ une seconde, à
 * l'accroche comme à la sortie (le roulis ne se défait jamais d'un coup).
 *
 * <p><b>Pivot.</b> Les yeux du corps couché : {@code P + 0,72 n}, à mi-hauteur du cube, soit à 1,62 de la paroi. Le
 * décalage du pivot par rapport au joueur est lissé (5 par seconde, 3 blocs par seconde au plus).
 *
 * <p><b>Réglage de confort</b> « Caméra sur les murs » ({@link RebornPrefs#camMurDroite}) : « Suit la paroi » (défaut) ou
 * « Droite ». « Droite » : le repère reste celui du monde (aucun roulis), seul le pivot change ; les touches sont alors
 * fixes ({@link MurEntrees}).
 *
 * <p><b>Ce que lisent les autres.</b> {@code CameraThirdPersonMixin} appelle {@link #image} à chaque image tant que
 * {@link #actif}, puis lit {@link #pivotX}…, {@link #ORIENTATION}, {@link #VISEE}, {@link #HAUT} et {@link #GAUCHE}.
 * {@code KeyboardInputMixin} appelle {@link #regarder} : le regard envoyé au serveur est la visée réelle, dans le monde.
 * {@link MurClient} appelle {@link #accrocher}, {@link #tenir}, {@link #lacher}, {@link #tick}, {@link #oublier}, et
 * {@link #changerFace} quand le marcheur passe d'une face à une autre. Hors du mur : une lecture de booléen. Aucune
 * allocation par image.
 */
public final class MurCamera {

    /** Lissage du repère : part de l'écart rattrapée par seconde. */
    private static final double RATTRAPAGE = 4.5;
    /** Plafond de la vitesse angulaire du repère (radians par seconde) : 100° par seconde. */
    private static final double VITESSE_MAX = Math.toRadians(100.0);
    /** Pivot au mur : {@code P + 0,72 n + 0,3 haut}. */
    private static final double PIVOT_NORMALE = 0.72, PIVOT_HAUT = 0.3;
    /** Lissage du décalage du pivot : part rattrapée par seconde, et vitesse maximale (blocs par seconde). */
    private static final double PIVOT_RATTRAPAGE = 5.0, PIVOT_VITESSE = 3.0;
    /** Pas de temps maximal d'une image (un gel du jeu ne fait pas sauter le repère). */
    private static final double DT_MAX = 0.05;
    /** Écart d'angle (radians) et de pivot (blocs) sous lesquels la cible est tenue pour atteinte. */
    private static final double ANGLE_NUL = 1.0e-3, PIVOT_NUL = 5.0e-3;
    /**
     * Après ce nombre de mises à jour de la caméra du jeu sans image de la caméra de paroi (mode photo, autre vue, écran
     * de caméra), le retour se termine sans fondu. Compté en images et non en temps : après un gel du jeu, les ticks de
     * rattrapage s'enchaînent sans image, et une mesure au temps couperait le retour d'un coup (vu au banc le 25/11).
     */
    private static final int SANS_IMAGE_MAX = 3;
    private static final float QUART = (float) (Math.PI / 2), DEMI_TOUR = (float) Math.PI;

    /** La caméra de paroi tient la caméra d'épaule (au mur, ou pendant le retour) : seule lecture hors du mur. */
    public static volatile boolean actif;

    /** Résultat de {@link #image} : le pivot autour duquel la caméra d'épaule se place. */
    public static double pivotX, pivotY, pivotZ;
    /** Résultat de {@link #image} et de {@link #regarder} : orientation complète de la caméra (roulis compris). */
    public static final Quaternionf ORIENTATION = new Quaternionf();
    /** Base de la caméra dans le monde, tirée de {@link #ORIENTATION} : visée, haut et gauche (unitaires). */
    public static final Vector3f VISEE = new Vector3f(), HAUT = new Vector3f(), GAUCHE = new Vector3f();
    /** Lacet et tangage (degrés du jeu) de la visée réelle ; le lacet garde sa valeur quand la visée est verticale. */
    public static float lacet, tangage;

    /** Repère courant de la caméra (monde = identité) et repère visé. */
    private static final Quaternionf REPERE = new Quaternionf(), CIBLE = new Quaternionf();
    /** Le repère courant est exactement celui du monde. */
    private static boolean neutre = true;
    private static boolean tenu;
    private static MurFace face;
    private static long imageNs;
    /** Mises à jour de la caméra du jeu depuis la dernière image de la caméra de paroi. */
    private static int sansImage;
    /** Décalage courant du pivot par rapport à la position du joueur. */
    private static double decX, decY, decZ;
    /** Réglage du joueur, repris à la fin du retour quand le mur a imposé l'épaule. */
    private static RebornCamera.Mode modeAvant;
    private static CameraType vueAvant;

    private MurCamera() {}

    /** Réglage « Caméra sur les murs » : vrai = « Suit la paroi » (roulis), faux = « Droite ». */
    public static boolean suit() {
        return !RebornPrefs.INSTANCE.camMurDroite && RebornCamera.INSTANCE.isEnabled();
    }

    /** Angle (degrés) entre le repère courant et celui du monde : 0 au sol, 90 quand la paroi est devenue le sol. */
    public static double roulis() {
        return neutre ? 0.0 : Math.toDegrees(2.0 * Math.acos(Math.min(1.0, Math.abs(REPERE.w))));
    }

    /** La face dont la caméra vise le repère ; {@code null} hors du mur. */
    public static MurFace face() { return tenu ? face : null; }

    /* ================================================================ cycle (MurClient) */

    /** L'accroche est confirmée (avant la pose de nage : le pivot part de l'œil debout). */
    static void accrocher(Minecraft mc, LocalPlayer p, MurFace f) {
        tenir(mc);
        if (!actif) {
            REPERE.identity();
            neutre = true;
            decX = 0.0;
            decY = p.getEyeHeight();
            decZ = 0.0;
            imageNs = Util.getNanos();
            sansImage = 0;
        }
        face = f;
        tenu = true;
        actif = true;
    }

    /** À chaque tick au mur : la 1re personne et la caméra de Minecraft passent en épaule (F5 pressée au mur comprise). */
    static void tenir(Minecraft mc) {
        RebornCamera cam = RebornCamera.INSTANCE;
        if (!RebornSession.rpFeaturesEnabled() || cam.mode() == RebornCamera.Mode.SHOULDER || mc.options == null) return;
        // Un écran cinématique tient la caméra en VANILLA (essayage du tailleur, test de la Feuille) : ce mode n'est
        // pas le réglage du joueur, on ne le retient pas et on ne le lui reprend pas.
        if (fr.reborn.hud.boutique.CameraEssayage.INSTANCE.active()
            || fr.reborn.hud.menu.tirage.CameraTirage.INSTANCE.active()) return;
        modeAvant = cam.mode();
        vueAvant = mc.options.getCameraType();
        cam.setMode(RebornCamera.Mode.SHOULDER);
        cam.tickView(mc);
    }

    /**
     * <b>Changement de face</b> (coins, temps 2) : à appeler au début du virage, avec la face d'arrivée. Le repère visé
     * devient celui de {@code vers} et le cap est conservé : la rotation autour de la verticale qui amène l'ancienne
     * normale sur la nouvelle est reportée sur l'orbite (lacet relatif) et retirée du repère courant, si bien que l'image
     * ne bouge pas à l'instant de l'appel ; le lissage de {@link #image} fait ensuite tourner la caméra avec le corps.
     * Sans effet hors du mur ou vers la même face. Vaut aussi en caméra « Droite » (la caméra tourne autour de la
     * verticale, sans roulis).
     */
    public static void changerFace(MurFace vers) {
        if (!tenu || vers == null || vers == face) return;
        MurFace de = face;
        // angle signé de l'ancienne normale vers la nouvelle, autour de la verticale (sens direct)
        float angle = (float) Math.atan2(de.nz * vers.nx - de.nx * vers.nz, de.nx * vers.nx + de.nz * vers.nz);
        face = vers;
        RebornCamera.INSTANCE.decaler(-Math.toDegrees(angle), 0.0);
        REPERE.rotateY(-angle);
        neutre = false;
    }

    /** Le joueur quitte le mur : le repère revient vers celui du monde, le pivot vers l'œil ; rien ne saute. */
    static void lacher(Minecraft mc) {
        if (!tenu) return;
        tenu = false;
        // sans caméra d'épaule (serveur sans les fonctions RP), aucune image ne jouerait le retour
        if (!RebornCamera.INSTANCE.isEnabled()) finir(mc);
    }

    /** Déconnexion, changement de monde : tout est rendu tout de suite. */
    static void oublier(Minecraft mc) {
        tenu = false;
        if (actif || modeAvant != null) finir(mc);
    }

    /** Une fois par tick : termine un retour que plus aucune image ne fait avancer. */
    static void tick(Minecraft mc) {
        if (actif && !tenu && sansImage >= SANS_IMAGE_MAX) finir(mc);
    }

    /** Début de chaque mise à jour de la caméra du jeu ({@code CameraThirdPersonMixin}), avant {@link #image}. */
    public static void miseAJour() {
        if (actif && sansImage < SANS_IMAGE_MAX) sansImage++;
    }

    /** Fin du retour : la caméra d'épaule reprend seule, et le joueur retrouve la vue qu'il avait avant le mur. */
    private static void finir(Minecraft mc) {
        actif = false;
        REPERE.identity();
        neutre = true;
        face = null;
        if (modeAvant == null) return;
        RebornCamera cam = RebornCamera.INSTANCE;
        // il a pu changer de vue pendant le retour : son choix le plus récent l'emporte
        if (cam.mode() == RebornCamera.Mode.SHOULDER) {
            cam.setMode(modeAvant);
            if (mc.options != null && modeAvant == RebornCamera.Mode.VANILLA && vueAvant != null) mc.options.setCameraType(vueAvant);
            cam.tickView(mc);
        }
        modeAvant = null;
        vueAvant = null;
    }

    /* ================================================================ image et regard */

    /**
     * Une image de la caméra d'épaule ({@code CameraThirdPersonMixin}, joueur local seulement) : avance le repère et le
     * pivot, puis pose {@link #pivotX}…, {@link #ORIENTATION} et la base de la caméra. Peut éteindre {@link #actif} (retour
     * terminé) : l'appelant le relit.
     */
    public static void image(Entity e, float pt) {
        long now = Util.getNanos();
        double dt = Mth.clamp((now - imageNs) / 1.0e9, 0.0, DT_MAX);
        imageNs = now;
        sansImage = 0;

        // repère : vers la paroi au mur (réglage « Suit la paroi »), vers le monde sinon
        boolean paroi = tenu && suit();
        if (paroi) CIBLE.rotationAxis(QUART, face.tx, 0f, face.tz);
        else CIBLE.identity();
        double angle = 0.0;
        if (paroi || !neutre) {
            double scalaire = Math.abs(REPERE.x * CIBLE.x + REPERE.y * CIBLE.y + REPERE.z * CIBLE.z + REPERE.w * CIBLE.w);
            angle = 2.0 * Math.acos(Math.min(1.0, scalaire));
            if (angle < ANGLE_NUL) {
                REPERE.set(CIBLE);
                neutre = !paroi;
                angle = 0.0;
            } else {
                REPERE.slerp(CIBLE, (float) Math.min(1.0 - Math.exp(-RATTRAPAGE * dt), VITESSE_MAX * dt / angle)).normalize();
                neutre = false;
            }
        }

        // pivot : les yeux du corps couché au mur, l'œil du jeu sinon
        double cx = 0.0, cy = e.getEyeHeight(), cz = 0.0;
        if (tenu) {
            cx = face.nx * PIVOT_NORMALE;
            cy = PIVOT_HAUT;
            cz = face.nz * PIVOT_NORMALE;
        }
        double ex = cx - decX, ey = cy - decY, ez = cz - decZ, reste = Math.sqrt(ex * ex + ey * ey + ez * ez);
        if (reste < PIVOT_NUL) {
            decX = cx;
            decY = cy;
            decZ = cz;
            reste = 0.0;
        } else {
            double part = Math.min(1.0 - Math.exp(-PIVOT_RATTRAPAGE * dt), PIVOT_VITESSE * dt / reste);
            decX += ex * part;
            decY += ey * part;
            decZ += ez * part;
        }
        pivotX = Mth.lerp(pt, e.xo, e.getX()) + decX;
        pivotY = Mth.lerp(pt, e.yo, e.getY()) + decY;
        pivotZ = Mth.lerp(pt, e.zo, e.getZ()) + decZ;

        orienter();
        if (!tenu && neutre && reste == 0.0) finir(Minecraft.getInstance());
    }

    /**
     * {@code KeyboardInputMixin}, à chaque tick au mur : le regard de l'entité (celui que le serveur et les autres joueurs
     * connaissent) est la <b>visée réelle</b> de la caméra, dans le monde, roulis en cours compris.
     */
    public static void regarder(LocalPlayer p) {
        orienter();
        p.setYRot(lacet);
        p.setXRot(tangage);
        p.setYHeadRot(lacet);
    }

    /** {@code orientation = repère × R(lacet, tangage)} de l'orbite, puis la base de la caméra et ses angles dans le monde. */
    private static void orienter() {
        RebornCamera cam = RebornCamera.INSTANCE;
        float l = (float) cam.camYaw(), t = (float) cam.camPitch();
        ORIENTATION.set(REPERE).rotateY(DEMI_TOUR - l * Mth.DEG_TO_RAD).rotateX(-t * Mth.DEG_TO_RAD);
        ORIENTATION.transform(0f, 0f, -1f, VISEE);
        ORIENTATION.transform(0f, 1f, 0f, HAUT);
        ORIENTATION.transform(-1f, 0f, 0f, GAUCHE);
        if (neutre) {
            lacet = l;
            tangage = t;
            return;
        }
        // visée verticale (on regarde le long du mur, vers le ciel ou vers le sol) : le lacet n'a plus de sens, on le garde
        if (VISEE.x * VISEE.x + VISEE.z * VISEE.z > 1.0e-8f) lacet = (float) Math.toDegrees(Math.atan2(-VISEE.x, VISEE.z));
        tangage = (float) Math.toDegrees(-Math.asin(Mth.clamp(VISEE.y, -1f, 1f)));
    }
}
