package fr.reborn.hud.mixin;

import fr.reborn.hud.immersion.PhotoMode;
import fr.reborn.hud.interaction.InteractionMode;
import net.minecraft.client.Mouse;
import net.minecraft.client.MinecraftClient;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
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

    // Position interne du curseur de Mouse — on la garde synchro même quand on
    // annule onCursorPos, sinon la caméra "saute" à la fermeture du menu (gros
    // delta accumulé).
    @Shadow private double x;
    @Shadow private double y;

    @Unique private double reborn$lastX;
    @Unique private double reborn$lastY;
    @Unique private boolean reborn$has;

    @Inject(method = "onCursorPos", at = @At("HEAD"), cancellable = true)
    private void reborn$onCursorPos(long window, double cx, double cy, CallbackInfo ci) {
        boolean photo = PhotoMode.INSTANCE.isActive();
        boolean interact = InteractionMode.INSTANCE.isActive();
        if (!photo && !interact) { reborn$has = false; return; }
        if (!reborn$has) {
            reborn$lastX = cx;
            reborn$lastY = cy;
            reborn$has = true;
        }
        double dx = cx - reborn$lastX;
        double dy = cy - reborn$lastY;
        reborn$lastX = cx;
        reborn$lastY = cy;
        if (photo) {
            // Mode photo : la souris pilote la caméra libre.
            PhotoMode.INSTANCE.rotate(dx, dy);
        } else {
            double sf = MinecraftClient.getInstance().getWindow().getScaleFactor();
            InteractionMode.INSTANCE.onMouseMove(dx, dy, sf);
        }
        // Garde la position interne de Mouse à jour → pas de saut caméra ensuite.
        this.x = cx;
        this.y = cy;
        ci.cancel(); // caméra figée côté joueur
    }

    @Inject(method = "onMouseButton", at = @At("HEAD"), cancellable = true)
    private void reborn$onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        if (PhotoMode.INSTANCE.isActive()) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && action == GLFW.GLFW_PRESS) {
                PhotoMode.INSTANCE.requestCapture();
            }
            ci.cancel(); // aucune interaction monde en mode photo
            return;
        }
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
