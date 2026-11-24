package fr.reborn.hud.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import fr.reborn.hud.mur.MurClient;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Boîte de collision couchée d'un joueur qui marche sur un mur (lot 5, essai C0), chez lui et chez ceux qui le voient :
 * le viseur d'un attaquant, F3 + B, les collisions du marcheur et le découpage du rendu lisent tous
 * {@code getBoundingBox()}, refaite ici à chaque {@code setPos}.
 *
 * <p>{@code Entity.makeBoundingBox(Vec3)} est la seule cible possible : ni {@code Avatar} ni {@code Player} ne la
 * redéfinissent (un mixin posé sur eux ne trouverait aucune méthode). La valeur rendue est remplacée
 * ({@code ModifyReturnValue}, sans {@code CallbackInfo}) : quand personne n'est au mur, le chemin de {@code setPos} de
 * <b>toutes</b> les entités ne paie qu'une lecture de booléen, sans allocation. {@link MurClient#boite} ne retient que les
 * avatars du monde client : le serveur intégré du solo garde les boîtes du jeu, comme le vrai serveur.
 */
@Mixin(Entity.class)
public abstract class EntityMurMixin {

    @ModifyReturnValue(method = "makeBoundingBox(Lnet/minecraft/world/phys/Vec3;)Lnet/minecraft/world/phys/AABB;",
                       at = @At("RETURN"))
    private AABB reborn$boiteAuMur(AABB origine, Vec3 position) {
        return MurClient.actif ? MurClient.boite((Entity) (Object) this, position, origine) : origine;
    }
}
