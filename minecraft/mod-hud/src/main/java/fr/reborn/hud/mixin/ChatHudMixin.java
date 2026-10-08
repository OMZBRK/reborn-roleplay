package fr.reborn.hud.mixin;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.chat.ChatPanel;
import fr.reborn.hud.chat.ChatSettings;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.runtime.HudTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Remplace le rendu du chat vanilla par le {@link ChatPanel} Reborn, à l'offset / l'échelle de l'élément HUD
 * {@link HudElement#CHAT}, en enrobant {@link ChatComponent#extractRenderState} (26.x, mode retained).
 *
 * <p>Le HUD vanilla dessine le chat en deux passes ({@code DisplayMode}) : on dessine le panneau sur
 * {@code FOREGROUND} quand le chat est ouvert, sur {@code BACKGROUND} (passe du HUD) quand il est fermé, et on
 * annule toutes les autres passes.
 */
@Mixin(ChatComponent.class)
public abstract class ChatHudMixin {

    private static final String EXTRACT =
        "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V";

    @Shadow @Final private List<GuiMessage> allMessages;

    @Inject(method = EXTRACT, at = @At("HEAD"), cancellable = true)
    private void reborn$customChat(GuiGraphicsExtractor ctx, Font font, int tickCount, int mouseX, int mouseY,
                                   ChatComponent.DisplayMode mode, boolean flag, CallbackInfo ci) {
        ci.cancel();
        if (!HudTransform.isVisible(HudElement.CHAT)) return;

        Minecraft mc = Minecraft.getInstance();
        boolean chatOpen = mc.gui.screen() instanceof ChatScreen;
        ChatComponent.DisplayMode wanted = chatOpen
            ? ChatComponent.DisplayMode.FOREGROUND
            : ChatComponent.DisplayMode.BACKGROUND;
        if (mode != wanted) return;

        ChatSettings settings = ChatSettings.defaults();
        String playerName = null;
        try {
            settings = RebornHudClient.config().getChatSettings();
            if (mc.player != null) playerName = mc.player.getGameProfile().name();
        } catch (RuntimeException ignored) {}

        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        double[] loc = HudTransform.toLocal(HudElement.CHAT, ChatPanel.mouseX, ChatPanel.mouseY);

        HudTransform.apply(ctx, HudElement.CHAT);
        ChatPanel.render(ctx, font, this.allMessages, tickCount, chatOpen, screenW, screenH, settings, playerName,
            loc[0], loc[1]);
        HudTransform.revert(ctx);
    }
}
