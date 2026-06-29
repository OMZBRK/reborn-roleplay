package fr.reborn.hud.mixin;

import fr.reborn.hud.interaction.InteractionMode;
import net.minecraft.client.Mouse;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Entrées du menu d'interaction live ({@link InteractionMode}). La vue et le
 * déplacement restent LIBRES — on n'intercepte que :
 * <ul>
 *   <li>la <b>molette</b> → navigue dans le menu (au lieu de changer de slot)</li>
 *   <li>le <b>clic gauche</b> → valide l'item sélectionné (pas d'attaque)</li>
 * </ul>
 * Le clic droit et le reste passent normalement (le menu ne se ferme PAS au
 * clic — seulement via Échap ou la touche bind).
 */
@Mixin(Mouse.class)
public abstract class MouseInteractionMixin {

    @Inject(method = "onMouseScroll", at = @At("HEAD"), cancellable = true)
    private void reborn$onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        if (!InteractionMode.INSTANCE.isActive() || vertical == 0) return;
        InteractionMode.INSTANCE.scroll(vertical > 0 ? -1 : 1);
        ci.cancel(); // n'affecte pas la hotbar
    }

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void reborn$onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        if (!InteractionMode.INSTANCE.isActive()) return;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (action == GLFW.GLFW_PRESS) {
                InteractionMode.INSTANCE.activateSelected();
            }
            ci.cancel(); // pas d'attaque pendant le menu
        }
        // Clic droit & autres : laissés au jeu (le menu ne se ferme pas au clic).
    }
}
