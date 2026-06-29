package fr.reborn.hud.mixin;

import fr.reborn.hud.crosshair.CrosshairManager;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Détecte qu'un coup est porté à une entité côté client pour déclencher le
 * hit-marker du viseur Reborn ({@link CrosshairManager#onHit()}).
 */
@Mixin(ClientPlayerInteractionManager.class)
public abstract class CrosshairHitMixin {

    @Inject(method = "attackEntity", at = @At("HEAD"))
    private void reborn$onAttackEntity(PlayerEntity player, Entity target, CallbackInfo ci) {
        CrosshairManager.onHit();
    }
}
