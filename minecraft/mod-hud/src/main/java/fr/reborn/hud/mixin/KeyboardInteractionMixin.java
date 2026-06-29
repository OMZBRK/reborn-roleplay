package fr.reborn.hud.mixin;

import fr.reborn.hud.interaction.InteractionMode;
import net.minecraft.client.Keyboard;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Permet de fermer le menu d'interaction live avec Échap (au lieu d'ouvrir le
 * menu pause). Les autres touches (déplacement…) passent normalement.
 */
@Mixin(Keyboard.class)
public abstract class KeyboardInteractionMixin {

    @Inject(method = "onKey", at = @At("HEAD"), cancellable = true)
    private void reborn$escCloseInteraction(long window, int key, int scancode, int action,
                                            int modifiers, CallbackInfo ci) {
        if (InteractionMode.INSTANCE.isActive()
                && key == GLFW.GLFW_KEY_ESCAPE
                && action == GLFW.GLFW_PRESS) {
            InteractionMode.INSTANCE.deactivate();
            ci.cancel();
        }
    }
}
