package fr.reborn.hud.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Accès à ce qui compose la projection du monde dans {@code GameRenderer#renderLevel} (privé en 26.2), pour que
 * {@link fr.reborn.hud.anchor.ProjectionMonde} projette les ancres du HUD exactement comme le monde : balancements
 * (coup reçu, marche) et spirale de nausée / portail.
 */
@Mixin(GameRenderer.class)
public interface GameRendererTeteAccessor {

    /** Inclinaison du coup reçu (et chute de la mort) ; ne lit que l'état caméra et les options déjà extraits. */
    @Invoker("bobHurt")
    void reborn$bobHurt(CameraRenderState camera, PoseStack poseStack);

    /** Balancement de la marche (option « Balancement de la vue ») ; ne lit que l'état caméra. */
    @Invoker("bobView")
    void reborn$bobView(CameraRenderState camera, PoseStack poseStack);

    @Accessor("spinningEffectTime")
    float reborn$spiraleTemps();

    @Accessor("spinningEffectSpeed")
    float reborn$spiraleVitesse();
}
