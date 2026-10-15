package fr.reborn.hud.eau;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Avatar;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * Les pas de la marche sur l'eau, <b>simulés par chaque client pour tous les marcheurs qu'il voit</b> : un joueur (ou
 * un mannequin) dont les pieds sont posés à la surface de l'eau, sans y être, est un marcheur — aucune donnée réseau à
 * relayer, tout le monde voit les mêmes flaques.
 *
 * <p>Version du 15/10 (référence Shinoda, v6) : <b>l'eau reste strictement celle du jeu</b>. Chaque pas se lit comme
 * un <b>impact sur l'eau</b> :
 * <ul>
 *   <li>une onde part du pied : petite au point d'impact, elle s'étale vite puis ralentit, son contour ondoie
 *       pendant l'expansion, et elle s'efface en fin de vie ; une seconde onde plus faible la suit 0,15 s plus tard.
 *       L'onde a l'allure d'un galet arrondi, convexe et irrégulier (0,8 à 1,3 bloc à la fin), trait fin et doux
 *       bleu-blanc pâle, très léger éclaircissement intérieur ; les ondes des pas successifs se chevauchent ;</li>
 *   <li>quelques <b>gouttelettes</b> (éclaboussures du jeu) jaillissent du pied et retombent, un peu plus au sprint,
 *       une petite gerbe à la retombée d'un saut ;</li>
 *   <li>un <b>bruit de pas dans l'eau</b> (pitch et volume variés), plus marqué au sprint et à la retombée.</li>
 * </ul>
 * Tout est posé par chaque client pour chaque marcheur visible : les autres joueurs voient et entendent les pas.
 * {@link EauRendu} dessine les ondes.
 *
 * <p><b>Tous les réglages sont les constantes ci-dessous</b> (distances en blocs, durées en secondes, opacités 0..1).
 */
final class Ondes {

    /* ======================================================================================= RÉGLAGES */

    /** Diamètre d'une flaque (blocs), tiré entre ces bornes ; rapport largeur / longueur du galet. */
    static final float DIAMETRE_MIN = 0.8f, DIAMETRE_MAX = 1.3f;
    static final float APLATI_MIN = 0.62f, APLATI_MAX = 0.90f;
    /** Irrégularité du contour (part du rayon), assez faible pour que le galet reste convexe. */
    static final float CABOSSE = 0.065f;
    /** Durée de vie (s) d'une onde à la marche et au sprint (interpolée selon l'allure). */
    static final float DUREE_MARCHE = 1.6f, DUREE_SPRINT = 1.7f;
    /** Seconde onde : retard (s), force (part de l'opacité) et taille (part de la première). */
    static final float ONDE2_RETARD = 0.15f, ONDE2_FORCE = 0.55f, ONDE2_TAILLE = 0.82f;
    /** Ondoiement du contour pendant l'expansion (part du rayon, s'éteint en fin de vie). */
    static final float ONDOIEMENT = 0.035f;
    /** Allures (blocs/s) de la marche et du sprint, pour l'interpolation. */
    static final double ALLURE_MARCHE = 3.0, ALLURE_SPRINT = 6.0;
    /** Retombée d'un saut : onde plus grande et plus longue. */
    static final float SAUT_TAILLE = 1.5f, SAUT_DUREE = 2.0f;
    /** Expansion : facteur d'échelle à l'impact → en fin de vie, très rapide au début puis freinée (ease-out). */
    static final float ECHELLE_DEBUT = 0.22f, ECHELLE_FIN = 1.15f;
    /** Part de la vie consacrée à l'essentiel de l'expansion (au-delà, l'onde ne grandit presque plus). */
    static final float EXPANSION = 0.55f;
    /** Trait : demi-épaisseur du flou (blocs) de part et d'autre du contour, opacité au centre du trait. */
    static final float FLOU = 0.045f;
    static final float OPACITE = 0.32f;
    /** Léger éclaircissement de l'intérieur de la flaque (opacité). */
    static final float INTERIEUR = 0.07f;
    /** Couleur : eau du biome mêlée de blanc dans cette proportion (bleu-blanc pâle, désaturé). */
    static final float BLANC = 0.68f;
    /** Pied gauche / droit : décalage latéral de la flaque (blocs). */
    static final double ECART_PIEDS = 0.12;
    /** Gouttelettes par pas (marche → sprint, tirées entre ces bornes) et à la retombée d'un saut. */
    static final int GOUTTES_MARCHE = 3, GOUTTES_SPRINT = 6, GOUTTES_SAUT = 12;
    /** Vitesse d'éjection des gouttelettes (blocs/tick) : horizontale, verticale. */
    static final double GOUTTES_ECART = 0.07, GOUTTES_HAUT = 0.16;
    /** Volume des bruits de pas dans l'eau : marche → sprint, retombée (un pas du jeu ≈ 0,15). */
    static final float SON_PAS = 0.10f, SON_PAS_COURSE = 0.18f, SON_SAUT = 0.30f;
    /** Marcheurs suivis autour de la caméra (blocs). */
    static final double PORTEE = 56.0;

    /* ================================================================================================ */

    /** Flaques en cours : tableaux parallèles préalloués (tampon circulaire), aucune allocation en jeu. */
    static final int CAPACITE = 128;
    static final double[] FX = new double[CAPACITE], FZ = new double[CAPACITE], FY = new double[CAPACITE];
    static final double[] NAISSANCE = new double[CAPACITE];
    static final float[] DEMI_A = new float[CAPACITE], DEMI_B = new float[CAPACITE], ANGLE = new float[CAPACITE];
    static final float[] PHASE1 = new float[CAPACITE], PHASE2 = new float[CAPACITE], VIE = new float[CAPACITE];
    static final float[] FORCE = new float[CAPACITE];
    static final int[] TEINTE = new int[CAPACITE];
    static final boolean[] ACTIVE = new boolean[CAPACITE];
    private static int tete;
    static int actives;

    /** Un marcheur suivi d'un tick à l'autre. */
    static final class Marcheur {
        double x, z;
        double vx, vz;            // vitesse lissée (blocs / tick)
        double distPas;
        boolean pied;
        int vu;
        boolean pose;             // posé sur l'eau au tick précédent

        double vitesse() { return Math.sqrt(vx * vx + vz * vz) * 20.0; }   // blocs / s
    }

    static final Map<Integer, Marcheur> MARCHEURS = new HashMap<>();
    private static int tick;

    private Ondes() {}

    static void vider() {
        MARCHEURS.clear();
        java.util.Arrays.fill(ACTIVE, false);
        actives = 0;
    }

    static boolean vide() { return actives == 0; }

    /** Temps de la simulation (s) : le temps du monde (s'arrête quand le jeu est en pause), interpolé. */
    static double temps(Minecraft mc, float partialTick) {
        return mc.level == null ? 0 : (mc.level.getGameTime() + partialTick) / 20.0;
    }

    /**
     * Surface de l'eau sous les pieds d'une entité qui s'y tient debout (pieds à la surface à quelques cm près, pas
     * dans l'eau), NaN sinon.
     */
    static double surfaceSous(ClientLevel level, Entity e) {
        if (e.isInWater() || e.isSwimming() || e.isPassenger() || e.isSpectator()) return Double.NaN;
        BlockPos b = BlockPos.containing(e.getX(), e.getY() - 0.07, e.getZ());
        FluidState f = level.getFluidState(b);
        if (!f.is(FluidTags.WATER) || level.getFluidState(b.above()).is(FluidTags.WATER)) return Double.NaN;
        double surf = b.getY() + f.getHeight(level, b);
        double d = e.getY() - surf;
        return d < -0.06 || d > 0.1 ? Double.NaN : surf;
    }

    static void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null || mc.player == null) { vider(); return; }
        tick++;
        double now = temps(mc, 0f);
        Vec3 cam = mc.gameRenderer.mainCamera().position();
        RandomSource rnd = level.getRandom();
        for (Entity e : level.entitiesForRendering()) {
            if (!(e instanceof Avatar) || e.isRemoved() || e.distanceToSqr(cam) > PORTEE * PORTEE) continue;
            double surf = surfaceSous(level, e);
            Marcheur m = MARCHEURS.get(e.getId());
            if (Double.isNaN(surf)) {
                if (m != null) m.pose = false;
                continue;
            }
            if (m == null) {
                m = new Marcheur();
                m.x = e.getX();
                m.z = e.getZ();
                MARCHEURS.put(e.getId(), m);
            }
            double dx = e.getX() - m.x, dz = e.getZ() - m.z;
            m.x = e.getX();
            m.z = e.getZ();
            if (dx * dx + dz * dz > 9) { m.vx = m.vz = 0; dx = dz = 0; }        // téléporté
            m.vx = m.vx * 0.55 + dx * 0.45;
            m.vz = m.vz * 0.55 + dz * 0.45;
            // posé sur l'eau après un saut ou une chute : onde plus grande, petite gerbe, bruit plus marqué
            double chute = e.yo - e.getY();
            if (!m.pose && chute > 0.15) {
                impact(level, m.x, surf, m.z, SAUT_TAILLE, SAUT_DUREE, now, rnd);
                gouttes(mc, m.x, surf, m.z, 0, 0, GOUTTES_SAUT, 1.4, rnd);
                level.playLocalSound(m.x, surf, m.z, SoundEvents.PLAYER_SPLASH, SoundSource.PLAYERS,
                        SON_SAUT * (0.85f + rnd.nextFloat() * 0.3f), 1.25f + rnd.nextFloat() * 0.25f, false);
            }
            m.pose = true;
            m.vu = tick;
            double v = m.vitesse();
            if (v > 0.6) {
                m.distPas += Math.sqrt(dx * dx + dz * dz);
                double pas = Math.max(0.75, Math.min(1.75, 0.55 + 0.17 * v));
                if (m.distPas >= pas) {
                    m.distPas -= pas;
                    pas(mc, m, surf, v, now, rnd);
                }
            } else {
                m.distPas = 0;
            }
        }
        MARCHEURS.values().removeIf(m -> tick - m.vu > 40);
        // flaques finies
        int n = 0;
        for (int i = 0; i < CAPACITE; i++) {
            if (!ACTIVE[i]) continue;
            if (now - NAISSANCE[i] > VIE[i]) ACTIVE[i] = false;
            else n++;
        }
        actives = n;
    }

    /** Un pas : une onde sous le pied (gauche, droit en alternance), des gouttelettes, un bruit de pas dans l'eau. */
    private static void pas(Minecraft mc, Marcheur m, double surf, double v, double now, RandomSource rnd) {
        double n = Math.max(1e-6, Math.sqrt(m.vx * m.vx + m.vz * m.vz));
        double fx = m.vx / n, fz = m.vz / n;
        m.pied = !m.pied;
        double cote = m.pied ? ECART_PIEDS : -ECART_PIEDS;
        double px = m.x - fz * cote, pz = m.z + fx * cote;
        // au sprint, les pas (donc les flaques) sont plus espacés et les flaques durent un peu plus
        double k = Math.max(0, Math.min(1, (v - ALLURE_MARCHE) / (ALLURE_SPRINT - ALLURE_MARCHE)));
        impact(mc.level, px, surf, pz, 1f, (float) (DUREE_MARCHE + (DUREE_SPRINT - DUREE_MARCHE) * k), now, rnd);
        int g = GOUTTES_MARCHE + (int) Math.round((GOUTTES_SPRINT - GOUTTES_MARCHE) * k) + rnd.nextInt(2);
        gouttes(mc, px, surf, pz, fx, fz, g, 1 + 0.4 * k, rnd);
        // bruit de pas dans l'eau : éclaboussure légère + clapotis, pitch et volume variés
        float vol = (float) (SON_PAS + (SON_PAS_COURSE - SON_PAS) * k) * (0.8f + rnd.nextFloat() * 0.4f);
        mc.level.playLocalSound(px, surf, pz, SoundEvents.GENERIC_SPLASH, SoundSource.PLAYERS,
                vol, 1.35f + rnd.nextFloat() * 0.45f, false);
        mc.level.playLocalSound(px, surf, pz, SoundEvents.PLAYER_SWIM, SoundSource.PLAYERS,
                vol * 0.6f, 1.5f + rnd.nextFloat() * 0.4f, false);
    }

    /** Un impact : la première onde, puis la seconde, plus faible, un peu plus petite et décalée dans le temps. */
    private static void impact(ClientLevel level, double x, double y, double z, float echelle, float duree,
                               double now, RandomSource rnd) {
        flaque(level, x, y, z, echelle, duree, 1f, now, rnd);
        flaque(level, x, y, z, echelle * ONDE2_TAILLE, duree * 0.9f, ONDE2_FORCE, now + ONDE2_RETARD, rnd);
    }

    /** Gouttelettes du jeu (éclaboussures) qui jaillissent du pied et retombent, un peu vers l'avant du pas. */
    private static void gouttes(Minecraft mc, double x, double y, double z, double fx, double fz, int n, double force,
                                RandomSource rnd) {
        if (mc.particleEngine == null) return;
        for (int k = 0; k < n; k++) {
            double a = rnd.nextDouble() * Math.PI * 2, r = rnd.nextDouble() * 0.18;
            double vx = (Math.cos(a) * GOUTTES_ECART + fx * 0.04) * force * (0.5 + rnd.nextDouble());
            double vz = (Math.sin(a) * GOUTTES_ECART + fz * 0.04) * force * (0.5 + rnd.nextDouble());
            double vy = GOUTTES_HAUT * force * (0.6 + rnd.nextDouble() * 0.6);
            mc.particleEngine.createParticle(net.minecraft.core.particles.ParticleTypes.SPLASH,
                    x + Math.cos(a) * r, y + 0.05, z + Math.sin(a) * r, vx, vy, vz);
        }
    }

    /** Pose une onde : galet tiré au hasard (taille, aplatissement, orientation, irrégularité). Teinte : l'eau du biome. */
    private static void flaque(ClientLevel level, double x, double y, double z, float echelle, float duree,
                               float force, double now, RandomSource rnd) {
        int i = tete;
        tete = (tete + 1) % CAPACITE;
        FX[i] = x; FY[i] = y; FZ[i] = z;
        NAISSANCE[i] = now;
        float d = echelle * (DIAMETRE_MIN + rnd.nextFloat() * (DIAMETRE_MAX - DIAMETRE_MIN));
        DEMI_A[i] = d / 2;
        DEMI_B[i] = d / 2 * (APLATI_MIN + rnd.nextFloat() * (APLATI_MAX - APLATI_MIN));
        ANGLE[i] = rnd.nextFloat() * 6.2832f;
        PHASE1[i] = rnd.nextFloat() * 6.2832f;
        PHASE2[i] = rnd.nextFloat() * 6.2832f;
        VIE[i] = duree;
        FORCE[i] = force;
        TEINTE[i] = net.minecraft.client.renderer.BiomeColors.getAverageWaterColor(level, BlockPos.containing(x, y - 0.1, z));
        if (!ACTIVE[i]) actives++;
        ACTIVE[i] = true;
    }
}
