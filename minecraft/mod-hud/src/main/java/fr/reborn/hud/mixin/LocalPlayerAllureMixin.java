package fr.reborn.hud.mixin;

import fr.reborn.hud.allure.Allure;
import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Allures (14/10) : en vanilla, relâcher la touche de sprint ne l'arrête pas. Chez Reborn, la touche maintenue est le
 * <b>trot</b> : relâchée (au-delà du temps d'un double appui), le personnage revient à la marche. Sans effet hors
 * serveur Reborn, en sprint (double appui) et pour un sprint lancé par double appui sur Avancer (cf. {@link Allure}).
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerAllureMixin {

    @Inject(method = "shouldStopRunSprinting", at = @At("HEAD"), cancellable = true)
    private void reborn$trotRelache(CallbackInfoReturnable<Boolean> cir) {
        if (Allure.INSTANCE.arretTrot()) cir.setReturnValue(true);
    }
}
