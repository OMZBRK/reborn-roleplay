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
 * Couche d'input pour le menu d'interaction live ({@link InteractionMode}) :
 * quand le mode est actif, la souris déplace un curseur (la caméra est gelée)
 * et les clics ciblent le menu — sans ouvrir d'écran, donc le joueur garde le
 * contrôle (déplacement, etc.).
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
        ci.cancel(); // empêche le mouvement caméra
    }

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void reborn$onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        if (!InteractionMode.INSTANCE.isActive()) return;
        if (action == GLFW.GLFW_PRESS) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
                InteractionMode.INSTANCE.onClick();
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                InteractionMode.INSTANCE.deactivate();
            }
        }
        // Avale tous les events souris pendant le mode (pas d'attaque/utilisation).
        ci.cancel();
    }
}
