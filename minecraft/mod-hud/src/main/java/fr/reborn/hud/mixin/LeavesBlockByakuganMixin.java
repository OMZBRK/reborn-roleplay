package fr.reborn.hud.mixin;

import fr.reborn.hud.byakugan.FeuillagePlein;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Byakugan (lot 5, étape B) : pendant la vision, pas de feuilles qui tombent (chacune écrit la profondeur et l'encre la
 * cernait : des points blancs qui volettent sous les houppiers). Les gouttes d'eau restent. Voir {@link FeuillagePlein}.
 */
@Mixin(LeavesBlock.class)
public abstract class LeavesBlockByakuganMixin {

    @Inject(method = "makeFallingLeavesParticles", at = @At("HEAD"), cancellable = true)
    private void reborn$pasDeFeuillesQuiTombent(Level level, BlockPos pos, RandomSource random, BlockState state, BlockPos below,
                                                CallbackInfo ci) {
        if (FeuillagePlein.actif()) ci.cancel();
    }
}
