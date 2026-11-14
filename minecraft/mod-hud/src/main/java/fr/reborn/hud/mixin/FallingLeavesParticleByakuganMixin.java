package fr.reborn.hud.mixin;

import fr.reborn.hud.byakugan.FeuillagePlein;
import net.minecraft.client.particle.FallingLeavesParticle;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Byakugan (lot 5, étape B, 6e tour) : une feuille qui tombait déjà à l'ouverture de la vision disparaît au tick suivant
 * (l'encre cernait chacune : un losange blanc qui volette). Les nouvelles ne naissent pas ({@link LeavesBlockByakuganMixin}).
 */
@Mixin(FallingLeavesParticle.class)
public abstract class FallingLeavesParticleByakuganMixin {

    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void reborn$retireeSousLeByakugan(CallbackInfo ci) {
        if (FeuillagePlein.actif()) {
            ((Particle) (Object) this).remove();
            ci.cancel();
        }
    }
}
