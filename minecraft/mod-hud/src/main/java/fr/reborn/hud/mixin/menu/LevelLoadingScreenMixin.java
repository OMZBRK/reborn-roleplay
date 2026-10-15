package fr.reborn.hud.mixin.menu;

import fr.reborn.hud.menu.connect.ChargementScene;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LevelLoadingScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.LevelLoadTracker;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Écran de chargement du monde pendant le trajet vers le serveur, et sur un serveur distant (changement de dimension,
 * réapparition) : la scène de chargement à la place des tuiles de chunks, de la barre et des textes vanilla. En monde
 * solo, rendu vanilla.
 *
 * <p>Fermeture ({@code tick} → {@code onClose} quand le monde est prêt) : retenue tant qu'un pack serveur est en route
 * (Nexo l'envoie à l'arrivée du joueur) puis le temps que la scène s'éteigne dans la nuit
 * ({@link ChargementScene#laisserFermerMonde}). Le monde continue d'être rendu derrière (le jeu attend que la section
 * du joueur soit compilée pour se dire prêt) : on ne le cache pas.
 */
@Mixin(LevelLoadingScreen.class)
public abstract class LevelLoadingScreenMixin extends Screen {

    @Shadow private float smoothedProgress;
    @Shadow private LevelLoadTracker loadTracker;

    protected LevelLoadingScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void reborn$scene(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!ChargementScene.pourMonde()) return;
        float p = this.loadTracker != null && this.loadTracker.hasProgress() ? this.smoothedProgress : -1f;
        ChargementScene.dessinerMonde(ctx, p);
        ci.cancel();
    }

    @Inject(method = "extractBackground", at = @At("HEAD"), cancellable = true)
    private void reborn$sansFond(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (ChargementScene.pourMonde()) ci.cancel();
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/screens/LevelLoadingScreen;onClose()V"))
    private void reborn$fermer(LevelLoadingScreen self) {
        if (ChargementScene.laisserFermerMonde()) self.onClose();
    }
}
