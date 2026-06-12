package fr.reborn.hud.mixin;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementBounds;
import fr.reborn.hud.element.HudElementState;
import fr.reborn.hud.ui.ChatPanelStyle;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin sur {@link ChatHud#render} : applique offset + scale + visibilité
 * configurés dans {@link fr.reborn.hud.config.HudConfig}.
 *
 * <p>Pattern push/translate/scale/pop avec injections symétriques HEAD + RETURN.
 *
 * <p>Quand {@code visible == false}, on injecte {@code ci.cancel()} sur HEAD
 * pour skipper complètement le render — sinon les messages continueraient
 * d'apparaître à leur position offset.
 *
 * <p>Anchor du scale : top-left de la boîte chat vanilla. Comme ChatHud
 * render avec son propre repère interne (bottom-up : les messages partent
 * du bas), scaler depuis le top-left donne un comportement intuitif
 * (l'utilisateur voit le chat grossir/rétrécir depuis son coin).
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void reborn$pushOffset(DrawContext ctx, int currentTick, int mouseX, int mouseY,
                                   boolean focused, CallbackInfo ci) {
        HudElementState state = readStateSafely();
        if (!state.visible()) {
            ci.cancel();
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        int screenW = mc.getWindow().getScaledWidth();
        int screenH = mc.getWindow().getScaledHeight();
        HudElementBounds anchor = HudElementBounds.vanillaFor(HudElement.CHAT, screenW, screenH);

        // Toujours push : on dessine notre panel style dans le matrix
        // transforme, et il faut pop dans @RETURN. Pas de short-circuit
        // "state vide → no-op" car on veut le panel meme a (0, 0, 1.0).
        ctx.getMatrices().push();
        ctx.getMatrices().translate(state.x(), state.y(), 0);
        if (state.scale() != 1.0f) {
            // Scale autour du top-left vanilla pour avoir un point d'ancrage
            // stable visuellement.
            ctx.getMatrices().translate(anchor.x(), anchor.y(), 0);
            ctx.getMatrices().scale(state.scale(), state.scale(), 1.0f);
            ctx.getMatrices().translate(-anchor.x(), -anchor.y(), 0);
        }

        // Dessine le panel style derriere le chat history vanilla. Comme
        // on est entre le push et le run de render(), nos drawcalls sont
        // sous le matrix transforme et arrivent BEFORE les messages —
        // donc visuellement en arriere-plan.
        ChatPanelStyle.drawPanel(ctx, screenW, screenH);
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void reborn$popOffset(DrawContext ctx, int currentTick, int mouseX, int mouseY,
                                  boolean focused, CallbackInfo ci) {
        HudElementState state = readStateSafely();
        if (!state.visible()) return; // HEAD a cancel, jamais entre dans render
        ctx.getMatrices().pop();
    }

    private static HudElementState readStateSafely() {
        try {
            return RebornHudClient.config().stateOf(HudElement.CHAT);
        } catch (IllegalStateException e) {
            return HudElementState.DEFAULT;
        }
    }
}
