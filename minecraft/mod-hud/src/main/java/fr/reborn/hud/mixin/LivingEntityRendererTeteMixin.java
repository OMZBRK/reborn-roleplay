package fr.reborn.hud.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.reborn.hud.anchor.TeteAncre;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Capture de la tête rendue pour {@link TeteAncre} (31/10). Point d'injection : juste <b>après</b> l'unique appel
 * {@code EntityModel#setupAnim(Object)} de {@code LivingEntityRenderer#submit} (offset 245 en 26.2) — le modèle
 * partagé est alors dans la pose de <i>cette</i> entité (tête de PlayerAnimationLib posée au RETURN de
 * {@code PlayerModel#setupAnim}, os « body » déjà dans la pile via {@code doTranslations}), et la pile est la pose
 * racine du modèle, relative à la caméra, en axes monde. Lire le modèle plus tôt (au {@code submitModel}) donnerait la
 * pose de l'entité précédente. Aucun autre mod du modpack ne vise cet appel (ETF, Sodium Extra, PAL : ailleurs).
 *
 * <p>Filtres : seulement les {@code AvatarRenderState} d'id > 0 (EntityCulling fabrique des faux états d'id 0), seulement
 * la passe du monde ({@link TeteAncre#passeMonde}). {@code setupAnim} n'est appelé là que si le joueur n'est pas
 * spectateur ({@code shouldRenderLayers}) : un spectateur n'est jamais capté (repli estimé).
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererTeteMixin {

    @Inject(method = "submit(Lnet/minecraft/client/renderer/entity/state/LivingEntityRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;setupAnim(Ljava/lang/Object;)V",
                     shift = At.Shift.AFTER))
    private void reborn$capturerTete(LivingEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                                     CameraRenderState camera, CallbackInfo ci) {
        if (!(state instanceof AvatarRenderState a) || a.id <= 0 || !TeteAncre.passeMonde(camera)) return;
        Object model = ((LivingEntityRenderer<?, ?, ?>) (Object) this).getModel();
        if (model instanceof HumanoidModel<?> h) TeteAncre.capturer(a, camera, poseStack.last().pose(), h.root(), h.head);
    }
}
