package fr.reborn.hud.mixin;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.chat.ChatSettings;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementBounds;
import fr.reborn.hud.element.HudElementState;
import fr.reborn.hud.runtime.ChatMessageRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.ChatHudLine;
import net.minecraft.client.gui.screen.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Mixin sur {@link ChatHud#render}.
 *
 * <p>On remplace le rendu vanilla des messages par {@link ChatMessageRenderer}
 * (features RP : têtes de joueurs, highlight de mention, timestamp) — mais à la
 * <b>géométrie vanilla</b> (messages ancrés en bas, juste au-dessus de la barre
 * de saisie). La barre de saisie elle-même reste à sa position vanilla normale
 * (cf {@code ChatScreenMixin} qui ne la déplace plus). Le scroll vanilla
 * ({@code scrolledLines}) est respecté.
 */
@Mixin(ChatHud.class)
public abstract class ChatHudMixin {

    @Shadow private List<ChatHudLine.Visible> visibleMessages;
    @Shadow private int scrolledLines;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void reborn$renderCustomChat(DrawContext ctx, int currentTick, int mouseX, int mouseY,
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

        // Offset/scale (déplacement via l'éditeur HUD). Par défaut (0,0,1) =
        // position vanilla pure.
        ctx.getMatrices().push();
        ctx.getMatrices().translate(state.x(), state.y(), 0);
        if (state.scale() != 1.0f) {
            ctx.getMatrices().translate(anchor.x(), anchor.y(), 0);
            ctx.getMatrices().scale(state.scale(), state.scale(), 1.0f);
            ctx.getMatrices().translate(-anchor.x(), -anchor.y(), 0);
        }

        boolean chatOpen = mc.currentScreen instanceof ChatScreen;
        ChatSettings settings = ChatSettings.defaults();
        String playerName = null;
        try {
            settings = RebornHudClient.config().getChatSettings();
            if (mc.player != null) playerName = mc.player.getGameProfile().getName();
        } catch (RuntimeException ignored) {}

        ChatMessageRenderer.renderMessages(ctx, mc.textRenderer,
            visibleMessages, scrolledLines, currentTick, chatOpen,
            screenW, screenH, settings, playerName);

        ctx.getMatrices().pop();
        ci.cancel(); // on a tout rendu nous-mêmes
    }

    /**
     * Le chat custom est dessiné décalé par l'offset HUD (ex. +23 en Y). La
     * détection de clic sur un lien ({@code getTextStyleAt}) utilise la géométrie
     * vanilla → on retranche l'offset pour que le clic suive le rendu réel.
     */
    @ModifyVariable(method = "getTextStyleAt", at = @At("HEAD"), ordinal = 1, argsOnly = true)
    private double reborn$adjustClickY(double y) {
        try {
            return y - readStateSafely().y();
        } catch (RuntimeException e) {
            return y;
        }
    }

    private static HudElementState readStateSafely() {
        try {
            return RebornHudClient.config().stateOf(HudElement.CHAT);
        } catch (IllegalStateException e) {
            return HudElementState.DEFAULT;
        }
    }
}
