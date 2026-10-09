package fr.reborn.hud.mixin.menu;

import fr.reborn.hud.ui.MenuScale;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/** Redimensionnement de la fenêtre / changement d'échelle avec un menu Reborn ouvert. */
@Mixin(Minecraft.class)
public abstract class MenuScaleResizeMixin {

    @ModifyArg(method = "resizeGui", at = @At(value = "INVOKE",
            target = "Lcom/mojang/blaze3d/platform/Window;setGuiScale(I)V"))
    private int reborn$menuScale(int scale) {
        Minecraft mc = (Minecraft) (Object) this;
        return MenuScale.fit(mc.getWindow(), scale, mc.gui == null ? null : mc.gui.screen());
    }
}
