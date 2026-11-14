package fr.reborn.hud.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.systems.CommandEncoder;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.textures.GpuTexture;
import com.llamalad7.mixinextras.sugar.Local;
import fr.reborn.hud.byakugan.ByakuganClient;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Byakugan (lot 5, étape B) : en 26.2, {@code renderLevel} vide la profondeur du monde juste avant la main (décalage 498),
 * donc bien avant le filtre ({@code PostChain.process}, dans {@code render}). Tant que <b>notre</b> filtre est posé, ce
 * vidage est sauté : le filtre lit la vraie profondeur (brume du lointain, lumière de perception du proche, ciel). Coût nul.
 * La main de la 1re personne se teste alors contre le monde (coupée seulement collée à un mur). Le KO, le mode photo et
 * tous les autres filtres gardent le vidage vanilla : la condition suit le filtre réellement posé, pas l'état du Byakugan.
 *
 * <p>Au même endroit, la projection du monde de l'image (balancement de la marche et nausée compris) est passée au filtre
 * avec l'état de la caméra ({@link ByakuganClient#vue}) : il replace chaque pixel dans le monde, pour la grille des blocs.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererByakuganDepthMixin {

    @WrapOperation(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
        at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/systems/CommandEncoder;clearDepthTexture(Lcom/mojang/blaze3d/textures/GpuTexture;D)V"))
    private void reborn$garderProfondeur(CommandEncoder encoder, GpuTexture depth, double value, Operation<Void> original) {
        GameRenderer self = (GameRenderer) (Object) this;
        if (ByakuganClient.FILTRE.equals(self.currentPostEffect())) return;
        original.call(encoder, depth, value);
    }

    @WrapOperation(method = "renderLevel(Lnet/minecraft/client/DeltaTracker;)V",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/ProjectionMatrixBuffer;getBuffer(Lorg/joml/Matrix4f;)Lcom/mojang/blaze3d/buffers/GpuBufferSlice;"))
    private GpuBufferSlice reborn$vueDuFiltre(ProjectionMatrixBuffer buffer, Matrix4f projection, Operation<GpuBufferSlice> original,
                                             @Local CameraRenderState cam) {
        ByakuganClient.vue(projection, cam);
        return original.call(buffer, projection);
    }
}
