package fr.reborn.hud.mixin;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.chat.ChatSettings;
import fr.reborn.hud.chat.RebornChatRenderer;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementBounds;
import fr.reborn.hud.element.HudElementState;
import fr.reborn.hud.runtime.ChatMessageRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Mixin sur {@link ChatHud#render}.
 *
 * <p><b>Mode customChat ON (par defaut)</b> : on cancel ENTIEREMENT le
 * rendu vanilla et on prend le relais via {@link ChatMessageRenderer}
 * (pas de fond noir vanilla). Si chat OUVERT en plus, on rajoute le
 * panel Zenkai ({@link RebornChatRenderer}) avec tabs + accent bar.
 *
 * <p><b>Mode customChat OFF</b> : vanilla rendu pur, juste avec notre
 * matrix transform (offset / scale).
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

    @Shadow private List<ChatHudLine.Visible> visibleMessages;
    @Shadow private int scrolledLines;

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

        boolean custom = false;
        try { custom = RebornHudClient.config().isEnableCustomChat(); }
        catch (RuntimeException ignored) {}

        // Push matrix : translate + scale autour de l'anchor.
        ctx.getMatrices().push();
        ctx.getMatrices().translate(state.x(), state.y(), 0);
        if (state.scale() != 1.0f) {
            ctx.getMatrices().translate(anchor.x(), anchor.y(), 0);
            ctx.getMatrices().scale(state.scale(), state.scale(), 1.0f);
            ctx.getMatrices().translate(-anchor.x(), -anchor.y(), 0);
        }

        if (!custom) {
            // Vanilla path : on laisse render() faire son boulot.
            // @RETURN injectera le pop.
            return;
        }

        // Custom path : cancel vanilla, on rend nous-meme.
        boolean chatOpen = mc.currentScreen instanceof ChatScreen;
        int opacity = 92;
        ChatSettings settings = ChatSettings.defaults();
        String playerName = null;
        try {
            settings = RebornHudClient.config().getChatSettings();
            opacity  = settings.opacity;
            ClientPlayerEntity player = mc.player;
            if (player != null) playerName = player.getGameProfile().getName();
        } catch (RuntimeException ignored) {}

        if (chatOpen) {
            // Panel custom (rounded + accent bar + tabs + input)
            RebornChatRenderer.render(ctx, mc.textRenderer, screenW, screenH, opacity);
        }
        // Messages custom (toujours, fermé ou ouvert) — pas de fond noir vanilla
        ChatMessageRenderer.renderMessages(ctx, mc.textRenderer,
            visibleMessages, scrolledLines,
            currentTick, chatOpen,
            screenW, screenH, settings, playerName);

        // Pop nous-meme + cancel pour que vanilla n'execute rien.
        ctx.getMatrices().pop();
        ci.cancel();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void reborn$popOffset(DrawContext ctx, int currentTick, int mouseX, int mouseY,
                                  boolean focused, CallbackInfo ci) {
        // RETURN ne fire pas si HEAD a cancel (cas custom). Donc on
        // pop uniquement si on n'a PAS pris le custom path (= vanilla path
        // a tourne et il faut equilibrer le push fait en HEAD).
        HudElementState state = readStateSafely();
        if (!state.visible()) return;
        boolean custom = false;
        try { custom = RebornHudClient.config().isEnableCustomChat(); }
        catch (RuntimeException ignored) {}
        if (custom) return; // already popped in HEAD
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
