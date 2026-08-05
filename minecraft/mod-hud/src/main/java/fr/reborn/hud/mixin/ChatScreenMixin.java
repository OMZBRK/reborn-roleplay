package fr.reborn.hud.mixin;

import fr.reborn.hud.RebornHudClient;
import fr.reborn.hud.chat.ChatLayout;
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

    @Shadow protected EditBox chatField;

    @Inject(method = "init", at = @At("TAIL"))
    private void reborn$chatFieldVisibility(CallbackInfo ci) {
        if (chatField == null) return;
        HudElementState state = readStateSafely();
        if (!state.visible()) {
            chatField.visible = false;
        }
        // Rétrécit la saisie à la largeur du chat (laisse la place au bouton emoji).
        int sw = Minecraft.getInstance().getWindow().getScaledWidth();
        chatField.setX(ChatLayout.TEXT_X);
        chatField.setWidth(ChatLayout.inputW(sw));
        EmojiPicker.close(); // picker fermé à chaque ouverture du chat
    }

    /**
     * La barre de saisie vanilla est un fill pleine largeur tout en bas. On le
     * remplace par une barre à la largeur du chat, stylée Reborn.
     */
    @Redirect(method = "render",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private void reborn$narrowInputBar(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int color) {
        Minecraft mc = Minecraft.getInstance();
        int sw = mc.getWindow().getScaledWidth();
        int sh = mc.getWindow().getScaledHeight();
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
    @Inject(method = "render", at = @At("TAIL"))
    private void reborn$renderEmoji(GuiGraphicsExtractor ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        EmojiPicker.render(ctx, mouseX, mouseY,
            mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight());
    }

    // Intercepte les clics sur le bouton/picker avant le reste de l'écran.
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void reborn$clickEmoji(double mx, double my, int button,
                                   CallbackInfoReturnable<Boolean> cir) {
        if (button != 0) return;
        Minecraft mc = Minecraft.getInstance();
        boolean handled = EmojiPicker.handleClick(mx, my,
            mc.getWindow().getScaledWidth(), mc.getWindow().getScaledHeight(),
            s -> { if (chatField != null) chatField.write(s); });
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
