package fr.reborn.hud.byakugan;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.LightCoordsUtil;

/**
 * Réseau de chakra (foyer au cœur, canaux, colonne, tenketsu) peint sur la silhouette des joueurs et des mobs
 * humanoïdes vus au Byakugan (modèles à disposition de skin joueur). Texture découpée (fond transparent), lignes orange
 * pur que le filtre négatif rend cyan lumineux ; décalage en profondeur ({@code entityCutoutZOffset}) pour se poser
 * sur la silhouette sans scintiller. C'est le modèle de l'entité lui-même : le réseau suit toutes les animations.
 * 4 images (impulsions qui partent du cœur).
 */
public final class ChakraNetworkLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {

    public ChakraNetworkLayer(RenderLayerParent<S, M> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, S state, float yRot, float xRot) {
        if (!(state instanceof ByakuganRenderState b) || !b.reborn$byakugan()) return;
        collector.order(1).submitModel(getParentModel(), state, poseStack, RenderTypes.entityCutoutZOffset(ByakuganClient.reseau()),
                LightCoordsUtil.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0, null);
    }
}
