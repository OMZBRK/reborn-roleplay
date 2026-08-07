package fr.reborn.hud.mixin;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.chat.ChatLayout;
import fr.reborn.hud.chat.ChatSettings;
import fr.reborn.hud.chat.EmojiPicker;
import fr.reborn.hud.element.HudElement;
import fr.reborn.hud.element.HudElementState;
import fr.reborn.hud.menu.Colors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Mixin sur {@link ChatScreen}.
 *
 * <p>La barre de saisie reste en bas mais est rétrécie à la <b>largeur du chat</b>
 * (façon Paladium) au lieu de toute la page, avec le <b>bouton emoji</b> à sa
 * droite. On masque le champ si l'élément CHAT est caché via l'éditeur HUD.
 */
@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin {

    @Shadow protected EditBox input;

    @Inject(method = "init", at = @At("TAIL"))
    private void reborn$chatFieldVisibility(CallbackInfo ci) {
        if (input == null) return;
        HudElementState state = readStateSafely();
        if (!state.visible()) {
            input.visible = false;
        }
        // Rétrécit la saisie à la largeur du chat (laisse la place au bouton emoji).
        int sw = Minecraft.getInstance().getWindow().getGuiScaledWidth();
        input.setX(ChatLayout.TEXT_X);
        input.setWidth(ChatLayout.inputW(sw));
        EmojiPicker.onClose(); // picker fermé à chaque ouverture du chat
    }

    /**
     * La barre de saisie vanilla est un fill pleine largeur tout en bas. On le
     * remplace par une barre à la largeur du chat, stylée Reborn.
     */
    @Redirect(method = "extractRenderState",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private void reborn$narrowInputBar(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int color) {
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getGuiScaledWidth();
        int sh = mc.getWindow().getGuiScaledHeight();
        if (y2 >= sh - 2 && (y2 - y1) <= 16 && x2 >= sw - 4) {
            int bw = ChatLayout.boxW(sw);
            ctx.fill(ChatLayout.LEFT, y1, ChatLayout.LEFT + bw, y2, 0xC00A0608);
            ctx.fill(ChatLayout.LEFT, y1, ChatLayout.LEFT + bw, y1 + 1,
                Colors.withAlpha(Colors.ACCENT, 0.5f));
        } else {
            ctx.fill(x1, y1, x2, y2, color);
        }
    }

    // Dessine le bouton emoji + le picker par-dessus l'écran de chat.
    // 26.1 : ChatScreen#render → extractRenderState(GuiGraphicsExtractor, int, int, float).
    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void reborn$renderEmoji(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        EmojiPicker.render(ctx, mouseX, mouseY,
            mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight());
        reborn$animatedCaret(ctx, mc);
    }

    /**
     * Curseur de saisie animé (façon Animated Typing) : un curseur accent avec
     * un fondu doux (pulse), dessiné en fin de texte. Styles : barre / soulignement
     * / bloc. Approx : curseur en fin de saisie (cas le plus courant).
     */
    private void reborn$animatedCaret(GuiGraphicsExtractor ctx, Minecraft mc) {
        if (input == null || !input.isFocused() || mc.font == null) return;
        ChatSettings s;
        try {
            s = RebornHudClient.config().getChatSettings();
        } catch (RuntimeException e) {
            return;
        }
        if (!s.animatedTyping) return;

        int caretX = input.getX() + 4 + mc.font.width(input.getValue());
        int caretY = input.getY();
        int h = 9;
        float pulse = 0.35f + 0.5f * (float) (0.5 + 0.5 * Math.sin(System.currentTimeMillis() / 260.0));
        int a = Math.min(255, (int) (pulse * 255));
        int col = (a << 24) | (Colors.ACCENT & 0x00FFFFFF);
        switch (s.typingCursorStyle) {
            case 1 -> ctx.fill(caretX, caretY + h - 1, caretX + 6, caretY + h + 1, col);   // soulignement
            case 2 -> ctx.fill(caretX, caretY - 1, caretX + 5, caretY + h,
                (Math.min(150, a) << 24) | (Colors.ACCENT & 0x00FFFFFF));                   // bloc
            default -> ctx.fill(caretX, caretY - 1, caretX + 2, caretY + h, col);           // barre
        }
    }

    // Intercepte les clics sur le bouton/picker avant le reste de l'écran.
    // 26.1 : Screen#mouseClicked(double,double,int) -> mouseClicked(MouseButtonEvent, boolean).
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void reborn$clickEmoji(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick,
                                   CallbackInfoReturnable<Boolean> cir) {
        if (event.button() != 0) return;
        Minecraft mc = Minecraft.getInstance();
        boolean handled = EmojiPicker.handleClick(event.x(), event.y(),
            mc.getWindow().getGuiScaledWidth(), mc.getWindow().getGuiScaledHeight(),
            s -> { if (input != null) input.insertText(s); });
        if (handled) cir.setReturnValue(true);
    }

    private static HudElementState readStateSafely() {
        try {
            return RebornHudClient.config().stateOf(HudElement.CHAT);
        } catch (IllegalStateException e) {
            return HudElementState.DEFAULT;
        }
    }
}
