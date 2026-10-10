package fr.reborn.hud.mixin;

import fr.reborn.hud.menu.character.scene.SceneCamera;
import net.minecraft.client.renderer.SkyRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Scènes de sélection / création : ni ciel, ni soleil, ni lune, ni étoiles du jeu (fond noir). */
@Mixin(SkyRenderer.class)
public abstract class SceneVoidSkyMixin {

    @Inject(method = "renderSkyDisc", at = @At("HEAD"), cancellable = true)
    private void reborn$noDisc(int color, CallbackInfo ci) { if (SceneCamera.active()) ci.cancel(); }

    @Inject(method = "renderDarkDisc", at = @At("HEAD"), cancellable = true)
    private void reborn$noDarkDisc(CallbackInfo ci) { if (SceneCamera.active()) ci.cancel(); }

    @Inject(method = "renderSunMoonAndStars", at = @At("HEAD"), cancellable = true)
    private void reborn$noCelestials(CallbackInfo ci) { if (SceneCamera.active()) ci.cancel(); }

    @Inject(method = "renderSunriseAndSunset", at = @At("HEAD"), cancellable = true)
    private void reborn$noSunrise(CallbackInfo ci) { if (SceneCamera.active()) ci.cancel(); }
}
