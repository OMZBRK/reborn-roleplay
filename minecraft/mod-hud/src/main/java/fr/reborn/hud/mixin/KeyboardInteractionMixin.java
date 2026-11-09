package fr.reborn.hud.mixin;

import fr.reborn.hud.interaction.InteractionMode;
import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Permet de fermer le menu d'interaction live avec Échap (au lieu d'ouvrir le
 * menu pause). Les autres touches (déplacement…) passent normalement. Écarte aussi d'un écran la répétition d'une touche
 * tenue depuis avant son ouverture (Y maintenue et « /me » du menu : {@code ModeConcentration.evenementClavier}).
 */
@Mixin(KeyboardHandler.class)
public abstract class KeyboardInteractionMixin {

    // 26.1 : KeyboardHandler#keyPress(long window, int action, KeyEvent event) ;
    // la touche/scancode/mods sont portés par le record KeyEvent.
    @Inject(method = "keyPress", at = @At("HEAD"), cancellable = true)
    private void reborn$escCloseInteraction(long window, int action, KeyEvent event,
                                            CallbackInfo ci) {
        if (fr.reborn.hud.concentration.ModeConcentration.evenementClavier(action, event)) {
            ci.cancel();
            return;
        }
        if (InteractionMode.INSTANCE.isCapturing()
                && event.key() == GLFW.GLFW_KEY_ESCAPE
                && action == GLFW.GLFW_PRESS) {
            // Concentration : sortie propre (effet, contour, rappels) ; sinon le menu seul se ferme
            if (fr.reborn.hud.concentration.ModeConcentration.actif()) {
                fr.reborn.hud.concentration.ModeConcentration.sortir(fr.reborn.hud.concentration.ModeConcentration.Raison.ECHAP);
            } else {
                InteractionMode.INSTANCE.deactivate();
            }
            ci.cancel();
        }
    }

    @Inject(method = "charTyped", at = @At("HEAD"), cancellable = true)
    private void reborn$repetitionTenue(long window, CharacterEvent event, CallbackInfo ci) {
        if (fr.reborn.hud.concentration.ModeConcentration.caractereRepete()) ci.cancel();
    }
}
