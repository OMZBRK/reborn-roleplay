package fr.reborn.hud.mur;

import fr.reborn.hud.camera.RebornCamera;
import fr.reborn.hud.runtime.RebornSession;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;

/**
 * <b>Cadrage de paroi</b> (lot 5, essai C0 ; étude §4.4) : ce que la caméra d'épaule change le temps du mur, pour que le
 * joueur voie la paroi. <b>Pas de roulis</b> : le monde reste droit, seul le cadre bouge.
 * <ul>
 *   <li><b>foyer</b> : {@code P + 0,4 n + 0,5 haut} au lieu de l'œil (en pose de nage, il tombe à 0,4 et colle au mur) ;</li>
 *   <li><b>recul</b> : 5 blocs, réduit quand le regard se tourne vers l'extérieur (3 à 120° d'écart au mur, 2 à 135° et
 *       au-delà), où la caméra buterait dans la paroi ;</li>
 *   <li><b>épaule côté air</b>, à 0,9 : le décalage éloigne toujours la caméra du mur, sans toucher au réglage du joueur ;</li>
 *   <li><b>un seul glissement</b> de {@value #GLISSE_MS} ms à l'accroche, vers 35° d'écart et 25° de regard levé ; la souris
 *       garde la main pendant et après, il n'y a jamais de recentrage ensuite ;</li>
 *   <li>au-delà de 120° d'écart, <b>notre corps s'efface de notre écran</b> (il le remplirait) ; les autres nous voient ;</li>
 *   <li>1re personne ou caméra de Minecraft : l'épaule le temps du mur, puis retour au réglage du joueur.</li>
 * </ul>
 * Hors du mur, rien ne change : {@code CameraThirdPersonMixin} ne lit que {@link #actif}. Aucune allocation par image.
 */
public final class MurCamera {

    private static final long GLISSE_MS = 350L;
    private static final double FOYER_NORMALE = 0.4, FOYER_HAUT = 0.5, RECUL = 5.0, EPAULE = 0.9;
    private static final double ECART_ACCROCHE = 35.0, TANGAGE_ACCROCHE = -25.0;

    /** Un cadrage est en cours, ou en fondu de sortie : seule lecture de la caméra hors du mur. */
    public static volatile boolean actif;

    /** Résultat de {@link #image} : foyer, recul, décalage d'épaule signé. */
    public static double foyerX, foyerY, foyerZ, recul, epaule;

    private static boolean tenu, glisse, efface;
    private static MurFace face;
    private static long debutMs, imageMs;
    /** Foyer au début du fondu en cours (l'œil debout à l'accroche, le foyer de paroi au lâcher). */
    private static double departX, departY, departZ;
    private static float melange, melangeDepart, glisseFait;
    private static double glisseLacet, glisseTangage, cote;
    /** Réglage du joueur, repris au lâcher quand le mur a imposé l'épaule. */
    private static RebornCamera.Mode modeAvant;
    private static CameraType vueAvant;

    private MurCamera() {}

    /** Notre propre corps doit-il s'effacer de notre écran (regard tourné vers l'extérieur) ? */
    public static boolean efface() { return efface && Util.getMillis() - imageMs < 150L; }

    /** L'accroche est confirmée (avant la pose de nage : le fondu part de l'œil debout). */
    static void accrocher(Minecraft mc, LocalPlayer p, MurFace f) {
        RebornCamera cam = RebornCamera.INSTANCE;
        modeAvant = null;
        tenir(mc);
        if (actif) {
            departX = foyerX;
            departY = foyerY;
            departZ = foyerZ;
        } else {
            departX = p.getX();
            departY = p.getY() + p.getEyeHeight();
            departZ = p.getZ();
        }
        face = f;
        tenu = true;
        efface = false;
        melangeDepart = melange;
        debutMs = imageMs = Util.getMillis();
        cote = cam.rightOffset() < 0 ? -1.0 : 1.0;
        // le seul glissement : vers 35° d'écart, du côté où la caméra se trouve déjà, regard levé de 25°
        double ecart = Mth.wrapDegrees(cam.camYaw() - f.lacet);
        glisseLacet = Mth.wrapDegrees(f.lacet + (ecart < 0 ? -ECART_ACCROCHE : ECART_ACCROCHE) - cam.camYaw());
        glisseTangage = TANGAGE_ACCROCHE - cam.camPitch();
        glisseFait = 0f;
        glisse = true;
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

    /** Le joueur quitte le mur : fondu de retour vers l'œil, et son réglage de caméra lui est rendu. */
    static void lacher(Minecraft mc) {
        if (!tenu) return;
        tenu = false;
        glisse = false;
        efface = false;
        melangeDepart = melange;
        departX = foyerX;
        departY = foyerY;
        departZ = foyerZ;
        debutMs = Util.getMillis();
        if (modeAvant == null) return;
        RebornCamera cam = RebornCamera.INSTANCE;
        cam.setMode(modeAvant);
        if (mc.options != null && modeAvant == RebornCamera.Mode.VANILLA && vueAvant != null) mc.options.setCameraType(vueAvant);
        cam.tickView(mc);
        modeAvant = null;
        actif = false;   // la caméra d'épaule ne tourne plus : pas de fondu à jouer
        melange = 0f;
    }

    /**
     * Une image de la caméra d'épaule pendant le cadrage ({@code CameraThirdPersonMixin}, joueur local seulement) : avance
     * le fondu et le glissement, pose {@link #foyerX}…, {@link #recul} et {@link #epaule}.
     *
     * @return la part du cadrage de paroi dans le recul, l'épaule et la hauteur (0 = réglage du joueur, 1 = paroi)
     */
    public static float image(Entity e, float pt) {
        RebornCamera cam = RebornCamera.INSTANCE;
        long now = Util.getMillis();
        float t = Mth.clamp((now - debutMs) / (float) GLISSE_MS, 0f, 1f);
        float l = t * t * (3f - 2f * t);
        double x = Mth.lerp(pt, e.xo, e.getX()), y = Mth.lerp(pt, e.yo, e.getY()), z = Mth.lerp(pt, e.zo, e.getZ());
        if (tenu) {
            if (glisse) {
                cam.decaler(glisseLacet * (l - glisseFait), glisseTangage * (l - glisseFait));
                glisseFait = l;
                if (t >= 1f) glisse = false;
            }
            melange = melangeDepart + (1f - melangeDepart) * l;
            foyerX = Mth.lerp(l, departX, x + face.nx * FOYER_NORMALE);
            foyerY = Mth.lerp(l, departY, y + FOYER_HAUT);
            foyerZ = Mth.lerp(l, departZ, z + face.nz * FOYER_NORMALE);
        } else {
            melange = melangeDepart * (1f - l);
            foyerX = Mth.lerp(l, departX, x);
            foyerY = Mth.lerp(l, departY, y + e.getEyeHeight());
            foyerZ = Mth.lerp(l, departZ, z);
            if (t >= 1f) {
                actif = false;
                melange = 0f;
            }
        }
        // écart du regard au mur : 0° face à la paroi, 90° le long, 180° dos au mur
        double lacet = cam.camYaw(), ecart = Math.abs(Mth.wrapDegrees(lacet - face.lacet));
        recul = ecart <= 105 ? RECUL : ecart <= 120 ? RECUL - (ecart - 105) * (2.0 / 15.0) : ecart <= 135 ? 3.0 - (ecart - 120) / 15.0 : 2.0;
        // épaule côté air : la droite de la caméra (regard × vertical) projetée sur la normale donne le côté
        double r = Math.toRadians(lacet), versAir = -Math.cos(r) * face.nx - Math.sin(r) * face.nz;
        double cible = versAir > 0.15 ? 1.0 : versAir < -0.15 ? -1.0 : cote < 0 ? -1.0 : 1.0;
        double duree = Math.min(0.1, (now - imageMs) / 1000.0) * 6.0;
        imageMs = now;
        cote += Mth.clamp(cible - cote, -duree, duree);
        epaule = cote * EPAULE;
        efface = tenu && (efface ? ecart > 118 : ecart > 122);
        return melange;
    }
}
