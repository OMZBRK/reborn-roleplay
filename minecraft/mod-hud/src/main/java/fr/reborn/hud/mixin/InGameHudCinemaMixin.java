package fr.reborn.hud.mixin;

import fr.reborn.hud.immersion.CinemaBars;
import fr.reborn.hud.immersion.PhotoMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Screenshot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Immersion : on intercepte le rendu du HUD vanilla pour
 * <ul>
 *   <li><b>Mode photo</b> : capture une frame PROPRE (la scène 3D au HEAD, avant
 *       que le HUD soit dessiné) puis masque le HUD (overlay dessiné par
 *       {@code PhotoModeScreen}).</li>
 *   <li><b>Bandes cinéma</b> : letterbox noir dessiné <b>par-dessus</b> le HUD
 *       (au TAIL). On ne <i>cancel</i> plus le rendu du HUD (fragile en mode
 *       extraction 26.1 : ça pouvait laisser l'écran bloqué au toggle off).</li>
 * </ul>
 */
@Mixin(Gui.class)
public abstract class InGameHudCinemaMixin {

    // 26.1 : Gui#chatHud → chat.
    @Shadow @Final private ChatComponent chat;

    // HEAD (avant le HUD) : masquage du HUD pour le mode photo ET le mode
    // cinéma « sans HUD ». 26.1 : Gui#render → extractRenderState(GuiGraphicsExtractor, DeltaTracker).
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void reborn$hudHead(GuiGraphicsExtractor ctx, DeltaTracker counter, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        // Mode photo : capture propre (scène 3D avant le HUD) puis masque le HUD.
        if (PhotoMode.INSTANCE.isActive()) {
            if (PhotoMode.INSTANCE.consumeCapture()) {
                // 26.1 : extractRenderState tourne HORS render thread → on défère la
                // capture via mc.execute (render thread), sinon "RenderSystem called
                // from wrong thread". Le HUD étant masqué en mode photo, le
                // framebuffer reste une scène 3D propre à ce moment-là.
                mc.execute(() -> Screenshot.grab(mc.gameDirectory, mc.getMainRenderTarget(),
                    text -> this.chat.addClientSystemMessage(text)));
            }
            ci.cancel(); // panneau dessiné par PhotoModeScreen ; on masque le HUD.
            return;
        }
        // Cinéma CLEAN / BARS : on masque le HUD vanilla. En mode BARS on dessine
        // en plus les bandes par-dessus l'écran clean (le TAIL ne s'exécutera pas
        // à cause du cancel, donc on les dessine ici). En CLEAN : rien, juste le
        // cancel → écran plein propre.
        if (CinemaBars.INSTANCE.hidesHud()) {
            if (CinemaBars.INSTANCE.barsActive() && CinemaBars.INSTANCE.isProgressActive()) {
                CinemaBars.INSTANCE.renderBars(ctx);
            }
            ci.cancel();
        }
    }

    // Rétraction des bandes au retour BARS → HUD : le HUD est de nouveau visible
    // (pas de cancel au HEAD), donc on dessine les bandes qui se rétractent
    // par-dessus le HUD au TAIL. Hors de cette transition, progress ~0 → no-op.
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void reborn$cinemaBars(GuiGraphicsExtractor ctx, DeltaTracker counter, CallbackInfo ci) {
        if (PhotoMode.INSTANCE.isActive()) return;
        if (!CinemaBars.INSTANCE.hidesHud() && CinemaBars.INSTANCE.isProgressActive()) {
            CinemaBars.INSTANCE.renderBars(ctx);
        }
    }
}
