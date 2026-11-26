package fr.reborn.hud.corde;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.LivingEntityRenderLayerRegistrationCallback;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;

/**
 * <b>Corde de ligotage dessinée par le mod</b>, prototype client du 26/11 (aucun réseau) : la couche {@link CordeLayer}
 * sur tous les avatars (joueurs, soi en 3e personne, mannequins), la pose {@link CordePose} (posée par
 * {@code PlayerModelCordeMixin}), la liste {@link CordeLigotes} (vide en production, remplie par le banc
 * {@code SteleCordeDebug}, {@code REBORN_STELE_CORDE=corde}).
 */
public final class CordeClient {

    private CordeClient() {}

    public static void init() {
        ClientPlayConnectionEvents.DISCONNECT.register((handler, mc) -> CordeLigotes.vider());
        LivingEntityRenderLayerRegistrationCallback.EVENT.register((entityType, entityRenderer, helper, context) -> {
            if (entityRenderer instanceof AvatarRenderer<?>) {
                @SuppressWarnings("unchecked")
                RenderLayerParent<AvatarRenderState, PlayerModel> parent = (RenderLayerParent<AvatarRenderState, PlayerModel>) entityRenderer;
                helper.register(new CordeLayer(parent));
            }
        });
    }
}
