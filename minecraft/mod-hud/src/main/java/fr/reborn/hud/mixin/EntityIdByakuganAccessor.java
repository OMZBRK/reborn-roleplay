package fr.reborn.hud.mixin;

import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Identifiant brut d'une entité, sans l'exception de {@code getId()} (26.2) pour une entité qui n'en a pas encore : l'entité
 * d'affichage d'une cage à monstres, recréée à chaque image, passe par le rendu avec un identifiant nul. Le Byakugan
 * (lot 5) lit ses ensembles d'identifiants par ici.
 */
@Mixin(Entity.class)
public interface EntityIdByakuganAccessor {

    @Accessor("id")
    int reborn$idBrut();
}
