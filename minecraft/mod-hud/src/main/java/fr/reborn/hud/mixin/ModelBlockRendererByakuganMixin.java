package fr.reborn.hud.mixin;

import fr.reborn.hud.byakugan.FeuillagePlein;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Byakugan (lot 5, étape B) : pendant la vision, les feuilles se compilent pleines, comme avec « Feuilles découpées : non »,
 * sans toucher l'option du joueur ({@link FeuillagePlein}). Appelé par les fils de compilation des chunks.
 */
@Mixin(ModelBlockRenderer.class)
public abstract class ModelBlockRendererByakuganMixin {

    @Inject(method = "forceOpaque(ZLnet/minecraft/world/level/block/state/BlockState;)Z", at = @At("HEAD"), cancellable = true)
    private static void reborn$feuillesPleines(boolean cutoutLeaves, BlockState state, CallbackInfoReturnable<Boolean> cir) {
        if (FeuillagePlein.feuillePleine(state)) cir.setReturnValue(true);
    }
}
