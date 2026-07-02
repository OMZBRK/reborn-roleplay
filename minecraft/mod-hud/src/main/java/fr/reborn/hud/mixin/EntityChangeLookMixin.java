package fr.reborn.hud.mixin;

import fr.reborn.hud.camera.RebornCamera;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Découplage souris ↔ perso pour la caméra épaule MMORPG.
 *
 * <p>Quand la caméra Reborn est active, la souris fait tourner la <b>caméra
 * en orbite</b> autour du joueur ({@link RebornCamera#rotateCamera}) au lieu
 * de tourner le personnage. On annule donc le {@code changeLookDirection}
 * vanilla pour le joueur local. Le perso, lui, s'oriente vers son déplacement
 * (cf {@code KeyboardInputMixin}).
 */
@Mixin(Entity.class)
public abstract class EntityChangeLookMixin {

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void reborn$routeMouseToCamera(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (RebornCamera.INSTANCE.isEnabled() && (Object) this == mc.player) {
            RebornCamera.INSTANCE.rotateCamera(cursorDeltaX, cursorDeltaY);
            ci.cancel();
        }
    }
}
