package fr.reborn.hud.mixin.menu;

import fr.reborn.hud.menu.connect.ChargementScene;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.toasts.ToastManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Pendant le trajet vers le serveur et tant que la scène de chargement est à l'écran, aucun toast vanilla par-dessus la scène
 * (« Messages non vérifiables », téléchargement du pack…) : rien de vanilla ne casse l'écran de chargement.
 */
@Mixin(ToastManager.class)
public abstract class ToastChargementMixin {

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void reborn$pasPendantLeChargement(GuiGraphicsExtractor ctx, CallbackInfo ci) {
        if (ChargementScene.masquerToasts()) ci.cancel();
    }
}
