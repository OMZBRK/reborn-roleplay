package fr.reborn.hud.mixin.menu;

import fr.reborn.hud.ui.EcranOpaque;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Menus opaques (voir {@link EcranOpaque}) : le HUD caché sous le menu n'est pas extrait (vitals, chat, barres…). */
@Mixin(Hud.class)
public abstract class OpaqueMenuHudMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V",
            at = @At("HEAD"), cancellable = true)
    private void reborn$opaqueHud(GuiGraphicsExtractor ctx, DeltaTracker delta, CallbackInfo ci) {
        if (EcranOpaque.mondeCache()) ci.cancel();
    }
}
