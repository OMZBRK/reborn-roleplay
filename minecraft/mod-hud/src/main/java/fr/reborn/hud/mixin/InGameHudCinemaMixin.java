package fr.reborn.hud.mixin;

import fr.reborn.hud.immersion.CinemaBars;
import fr.reborn.hud.immersion.PhotoMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.util.ScreenshotRecorder;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Immersion : on intercepte le rendu du HUD vanilla pour
 * <ul>
 *   <li><b>Bandes cinéma</b> : 0 HUD sauf le chat.</li>
 *   <li><b>Mode photo</b> : capture une frame PROPRE (la scène 3D au HEAD, avant
 *       que le HUD soit dessiné) puis affiche l'overlay du mode photo.</li>
 * </ul>
 * Annuler tout le {@code render} coupe aussi les HudRenderCallback, d'où le
 * dessin manuel ici.
 */
@Mixin(Gui.class)
public abstract class InGameHudCinemaMixin {

    @Shadow @Final private ChatComponent chatHud;
    @Shadow private int ticks;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void reborn$immersionHud(GuiGraphicsExtractor ctx, DeltaTracker counter, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();

        if (PhotoMode.INSTANCE.isActive()) {
            if (PhotoMode.INSTANCE.consumeCapture()) {
                // Au HEAD, le framebuffer = scène 3D sans HUD → screenshot propre.
                ScreenshotRecorder.saveScreenshot(mc.runDirectory, mc.getFramebuffer(),
                    text -> this.chatHud.addMessage(text));
            }
            // Le panneau est dessiné par PhotoModeScreen. Ici on masque juste le HUD.
            ci.cancel();
            return;
        }

        if (CinemaBars.INSTANCE.isProgressActive()) {
            CinemaBars.INSTANCE.renderBars(ctx);
            if (!mc.options.hideGui) {
                int sw = mc.getWindow().getGuiScaledWidth();
                int sh = mc.getWindow().getGuiScaledHeight();
                int mx = (int) (mc.mouse.getX() * sw / mc.getWindow().width());
                int my = (int) (mc.mouse.getY() * sh / mc.getWindow().getHeight());
                boolean focused = mc.screen instanceof ChatScreen;
                this.chatHud.render(ctx, this.ticks, mx, my, focused);
            }
            ci.cancel();
        }
    }
}
