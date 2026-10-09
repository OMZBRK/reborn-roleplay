package fr.reborn.hud.mixin.menu;

import fr.reborn.hud.ui.MenuScale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Ouverture / fermeture d'un écran : échelle des menus Reborn (voir {@link MenuScale}). */
@Mixin(Gui.class)
public abstract class MenuScaleMixin {

    @Shadow @Final private Minecraft minecraft;
    @Shadow private Screen screen;

    @Inject(method = "setScreen", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lcom/mojang/blaze3d/platform/Window;getGuiScaledWidth()I"))
    private void reborn$beforeInit(Screen s, CallbackInfo ci) {
        MenuScale.apply(minecraft, screen);
    }

    @Inject(method = "setScreen", at = @At("TAIL"))
    private void reborn$afterClose(Screen s, CallbackInfo ci) {
        if (screen == null) MenuScale.apply(minecraft, null);
    }
}
