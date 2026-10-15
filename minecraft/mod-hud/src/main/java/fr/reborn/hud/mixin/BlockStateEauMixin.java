package fr.reborn.hud.mixin;

import fr.reborn.hud.eau.MarcheEauClient;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Marche sur l'eau : pour le joueur local qui en a le droit ({@link MarcheEauClient#solide}), la surface de l'eau (eau,
 * et plantes aquatiques gorgées d'eau qui affleurent) a une forme de collision — on tient debout dessus. Une seule
 * lecture de booléen quand la marche sur l'eau n'est pas active.
 */
@Mixin(BlockBehaviour.BlockStateBase.class)
public abstract class BlockStateEauMixin {

    @Inject(method = "getCollisionShape(Lnet/minecraft/world/level/BlockGetter;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/phys/shapes/CollisionContext;)Lnet/minecraft/world/phys/shapes/VoxelShape;",
            at = @At("RETURN"), cancellable = true)
    private void reborn$surfaceEau(BlockGetter level, BlockPos pos, CollisionContext ctx, CallbackInfoReturnable<VoxelShape> cir) {
        if (!MarcheEauClient.solide) return;
        VoxelShape origine = cir.getReturnValue();
        if (origine != null && !origine.isEmpty()) return;
        VoxelShape s = MarcheEauClient.forme((BlockState) (Object) this, level, pos, ctx);
        if (s != null) cir.setReturnValue(s);
    }
}
