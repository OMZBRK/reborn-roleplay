package fr.reborn.hud.mixin;

import fr.reborn.hud.interaction.InteractionMode;
import net.minecraft.client.MouseHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.input.MouseButtonInfo;
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
@Mixin(MouseHandler.class)
public abstract class MouseInteractionMixin {

    // Position interne du curseur de MouseHandler — on la garde synchro même quand
    // on annule onMove, sinon la caméra "saute" à la fermeture du menu (gros
    // delta accumulé). 26.1 : les champs sont désormais xpos/ypos.
    @Shadow private double xpos;
    @Shadow private double ypos;

    @Unique private double reborn$lastX;
    @Unique private double reborn$lastY;
    @Unique private boolean reborn$has;

    @Inject(method = "onMove", at = @At("HEAD"), cancellable = true)
    private void reborn$onCursorPos(long window, double cx, double cy, CallbackInfo ci) {
        if (!InteractionMode.INSTANCE.isActive()) {
            reborn$has = false;
            return;
        }
        if (!reborn$has) {
            reborn$lastX = cx;
            reborn$lastY = cy;
            reborn$has = true;
        }
        double dx = cx - reborn$lastX;
        double dy = cy - reborn$lastY;
        reborn$lastX = cx;
        reborn$lastY = cy;
        double sf = Minecraft.getInstance().getWindow().getGuiScale();
        InteractionMode.INSTANCE.onMouseMove(dx, dy, sf);
        // Garde la position interne de MouseHandler à jour → pas de saut caméra ensuite.
        this.xpos = cx;
        this.ypos = cy;
        ci.cancel(); // caméra figée côté joueur
    }

    // 26.1 : MouseHandler#onButton(long window, MouseButtonInfo button, int action) ;
    // le n° de bouton et les mods sont portés par le record MouseButtonInfo.
    @Inject(method = "onButton", at = @At("HEAD"), cancellable = true)
    private void reborn$onMouseButton(long window, MouseButtonInfo button, int action, CallbackInfo ci) {
        if (!InteractionMode.INSTANCE.isActive()) return;
        if (button.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (action == GLFW.GLFW_PRESS) {
                InteractionMode.INSTANCE.onClick();
            }
            ci.cancel(); // pas d'attaque ; ne ferme pas le menu
        }
        // Clic droit & autres : laissés au jeu, ne ferment pas le menu.
    }
}
