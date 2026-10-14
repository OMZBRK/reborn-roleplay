package fr.reborn.hud.mixin;

import fr.reborn.hud.ko.KoClient;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Douleur, à terre, inconscient : les sons du monde s'étouffent (le cœur et le souffle restent nets) — {@link KoClient#volumeSon}. */
@Mixin(SoundEngine.class)
public abstract class SoundEngineKoMixin {

    @Inject(method = "calculateVolume(Lnet/minecraft/client/resources/sounds/SoundInstance;)F", at = @At("RETURN"),
            cancellable = true)
    private void reborn$koEtouffe(SoundInstance son, CallbackInfoReturnable<Float> cir) {
        float f = KoClient.volumeSon(son);
        if (f < 1f) cir.setReturnValue(cir.getReturnValueF() * f);
    }
}
