package fr.reborn.hud.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import fr.reborn.hud.byakugan.FeuillagePlein;
import net.minecraft.client.renderer.chunk.SectionCompiler;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Byakugan (lot 5, étape B) : pendant la vision, les plantes basses ne sont pas maillées (forme de rendu « invisible »
 * pour le compilateur des chunks seulement : le fluide d'un bloc noyé, la collision, tout le reste du jeu ne changent pas),
 * voir {@link FeuillagePlein}. Appelé par les fils de compilation des chunks.
 */
@Mixin(SectionCompiler.class)
public abstract class SectionCompilerByakuganMixin {

    @WrapOperation(method = "compile",
        at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/block/state/BlockState;getRenderShape()Lnet/minecraft/world/level/block/RenderShape;"))
    private RenderShape reborn$plantesCachees(BlockState state, Operation<RenderShape> original) {
        return FeuillagePlein.plante(state) ? RenderShape.INVISIBLE : original.call(state);
    }
}
