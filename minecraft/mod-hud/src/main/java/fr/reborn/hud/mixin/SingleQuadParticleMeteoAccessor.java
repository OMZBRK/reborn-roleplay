package fr.reborn.hud.mixin;

import net.minecraft.client.particle.SingleQuadParticle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Météo : transparence des volutes de sable et de brume (fumée du jeu rendue très légère). */
@Mixin(SingleQuadParticle.class)
public interface SingleQuadParticleMeteoAccessor {

    @Accessor("alpha")
    void reborn$setAlpha(float alpha);
}
