package fr.reborn.hud.mixin;

import fr.reborn.hud.crosshair.CrosshairManager;
import net.minecraft.client.multiplayer.ClientPlayerInteractionManager;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
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
    private void reborn$onAttackEntity(Player player, Entity target, CallbackInfo ci) {
        CrosshairManager.onHit();
    }
}
