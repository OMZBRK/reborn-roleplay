package fr.reborn.hud.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Part de nage d'une entité (0 debout, 1 à plat ventre), pour la marche sur les murs (lot 5, essai C0) : le serveur tient
 * le marcheur en pose de nage, le jeu ferait donc monter cette part vers 1. {@code MurClient} la garde à zéro tant que
 * l'entité est au mur : au lâcher, le corps ne passe pas par une demi-seconde de nage qui retombe.
 */
@Mixin(LivingEntity.class)
public interface LivingEntityMurAccessor {

    @Accessor("swimAmount")
    void reborn$nage(float valeur);

    @Accessor("swimAmountO")
    void reborn$nageAvant(float valeur);
}
