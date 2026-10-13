package fr.reborn.hud.mixin.menu;

import fr.reborn.hud.ui.EcranOpaque;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Menus opaques (voir {@link EcranOpaque}) : le monde caché derrière n'est ni extrait ni rendu. Le drapeau
 * « renderLevel » de {@code renderFrame} n'est modifié que pour {@code GameRenderer.extract} et
 * {@code GameRenderer.render} (la mise à jour du niveau, le son et les ticks continuent).
 */
@Mixin(Minecraft.class)
public abstract class OpaqueMenuLevelMixin {

    @Inject(method = "renderFrame", at = @At("HEAD"))
    private void reborn$opaqueDebut(boolean renderLevel, CallbackInfo ci) {
        EcranOpaque.debutImage((Minecraft) (Object) this);
    }

    @ModifyArg(method = "renderFrame", index = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;extract(Lnet/minecraft/client/DeltaTracker;Z)V"))
    private boolean reborn$opaqueExtract(boolean renderLevel) {
        return EcranOpaque.monde(renderLevel);
    }

    @ModifyArg(method = "renderFrame", index = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GameRenderer;render(Lnet/minecraft/client/DeltaTracker;Z)V"))
    private boolean reborn$opaqueRender(boolean renderLevel) {
        return EcranOpaque.monde(renderLevel);
    }
}
