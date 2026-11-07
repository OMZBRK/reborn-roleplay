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
 * Entrées du menu d'interaction live ({@link InteractionMode}), moteur de la Concentration. Quand le mode
 * est actif : la souris déplace le <b>curseur</b> et la caméra est <b>figée</b>
 * (elle ne tourne plus, sauf clic droit maintenu en concentration). Le <b>clic gauche</b> valide l'item survolé. Le
 * déplacement (touches) reste libre. Fermeture via Échap ou la touche Concentration.
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
        if (!InteractionMode.INSTANCE.isCapturing()) {
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
        // Concentration, clic droit maintenu : la caméra tourne (le jeu reçoit le mouvement), le curseur reste en place.
        // La position interne de MouseHandler (xpos / ypos) est restée à jour : pas de saut.
        if (fr.reborn.hud.concentration.ModeConcentration.rotation()) return;
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
        // EN PARADE ON NE TAPE PAS : tant que la garde (touche C) est maintenue en jeu,
        // on annule le clic gauche (attaque). Le serveur refuse aussi le coup, mais on
        // coupe ici le swing/anim pour un feel propre. Uniquement hors écran (sinon on
        // casserait les clics de menu).
        if (button.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && Minecraft.getInstance().gui.screen() == null
                && fr.reborn.hud.combat.CombatInput.INSTANCE.isBlocking()) {
            ci.cancel();
            return;
        }
        // ÉTAT DU SERVEUR : FRAPPER refusé (douleur, à terre, étourdi, porté, entravé…) — pas de coup dans le vide sur
        // une cible visée. Seulement une ENTITÉ visée : casser un bloc, cliquer dans le vide restent libres.
        // Curseur actif (concentration) : le clic gauche ouvre un menu, ce n'est pas un coup ; le viseur central
        // (hitResult) n'a rien à voir avec le curseur, ce refus ne doit pas avaler le clic.
        if (button.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT
                && !InteractionMode.INSTANCE.isCapturing()
                && Minecraft.getInstance().gui.screen() == null
                && fr.reborn.hud.etat.EtatClient.enJeu() && fr.reborn.hud.etat.EtatClient.refuse("fra")
                && Minecraft.getInstance().hitResult instanceof net.minecraft.world.phys.EntityHitResult) {
            ci.cancel();
            return;
        }
        if (!InteractionMode.INSTANCE.isCapturing()) return;
        // Les RELÂCHEMENTS passent toujours au jeu : un bouton enfoncé avant l'entrée (manger, poser, miner) doit être
        // relâché côté vanilla, sinon la touche reste « enfoncée » et l'action continue en boucle sur le viseur central.
        if (button.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            if (action == GLFW.GLFW_RELEASE) return;
            if (action == GLFW.GLFW_PRESS && !fr.reborn.hud.concentration.ModeConcentration.rotation()) {
                InteractionMode.INSTANCE.onClick();
            }
            ci.cancel(); // pas d'attaque ; ne ferme pas le menu
            return;
        }
        // Concentration : clic droit maintenu = la caméra tourne (pas d'« utiliser » sur l'objet en main) ; refusé menu
        // ouvert (le panneau resterait figé à côté d'une cible qui s'éloigne).
        if (button.button() == GLFW.GLFW_MOUSE_BUTTON_RIGHT && fr.reborn.hud.concentration.ModeConcentration.actif()) {
            if (action == GLFW.GLFW_RELEASE) {
                fr.reborn.hud.concentration.ModeConcentration.clicDroit(false);
                return;
            }
            if (action == GLFW.GLFW_PRESS) fr.reborn.hud.concentration.ModeConcentration.clicDroit(true);
            ci.cancel();
        }
        // Autres boutons : laissés au jeu, ne ferment pas le menu.
    }
}
