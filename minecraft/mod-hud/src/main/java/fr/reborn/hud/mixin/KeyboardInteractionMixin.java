package fr.reborn.hud.mixin;

import fr.reborn.hud.interaction.InteractionMode;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Permet de fermer le menu d'interaction live avec Échap (au lieu d'ouvrir le
 * menu pause). Les autres touches (déplacement…) passent normalement.
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardInteractionMixin {

    // 26.1 : KeyboardHandler#keyPress(long window, int action, KeyEvent event) ;
    // la touche/scancode/mods sont portés par le record KeyEvent.
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void reborn$escCloseInteraction(long window, int action, KeyEvent event,
                                            CallbackInfo ci) {
        if (InteractionMode.INSTANCE.isActive()
                && event.key() == GLFW.GLFW_KEY_ESCAPE
                && action == GLFW.GLFW_PRESS) {
            InteractionMode.INSTANCE.deactivate();
            ci.cancel();
        }
    }
}
