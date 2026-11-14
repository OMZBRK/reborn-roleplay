package fr.reborn.hud.mixin;

import fr.reborn.hud.byakugan.ByakuganClient;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Byakugan (lot 5, étape B) : une personne vue derrière un mur « brille » pour le jeu, chez le porteur seulement. Le jeu
 * pose alors la passe de contour (le liseré cyan, couleur-clé posée par {@code LivingEntityRendererByakuganMixin}). Un mod
 * d'élagage (EntityCulling) qui écarterait l'entité derrière le mur n'est pas traité ici : à vérifier avec le pack (non testé).
 */
@Mixin(Minecraft.class)
public abstract class MinecraftByakuganGlowMixin {

    @Inject(method = "shouldEntityAppearGlowing(Lnet/minecraft/world/entity/Entity;)Z", at = @At("HEAD"), cancellable = true)
    private void reborn$byakuganContour(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (ByakuganClient.contour(entity)) cir.setReturnValue(true);
    }
}
