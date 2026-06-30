package fr.reborn.hud.mixin;

import fr.reborn.hud.immersion.CinemaBars;
import fr.reborn.hud.immersion.PhotoMode;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.render.RenderTickCounter;
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
@Mixin(InGameHud.class)
public abstract class InGameHudCinemaMixin {

    @Shadow @Final private ChatHud chatHud;
    @Shadow private int ticks;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void reborn$immersionHud(DrawContext ctx, RenderTickCounter counter, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();

        if (PhotoMode.INSTANCE.isActive()) {
            if (PhotoMode.INSTANCE.consumeCapture()) {
                // Au HEAD, le framebuffer = scène 3D sans HUD → screenshot propre.
                ScreenshotRecorder.saveScreenshot(mc.runDirectory, mc.getFramebuffer(),
                    text -> this.chatHud.addMessage(text));
            }
            // Bandes cinéma de cadrage (dessinées APRÈS la capture → absentes du
            // screenshot, juste pour composer). Le panneau est dessiné par l'écran.
            int w = ctx.getScaledWindowWidth(), h = ctx.getScaledWindowHeight();
            int barH = Math.round(h * 0.10f);
            ctx.fill(0, 0, w, barH, 0xFF000000);
            ctx.fill(0, h - barH, w, h, 0xFF000000);
            ci.cancel();
            return;
        }

        if (CinemaBars.INSTANCE.isProgressActive()) {
            CinemaBars.INSTANCE.renderBars(ctx);
            if (!mc.options.hudHidden) {
                int sw = mc.getWindow().getScaledWidth();
                int sh = mc.getWindow().getScaledHeight();
                int mx = (int) (mc.mouse.getX() * sw / mc.getWindow().getWidth());
                int my = (int) (mc.mouse.getY() * sh / mc.getWindow().getHeight());
                boolean focused = mc.currentScreen instanceof ChatScreen;
                this.chatHud.render(ctx, this.ticks, mx, my, focused);
            }
            ci.cancel();
        }
    }
}
