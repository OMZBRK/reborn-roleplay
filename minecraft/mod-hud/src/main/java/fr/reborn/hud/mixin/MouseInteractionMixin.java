package fr.reborn.hud.mixin;

import fr.reborn.hud.interaction.InteractionMode;
import net.minecraft.client.Mouse;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Entrées du menu d'interaction live ({@link InteractionMode}). Quand le mode
 * est actif : la souris déplace le <b>curseur</b> et la caméra est <b>figée</b>
 * (elle ne tourne plus). Le <b>clic gauche</b> valide l'item survolé. Le
 * déplacement (touches) reste libre. Fermeture via Échap ou la touche bind.
 */
@Mixin(Mouse.class)
public abstract class MouseInteractionMixin {

    @Unique private double reborn$lastX;
    @Unique private double reborn$lastY;
    @Unique private boolean reborn$has;

    @Inject(method = "onCursorPos", at = @At("HEAD"), cancellable = true)
    private void reborn$onCursorPos(long window, double x, double y, CallbackInfo ci) {
        if (!InteractionMode.INSTANCE.isActive()) {
            reborn$has = false;
            return;
        }
        if (!reborn$has) {
            reborn$lastX = x;
            reborn$lastY = y;
            reborn$has = true;
        }
        double dx = x - reborn$lastX;
        double dy = y - reborn$lastY;
        reborn$lastX = x;
        reborn$lastY = y;
        double sf = MinecraftClient.getInstance().getWindow().getScaleFactor();
        InteractionMode.INSTANCE.onMouseMove(dx, dy, sf);
        ci.cancel(); // caméra figée
    }

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void reborn$onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        if (!InteractionMode.INSTANCE.isActive()) return;
        if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (action == GLFW.GLFW_PRESS) {
                InteractionMode.INSTANCE.onClick();
            }
            ci.cancel(); // pas d'attaque ; ne ferme pas le menu
        }
        // Clic droit & autres : laissés au jeu, ne ferment pas le menu.
    }
}
