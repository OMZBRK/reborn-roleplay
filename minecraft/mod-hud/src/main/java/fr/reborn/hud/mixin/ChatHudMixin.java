package fr.reborn.hud.mixin;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.chat.ChatSettings;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementBounds;
import fr.reborn.hud.element.HudElementState;
import fr.reborn.hud.runtime.ChatMessageRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.gui.screens.ChatScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import net.minecraft.network.chat.Style;

import java.util.List;

/**
 * Mixin sur {@link ChatComponent#render}.
 *
 * <p>On remplace le rendu vanilla des messages par {@link ChatMessageRenderer}
 * (features RP : têtes de joueurs, highlight de mention, timestamp) — mais à la
 * <b>géométrie vanilla</b> (messages ancrés en bas, juste au-dessus de la barre
 * de saisie). La barre de saisie elle-même reste à sa position vanilla normale
 * (cf {@code ChatScreenMixin} qui ne la déplace plus). Le scroll vanilla
 * ({@code scrolledLines}) est respecté.
 */
@Mixin(ChatComponent.class)
public abstract class ChatHudMixin {

    @Shadow private List<GuiMessage.Line> visibleMessages;
    @Shadow private int scrolledLines;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void reborn$renderCustomChat(GuiGraphicsExtractor ctx, int currentTick, int mouseX, int mouseY,
                                         boolean focused, CallbackInfo ci) {
        HudElementState state = readStateSafely();
        if (!state.visible()) {
            ci.cancel();
            return;
        }

        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        HudElementBounds anchor = HudElementBounds.vanillaFor(HudElement.CHAT, screenW, screenH);

        // Offset/scale (déplacement via l'éditeur HUD). Par défaut (0,0,1) =
        // position vanilla pure.
        ctx.pose().pushMatrix();
        ctx.pose().translate(state.x(), state.y());
        if (state.scale() != 1.0f) {
            ctx.pose().translate(anchor.x(), anchor.y());
            ctx.pose().scale(state.scale(), state.scale());
            ctx.pose().translate(-anchor.x(), -anchor.y());
        }

        boolean chatOpen = mc.screen instanceof ChatScreen;
        ChatSettings settings = ChatSettings.defaults();
        String playerName = null;
        try {
            settings = RebornHudClient.config().getChatSettings();
            if (mc.player != null) playerName = mc.player.getProfile().getName();
        } catch (RuntimeException ignored) {}

        ChatMessageRenderer.renderMessages(ctx, mc.font,
            visibleMessages, scrolledLines, currentTick, chatOpen,
            screenW, screenH, settings, playerName);

        ctx.pose().popMatrix();
        ci.cancel(); // on a tout rendu nous-mêmes
    }

    /**
     * Le chat custom est dessiné décalé par l'offset HUD (ex. +23 en Y). La
     * détection de clic sur un lien ({@code getTextStyleAt}) utilise la géométrie
     * vanilla → on retranche l'offset pour que le clic suive le rendu réel.
     */
    @Inject(method = "getTextStyleAt", at = @At("HEAD"), cancellable = true)
    private void reborn$styleAt(double x, double y, CallbackInfoReturnable<Style> cir) {
        try {
            Minecraft mc = Minecraft.getInstance();
            HudElementState st = readStateSafely();
            ChatSettings settings = RebornHudClient.config().getChatSettings();
            Style s = ChatMessageRenderer.styleAt(mc, mc.font, visibleMessages, scrolledLines,
                mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(), settings,
                x, y, st.x(), st.y());
            cir.setReturnValue(s);
        } catch (RuntimeException ignored) {
            // en cas d'erreur, laisse vanilla gérer
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
