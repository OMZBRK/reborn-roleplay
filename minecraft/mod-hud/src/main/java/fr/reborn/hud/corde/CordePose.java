package fr.reborn.hud.corde;

import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/**
 * <b>Pose du ligoté</b> (prototype du 26/11) : bras ramenés le long du corps et un peu en arrière, mains dans le dos ;
 * en {@link CordeLigotes#COMPLET}, jambes droites et serrées. Posée par {@code PlayerModelCordeMixin} à la fin de
 * {@code PlayerModel.setupAnim}, après la marche vanilla et après PlayerAnimationLib (priorité de mixin 2500, au-dessus
 * des 2001 de son {@code PlayerModelMixin}) :
 * <ul>
 *   <li>en {@link CordeLigotes#HAUT}, seuls les bras sont écrits : les jambes gardent la marche (vanilla ou démarche
 *       Reborn), la pose tient donc quand le corps marche ;</li>
 *   <li>un calque PlayerAnimationLib (comme la méditation) a été essayé d'abord : au format GeckoLib, il remet aussi à
 *       zéro les membres qu'il n'anime pas : les jambes d'un « haut seul » restaient raides en marchant, alors que le
 *       témoin sans calque ({@code v3_corde_marche_temoin}) marchait (premières passes du banc, 26/11).</li>
 * </ul>
 * La corde ({@link CordeLayer}) ne suppose rien de ces angles : elle relit les membres posés à chaque image. Les manches
 * et le pantalon du skin sont des enfants des bras et des jambes : ils suivent. Aucune allocation.
 */
public final class CordePose {

    /** Bras en arrière (x positif = main vers le dos) et rentrés vers le corps, en radians. */
    static final float BRAS_X = (float) Math.toRadians(20.0), BRAS_Z = (float) Math.toRadians(6.0);

    private CordePose() {}

    /** Pose l'avatar s'il est ligoté (un entier lu si la liste est vide). */
    public static void appliquer(PlayerModel m, AvatarRenderState state) {
        if (CordeLigotes.n == 0) return;
        byte v = CordeLigotes.variante(state.id);
        if (v == CordeLigotes.AUCUNE) return;
        // le bras droit est du côté x négatif du modèle : z négatif rentre sa main vers le corps
        m.rightArm.xRot = BRAS_X;
        m.rightArm.yRot = 0f;
        m.rightArm.zRot = -BRAS_Z;
        m.leftArm.xRot = BRAS_X;
        m.leftArm.yRot = 0f;
        m.leftArm.zRot = BRAS_Z;
        if (v != CordeLigotes.COMPLET) return;
        m.rightLeg.xRot = 0f;
        m.rightLeg.yRot = 0f;
        m.rightLeg.zRot = 0f;
        m.leftLeg.xRot = 0f;
        m.leftLeg.yRot = 0f;
        m.leftLeg.zRot = 0f;
    }
}
