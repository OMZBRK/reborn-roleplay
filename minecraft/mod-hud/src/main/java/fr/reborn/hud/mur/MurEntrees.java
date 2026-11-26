package fr.reborn.hud.mur;

import fr.reborn.hud.camera.RebornCamera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * <b>Direction voulue sur la paroi</b> (lot 5, étape C1a, cahier §1) : ce que les touches de déplacement demandent, dans
 * le plan du mur. C'est la seule intention de la marche sur les murs : {@link MurClient} s'en sert pour marcher, et les
 * passages d'une surface à l'autre (coins, sommet, pied) se déclenchent en poussant dans cette direction contre une limite.
 *
 * <p><b>Touches continues.</b> Caméra « Suit la paroi » : « avant » est la visée de la caméra (dans le repère visé de
 * {@link MurCamera}) projetée sur la paroi et normalisée, « droite » est {@code avant × normale}. Aucun cran, aucune base
 * figée : la direction suit la souris à chaque tick, comme au sol. Si la visée est presque le long de la normale, le
 * dernier « avant » valable est gardé. La visée lue est celle du repère <b>visé</b>, pas celle de l'image en cours de
 * roulis : à l'accroche, « avant » monte dès le premier tick. Visée libre (ALT tenue) : la direction reste celle d'avant
 * l'appui, la caméra tourne seule.
 *
 * <p><b>Touches fixes.</b> Caméra « Droite » (ou sans caméra d'épaule) : avant monte, arrière descend, gauche et droite
 * vont à gauche et à droite de l'écran sur la paroi. Le côté ne change que lorsque la caméra a franchement tourné
 * (zone morte), jamais sous les doigts.
 *
 * <p><b>Repère des composantes.</b> {@link #u} est la part le long de la tangente {@code t} de la face
 * ({@link MurFace#tx}, {@link MurFace#tz}), {@link #v} la part vers le haut du monde. Ce repère se transporte tel quel
 * d'une face à sa voisine (la rotation qui amène une normale sur l'autre amène aussi une tangente sur l'autre) : après
 * {@link MurCamera#changerFace}, les mêmes {@code (u, v)} lus sur la nouvelle face gardent le cap.
 */
public final class MurEntrees {

    /** Sous cette longueur, la visée projetée sur la paroi ne donne plus de direction : le dernier « avant » est gardé. */
    private static final double VISEE_MIN = 0.02;
    /** Caméra droite : la droite de l'écran ne change de côté sur la paroi qu'au-delà de cette part de la tangente. */
    private static final double COTE_MIN = 0.2;

    /** Direction voulue sur la paroi, unitaire : part le long de la tangente de la face, part vers le haut. */
    public static double u, v;
    /** La touche de sprint est tenue. */
    public static boolean course;

    /** Dernier « avant » valable sur la paroi (mêmes composantes), et côté de la droite de l'écran en caméra droite. */
    private static double avantU, avantV = 1.0, cote = 1.0;

    private MurEntrees() {}

    /** Nouvelle accroche : rien n'est gardé de la marche précédente. */
    static void oublier() {
        avantU = 0.0;
        avantV = 1.0;
        cote = 1.0;
        u = v = 0.0;
        course = false;
    }

    /**
     * Lit les touches de déplacement et la caméra pour la face {@code f}.
     *
     * @return vrai si une direction est voulue : {@link #u} et {@link #v} sont alors posés (unitaires), ainsi que
     *     {@link #course}
     */
    public static boolean lire(Minecraft mc, MurFace f) {
        int avant = 0, droite = 0;
        if (mc.gui.screen() == null) {
            avant = (mc.options.keyUp.isDown() ? 1 : 0) - (mc.options.keyDown.isDown() ? 1 : 0);
            droite = (mc.options.keyRight.isDown() ? 1 : 0) - (mc.options.keyLeft.isDown() ? 1 : 0);
        }
        course = mc.options.keySprint.isDown();
        boolean suit = viser(mc.player, f);
        if (avant == 0 && droite == 0) return false;
        double mu, mv;
        if (suit) {
            // sur la paroi : « droite » = avant × n = (avantV, −avantU)
            mu = avant * avantU + droite * avantV;
            mv = avant * avantV - droite * avantU;
        } else {
            mu = droite * cote;
            mv = avant;
        }
        double l = Math.sqrt(mu * mu + mv * mv);
        if (l < 1.0e-6) return false;
        u = mu / l;
        v = mv / l;
        return true;
    }

    /** « Avant » sur la face {@code f}, sans regarder les touches : composantes dans {@code hors[0]} (tangente) et {@code hors[1]} (haut). */
    public static void avant(LocalPlayer p, MurFace f, double[] hors) {
        if (viser(p, f)) {
            hors[0] = avantU;
            hors[1] = avantV;
        } else {
            hors[0] = 0.0;
            hors[1] = 1.0;
        }
    }

    /** La direction voulue {@code (u, v)} de la face {@code f}, dans le monde : {@code hors} reçoit x, y, z. */
    public static void monde(MurFace f, double[] hors) {
        hors[0] = u * f.tx;
        hors[1] = v;
        hors[2] = u * f.tz;
    }

    /**
     * Met à jour ce que la caméra donne aux touches pour la face {@code f}.
     *
     * @return vrai en caméra « Suit la paroi » (touches continues), faux en touches fixes
     */
    private static boolean viser(LocalPlayer p, MurFace f) {
        RebornCamera cam = RebornCamera.INSTANCE;
        boolean epaule = cam.isEnabled(), suit = MurCamera.suit();
        // visée libre (ALT) : la caméra tourne sans réorienter la marche, comme au sol
        if (epaule && cam.freeLook()) return suit;
        double lacet = Math.toRadians(epaule || p == null ? cam.camYaw() : p.getYRot());
        if (suit) {
            // Au mur, l'orbite est relative à la paroi : son cap horizontal h devient, dans le repère visé,
            // (h · t) t − (h · n) haut. La visée complète y ajoute − sin(tangage) n, que la projection retire.
            double hx = -Math.sin(lacet), hz = Math.cos(lacet), c = Math.cos(Math.toRadians(cam.camPitch()));
            double pu = c * (hx * f.tx + hz * f.tz), pv = -c * (hx * f.nx + hz * f.nz);
            double l = Math.sqrt(pu * pu + pv * pv);
            if (l >= VISEE_MIN) {
                avantU = pu / l;
                avantV = pv / l;
            }
            return true;
        }
        // droite de l'écran (visée × verticale) sur la tangente de la paroi
        double d = -Math.cos(lacet) * f.tx - Math.sin(lacet) * f.tz;
        if (d > COTE_MIN) cote = 1.0;
        else if (d < -COTE_MIN) cote = -1.0;
        return false;
    }
}
