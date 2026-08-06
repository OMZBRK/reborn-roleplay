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

    // Mode photo : au HEAD (avant le HUD), capture propre + masque le HUD.
    // 26.1 : Gui#render → extractRenderState(GuiGraphicsExtractor, DeltaTracker).
    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void reborn$photoHud(GuiGraphicsExtractor ctx, DeltaTracker counter, CallbackInfo ci) {
        if (!PhotoMode.INSTANCE.isActive()) return;
        Minecraft mc = Minecraft.getInstance();
        if (PhotoMode.INSTANCE.consumeCapture()) {
            // Au HEAD, le framebuffer = scène 3D sans HUD → screenshot propre.
            Screenshot.grab(mc.gameDirectory, mc.getMainRenderTarget(),
                text -> this.chat.addClientSystemMessage(text));
        }
        // Le panneau est dessiné par PhotoModeScreen ; ici on masque juste le HUD.
        ci.cancel();
    }

    // Bandes cinéma : au TAIL (par-dessus le HUD). Pas de cancel → jamais bloqué.
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void reborn$cinemaBars(GuiGraphicsExtractor ctx, DeltaTracker counter, CallbackInfo ci) {
        if (PhotoMode.INSTANCE.isActive()) return;
        if (CinemaBars.INSTANCE.isProgressActive()) {
            CinemaBars.INSTANCE.renderBars(ctx);
        }
    }
}
