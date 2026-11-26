package fr.reborn.hud.mur;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import org.joml.Quaternionf;

import java.util.function.DoubleConsumer;

/**
 * Rendu du corps au mur (lot 5, essai C0 puis étape C1a), pour soi et pour les autres ({@code AvatarRendererMurMixin}) :
 * corps entier tourné, pieds vers la paroi, tête vers l'air, devant vers le sens de marche, tête tournée vers le regard.
 * Pendant un virage de coin, le lacet de la paroi passe d'une face à l'autre ({@link MurClient}, au plus 20° par tick) :
 * le corps tourne autour de l'arête, chez soi comme chez les voisins.
 *
 * <p>Le serveur tient le marcheur en pose de nage (cube de 0,6) : à l'extraction on retire tout ce que cette pose ferait au
 * rendu (rotation de nage, bras de nage, accroupi), puis à la pose des rotations on tourne le corps autour de son centre.
 * La rotation est posée <b>de part et d'autre</b> de celle du jeu, sans l'annuler : le lacet du corps, le tremblement du
 * gel et ce que d'autres mods y ajoutent restent en place.
 *
 * <p>Repère : après les rotations du jeu, le modèle a les pieds à l'origine, la tête vers +Y et le devant vers −Z. Avec
 * {@code k} l'avancement de la bascule, on veut {@code T(C) · RY(180 − lacet du mur) · RX(90° k) · RY(cap) · T(0, −0,9, 0)}
 * (lacet du mur : celui de la face, ou l'angle interpolé d'un virage),
 * où {@code C = (0,9 − 0,6 k) haut} est le centre du corps vu de {@code P} : debout à {@code k = 0} (le rendu du jeu,
 * exactement), couché à {@code k = 1}, semelles sur la paroi, à 0,3 au-dessus de {@code P}. Le modèle (1,875 de long)
 * dépasse alors la boîte de 0,05 côté semelles et de 0,075 côté crâne : les 0,075 dont la tête dépasse déjà la boîte debout
 * du jeu. Aucune allocation par image : un seul quaternion, réutilisé.
 */
public final class MurRendu {

    private static final float DEMI_TOUR = (float) Math.PI, TOUR = (float) (Math.PI * 2), QUART = (float) (Math.PI / 2);
    /** Hauteur du centre du corps debout, autour duquel il bascule. */
    private static final float CENTRE = 0.9f;
    /** Débattement de la tête au mur, en degrés : de côté et en hauteur. */
    private static final float TETE_COTE = 80f, TETE_HAUT = 75f;
    private static final Quaternionf Q = new Quaternionf();

    /**
     * Banc ({@code audit/MurSpike}) : reçoit, à chaque pose du corps local pendant une sortie animée, le cap rendu sur la
     * paroi (degrés, non ramenés). Null en jeu.
     */
    public static volatile DoubleConsumer debugCapSortie;

    private MurRendu() {}

    /** Fin de {@code AvatarRenderer.extractRenderState} : l'état de rendu apprend si ce corps est au mur. */
    public static void extraire(Avatar e, AvatarRenderState etat, float pt) {
        MurRenderState m = (MurRenderState) etat;
        MurClient.Etat s = MurClient.actif ? MurClient.etat(e.getId()) : null;
        float k = s != null ? Mth.lerp(pt, s.kAvant, s.k) : 0f;
        if (k <= 0f) {
            m.reborn$mur(null, 0f, 0f, 0f, Float.NaN);
            return;
        }
        float cap = s.capAvant + court(s.cap - s.capAvant) * pt;
        float lacet = s.lacetAvant + court((s.lacet - s.lacetAvant) * Mth.DEG_TO_RAD) * Mth.RAD_TO_DEG * pt;
        m.reborn$mur(s.face, k, cap, lacet, s.debout);
        // la pose de nage du serveur ne doit ni coucher le corps à plat ventre, ni sortir les bras de nage, ni accroupir
        etat.swimAmount = 0f;
        etat.isVisuallySwimming = false;
        etat.isCrouching = false;
        tete(e, etat, lacet, k, cap, pt);
    }

    /**
     * La tête suit le <b>regard réel</b> (celui que le serveur connaît : la visée dans le monde), lu dans le repère du
     * corps couché : devant = le cap sur la paroi, dessus = la normale. Bornée à ± {@value #TETE_COTE}° de côté et
     * ± {@value #TETE_HAUT}° en hauteur ; un regard tourné vers l'arrière ramène la tête dans l'axe au lieu de la faire
     * claquer d'une épaule à l'autre. Fondue avec la tête du jeu pendant la bascule. {@code mur} : lacet de la paroi
     * (degrés), dont on tire sa normale {@code n = (sin, −cos)} et sa tangente {@code t = (−cos, −sin)}.
     */
    private static void tete(Avatar e, AvatarRenderState etat, float mur, float k, float cap, float pt) {
        float lacet = e.getViewYRot(pt) * Mth.DEG_TO_RAD, tangage = e.getViewXRot(pt) * Mth.DEG_TO_RAD;
        float c = Mth.cos(tangage);
        float rx = -Mth.sin(lacet) * c, ry = -Mth.sin(tangage), rz = Mth.cos(lacet) * c;
        float sm = Mth.sin(mur * Mth.DEG_TO_RAD), cm = Mth.cos(mur * Mth.DEG_TO_RAD);
        // sur la paroi : part le long de la tangente, part vers le haut du monde, part le long de la normale
        float rt = -rx * cm - rz * sm, rn = rx * sm - rz * cm;
        float sc = Mth.sin(cap), cc = Mth.cos(cap);
        // devant du corps = −sin(cap) t + cos(cap) haut ; sa droite = devant × n = cos(cap) t + sin(cap) haut
        float devant = -sc * rt + cc * ry, droite = cc * rt + sc * ry;
        float cote = devant * devant + droite * droite > 1.0e-6f ? (float) Math.atan2(droite, devant) * Mth.RAD_TO_DEG : 0f;
        float derriere = Math.abs(cote) - (180f - TETE_COTE);
        cote = Mth.clamp(cote, -TETE_COTE, TETE_COTE);
        if (derriere > 0f) cote *= 1f - derriere / TETE_COTE;
        float haut = Mth.clamp((float) -Math.asin(Mth.clamp(rn, -1f, 1f)) * Mth.RAD_TO_DEG, -TETE_HAUT, TETE_HAUT);
        etat.yRot = Mth.lerp(k, etat.yRot, cote);
        etat.xRot = Mth.lerp(k, etat.xRot, haut);
    }

    /** Début de {@code AvatarRenderer.setupRotations} : le centre du corps, puis la bascule. */
    public static void avant(AvatarRenderState etat, PoseStack pile, float corps) {
        MurRenderState m = (MurRenderState) etat;
        MurFace f = m.reborn$murFace();
        if (f == null) return;
        float k = m.reborn$murBascule(), mur = m.reborn$murLacet();
        // cap : du lacet que le corps a debout (k = 0) au cap sur la paroi (k = 1) ; pendant une sortie animée, le lacet
        // debout est celui qu'elle vise, pas le yBodyRot du jeu (LocalPlayerBodyMixin le remet sur le regard à chaque tick)
        float vise = m.reborn$murDebout();
        float debout = (mur - (Float.isNaN(vise) ? corps : vise)) * Mth.DEG_TO_RAD;
        float cap = debout + court(m.reborn$murCap() - debout) * k;
        if (!Float.isNaN(vise) && debugCapSortie != null) debugCapSortie.accept(cap * Mth.RAD_TO_DEG);
        float jeu = (180f - corps) * Mth.DEG_TO_RAD;   // la rotation que le jeu pose juste après, avec le vrai lacet du corps
        pile.translate(0f, CENTRE - 0.6f * k, 0f);
        pile.mulPose(Q.rotationY((180f - mur) * Mth.DEG_TO_RAD).rotateX(QUART * k).rotateY(cap - jeu));
    }

    /** Fin de {@code AvatarRenderer.setupRotations} : les pieds repartent du centre. */
    public static void apres(AvatarRenderState etat, PoseStack pile) {
        if (((MurRenderState) etat).reborn$murFace() != null) pile.translate(0f, -CENTRE, 0f);
    }

    /** Écart d'angle ramené entre −π et π. */
    private static float court(float d) {
        d %= TOUR;
        if (d > DEMI_TOUR) d -= TOUR;
        else if (d < -DEMI_TOUR) d += TOUR;
        return d;
    }
}
