package fr.reborn.hud.mixin;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementOffset;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin sur {@link ChatHud#render} : applique l'offset configuré dans
 * {@link fr.reborn.hud.config.HudConfig} via une translation matricielle
 * autour du draw vanilla.
 *
 * <p>Pattern push/translate/pop avec deux injections symétriques :
 * <ul>
 *   <li>HEAD : push matrix + translate (x, y)</li>
 *   <li>RETURN : pop matrix</li>
 * </ul>
 *
 * <p>L'offset s'applique au texte du chat, à son fond, au cursor d'entrée
 * — tout ce qui est dessiné par {@code ChatHud.render}.
 *
 * <p>Lecture safe : si {@link RebornHudClient#config()} pas encore prête
 * (très tôt au boot, théoriquement impossible ici mais belt-and-braces),
 * on no-op.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void reborn$pushOffset(DrawContext ctx, int currentTick, int mouseX, int mouseY,
                                   boolean focused, CallbackInfo ci) {
        HudElementOffset off = readOffsetSafely();
        if (off.x() == 0 && off.y() == 0) return;
        ctx.getMatrices().push();
        ctx.getMatrices().translate(off.x(), off.y(), 0);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void reborn$popOffset(DrawContext ctx, int currentTick, int mouseX, int mouseY,
                                  boolean focused, CallbackInfo ci) {
        HudElementOffset off = readOffsetSafely();
        if (off.x() == 0 && off.y() == 0) return;
        ctx.getMatrices().pop();
    }

    private static HudElementOffset readOffsetSafely() {
        try {
            return RebornHudClient.config().offsetOf(HudElement.CHAT);
        } catch (IllegalStateException e) {
            return HudElementOffset.ZERO;
        }
    }
}
