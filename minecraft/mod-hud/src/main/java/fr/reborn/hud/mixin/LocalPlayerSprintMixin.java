package fr.reborn.hud.mixin;

import fr.reborn.hud.notif.NotifClient;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Sacoche trop lourde (palier « Chargé » et au-delà, état poussé par ShinobiCore sur {@code reborn:notif}) : le joueur
 * local ne peut plus <b>commencer</b> à sprinter, et un sprint en cours s'arrête. Le serveur annule de son côté le
 * bonus de vitesse du sprint (modificateur d'attribut) ; ceci évite l'animation et l'aller-retour.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerSprintMixin {

    @Inject(method = "canStartSprinting", at = @At("HEAD"), cancellable = true)
    private void reborn$sacocheTropLourde(CallbackInfoReturnable<Boolean> cir) {
        if (NotifClient.sprintBloque()) cir.setReturnValue(false);
    }

    @Inject(method = "shouldStopRunSprinting", at = @At("HEAD"), cancellable = true)
    private void reborn$arretSprint(CallbackInfoReturnable<Boolean> cir) {
        if (NotifClient.sprintBloque()) cir.setReturnValue(true);
    }
}
