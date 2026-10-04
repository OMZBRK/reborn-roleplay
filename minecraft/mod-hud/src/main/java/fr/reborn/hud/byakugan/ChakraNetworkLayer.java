package fr.reborn.hud.byakugan;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Réseau de chakra (foyer au cœur, canaux, colonne, tenketsu) dessiné à l'intérieur du corps fantôme des joueurs vus
 * au Byakugan. Même rendu que les yeux d'enderman ({@code RenderTypes.eyes}) : lumineux et additif (le noir de la
 * texture ne dessine rien). Le modèle est légèrement rétréci autour du centre du corps pour que le réseau paraisse
 * dedans et non posé sur la peau ; il suit toutes les animations puisque c'est le modèle du joueur lui-même.
 */
public final class ChakraNetworkLayer extends RenderLayer<AvatarRenderState, PlayerModel> {

    private static final float RETRAIT = 0.93f;
    private static final float CENTRE_Y = 0.75f;      // milieu du corps dans l'espace du modèle (en blocs)

    public ChakraNetworkLayer(RenderLayerParent<AvatarRenderState, PlayerModel> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, AvatarRenderState state, float yRot, float xRot) {
        if (!(state instanceof ByakuganRenderState b) || !b.reborn$byakugan()) return;
        poseStack.pushPose();
        poseStack.translate(0f, CENTRE_Y, 0f);
        poseStack.scale(RETRAIT, RETRAIT, RETRAIT);
        poseStack.translate(0f, -CENTRE_Y, 0f);
        collector.order(1).submitModel(getParentModel(), state, poseStack, RenderTypes.eyes(ByakuganClient.reseau()),
                light, OverlayTexture.NO_OVERLAY, 0, null);
        poseStack.popPose();
    }
}
