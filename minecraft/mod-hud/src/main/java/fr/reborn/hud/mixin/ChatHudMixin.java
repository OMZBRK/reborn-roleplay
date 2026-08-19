package fr.reborn.hud.mixin;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.chat.ChatSettings;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.runtime.ChatMessageRenderer;
import fr.reborn.hud.runtime.HudTransform;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

/**
 * Rendu <b>chat RP custom</b> (têtes de joueurs, mentions, timestamps, panneau
 * sombre) + offset/scale/visibilité HUD, en enrobant
 * {@link ChatComponent#extractRenderState} (26.1, mode retained).
 *
 * <p>On cible l'overload public à 7 paramètres via son descripteur. Le chat passe
 * par cette méthode dans les deux contextes (HUD via {@code Gui#extractChat} ET
 * {@code ChatScreen} quand il est ouvert) → la position modifiée + le rendu custom
 * s'appliquent partout.
 *
 * <p>Le HUD vanilla dessine le chat en deux passes ({@code DisplayMode}) :
 * {@code BACKGROUND} (fond) puis {@code FOREGROUND} (texte). On <b>supprime le
 * fond vanilla</b> et on dessine NOTRE rendu ({@link ChatMessageRenderer}, qui a
 * son propre fond) sur la passe FOREGROUND, à l'offset HUD.
 */
@Mixin(ChatComponent.class)
public abstract class ChatHudMixin {

    private static final String EXTRACT =
        "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V";

    // 26.1 : liste des lignes affichables + position de scroll.
    @Shadow private List<GuiMessage.Line> trimmedMessages;
    @Shadow private int chatScrollbarPos;

    @Inject(method = EXTRACT, at = @At("HEAD"), cancellable = true)
    private void reborn$customChat(GuiGraphicsExtractor ctx, Font font, int tickCount, int mouseX, int mouseY,
                                   ChatComponent.DisplayMode mode, boolean flag, CallbackInfo ci) {
        if (!HudTransform.isVisible(HudElement.CHAT)) { ci.cancel(); return; }

        Minecraft mc = Minecraft.getInstance();
        boolean chatOpen = mc.gui.screen() instanceof ChatScreen;
        // On dessine le chat RP custom sur LA passe correspondant au contexte :
        // FOREGROUND quand le chat est ouvert, BACKGROUND (passe du HUD) quand il
        // est fermé — sinon les messages n'apparaissaient qu'en ouvrant le chat.
        // Les autres passes vanilla sont annulées (pas de rendu vanilla / doublon).
        ChatComponent.DisplayMode wanted = chatOpen
            ? ChatComponent.DisplayMode.FOREGROUND
            : ChatComponent.DisplayMode.BACKGROUND;
        if (mode != wanted) { ci.cancel(); return; }

        // On dessine le chat RP custom à l'offset HUD.
        HudTransform.apply(ctx, HudElement.CHAT);
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        boolean focused = chatOpen;
        // currentTick = le compteur passé par vanilla (matche GuiMessage.addedTime)
        // → le fade unfocused est correct = messages visibles SANS ouvrir le chat.
        int currentTick = tickCount;

        ChatSettings settings = ChatSettings.defaults();
        String playerName = null;
        try {
            settings = RebornHudClient.config().getChatSettings();
            if (mc.player != null) playerName = mc.player.getGameProfile().name();
        } catch (RuntimeException ignored) {}

        ChatMessageRenderer.renderMessages(ctx, font, this.trimmedMessages, this.chatScrollbarPos,
            currentTick, focused, screenW, screenH, settings, playerName);

        HudTransform.revert(ctx);
        ci.cancel();
    }
}
