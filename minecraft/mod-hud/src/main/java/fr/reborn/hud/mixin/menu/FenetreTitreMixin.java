package fr.reborn.hud.mixin.menu;

import fr.reborn.hud.menu.FenetreReborn;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Titre de la fenêtre du jeu : « 26.2* Reborn » dans tous les états (menu, solo, multijoueur), au lieu de
 * « Minecraft* 26.2 - Multijoueur (serveur tiers) ». Vanilla recalcule le titre par {@code Minecraft#updateTitle}
 * (changement d'écran, de monde, de connexion), qui passe toujours par {@code createTitle} : on remplace son retour,
 * le titre reste donc juste après chaque changement.
 */
@Mixin(Minecraft.class)
public abstract class FenetreTitreMixin {

    @Inject(method = "createTitle", at = @At("HEAD"), cancellable = true)
    private void reborn$titre(CallbackInfoReturnable<String> cir) {
        cir.setReturnValue(FenetreReborn.titre());
    }
}
