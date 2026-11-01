package fr.reborn.hud.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Débattement maximal tête / corps de vanilla ({@code LivingEntity#getMaxHeadRotationRelativeToBody}, protégé) : 50°,
 * 15° quand un joueur pare au bouclier ({@code Player}). C'est la borne que les autres clients appliquent à notre
 * avatar ({@code tickHeadTurn}) ; {@code LocalPlayerBodyMixin} la reprend pour que la vue de soi en 3e personne
 * corresponde à ce que voient les autres.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityTeteAccessor {

    @Invoker("getMaxHeadRotationRelativeToBody")
    float reborn$debattementTeteMax();
}
