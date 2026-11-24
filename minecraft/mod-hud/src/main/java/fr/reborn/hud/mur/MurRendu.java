package fr.reborn.hud.mur;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Avatar;
import org.joml.Quaternionf;

/**
 * Rendu du corps au mur (lot 5, essai C0), pour soi et pour les autres ({@code AvatarRendererMurMixin}) : corps entier
 * tourné, pieds vers la paroi, tête vers l'air, devant vers le sens de marche.
 *
 * <p>Le serveur tient le marcheur en pose de nage (cube de 0,6) : à l'extraction on retire tout ce que cette pose ferait au
 * rendu (rotation de nage, bras de nage, accroupi), puis à la pose des rotations on tourne le corps autour de son centre.
 * La rotation est posée <b>de part et d'autre</b> de celle du jeu, sans l'annuler : le lacet du corps, le tremblement du
 * gel et ce que d'autres mods y ajoutent restent en place.
 *
 * <p>Repère : après les rotations du jeu, le modèle a les pieds à l'origine, la tête vers +Y et le devant vers −Z. Avec
 * {@code k} l'avancement de la bascule, on veut {@code T(C) · RY(180 − lacet du mur) · RX(90° k) · RY(cap) · T(0, −0,9, 0)},
 * où {@code C = (0,9 − 0,6 k) haut} est le centre du corps vu de {@code P} : debout à {@code k = 0} (le rendu du jeu,
 * exactement), couché à {@code k = 1}, semelles sur la paroi, à 0,3 au-dessus de {@code P}. Le modèle (1,875 de long)
 * dépasse alors la boîte de 0,05 côté semelles et de 0,075 côté crâne : les 0,075 dont la tête dépasse déjà la boîte debout
 * du jeu. Aucune allocation par image : un seul quaternion, réutilisé.
 */
public final class MurRendu {

    private static final float DEMI_TOUR = (float) Math.PI, TOUR = (float) (Math.PI * 2), QUART = (float) (Math.PI / 2);
    /** Hauteur du centre du corps debout, autour duquel il bascule. */
    private static final float CENTRE = 0.9f;
    private static final Quaternionf Q = new Quaternionf();

    private MurRendu() {}

    /** Fin de {@code AvatarRenderer.extractRenderState} : l'état de rendu apprend si ce corps est au mur. */
    public static void extraire(Avatar e, AvatarRenderState etat, float pt) {
        MurRenderState m = (MurRenderState) etat;
        MurClient.Etat s = MurClient.actif ? MurClient.etat(e.getId()) : null;
        float k = s != null ? Mth.lerp(pt, s.kAvant, s.k) : 0f;
        if (k <= 0f) {
            m.reborn$mur(null, 0f, 0f);
            return;
        }
        m.reborn$mur(s.face, k, s.capAvant + court(s.cap - s.capAvant) * pt);
        // la pose de nage du serveur ne doit ni coucher le corps à plat ventre, ni sortir les bras de nage, ni accroupir
        etat.swimAmount = 0f;
        etat.isVisuallySwimming = false;
        etat.isCrouching = false;
        // la tête reste dans l'axe du corps (le regard du joueur suit la caméra, qui n'est pas couchée)
        etat.yRot *= 1f - k;
        etat.xRot *= 1f - k;
        // regard tourné vers l'extérieur (cadrage de paroi) : notre propre corps s'efface de notre écran
        if (s.local && MurCamera.efface()) {
            etat.isInvisible = true;
            etat.isInvisibleToPlayer = true;
            etat.isSpectator = true;
        }
    }

    /** Début de {@code AvatarRenderer.setupRotations} : le centre du corps, puis la bascule. */
    public static void avant(AvatarRenderState etat, PoseStack pile, float corps) {
        MurRenderState m = (MurRenderState) etat;
        MurFace f = m.reborn$murFace();
        if (f == null) return;
        float k = m.reborn$murBascule();
        // cap : du lacet que le corps avait debout (k = 0) au cap sur la paroi (k = 1)
        float debout = (f.lacet - corps) * Mth.DEG_TO_RAD;
        float cap = debout + court(m.reborn$murCap() - debout) * k;
        float jeu = (180f - corps) * Mth.DEG_TO_RAD;   // la rotation que le jeu pose juste après
        pile.translate(0f, CENTRE - 0.6f * k, 0f);
        pile.mulPose(Q.rotationY((180f - f.lacet) * Mth.DEG_TO_RAD).rotateX(QUART * k).rotateY(cap - jeu));
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
