package fr.reborn.hud.byakugan;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;

/**
 * Signe du Byakugan vu par les autres (lot 5, étape B) : de près ({@code dojutsu.byakugan.signe-rayon}, 12 blocs), les
 * veines des tempes d'un porteur se gonflent. Quelques pixels au gabarit du skin (tempes et côtés de la tête, jamais sur
 * les yeux, dont la place change d'un skin à l'autre) ; un seul modèle de plus par porteur proche, rien sinon. Le serveur
 * envoie les porteurs (identifiants d'entités), jamais un nom.
 */
public final class ByakuganSigneLayer<S extends LivingEntityRenderState, M extends EntityModel<? super S>> extends RenderLayer<S, M> {

    public ByakuganSigneLayer(RenderLayerParent<S, M> parent) {
        super(parent);
    }

    @Override
    public void submit(PoseStack poseStack, SubmitNodeCollector collector, int light, S state, float yRot, float xRot) {
        if (!(state instanceof ByakuganRenderState b) || !b.reborn$signe() || b.reborn$byakugan()) return;
        collector.order(1).submitModel(getParentModel(), state, poseStack, RenderTypes.entityCutoutZOffset(ByakuganClient.SIGNE),
            light, OverlayTexture.NO_OVERLAY, 0, null);
    }
}
